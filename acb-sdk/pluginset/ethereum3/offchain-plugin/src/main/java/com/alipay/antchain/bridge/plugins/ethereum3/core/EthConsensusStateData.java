package com.alipay.antchain.bridge.plugins.ethereum3.core;

import java.util.ArrayList;
import java.util.List;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.LightClientUpdateWrapper;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec.Eth2ChainConfig;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec.Eth2ConstantParams;
import lombok.*;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateSchema;
import tech.pegasys.teku.spec.datastructures.state.SigningData;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class EthConsensusStateData {

    public static EthConsensusStateData fromJson(String value, SchemaDefinitions schemaDefinitions, SpecConfigAltair specConfigAltair) {
        var ethConsensusStateData = new EthConsensusStateData();
        var jsonObject = JSON.parseObject(value);
        try {
            if (jsonObject.containsKey("beacon_block_header")) {
                ethConsensusStateData.setBeaconBlockHeader(
                        JsonUtil.parse(jsonObject.getString("beacon_block_header"), BeaconBlockHeader.SSZ_SCHEMA.getJsonTypeDefinition())
                );
            }
            if (jsonObject.containsKey("execution_payload_header")) {
                var bodySchema = schemaDefinitions.getBlindedBeaconBlockBodySchema();
                ethConsensusStateData.setExecutionPayloadHeader(
                        (ExecutionPayloadHeader) JsonUtil.parse(jsonObject.getString("execution_payload_header"),
                                bodySchema.getChildSchema(bodySchema.getFieldIndex(BlockBodyFields.EXECUTION_PAYLOAD_HEADER)).getJsonTypeDefinition())
                );
            }
            if (jsonObject.containsKey("execution_payload_branches")) {
                var branches = jsonObject.getJSONArray("execution_payload_branches");
                var executionPayloadHeaderBranches = new ArrayList<Bytes32>();
                for (int i = 0; i < branches.size(); i++) {
                    executionPayloadHeaderBranches.add(Bytes32.fromHexString(branches.getString(i)));
                }
                ethConsensusStateData.setExecutionPayloadBranches(executionPayloadHeaderBranches);
            }
            if (jsonObject.containsKey("light_client_update")) {
                ethConsensusStateData.setLightClientUpdateWrapper(
                        new LightClientUpdateWrapper(
                                JsonUtil.parse(jsonObject.getString("light_client_update"), new LightClientUpdateSchema(specConfigAltair).getJsonTypeDefinition())
                        )
                );
            }
            if (jsonObject.containsKey("am_contract")) {
                ethConsensusStateData.setAmContract(Address.fromHexString(jsonObject.getString("am_contract")));
            }
            return ethConsensusStateData;
        } catch (Exception e) {
            throw new RuntimeException("parse consensus state data error", e);
        }
    }

    private BeaconBlockHeader beaconBlockHeader;

    /**
     * ExecutionPayload has all exec layer transactions data, it causes space and time waste.
     * Maybe find a way to exclude the transaction data off
     */
    private ExecutionPayloadHeader executionPayloadHeader;

    private List<Bytes32> executionPayloadBranches;

    private LightClientUpdateWrapper lightClientUpdateWrapper;

    private Address amContract;

    public void setAmContractHex(String contractHex) {
        this.amContract = Address.fromHexString(contractHex);
    }

    public void validate(SyncCommittee currSyncCommittee, EthConsensusEndorsements endorsements, Eth2ChainConfig eth2ChainConfig) {
        if (ObjectUtil.isNotNull(this.beaconBlockHeader)) {
            var schemaDefinitions = eth2ChainConfig.getCurrentSchemaDefinitions(this.beaconBlockHeader.getSlot().bigIntegerValue());
            var bodySchema = schemaDefinitions.getBlindedBeaconBlockBodySchema();

            var proof = SszBytes32VectorSchema.create(this.executionPayloadBranches.size())
                    .createFromElements(this.executionPayloadBranches.stream().map(SszBytes32::of).toList());
            if (!new Predicates(null).isValidMerkleBranch(
                    this.executionPayloadHeader.hashTreeRoot(),
                    proof,
                    proof.size(),
                    bodySchema.getFieldIndex(BlockBodyFields.EXECUTION_PAYLOAD_HEADER),
                    this.getBeaconBlockHeader().getBodyRoot()
            )) {
                throw new InvalidConsensusDataException("execution payload header merkle proof is invalid");
            }

            if (currSyncCommittee.getPubkeys().size() != endorsements.getSyncAggregate().getSyncCommitteeBits().size()) {
                throw new InvalidConsensusDataException("sync aggregate bits' size is not equal to current sync committee size");
            }
            var threshold = (endorsements.getSyncAggregate().getSyncCommitteeBits().size() * 2 + 2) / 3;
            if (endorsements.getSyncAggregate().getSyncCommitteeBits().stream().filter(AbstractSszPrimitive::get).count() < threshold) {
                throw new InvalidConsensusDataException("sync committee signature is not enough");
            }

            List<BLSPublicKey> contributionPubkeys = new ArrayList<>();
            for (int i = 0; i < currSyncCommittee.getPubkeys().size(); i++) {
                if (endorsements.getSyncAggregate().getSyncCommitteeBits().getBit(i)) {
                    contributionPubkeys.add(currSyncCommittee.getPubkeys().get(i).getBLSPublicKey());
                }
            }

            var signingRoot = new SigningData(
                    this.beaconBlockHeader.getRoot(),
                    Bytes32.wrap(eth2ChainConfig.getForkBySlot(this.beaconBlockHeader.getSlot().bigIntegerValue()).getDomain())
            ).hashTreeRoot();

            if (!BLSSignatureVerifier.SIMPLE.verify(
                    contributionPubkeys,
                    signingRoot,
                    endorsements.getSyncAggregate().getSyncCommitteeSignature().getSignature()
            )) {
                throw new InvalidConsensusDataException("sync committee signature is invalid");
            }
        }

        if (lightClientUpdateWrapper != null) {
            validateLightClientUpdate(lightClientUpdateWrapper, currSyncCommittee, eth2ChainConfig);
        }
    }

    public boolean isLastSlotForCurrentPeriod(long syncPeriodLength) {
        return this.beaconBlockHeader.getSlot().mod(syncPeriodLength).equals(UInt64.ZERO);
    }

    public UInt64 getCurrSyncPeriod(long syncPeriodLength) {
        return this.beaconBlockHeader.getSlot().dividedBy(syncPeriodLength);
    }

    @SneakyThrows
    public String toJson() {
        JSONObject jsonObject = new JSONObject();
        if (beaconBlockHeader != null) {
            jsonObject.put("beacon_block_header", JsonUtil.serialize(beaconBlockHeader, beaconBlockHeader.getSchema().getJsonTypeDefinition()));
        }
        if (executionPayloadHeader != null) {
            jsonObject.put("execution_payload_header", JsonUtil.serialize(executionPayloadHeader, ((ExecutionPayloadHeaderSchema) executionPayloadHeader.getSchema()).getJsonTypeDefinition()));
        }
        if (executionPayloadBranches != null) {
            var jsonArr = new JSONArray();
            executionPayloadBranches.forEach(x -> jsonArr.add(x.toHexString()));
            jsonObject.put("execution_payload_branches", jsonArr);
        }
        if (lightClientUpdateWrapper != null) {
            jsonObject.put("light_client_update", lightClientUpdateWrapper.toJson());
        }
        if (amContract != null) {
            jsonObject.put("am_contract", amContract.toHexString());
        }
        return jsonObject.toJSONString();
    }

    private void validateLightClientUpdate(
            LightClientUpdateWrapper lightClientUpdate,
            SyncCommittee currentSyncCommittee,
            Eth2ChainConfig eth2ChainConfig
    ) {
        var attestedHeader = lightClientUpdate.getAttestedHeader();
        var sigPeriod = lightClientUpdate.getSignatureSlot().dividedBy(eth2ChainConfig.getSyncPeriodLength());
        var attestedHeaderPeriod = attestedHeader.getBeacon().getSlot().dividedBy(eth2ChainConfig.getSyncPeriodLength());
        if (!sigPeriod.equals(attestedHeaderPeriod)) {
            throw new InvalidConsensusDataException("sig slot period is not equal to attested header period");
        }
        var currPeriod = this.beaconBlockHeader.getSlot().dividedBy(eth2ChainConfig.getSyncPeriodLength());
        if (!sigPeriod.equals(currPeriod)) {
            throw new InvalidConsensusDataException("sig slot period is not equal to current committee period");
        }

        var nextSyncCommittee = lightClientUpdate.getNextSyncCommittee();
        var syncAggregate = lightClientUpdate.getSyncAggregate();
        List<BLSPublicKey> contributionPubkeys = new ArrayList<>();
        for (int i = 0; i < currentSyncCommittee.getPubkeys().size(); i++) {
            if (syncAggregate.getSyncCommitteeBits().getBit(i)) {
                contributionPubkeys.add(currentSyncCommittee.getPubkeys().get(i).getBLSPublicKey());
            }
        }

        var nextSyncCommitteeBranch = lightClientUpdate.getNextSyncCommitteeBranch();
        if (!new Predicates(null).isValidMerkleBranch(
                nextSyncCommittee.hashTreeRoot(),
                nextSyncCommitteeBranch,
                nextSyncCommitteeBranch.size(),
                Eth2ConstantParams.STATE_INDEX_NEXT_SYNC_COMMITTEE,
                attestedHeader.getBeacon().getStateRoot()
        )) {
            throw new InvalidConsensusDataException("next sync committee branches is invalid");
        }

        // verify sync committee signature
        var signingRoot = new SigningData(
                attestedHeader.hashTreeRoot(),
                Bytes32.wrap(eth2ChainConfig.getForkBySlot(this.beaconBlockHeader.getSlot().bigIntegerValue()).getDomain())
        ).hashTreeRoot();
        if (!BLSSignatureVerifier.SIMPLE.verify(
                contributionPubkeys,
                signingRoot,
                syncAggregate.getSyncCommitteeSignature().getSignature()
        )) {
            throw new InvalidConsensusDataException("sync committee signature is invalid");
        }
    }
}
