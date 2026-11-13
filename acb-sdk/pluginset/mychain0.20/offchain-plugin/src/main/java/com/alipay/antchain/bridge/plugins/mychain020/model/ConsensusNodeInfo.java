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

package com.alipay.antchain.bridge.plugins.mychain020.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alipay.antchain.bridge.plugins.mychain020.exceptions.VerifyConsensusStateException;
import com.alipay.antchain.bridge.plugins.mychain020.exceptions.VerifyCrossChainMsgException;
import com.alipay.antchain.bridge.plugins.mychain020.utils.MychainUtils;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.crypto.PublicKey;
import com.alipay.mychain.sdk.crypto.hash.Hash;
import com.alipay.mychain.sdk.crypto.hash.HashFactory;
import com.alipay.mychain.sdk.crypto.hash.HashTypeEnum;
import com.alipay.mychain.sdk.crypto.hash.IHash;
import com.alipay.mychain.sdk.domain.MychainObject;
import com.alipay.mychain.sdk.domain.block.BlockHeader;
import com.alipay.mychain.sdk.domain.consensus.Consensus;
import com.alipay.mychain.sdk.domain.consensus.ConsensusAlgorithmType;
import com.alipay.mychain.sdk.domain.consensus.abft.ABFT;
import com.alipay.mychain.sdk.domain.consensus.checkpoint.Checkpoint;
import com.alipay.mychain.sdk.domain.consensus.checkpoint.CheckpointConsensusProofInfo;
import com.alipay.mychain.sdk.domain.consensus.honeyBadger.HBConsensusProofInfo;
import com.alipay.mychain.sdk.domain.consensus.honeyBadger.HoneyBadger;
import com.alipay.mychain.sdk.domain.consensus.pbft.Pbft;
import com.alipay.mychain.sdk.domain.consensus.pbft.PbftConsensusProofInfo;
import com.alipay.mychain.sdk.domain.consensus.stableproof.StableProof;
import com.alipay.mychain.sdk.domain.consensus.stableproof.StableProofConsensusProofInfo;
import com.alipay.mychain.sdk.domain.spv.BlockHeaderInfo;
import com.alipay.mychain.sdk.domain.spv.BlockProofInfo;
import com.alipay.mychain.sdk.domain.spv.ContractNodeState;
import com.alipay.mychain.sdk.domain.spv.VerifiedBlock;
import com.alipay.mychain.sdk.domain.status.ContractNodeStatusEnum;
import com.alipay.mychain.sdk.domain.status.EndPoint;
import com.alipay.mychain.sdk.domain.status.NodeInfo;
import com.alipay.mychain.sdk.domain.status.NodeRoleEnum;
import com.alipay.mychain.sdk.domain.transaction.LogEntry;
import com.alipay.mychain.sdk.domain.transaction.Transaction;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.trie.MerkleTree;
import com.alipay.mychain.sdk.utils.BlockUtils;
import com.alipay.mychain.sdk.utils.ByteUtils;
import com.alipay.mychain.sdk.vm.EVMOutput;
import lombok.*;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsensusNodeInfo {

    public static class TransactionReceiptListSerializer implements ObjectSerializer {
        @Override
        public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
            List<TransactionReceipt> data = (List<TransactionReceipt>) object;
            serializer.write(JSONArray.toJSONString(data.stream().map(MychainObject::toString).collect(Collectors.toList())));
        }
    }

    public static class TransactionReceiptListDeserializer implements ObjectDeserializer {
        @Override
        public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
            List<TransactionReceipt> receiptList = JSONArray.parseArray(parser.parseObject(String.class), String.class)
                    .stream()
                    .map(x -> {
                        TransactionReceipt receipt = new TransactionReceipt();
                        receipt.fromJson(JSON.parseObject(x));
                        return receipt;
                    }).collect(Collectors.toList());
            return (T) receiptList;
        }

        @Override
        public int getFastMatchToken() {
            return 0;
        }
    }

    public static class TransactionListSerializer implements ObjectSerializer {
        @Override
        public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
            List<Transaction> data = (List<Transaction>) object;
            serializer.write(JSONArray.toJSONString(data.stream().map(MychainObject::toString).collect(Collectors.toList())));
        }
    }

    public static class TransactionListDeserializer implements ObjectDeserializer {
        @Override
        public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
            List<Transaction> txList = JSONArray.parseArray(parser.parseObject(String.class), String.class)
                    .stream()
                    .map(x -> {
                        Transaction tx = new Transaction();
                        tx.fromJson(JSON.parseObject(x));
                        return tx;
                    }).collect(Collectors.toList());
            return (T) txList;
        }

        @Override
        public int getFastMatchToken() {
            return 0;
        }
    }

    public static ConsensusNodeInfo decode(byte[] data) {
        return JSON.parseObject(data, ConsensusNodeInfo.class);
    }

    private HashTypeEnum mychainHashType;

    private Set<String> amContractIds;

    private List<String> consensusNodePublicKeys;

    @JSONField(serializeUsing = TransactionReceiptListSerializer.class, deserializeUsing = TransactionReceiptListDeserializer.class)
    private List<TransactionReceipt> transactionReceipts;

    @JSONField(serializeUsing = TransactionListSerializer.class, deserializeUsing = TransactionListDeserializer.class)
    private List<Transaction> transactions;

    public byte[] encode() {
        return JSON.toJSONBytes(this);
    }

    public boolean verifyConsensusNodeSigs(BlockProofInfo blockProofInfo, BlockHeader currBlockHeader, Logger logger) {
        return verifyBlockHeader(
                new VerifiedBlock(
                        0L,
                        currBlockHeader.getNumber().subtract(BigInteger.ONE),
                        currBlockHeader.getParentHash(),
                        this.consensusNodePublicKeys.stream()
                                .map(PublicKey::new).collect(Collectors.toSet())
                ),
                new BlockHeaderInfo(blockProofInfo, currBlockHeader),
                logger
        );
    }

    /**
     * From Mychain SDK
     *
     * @param block
     * @param info
     * @param logger
     * @return
     */
    public boolean verifyBlockHeader(final VerifiedBlock block, BlockHeaderInfo info, Logger logger) {
        // check parent hash
        if (!info.getBlockHeader().getParentHash().hexStrValue().equalsIgnoreCase(block.getBlockHash().hexStrValue())
                || info.getBlockHeader().getNumber().compareTo(block.getBlockNum().add(BigInteger.ONE)) != 0) {
            logger.error("verifyBlockHeader, verify hash or block number failed");
            return false;
        }

        // check self hash
        if (!info.getBlockHeader().getHash().hexStrValue().equalsIgnoreCase(
                BlockHeader.calcHash(info.getBlockHeader(), mychainHashType))) {
            logger.error("verifyBlockHeader, check hash failed");
            return false;
        }

        // check consensus proof
        Hash pksHash = Consensus.calcPKsMerkleRootHash(block.getPublicKeysList().get(0), mychainHashType);
        Hash digest = null;
        if (info.getProof().getType().getValue() == ConsensusAlgorithmType.PBFT.getValue()
                || info.getProof().getType().getValue() == ConsensusAlgorithmType.HONEYBADGER.getValue()) {
            digest = Consensus.calcProofHash(info.getBlockHeader().getParentHash(),
                    info.getBlockHeader().getTransactionRoot(),
                    pksHash, block.getPublicKeysList().get(0).size(), info.getBlockHeader().getTimestamp(), mychainHashType);
        }
        switch (info.getProof().getType()) {
            case PBFT:
                logger.debug("spvImp verifyBlockHeader case PBFT");
                PbftConsensusProofInfo pbftConsensusProofInfo = PbftConsensusProofInfo.decode(
                        info.getProof().getProof());

                // check proof digest
                if (!Arrays.equals(pbftConsensusProofInfo.getSignatureInfo().getDigest(), digest.getValue())) {
                    logger.error("verifyBlockHeader, case PBFT: check proof digest failed");
                    return false;
                }

                // check seq
                if (pbftConsensusProofInfo.getSignatureInfo().getSeq() != block.getBlockNum().longValue() + 1) {
                    logger.error("verifyBlockHeader, case PBFT: check seq failed");
                    return false;
                }

                if (!Pbft.verifyPbftConsensusProof(block.getPublicKeysList().get(0), pbftConsensusProofInfo, mychainHashType)) {
                    return false;
                }

                break;
            case HONEYBADGER:
                logger.debug("spvImp verifyBlockHeader case HONEYBADGER");
                HBConsensusProofInfo hbConsensusProofInfo = HBConsensusProofInfo.decode(info.getProof().getProof());

                // check proof digest
                if (!Arrays.equals(hbConsensusProofInfo.getHbSignatureInfo().getDigest(), digest.getValue())) {
                    logger.error("verifyBlockHeader, case HONEYBADGER: check proof digest failed");
                    return false;
                }

                //因为v3不支持此类型，所以v2中这样校验是没有问题的
                // check view
                if (hbConsensusProofInfo.getHbSignatureInfo().getView() != block.getBlockNum().longValue() + 1) {
                    logger.error("verifyLastBlockProof, case HONEYBADGER: check view failed");
                    return false;
                }

                if (!HoneyBadger.verifyHBConsensusProof(block.getPublicKeysList().get(0), hbConsensusProofInfo, mychainHashType)) {
                    return false;
                }

                break;
            case CHECKPOINT:
                digest = Consensus.calcProofHash(BlockUtils.getPBDSVersion(info.getBlockHeader().getVersion()), info.getBlockHeader().getNumber(), info.getBlockHeader().getHash(), mychainHashType);
                CheckpointConsensusProofInfo checkpointConsensusProofInfo = CheckpointConsensusProofInfo.decode(
                        info.getProof().getProof());

                if (!Checkpoint.verifyCheckpointConsensusProof(block.getPublicKeysList().get(0), checkpointConsensusProofInfo, digest.getData(), mychainHashType)) {
                    logger.error("checkpoint: check digest failed");
                    return false;
                }

                break;
            case ABFT:
                logger.debug("spvImp verifyBlockHeader case ABFT");
                hbConsensusProofInfo = HBConsensusProofInfo.decode(info.getProof().getProof());

                // check proof digest
                if (!Arrays.equals(hbConsensusProofInfo.getHbSignatureInfo().getDigest(), digest.getValue())) {
                    logger.error("verifyBlockHeader, case ABFT: check proof digest failed");
                    return false;
                }

                // check view
                if (hbConsensusProofInfo.getHbSignatureInfo().getView() != block.getBlockNum().longValue() + 1) {
                    logger.error("verifyLastBlockProof, case ABFT: check view failed");
                    return false;
                }

                if (ABFT.verifyHBConsensusProof(block.getPublicKeys(), hbConsensusProofInfo, mychainHashType)) {
                    return true;
                }

                break;
            case STABLE_PROOF:
                digest = Consensus.calcProofHash(BlockUtils.getPBDSVersion(info.getBlockHeader().getVersion()), info.getBlockHeader().getNumber(), info.getBlockHeader().getHash(), mychainHashType);
                StableProofConsensusProofInfo stableProofConsensusProofInfo = StableProofConsensusProofInfo.decode(info.getProof().getProof());
                if (!StableProof.verifyStableProofConsensusProof(block.getPublicKeysList().get(0), stableProofConsensusProofInfo, digest.getData(), mychainHashType)) {
                    logger.error("stable proof: check digest failed");
                    return false;
                }
                break;
            default:
                logger.debug("spvImp verifyBlockHeader case other");
                return false;
        }

        return true;
    }

    public void processConsensusUpdate(final BlockHeader header, Logger logger) {
        Set<PublicKey> publicKeys = checkEvent(header, logger);
        if (ObjectUtil.isEmpty(publicKeys)) {
            throw new VerifyConsensusStateException(header.getNumber(), header.getHash().toString(), "get empty public keys after consensus update");
        }

        this.consensusNodePublicKeys = publicKeys.stream().map(PublicKey::hexStrValue).collect(Collectors.toList());
        logger.info("update consensus public keys: {}", this.consensusNodePublicKeys);
    }

    public void processReceiptVerification(BlockHeader header, CrossChainMsgLedgerData ledgerData, byte[] receiptHashes, Logger logger) {
        try {
            if (receiptHashes.length % 32 != 0) {
                throw new VerifyCrossChainMsgException(header.getNumber(), ledgerData.getReceiptIndex(), ledgerData.getLogIndex(),
                        "receipt hashes length is not multiple of 32");
            }
            if (ledgerData.getReceipt().getLogs().size() <= ledgerData.getLogIndex()) {
                throw new VerifyCrossChainMsgException(header.getNumber(), ledgerData.getReceiptIndex(), ledgerData.getLogIndex(),
                        "logs size is less than log index");
            }
            if (!this.amContractIds.stream().map(x -> Utils.getIdentityByName(x, this.mychainHashType).hexStrValue())
                    .collect(Collectors.toSet()).contains(ledgerData.getAmContractIdHex())) {
                throw new VerifyCrossChainMsgException(header.getNumber(), ledgerData.getReceiptIndex(), ledgerData.getLogIndex(),
                        "incorrect am contract id");
            }

            Vector<Hash> hashVector = new Vector<>();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(receiptHashes);
            for (int i = 0; i < receiptHashes.length / 32; i++) {
                hashVector.add(new Hash(IoUtil.readBytes(inputStream, 32)));
            }

            if (!verifyReceipt(header, hashVector, ledgerData.getReceiptIndex(), ledgerData.getReceipt(), logger)) {
                throw new VerifyCrossChainMsgException(header.getNumber(), ledgerData.getReceiptIndex(), ledgerData.getLogIndex(),
                        "verify receipt failed");
            }
        } catch (VerifyCrossChainMsgException e) {
            throw e;
        } catch (Exception e) {
            throw new VerifyCrossChainMsgException(header.getNumber(), ledgerData.getReceiptIndex(), ledgerData.getLogIndex(),
                    "unexpected exception: ", e);
        }
    }

    private Set<PublicKey> checkEvent(
            final BlockHeader header,
            Logger logger
    ) {
        Set<PublicKey> publicKeys = this.consensusNodePublicKeys.stream().map(PublicKey::new).collect(Collectors.toSet());

        // use bloom filter to match events
        if (MychainUtils.bloomTopicsMatch(
                MychainUtils.CONSENSUS_UPDATE_EVENT_TOPICS_LIST,
                this.mychainHashType,
                header.getLogBloom()
        )) {
            // verify body
            if (!verifyBlockBody(header, this.transactions, this.transactionReceipts, logger)) {
                throw new VerifyConsensusStateException(
                        header.getNumber(), header.getHash().toString(),
                        "checkEvent, verify block body error"
                );
            }
            return handleEvent(this.transactions, this.transactionReceipts, publicKeys, logger);
        } else {
            logger.debug("checkEvent, log_filter is not match.");
            return publicKeys;
        }
    }

    private boolean verifyBlockBody(
            final BlockHeader header,
            final List<Transaction> transactionList,
            final List<TransactionReceipt> receiptList,
            Logger logger
    ) {
        // calc tx root
        Vector<Hash> txHashes = new Vector<>();
        for (Transaction tx : transactionList) {
            if (tx.getHash().equals(Hash.ZERO)) {
                txHashes.add(Hash.ZERO);
            } else {
                txHashes.add(tx.getHash());
            }
        }

        // check tx root
        if (txHashes.isEmpty() || !MerkleTree.root(txHashes).hexStrValue().equalsIgnoreCase(
                header.getTransactionRoot().hexStrValue())) {
            logger.error("verifyBlockBody, check tx root failed");
            return false;
        }

        // calc receipt root
        Vector<Hash> txReceiptHashes = new Vector<>();
        for (TransactionReceipt receipt : receiptList) {
            IHash iHash = HashFactory.getHash(this.mychainHashType);
            byte[] hash = iHash.hash(receipt.toRlp());
            txReceiptHashes.add(new Hash(hash));
        }

        return !txReceiptHashes.isEmpty() && MerkleTree.root(txReceiptHashes).hexStrValue().equalsIgnoreCase(
                header.getReceiptRoot().hexStrValue());
    }

    private Set<PublicKey> handleEvent(
            final List<Transaction> transactionList,
            final List<TransactionReceipt> receiptList,
            Set<PublicKey> pubKeys,
            Logger logger
    ) {
        int index = 0;

        for (TransactionReceipt receipt : receiptList) {
            for (LogEntry log : receipt.getLogs()) {
                // find sys(admin) event
                boolean isAdmin = MychainUtils.SYSTEM_CONTRACT_NODE.hexStrValue()
                        .equalsIgnoreCase(transactionList.get(index).getTo().hexStrValue());
                // check receipt.output and log.getTopics().isNotEmpty()
                if (isAdmin && MychainUtils.SYSTEM_ACTIVE_NODE_OUTPUT.equalsIgnoreCase(ByteUtils.toHexString(receipt.getOutput()))
                        && log.getTopics() != null && !log.getTopics().isEmpty()) {

                    PublicKey pubKey;
                    if (Objects.equals(MychainUtils.MYCHAIN_V2_SYSTEM_NODE_ACTIVE_SIGN_HEX, log.getTopics().get(0))) {
                        pubKey = parseLogData(log.getLogData());
                        pubKeys.add(pubKey);
                        logger.debug("HandleEvent, add pub_key, pub_key size:{}", pubKeys.size());
                        continue;
                    }

                    if (Objects.equals(MychainUtils.MYCHAIN_V2_SYSTEM_NODE_DELETE_SIGN_HEX, log.getTopics().get(0))) {
                        pubKey = parseLogData(log.getLogData());
                        if (pubKeys.contains(pubKey)) {
                            pubKeys.remove(pubKey);
                            logger.debug("HandleEvent, remove pub_key, pub_key size:{}", pubKeys.size());
                        }

                        continue;
                    }

                    if (Objects.equals(MychainUtils.MYCHAIN_V3_SYSTEM_NODE_ADD_SIGN_HEX, log.getTopics().get(0)) ||
                            Objects.equals(MychainUtils.MYCHAIN_V3_SYSTEM_NODE_UPDATE_SIGN_HEX, log.getTopics().get(0)) ||
                            Objects.equals(MychainUtils.MYCHAIN_V3_SYSTEM_NODE_ACTIVATE_SIGN_HEX, log.getTopics().get(0))) {
                        EVMOutput evmDecoder = new EVMOutput(Hex.toHexString(log.getLogData()));
                        //nodePubkey
                        evmDecoder.getBytes();
                        //nodeState
                        evmDecoder.getInt();
                        String nodeInfoStr = evmDecoder.getString();
                        NodeInfo nodeInfo = serializeNodeInfo(nodeInfoStr);
                        pubKey = nodeInfo.getPublicKey();
                        pubKeys.remove(pubKey);
                        if (nodeInfo.getContractNodeStatusEnum().getCode() == ContractNodeState.NODE_STATE_NORMAL.getValue() &&
                                nodeInfo.getNodeRoleEnum().getCode() == NodeRoleEnum.NODE_ROLE_CONSENSUS.getCode()) {
                            pubKeys.add(pubKey);
                            logger.debug("HandleEvent, pub_key size {}", pubKeys.size());
                        }
                        continue;
                    }

                    if (Objects.equals(MychainUtils.MYCHAIN_V3_SYSTEM_NODE_DELETE_SIGN_HEX, log.getTopics().get(0))) {
                        EVMOutput evmDecoder = new EVMOutput(Hex.toHexString(log.getLogData()));
                        //nodePubkey
                        evmDecoder.getBytes();
                        //nodeState
                        evmDecoder.getInt();
                        String nodeInfoStr = evmDecoder.getString();
                        NodeInfo nodeInfo = serializeNodeInfo(nodeInfoStr);
                        pubKey = nodeInfo.getPublicKey();
                        pubKeys.remove(pubKey);
                        logger.debug("HandleEvent, remove pub_key, pub_key size: {}", pubKeys.size());
                    }
                }
            }

            index++;
        }

        return pubKeys;
    }

    private PublicKey parseLogData(byte[] logData) {
        return new PublicKey(ByteUtils.toHexString(decodeReturnData(logData, 0)));
    }

    private byte[] decodeReturnData(byte[] data, int pos) {
        byte[] temp = new byte[32];
        System.arraycopy(data, pos * 32, temp, 0, 32);
        BigInteger offset = new BigInteger(temp);

        byte[] countBytes = new byte[32];

        System.arraycopy(data, offset.intValue(), countBytes, 0, 32);
        BigInteger count = new BigInteger(countBytes);

        byte[] decodedValue = new byte[count.intValue()];
        System.arraycopy(data, offset.intValue() + 32, decodedValue, 0, count.intValue());
        return decodedValue;
    }

    private NodeInfo serializeNodeInfo(String nodeInfoStr) {
        JSONObject jsonObject = JSONObject.parseObject(nodeInfoStr);
        Hash nodeId = new Hash(jsonObject.getString("nodeId"));
        List<EndPoint> endPoints = new ArrayList<>();
//        JSONArray jsonArray = jsonObject.getJSONArray("endpoints");
        PublicKey publicKey = new PublicKey(jsonObject.getString("publicKey"));
        NodeRoleEnum nodeRole = NodeRoleEnum.getNodeRoleEnumByCode(jsonObject.getIntValue("nodeType"));
        ContractNodeStatusEnum nodeState = ContractNodeStatusEnum.getContractNodeStatusEnumByCode(jsonObject.getIntValue("nodeState"));
        NodeInfo nodeInfo = NodeInfo.builder(nodeId, endPoints, publicKey, nodeRole, nodeState);
//        node_info.domainName = root.get("domainName", std::string());
//        node_info.nodeACRule.FromString(root.get("nodesACRule", std::string()));
//        node_info.dkg_node_id = root.get<uint32_t>("dkg_node_id", 0);
//        node_info.node_pending_state = NodePendingState(root.get<uint8_t>("node_pending_state", 0));
        return nodeInfo;
    }

    private boolean verifyReceipt(BlockHeader header, Vector<Hash> hashes, int receiptHashIndex, TransactionReceipt receipt, Logger logger) {
        if (!new Hash(HashFactory.getHash(this.mychainHashType).hash(receipt.toRlp())).equals(hashes.get(receiptHashIndex))) {
            logger.error("verifyReceipt, check receipt hash failed");
            return false;
        }

        if (!MerkleTree.root(hashes, mychainHashType).hexStrValue().equalsIgnoreCase(
                header.getReceiptRoot().hexStrValue())) {
            logger.error("verifyReceipt, check receipt root failed: (all hashes : {}, root in header: {}, but get: {})",
                    StrUtil.join(",", hashes),
                    header.getReceiptRoot().hexStrValue(),
                    MerkleTree.root(hashes, mychainHashType).hexStrValue()
            );
            return false;
        }

        return true;
    }
}
