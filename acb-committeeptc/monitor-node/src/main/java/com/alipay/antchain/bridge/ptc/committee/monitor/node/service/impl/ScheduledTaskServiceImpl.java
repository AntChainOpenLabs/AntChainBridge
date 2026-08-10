/*
 * Copyright 2024 Ant Group
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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.service.impl;

import java.math.BigInteger;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.exception.CommitteeNodeInternalException;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.DomainSpaceCertWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.ISystemConfigRepository;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IBCDNSManageService;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IScheduledTaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ScheduledTaskServiceImpl implements IScheduledTaskService {

    @Resource
    private IBCDNSManageService bcdnsManageService;

    @Resource
    private AbstractCrossChainCertificate ptcCrossChainCert;

    @Resource
    private ISystemConfigRepository systemConfigRepository;

    @Override
    @Scheduled(fixedDelayString = "${committee.node.schedule.ptc-trust-root-listen.fixed-delay:60000}")
    public void listenPtcTrustRoot() {
        try {
            if (bcdnsManageService.countBCDNSService() == 0) {
                log.info("have no bcdns service, please add one at least");
                return;
            }

            DomainSpaceCertWrapper domainSpaceCertWrapper = bcdnsManageService.getDomainSpaceCert(ptcCrossChainCert.getIssuer());
            if (ObjectUtil.isNull(domainSpaceCertWrapper)) {
                throw new CommitteeNodeInternalException(
                        "No domain space cert found for issuer {}", HexUtil.encodeHexStr(ptcCrossChainCert.getIssuer().encode())
                );
            }
            PTCTrustRoot ptcTrustRoot = bcdnsManageService.getBCDNSClient(domainSpaceCertWrapper.getDomainSpace())
                    .queryPTCTrustRoot(ptcCrossChainCert.getCredentialSubjectInstance().getApplicant());
            if (ObjectUtil.isNull(ptcTrustRoot)) {
                throw new CommitteeNodeInternalException("No ptc trust root found");
            }

            BigInteger currVer = systemConfigRepository.queryCurrentPtcAnchorVersion();
            BigInteger verOnBcdns = ptcTrustRoot.getVerifyAnchorMap().keySet().stream().max(BigInteger::compareTo).orElse(BigInteger.ZERO);
            if (currVer.compareTo(verOnBcdns) >= 0) {
                log.debug("No new ptc trust root found");
                return;
            }
            log.info("New ptc trust root found, new verify anchors from version {} to {}", currVer, verOnBcdns);
            systemConfigRepository.setPtcTrustRoot(ptcTrustRoot);
        } catch (Throwable t) {
            log.error("Failed to listen ptc trust root", t);
        }
    }
}
