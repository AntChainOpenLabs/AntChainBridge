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

package com.alipay.antchain.bridge.plugins.mychain020;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.commons.core.base.ConsensusState;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.commons.core.bta.IBlockchainTrustAnchor;
import com.alipay.antchain.bridge.plugins.lib.HeteroChainDataVerifierService;
import com.alipay.antchain.bridge.plugins.mychain020.crypto.CryptoSuiteUtil;
import com.alipay.antchain.bridge.plugins.mychain020.exceptions.VerifyConsensusStateException;
import com.alipay.antchain.bridge.plugins.mychain020.model.ConsensusNodeInfo;
import com.alipay.antchain.bridge.plugins.mychain020.model.ContractAddressInfo;
import com.alipay.antchain.bridge.plugins.mychain020.model.CrossChainMsgLedgerData;
import com.alipay.antchain.bridge.plugins.mychain020.model.MychainSubjectIdentity;
import com.alipay.antchain.bridge.plugins.spi.ptc.AbstractHCDVSService;
import com.alipay.antchain.bridge.plugins.spi.ptc.core.VerifyResult;
import com.alipay.mychain.sdk.domain.block.BlockHeader;
import com.alipay.mychain.sdk.domain.spv.BlockProofInfo;

@HeteroChainDataVerifierService(pluginId = "plugin-mychain2", products = "mychain2")
public class MychainHcdvsService extends AbstractHCDVSService {

    @Override
    public VerifyResult verifyAnchorConsensusState(IBlockchainTrustAnchor bta, ConsensusState anchorState) {
        MychainSubjectIdentity subjectIdentity = MychainSubjectIdentity.decode(bta.getSubjectIdentity());
        ConsensusNodeInfo consensusNodeInfo = ConsensusNodeInfo.builder()
                .mychainHashType(CryptoSuiteUtil.getHashTypeEnum(subjectIdentity.getCryptoSuite()))
                .consensusNodePublicKeys(
                        subjectIdentity.getPoaCertsPubKeyHash().stream()
                                .map(HexUtil::encodeHexStr)
                                .collect(Collectors.toList())
                ).amContractIds(ContractAddressInfo.decode(new String(bta.getAmId())).toSet())
                .build();

        BlockProofInfo blockProofInfo = new BlockProofInfo();
        blockProofInfo.fromJson(JSON.parseObject(new String(anchorState.getEndorsements())));
        BlockHeader blockHeader = new BlockHeader();
        blockHeader.fromJson(JSON.parseObject(new String(anchorState.getStateData())));
        if (!consensusNodeInfo.verifyConsensusNodeSigs(
                blockProofInfo,
                blockHeader,
                getHCDVSLogger()
        )) {
            getHCDVSLogger().error("consensus node sigs verify failed for anchor state verification with height {}",
                    anchorState.getHeight().toString());
            return VerifyResult.fail("consensus node sigs verify failed");
        }

        anchorState.setConsensusNodeInfo(consensusNodeInfo.encode());

        return VerifyResult.success();
    }

    @Override
    public VerifyResult verifyConsensusState(ConsensusState stateToVerify, ConsensusState parentState) {
        try {
            ConsensusNodeInfo parentConsensusNodeInfo = ConsensusNodeInfo.decode(parentState.getConsensusNodeInfo());

            BlockProofInfo blockProofInfo = new BlockProofInfo();
            blockProofInfo.fromJson(JSON.parseObject(new String(stateToVerify.getEndorsements())));

            BlockHeader blockHeader = new BlockHeader();
            blockHeader.fromJson(JSON.parseObject(new String(stateToVerify.getStateData())));

            if (!StrUtil.equalsIgnoreCase(blockHeader.getHash().hexStrValue(), stateToVerify.getHashHex())) {
                getHCDVSLogger().error("block hash from header {} not equal with {} in consensus state {}",
                        blockHeader.getHash().toString(), stateToVerify.getHashHex(), stateToVerify.getHeight().toString());
                return VerifyResult.fail("block hash not equal with consensus state");
            }
            if (!StrUtil.equalsIgnoreCase(blockHeader.getParentHash().hexStrValue(), parentState.getHashHex())) {
                getHCDVSLogger().error("parent hash from header {} not equal with {} in parent consensus state {}",
                        blockHeader.getParentHash().toString(), parentState.getHashHex(), stateToVerify.getHeight().toString());
                return VerifyResult.fail("parent hash not equal with parent consensus state");
            }
            if (!blockHeader.getNumber().equals(stateToVerify.getHeight())) {
                getHCDVSLogger().error("block height from header {} not equal with {} in consensus state",
                        blockHeader.getNumber().toString(), stateToVerify.getHeight().toString());
                return VerifyResult.fail("block height not equal with consensus state");
            }

            if (!parentConsensusNodeInfo.verifyConsensusNodeSigs(blockProofInfo, blockHeader, getHCDVSLogger())) {
                getHCDVSLogger().error("consensus node sigs verify failed for curr state verification with height {}",
                        stateToVerify.getHeight().toString());
                return VerifyResult.fail("consensus node sigs verify failed");
            }

            ConsensusNodeInfo currConsensusNodeInfo = ConsensusNodeInfo.decode(stateToVerify.getConsensusNodeInfo());
            Set<String> currAmContractIds = normalizeAmContractIds(currConsensusNodeInfo.getAmContractIds());
            Set<String> parentAmContractIds = normalizeAmContractIds(parentConsensusNodeInfo.getAmContractIds());
            if (CollectionUtil.isEmpty(currAmContractIds) || CollectionUtil.isEmpty(parentAmContractIds)) {
                getHCDVSLogger().error("am contract ids are empty in current or parent consensus state");
                return VerifyResult.fail("am contract ids are empty");
            }
            if (!currAmContractIds.equals(parentAmContractIds)) {
                getHCDVSLogger().error("am contract ids {} not equal with {} from parent consensus state",
                        StrUtil.join(",", currAmContractIds),
                        StrUtil.join(",", parentAmContractIds)
                );
                return VerifyResult.fail("am contract ids not equal with parent consensus state");
            }

            currConsensusNodeInfo.setAmContractIds(currAmContractIds);
            currConsensusNodeInfo.setConsensusNodePublicKeys(parentConsensusNodeInfo.getConsensusNodePublicKeys());
            if (ObjectUtil.isNotEmpty(currConsensusNodeInfo.getTransactionReceipts())) {
                currConsensusNodeInfo.processConsensusUpdate(blockHeader, getHCDVSLogger());
                currConsensusNodeInfo.setTransactionReceipts(null);
                currConsensusNodeInfo.setTransactions(null);
            }

            stateToVerify.setConsensusNodeInfo(currConsensusNodeInfo.encode());

            return VerifyResult.success();
        } catch (VerifyConsensusStateException e) {
            getHCDVSLogger().error("failed to verify consensus state :", e);
            return VerifyResult.fail(e.getMessage());
        } catch(Throwable t) {
            getHCDVSLogger().error("verify consensus state (height: {}, hash: {}) failed with unexpected exception: ",
                    stateToVerify.getHeight().toString(), stateToVerify.getHashHex(), t);
            return VerifyResult.fail("verify consensus state failed with unexpected exception");
        }
    }

    @Override
    public VerifyResult verifyCrossChainMessage(CrossChainMessage message, ConsensusState currState) {
        try {
            BlockHeader blockHeader = new BlockHeader();
            blockHeader.fromJson(JSON.parseObject(new String(currState.getStateData())));

            ConsensusNodeInfo.decode(currState.getConsensusNodeInfo()).processReceiptVerification(
                    blockHeader,
                    CrossChainMsgLedgerData.decode(message.getProvableData().getLedgerData()),
                    message.getProvableData().getProof(),
                    getHCDVSLogger()
            );
        } catch (VerifyConsensusStateException e) {
            getHCDVSLogger().error("failed to verify cross chain message :", e);
            return VerifyResult.fail(e.getMessage());
        } catch(Throwable t) {
            getHCDVSLogger().error("unexpected error: ", t);
            return VerifyResult.fail("verify cross chain message failed with unexpected exception");
        }

        return VerifyResult.success();
    }

    private Set<String> normalizeAmContractIds(Set<String> amContractIds) {
        if (amContractIds == null) {
            return Collections.emptySet();
        }
        return amContractIds.stream()
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
    }

    @Override
    public byte[] parseMessageFromLedgerData(byte[] ledgerData) {
        if (ObjectUtil.isEmpty(ledgerData)) {
            throw new IllegalArgumentException("ledgerData is null or empty");
        }

        return CrossChainMsgLedgerData.decode(ledgerData).getCrossChainMessage();
    }
}
