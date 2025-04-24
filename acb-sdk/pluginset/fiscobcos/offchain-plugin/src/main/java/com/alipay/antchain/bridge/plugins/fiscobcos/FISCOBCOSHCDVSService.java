package com.alipay.antchain.bridge.plugins.fiscobcos;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.commons.core.base.ConsensusState;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.commons.core.bta.IBlockchainTrustAnchor;
import com.alipay.antchain.bridge.plugins.lib.HeteroChainDataVerifierService;
import com.alipay.antchain.bridge.plugins.spi.ptc.AbstractHCDVSService;
import com.alipay.antchain.bridge.plugins.spi.ptc.core.VerifyResult;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.crypto.hash.Keccak256;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.utils.MerkleProofUtility;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * FISCO BCOS 异构链数据验证服务
 * <p>
 * 用于验证来自 FISCO BCOS 区块链的共识状态和跨链消息
 * </p>
 */
@HeteroChainDataVerifierService(products = "fiscobcos", pluginId = "fiscobcos_hcdvsservice")
public class FISCOBCOSHCDVSService extends AbstractHCDVSService {

    private static final String SEND_AUTH_MESSAGE_TOPIC = "0x79b7516b1b7a6a39fb4b7b22e8667cd3744e5c27425292f8a9f49d1042c0c651";
    private final CryptoSuite cryptoSuite = new CryptoSuite(CryptoType.ECDSA_TYPE);

    /**
     * 查找并解析交易收据中的SendAuthMessage事件
     *
     * @param logEntries 交易收据中的日志条目
     * @return 包含事件信息的结果对象，如果未找到则返回null
     */
    private AuthMessageEventResult findAuthMessageEvent(JSONArray logEntries) {
        if (logEntries == null || logEntries.isEmpty()) {
            getHCDVSLogger().warn("交易收据不包含日志条目");
            return null;
        }

        for (int i = 0; i < logEntries.size(); i++) {
            JSONObject logEntry = logEntries.getJSONObject(i);
            JSONArray topics = logEntry.getJSONArray("topics");

            if (topics != null && !topics.isEmpty() && SEND_AUTH_MESSAGE_TOPIC.equalsIgnoreCase(topics.getString(0))) {
                String data = logEntry.getString("data");
                if (StrUtil.isNotEmpty(data)) {
                    byte[] amMessage = extractAMMessageFromData(data);
                    if (amMessage != null && amMessage.length > 0) {
                        return new AuthMessageEventResult(i, amMessage, logEntry);
                    }
                }
            }
        }

        return null;
    }

    /**
     * 从事件数据中提取AM消息
     *
     * @param data 事件数据的十六进制字符串
     * @return 提取的AM消息字节数组
     */
    private byte[] extractAMMessageFromData(String data) {
        try {
            // 移除0x前缀
            if (data.startsWith("0x")) {
                data = data.substring(2);
            }

            // 将hex字符串转换为字节数组
            byte[] dataBytes = HexUtil.decodeHex(data);

            // 前32字节是偏移量，指向数据开始的位置
            byte[] offsetBytes = new byte[32];
            System.arraycopy(dataBytes, 0, offsetBytes, 0, 32);
            BigInteger offsetBigInt = new BigInteger(1, offsetBytes); // 1表示正数
            int offset = offsetBigInt.intValue();

            // 读取数据长度
            byte[] lengthBytes = new byte[32];
            System.arraycopy(dataBytes, offset, lengthBytes, 0, 32);
            BigInteger lengthBigInt = new BigInteger(1, lengthBytes);
            int length = lengthBigInt.intValue();

            // 读取实际数据
            if (offset + 32 + length <= dataBytes.length) {
                byte[] messageBytes = new byte[length];
                System.arraycopy(dataBytes, offset + 32, messageBytes, 0, length);
                return messageBytes;
            } else {
                getHCDVSLogger().warn("数据长度超出范围: offset={}, length={}, dataLength={}",
                        offset, length, dataBytes.length);
                return new byte[0];
            }
        } catch (Exception e) {
            getHCDVSLogger().error("提取AM消息时发生异常: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    @Override
    public VerifyResult verifyAnchorConsensusState(IBlockchainTrustAnchor bta, ConsensusState anchorState) {
        String heightStr = anchorState.getHeight().toString();
        String hashHex = anchorState.getHashHex();
        String domain = bta.getDomain().toString();

        getHCDVSLogger().info("开始验证FISCO BCOS锚定共识状态 (height: {}, hash: {}) for domain {}", heightStr, hashHex, domain);

        try {
            // 1. 验证AM合约地址
            JSONObject btaSubjectIdentity = JSON.parseObject(new String(bta.getSubjectIdentity()));
            String amContractInBTA = btaSubjectIdentity.getString("amContract");
            byte[] amIdInBTA = HexUtil.decodeHex(StrUtil.removePrefix(amContractInBTA, "0x"));

            if (!checkAmContract(amIdInBTA, bta.getAmId())) {
                getHCDVSLogger().error("AM合约地址验证失败，BTA中AM地址为 {} 而锚定状态中为 {}",
                        amContractInBTA, HexUtil.encodeHexStr(bta.getAmId()));
                return VerifyResult.fail("AM合约地址不匹配");
            }

            // 2. 验证区块状态数据
            JSONObject stateDataJson = JSON.parseObject(new String(anchorState.getStateData()));
            if (!validateStateData(stateDataJson)) {
                return VerifyResult.fail("区块状态数据不完整");
            }

            // 3. 验证共识节点签名
            JSONObject endorsementsJson = JSON.parseObject(new String(anchorState.getEndorsements()));
            JSONObject consensusNodeInfoJson = JSON.parseObject(new String(anchorState.getConsensusNodeInfo()));
            JSONArray signatureListArray = endorsementsJson.getJSONArray("signatures");
            JSONArray sealerListArray = consensusNodeInfoJson.getJSONArray("sealerList");

            if (signatureListArray == null || sealerListArray == null) {
                getHCDVSLogger().error("签名列表或共识节点列表为空");
                return VerifyResult.fail("签名列表或共识节点列表为空");
            }

            // 计算所需的最小签名数量
            int minRequiredSignatures = calculateMinRequiredSignatures(sealerListArray.size());
            if (signatureListArray.size() < minRequiredSignatures) {
                getHCDVSLogger().error("签名数量不足，需要至少 {} 个签名，实际只有 {} 个",
                        minRequiredSignatures, signatureListArray.size());
                return VerifyResult.fail("签名数量不足");
            }

            // 验证签名
            int validSignatureCount = verifySignatures(signatureListArray, sealerListArray, hashHex);
            if (validSignatureCount < minRequiredSignatures) {
                getHCDVSLogger().error("有效签名数量不足，需要至少 {} 个有效签名，实际只有 {} 个",
                        minRequiredSignatures, validSignatureCount);
                return VerifyResult.fail("有效签名数量不足");
            }

            getHCDVSLogger().info("FISCO BCOS锚定共识状态验证成功 (height: {}, hash: {})", heightStr, hashHex);
            return VerifyResult.success();

        } catch (Exception e) {
            getHCDVSLogger().error("验证锚定共识状态失败", e);
            return VerifyResult.fail(String.format("验证锚定共识状态时发生异常: %s", e.getMessage()));
        }
    }

    private boolean validateStateData(JSONObject stateDataJson) {
        String receiptsRoot = stateDataJson.getString("receiptsRoot");
        String transactionsRoot = stateDataJson.getString("transactionsRoot");
        String stateRoot = stateDataJson.getString("stateRoot");

        if (StrUtil.isEmpty(receiptsRoot) || StrUtil.isEmpty(transactionsRoot) || StrUtil.isEmpty(stateRoot)) {
            getHCDVSLogger().error("区块状态数据不完整，缺少必要的根哈希信息");
            return false;
        }
        return true;
    }

    private int calculateMinRequiredSignatures(int totalNodes) {
        // FISCO BCOS使用PBFT，f = (n-1)/3 向下取整
        return totalNodes - (totalNodes - 1) / 3;
    }

    private int verifySignatures(JSONArray signatureListArray, JSONArray sealerListArray, String hashHex) {
        if (StrUtil.isEmpty(hashHex)) {
            getHCDVSLogger().error("区块哈希为空");
            return 0;
        }

        byte[] hashBytes = HexUtil.decodeHex(StrUtil.removePrefix(hashHex, "0x"));
        int validSignatureCount = 0;

        for (int i = 0; i < signatureListArray.size(); i++) {
            JSONObject signatureInfo = signatureListArray.getJSONObject(i);
            String indexStr = signatureInfo.getString("index");

            if (StrUtil.isEmpty(indexStr)) {
                getHCDVSLogger().warn("签名条目缺少index字段");
                continue;
            }

            int sealerIndex = Integer.parseInt(indexStr);

            if (sealerIndex < 0 || sealerIndex >= sealerListArray.size()) {
                getHCDVSLogger().warn("签名索引 {} 超出共识节点列表范围 {}", sealerIndex, sealerListArray.size());
                continue;
            }

            String nodeId = sealerListArray.getString(sealerIndex);
            String publicKeyHex = StrUtil.removePrefix(nodeId, "0x");

            try {
                byte[] signatureBytes = HexUtil.decodeHex(
                        StrUtil.removePrefix(signatureInfo.getString("signature"), "0x"));
                if (cryptoSuite.verify(publicKeyHex, hashBytes, signatureBytes)) {
                    validSignatureCount++;
                    getHCDVSLogger().debug("节点 {} 的签名验证成功", nodeId);
                } else {
                    getHCDVSLogger().warn("节点 {} 的签名验证失败", nodeId);
                }
            } catch (Exception e) {
                getHCDVSLogger().warn("验证节点 {} 的签名时发生异常: {}", nodeId, e.getMessage());
            }
        }

        return validSignatureCount;
    }

    /**
     * 检查AM合约地址是否匹配
     *
     * @param amIdInBTA   BTA中的AM合约地址
     * @param amIdInState 锚定状态中的AM合约地址
     * @return 是否匹配
     */
    private boolean checkAmContract(byte[] amIdInBTA, byte[] amIdInState) {
        // FISCO BCOS合约地址是20字节，而BTA中的amId是32字节，需要特殊处理
        if (amIdInBTA.length == 20 && amIdInState.length == 32) {
            // 如果BTA中的是20字节，state中的是32字节，则比较后20字节
            byte[] trimmedStateAmId = Arrays.copyOfRange(amIdInState, 12, 32);
            return Arrays.equals(amIdInBTA, trimmedStateAmId);
        } else if (amIdInBTA.length == 32 && amIdInState.length == 32) {
            // 如果都是32字节，直接比较
            return Arrays.equals(amIdInBTA, amIdInState);
        } else {
            // 其他情况，认为不匹配
            return false;
        }
    }

    @Override
    public VerifyResult verifyConsensusState(ConsensusState stateToVerify, ConsensusState parentState) {
        String parentHeightStr = parentState.getHeight().toString();
        String childHeightStr = stateToVerify.getHeight().toString();
        String parentHashHex = parentState.getHashHex();
        String childHashHex = stateToVerify.getHashHex();

        getHCDVSLogger().info("开始验证FISCO BCOS共识状态 (child height: {}, hash: {}) 使用父区块 (height: {}, hash: {})",
                childHeightStr, childHashHex, parentHeightStr, parentHashHex);

        try {
            // 1. 解析状态数据
            JSONObject parentStateDataJson = JSON.parseObject(new String(parentState.getStateData()));
            JSONObject childStateDataJson = JSON.parseObject(new String(stateToVerify.getStateData()));

            // 2. 验证区块状态数据完整性
            if (!validateStateData(parentStateDataJson)) {
                return VerifyResult.fail("父区块状态数据不完整");
            }
            if (!validateStateData(childStateDataJson)) {
                return VerifyResult.fail("子区块状态数据不完整");
            }

            // 3. 验证区块链接关系：检查parentHash
            String childParentHash = HexUtil.encodeHexStr(stateToVerify.getParentHash());
            if (!childParentHash.startsWith("0x")) {
                childParentHash = "0x" + childParentHash;
            }

            if (StrUtil.isEmpty(childParentHash) || !childParentHash.equalsIgnoreCase(parentHashHex)) {
                getHCDVSLogger().error("区块链接关系验证失败: 子区块parentHash {} 与父区块hash {} 不匹配",
                        childParentHash, parentHashHex);
                return VerifyResult.fail("区块链接关系验证失败");
            }

            // 4. 验证共识节点签名
            JSONObject parentConsensusNodeInfoJson = JSON.parseObject(new String(parentState.getConsensusNodeInfo()));
            JSONArray sealerListArray = parentConsensusNodeInfoJson.getJSONArray("sealerList");

            if (sealerListArray == null || sealerListArray.isEmpty()) {
                getHCDVSLogger().error("父区块中的共识节点列表为空");
                return VerifyResult.fail("共识节点列表为空");
            }

            JSONObject childEndorsementsJson = JSON.parseObject(new String(stateToVerify.getEndorsements()));
            JSONArray signatureListArray = childEndorsementsJson.getJSONArray("signatures");

            if (signatureListArray == null || signatureListArray.isEmpty()) {
                getHCDVSLogger().error("子区块的签名列表为空");
                return VerifyResult.fail("签名列表为空");
            }

            // 计算所需的最小签名数量
            int minRequiredSignatures = calculateMinRequiredSignatures(sealerListArray.size());
            if (signatureListArray.size() < minRequiredSignatures) {
                getHCDVSLogger().error("签名数量不足，需要至少 {} 个签名，实际只有 {} 个",
                        minRequiredSignatures, signatureListArray.size());
                return VerifyResult.fail("签名数量不足");
            }

            // 验证签名
            int validSignatureCount = verifySignatures(signatureListArray, sealerListArray, childHashHex);

            if (validSignatureCount < minRequiredSignatures) {
                getHCDVSLogger().error("有效签名数量不足，需要至少 {} 个有效签名，实际只有 {} 个",
                        minRequiredSignatures, validSignatureCount);
                return VerifyResult.fail("有效签名数量不足");
            }

            getHCDVSLogger().info("FISCO BCOS共识状态验证成功 (height: {}, hash: {})", childHeightStr, childHashHex);
            return VerifyResult.success();

        } catch (Exception e) {
            getHCDVSLogger().error("验证共识状态失败", e);
            return VerifyResult.fail(String.format("验证共识状态时发生异常: %s", e.getMessage()));
        }
    }

    @Override
    public VerifyResult verifyCrossChainMessage(CrossChainMessage message, ConsensusState currState) {
        if (message.getProvableData() == null) {
            getHCDVSLogger().error("跨链消息没有可证明数据");
            return VerifyResult.fail("跨链消息没有可证明数据");
        }

        String txHash = HexUtil.encodeHexStr(message.getProvableData().getTxHash());
        if (!txHash.startsWith("0x")) {
            txHash = "0x" + txHash;
        }

        long messageHeight = message.getProvableData().getHeight();
        long stateHeight = currState.getHeight().longValue();

        getHCDVSLogger().info("开始验证FISCO BCOS跨链消息 (txHash: {}, blockHeight: {})", txHash, messageHeight);

        try {
            // 1. 验证消息高度与当前状态高度匹配
            if (messageHeight != stateHeight) {
                getHCDVSLogger().error("消息高度 {} 与当前状态高度 {} 不匹配", messageHeight, stateHeight);
                return VerifyResult.fail("消息高度与当前状态高度不匹配");
            }

            // 2. 从当前状态中获取收据根哈希
            JSONObject stateDataJson = JSON.parseObject(new String(currState.getStateData()));
            String receiptsRootInState = stateDataJson.getString("receiptsRoot");
            if (StrUtil.isEmpty(receiptsRootInState)) {
                getHCDVSLogger().error("当前状态中缺少收据根哈希");
                return VerifyResult.fail("当前状态中缺少收据根哈希");
            }

            // 3. 从ledgerData解析交易收据
            String receiptJson = new String(message.getProvableData().getLedgerData());
            JSONObject receiptObj = JSON.parseObject(receiptJson);

            // 验证交易哈希
            String receiptTxHash = receiptObj.getString("transactionHash");
            if (!txHash.equalsIgnoreCase(receiptTxHash)) {
                getHCDVSLogger().error("交易收据哈希 {} 与消息中的交易哈希 {} 不匹配", receiptTxHash, txHash);
                return VerifyResult.fail("交易收据哈希与消息中的交易哈希不匹配");
            }

            // 4. 从proof中解析Merkle证明
            String proofJson = new String(message.getProvableData().getProof());
            JSONObject proofObj = JSON.parseObject(proofJson);
            String receiptHash = proofObj.getString("receiptHash");
            JSONArray txReceiptProof = proofObj.getJSONArray("txReceiptProof");
            String receiptsRoot = proofObj.getString("receiptsRoot");

            if (StrUtil.isEmpty(receiptHash) || txReceiptProof == null || txReceiptProof.isEmpty() || StrUtil.isEmpty(receiptsRoot)) {
                getHCDVSLogger().error("Merkle证明数据不完整");
                return VerifyResult.fail("Merkle证明数据不完整");
            }

            // 验证收据根与状态中的收据根一致
            if (!receiptsRoot.equalsIgnoreCase(receiptsRootInState)) {
                getHCDVSLogger().error("证明中的收据根 {} 与状态中的收据根 {} 不匹配", receiptsRoot, receiptsRootInState);
                return VerifyResult.fail("收据根不匹配");
            }

            // 5. 使用MerkleProof验证交易收据
            List<String> proofNodes = new ArrayList<>();
            for (int i = 0; i < txReceiptProof.size(); i++) {
                proofNodes.add(txReceiptProof.getString(i));
            }

            // 使用Keccak256作为哈希算法进行验证
            boolean proofValid = false;
            try {
                proofValid = MerkleProofUtility.verifyMerkle(
                        receiptsRoot,
                        proofNodes,
                        receiptHash,
                        new Keccak256()
                );

                if (!proofValid) {
                    getHCDVSLogger().error("Merkle证明验证失败");
                    return VerifyResult.fail("Merkle证明验证失败");
                }
            } catch (Exception e) {
                getHCDVSLogger().error("Merkle证明验证时发生异常", e);
                return VerifyResult.fail("Merkle证明验证异常: " + e.getMessage());
            }

            // 6. 验证交易收据中是否包含SendAuthMessage事件
            JSONArray logEntries = receiptObj.getJSONArray("logEntries");
            AuthMessageEventResult eventResult = findAuthMessageEvent(logEntries);

            if (eventResult == null) {
                getHCDVSLogger().error("交易收据中未找到SendAuthMessage事件");
                return VerifyResult.fail("交易收据中未找到SendAuthMessage事件");
            }

            // 验证消息内容是否与跨链消息一致
            if (!Arrays.equals(eventResult.getMessage(), message.getMessage())) {
                getHCDVSLogger().error("事件中的消息内容与跨链消息不一致");
                return VerifyResult.fail("事件中的消息内容与跨链消息不一致");
            }

            getHCDVSLogger().info("FISCO BCOS跨链消息验证成功 (txHash: {})", txHash);
            return VerifyResult.success();

        } catch (Exception e) {
            getHCDVSLogger().error("验证跨链消息时发生异常", e);
            return VerifyResult.fail("验证跨链消息异常: " + e.getMessage());
        }
    }

    @Override
    public byte[] parseMessageFromLedgerData(byte[] ledgerData) {
        getHCDVSLogger().info("开始从ledgerData解析AM消息原文");

        try {
            // 1. 将ledgerData反序列化为JSON对象
            String receiptJson = new String(ledgerData);
            JSONObject receiptObj = JSON.parseObject(receiptJson);

            // 2. 查找并解析SendAuthMessage事件
            JSONArray logEntries = receiptObj.getJSONArray("logEntries");
            AuthMessageEventResult eventResult = findAuthMessageEvent(logEntries);

            if (eventResult != null) {
                getHCDVSLogger().info("成功从ledgerData解析AM消息, 大小: {} 字节", eventResult.getMessage().length);
                return eventResult.getMessage();
            }

            // 3. 没有找到有效的SendAuthMessage事件
            getHCDVSLogger().warn("在交易收据中未找到有效的SendAuthMessage事件数据");
            return new byte[0];

        } catch (Exception e) {
            getHCDVSLogger().error("从ledgerData解析AM消息时发生异常: {}", e.getMessage(), e);
            throw new RuntimeException("从ledgerData解析AM消息失败", e);
        }
    }

    /**
     * SendAuthMessage事件结果类，包含事件索引、消息内容和事件日志
     */
    private static class AuthMessageEventResult {
        private final int index;
        private final byte[] message;
        private final JSONObject logEntry;

        public AuthMessageEventResult(int index, byte[] message, JSONObject logEntry) {
            this.index = index;
            this.message = message;
            this.logEntry = logEntry;
        }

        public int getIndex() {
            return index;
        }

        public byte[] getMessage() {
            return message;
        }

        public JSONObject getLogEntry() {
            return logEntry;
        }
    }
}