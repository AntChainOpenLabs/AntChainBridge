package com.alipay.antchain.bridge.plugins.ethereum3;

import java.nio.charset.Charset;
import java.util.List;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.plugins.ethereum3.core.EthConsensusEndorsements;
import com.alipay.antchain.bridge.plugins.ethereum3.core.EthConsensusStateData;
import com.alipay.antchain.bridge.plugins.ethereum3.core.EthSubjectIdentity;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.*;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.*;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec.DummySpecConfig;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec.Eth2ChainConfig;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.junit.Assert;
import org.junit.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.MerkleUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrapSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateSchema;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;

public class EthDataTest {

    private static final String RAW_ETH_RECEIPT_PROOF = """
            {
              "proofRelatedNodes": [
                "0xf90131a02eedae316eb8ccc0f59055d2cef9fa86f2689e883214e038834353483a67c97ba0e7e22051f731995a4d0e4ccc864b74d43b2920ef459ec1aa94fd58583f17d8aba0d3e5a858235024e14bdf2372c651848d0ba89350a767b3afaab2ea8b085b25d8a06ed3cdfd022f41658b93a87762842dac6de46b3c3278de8e8ba2279975f0a27da0eb7bb9233520548b3ff914d990d7095d823acaf18a72739e2d64012788c22975a0259ca871b40b2a0985153296ce5ef5d491849e5a2f0d7ae2e52a003abe18349fa036445ce1f52e1b6a519ba036160bef513c2c3c1beb5b6e67852fdeac67112ecaa0d1e98aca8cde82896a18346816eabbff40435c60dcfc0d9137796c2b23a023d3a08d2376a621707b5d03050a19c6dc20c30d00f89d5291deb4aa9b52cb941952928080808080808080",
                "0xf851a088b8cdc484315072ff50808eb198192eabc0bd773153641ecdf81f8bb98a9862a08ec05df12951132edccdf13d6ca0b418d901e6f76b58266caedfd2004a090ba8808080808080808080808080808080",
                "0xf9044220b9043e02f9043a0183025e10b9010000200000000000000080000080000000000000000000000000000000000000000000000000000000000000000200000002000000080000000000080000000000000000080000000000000008000000200000000000000000000000008020000000000000000080000000000000400000000000000000000000000014000000000000000000000000000000000000000000000001000002080000004000000000004000000000000000000000000000000000000000000000008000000000000000000002000000000000000000000000000000000000001000000000000000000000200000000000002002000000000800001000000000400000000000000000f9032ff87a94c02aaa39b223fe8d0a0e5c4f27ead9083c756cc2f842a0e1fffcc4923d04b559f4d29a8bfc6cda04eb5b0d3c460751c2402c5c5cc9109ca00000000000000000000000003fc91a3afd70395cd496c647d5a6cc9d4b2b7fada0000000000000000000000000000000000000000000000000145232a26ba7eeb8f89b94c02aaa39b223fe8d0a0e5c4f27ead9083c756cc2f863a0ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efa00000000000000000000000003fc91a3afd70395cd496c647d5a6cc9d4b2b7fada0000000000000000000000000d811cbad511a756b125c5ea8a3b88ec414a3bee5a0000000000000000000000000000000000000000000000000145232a26ba7eeb8f89b94210028b5a1e9effb93ce31006a18629f31131093f863a0ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3efa0000000000000000000000000d811cbad511a756b125c5ea8a3b88ec414a3bee5a0000000000000000000000000994e092c13aa50d312643b5caa0273317b664f5da00000000000000000000000000000000000000000000000000000a31086eb7834f87994d811cbad511a756b125c5ea8a3b88ec414a3bee5e1a01c411e9a96e071241c2f21f7726b17ae89e3cab4c78be50e062b03a9fffbbad1b84000000000000000000000000000000000000000000000000000067e7ad73559b6000000000000000000000000000000000000000000000000e2e0a952bc7b7b8af8fc94d811cbad511a756b125c5ea8a3b88ec414a3bee5f863a0d78ad95fa46c994b6551d0da85fc275fe613ce37657fb8d5e3d130840159d822a00000000000000000000000003fc91a3afd70395cd496c647d5a6cc9d4b2b7fada0000000000000000000000000994e092c13aa50d312643b5caa0273317b664f5db8800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000145232a26ba7eeb80000000000000000000000000000000000000000000000000000a31086eb78340000000000000000000000000000000000000000000000000000000000000000"
              ],
              "receiptIndex": 0
            }""";

    private static final String RECEIPTS_ROOT_HEX = "0x136fec3a3ab3befeaaee004e0f695702af1fe75e5d97735c9fbfc0e8f3cb0c86";

    private static final String TRANSACTION_RECEIPT = """
            {
              "blockHash": "0x3199aedcdc190ebf291314bab7c61d7a659e8ec28cd483c689736cfbfa52bea6",
              "blockNumber": "0x143a3dc",
              "contractAddress": null,
              "cumulativeGasUsed": "0x25e10",
              "effectiveGasPrice": "0x5e968856f",
              "from": "0x994e092c13aa50d312643b5caa0273317b664f5d",
              "gasUsed": "0x25e10",
              "logs": [
                {
                  "address": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
                  "blockHash": "0x3199aedcdc190ebf291314bab7c61d7a659e8ec28cd483c689736cfbfa52bea6",
                  "blockNumber": "0x143a3dc",
                  "data": "0x000000000000000000000000000000000000000000000000145232a26ba7eeb8",
                  "logIndex": "0x0",
                  "removed": false,
                  "topics": [
                    "0xe1fffcc4923d04b559f4d29a8bfc6cda04eb5b0d3c460751c2402c5c5cc9109c",
                    "0x0000000000000000000000003fc91a3afd70395cd496c647d5a6cc9d4b2b7fad"
                  ],
                  "transactionHash": "0x3bb6abf9961e77ae3caa906f95569f600e810fd7584c20c0fee53a77f095792e",
                  "transactionIndex": "0x0"
                },
                {
                  "address": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
                  "blockHash": "0x3199aedcdc190ebf291314bab7c61d7a659e8ec28cd483c689736cfbfa52bea6",
                  "blockNumber": "0x143a3dc",
                  "data": "0x000000000000000000000000000000000000000000000000145232a26ba7eeb8",
                  "logIndex": "0x1",
                  "removed": false,
                  "topics": [
                    "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                    "0x0000000000000000000000003fc91a3afd70395cd496c647d5a6cc9d4b2b7fad",
                    "0x000000000000000000000000d811cbad511a756b125c5ea8a3b88ec414a3bee5"
                  ],
                  "transactionHash": "0x3bb6abf9961e77ae3caa906f95569f600e810fd7584c20c0fee53a77f095792e",
                  "transactionIndex": "0x0"
                },
                {
                  "address": "0x210028b5a1e9effb93ce31006a18629f31131093",
                  "blockHash": "0x3199aedcdc190ebf291314bab7c61d7a659e8ec28cd483c689736cfbfa52bea6",
                  "blockNumber": "0x143a3dc",
                  "data": "0x0000000000000000000000000000000000000000000000000000a31086eb7834",
                  "logIndex": "0x2",
                  "removed": false,
                  "topics": [
                    "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                    "0x000000000000000000000000d811cbad511a756b125c5ea8a3b88ec414a3bee5",
                    "0x000000000000000000000000994e092c13aa50d312643b5caa0273317b664f5d"
                  ],
                  "transactionHash": "0x3bb6abf9961e77ae3caa906f95569f600e810fd7584c20c0fee53a77f095792e",
                  "transactionIndex": "0x0"
                },
                {
                  "address": "0xd811cbad511a756b125c5ea8a3b88ec414a3bee5",
                  "blockHash": "0x3199aedcdc190ebf291314bab7c61d7a659e8ec28cd483c689736cfbfa52bea6",
                  "blockNumber": "0x143a3dc",
                  "data": "0x00000000000000000000000000000000000000000000000000067e7ad73559b6000000000000000000000000000000000000000000000000e2e0a952bc7b7b8a",
                  "logIndex": "0x3",
                  "removed": false,
                  "topics": [
                    "0x1c411e9a96e071241c2f21f7726b17ae89e3cab4c78be50e062b03a9fffbbad1"
                  ],
                  "transactionHash": "0x3bb6abf9961e77ae3caa906f95569f600e810fd7584c20c0fee53a77f095792e",
                  "transactionIndex": "0x0"
                },
                {
                  "address": "0xd811cbad511a756b125c5ea8a3b88ec414a3bee5",
                  "blockHash": "0x3199aedcdc190ebf291314bab7c61d7a659e8ec28cd483c689736cfbfa52bea6",
                  "blockNumber": "0x143a3dc",
                  "data": "0x0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000145232a26ba7eeb80000000000000000000000000000000000000000000000000000a31086eb78340000000000000000000000000000000000000000000000000000000000000000",
                  "logIndex": "0x4",
                  "removed": false,
                  "topics": [
                    "0xd78ad95fa46c994b6551d0da85fc275fe613ce37657fb8d5e3d130840159d822",
                    "0x0000000000000000000000003fc91a3afd70395cd496c647d5a6cc9d4b2b7fad",
                    "0x000000000000000000000000994e092c13aa50d312643b5caa0273317b664f5d"
                  ],
                  "transactionHash": "0x3bb6abf9961e77ae3caa906f95569f600e810fd7584c20c0fee53a77f095792e",
                  "transactionIndex": "0x0"
                }
              ],
              "logsBloom": "0x00200000000000000080000080000000000000000000000000000000000000000000000000000000000000000200000002000000080000000000080000000000000000080000000000000008000000200000000000000000000000008020000000000000000080000000000000400000000000000000000000000014000000000000000000000000000000000000000000000001000002080000004000000000004000000000000000000000000000000000000000000000008000000000000000000002000000000000000000000000000000000000001000000000000000000000200000000000002002000000000800001000000000400000000000000000",
              "status": "0x1",
              "to": "0x3fc91a3afd70395cd496c647d5a6cc9d4b2b7fad",
              "transactionHash": "0x3bb6abf9961e77ae3caa906f95569f600e810fd7584c20c0fee53a77f095792e",
              "transactionIndex": "0x0",
              "type": "0x2"
            }""";

    private static final String BLOOM_FILTER_HEX = "0x00000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000020000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000004000000000000002000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000001000004000000000000000000000000000000000000000000000000000000040000000000000010000000008000000";

    private static final String BLOOM_FILTER_HAS_NO_AM = "0x0da35fdeed8c63f73938fe5da402debd3167713798e7198c1399b04ff8b38ceb1bcb4d41c7fb2252da5433ee88c719b17675c8ee8e7d6f80f6e1bdeefef9289241ea0f284af9ca18ec63f9aeef0568a9875b78f98c4e9e28172d9cc7ede498339e15327aee76d1f7276c9bc6fd1ce9c3037c26629f322ed952626afd709fd455eb3abe769c84dd9d68e961409bfeae37514de3a1f195ebd94e7530fed17816a7affa0b62b3fd26c84d1fcdcff9c25ec1a4d345025ea5b74a2d5a7dd6394588765950341af11fd84a494d994f0b7b9dd671e13d8ff6691c56bb15732320e9ffd635392ce97183c45e4cd62fc82b7a05d46458d220fb9c72dafa89db73dac47ca7";

    private static final LogsBloomFilter SEND_AUTH_MESSAGE_LOG_TOPIC_FILTER = LogsBloomFilter.builder()
            .insertBytes(EthLogTopic.create(Bytes32.fromHexString("0x79b7516b1b7a6a39fb4b7b22e8667cd3744e5c27425292f8a9f49d1042c0c651"))).build();

    private static final String BOOTSTRAP = FileUtil.readString("bootstrap.json", Charset.defaultCharset());

    private static final String BLOCK_V2 = FileUtil.readString("blockv2.json", Charset.defaultCharset());

    private static final String BLINDED_BLOCK = FileUtil.readString("blinded-block.json", Charset.defaultCharset());

    private static final String PREV_BLINDED_BLOCK = FileUtil.readString("blinded-block-prev.json", Charset.defaultCharset());

    private static final String PREV_BLOCK_V2 = FileUtil.readString("blockv2-prev.json", Charset.defaultCharset());

    private static final String UPDATE = FileUtil.readString("update.json", Charset.defaultCharset());

    @Test
    public void testEthReceiptProof() {
        var proof = EthReceiptProof.decodeFromJson(RAW_ETH_RECEIPT_PROOF);
        Assert.assertEquals(RECEIPTS_ROOT_HEX, proof.validateAndGetRoot().toHexString());
    }

    @Test
    public void testEthTransactionReceipt() {
        var receipt = JSON.parseObject(TRANSACTION_RECEIPT, TransactionReceipt.class);
        var ethReceipt = EthTransactionReceipt.generateFrom(receipt);

        Assert.assertEquals(Numeric.decodeQuantity(receipt.getStatus()).intValue(), ethReceipt.getStatus());
        Assert.assertEquals(receipt.getLogs().size(), ethReceipt.getLogs().size());
        Assert.assertEquals(receipt.getCumulativeGasUsed().longValue(), ethReceipt.getCumulativeGasUsed());
        Assert.assertEquals(receipt.getLogsBloom(), ethReceipt.getBloomFilter().toHexString());
    }

    @Test
    public void testBloomFilter() {
        var filter = LogsBloomFilter.fromHexString(BLOOM_FILTER_HEX);
        Assert.assertTrue(
                filter.couldContain(SEND_AUTH_MESSAGE_LOG_TOPIC_FILTER)
                && filter.couldContain(LogsBloomFilter.builder().insertBytes(Address.fromHexString("0xedd16fed7e0e6755d52d3cdc3319a74e0dd5890b")).build())
        );
        Assert.assertFalse(
                filter.couldContain(SEND_AUTH_MESSAGE_LOG_TOPIC_FILTER)
                && filter.couldContain(LogsBloomFilter.builder().insertBytes(Address.fromHexString("0xedd16fed7e0e6755d52d3cdc3319a74e0dd58912")).build())
        );

        filter = LogsBloomFilter.fromHexString(BLOOM_FILTER_HAS_NO_AM);
        Assert.assertFalse(
                filter.couldContain(SEND_AUTH_MESSAGE_LOG_TOPIC_FILTER)
                && filter.couldContain(LogsBloomFilter.builder().insertBytes(Address.fromHexString("0xedd16fed7e0e6755d52d3cdc3319a74e0dd5890b")).build())
        );
    }

    @Test
    @SneakyThrows
    public void testEthConsensusStateData() {
        var spec = SpecFactory.create("mainnet");
        var schemaDef = spec.atSlot(UInt64.MAX_VALUE).getSchemaDefinitions();

        var lightClientUpdate = JsonUtil.parse(UPDATE, new LightClientUpdateSchema(new DummySpecConfig()).getJsonTypeDefinition());
        var block = JsonUtil.parse(PREV_BLINDED_BLOCK, schemaDef.getSignedBlindedBeaconBlockSchema().getJsonTypeDefinition());
        EthConsensusStateData data = new EthConsensusStateData();
        data.setLightClientUpdateWrapper(new LightClientUpdateWrapper(lightClientUpdate));
        data.setBeaconBlockHeader(block.asHeader().getMessage());
        data.setExecutionPayloadHeader(block.getBeaconBlock().get().getBody().getOptionalExecutionPayloadHeader().get());

        var bodySchema = schemaDef.getBlindedBeaconBlockBodySchema();
        data.setExecutionPayloadBranches(
                MerkleUtil.constructMerkleProof(
                        block.getBeaconBlock().get().getBody().getBackingNode(),
                        bodySchema.getChildGeneralizedIndex(bodySchema.getFieldIndex(BlockBodyFields.EXECUTION_PAYLOAD_HEADER))
                )
        );

        System.out.println(data.toJson());

        var dataDeserialized = EthConsensusStateData.fromJson(
                FileUtil.readString("eth_consensus_state_data.json", Charset.defaultCharset()),
                Eth2ChainConfig.MAINNET_CHAIN_CONFIG.getCurrentSchemaDefinitions(UInt64.MAX_VALUE.bigIntegerValue()),
                Eth2ChainConfig.MAINNET_CHAIN_CONFIG.getSpecConfig()
        );
        Assert.assertNotNull(dataDeserialized);
        Assert.assertNotNull(dataDeserialized.getExecutionPayloadHeader());
        Assert.assertNotNull(dataDeserialized.getLightClientUpdateWrapper());
        Assert.assertNotNull(dataDeserialized.getBeaconBlockHeader());
        Assert.assertFalse(dataDeserialized.getExecutionPayloadBranches().isEmpty());
    }

    @Test
    @SneakyThrows
    public void testEthSubjectIdentity() {
        var lightClientBootstrap = JsonUtil.parse(BOOTSTRAP, new LightClientBootstrapSchema(new DummySpecConfig()).getJsonTypeDefinition());

        var subjectId = new EthSubjectIdentity(
                lightClientBootstrap.getCurrentSyncCommittee(),
                Eth2ChainConfig.MAINNET_CHAIN_CONFIG
        );

        System.out.println(subjectId.toJson());

        var subjectIdentity = EthSubjectIdentity.fromJson(FileUtil.readString("eth_subject_identity.json", Charset.defaultCharset()));
        Assert.assertEquals(subjectId.getCurrentSyncCommittee().size(), subjectIdentity.getCurrentSyncCommittee().size());
        Assert.assertEquals(subjectId.getEth2ChainConfig().getSyncCommitteeSize(), subjectIdentity.getEth2ChainConfig().getSyncCommitteeSize());
        Assert.assertNotNull(subjectIdentity.getEth2ChainConfig().getSpec());
    }

    @Test
    @SneakyThrows
    public void testEthConsensusEndorsements() {
        var spec = SpecFactory.create("mainnet");
        var schemaDef = spec.atSlot(UInt64.MAX_VALUE).getSchemaDefinitions();

        var block = JsonUtil.parse(BLINDED_BLOCK, schemaDef.getSignedBlindedBeaconBlockSchema().getJsonTypeDefinition());

        var endorsements = new EthConsensusEndorsements(block.getBeaconBlock().get().getBody().getOptionalSyncAggregate().get());

        System.out.println(endorsements.toJson());

        var endorsementsDeserialized = EthConsensusEndorsements.fromJson(
                FileUtil.readString("eth_consensus_endorsements.json", Charset.defaultCharset()), Eth2ChainConfig.MAINNET_CHAIN_CONFIG.getSyncCommitteeSize()
        );

        Assert.assertNotNull(endorsementsDeserialized);
    }

    @Test
    @SneakyThrows
    public void testCreateMerkleProof() {
        var spec = SpecFactory.create("mainnet");
        var schemaDef = spec.atSlot(UInt64.MAX_VALUE).getSchemaDefinitions();

        var block = JsonUtil.parse(PREV_BLINDED_BLOCK, schemaDef.getSignedBlindedBeaconBlockSchema().getJsonTypeDefinition());

        var body = block.getBeaconBlock().get().getBody();
        var bodySchema = schemaDef.getBlindedBeaconBlockBodySchema();

        List<Bytes32> branches = MerkleUtil.constructMerkleProof(
                body.getBackingNode(),
                bodySchema.getChildGeneralizedIndex(bodySchema.getFieldIndex(BlockBodyFields.EXECUTION_PAYLOAD_HEADER))
        );
        Assert.assertFalse(branches.isEmpty());
        var proof = SszBytes32VectorSchema.create(branches.size())
                .createFromElements(branches.stream().map(SszBytes32::of).toList());
        Assert.assertTrue(
                new Predicates(null).isValidMerkleBranch(
                        body.getOptionalExecutionPayloadHeader().get().hashTreeRoot(),
                        proof,
                        proof.size(),
                        bodySchema.getFieldIndex(BlockBodyFields.EXECUTION_PAYLOAD_HEADER),
                        block.getBodyRoot()
                )
        );
    }

    @Test
    @SneakyThrows
    public void testEthConsensusStateDataVerification() {
        var endorsementsDeserialized = EthConsensusEndorsements.fromJson(
                FileUtil.readString("eth_consensus_endorsements.json", Charset.defaultCharset()), Eth2ChainConfig.MAINNET_CHAIN_CONFIG.getSyncCommitteeSize()
        );
        var dataDeserialized = EthConsensusStateData.fromJson(
                FileUtil.readString("eth_consensus_state_data.json", Charset.defaultCharset()),
                Eth2ChainConfig.MAINNET_CHAIN_CONFIG.getCurrentSchemaDefinitions(UInt64.MAX_VALUE.bigIntegerValue()),
                Eth2ChainConfig.MAINNET_CHAIN_CONFIG.getSpecConfig()
        );
        var subjectIdentity = EthSubjectIdentity.fromJson(FileUtil.readString("eth_subject_identity.json", Charset.defaultCharset()));

        dataDeserialized.validate(
                subjectIdentity.getCurrentSyncCommittee(),
                endorsementsDeserialized,
                subjectIdentity.getEth2ChainConfig()
        );
    }
}
