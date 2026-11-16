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

package com.alipay.antchain.bridge.relayer.core.service.anchor.workers;

import java.math.BigInteger;

import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.base.ConsensusState;
import com.alipay.antchain.bridge.commons.core.bta.IBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ValidatedConsensusState;
import com.alipay.antchain.bridge.ptc.service.IPTCService;
import com.alipay.antchain.bridge.relayer.commons.exception.PtcVerifyConsensusStateException;
import com.alipay.antchain.bridge.relayer.commons.model.BtaDO;
import com.alipay.antchain.bridge.relayer.commons.model.TpBtaDO;
import com.alipay.antchain.bridge.relayer.commons.model.ValidatedConsensusStateDO;
import com.alipay.antchain.bridge.relayer.core.service.anchor.context.AnchorProcessContext;
import com.alipay.antchain.bridge.relayer.core.types.blockchain.AbstractBlock;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsensusStateWorker extends BlockWorker {

    public ConsensusStateWorker(AnchorProcessContext processContext) {
        super(processContext);
    }

    @Override
    public boolean process(AbstractBlock block, TpBtaDO tpBtaDO) {
        if (ObjectUtil.isNull(tpBtaDO)) {
            log.info("blockchain {}-{} has non-endorsement, skip consensus state process",
                    getProcessContext().getAnchorProduct(), getProcessContext().getAnchorBlockchainId());
            return true;
        }
        if (ObjectUtil.isNull(block.getConsensusState())) {
            log.error("block {} has no consensus state with it for blockchain {}-{}", block.getHeight(), block.getProduct(), block.getBlockchainId());
            return false;
        }

        try {
            if (!getProcessContext().getPtcManager().isPtcServiceWorking(tpBtaDO.getPtcServiceId())) {
                throw new PtcVerifyConsensusStateException("ptc service {} is not working", tpBtaDO.getPtcServiceId());
            }

            IPTCService ptcService = getProcessContext().getPtcManager().getPtcService(tpBtaDO.getPtcServiceId());
            if (ObjectUtil.isNull(ptcService)) {
                throw new PtcVerifyConsensusStateException("ptc service stub {} is null", tpBtaDO.getPtcServiceId());
            }

            restoreAnchorStateIfNecessary(block, tpBtaDO, ptcService);

            ValidatedConsensusStateDO parentVcs = ValidatedConsensusStateDO.builder().build();
            if (!ptcService.getPtcFeatureDescriptor().isStorageEnabled()) {
                parentVcs = getProcessContext().getBlockchainRepository().getValidatedConsensusState(
                        tpBtaDO.getPtcServiceId(),
                        block.getDomain(),
                        tpBtaDO.getCrossChainLane(),
                        block.getConsensusState().getParentHashHex()
                );
                if (ObjectUtil.isNull(parentVcs)) {
                    throw new PtcVerifyConsensusStateException(
                            "parent consensus state {} is not found for domain {} and ptc {}",
                            block.getConsensusState().getParentHashHex(), block.getDomain(), tpBtaDO.getPtcServiceId()
                    );
                }
            }

            ValidatedConsensusState vcs = ptcService.commitConsensusState(tpBtaDO.getTpbta(), parentVcs.getValidatedConsensusState(), block.getConsensusState());
            if (ObjectUtil.isNull(vcs)) {
                throw new PtcVerifyConsensusStateException(
                        "null validated consensus for domain {} and height {} state from {}",
                        block.getDomain(), block.getHeight(), tpBtaDO.getPtcServiceId()
                );
            }

            if (!ptcService.getPtcFeatureDescriptor().isStorageEnabled()) {
                log.debug("ptc service {} is not storage enabled", tpBtaDO.getPtcServiceId());
                log.info("save validated consensus state {}-{} for domain {} from ptc service {} with TpBTA {}",
                        vcs.getHeight().toString(),
                        vcs.getHashHex(),
                        vcs.getDomain().toString(),
                        tpBtaDO.getPtcServiceId(),
                        tpBtaDO.getCrossChainLane().getLaneKey()
                );
                getProcessContext().getBlockchainRepository().setValidatedConsensusState(
                        ValidatedConsensusStateDO.builder()
                                .blockchainProduct(block.getProduct())
                                .blockchainId(block.getBlockchainId())
                                .ptcServiceId(tpBtaDO.getPtcServiceId())
                                .tpbtaLane(tpBtaDO.getCrossChainLane())
                                .validatedConsensusState(vcs)
                                .build()
                );
            }

            log.info("successful to verify consensus state {}-{} for domain {} from ptc service {} with TpBTA {}",
                    block.getHeight(), block.getConsensusState().getHashHex(), block.getDomain(), tpBtaDO.getPtcServiceId(),
                    tpBtaDO.getCrossChainLane().getLaneKey());
        } catch (Exception e) {
            log.error("verify consensus stat {} for domain {} from ptc service {} with TpBTA {} failed: ",
                    block.getHeight(),
                    block.getDomain(),
                    tpBtaDO.getPtcServiceId(),
                    tpBtaDO.getCrossChainLane().getLaneKey(),
                    e
            );
            return false;
        }

        return true;
    }

    /**
     * A storage-enabled PTC keeps the verified consensus-state chain on the PTC node. If that
     * runtime storage is restored from an incomplete snapshot while the relayer database keeps
     * its anchor progress, the first post-BTA block can no longer be verified because its parent
     * (the BTA anchor state) is missing.
     *
     * Re-submitting the BTA anchor is safe and idempotent on committee PTC nodes. Limit the repair
     * strictly to {@code initHeight + 1}; a missing parent at any later height is a real continuity
     * error and must not be hidden by rewinding or fabricating state.
     */
    private void restoreAnchorStateIfNecessary(AbstractBlock block, TpBtaDO tpBtaDO, IPTCService ptcService) {
        if (!ptcService.getPtcFeatureDescriptor().isStorageEnabled()) {
            return;
        }

        BtaDO btaDO = getProcessContext().getBlockchainRepository().getBta(new CrossChainDomain(block.getDomain()));
        if (ObjectUtil.isNull(btaDO) || ObjectUtil.isNull(btaDO.getBta())) {
            throw new PtcVerifyConsensusStateException("bta is not found for domain {}", block.getDomain());
        }

        IBlockchainTrustAnchor bta = btaDO.getBta();
        if (bta.getSubjectVersion() != tpBtaDO.getBtaSubjectVersion()) {
            throw new PtcVerifyConsensusStateException(
                    "bta subject version {} does not match tpbta subject version {} for domain {}",
                    bta.getSubjectVersion(), tpBtaDO.getBtaSubjectVersion(), block.getDomain()
            );
        }
        if (!BigInteger.valueOf(block.getHeight()).equals(bta.getInitHeight().add(BigInteger.ONE))) {
            return;
        }

        ConsensusState anchorState = getProcessContext().getBlockchainClient().getConsensusState(bta.getInitHeight());
        if (ObjectUtil.isNull(anchorState)) {
            throw new PtcVerifyConsensusStateException(
                    "failed to query BTA anchor consensus state at height {} for domain {}",
                    bta.getInitHeight(), block.getDomain()
            );
        }
        ValidatedConsensusState anchorVcs = ptcService.commitAnchorState(bta, tpBtaDO.getTpbta(), anchorState);
        if (ObjectUtil.isNull(anchorVcs)) {
            throw new PtcVerifyConsensusStateException(
                    "ptc {} returned null while restoring BTA anchor state at height {} for domain {}",
                    tpBtaDO.getPtcServiceId(), bta.getInitHeight(), block.getDomain()
            );
        }
        log.info(
                "ensured BTA anchor consensus state {}-{} for domain {} on storage-enabled ptc {}",
                anchorState.getHeight(), anchorState.getHashHex(), block.getDomain(), tpBtaDO.getPtcServiceId()
        );
    }
}
