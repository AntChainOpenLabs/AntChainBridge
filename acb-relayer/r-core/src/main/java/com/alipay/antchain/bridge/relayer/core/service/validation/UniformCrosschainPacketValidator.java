/*
 * Copyright 2023 Ant Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alipay.antchain.bridge.relayer.core.service.validation;

import javax.annotation.Resource;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageTrustLevelEnum;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageV2;
import com.alipay.antchain.bridge.commons.core.am.IAuthMessage;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyProof;
import com.alipay.antchain.bridge.commons.core.ptc.ValidatedConsensusState;
import com.alipay.antchain.bridge.ptc.service.IPTCService;
import com.alipay.antchain.bridge.ptc.types.PTCVerifyCrossChainMessageResult;
import com.alipay.antchain.bridge.relayer.commons.constant.AuthMsgProcessStateEnum;
import com.alipay.antchain.bridge.relayer.commons.constant.UniformCrosschainPacketStateEnum;
import com.alipay.antchain.bridge.relayer.commons.exception.UcpValidationException;
import com.alipay.antchain.bridge.relayer.commons.model.TpBtaDO;
import com.alipay.antchain.bridge.relayer.commons.model.UniformCrosschainPacketContext;
import com.alipay.antchain.bridge.relayer.commons.model.ValidatedConsensusStateDO;
import com.alipay.antchain.bridge.relayer.core.manager.ptc.PtcManager;
import com.alipay.antchain.bridge.relayer.core.service.report.PlatformReportClient;
import com.alipay.antchain.bridge.relayer.dal.repository.IBlockchainRepository;
import com.alipay.antchain.bridge.relayer.dal.repository.ICrossChainMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UniformCrosschainPacketValidator {

    @Resource
    private ICrossChainMessageRepository crossChainMessageRepository;

    @Resource
    private IBlockchainRepository blockchainRepository;

    @Resource
    private PtcManager ptcManager;

    @Resource
    private PlatformReportClient platformReportClient;

    public void doProcess(UniformCrosschainPacketContext ucpContext) {

        if (ucpContext.getUcp().getSrcMessage().getType() == CrossChainMessage.CrossChainMessageType.AUTH_MSG) {
            IAuthMessage authMessage = AuthMessageFactory.createAuthMessage(ucpContext.getUcp().getSrcMessage().getMessage());
            if (crossChainMessageRepository.getAuthMessageState(ucpContext.getUcpId()) == AuthMsgProcessStateEnum.NOT_READY) {
                log.info("auth msg in ucp {} not ready, so skip validation process", ucpContext.getUcpId());
                return;
            }

            if (ucpContext.isEndorsedByTpBta()) {
                TpBtaDO tpBtaDO = ptcManager.getExactTpBta(ucpContext.getTpBtaLane(), ucpContext.getTpbtaVersion());
                if (ObjectUtil.isNull(tpBtaDO)) {
                    throw new UcpValidationException(
                            ucpContext.getUcpId(),
                            ucpContext.getSrcDomain(),
                            ucpContext.getTpbtaLaneKey(),
                            "none tpbta found"
                    );
                }

                IPTCService ptcService = ptcManager.getPtcService(tpBtaDO.getPtcServiceId());
                ValidatedConsensusState vcs = null;
                if (!ptcService.getPtcFeatureDescriptor().isStorageEnabled()) {
                    ValidatedConsensusStateDO validatedConsensusStateDO = blockchainRepository.getValidatedConsensusState(
                            tpBtaDO.getPtcServiceId(),
                            ucpContext.getSrcDomain(),
                            tpBtaDO.getCrossChainLane(),
                            ucpContext.getUcp().getSrcMessage().getProvableData().getHeightVal()
                    );
                    if (ObjectUtil.isNull(validatedConsensusStateDO)) {
                        throw new UcpValidationException(
                                ucpContext.getUcpId(),
                                ucpContext.getSrcDomain(),
                                ucpContext.getTpbtaLaneKey(),
                                StrUtil.format("none validated consensus state with height {} found", ucpContext.getUcp().getSrcMessage().getProvableData().getHeightVal().toString())
                        );
                    }
                    vcs = validatedConsensusStateDO.getValidatedConsensusState();
                }

                log.info("calling ptc {} to verify ucp {} with TpBTA {}", tpBtaDO.getPtcServiceId(), ucpContext.getUcpId(), ucpContext.getTpbtaLaneKey());
                PTCVerifyCrossChainMessageResult verifyResult = ptcService.verifyCrossChainMessageWithResult(
                        tpBtaDO.getTpbta(),
                        vcs,
                        ucpContext.getUcp(),
                        ucpContext.getUcpId()
                );
                ThirdPartyProof tpProof = verifyResult.getThirdPartyProof();
                if (ObjectUtil.isNull(tpProof)) {
                    throw new UcpValidationException(ucpContext.getUcpId(), ucpContext.getSrcDomain(), ucpContext.getTpbtaLaneKey(), "none tp proof found");
                }
                log.info("successful to verify ucp {} with TpBTA {}", ucpContext.getUcpId(), ucpContext.getTpbtaLaneKey());

                platformReportClient.reportRegulation(
                        ucpContext.getUcpId(),
                        verifyResult.getRegulationStatus(),
                        verifyResult.getRegulationReason()
                );
                ucpContext.getUcp().setTpProof(tpProof);
                crossChainMessageRepository.putTpProof(ucpContext.getUcpId(), ucpContext.getTpProof());
            }

            crossChainMessageRepository.updateUniformCrosschainPacketState(
                    ucpContext.getUcpId(),
                    UniformCrosschainPacketStateEnum.PROVED
            );

            if (AuthMessageV2.MY_VERSION == authMessage.getVersion()) {
                if (((AuthMessageV2) authMessage).getTrustLevel() != AuthMessageTrustLevelEnum.NEGATIVE_TRUST) {
                    log.debug("change no am state when trust level is not negative");
                    return;
                }
            }
            crossChainMessageRepository.updateAuthMessageState(
                    ucpContext.getUcpId(),
                    AuthMsgProcessStateEnum.PROVED
            );
        } else {
            crossChainMessageRepository.updateUniformCrosschainPacketState(
                    ucpContext.getUcpId(),
                    UniformCrosschainPacketStateEnum.PROVED
            );
        }

        log.info("successful to process validation of ucp {} ", ucpContext.getUcpId());
    }
}
