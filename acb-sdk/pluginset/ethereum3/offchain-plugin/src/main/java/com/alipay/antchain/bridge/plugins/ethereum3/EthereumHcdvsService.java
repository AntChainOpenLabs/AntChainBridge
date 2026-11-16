package com.alipay.antchain.bridge.plugins.ethereum3;

import java.math.BigInteger;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.commons.core.base.ConsensusState;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.commons.core.bta.IBlockchainTrustAnchor;
import com.alipay.antchain.bridge.plugins.ethereum3.core.*;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.EthReceiptProof;
import com.alipay.antchain.bridge.plugins.lib.HeteroChainDataVerifierService;
import com.alipay.antchain.bridge.plugins.spi.ptc.AbstractHCDVSService;
import com.alipay.antchain.bridge.plugins.spi.ptc.core.VerifyResult;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.web3j.utils.Numeric;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@HeteroChainDataVerifierService(pluginId = "plugin-ethereum3", products = "ethereum3")
public class EthereumHcdvsService extends AbstractHCDVSService {

    @Override
    public VerifyResult verifyAnchorConsensusState(IBlockchainTrustAnchor bta, ConsensusState anchorState) {
        getHCDVSLogger().info("verify anchor consensus state ⚓️ (slot: {}, hash: {}) for domain {} now!",
                anchorState.getHeight().toString(), anchorState.getHashHex(), bta.getDomain().toString());

        var ethSubjectIdentity = EthSubjectIdentity.fromJson(new String(bta.getSubjectIdentity()));
        if (ethSubjectIdentity.getEth2ChainConfig().getSyncCommitteeSize() != ethSubjectIdentity.getCurrentSyncCommittee().getPubkeys().size()) {
            return VerifyResult.fail("sync committee size not match with the one in eth2 config");
        }

        var ethConsensusStateData = EthConsensusStateData.fromJson(
                new String(anchorState.getStateData()),
                ethSubjectIdentity.getEth2ChainConfig().getCurrentSchemaDefinitions(anchorState.getHeight()),
                ethSubjectIdentity.getEth2ChainConfig().getSpecConfig()
        );
        if (!Address.wrap(Bytes.wrap(ArrayUtil.sub(bta.getAmId(), 12, 32))).equals(ethConsensusStateData.getAmContract())) {
            getHCDVSLogger().error("am contract address {} in consensus state data not match with the one in BTA {}",
                    ethConsensusStateData.getAmContract(), Address.wrap(Bytes32.wrap(bta.getAmId())).toHexString());
            return VerifyResult.fail("invalid am contract");
        }

        var ethEndorsements = EthConsensusEndorsements.fromJson(
                new String(anchorState.getEndorsements()),
                ethSubjectIdentity.getCurrentSyncCommittee().getPubkeys().size()
        );
        try {
            ethConsensusStateData.validate(ethSubjectIdentity.getCurrentSyncCommittee(), ethEndorsements, ethSubjectIdentity.getEth2ChainConfig());
        } catch (InvalidConsensusDataException e) {
            getHCDVSLogger().error("failed to verify eth consensus state data (slot: {}, hash: {}) for domain {}",
                    anchorState.getHeight().toString(), anchorState.getHashHex(), bta.getDomain().toString(), e);
            return VerifyResult.fail("failed to verify eth consensus state data: {}", e.getMessage());
        }

        getHCDVSLogger().info("successful to verify anchor consensus state ⚓️ (slot: {}, hash: {}) for domain {} now!",
                anchorState.getHeight().toString(), anchorState.getHashHex(), bta.getDomain().toString());
        if (ethConsensusStateData.getLightClientUpdateWrapper() != null) {
            getHCDVSLogger().info("light client update inside anchor consensus state, update the sync committee");
            ethSubjectIdentity.setCurrentSyncCommittee(ethConsensusStateData.getLightClientUpdateWrapper().getNextSyncCommittee());
        }
        anchorState.setConsensusNodeInfo(ethSubjectIdentity.toJson().getBytes());
        return VerifyResult.success();
    }

    @Override
    public VerifyResult verifyConsensusState(ConsensusState stateToVerify, ConsensusState parentState) {
        getHCDVSLogger().info("verify consensus state (slot: {}, root: {}) now!", stateToVerify.getHeight().toString(), stateToVerify.getHashHex());

        var ethSubjectIdentity = EthSubjectIdentity.fromJson(new String(parentState.getConsensusNodeInfo()));
        var ethConsensusStateData = EthConsensusStateData.fromJson(
                new String(stateToVerify.getStateData()),
                ethSubjectIdentity.getEth2ChainConfig().getCurrentSchemaDefinitions(stateToVerify.getHeight()),
                ethSubjectIdentity.getEth2ChainConfig().getSpecConfig()
        );
        var parentConsensusData = EthConsensusStateData.fromJson(
                new String(parentState.getStateData()),
                ethSubjectIdentity.getEth2ChainConfig().getCurrentSchemaDefinitions(stateToVerify.getHeight()),
                ethSubjectIdentity.getEth2ChainConfig().getSpecConfig()
        );

        if (!parentConsensusData.getAmContract().equals(ethConsensusStateData.getAmContract())) {
            getHCDVSLogger().error("am contract address {} in curr consensus state data not match with the one in parent {}",
                    ethConsensusStateData.getAmContract().toString(), parentConsensusData.getAmContract().toString());
            return VerifyResult.fail("am contract not equal");
        }

        if (ObjectUtil.isNull(ethConsensusStateData.getBeaconBlockHeader())) {
            getHCDVSLogger().info("😈 slot {} is missed on ethereum, just pass it", stateToVerify.getHeight().toString());
            // inherit the state data from last not missed block.
            stateToVerify.setStateData(parentState.getStateData());
        } else {
            var currSlot = ethConsensusStateData.getBeaconBlockHeader().getSlot();
            if (!currSlot.equals(UInt64.valueOf(stateToVerify.getHeight()))) {
                getHCDVSLogger().error("❌ beacon block has different slot {} with number {} in consensus state",
                        currSlot, stateToVerify.getHeight().toString());
                return VerifyResult.fail("invalid slot");
            }

            var currBlockRoot = ethConsensusStateData.getBeaconBlockHeader().getRoot();
            if (currBlockRoot.compareTo(Bytes32.wrap(stateToVerify.getHash())) != 0) {
                getHCDVSLogger().error("❌ block at slot {} has different block root {} with root {} in current state",
                        stateToVerify.getHeight().toString(), currBlockRoot.toHexString(), stateToVerify.getHashHex());
                return VerifyResult.fail("invalid block root");
            }
            var parentRootExpected = parentConsensusData.getBeaconBlockHeader().getRoot();
            var parentRoot = ethConsensusStateData.getBeaconBlockHeader().getParentRoot();
            if (parentRootExpected.compareTo(parentRoot) != 0) {
                getHCDVSLogger().error("❌ block at slot {} has different parent root {} with block root {} of parent state at slot {}",
                        stateToVerify.getHeight().toString(), parentRoot.toHexString(),
                        parentRootExpected.toHexString(), parentConsensusData.getBeaconBlockHeader().getSlot().toString());
                return VerifyResult.fail("invalid parent hash");
            }

            var ethEndorsements = EthConsensusEndorsements.fromJson(
                    new String(stateToVerify.getEndorsements()),
                    ethSubjectIdentity.getCurrentSyncCommittee().getPubkeys().size()
            );
            try {
                ethConsensusStateData.validate(ethSubjectIdentity.getCurrentSyncCommittee(), ethEndorsements, ethSubjectIdentity.getEth2ChainConfig());
            } catch (InvalidConsensusDataException e) {
                getHCDVSLogger().error("❌ failed to verify eth consensus state data (slot: {}, hash: {})",
                        stateToVerify.getHeight().toString(), stateToVerify.getHashHex(), e);
                return VerifyResult.fail("failed to verify eth consensus state data: {}", e.getMessage());
            }
        }

        if (ethConsensusStateData.isLastSlotForCurrentPeriod(ethSubjectIdentity.getEth2ChainConfig().getSyncPeriodLength())) {
            if (ethConsensusStateData.getLightClientUpdateWrapper() == null) {
                getHCDVSLogger().error("❌ has none light client update for the last slot {} for current period {}",
                        ethConsensusStateData.getBeaconBlockHeader().getSlot().toString(),
                        ethConsensusStateData.getCurrSyncPeriod(ethSubjectIdentity.getEth2ChainConfig().getSyncPeriodLength())
                );
                return VerifyResult.fail("none light client update at last slot in period");
            }
            getHCDVSLogger().info("🗳️ last slot {} for current period {}, update the sync committee",
                    ethConsensusStateData.getBeaconBlockHeader().getSlot().toString(),
                    ethConsensusStateData.getCurrSyncPeriod(ethSubjectIdentity.getEth2ChainConfig().getSyncPeriodLength())
            );
            ethSubjectIdentity.setCurrentSyncCommittee(ethConsensusStateData.getLightClientUpdateWrapper().getNextSyncCommittee());
        }

        stateToVerify.setConsensusNodeInfo(ethSubjectIdentity.toJson().getBytes());

        getHCDVSLogger().info("🌈 successful to verify consensus state (slot: {}, root: {}) now!",
                stateToVerify.getHeight().toString(), stateToVerify.getHashHex());
        return VerifyResult.success();
    }

    @Override
    public VerifyResult verifyCrossChainMessage(CrossChainMessage message, ConsensusState currState) {
        if (new BigInteger(currState.getHash()).equals(BigInteger.ZERO)) {
            getHCDVSLogger().error("curr state's slot is missed, where ccmsg from ? 🤔");
            return VerifyResult.fail("slot is missed");
        }

        getHCDVSLogger().info("👀 verify the crosschain msg with txhash {} at consensus state {} from blockchain {} now !",
                Numeric.toHexString(message.getProvableData().getTxHash()), message.getProvableData().getHeight(), currState.getDomain());

        var ethReceiptProof = EthReceiptProof.decodeFromJson(new String(message.getProvableData().getProof()));

        var ethSubjectIdentity = EthSubjectIdentity.fromJson(new String(currState.getConsensusNodeInfo()));
        var ethConsensusStateData = EthConsensusStateData.fromJson(
                new String(currState.getStateData()),
                ethSubjectIdentity.getEth2ChainConfig().getCurrentSchemaDefinitions(currState.getHeight()),
                ethSubjectIdentity.getEth2ChainConfig().getSpecConfig()
        );

        var rootCalc = ethReceiptProof.validateAndGetRoot();
        var rootExpect = ethConsensusStateData.getExecutionPayloadHeader().getReceiptsRoot();
        if (rootCalc.compareTo(rootExpect) != 0) {
            getHCDVSLogger().error("❌ receipt root {} not equal to root {} in exec payload header at slot {}",
                    rootCalc.toHexString(), rootExpect.toHexString(), ethConsensusStateData.getBeaconBlockHeader().getSlot().toString());
            return VerifyResult.fail("receipt root not equal");
        }

        try {
            var ledgerLog = EthAuthMessageLog.decodeFromJson(new String(message.getProvableData().getLedgerData()));
            if (ledgerLog == null || ledgerLog.getSendAuthMessageLog() == null
                    || !BigInteger.valueOf(ethReceiptProof.getReceiptIndex()).equals(
                            ledgerLog.getSendAuthMessageLog().getTransactionIndex())) {
                return VerifyResult.fail("receipt transaction index does not match ledger event");
            }
            ledgerLog.verifyReceiptLog(ethReceiptProof.getEthTransactionReceipt().getLogs(),
                    ethConsensusStateData.getAmContract(), message.getMessage());
        } catch (RuntimeException e) {
            // Malformed/ambiguous ledger data must fail verification, never fall back to RPC or success.
            return VerifyResult.fail("invalid receipt event: {}", e.getMessage());
        }

        getHCDVSLogger().info("🌈 crosschain message (slot: {}, txhash: {}) pass the verification",
                message.getProvableData().getHeight(), Numeric.toHexString(message.getProvableData().getTxHash()));

        return VerifyResult.success();
    }

    @Override
    public byte[] parseMessageFromLedgerData(byte[] ledgerData) {
        return EthAuthMessageLog.decodeFromJson(new String(ledgerData)).decodeMessage();
    }
}
