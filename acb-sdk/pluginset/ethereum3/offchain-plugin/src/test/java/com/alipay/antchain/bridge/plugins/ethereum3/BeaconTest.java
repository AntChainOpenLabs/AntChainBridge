package com.alipay.antchain.bridge.plugins.ethereum3;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson.JSONArray;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.EthDataValidator;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.EthTransactionReceipt;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.LightClientUpdateWrapper;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec.DummySpecConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Assert;
import org.junit.Test;
import org.web3j.utils.Numeric;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrapSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeader;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateSchema;
import tech.pegasys.teku.spec.datastructures.state.SigningData;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;

public class BeaconTest {

    @AllArgsConstructor
    @Getter
    public static class Fork {
        public static final String MAINNET_GENESIS_VALIDATORS_ROOT = "0x4b363db94e286120d76eb905340fdd4e54bfe9f06bf33ff6cf5ad27f511bfe95";

        private String name;
        private BigInteger epoch;
        private byte[] version;

        public byte[] computeDomain(String genesisValidatorsRootHex) {
            var forkVersion32 = ArrayUtil.addAll(this.version, new byte[28]);
            var h = DigestUtil.sha256(ArrayUtil.addAll(
                    forkVersion32,
                    Numeric.hexStringToByteArray(genesisValidatorsRootHex)
            ));
            var prefix = new byte[]{7, 0, 0, 0};
            return ArrayUtil.addAll(prefix, ArrayUtil.sub(h, 0, 28));
        }
    }

    public static final List<Fork> FORKS = ListUtil.toList(
            new Fork("GENESIS", BigInteger.valueOf(0), new byte[]{0, 0, 0, 0}),
            new Fork("ALTAIR", BigInteger.valueOf(74240), new byte[]{1, 0, 0, 0}),
            new Fork("BELLATRIX", BigInteger.valueOf(144896), new byte[]{2, 0, 0, 0}),
            new Fork("CAPELLA", BigInteger.valueOf(194048), new byte[]{3, 0, 0, 0}),
            new Fork("DENEB", BigInteger.valueOf(269568), new byte[]{4, 0, 0, 0})
    );

    private static final String BOOTSTRAP = FileUtil.readString("bootstrap.json", Charset.defaultCharset());

    private static final String UPDATE = FileUtil.readString("update.json", Charset.defaultCharset());

    private static final String UPDATE_NEXT_PERIOD = FileUtil.readString("update-next-period.json", Charset.defaultCharset());

    private static final String BLOCK_V2 = FileUtil.readString("blockv2.json", Charset.defaultCharset());

    private static final String PREV_BLK_HDR = FileUtil.readString("block-header-prev.json", Charset.defaultCharset());

    private static final String PREV_BLOCK_V2 = FileUtil.readString("blockv2-prev.json", Charset.defaultCharset());

    private static final String BLOCK_RECEIPTS = FileUtil.readString("block-receipts.json", Charset.defaultCharset());

    private static final String BLINDED_BLOCK_PREV = FileUtil.readString("blinded-block-prev.json", Charset.defaultCharset());

    private static final String BLINDED_BLOCK = FileUtil.readString("blinded-block.json", Charset.defaultCharset());

    private static final String UPDATE_1297 = FileUtil.readString("update-1297.json", Charset.defaultCharset());

    private static final String BLINDED_BLOCK_10639460 = FileUtil.readString("blinded-block-10639460.json", Charset.defaultCharset());
    // 10639459 is missed : https://beaconcha.in/slot/10639459
    private static final String BLINDED_BLOCK_10639458 = FileUtil.readString("blinded-block-10639458.json", Charset.defaultCharset());

    @Test
    @SneakyThrows
    public void testVerifyNewUpdate() {
        var lightClientUpdate = JsonUtil.parse(UPDATE, new LightClientUpdateSchema(new DummySpecConfig()).getJsonTypeDefinition());
        var lightClientUpdateNextPeriod = JsonUtil.parse(UPDATE_NEXT_PERIOD, new LightClientUpdateSchema(new DummySpecConfig()).getJsonTypeDefinition());

        // finalized header's hash is block root
        var currentSyncCommittee = (SyncCommittee) lightClientUpdate.get(1);
        var nextSyncCommittee = (SyncCommittee) lightClientUpdateNextPeriod.get(1);

        List<BLSPublicKey> contributionPubkeys = new ArrayList<>();
        for (int i = 0; i < currentSyncCommittee.getPubkeys().size(); i++) {
            if (((SyncAggregate) lightClientUpdateNextPeriod.get(5)).getSyncCommitteeBits().getBit(i)) {
                contributionPubkeys.add(currentSyncCommittee.getPubkeys().get(i).getBLSPublicKey());
            }
        }

        // verify merkle proof to ensure finalized header is valid and next sync committee root is valid
        var finalizedHeader = (LightClientHeader) lightClientUpdateNextPeriod.get(3);
        var finalizedBranch = (SszBytes32Vector) lightClientUpdateNextPeriod.get(4);
        var attestedHeader = (LightClientHeader) lightClientUpdateNextPeriod.get(0);

        Assert.assertTrue(
                new Predicates(null).isValidMerkleBranch(
                        finalizedHeader.hashTreeRoot(),
                        finalizedBranch,
                        finalizedBranch.size(),
                        105,
                        attestedHeader.getBeacon().getStateRoot()
                )
        );

        var nextSyncCommitteeBranch = (SszBytes32Vector) lightClientUpdateNextPeriod.get(2);
        Assert.assertTrue(
                new Predicates(null).isValidMerkleBranch(
                        nextSyncCommittee.hashTreeRoot(),
                        nextSyncCommitteeBranch,
                        nextSyncCommitteeBranch.size(),
                        55,
                        attestedHeader.getBeacon().getStateRoot()
                )
        );

        var sigSlot = ((SszUInt64) lightClientUpdateNextPeriod.get(6)).get();
        Assert.assertEquals(sigSlot.dividedBy(8192), attestedHeader.getBeacon().getSlot().dividedBy(8192));

        // verify sync committee signature
        var syncAggregate = (SyncAggregate) lightClientUpdateNextPeriod.get(5);
        var signingRoot = new SigningData(
                attestedHeader.hashTreeRoot(), Bytes32.wrap(FORKS.getLast().computeDomain(Fork.MAINNET_GENESIS_VALIDATORS_ROOT))
        ).hashTreeRoot();

        Assert.assertTrue(
                BLSSignatureVerifier.SIMPLE.verify(
                        contributionPubkeys,
                        signingRoot,
                        syncAggregate.getSyncCommitteeSignature().getSignature()
                )
        );
    }

    @Test
    @SneakyThrows
    public void testVerifyBlockV2() {
        var spec = SpecFactory.create("mainnet");
        var schemaDef = spec.atSlot(UInt64.valueOf(10421198L)).getSchemaDefinitions();
        var lightClientBootstrap = JsonUtil.parse(BOOTSTRAP, new LightClientBootstrapSchema(new DummySpecConfig()).getJsonTypeDefinition());
        var block = JsonUtil.parse(BLOCK_V2, schemaDef.getSignedBeaconBlockSchema().getJsonTypeDefinition());

        var prevBeaconBlockHeader = JsonUtil.parse(PREV_BLK_HDR, SignedBeaconBlockHeader.SSZ_SCHEMA.getJsonTypeDefinition());

//        var blockPrev = JsonUtil.parse(PREV_BLOCK_V2, schemaDef.getSignedBeaconBlockSchema().getJsonTypeDefinition());

        Assert.assertEquals(prevBeaconBlockHeader.getMessage().getSlot().increment(), block.getSlot());
        Assert.assertEquals(lightClientBootstrap.getLightClientHeader().getBeacon().getSlot().dividedBy(8192), block.getSlot().dividedBy(8192));

        var syncAggregate = block.getMessage().getBody().getOptionalSyncAggregate().get();
        Assert.assertTrue(
                syncAggregate.getSyncCommitteeBits().stream().filter(AbstractSszPrimitive::get).count() * 3 > syncAggregate.getSyncCommitteeBits().size() * 2L
        );
        List<BLSPublicKey> contributionPubkeys = new ArrayList<>();
        for (int i = 0; i < lightClientBootstrap.getCurrentSyncCommittee().getPubkeys().size(); i++) {
            if (syncAggregate.getSyncCommitteeBits().getBit(i)) {
                contributionPubkeys.add(lightClientBootstrap.getCurrentSyncCommittee().getPubkeys().get(i).getBLSPublicKey());
            }
        }

        Assert.assertEquals(prevBeaconBlockHeader.getMessage().getRoot(), block.getMessage().getParentRoot());

        var signingRoot = new SigningData(
                prevBeaconBlockHeader.getMessage().getRoot(), Bytes32.wrap(FORKS.getLast().computeDomain(Fork.MAINNET_GENESIS_VALIDATORS_ROOT))
        ).hashTreeRoot();

        Assert.assertTrue(
                BLSSignatureVerifier.SIMPLE.verify(
                        contributionPubkeys,
                        signingRoot,
                        syncAggregate.getSyncCommitteeSignature().getSignature()
                )
        );
    }

    @Test
    @SneakyThrows
    public void testVerifyBlindedBlock() {
        var spec = SpecFactory.create("mainnet");
        var schemaDef = spec.atSlot(UInt64.valueOf(10421198L)).getSchemaDefinitions();
        var lightClientBootstrap = JsonUtil.parse(BOOTSTRAP, new LightClientBootstrapSchema(new DummySpecConfig()).getJsonTypeDefinition());
        var block = JsonUtil.parse(BLINDED_BLOCK, schemaDef.getSignedBlindedBeaconBlockSchema().getJsonTypeDefinition());
        var prevBlock = JsonUtil.parse(BLINDED_BLOCK_PREV, schemaDef.getSignedBlindedBeaconBlockSchema().getJsonTypeDefinition());
        var prevBeaconBlockHeader = prevBlock.asHeader();

        Assert.assertEquals(prevBeaconBlockHeader.getMessage().getSlot().increment(), block.getSlot());
        Assert.assertEquals(lightClientBootstrap.getLightClientHeader().getBeacon().getSlot().dividedBy(8192), block.getSlot().dividedBy(8192));

        var syncAggregate = block.getMessage().getBody().getOptionalSyncAggregate().get();
        Assert.assertTrue(
                syncAggregate.getSyncCommitteeBits().stream().filter(AbstractSszPrimitive::get).count() * 3 > syncAggregate.getSyncCommitteeBits().size() * 2L
        );
        List<BLSPublicKey> contributionPubkeys = new ArrayList<>();
        for (int i = 0; i < lightClientBootstrap.getCurrentSyncCommittee().getPubkeys().size(); i++) {
            if (syncAggregate.getSyncCommitteeBits().getBit(i)) {
                contributionPubkeys.add(lightClientBootstrap.getCurrentSyncCommittee().getPubkeys().get(i).getBLSPublicKey());
            }
        }

        Assert.assertEquals(prevBeaconBlockHeader.getMessage().getRoot(), block.getMessage().getParentRoot());

        var signingRoot = new SigningData(
                prevBeaconBlockHeader.getMessage().getRoot(), Bytes32.wrap(FORKS.getLast().computeDomain(Fork.MAINNET_GENESIS_VALIDATORS_ROOT))
        ).hashTreeRoot();

        Assert.assertTrue(
                BLSSignatureVerifier.SIMPLE.verify(
                        contributionPubkeys,
                        signingRoot,
                        syncAggregate.getSyncCommitteeSignature().getSignature()
                )
        );
    }

    @Test
    @SneakyThrows
    public void testVerifyBlindedBlockWithOneSlotMissed() {
        var spec = SpecFactory.create("mainnet");
        var schemaDef = spec.atSlot(UInt64.valueOf(10639460)).getSchemaDefinitions();
        var lightClientUpdate = new LightClientUpdateWrapper(JsonUtil.parse(UPDATE_1297, new LightClientUpdateSchema(new DummySpecConfig()).getJsonTypeDefinition()));
        var block = JsonUtil.parse(BLINDED_BLOCK_10639460, schemaDef.getSignedBlindedBeaconBlockSchema().getJsonTypeDefinition());
        var prevBlock = JsonUtil.parse(BLINDED_BLOCK_10639458, schemaDef.getSignedBlindedBeaconBlockSchema().getJsonTypeDefinition());
        var prevBeaconBlockHeader = prevBlock.asHeader();

//        Assert.assertEquals(prevBeaconBlockHeader.getMessage().getSlot().increment(), block.getSlot());
//        Assert.assertEquals(lightClientUpdate.getAttestedHeader().getBeacon().getSlot().dividedBy(8192), block.getSlot().dividedBy(8192));

        var syncAggregate = block.getMessage().getBody().getOptionalSyncAggregate().get();
        Assert.assertTrue(
                syncAggregate.getSyncCommitteeBits().stream().filter(AbstractSszPrimitive::get).count() * 3 > syncAggregate.getSyncCommitteeBits().size() * 2L
        );
        List<BLSPublicKey> contributionPubkeys = new ArrayList<>();
        for (int i = 0; i < lightClientUpdate.getNextSyncCommittee().getPubkeys().size(); i++) {
            if (syncAggregate.getSyncCommitteeBits().getBit(i)) {
                contributionPubkeys.add(lightClientUpdate.getNextSyncCommittee().getPubkeys().get(i).getBLSPublicKey());
            }
        }

        Assert.assertEquals(prevBeaconBlockHeader.getMessage().getRoot(), block.getMessage().getParentRoot());

        var signingRoot = new SigningData(
                prevBeaconBlockHeader.getMessage().getRoot(), Bytes32.wrap(FORKS.getLast().computeDomain(Fork.MAINNET_GENESIS_VALIDATORS_ROOT))
        ).hashTreeRoot();

        Assert.assertTrue(
                BLSSignatureVerifier.SIMPLE.verify(
                        contributionPubkeys,
                        signingRoot,
                        syncAggregate.getSyncCommitteeSignature().getSignature()
                )
        );
    }

    @Test
    @SneakyThrows
    public void testVerifyReceipt() {
        var spec = SpecFactory.create("mainnet");
        var schemaDef = spec.atSlot(UInt64.valueOf(10421198L)).getSchemaDefinitions();

        var block = JsonUtil.parse(BLOCK_V2, schemaDef.getSignedBeaconBlockSchema().getJsonTypeDefinition());
        var receipts = JSONArray.parseArray(BLOCK_RECEIPTS, org.web3j.protocol.core.methods.response.TransactionReceipt.class);

        var besuReceipts = receipts.stream().map(EthTransactionReceipt::generateFrom).toList();

        var receiptsRoot = block.getMessage().getBody().getOptionalExecutionPayload().get().getReceiptsRoot().toHexString();
        System.out.println(receiptsRoot);

        var receiptsRootCalc = EthDataValidator.receiptsRoot(besuReceipts).toHexString();
        System.out.println(receiptsRootCalc);

        Assert.assertEquals(receiptsRoot, receiptsRootCalc);

        for (int i = 0; i < besuReceipts.size(); i++) {
            var proof = EthDataValidator.getReceiptProof(i, besuReceipts);
            Assert.assertEquals(receiptsRoot, proof.validateAndGetRoot().toHexString());

            var receiptDecoded = proof.getEthTransactionReceipt();
            var originalReceipt = besuReceipts.get(i);
            Assert.assertEquals(
                    originalReceipt.getLogs().size(),
                    receiptDecoded.getLogs().size()
            );
            Assert.assertEquals(
                    originalReceipt.getStatus(),
                    receiptDecoded.getStatus()
            );
            Assert.assertEquals(
                    originalReceipt.getBloomFilter().toHexString(),
                    receiptDecoded.getBloomFilter().toHexString()
            );
        }
    }
}
