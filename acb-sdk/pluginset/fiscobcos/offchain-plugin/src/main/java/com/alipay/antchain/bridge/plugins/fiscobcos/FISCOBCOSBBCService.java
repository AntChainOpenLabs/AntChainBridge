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
package com.alipay.antchain.bridge.plugins.fiscobcos;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.AuthMessageContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.bbc.syscontract.PTCContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.SDPContract;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.CrossChainCertificateTypeEnum;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.ptc.*;
import com.alipay.antchain.bridge.commons.core.rcc.ReliableCrossChainMessage;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.plugins.fiscobcos.abi.AuthMsg;
import com.alipay.antchain.bridge.plugins.fiscobcos.abi.CommitteePtcVerifier;
import com.alipay.antchain.bridge.plugins.fiscobcos.abi.PtcHub;
import com.alipay.antchain.bridge.plugins.fiscobcos.abi.SDPMsg;
import com.alipay.antchain.bridge.plugins.lib.BBCService;
import com.alipay.antchain.bridge.plugins.spi.bbc.AbstractBBCService;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeEndorseProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.trustroot.CommitteeVerifyAnchor;
import com.webank.wedpr.crypto.NativeInterface;
import lombok.Getter;
import lombok.SneakyThrows;
import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.client.protocol.response.BcosBlock;
import org.fisco.bcos.sdk.v3.client.protocol.response.BcosBlockHeader;
import org.fisco.bcos.sdk.v3.codec.ContractCodec;
import org.fisco.bcos.sdk.v3.codec.ContractCodecException;
import org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes;
import org.fisco.bcos.sdk.v3.config.ConfigOption;
import org.fisco.bcos.sdk.v3.config.model.*;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.eventsub.EventSubParams;
import org.fisco.bcos.sdk.v3.eventsub.EventSubscribe;
import org.fisco.bcos.sdk.v3.model.EventLog;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.transaction.codec.decode.TransactionDecoderInterface;
import org.fisco.bcos.sdk.v3.transaction.codec.decode.TransactionDecoderService;
import org.fisco.bcos.sdk.v3.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.v3.transaction.manager.TransactionProcessorFactory;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static com.alipay.antchain.bridge.plugins.fiscobcos.abi.AuthMsg.SENDAUTHMESSAGE_EVENT;

@BBCService(products = "fiscobcos", pluginId = "plugin-fiscobcos")
@Getter
public class FISCOBCOSBBCService extends AbstractBBCService {
    private FISCOBCOSConfig config;

    private BcosSDK sdk;
    private Client client;
    private CryptoKeyPair keyPair;
    private ContractCodec contractCodec;
    private AssembleTransactionProcessor transactionProcessorAM;
    private AssembleTransactionProcessor transactionProcessorSDP;
    private AssembleTransactionProcessor transactionProcessorPTC;

    private AbstractBBCContext bbcContext;

    @Override
    @SneakyThrows
    public void startup(AbstractBBCContext abstractBBCContext) {
        getBBCLogger().info("FISCO-BCOS BBCService startup with context: {}", new String(abstractBBCContext.getConfForBlockchainClient()));

        // init NativeInterface
        Future<?> future = ThreadUtil.execAsync(() -> {
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
            NativeInterface.secp256k1GenKeyPair();
        });
        future.get();

        if (ObjectUtil.isNull(abstractBBCContext)) {
            throw new RuntimeException("null bbc context");
        }
        if (ObjectUtil.isEmpty(abstractBBCContext.getConfForBlockchainClient())) {
            throw new RuntimeException("empty blockchain client conf");
        }

        // 1. obtain the configuration information
        try {
            config = FISCOBCOSConfig.fromJsonString(new String(abstractBBCContext.getConfForBlockchainClient()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (StrUtil.isEmpty(config.getCaCert())) {
            throw new RuntimeException("CA certification is empty");
        }

        if (StrUtil.isEmpty(config.getSslCert())) {
            throw new RuntimeException("SSL certification is empty");
        }

        if (StrUtil.isEmpty(config.getSslKey())) {
            throw new RuntimeException("SSL key is empty");
        }

        if (StrUtil.isEmpty(config.getConnectPeer())) {
            throw new RuntimeException("Address of peer to connect is empty");
        }

        if (StrUtil.isEmpty(config.getGroupID())) {
            throw new RuntimeException("groupID to which the connected node belongs is empty");
        }

        // 2. connect to the FISCO-BCOS network
        try {
            ConfigProperty configProperty = new ConfigProperty();

            // 实例化 cryptoMaterial
            Map<String, Object> cryptoMaterial = new HashMap<>();
            cryptoMaterial.put("useSMCrypto", config.getUseSMCrypto());
            cryptoMaterial.put("disableSsl", config.getDisableSsl());
            configProperty.cryptoMaterial = cryptoMaterial;

            // 实例化 network
            Map<String, Object> network = new HashMap<>();
            network.put("messageTimeout", config.getMessageTimeout());
            network.put("defaultGroup", config.getDefaultGroup());
            network.put("peers", new ArrayList<>(Collections.singletonList(config.getConnectPeer())));
            configProperty.network = network;

            // 实例化 account
            Map<String, Object> account = new HashMap<>();
            account.put("keyStoreDir", config.getKeyStoreDir());
            account.put("accountFileFormat", config.getAccountFileFormat());
            configProperty.account = account;

            // 实例化 threadPool
            configProperty.threadPool = new HashMap<>();

            // 实例化 amop
            configProperty.amop = new ArrayList<>();

            ConfigOption configOption = new ConfigOption();

            CryptoMaterialConfig cryptoMaterialConfig = new CryptoMaterialConfig();
            cryptoMaterialConfig.setCaCert(config.getCaCert());
            cryptoMaterialConfig.setSdkCert(config.getSslCert());
            cryptoMaterialConfig.setSdkPrivateKey(config.getSslKey());
            configOption.setCryptoMaterialConfig(cryptoMaterialConfig);

            configOption.setAccountConfig(new AccountConfig(configProperty));
            configOption.setAmopConfig(new AmopConfig(configProperty));
            configOption.setNetworkConfig(new NetworkConfig(configProperty));
            configOption.setThreadPoolConfig(new ThreadPoolConfig(configProperty));

            configOption.setJniConfig(configOption.generateJniConfig());
            configOption.setConfigProperty(configProperty);

            // Initialize BcosSDK
            sdk = new BcosSDK(configOption);
            // Initialize the client for the group
            client = sdk.getClient(config.getGroupID());

        } catch (Exception e) {
            throw new RuntimeException(String.format("failed to connect fisco-bcos to peer:%s, group:%s", config.getConnectPeer(), config.getGroupID()), e);
        }

        // 3. initialize keypair and create transaction processor
        this.keyPair = client.getCryptoSuite().getCryptoKeyPair();
        // AM
        this.transactionProcessorAM = TransactionProcessorFactory.createAssembleTransactionProcessor(
                client,
                keyPair,
                "AuthMsg",
                AuthMsg.ABI_ARRAY[0],
                AuthMsg.BINARY_ARRAY[0]
        );
        // SDP
        this.transactionProcessorSDP = TransactionProcessorFactory.createAssembleTransactionProcessor(
                client,
                keyPair,
                "SDPMsg",
                SDPMsg.ABI_ARRAY[0],
                SDPMsg.BINARY_ARRAY[0]
        );
        // PtcHub
        this.transactionProcessorPTC = TransactionProcessorFactory.createAssembleTransactionProcessor(
                client,
                keyPair,
                "PtcHub",
                PtcHub.ABI_ARRAY[0],
                PtcHub.BINARY_ARRAY[0]
        );
        this.contractCodec = new ContractCodec(client.getCryptoSuite(), false);

        // 4. set context
        this.bbcContext = abstractBBCContext;

        // 5. set the pre-deployed contracts into context
        if (ObjectUtil.isNull(abstractBBCContext.getAuthMessageContract())
                && StrUtil.isNotEmpty(this.config.getAmContractAddressDeployed())) {
            AuthMessageContract authMessageContract = new AuthMessageContract();
            authMessageContract.setContractAddress(this.config.getAmContractAddressDeployed());
            authMessageContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            this.bbcContext.setAuthMessageContract(authMessageContract);
        }

        if (ObjectUtil.isNull(abstractBBCContext.getSdpContract())
                && StrUtil.isNotEmpty(this.config.getSdpContractAddressDeployed())) {
            SDPContract sdpContract = new SDPContract();
            sdpContract.setContractAddress(this.config.getSdpContractAddressDeployed());
            sdpContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            this.bbcContext.setSdpContract(sdpContract);
        }

        if (ObjectUtil.isNull(abstractBBCContext.getPtcContract())
                && StrUtil.isNotEmpty(this.config.getPtcHubContractAddressDeployed())) {
            PTCContract ptcContract = new PTCContract();
            ptcContract.setContractAddress(this.config.getPtcHubContractAddressDeployed());
            ptcContract.setStatus(ContractStatusEnum.CONTRACT_READY);
            this.bbcContext.setPtcContract(ptcContract);
        }

        getBBCLogger().info("FISCO-BCOS BBCService startup success for {}", this.config.getConnectPeer());
    }

    @Override
    public void shutdown() {
        getBBCLogger().info("shut down FISCO-BCOS BBCService!");
        this.client.stop();
    }

    @Override
    public AbstractBBCContext getContext() {
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }

        getBBCLogger().debug("FISCO-BCOS BBCService context (amAddr: {}, amStatus: {}, sdpAddr: {}, sdpStatus: {}, ptcAddr: {}, ptcStatus: {})",
                this.bbcContext.getAuthMessageContract() != null ? this.bbcContext.getAuthMessageContract().getContractAddress() : "",
                this.bbcContext.getAuthMessageContract() != null ? this.bbcContext.getAuthMessageContract().getStatus() : "",
                this.bbcContext.getSdpContract() != null ? this.bbcContext.getSdpContract().getContractAddress() : "",
                this.bbcContext.getSdpContract() != null ? this.bbcContext.getSdpContract().getStatus() : "",
                this.bbcContext.getPtcContract() != null ? this.bbcContext.getPtcContract().getContractAddress() : "",
                this.bbcContext.getPtcContract() != null ? this.bbcContext.getPtcContract().getStatus() : ""
        );

        return this.bbcContext;
    }

    @Override
    public CrossChainMessageReceipt readCrossChainMessageReceipt(String txHash) {
        // 1. Obtain FISCO-BCOS receipt according to transaction hash
        TransactionReceipt transactionReceipt;

        try {
            transactionReceipt = client.getTransactionReceipt(txHash, false).getTransactionReceipt();
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to read cross chain message receipt (txHash: %s)", txHash
                    ), e
            );
        }

        // 2. Construct cross-chain message receipt
        CrossChainMessageReceipt crossChainMessageReceipt = getCrossChainMessageReceipt(transactionReceipt);
        getBBCLogger().info("cross chain message receipt for txhash {} : {}", txHash, JSON.toJSONString(crossChainMessageReceipt));

        return crossChainMessageReceipt;
    }

    private CrossChainMessageReceipt getCrossChainMessageReceipt(TransactionReceipt transactionReceipt) {
        CrossChainMessageReceipt crossChainMessageReceipt = new CrossChainMessageReceipt();
        if (transactionReceipt == null) {
            // If the transaction is not packaged, the return receipt is empty
            crossChainMessageReceipt.setConfirmed(false);
            crossChainMessageReceipt.setSuccessful(false);
            crossChainMessageReceipt.setTxhash("");
            crossChainMessageReceipt.setErrorMsg("");
            return crossChainMessageReceipt;
        }

        // Check if the transaction is confirmed by comparing with current block number
        BigInteger currBlockNum = BigInteger.valueOf(queryLatestHeight());
        if (transactionReceipt.getBlockNumber().compareTo(currBlockNum) > 0) {
            crossChainMessageReceipt.setConfirmed(false);
            crossChainMessageReceipt.setSuccessful(true);
            crossChainMessageReceipt.setTxhash(transactionReceipt.getTransactionHash());
            crossChainMessageReceipt.setErrorMsg("");
            return crossChainMessageReceipt;
        }

        // Load SDP contract and get events
        SDPMsg sdpMsg = SDPMsg.load(
                this.bbcContext.getSdpContract().getContractAddress(),
                this.client,
                this.keyPair
        );
        List<SDPMsg.ReceiveMessageEventResponse> receiveMessageEventResponses = sdpMsg.getReceiveMessageEvents(transactionReceipt);

        if (ObjectUtil.isNotEmpty(receiveMessageEventResponses)) {
            SDPMsg.ReceiveMessageEventResponse response = receiveMessageEventResponses.get(0);
            crossChainMessageReceipt.setConfirmed(true);
            crossChainMessageReceipt.setSuccessful(transactionReceipt.isStatusOK() && response.result);
            crossChainMessageReceipt.setTxhash(transactionReceipt.getTransactionHash());
            crossChainMessageReceipt.setErrorMsg(
                    transactionReceipt.isStatusOK() ? StrUtil.format(
                            "SDP calls biz contract: {}", response.result ? "SUCCESS" : response.errMsg
                    ) : StrUtil.emptyToDefault(transactionReceipt.getMessage(), "")
            );

            getBBCLogger().info(
                    "event receiveMessage from SDP contract is found in tx {} of block {} : " +
                            "( send_domain: {}, sender: {}, receiver: {}, biz_call: {}, err_msg: {} )",
                    transactionReceipt.getTransactionHash(),
                    transactionReceipt.getBlockNumber(),
                    response.senderDomain,
                    HexUtil.encodeHexStr(response.senderID),
                    response.receiverID,
                    response.result.toString(),
                    response.errMsg
            );
            return crossChainMessageReceipt;
        }

        crossChainMessageReceipt.setConfirmed(true);
        crossChainMessageReceipt.setSuccessful(transactionReceipt.isStatusOK());
        crossChainMessageReceipt.setTxhash(transactionReceipt.getTransactionHash());
        crossChainMessageReceipt.setErrorMsg(StrUtil.emptyToDefault(transactionReceipt.getMessage(), ""));

        return crossChainMessageReceipt;
    }

    @Override
    public List<CrossChainMessage> readCrossChainMessagesByHeight(long height) {
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }

        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())) {
            throw new RuntimeException("empty am contract in bbc context");
        }

        try {
            // 2. get block
            BcosBlock.Block block = client.getBlockByNumber(BigInteger.valueOf(height), false, true).getBlock();
            if (block == null) {
                getBBCLogger().info("Block not found at height {}", height);
                return ListUtil.empty();
            }

            // 3. read messages according to scan policy
            List<CrossChainMessage> messageList;
            switch (config.getMsgScanPolicy()) {
                case BLOCK_SCAN:
                    messageList = readMessagesFromEntireBlock(block);
                    break;
                case LOG_FILTER:
                    messageList = readMessagesByFilter(block);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported message scan policy: " + config.getMsgScanPolicy());
            }

            // 4. log results
            if (!messageList.isEmpty()) {
                getBBCLogger().info("read cross chain messages (height: {}, msg_size: {})", height, messageList.size());
                getBBCLogger().debug("read cross chain messages (height: {}, msgs: {})",
                        height,
                        messageList.stream().map(JSON::toJSONString).collect(Collectors.joining(","))
                );
            }

            return messageList;
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to readCrossChainMessagesByHeight (Height: %d, contractAddr: %s, topic: %s)",
                            height,
                            this.bbcContext.getAuthMessageContract().getContractAddress(),
                            SENDAUTHMESSAGE_EVENT
                    ), e
            );
        }
    }

    private List<CrossChainMessage> readMessagesByFilter(BcosBlock.Block block) {
        List<CrossChainMessage> messageList = new ArrayList<>();
        String contractAddress = this.bbcContext.getAuthMessageContract().getContractAddress();

        // 创建事件订阅参数
        EventSubParams params = new EventSubParams();
        params.setFromBlock(BigInteger.valueOf(block.getNumber()));  // 从当前区块开始
        params.setToBlock(BigInteger.valueOf(block.getNumber()));    // 到当前区块结束
        params.addAddress(contractAddress);      // 指定合约地址

        try {
            // 初始化事件订阅
            EventSubscribe eventSubscribe = EventSubscribe.build(client.getGroup(), client.getConfigOption());
            eventSubscribe.start();

            // 添加SendAuthMessage事件的topic
            params.addTopic(0, String.valueOf(SENDAUTHMESSAGE_EVENT));

            // 创建信号量用于同步
            Semaphore semaphore = new Semaphore(1, true);
            semaphore.acquire();

            // 注册事件回调
            eventSubscribe.subscribeEvent(params, (eventId, status, logs) -> {
                try {
                    if (status == 0 && logs != null) {  // 正常推送
                        for (EventLog eventLog : logs) {
                            try {
                                // 解析事件数据
                                List<Object> eventData = contractCodec.decodeEventByTopic(
                                        AuthMsg.ABI_ARRAY[0],
                                        "SendAuthMessage",
                                        eventLog);

                                if (!eventData.isEmpty() && eventData.get(0) instanceof byte[]) {
                                    String txHash = eventLog.getTransactionHash();
                                    // 获取带证明的交易收据
                                    TransactionReceipt receipt = client.getTransactionReceipt(txHash, true).getTransactionReceipt();
                                    
                                    // 构造ledgerData：直接使用收据JSON序列化
                                    byte[] ledgerData = JSON.toJSONString(receipt).getBytes();
                                    
                                    // 构造proof：包含receiptHash、txReceiptProof和receiptsRoot
                                    Map<String, Object> proofMap = new HashMap<>();
                                    proofMap.put("receiptHash", receipt.getReceiptHash());
                                    proofMap.put("txReceiptProof", receipt.getTxReceiptProof());
                                    proofMap.put("receiptsRoot", block.getReceiptsRoot());
                                    
                                    byte[] proof = JSON.toJSONString(proofMap).getBytes();
                                    
                                    // 创建跨链消息
                                    messageList.add(CrossChainMessage.createCrossChainMessage(
                                            CrossChainMessage.CrossChainMessageType.AUTH_MSG,
                                            block.getNumber(),
                                            block.getTimestamp(),
                                            HexUtil.decodeHex(StrUtil.removePrefix(block.getHash().trim(), "0x")),
                                            (byte[]) eventData.get(0),
                                            ledgerData,
                                            proof,
                                            HexUtil.decodeHex(txHash.replaceFirst("^0x", ""))
                                    ));

                                    getBBCLogger().debug("成功解析SendAuthMessage事件: txHash={}", txHash);
                                }
                            } catch (Exception e) {
                                getBBCLogger().warn("解析SendAuthMessage事件失败: {}", e.getMessage());
                            }
                        }
                    }
                } finally {
                    if (status == 1) {  // 推送完成
                        semaphore.release();
                    }
                }
            });

            // 等待事件处理完成
            semaphore.acquire();
            semaphore.release();

            // 停止事件订阅
            eventSubscribe.stop();

        } catch (Exception e) {
            getBBCLogger().error("事件订阅处理失败: {}", e.getMessage());
        }

        return messageList;
    }

    /**
     * 读取区块中的跨链消息并添加正确的交易证明
     *
     * @param block 要扫描的区块
     * @return 跨链消息列表
     */
    private List<CrossChainMessage> readMessagesFromEntireBlock(BcosBlock.Block block) {
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }

        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())) {
            throw new RuntimeException("empty am contract in bbc context");
        }

        List<CrossChainMessage> messageList = new ArrayList<>();
        TransactionDecoderInterface decoder =
                new TransactionDecoderService(client.getCryptoSuite().getHashImpl(), false);

        // 处理区块中的所有交易
        for (String txHash : block.getTransactionHashes().stream()
                .map(hash -> hash.get())
                .collect(Collectors.toList())) {

            try {
                // 正确获取带证明的交易收据 - 关键是第二个参数为true
                TransactionReceipt receipt = client.getTransactionReceipt(txHash, true).getTransactionReceipt();

                if (receipt == null) {
                    continue;
                }

                // 解码交易收据中的事件
                Map<String, List<List<Object>>> events;
                try {
                    events = decoder.decodeEvents(AuthMsg.ABI_ARRAY[0], receipt.getLogEntries());
                } catch (ContractCodecException e) {
                    getBBCLogger().error("Failed to decode events: " + e.getMessage(), e);
                    continue;
                }

                // 处理SendAuthMessage事件
                List<List<Object>> authMessages = events.getOrDefault("SendAuthMessage", Collections.emptyList());
                for (List<Object> event : authMessages) {
                    try {
                        // 构造ledgerData: 直接使用收据JSON序列化
                        byte[] ledgerData = JSON.toJSONString(receipt).getBytes();

                        // 构造proof: 直接使用收据的txReceiptProof
                        Map<String, Object> proofMap = new HashMap<>();
                        proofMap.put("receiptHash", receipt.getReceiptHash());
                        proofMap.put("txReceiptProof", receipt.getTxReceiptProof());
                        proofMap.put("receiptsRoot", block.getReceiptsRoot());

                        byte[] proof = JSON.toJSONString(proofMap).getBytes();

                        // 创建跨链消息对象
                        CrossChainMessage message = CrossChainMessage.createCrossChainMessage(
                                CrossChainMessage.CrossChainMessageType.AUTH_MSG,
                                receipt.getBlockNumber().longValue(),
                                block.getTimestamp(),
                                HexUtil.decodeHex(StrUtil.removePrefix(block.getHash().trim(), "0x")),
                                (byte[]) event.get(0),  // 消息内容
                                ledgerData,
                                proof,
                                HexUtil.decodeHex(txHash.replaceFirst("^0x", ""))
                        );

                        messageList.add(message);
                    } catch (Exception e) {
                        getBBCLogger().error("Failed to process auth message: " + e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                getBBCLogger().error("Failed to process transaction: " + txHash, e);
            }
        }

        return messageList;
    }

    @Override
    public ConsensusState readConsensusState(BigInteger height) {
        getBBCLogger().info("🔍 开始获取高度 {} 的共识状态", height);
        
        try {
            // 1. 获取指定高度的区块信息
            BcosBlock.Block block = this.client.getBlockByNumber(BigInteger.valueOf(height.longValue()), false, false).getBlock();
            if (block == null) {
                getBBCLogger().warn("😠 无法获取高度为 {} 的区块", height);
                throw new RuntimeException("无法获取高度为 " + height + " 的区块");
            }
            
            // 2. 获取区块哈希
            byte[] blockHash = HexUtil.decodeHex(StrUtil.removePrefix(block.getHash(), "0x"));
            
            // 3. 获取父区块哈希
            List<BcosBlockHeader.ParentInfo> parentInfo = block.getParentInfo();
            byte[] parentHash = new byte[0];
            if (!parentInfo.isEmpty()) {
                parentHash = HexUtil.decodeHex(StrUtil.removePrefix(parentInfo.get(0).getBlockHash(), "0x"));
            }
            
            // 4. 获取区块时间戳（处理十六进制格式）
            long timestamp;
            String timestampStr = String.valueOf(block.getTimestamp());
            if (StrUtil.startWith(timestampStr, "0x")) {
                timestamp = Long.parseLong(StrUtil.removePrefix(timestampStr, "0x"), 16);
            } else {
                try {
                    timestamp = Long.parseLong(timestampStr);
                } catch (NumberFormatException e) {
                    getBBCLogger().warn("⚠️ 时间戳格式解析失败: {}, 使用当前时间", timestampStr);
                    timestamp = System.currentTimeMillis();
                }
            }
            
            // 5. 简化stateData ：用三个关键哈希值表示区块头
            JSONObject blockHeaderJson = new JSONObject();
            blockHeaderJson.put("transactionsRoot", block.getTransactionsRoot());
            blockHeaderJson.put("receiptsRoot", block.getReceiptsRoot());
            blockHeaderJson.put("stateRoot", block.getStateRoot());
            
            byte[] stateData = blockHeaderJson.toJSONString().getBytes();
            
            // 6. 获取共识节点信息
            byte[] consensusNodeInfo = getConsensusNodeInfo(block);
            
            // 7. 获取区块背书信息
            byte[] endorsements = getBlockEndorsements(block);
            
            // 8. 构造并返回共识状态
            ConsensusState consensusState = new ConsensusState(
                    height,
                    blockHash,
                    parentHash,
                    timestamp,
                    stateData,
                    consensusNodeInfo,
                    endorsements
            );
            
            getBBCLogger().info("🎉 成功获取高度 {} 的共识状态", height);
            return consensusState;
        } catch (Exception e) {
            getBBCLogger().error("🤯 获取共识状态失败: {}", e.getMessage(), e);
            throw new RuntimeException(
                    StrUtil.format("获取高度 %s 的共识状态失败", height),
                    e
            );
        }
    }

    /**
     * 获取共识节点信息
     *
     * @param block 区块
     * @return 共识节点信息的字节数组
     */
    private byte[] getConsensusNodeInfo(BcosBlock.Block block) {
        try {
            // 获取当前区块的共识节点列表
            List<String> sealerList = new ArrayList<>();

            // 从区块中获取共识节点信息
            if (block.getSealerList() != null && !block.getSealerList().isEmpty()) {
                sealerList.addAll(block.getSealerList());
            } else {
                getBBCLogger().warn("⚠️ 区块中无共识节点列表信息，使用空列表");
            }

            // 构建共识节点信息JSON
            JSONObject consensusInfo = new JSONObject();
            consensusInfo.put("sealerList", sealerList);

            return consensusInfo.toJSONString().getBytes();
        } catch (Exception e) {
            getBBCLogger().warn("⚠️ 获取共识节点信息失败，使用空字节", e);
            return "{}".getBytes();
        }
    }

    /**
     * 获取区块背书信息
     *
     * @param block 区块
     * @return 背书信息的字节数组
     */
    private byte[] getBlockEndorsements(BcosBlock.Block block) {
        try {
            // 获取区块签名列表
            List<BcosBlockHeader.Signature> signatureList = block.getSignatureList();
            if (signatureList == null || signatureList.isEmpty()) {
                return "[]".getBytes();
            }

            // 构建背书信息JSON
            JSONObject endorsements = new JSONObject();
            endorsements.put("signatures", signatureList);
            
            return endorsements.toJSONString().getBytes();
        } catch (Exception e) {
            getBBCLogger().warn("⚠️ 获取区块背书信息失败，使用空字节", e);
            return "{}".getBytes();
        }
    }


    @Override
    public Long queryLatestHeight() {
        Long l;
        try {
            l = client.getBlockNumber().getBlockNumber().longValue();
        } catch (Exception e) {
            throw new RuntimeException("failed to query latest height", e);
        }
        getBBCLogger().info("node: {}, latest height: {}", config.getConnectPeer(), l);
        return l;
    }

    @Override
    public long querySDPMessageSeq(String senderDomain, String senderID, String receiverDomain, String receiverID) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getSdpContract())) {
            throw new RuntimeException("empty sdp contract in bbc context");
        }

        // 2. load sdpMsg
        SDPMsg sdpMsg = SDPMsg.load(
                bbcContext.getSdpContract().getContractAddress(),
                this.client,
                this.keyPair
        );

        // 3. query sequence
        long seq;
        try {
            seq = sdpMsg.querySDPMessageSeq(
                    senderDomain,
                    HexUtil.decodeHex(senderID),
                    receiverDomain,
                    HexUtil.decodeHex(receiverID)
            ).longValue();

            getBBCLogger().info("sdpMsg seq: {} (senderDomain: {}, senderID: {}, receiverDomain: {}, receiverID: {})",
                    seq,
                    senderDomain,
                    senderID,
                    receiverDomain,
                    receiverID
            );
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "failed to query sdpMsg seq (senderDomain: %s, senderID: %s, receiverDomain: %s, receiverID: %s)",
                    senderDomain,
                    senderID,
                    receiverDomain,
                    receiverID
            ), e);
        }

        return seq;
    }

    @Override
    public void setProtocol(String protocolAddress, String protocolType) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())) {
            throw new RuntimeException("empty am contract in bbc context");
        }

        // 2. load am contract
        AuthMsg am = AuthMsg.load(
                this.bbcContext.getAuthMessageContract().getContractAddress(),
                this.client,
                this.keyPair
        );

        // 3. set protocol to am
        try {
            TransactionReceipt receipt = am.setProtocol(protocolAddress, BigInteger.valueOf(Long.parseLong(protocolType)));
            if (receipt.getStatus() == 0) {
                getBBCLogger().info(
                        "set protocol (address: {}, type: {}) to AM {} by tx {} ",
                        protocolAddress, protocolType,
                        this.bbcContext.getAuthMessageContract().getContractAddress(),
                        receipt.getTransactionHash()
                );
            } else {
                getBBCLogger().info(
                        "set protocol failed, receipt status code: {}",
                        receipt.getStatus()
                );
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to set protocol (address: %s, type: %s) to AM %s",
                            protocolAddress, protocolType, this.bbcContext.getAuthMessageContract().getContractAddress()
                    ), e
            );
        }

        // 4. update am contract status
        try {
            if (!StrUtil.isEmpty(am.getProtocol(BigInteger.ZERO))) {
                this.bbcContext.getAuthMessageContract().setStatus(ContractStatusEnum.CONTRACT_READY);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to update am contract status (address: %s)",
                            this.bbcContext.getAuthMessageContract().getContractAddress()
                    ), e);
        }
    }

    @Override
    public void setAmContract(String contractAddress) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getSdpContract())) {
            throw new RuntimeException("empty sdp contract in bbc context");
        }

        // 2. load sdp contract
        SDPMsg sdp = SDPMsg.load(
                this.bbcContext.getSdpContract().getContractAddress(),
                this.client,
                this.keyPair
        );

        // 3. set am to sdp
        try {
            TransactionReceipt receipt = sdp.setAmContract(contractAddress);
            if (receipt.getStatus() == 0) {
                getBBCLogger().info(
                        "set am contract (address: {}) to SDP {} by tx {}",
                        contractAddress,
                        this.bbcContext.getSdpContract().getContractAddress(),
                        receipt.getTransactionHash()
                );
            } else {
                getBBCLogger().info(
                        "set am contract failed, receipt status code: {}",
                        receipt.getStatus()
                );
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to set am contract (address: %s) to SDP %s",
                            contractAddress,
                            this.bbcContext.getSdpContract().getContractAddress()
                    ), e
            );
        }

        // 4. update sdp contract status
        try {
            if (!StrUtil.isEmpty(sdp.getAmAddress()) && !isByteArrayZero(sdp.getLocalDomain())) {
                this.bbcContext.getSdpContract().setStatus(ContractStatusEnum.CONTRACT_READY);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to update sdp contract status (address: %s)",
                            this.bbcContext.getSdpContract().getContractAddress()
                    ), e);
        }
    }

    @Override
    public void setLocalDomain(String domain) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (StrUtil.isEmpty(this.bbcContext.getSdpContract().getContractAddress())) {
            throw new RuntimeException("none sdp contract address");
        }

        // 2. load sdp contract
        SDPMsg sdp = SDPMsg.load(
                this.bbcContext.getSdpContract().getContractAddress(),
                this.client,
                this.keyPair
        );

        // 3. set domain to sdp
        try {
            TransactionReceipt receipt = sdp.setLocalDomain(domain);
            if (receipt.getStatus() == 0) {
                getBBCLogger().info(
                        "set domain ({}) to SDP {} by tx {}",
                        domain,
                        this.bbcContext.getSdpContract().getContractAddress(),
                        receipt.getTransactionHash()
                );
            } else {
                getBBCLogger().info(
                        "set domain failed, receipt status code: {}",
                        receipt.getStatus()
                );
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to set domain (%s) to SDP %s",
                            domain,
                            this.bbcContext.getSdpContract().getContractAddress()
                    ), e
            );
        }

        // 4. update sdp contract status
        try {
            if (!StrUtil.isEmpty(sdp.getAmAddress()) && !ObjectUtil.isEmpty(sdp.getLocalDomain())) {
                this.bbcContext.getSdpContract().setStatus(ContractStatusEnum.CONTRACT_READY);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to update sdp contract status (address: %s)",
                            this.bbcContext.getSdpContract().getContractAddress()
                    ), e);
        }
    }

    @Override
    public CrossChainMessageReceipt relayAuthMessage(byte[] rawMessage) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())) {
            throw new RuntimeException("empty am contract in bbc context");
        }

        String amContractAddress = this.bbcContext.getAuthMessageContract().getContractAddress();
        getBBCLogger().debug("relay AM {} to {} ", HexUtil.encodeHexStr(rawMessage), amContractAddress);

        try {
            // 2. create Transaction
            CrossChainMessageReceipt crossChainMessageReceipt = new CrossChainMessageReceipt();

            /*FISCO BCOS在使用sendCall预执行交易部分仍存在问题，待FISCO BCOS版本更新后升级该部分代码*/
//            // 2.1 预执行交易，检查是否会被回滚
//            CallResponse preExecResponse = transactionProcessorAM.sendCall(
//                    this.keyPair.getAddress(),  // from: 使用当前SDK的密钥对地址，确保与实际交易一致
//                    amContractAddress,     // to: 合约地址
//                    AuthMsg.ABI_ARRAY[0],  // abi: 合约ABI定义
//                    AuthMsg.FUNC_RECVPKGFROMRELAYER, // functionName: 要调用的合约函数名
//                    Collections.singletonList(new DynamicBytes(rawMessage)) // paramsList: 函数参数列表
//            );
//
//            // 2.2 如果预执行失败，设置相应的返回值
//            if (preExecResponse.getReturnCode() != 0 || preExecResponse.getReturnObject() == null) {
//                String errorMsg = preExecResponse.getReturnMessage();
//                getBBCLogger().error("call am contract {} reverted, reason: {}", amContractAddress, errorMsg);
//
//                // 检查是否是因为消息已处理导致的失败
//                boolean isNonceProcessed = StrUtil.contains(errorMsg, "nonce has been processed");
//
//                crossChainMessageReceipt.setSuccessful(false);
//                crossChainMessageReceipt.setConfirmed(isNonceProcessed); // 如果是已处理的消息，设置为已确认
//                crossChainMessageReceipt.setErrorMsg(errorMsg);
//                return crossChainMessageReceipt;
//            }

            // 2.1 发送实际交易
            TransactionResponse response = transactionProcessorAM.sendTransactionAndGetResponse(
                    amContractAddress,
                    AuthMsg.ABI_ARRAY[0],
                    AuthMsg.FUNC_RECVPKGFROMRELAYER,
                    Collections.singletonList(new DynamicBytes(rawMessage)));

            // 2.2 检查交易结果
            if (ObjectUtil.isNull(response)) {
                throw new RuntimeException("send transaction with null result");
            }
            
            TransactionReceipt receipt = response.getTransactionReceipt();
            if (ObjectUtil.isNull(receipt)) {
                throw new RuntimeException("transaction receipt is null");
            }
            
            if (StrUtil.isEmpty(receipt.getTransactionHash())) {
                throw new RuntimeException("transaction hash is empty");
            }
            
            // 2.3 设置返回值 注意：FISCO BCOS的交易是同步的，已经有了交易回执就可以直接设置confirmed为true
            crossChainMessageReceipt.setConfirmed(true);
            crossChainMessageReceipt.setSuccessful(receipt.isStatusOK());
            crossChainMessageReceipt.setTxhash(receipt.getTransactionHash());
            
            // 如果交易失败，记录详细错误信息
            if (!receipt.isStatusOK()) {
                String errorMsg = StrUtil.emptyToDefault(response.getReceiptMessages(), "Unknown error");
                crossChainMessageReceipt.setErrorMsg(errorMsg);
                getBBCLogger().error("relay AM to {} failed: {}", amContractAddress, errorMsg);
            } else {
                crossChainMessageReceipt.setErrorMsg("");
                getBBCLogger().info("relay AM to {} success, tx: {}", amContractAddress, receipt.getTransactionHash());
            }

            return crossChainMessageReceipt;
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("failed to relay AM %s to %s: %s",
                            HexUtil.encodeHexStr(rawMessage), 
                            amContractAddress,
                            e.getMessage()
                    ), e
            );
        }
    }

    @Override
    public void setupAuthMessageContract() {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNotNull(this.bbcContext.getAuthMessageContract())
                && StrUtil.isNotEmpty(this.bbcContext.getAuthMessageContract().getContractAddress())) {
            // If the contract has been pre-deployed and the contract address is configured in the configuration file,
            // there is no need to redeploy.
            return;
        }

        // 2. deploy contract
        AuthMsg authMsg;
        try {
            authMsg = AuthMsg.deploy(client, keyPair);
        } catch (Exception e) {
            throw new RuntimeException("failed to deploy authMsg", e);
        }

        // 3. get tx receipt
        TransactionReceipt transactionReceipt = authMsg.getDeployReceipt();

        // 4. check whether the deployment is successful
        if (!ObjectUtil.isNull(transactionReceipt) && transactionReceipt.getStatus() == 0) {
            AuthMessageContract authMessageContract = new AuthMessageContract();
            authMessageContract.setContractAddress(authMsg.getContractAddress());
            authMessageContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            bbcContext.setAuthMessageContract(authMessageContract);
            getBBCLogger().info("setup am contract successful: {}", authMsg.getContractAddress());
        } else {
            throw new RuntimeException("failed to get deploy authMsg tx receipt");
        }
    }

    @Override
    public void setupSDPMessageContract() {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNotNull(this.bbcContext.getSdpContract())
                && StrUtil.isNotEmpty(this.bbcContext.getSdpContract().getContractAddress())) {
            // If the contract has been pre-deployed and the contract address is configured in the configuration file,
            // there is no need to redeploy.
            return;
        }

        // 2. deploy contract
        SDPMsg sdpMsg;
        try {
            sdpMsg = SDPMsg.deploy(client, keyPair);
        } catch (Exception e) {
            throw new RuntimeException("failed to deploy sdpMsg", e);
        }

        // 3. get tx receipt
        TransactionReceipt transactionReceipt = sdpMsg.getDeployReceipt();

        // 4. check whether the deployment is successful
        if (!ObjectUtil.isNull(transactionReceipt) && transactionReceipt.getStatus() == 0) {
            SDPContract sdpContract = new SDPContract();
            sdpContract.setContractAddress(sdpMsg.getContractAddress());
            sdpContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            bbcContext.setSdpContract(sdpContract);
            getBBCLogger().info("setup sdp contract successful: {}", sdpMsg.getContractAddress());
        } else {
            throw new RuntimeException("failed to get deploy sdpMsg tx receipt");
        }
    }

    @Override
    public void setupPTCContract() {
        // 1. Check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNotNull(this.bbcContext.getPtcContract())
                && StrUtil.isNotEmpty(this.bbcContext.getPtcContract().getContractAddress())) {
            // If the contract has been pre-deployed and the contract address is configured in the configuration file,
            // there is no need to redeploy.
            getBBCLogger().info("PTC contract has been deployed: {}", this.bbcContext.getPtcContract().getContractAddress());
            return;
        }

        AbstractCrossChainCertificate bcdnsRootCert = CrossChainCertificateUtil.readCrossChainCertificateFromPem(
                this.config.getBcdnsRootCertPem().getBytes()
        );

        if (bcdnsRootCert.getType() != CrossChainCertificateTypeEnum.BCDNS_TRUST_ROOT_CERTIFICATE) {
            getBBCLogger().error("bcdns root cert in config is incorrect: {}", this.config.getBcdnsRootCertPem());
            throw new RuntimeException("incorrect bcdns root cert");
        }

        // 2. Deploy CommitteePtcVerifier contract
        String committeePtcVerifierAddr;
        CommitteePtcVerifier committeePtcVerifier;
        try {
            committeePtcVerifier = CommitteePtcVerifier.deploy(client, keyPair);
            TransactionReceipt verifierReceipt = committeePtcVerifier.getDeployReceipt();
            if (!ObjectUtil.isNull(verifierReceipt) && verifierReceipt.getStatus() == 0) {
                committeePtcVerifierAddr = committeePtcVerifier.getContractAddress();
                getBBCLogger().info("deploy contract committee verifier: {}", committeePtcVerifierAddr);
            } else {
                throw new RuntimeException("failed to get deploy CommitteePtcVerifier tx receipt");
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to deploy CommitteePtcVerifier contract", e);
        }

        // 3. Deploy PTC Hub contract
        String ptcHubAddr;
        try {
            PtcHub ptcHub = PtcHub.deploy(client, keyPair, bcdnsRootCert.encode());
            TransactionReceipt hubReceipt = ptcHub.getDeployReceipt();
            if (!ObjectUtil.isNull(hubReceipt) && hubReceipt.getStatus() == 0) {
                ptcHubAddr = ptcHub.getContractAddress();
                getBBCLogger().info("deploy contract ptc hub: {}", ptcHubAddr);
            } else {
                throw new RuntimeException("failed to get deploy PTC Hub tx receipt");
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to deploy PTC Hub contract", e);
        }

        // 4. Add CommitteePtcVerifier to PTC Hub
        try {
            PtcHub ptcHub = PtcHub.load(ptcHubAddr, client, keyPair);
            TransactionReceipt addVerifierReceipt = ptcHub.addPtcVerifier(committeePtcVerifierAddr);
            if (!ObjectUtil.isNull(addVerifierReceipt) && addVerifierReceipt.getStatus() == 0) {
                getBBCLogger().info(
                        "set committee verifier {} to ptc hub {} by tx {}",
                        committeePtcVerifierAddr,
                        ptcHubAddr,
                        addVerifierReceipt.getTransactionHash()
                );
            } else {
                throw new RuntimeException(
                        StrUtil.format(
                                "transaction {} shows failed when set committee verifier {} to ptc hub {}",
                                addVerifierReceipt.getTransactionHash(),
                                committeePtcVerifierAddr,
                                ptcHubAddr
                        )
                );
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "unexpected failure when setting committee verifier %s to ptc hub %s",
                            committeePtcVerifierAddr,
                            ptcHubAddr
                    ),
                    e
            );
        }

        // 5. Update context
        PTCContract ptcContract = new PTCContract();
        ptcContract.setContractAddress(ptcHubAddr);
        ptcContract.setStatus(ContractStatusEnum.CONTRACT_READY);
        bbcContext.setPtcContract(ptcContract);

        config.setPtcHubContractAddressDeployed(ptcHubAddr);

        getBBCLogger().info("setup ptc contract successful: {}", ptcHubAddr);
    }

    @Override
    public void setPtcContract(String ptcContractAddress) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())) {
            throw new RuntimeException("empty am contract in bbc context");
        }

        // 2. load AM contract
        AuthMsg am = AuthMsg.load(
                this.bbcContext.getAuthMessageContract().getContractAddress(),
                this.client,
                this.keyPair
        );

        // 3. set PTC contract address to AM
        try {
            TransactionReceipt receipt = am.setPtcHub(ptcContractAddress);
            
            if (receipt.getStatus() == 0) {
                getBBCLogger().info(
                        "set PTC contract (address: {}) to AM {} by tx {}",
                        ptcContractAddress,
                        this.bbcContext.getAuthMessageContract().getContractAddress(),
                        receipt.getTransactionHash()
                );
                
                // 4. initialize PTC contract in context if not exists
                if (ObjectUtil.isNull(this.bbcContext.getPtcContract())) {
                    this.bbcContext.setPtcContract(new PTCContract());
                }
                
                // 5. set contract address and status
                this.bbcContext.getPtcContract().setContractAddress(ptcContractAddress);
                this.bbcContext.getPtcContract().setStatus(ContractStatusEnum.CONTRACT_READY);
            } else {
                getBBCLogger().error(
                        "set PTC contract failed, receipt status code: {}",
                        receipt.getStatus()
                );
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to set PTC contract (address: %s) to AM %s",
                            ptcContractAddress,
                            this.bbcContext.getAuthMessageContract().getContractAddress()
                    ), e
            );
        }
    }

    @Override
    public void updatePTCTrustRoot(PTCTrustRoot ptcTrustRoot) {
        // 1. Check PTC contract status
        checkPtcContract();
        
        // 2. Load PTC Hub contract
        PtcHub ptcHub = PtcHub.load(
                this.bbcContext.getPtcContract().getContractAddress(),
                this.client,
                this.keyPair
        );
        
        try {
            // 3. Call contract to update PTC trust root
            TransactionReceipt receipt = ptcHub.updatePTCTrustRoot(ptcTrustRoot.encode());
            
            // 4. Check transaction result
            if (receipt.getStatus() == 0) {
                getBBCLogger().info(
                        "update PTC trust root successful by tx {} ",
                        receipt.getTransactionHash()
                );
            } else {
                throw new RuntimeException("transaction failed with status: " + receipt.getStatus());
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to update PTC trust root", e);
        }
    }

    @Override
    public void addTpBta(ThirdPartyBlockchainTrustAnchor tpbta) {
        // 1. Check PTC contract status
        checkPtcContract();
        
        try {
            // 2. Verify TP-BTA
            // 2.1 Check if PTC trust root exists
            if (!hasPTCTrustRoot(tpbta.getSignerPtcCredentialSubject().getApplicant())) {
                throw new RuntimeException("no ptc trust root found");
            }
            
            // 2.2 Check if PTC verify anchor exists
            if (!hasPTCVerifyAnchor(tpbta.getSignerPtcCredentialSubject().getApplicant(), tpbta.getPtcVerifyAnchorVersion())) {
                throw new RuntimeException("no ptc verify anchor found");
            }
            
            // 2.3 Get PTC verify anchor and check version
            PTCVerifyAnchor ptcVerifyAnchor = getPTCVerifyAnchor(tpbta.getSignerPtcCredentialSubject().getApplicant(), tpbta.getPtcVerifyAnchorVersion());
            if (!ptcVerifyAnchor.getVersion().equals(tpbta.getPtcVerifyAnchorVersion())) {
                throw new RuntimeException("verify anchor version not equal");
            }
            
            // 2.4 Verify committee endorsement
            CommitteeVerifyAnchor committeeVerifyAnchor = CommitteeVerifyAnchor.decode(ptcVerifyAnchor.getAnchor());
            CommitteeEndorseProof committeeEndorseProof = CommitteeEndorseProof.decode(tpbta.getEndorseProof());
            
            // 2.5 Check committee ID
            if (!StrUtil.equals(committeeVerifyAnchor.getCommitteeId(), committeeEndorseProof.getCommitteeId())) {
                throw new RuntimeException("committee id in proof not equal with the one in verify anchor");
            }
            
            // 2.6 Verify signatures
            byte[] encodedToSign = tpbta.getEncodedToSign();
            int correct = 0;
            for (int i = 0; i < committeeEndorseProof.getSigs().size(); i++) {
                CommitteeNodeProof info = committeeEndorseProof.getSigs().get(i);
                for (int j = 0; j < committeeVerifyAnchor.getAnchors().size(); j++) {
                    if (StrUtil.equals(info.getNodeId(), committeeVerifyAnchor.getAnchors().get(j).getNodeId())) {
                        boolean res = false;
                        for (int k = 0; k < committeeVerifyAnchor.getAnchors().get(j).getNodePublicKeys().size(); k++) {
                            res = SignAlgoEnum.getByName(info.getSignAlgo().getName())
                                    .getSigner()
                                    .verify(committeeVerifyAnchor.getAnchors().get(j).getNodePublicKeys().get(k).getPublicKey(), encodedToSign, info.getSig());
                            if (res) {
                                break;
                            }
                        }
                        if (res) {
                            correct++;
                            break;
                        }
                    }
                }
            }
            
            // 2.7 Check if enough signatures
            if (3*correct <= 2*committeeVerifyAnchor.getAnchors().size()) {
                throw new RuntimeException("the number of signatures is less than 2/3");
            }
            
            // 3. Load PTC Hub contract and add TP-BTA
            PtcHub ptcHub = PtcHub.load(
                    this.bbcContext.getPtcContract().getContractAddress(),
                    this.client,
                    this.keyPair
            );
            
            // 4. Call contract to add TpBTA
            TransactionReceipt receipt = ptcHub.addTpBta(tpbta.encode());
            
            // 5. Check transaction result
            if (receipt.getStatus() != 0) {
                throw new RuntimeException("transaction failed with status: " + receipt.getStatus());
            }
            
            getBBCLogger().info(
                    "add TpBTA successful by tx {} ",
                    receipt.getTransactionHash()
            );
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("failed to add tp-bta: %s", HexUtil.encodeHexStr(tpbta.encode())), e
            );
        }
    }

    @Override
    public ThirdPartyBlockchainTrustAnchor getTpBta(CrossChainLane tpbtaLane, int tpBtaVersion) {
        // 1. Check PTC contract status
        checkPtcContract();

        // 2. Load PTC Hub contract
        PtcHub ptcHub = PtcHub.load(
                this.bbcContext.getPtcContract().getContractAddress(),
                this.client,
                this.keyPair
        );

        ThirdPartyBlockchainTrustAnchor thirdPartyBlockchainTrustAnchor = null;
        try {
            getBBCLogger().info("call to get tpbta {}:{}", tpbtaLane.getLaneKey(), tpBtaVersion);

            // 3. Call contract to get TpBTA using encoded lane data
            byte[] tpBtaBytes = ptcHub.getTpBta(tpbtaLane.encode(), BigInteger.valueOf(tpBtaVersion));

            // Convert bytes to ThirdPartyBlockchainTrustAnchor object if not empty
            if (ObjectUtil.isNotEmpty(tpBtaBytes)) {
                thirdPartyBlockchainTrustAnchor = ThirdPartyBlockchainTrustAnchor.decode(tpBtaBytes);
                getBBCLogger().info("Third party blockchain trust anchor: {} ", 
                        HexUtil.encodeHexStr(thirdPartyBlockchainTrustAnchor.encode()));
            } else {
                getBBCLogger().info("No third party blockchain trust anchor found for lane {}:{}", 
                        tpbtaLane.getLaneKey(), tpBtaVersion);
            }
        } catch (Exception e) {
            getBBCLogger().error("[FISCOBCOSBBCService] call ptc hub to get tpbta {}:{} failed",
                    tpbtaLane.getLaneKey(), tpBtaVersion, e);
            throw new RuntimeException(
                    StrUtil.format("[FISCOBCOSBBCService] call ptc hub to get tpbta {}:{} failed", 
                            tpbtaLane.getLaneKey(), tpBtaVersion), e
            );
        }
        return thirdPartyBlockchainTrustAnchor;
    }

    @Override
    public boolean hasTpBta(CrossChainLane tpbtaLane, int tpBtaVersion) {
        // 1. Check PTC contract status
        checkPtcContract();

        // 2. Load PTC Hub contract
        PtcHub ptcHub = PtcHub.load(
                this.bbcContext.getPtcContract().getContractAddress(),
                this.client,
                this.keyPair
        );

        try {
            getBBCLogger().info("call to check if tpbta exist {}:{}", tpbtaLane.getLaneKey(), tpBtaVersion);

            // 3. Call contract to check if TpBTA exists using encoded lane data
            return ptcHub.hasTpBta(tpbtaLane.encode(), BigInteger.valueOf(tpBtaVersion));
        } catch (Exception e) {
            throw new RuntimeException(StrUtil.format(
                    "unexpected exception when checking tpbta {}:{} from ptc hub",
                    tpbtaLane.getLaneKey(), tpBtaVersion
            ), e);
        }
    }

    @Override
    public PTCTrustRoot getPTCTrustRoot(ObjectIdentity ptcOwnerOid) {
        // 1. Check PTC contract status
        checkPtcContract();

        // 2. Load PTC Hub contract
        PtcHub ptcHub = PtcHub.load(
                this.bbcContext.getPtcContract().getContractAddress(),
                this.client,
                this.keyPair
        );

        try {
            byte[] rawOid = ptcOwnerOid.encode();
            getBBCLogger().info("call to get ptc trust root for {}", HexUtil.encodeHexStr(rawOid));

            // 3. Call contract to get PTC trust root
            byte[] raw = ptcHub.getPTCTrustRoot(rawOid);

            // 4. Convert bytes to PTCTrustRoot object if not empty
            if (ObjectUtil.isNotEmpty(raw)) {
                return PTCTrustRoot.decode(raw);
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(StrUtil.format(
                    "unexpected exception when getting ptc trust root for oid {} from ptc hub",
                    HexUtil.encodeHexStr(ptcOwnerOid.encode())
            ), e);
        }
    }

    @Override
    public boolean hasPTCTrustRoot(ObjectIdentity ptcOwnerOid) {
        // 1. Check PTC contract status
        checkPtcContract();

        // 2. Load PTC Hub contract
        PtcHub ptcHub = PtcHub.load(
                this.bbcContext.getPtcContract().getContractAddress(),
                this.client,
                this.keyPair
        );

        try {
            byte[] rawOid = ptcOwnerOid.encode();
            getBBCLogger().info("call to check if has ptc trust root for {}", HexUtil.encodeHexStr(rawOid));

            // 3. Call contract to check if PTC trust root exists
            return ptcHub.hasPTCTrustRoot(rawOid);
        } catch (Exception e) {
            throw new RuntimeException(StrUtil.format(
                    "unexpected exception when checking ptc trust root for oid {} from ptc hub",
                    HexUtil.encodeHexStr(ptcOwnerOid.encode())
            ), e);
        }
    }

    @Override
    public PTCVerifyAnchor getPTCVerifyAnchor(ObjectIdentity ptcOwnerOid, BigInteger version) {
        // 1. Check PTC contract status
        checkPtcContract();

        // 2. Load PTC Hub contract
        PtcHub ptcHub = PtcHub.load(
                this.bbcContext.getPtcContract().getContractAddress(),
                this.client,
                this.keyPair
        );

        try {
            byte[] rawOid = ptcOwnerOid.encode();
            getBBCLogger().info("get ptc verify anchor for {}", HexUtil.encodeHexStr(rawOid));

            // 3. Call contract to get PTC verify anchor
            byte[] raw = ptcHub.getPTCVerifyAnchor(rawOid, version);

            // 4. Convert bytes to PTCVerifyAnchor object if not empty
            if (ObjectUtil.isNotEmpty(raw)) {
                return PTCVerifyAnchor.decode(raw);
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(StrUtil.format(
                    "unexpected exception when getting ptc verify anchor for oid {} from ptc hub",
                    HexUtil.encodeHexStr(ptcOwnerOid.encode())
            ), e);
        }
    }

    @Override
    public boolean hasPTCVerifyAnchor(ObjectIdentity ptcOwnerOid, BigInteger version) {
        // 1. Check PTC contract status
        checkPtcContract();

        // 2. Load PTC Hub contract
        PtcHub ptcHub = PtcHub.load(
                this.bbcContext.getPtcContract().getContractAddress(),
                this.client,
                this.keyPair
        );

        try {
            byte[] rawOid = ptcOwnerOid.encode();
            getBBCLogger().info("call to check if ptc verify anchor exists for {}", HexUtil.encodeHexStr(rawOid));

            // 3. Call contract to check if PTC verify anchor exists
            return ptcHub.hasPTCVerifyAnchor(rawOid, version);
        } catch (Exception e) {
            throw new RuntimeException(StrUtil.format(
                    "unexpected exception when checking ptc verify anchor for oid {} from ptc hub",
                    HexUtil.encodeHexStr(ptcOwnerOid.encode())
            ), e);
        }
    }

    @Override
    public Set<PTCTypeEnum> getSupportedPTCType() {
        // 1. Check PTC contract status
        checkPtcContract();

        // 2. Load PTC Hub contract
        PtcHub ptcHub = PtcHub.load(
                this.bbcContext.getPtcContract().getContractAddress(),
                this.client,
                this.keyPair
        );

        try {
            getBBCLogger().info("call to get supported ptc types");

            // 3. Call contract to get supported PTC types
            List<BigInteger> types = ptcHub.getSupportedPTCType();

            // 4. Convert BigInteger types to PTCTypeEnum
            return types.stream()
                    .map(x -> PTCTypeEnum.valueOf(x.byteValueExact()))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            throw new RuntimeException(StrUtil.format(
                    "unexpected exception when getting supported ptc types from ptc hub"
            ), e);
        }
    }
    
    /**
     * Decode ThirdPartyProof from raw message
     *
     * @param proofsData raw message data
     * @return ThirdPartyProof object
     */
    private ThirdPartyProof decodeTpProofFromMsg(byte[] proofsData) {
        int _len = proofsData.length;
        int _offset = 0;
        // hints len
        byte[] hints_len_bytes = new byte[4];
        System.arraycopy(proofsData, _offset, hints_len_bytes, 0, 4);
        _offset += 4;
        int hints_len = (int) extractUint32(hints_len_bytes, 4);
        // hints
        byte[] hints = new byte[hints_len];
        System.arraycopy(proofsData, _offset, hints, 0, hints_len);
        _offset += hints_len;

        // proof lens
        byte[] proof_len_bytes = new byte[4];
        System.arraycopy(proofsData, _offset, proof_len_bytes, 0, 4);
        _offset += 4;
        int proof_len = (int) extractUint32(proof_len_bytes, 4);
        // proof
        byte[] proof = new byte[proof_len];
        System.arraycopy(proofsData, _offset, proof, 0, proof_len);
        _offset += proof_len;

        return ThirdPartyProof.decode(proof);
    }

    /**
     * Extract uint32 value from byte array
     *
     * @param b byte array
     * @param offset offset in byte array
     * @return uint32 value
     */
    private long extractUint32(byte[] b, int offset) {
        long l = 0;
        for (int bit = 4; bit > 0; bit--) {
            l <<= 8;
            l |= b[offset - bit] & 0xFF;
        }
        return l;
    }

    @Override
    public BlockState queryValidatedBlockStateByDomain(CrossChainDomain recvDomain) {
        // 1. Check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getSdpContract())) {
            throw new RuntimeException("empty sdp contract in bbc context");
        }

        // 2. Load SDP contract
        SDPMsg sdpMsg = SDPMsg.load(
                this.bbcContext.getSdpContract().getContractAddress(),
                this.client,
                this.keyPair
        );

        try {
            getBBCLogger().info("querying validated block state for domain {}", recvDomain.getDomain());

            // 3. Call contract to query validated block state and get result directly
            SDPMsg.BlockState result = sdpMsg.queryValidatedBlockStateByDomain(recvDomain.getDomain());

            // 4. Return null if result is empty
            if (ObjectUtil.isNull(result)) {
                return null;
            }

            // 5. Create BlockState using constructor with the domain and result values
            return new BlockState(recvDomain, result.blockHash, result.blockHeight, result.blockTimestamp.longValue());
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("failed to query validated block state for domain %s", recvDomain.getDomain()),
                    e
            );
        }
    }

    @Override
    public CrossChainMessageReceipt recvOffChainException(String exceptionMsgAuthor, byte[] exceptionMsgPkg) {
        // 1. Check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getSdpContract())) {
            throw new RuntimeException("empty sdp contract in bbc context");
        }
        
        getBBCLogger().info("rollback sdp msg from {} now", exceptionMsgAuthor);
        getBBCLogger().debug("exceptionMsgPkg: {}", HexUtil.encodeHexStr(exceptionMsgPkg));
        
        // 2. Convert exception message author from hex string to bytes
        byte[] sender = HexUtil.decodeHex(exceptionMsgAuthor);
        
        // 3. Load SDP contract
        SDPMsg sdpMsg = SDPMsg.load(
                this.bbcContext.getSdpContract().getContractAddress(),
                this.client,
                this.keyPair
        );
        
        try {
            // 4. Call contract to handle exception message - pass byte[] instead of String
            TransactionReceipt receipt = sdpMsg.recvOffChainException(
                    sender,
                    exceptionMsgPkg
            );
            
            // 5. Create response
            CrossChainMessageReceipt crossChainMessageReceipt = new CrossChainMessageReceipt();
            
            // Check for null receipt
            if (ObjectUtil.isNull(receipt)) {
                crossChainMessageReceipt.setErrorMsg("null receipt after sent tx");
                return crossChainMessageReceipt;
            }
            
            crossChainMessageReceipt.setTxhash(receipt.getTransactionHash());
            crossChainMessageReceipt.setConfirmed(true);
            
            if (receipt.isStatusOK()) {
                getBBCLogger().info(
                        "successful to rollback sdp msg from {} to sdp {} by tx {}", 
                        exceptionMsgAuthor,
                        this.bbcContext.getSdpContract().getContractAddress(),
                        receipt.getTransactionHash()
                );
                crossChainMessageReceipt.setSuccessful(true);
            } else {
                getBBCLogger().error(
                        "failed to rollback sdp msg from {} to sdp {} by tx {}", 
                        exceptionMsgAuthor,
                        this.bbcContext.getSdpContract().getContractAddress(),
                        receipt.getTransactionHash()
                );
                crossChainMessageReceipt.setSuccessful(false);
                crossChainMessageReceipt.setErrorMsg(StrUtil.emptyToDefault(receipt.getMessage(), ""));
            }
            
            return crossChainMessageReceipt;
            
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to process rollback sdp msg from %s to sdp %s",
                            exceptionMsgAuthor,
                            this.bbcContext.getSdpContract().getContractAddress()
                    ),
                    e
            );
        }
    }

    @Override
    public CrossChainMessageReceipt reliableRetry(ReliableCrossChainMessage msg) {
        throw new UnsupportedOperationException("not supported");
    }

    private void checkPtcContract() {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getPtcContract())) {
            throw new RuntimeException("empty ptc contract in bbc context");
        }
        if (this.bbcContext.getPtcContract().getStatus() != ContractStatusEnum.CONTRACT_READY) {
            throw new RuntimeException("ptc hub is not ready");
        }
    }

    private boolean isByteArrayZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0x00) {
                return false;
            }
        }
        return true;
    }
}
