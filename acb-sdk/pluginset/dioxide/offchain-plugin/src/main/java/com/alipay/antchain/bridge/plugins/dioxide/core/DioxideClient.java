package com.alipay.antchain.bridge.plugins.dioxide.core;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.net.http.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import lombok.Getter;
import com.alipay.antchain.bridge.plugins.lib.transactions.JdbcTransactionCoordinator;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;
import com.alipay.antchain.bridge.plugins.dioxide.conf.DioxideAddressTypeEnum;
import com.alipay.antchain.bridge.plugins.dioxide.gcl.*;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.plugins.dioxide.conf.DioxideConfig;
import com.alipay.antchain.bridge.plugins.dioxide.conf.DioxideTypes;

@Getter
public class DioxideClient {

    private final Logger bbcLogger;

    private final DioxideConfig config;

    private final DioxideAccount dioxideAccount;

    private final DioxideAddress dappAddress;

    private final HttpClient httpClient;

    private final ExecutorService executor;

    private volatile JdbcTransactionCoordinator transactionCoordinator;
    private long coordinatorCheckpointHeight;

    @SneakyThrows
    private synchronized JdbcTransactionCoordinator coordinator() {
        if (transactionCoordinator == null) {
            String filename = config.getTxCoordinatorConfigFile();
            if (StrUtil.isEmpty(filename)) {
                filename = System.getProperty("dioxide.tx.config", System.getenv("DIOXIDE_TX_COORDINATOR_CONFIG"));
                if (StrUtil.isEmpty(filename)) { filename = "/etc/antchain-bridge/dioxide-tx.properties"; }
            }
            Properties p = JdbcTransactionCoordinator.readConfig(filename);
            coordinatorCheckpointHeight = Long.parseLong(JdbcTransactionCoordinator.required(p, "checkpointHeight"));
            transactionCoordinator = JdbcTransactionCoordinator.fromProperties(p, getClass().getClassLoader());
        }
        return transactionCoordinator;
    }

    private static final int DEFAULT_TIMEOUT = 20 * 1000;

    private static final List<String> PRE_CONTRACT_ORDER = List.of(
            Interfaces.IAUTHMESSAGE,
            Interfaces.ICONTRACTUSINGSDP,
            Interfaces.ISDPMESSAGE,
            Interfaces.ISUBPROTOCOL,
            Libs.UTILS,
            Libs.SIZEOF,
            Libs.BYTESTOTYPES,
            Libs.TYPESTOBYTES,
            Libs.TLVUTILS,
            Libs.SDPLIB,
            Libs.AMLIB
    );

    private static final String SEND_AUTH_MESSAGE_EVENT_NAME = "SendAuthMessage:name";

    private static final String RECV_MESSAGE_IN_PROTOCOL = "recvMessageInProtocol:name";

    private static final String INVOCATION_SUCCESS = "IVKRET_SUCCESS";

    public DioxideClient(DioxideConfig config, Logger bbcLogger) {
        this.bbcLogger = bbcLogger;
        this.config = config;

        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        httpClient = HttpClient.newBuilder()
                .executor(executor)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // test if rpcUrl is true
        try {
            String rawResp = makeRequest("dx.overview", "");
            checkIfErrorResponse(rawResp);
        } catch (Exception e) {
            throw new RuntimeException(String.format("failed to connect dioxide (url: %s) or other error (exception: %s)", config.getRpcUrl(), e.getMessage()), e);
        }

        this.dioxideAccount = DioxideAccount.fromKey(this.config.getPrivateKey());
        dappAddress = new DioxideAddress(this.config.getDappName(), DioxideAddressTypeEnum.DAPP);
    }

    public void shutdown() {
        this.executor.shutdown();
        this.httpClient.close();
    }

    @SneakyThrows
    public Long queryLatestHeight() {
        String rawResp = makeRequest("dx.committed_head_height", "");
        JSONObject resp = checkIfErrorResponse(rawResp);
        return resp.getLong("HeadHeight");
    }

    public DioxideTransaction getTransactionByHash(String txHash) {
        String rawResp = makeRequest("dx.transaction", JSON.toJSONString(orderedMap("hash", txHash)));
        JSONObject ret = checkIfErrorResponse(rawResp);
        try {
            return JSON.toJavaObject(ret, DioxideTransaction.class);
        } catch (Exception e) {
            getBbcLogger().error("[getTransactionByHash] error: {}", e.getMessage());
            getBbcLogger().info("\n{}", JSON.toJSONString(ret, SerializerFeature.PrettyFormat));
            return null;
        }
    }

    public JSONObject getTransactionJonObjectByHash(String txHash) {
        String rawResp = makeRequest("dx.transaction", JSON.toJSONString(orderedMap("hash", txHash)));
        return checkIfErrorResponse(rawResp);
    }

    public DioxideTransactionBlock getTransactionBlockInGlobalShardByHeight(long height) {
        String rawResp = makeRequest("dx.transaction_block", JSON.toJSONString(orderedMap(
                "query_type", 0,
                "shard_index", DioxideTypes.GLOBAL_IDENTIFIER,
                "height", height
        )));
        JSONObject ret = checkIfErrorResponse(rawResp);
        try {
            return JSON.toJavaObject(ret, DioxideTransactionBlock.class);
        } catch (Exception e) {
            getBbcLogger().error(e.getMessage(), e);
            getBbcLogger().info("\n{}", JSON.toJSONString(ret, SerializerFeature.PrettyFormat));
            return null;
        }
    }

    public JSONObject getConsensusHeaderByHeight(long height) {
        String rawResp = makeRequest("dx.consensus_header", JSON.toJSONString(orderedMap(
                "query_type", 0,
                "height", height
        )));
        return checkIfErrorResponse(rawResp);
    }

    public CrossChainMessageReceipt getCrossChainMessageReceipt(DioxideTransaction dioxideTransaction) {
        if (dioxideTransaction == null) {
            return buildCrossChainMessageReceipt(null, false, false, "tx0 is null");
        }

        TxFinalityResult txFinalityResult = evaluateTxFinalityWithRelays(dioxideTransaction);
        if (txFinalityResult.state() == TxFinalityState.FAILED) {
            return buildCrossChainMessageReceipt(
                    dioxideTransaction,
                    true,
                    false,
                    String.format(
                            "transaction %s reached terminal failure state %s",
                            txFinalityResult.txHash(),
                            txFinalityResult.confirmState()
                    )
            );
        }
        if (txFinalityResult.state() == TxFinalityState.PENDING) {
            return buildCrossChainMessageReceipt(
                    dioxideTransaction,
                    false,
                    true,
                    String.format(
                            "transaction %s is not finalized (state: %s)",
                            StrUtil.emptyToDefault(txFinalityResult.txHash(), "unknown"),
                            StrUtil.emptyToDefault(txFinalityResult.confirmState(), "unknown")
                    )
            );
        }

        // A finalized tx0 may not have produced its inbound relay yet.
        if (dioxideTransaction.getInvocation() == null || CollUtil.isEmpty(dioxideTransaction.getInvocation().getRelays())) {
            return buildCrossChainMessageReceipt(
                    dioxideTransaction,
                    false,
                    true,
                    "tx1(inbound relay) in tx0 is not available"
            );
        }

        List<String> tx2Hashes = dioxideTransaction.getInvocation().getRelays().stream()
                .filter(Objects::nonNull)
                .filter(StrUtil::isNotEmpty)
                .distinct()
                .toList();

        boolean eventStructurePending = false;
        List<String> eventTargetTxHashes = new ArrayList<>();
        Map<String, JSONObject> relayGroups = new HashMap<>();
        for (String tx2Hash : tx2Hashes) {
            String groupHash = normalizeRelayTxHash(tx2Hash);
            if (!relayGroups.containsKey(groupHash)) {
                relayGroups.put(groupHash, getTransactionJonObjectByHash(groupHash));
            }
            JSONObject tx2 = scopeRelayGroup(relayGroups.get(groupHash), tx2Hash);
            RelayEventInspection relayEventInspection = inspectRelayEvents(tx2, tx2Hash);
            if (relayEventInspection.state() == RelayEventState.FAILED) {
                return buildCrossChainMessageReceipt(
                        dioxideTransaction,
                        true,
                        false,
                        relayEventInspection.errorMessage()
                );
            }
            eventStructurePending |= relayEventInspection.state() == RelayEventState.PENDING;
            eventTargetTxHashes.addAll(relayEventInspection.eventTargetTxHashes());
        }

        TxFinalityResult pendingEventFinality = eventStructurePending
                ? new TxFinalityResult(TxFinalityState.PENDING, "", "")
                : null;
        List<DioxideTransaction> eventTargetTxs = new ArrayList<>();
        for (String eventTargetTxHash : eventTargetTxHashes.stream().distinct().toList()) {
            DioxideTransaction eventTargetTx = scopeTransactionReference(
                    getTransactionByHash(normalizeRelayTxHash(eventTargetTxHash)), eventTargetTxHash);
            if (eventTargetTx == null) {
                if (pendingEventFinality == null) {
                    pendingEventFinality = new TxFinalityResult(
                            TxFinalityState.PENDING,
                            eventTargetTxHash,
                            ""
                    );
                }
                continue;
            }
            eventTargetTxs.add(eventTargetTx);
            TxFinalityResult eventTargetFinality = evaluateTxFinalityWithRelays(eventTargetTx);
            if (eventTargetFinality.state() == TxFinalityState.FAILED) {
                return buildCrossChainMessageReceipt(
                        dioxideTransaction,
                        true,
                        false,
                        String.format(
                                "transaction %s reached terminal failure state %s",
                                eventTargetFinality.txHash(),
                                eventTargetFinality.confirmState()
                        )
                );
            }
            if (eventTargetFinality.state() == TxFinalityState.PENDING && pendingEventFinality == null) {
                pendingEventFinality = eventTargetFinality;
            }
        }

        if (pendingEventFinality != null) {
            return buildCrossChainMessageReceipt(
                    dioxideTransaction,
                    false,
                    true,
                    String.format(
                            "transaction %s is not finalized (state: %s)",
                            StrUtil.emptyToDefault(pendingEventFinality.txHash(), "unknown"),
                            StrUtil.emptyToDefault(pendingEventFinality.confirmState(), "unknown")
                    )
            );
        }

        for (DioxideTransaction eventTargetTx : eventTargetTxs) {
            if (StrUtil.equals(eventTargetTx.getTarget(), RECV_MESSAGE_IN_PROTOCOL)) {
                return buildCrossChainMessageReceipt(dioxideTransaction, true, true, "");
            }
        }

        return buildCrossChainMessageReceipt(
                dioxideTransaction,
                true,
                false,
                "no recvMessageInProtocol event in protocol contract"
        );
    }

    private CrossChainMessageReceipt buildCrossChainMessageReceipt(
            DioxideTransaction dioxideTransaction,
            boolean confirmed,
            boolean successful,
            String errorMsg
    ) {
        CrossChainMessageReceipt receipt = new CrossChainMessageReceipt();
        receipt.setConfirmed(confirmed);
        receipt.setSuccessful(successful);
        receipt.setTxhash(dioxideTransaction == null ? "" : StrUtil.nullToEmpty(dioxideTransaction.getTxHash()));
        receipt.setErrorMsg(StrUtil.nullToEmpty(errorMsg));
        if (confirmed && dioxideTransaction != null) {
            try {
                coordinator().recordOutcome(dioxideTransaction.getTxHash(), successful);
            } catch (Exception e) {
                // The on-chain receipt remains authoritative; a journal outage cannot change its outcome.
                getBbcLogger().warn("Could not update Dioxide submission outcome for {} ({})",
                        dioxideTransaction.getTxHash(), e.getClass().getSimpleName());
            }
        }
        return receipt;
    }


    public List<CrossChainMessage> readAuthMessagesFromBlock(long height) {
        List<CrossChainMessage> messageList = ListUtil.toList();
        // 1. get tx block on shard where AM is located
        DioxideTransactionBlock block = getTransactionBlockInGlobalShardByHeight(height);
        // 2. get tx from tx block
        List<String> dispatchedAndOutboundRelays = ListUtil.toList();
        if (block.getDispatchedRelayTxnCount() != 0) {
            dispatchedAndOutboundRelays.addAll(block.getTransactions().getDispatchedRelays());
        }
        if (block.getOutboundRelayTxnCount() != 0) {
            dispatchedAndOutboundRelays.addAll(block.getTransactions().getOutboundRelays());
        }
        if (CollectionUtils.isNotEmpty(dispatchedAndOutboundRelays)) {
            dispatchedAndOutboundRelays.forEach(txn -> {
                DioxideTransaction dtx = getTransactionByHash(txn);
                if (StrUtil.isNotEmpty(dtx.getTarget())) {
                    if (dtx.getTarget().equals(SEND_AUTH_MESSAGE_EVENT_NAME) &&
                        StrUtil.contains(dtx.getFunction(), config.getDappName())) {
                        getBbcLogger().info("send am event found in global shard, block: {}, block hash: {}, tx hash: {} contract name: {}",
                                block.getHeight(), block.getHash(), dtx.getTxHash(), String.format("%s:%s", config.getDappName(), config.getAmContractName()));
                        messageList.add(CrossChainMessage.createCrossChainMessage(
                                CrossChainMessage.CrossChainMessageType.AUTH_MSG,
                                dtx.getHeight(),
                                getConsensusHeaderByHeight(height).getLongValue("Timestamp"),
                                // Store the original Dioxide block hash reversibly: relayer will hex-encode these UTF-8 bytes.
                                block.getHash().getBytes(StandardCharsets.UTF_8),
                                // todo: parse the am msg
                                deserializedSendMsgEventArgs(dtx.getInputAsString()),
                                // todo: put ledger data, for SPV or other attestations
                                // this time we need no verify. it's ok to set it with empty bytes
                                "".getBytes(),
                                // todo: put proof data
                                // this time we need no proof data. it's ok to set it with empty bytes
                                "".getBytes(),
                                // Store the original Dioxide tx hash reversibly: relayer will hex-encode these UTF-8 bytes.
                                dtx.getTxHash().getBytes(StandardCharsets.UTF_8)
                        ));
                    }
                }
            });
            return messageList;
        }

        if (!messageList.isEmpty()) {
            getBbcLogger().info("read cross chain messages (blockNumber: {}, msg_size: {})", height, messageList.size());
            getBbcLogger().debug("read cross chain messages (blockNumber: {}, msgs: {})",
                    height,
                    messageList.stream().map(JSON::toJSONString).collect(Collectors.joining(","))
            );
        }

        return ListUtil.empty();
    }

    private byte[] deserializedSendMsgEventArgs(String input) {
        // 1. hex 转 byte[]
        byte[] inputBytes = HexUtil.decodeHex(input);

        // 2. 检查长度是否至少包含 4 字节
        if (inputBytes.length < 4) {
            throw new IllegalArgumentException("illegal input bytes length: " + inputBytes.length);
        }

        // 3. 解析前 4 字节（小端序）作为长度字段
        int len = ((inputBytes[3] & 0xFF) << 24) |
                ((inputBytes[2] & 0xFF) << 16) |
                ((inputBytes[1] & 0xFF) << 8)  |
                ((inputBytes[0] & 0xFF));

        // 4. 校验后续字节长度是否与长度字段一致
        if (inputBytes.length != 4 + len) {
            throw new IllegalArgumentException("Length field mismatch: expected " + len +
                    " bytes, but got " + (inputBytes.length - 4));
        }

        // 5. 拷贝数据字段（丢弃前4字节）
        byte[] evnetData = new byte[len];
        System.arraycopy(inputBytes, 4, evnetData, 0, len);

        return evnetData;
    }

    public long deployAuthMsgContract() {
        try {
            deployPreContract();

            List<String> codes = new ArrayList<>();
            List<String> cargs = new ArrayList<>();

            String amContractSource =
                    new String(
                            Base64.getDecoder().decode(Contracts.AUTHMSG),
                            StandardCharsets.UTF_8
                    );

            codes.add(amContractSource);
            cargs.add(JSON.toJSONString(orderedMap(
                    "_owner", dioxideAccount.getAddressInString(),
                    "_relayer", dioxideAccount.getAddressInString()
            )));

            String txHash = sendTransaction(
                    JSON.toJSONString(orderedMap(
                    "delegatee", dappAddress.getAddressInString(),
                    "function", "core.delegation.deploy_contracts",
                    "args", orderedMap("code", codes, "cargs", cargs))),
                    true
            );
            waitForContractDeployed(txHash);
            // test if the contract been deployed successfully, and get the contract name
            return getContractCid(config.getAmContractName());
        } catch (Exception e) {
            throw new RuntimeException(String.format("failed to deploy contract: %s", config.getAmContractName()) ,e);
        }
    }

    public long deploySdpContract() {
        try {
            deployPreContract();

            List<String> codes = new ArrayList<>();
            List<String> cargs = new ArrayList<>();

            String sdpContractSource =
                    new String(
                            Base64.getDecoder().decode(Contracts.SDPMSG),
                            StandardCharsets.UTF_8
                    );

            codes.add(sdpContractSource);
            cargs.add(JSON.toJSONString(orderedMap(
                    "_owner", dioxideAccount.getAddressInString()
            )));

            String txHash = sendTransaction(
                    JSON.toJSONString(orderedMap(
                    "delegatee", dappAddress.getAddressInString(),
                    "function", "core.delegation.deploy_contracts",
                    "args", orderedMap("code", codes, "cargs", cargs))),
                    true
            );
            waitForContractDeployed(txHash);
            // test if the contract been deployed successfully, and get the contract name
            return getContractCid(config.getSdpContractName());
        } catch (Exception e) {
            throw new RuntimeException(String.format("failed to deploy contract: %s", config.getSdpContractName()) ,e);
        }
    }

    public long deployContract(String contractName, String contractSource, String cargsString) {
        try {
            deployPreContract();

            List<String> codes = new ArrayList<>();
            List<String> cargs = new ArrayList<>();

            codes.add(contractSource);
            cargs.add(cargsString);

            String txHash = sendTransaction(
                    JSON.toJSONString(orderedMap(
                    "delegatee", dappAddress.getAddressInString(),
                    "function", "core.delegation.deploy_contracts",
                    "args", orderedMap("code", codes, "cargs", cargs))),
                    true
            );
            waitForContractDeployed(txHash);
            // test if the contract been deployed successfully, and get the contract name
            return getContractCid(contractName);
        } catch (Exception e) {
            throw new RuntimeException(String.format("failed to deploy contract: %s", contractName) ,e);
        }
    }

    private void deployPreContract() {
        if (config.getIsPreContractDeployed()) {
            getBbcLogger().info("[deployPreContract] is already deployed");
            return;
        }

        // mint some tokens for client
        try {
            String txHash = sendTransaction(
                    JSON.toJSONString(orderedMap(
                    "sender", dioxideAccount.getAddressInString(),
                    "function", "core.coin.mint",
                    "args", orderedMap(
                            "Amount", String.format("%d", (long) Math.pow(10, 18))
                    ))),
                    true
            );
            getBbcLogger().info("success to mint some tokens");
        } catch (Exception e) {
            throw new RuntimeException("failed to mint some tokens", e);
        }

        // deploy dapp
        try {
            String txHash = sendTransaction(
                    JSON.toJSONString(orderedMap(
                    "sender", dioxideAccount.getAddressInString(),
                    "function", "core.delegation.create",
                    "args", orderedMap(
                            "Type", 10,
                            "Name", config.getDappName(),
                            "Deposit", String.format("%d", (long) Math.pow(10, 11))
                            ))),
                    true
            );
            getBbcLogger().info("wait for deploying dapp: [txhash]{}", txHash);
            if (!waitForDappDeployed(txHash, DEFAULT_TIMEOUT)) {
                throw new RuntimeException("waitForDappDeployed failed");
            }
            getBbcLogger().info("success to deploy dapp: [txhash]{}", dappAddress.getAddressInString());
        } catch (Exception e) {
            throw new RuntimeException("failed to deploy dapp", e);
        }

        // deploy lib utils etc.
        try {
            List<String> codes = new ArrayList<>();
            List<String> cargs = new ArrayList<>();

            // 按 FILE_ORDER 顺序填充 codes 和 cargs
            for (String contractSource : PRE_CONTRACT_ORDER) {
                codes.add(new String(
                        Base64.getDecoder().decode(contractSource),
                        StandardCharsets.UTF_8
                ));
                cargs.add("");
            }

            getBbcLogger().info("dapp name to use: {}", dappAddress.getAddressInString());
            String txHash = sendTransaction(
                    JSON.toJSONString(orderedMap(
                    "delegatee", dappAddress.getAddressInString(),
                    "function", "core.delegation.deploy_contracts",
                    "args", orderedMap("code", codes, "cargs", cargs))),
                    true
            );
            waitForContractDeployed(txHash);
            getBbcLogger().info("deploy pre contract from onchain file: {}", txHash);
            config.setIsPreContractDeployed(true);
        } catch (Exception e) {
            throw new RuntimeException("failed to deploy pre contract", e);
        }
    }

    private long getContractCid(String contractName) {
        String rawResp = makeRequest("dx.contract_info", JSON.toJSONString(orderedMap(
                "contract", String.format("%s.%s", config.getDappName(), contractName)
        )));
        JSONObject resp = checkIfErrorResponse(rawResp);
        return resp.getLongValue("ContractVersionID");
    }

    public boolean isContractVersionCurrent(String contractName, String configuredContractCid) {
        if (StrUtil.isEmpty(configuredContractCid)) {
            return false;
        }
        long currentCid = getContractCid(contractName);
        if (!contractVersionMatches(configuredContractCid, currentCid)) {
            getBbcLogger().warn(
                    "stale or invalid Dioxide contract context for {}.{}: configured CID {}, current CID {}",
                    config.getDappName(),
                    contractName,
                    configuredContractCid,
                    currentCid
            );
            return false;
        }
        return true;
    }

    static boolean contractVersionMatches(String configuredContractCid, long currentCid) {
        try {
            return Long.parseLong(configuredContractCid) == currentCid;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public long querySdpSeq(String senderDomain, String senderID, String receiverDomain, String receiverID) {
        try {
            return coordinator().withQueryLock(
                    config.getDappName() + "." + config.getSdpContractName() + "|" + dioxideAccount.getAddressInString(),
                    () -> querySdpSeqLocked(senderDomain, senderID, receiverDomain, receiverID));
        } catch (Exception e) {
            throw new RuntimeException("coordinated SDP sequence query failed", e);
        }
    }

    private long querySdpSeqLocked(String senderDomain, String senderID, String receiverDomain, String receiverID) {
        try {
            int[] senderDomainArray = toIntArray(senderDomain.getBytes(StandardCharsets.UTF_8));
            int[] senderIDArray = toIntArray(HexUtil.decodeHex(senderID));
            int[] receiverDomainArray = toIntArray(receiverDomain.getBytes(StandardCharsets.UTF_8));
            int[] receiverIDArray = toIntArray(HexUtil.decodeHex(receiverID));
            long seq;

            String txHash = sendTransaction(
                    JSON.toJSONString(orderedMap(
                    "sender", dioxideAccount.getAddressInString(),
                    "function", String.format("%s.%s.%s",config.getDappName(), config.getSdpContractName(), "querySDPMessageSeq"),
                    "args", orderedMap(
                            "senderDomain", senderDomainArray,
                            "senderID", senderIDArray,
                            "receiverDomain", receiverDomainArray,
                            "receiverID", receiverIDArray
                    ))),
                    true
            );

            if (StrUtil.isEmpty(txHash)) {
                throw new RuntimeException("tx hash is empty");
            }
            JSONObject resp = getContractState(config.getDappName(), config.getSdpContractName(), DioxideTypes.Scope.Address, dioxideAccount.getAddressInString());
            JSONObject state = resp.getJSONObject("State");
            seq = state.getLongValue("resultInQuerySDPMessageSeq");

            getBbcLogger().info("result of sdp contract state [address]: \n{}", JSON.toJSONString(resp, SerializerFeature.PrettyFormat));

            getBbcLogger().info("sdpMsg seq: {} (senderDomain: {}, senderID: {}, receiverDomain: {}, receiverID: {})",
                    seq, senderDomain, senderID, receiverDomain, receiverID);
            return seq;
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("failed to query sdpMsg seq (senderDomain: %s, senderID: %s, receiverDomain: %s, receiverID: %s)",
                            senderDomain, senderID, receiverDomain, receiverID), e
            );
        }
    }

    public void setProtocolToAuthMsg(String protocolCidInString, String protocolType) {
        long protocolCid = Long.parseLong(protocolCidInString);
        String protocolAddress = String.format("0x%016X:contract", protocolCid);

        // Registration rechecks predeployed AM/SDP bindings. Do not submit the
        // same setProtocol twice: the contract intentionally rejects duplicates.
        JSONObject state = getContractState(config.getDappName(), config.getAmContractName(),
                DioxideTypes.Scope.Global, "").getJSONObject("State");
        if (state == null) {
            throw new IllegalStateException("missing AuthMsg state before protocol binding");
        }
        JSONObject ids = state.getJSONObject("protocolIDs");
        JSONObject routes = state.getJSONObject("protocolRoutes");
        if ((ids != null && ids.containsKey(protocolType))
                || (routes != null && routes.containsKey(protocolType))) {
            if (ids != null && routes != null
                    && Long.toString(protocolCid).equals(ids.getString(protocolType))
                    && protocolAddress.equalsIgnoreCase(routes.getString(protocolType))) {
                return;
            }
            throw new IllegalStateException("conflicting AuthMsg protocol binding for type " + protocolType);
        }

        try {
            String txHash = sendTransaction(
                    JSON.toJSONString(orderedMap(
                    "sender", dioxideAccount.getAddressInString(),
                    "function", String.format("%s.%s.%s",config.getDappName(), config.getAmContractName(), "setProtocol"),
                    "args", orderedMap(
                            "protocolID", protocolCid,
                            "protocolAddress", protocolAddress,
                            "protocolType", Integer.parseInt(protocolType)
                    ))),
                    true
            );

            if (StrUtil.isEmpty(txHash)) {
                throw new RuntimeException("tx hash is empty");
            }
            getBbcLogger().info("set protocol (cid: {}, address: {}, type: {}) to AM {} by tx {} ",
                    protocolCid, protocolAddress, protocolType, config.getAmContractName(), txHash
            );
            JSONObject resp = getContractState(config.getDappName(), config.getAmContractName(), DioxideTypes.Scope.Global, "");
            getBbcLogger().info("result of am contract state: \n{}", JSON.toJSONString(resp, SerializerFeature.PrettyFormat));
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to set protocol (cid: %s, address: %s, type: %s) to AM %s",
                            protocolCid, protocolAddress, protocolType, config.getAmContractName()
                    ), e
            );
        }
    }

    public CrossChainMessageReceipt relayMsgToAuthMsg(byte[] rawMessage, String submissionId) {
        if (StrUtil.isEmpty(submissionId)) {
            throw new IllegalArgumentException("Dioxide relay requires a stable submissionId; upgrade the Relayer and Plugin Server");
        }
        try {
            String txHash = sendTransaction(
                    JSON.toJSONString(orderedMap(
                    "sender", dioxideAccount.getAddressInString(),
                    "function", String.format("%s.%s.%s",config.getDappName(), config.getAmContractName(), "recvPkgFromRelayer"),
                    "args",  orderedMap(
                            "pkg", toIntArray(rawMessage)
                    ),
                    "gaslimit", 50000000)),
                    false, submissionId
            );

            if (StrUtil.isEmpty(txHash)) {
                throw new RuntimeException("tx hash is empty");
            }

            CrossChainMessageReceipt crossChainMessageReceipt = new CrossChainMessageReceipt();
            crossChainMessageReceipt.setConfirmed(false);
            crossChainMessageReceipt.setSuccessful(true);
            crossChainMessageReceipt.setTxhash(txHash);
            crossChainMessageReceipt.setErrorMsg("");
            getBbcLogger().info("relay am msg by tx {}", txHash);

            return crossChainMessageReceipt;

        } catch (Exception e) {
            throw new RuntimeException(
                    "failed to relay AM to " + config.getAmContractName(), e
            );
        }
    }

    public void setAmContractToSdp(String amContractCidInString) {
        long amContractCid = Long.parseLong(amContractCidInString);
        String amContractAddress = String.format("0x%016X:contract", amContractCid);

        JSONObject current = getContractState(config.getDappName(), config.getSdpContractName(),
                DioxideTypes.Scope.Global, "").getJSONObject("State");
        if (current == null) {
            throw new IllegalStateException("missing SDP state before AM binding");
        }
        if (current.getLongValue("amContractId") != 0L) {
            if (current.getLongValue("amContractId") == amContractCid
                    && amContractAddress.equalsIgnoreCase(current.getString("amAddress"))) {
                return;
            }
            throw new IllegalStateException("conflicting SDP AM binding");
        }

        try {
            String txHash = sendTransaction(
                    JSON.toJSONString(orderedMap(
                    "sender", dioxideAccount.getAddressInString(),
                    "function", String.format("%s.%s.%s",config.getDappName(), config.getSdpContractName(), "setAmContract"),
                    "args",  orderedMap(
                            "_amContractId", amContractCid,
                            "_amAddress",  amContractAddress
                    ))),
                    true
            );

            if (StrUtil.isEmpty(txHash)) {
                throw new RuntimeException("tx hash is empty");
            }
            getBbcLogger().info("set am contract (cid: {}, address: {}) to SDP {} by tx {}",
                    amContractCid, amContractAddress, config.getSdpContractName(), txHash);
            JSONObject resp = getContractState(config.getDappName(), config.getSdpContractName(), DioxideTypes.Scope.Global, "");
            getBbcLogger().info("result of sdp contract state: \n{}", JSON.toJSONString(resp, SerializerFeature.PrettyFormat));
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("failed to set am contract (cid: %s, address: %s) to SDP %s",
                            amContractCid, amContractAddress, config.getSdpContractName()
                    ), e
            );
        }
    }

    public void setLocalDomainToSdp(String localDomain) {
        JSONObject current = getContractState(config.getDappName(), config.getSdpContractName(),
                DioxideTypes.Scope.Global, "").getJSONObject("State");
        if (current == null) {
            throw new IllegalStateException("missing SDP state before domain binding");
        }
        JSONArray existing = current.getJSONArray("localDomain");
        if (existing != null && existing.size() == localDomain.getBytes(StandardCharsets.UTF_8).length) {
            byte[] expected = localDomain.getBytes(StandardCharsets.UTF_8);
            boolean equal = true;
            for (int i = 0; i < expected.length; i++) {
                equal &= existing.getIntValue(i) == (expected[i] & 0xff);
            }
            if (equal) {
                return;
            }
        }

        try {
            int[] domainArray = toIntArray(localDomain.getBytes(StandardCharsets.UTF_8));
            String txHash = sendTransaction(
                    JSON.toJSONString(orderedMap(
                            "sender", dioxideAccount.getAddressInString(),
                            "function", String.format("%s.%s.setLocalDomain", config.getDappName(), config.getSdpContractName()),
                            "args", orderedMap(
                                    "domain", domainArray)
                    )),
                    true
            );

            if (StrUtil.isEmpty(txHash)) {
                throw new RuntimeException("tx hash is empty");
            }
            getBbcLogger().info("set domain ({}) to SDP {} by tx {}", localDomain, config.getSdpContractName(), txHash);
            JSONObject resp = getContractState(config.getDappName(), config.getSdpContractName(), DioxideTypes.Scope.Global, "");
            getBbcLogger().info("result of sdp contract state: \n{}", JSON.toJSONString(resp, SerializerFeature.PrettyFormat));
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to set domain (%s) to SDP %s", localDomain, config.getSdpContractName()
                    ), e
            );
        }
    }

    private int[] toIntArray(byte[] bytes) {
        int[] arr = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            arr[i] = bytes[i] & 0xFF;  // 转无符号 uint8
        }
        return arr;
    }

    private void waitForContractDeployed(String txHash) {
        JSONObject state = getContractState("core","contracts", DioxideTypes.Scope.Global, "").getJSONObject("State");
        long targetHeight = -1;
        if (state != null && !state.isEmpty()) {
            JSONArray scheduledList = state.getJSONArray("Scheduled");
            if (scheduledList != null) {
                for (int i = 0; i < scheduledList.size(); i++) {
                    JSONObject s = scheduledList.getJSONObject(i);
                    if (txHash.equals(s.getString("BuildKey"))) {
                        targetHeight = s.getLongValue("TargetHeight");
                        break;
                    }
                }
            }
        }
        long curHeight = queryLatestHeight();
        while (curHeight <= targetHeight) {
            curHeight = queryLatestHeight();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @SneakyThrows
    public JSONObject getContractState(String dappName, String contractName, DioxideTypes.Scope scopeType, String key) {
        LinkedHashMap<String, Object> params = orderedMap(
                "contract_with_scope", String.format("%s.%s.%s", dappName, contractName, scopeType.toString().toLowerCase())
        );
        if (scopeType != DioxideTypes.Scope.Global) {
            params.put("scope_key", key);
        }

        String rawResp = makeRequest("dx.contract_state", JSON.toJSONString(params));
        return checkIfErrorResponse(rawResp);
    }

    private boolean waitForDappDeployed(String txHash, int timeOut) {
        if (waitForTransactionConfirmed(txHash, timeOut)) {
            List<JSONObject> relays = getAllRelayTransactions(getTransactionByHash(txHash) ,true);
            for (JSONObject relay : relays) {
                if (relay.getString("Function").equals("core.coin.address.deposit")) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    private List<JSONObject> getAllRelayTransactions(DioxideTransaction tx, boolean detail) {
        List<JSONObject> res = new ArrayList<>();

        if (evaluateTxFinalityWithRelays(tx).state() != TxFinalityState.FINALIZED) {
            return res;
        }

        Queue<DioxideTransaction> queue = new LinkedList<>();
        queue.add(tx);
        while (!queue.isEmpty()) {
            DioxideTransaction curTx = queue.poll();
            if (curTx.getInvocation() != null && curTx.getInvocation().getRelays() != null) {
                curTx.getInvocation().getRelays().forEach(relayHash -> {
                    DioxideTransaction relayTx = getTransactionByHash(relayHash.split(":")[0]);
                    if (detail) {
                        res.add(JSON.parseObject(JSON.toJSONString(relayTx)));
                    } else {
                        res.add(JSON.parseObject(relayHash));
                    }
                    queue.add(relayTx);
                });
            }
        }
        return res;
    }

    private boolean waitForTransactionConfirmed(String txHash, int timeOut) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeOut);
        while (System.nanoTime() < deadline) {
            TxFinalityResult result = evaluateTxFinalityWithRelays(getTransactionByHash(txHash));
            if (result.state() == TxFinalityState.FAILED) {
                throw new IllegalStateException("Dioxide transaction failed: " + result.txHash() + " (" + result.confirmState() + ")");
            }
            if (result.state() == TxFinalityState.FINALIZED) { return true; }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public boolean isTxConfirmed(DioxideTransaction tx) {
        if (tx == null) {
            return false;
        }
        return (tx.getConfirmState() != null
                && DioxideTypes.TXN_CONFIRMED_STATUS.contains(tx.getConfirmState()))
                || isDurablyCommitted(tx);
    }

    public static boolean isTxFinalized(DioxideTransaction tx) {
        if (tx == null) {
            return false;
        }
        return (tx.getConfirmState() != null
                && DioxideTypes.TXN_FINALIZED_STATUS.contains(tx.getConfirmState()))
                || isDurablyCommitted(tx);
    }

    static boolean isTxTerminalFailed(DioxideTransaction tx) {
        if (tx == null) {
            return false;
        }
        return StrUtil.equalsAny(
                tx.getConfirmState(),
                DioxideTypes.TxnConfirmState.TXN_RELAY_INVALIDED.name(),
                DioxideTypes.TxnConfirmState.TXN_ABORTED.name(),
                DioxideTypes.TxnConfirmState.TXN_EXPIRED.name()
        ) || StrUtil.equalsAny(
                tx.getState(),
                DioxideTypes.BlockState.DUS_INVALID.name(),
                DioxideTypes.BlockState.DUS_FORKED.name(),
                DioxideTypes.BlockState.DUS_ARCHIVED_UNCLE.name()
        );
    }

    private static boolean isDurablyCommitted(DioxideTransaction tx) {
        return StrUtil.equalsAny(
                tx.getState(),
                DioxideTypes.BlockState.DUS_FINALIZED.name(),
                DioxideTypes.BlockState.DUS_ARCHIVED.name()
        );
    }

    private TxFinalityResult evaluateTxFinalityWithRelays(DioxideTransaction dioxideTransaction) {
        return evaluateTxFinalityWithRelays(dioxideTransaction, this::getTransactionByHash);
    }

    static TxFinalityResult evaluateTxFinalityWithRelays(
            DioxideTransaction dioxideTransaction,
            Function<String, DioxideTransaction> transactionResolver
    ) {
        if (dioxideTransaction == null) {
            return new TxFinalityResult(TxFinalityState.PENDING, "", "");
        }

        Queue<DioxideTransaction> queue = new ArrayDeque<>();
        Set<String> queuedTxHashes = new HashSet<>();
        Map<String, DioxideTransaction> resolvedGroups = new HashMap<>();
        queue.add(dioxideTransaction);
        if (StrUtil.isNotEmpty(dioxideTransaction.getTxHash())) {
            queuedTxHashes.add(normalizeRelayTxHash(dioxideTransaction.getTxHash()));
        }

        TxFinalityResult pendingResult = null;
        while (!queue.isEmpty()) {
            DioxideTransaction currentTx = queue.poll();
            if (isTxTerminalFailed(currentTx)) {
                return new TxFinalityResult(
                        TxFinalityState.FAILED,
                        currentTx.getTxHash(),
                        currentTx.getConfirmState()
                );
            }
            if (!isTxFinalized(currentTx) && pendingResult == null) {
                pendingResult = new TxFinalityResult(
                        TxFinalityState.PENDING,
                        currentTx.getTxHash(),
                        currentTx.getConfirmState()
                );
            }

            List<String> relayReferences = new ArrayList<>();
            String invocationFailure = collectInvocationReferences(currentTx, relayReferences);
            if (invocationFailure != null) {
                return new TxFinalityResult(TxFinalityState.FAILED, currentTx.getTxHash(), invocationFailure);
            }
            for (String relayTxReference : relayReferences) {
                String relayTxHash = normalizeRelayTxHash(relayTxReference);
                if (StrUtil.isEmpty(relayTxHash)) {
                    if (pendingResult == null) {
                        pendingResult = new TxFinalityResult(TxFinalityState.PENDING, "", "");
                    }
                    continue;
                }
                if (!queuedTxHashes.add(relayTxReference)) {
                    continue;
                }
                if (!resolvedGroups.containsKey(relayTxHash)) {
                    resolvedGroups.put(relayTxHash, transactionResolver.apply(relayTxHash));
                }
                DioxideTransaction relayTx = scopeTransactionReference(resolvedGroups.get(relayTxHash), relayTxReference);
                if (relayTx == null) {
                    if (pendingResult == null) {
                        pendingResult = new TxFinalityResult(TxFinalityState.PENDING, relayTxHash, "");
                    }
                    continue;
                }
                queue.add(relayTx);
            }
        }

        return pendingResult == null
                ? new TxFinalityResult(
                        TxFinalityState.FINALIZED,
                        dioxideTransaction.getTxHash(),
                        dioxideTransaction.getConfirmState()
                )
                : pendingResult;
    }

    /** A group may contain many unrelated business transactions: preserve its indexed member. */
    static DioxideTransaction scopeTransactionReference(DioxideTransaction group, String reference) {
        if (group == null || reference == null) { return null; }
        int separator = reference.indexOf(':');
        if (separator < 0) { return group; }
        try {
            int index = Integer.parseInt(reference.substring(separator + 1));
            if (index < 0 || group.getEmbeddedRelays() == null || index >= group.getEmbeddedRelays().size()) { return null; }
            DioxideTransaction member = group.getEmbeddedRelays().get(index);
            if (member == null) { return null; }
            return DioxideTransaction.builder().txHash(reference).state(group.getState())
                    .confirmState(group.getConfirmState()).target(member.getTarget()).embeddedRelays(List.of(member)).build();
        } catch (NumberFormatException e) { return null; }
    }

    static JSONObject scopeRelayGroup(JSONObject group, String reference) {
        if (group == null || reference == null) { return null; }
        int separator = reference.indexOf(':');
        if (separator < 0) { return group; }
        try {
            int index = Integer.parseInt(reference.substring(separator + 1));
            JSONArray members = group.getJSONArray("Relays");
            if (index < 0 || members == null || index >= members.size()) { return null; }
            JSONObject result = new JSONObject();
            result.putAll(group); // JSONObject(Map) aliases its backing map; never shrink the cached group.
            JSONArray selected = new JSONArray();
            selected.add(members.get(index));
            result.put("Relays", selected);
            return result;
        } catch (NumberFormatException e) { return null; }
    }

    static String normalizeRelayTxHash(String relayTxReference) {
        if (StrUtil.isEmpty(relayTxReference)) {
            return "";
        }
        int separatorIndex = relayTxReference.indexOf(':');
        return separatorIndex >= 0 ? relayTxReference.substring(0, separatorIndex) : relayTxReference;
    }

    private static String collectInvocationReferences(DioxideTransaction tx, List<String> references) {
        if (tx.getInvocation() != null) {
            String status = tx.getInvocation().getStatus();
            if (StrUtil.isNotEmpty(status) && !INVOCATION_SUCCESS.equals(status)) { return status; }
            if (tx.getInvocation().getRelays() != null) { references.addAll(tx.getInvocation().getRelays()); }
        }
        if (tx.getEmbeddedRelays() != null) {
            for (DioxideTransaction embedded : tx.getEmbeddedRelays()) {
                if (embedded == null) { continue; }
                String error = collectInvocationReferences(embedded, references);
                if (error != null) { return error; }
            }
        }
        return null;
    }

    static RelayEventInspection inspectRelayEvents(JSONObject relayGroup, String relayGroupTxHash) {
        if (relayGroup == null) {
            return new RelayEventInspection(RelayEventState.PENDING, List.of(), "");
        }

        DioxideTransaction relayGroupTransaction = relayGroup.toJavaObject(DioxideTransaction.class);
        boolean relayGroupFinalized = isTxFinalized(relayGroupTransaction);
        JSONArray eventRelays = relayGroup.getJSONArray("Relays");
        if (CollUtil.isEmpty(eventRelays)) {
            return relayGroupFinalized
                    ? failedRelayEventInspection(relayGroupTxHash, "contains no embedded relay invocations")
                    : new RelayEventInspection(RelayEventState.PENDING, List.of(), "");
        }

        List<DioxideTransaction> embeddedInvocations = eventRelays.toJavaList(DioxideTransaction.class);
        if (CollUtil.isEmpty(embeddedInvocations)) {
            return relayGroupFinalized
                    ? failedRelayEventInspection(relayGroupTxHash, "contains no decodable embedded relay invocations")
                    : new RelayEventInspection(RelayEventState.PENDING, List.of(), "");
        }

        List<String> eventTargetTxHashes = new ArrayList<>();
        for (DioxideTransaction embeddedInvocation : embeddedInvocations) {
            if (embeddedInvocation == null || embeddedInvocation.getInvocation() == null) {
                continue;
            }
            String invocationStatus = embeddedInvocation.getInvocation().getStatus();
            if (StrUtil.isNotEmpty(invocationStatus) && !StrUtil.equals(invocationStatus, INVOCATION_SUCCESS)) {
                return failedRelayEventInspection(
                        relayGroupTxHash,
                        String.format(
                                "embedded invocation %s failed with status %s",
                                StrUtil.emptyToDefault(embeddedInvocation.getFunction(), "unknown"),
                                invocationStatus
                        )
                );
            }
            if (CollUtil.isEmpty(embeddedInvocation.getInvocation().getRelays())) {
                continue;
            }
            embeddedInvocation.getInvocation().getRelays().stream()
                    .filter(StrUtil::isNotEmpty)
                    .forEach(eventTargetTxHashes::add);
        }

        List<String> distinctTargetTxHashes = eventTargetTxHashes.stream().distinct().toList();
        if (CollUtil.isNotEmpty(distinctTargetTxHashes)) {
            return new RelayEventInspection(RelayEventState.READY, distinctTargetTxHashes, "");
        }
        // One indexed member (e.g. AM lambda 8) may legitimately emit no event.
        // The combined receipt still requires a protocol event from another member of THIS root.
        if (relayGroupFinalized && relayGroupTxHash.contains(":")) {
            return new RelayEventInspection(RelayEventState.READY, List.of(), "");
        }
        return relayGroupFinalized
                ? failedRelayEventInspection(relayGroupTxHash, "produced no protocol event transaction")
                : new RelayEventInspection(RelayEventState.PENDING, List.of(), "");
    }

    private static RelayEventInspection failedRelayEventInspection(String relayGroupTxHash, String reason) {
        return new RelayEventInspection(
                RelayEventState.FAILED,
                List.of(),
                String.format(
                        "relay transaction %s %s",
                        StrUtil.emptyToDefault(relayGroupTxHash, "unknown"),
                        reason
                )
        );
    }

    enum TxFinalityState {
        PENDING,
        FINALIZED,
        FAILED
    }

    record TxFinalityResult(TxFinalityState state, String txHash, String confirmState) {
    }

    enum RelayEventState {
        PENDING,
        READY,
        FAILED
    }

    record RelayEventInspection(
            RelayEventState state,
            List<String> eventTargetTxHashes,
            String errorMessage
    ) {
    }

    @SneakyThrows
    public String sendTransaction(String params, boolean sync) {
        return sendTransaction(params, sync, "operation:" + UUID.randomUUID());
    }

    @SneakyThrows
    public String sendTransaction(String params, boolean sync, String submissionId) {
        JSONObject request = JSON.parseObject(params);
        String account = request.getString(request.containsKey("delegatee") ? "delegatee" : "sender");
        JdbcTransactionCoordinator allocation = coordinator();
        String txHash = allocation.submit(submissionId, account, params.getBytes(StandardCharsets.UTF_8),
                new JdbcTransactionCoordinator.Transport() {
                    public String checkpoint() {
                        return getConsensusHeaderByHeight(coordinatorCheckpointHeight).getString("Hash");
                    }
                    public long currentIsn(String address) {
                        JSONObject ret = checkIfErrorResponse(makeRequest("dx.isn", JSON.toJSONString(orderedMap("address", address))));
                        Long isn = ret.getLong("ISN");
                        if (isn == null) { throw new IllegalStateException("node returned no ISN"); }
                        return isn;
                    }
                    public byte[] composeAndSign(long isn) {
                        request.put("isn", isn);
                        String unsigned = composeTransaction(JSON.toJSONString(request));
                        byte[] encoded = Base64.getDecoder().decode(unsigned);
                        if (encoded.length < 12 || java.nio.ByteBuffer.wrap(encoded, 8, 4)
                                .order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt() != (int) isn) {
                            throw new IllegalStateException("node did not compose the reserved ISN");
                        }
                        getBbcLogger().info("Dioxide submission prepared: id={}, account={}, isn={}", submissionId, account, isn);
                        return Base64.getDecoder().decode(signTransaction(unsigned));
                    }
                    public String broadcast(byte[] signed) {
                        return sendRawTransaction(Base64.getEncoder().encodeToString(signed), false);
                    }
                });
        getBbcLogger().info("Dioxide submission accepted: id={}, account={}, hash={}", submissionId, account, txHash);
        if (sync && !waitForTransactionConfirmed(txHash, DEFAULT_TIMEOUT)) {
            throw new IllegalStateException("Dioxide synchronous submission timed out: " + txHash);
        }
        return txHash;
    }

    @SneakyThrows
    private String composeTransaction(String params) {
        String rawResp = makeRequest("tx.compose", params);
        JSONObject resp = checkIfErrorResponse(rawResp);
        return resp.getString("TxData");
    }

    @SneakyThrows
    private String signTransaction(String unsigned_txn) {
        String rawResp = makeRequest("tx.sign", JSON.toJSONString(orderedMap(
                "sk", List.of(dioxideAccount.getPrivateKeyInString()),
                "txdata", unsigned_txn
        )));
        JSONObject resp = checkIfErrorResponse(rawResp);
        return resp.getString("TxData");
    }

    @SneakyThrows
    private String sendRawTransaction(String signed_txn, boolean sync) {
        String rawResp = makeRequest("tx.send", JSON.toJSONString(orderedMap("txdata", signed_txn)));
        if (Objects.isNull(rawResp)) {
            throw new RuntimeException("[tx.send] get null response from dioxide");
        }
        RpcResponse rpcResponse = JSON.parseObject(rawResp, RpcResponse.class);
        if (!rpcResponse.isSuccess()) {
            throw new JdbcTransactionCoordinator.NodeRejectedException(
                    rpcResponse.getErr() == null ? -1 : rpcResponse.getErr(),
                    rpcResponse.getFailResponse()
            );
        }
        JSONObject resp = rpcResponse.getSuccessResponse();
        if (Objects.isNull(resp)) {
            throw new RuntimeException("[tx.send] get malformed success response from dioxide");
        }
        String txHash = resp.getString("Hash");
        if (sync) {
            if (!waitForTransactionConfirmed(txHash, DEFAULT_TIMEOUT)) {
                throw new IllegalStateException("Dioxide synchronous submission timed out: " + txHash);
            }
        }
        return txHash;
    }

    private String makeRequest(String method, String params) {
        String uri = config.getRpcUrl() + "?req=" + method;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(params))
                .timeout(Duration.ofSeconds(10))
                .build();

        try {
            return httpClient.send(req, HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException | InterruptedException e) {
            String msg = "[makeRequest] unexpected error in method: " + method;
            getBbcLogger().error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    private JSONObject checkIfErrorResponse(String rawResp) {
        if(Objects.isNull(rawResp)) {
            getBbcLogger().error("[checkIfErrorResponse] get null response from dioxide");
            throw new RuntimeException(StrUtil.format("[checkIfErrorResponse] get null response from dioxide"));
        }
        RpcResponse resp = JSON.parseObject(rawResp, RpcResponse.class);
        if (!resp.isSuccess()) {
            getBbcLogger().error("[checkIfErrorResponse] get ERROR response from dioxide: {}", JSON.toJSONString(resp));
            throw new RuntimeException(StrUtil.format("[checkIfErrorResponse] get ERROR response from dioxide: {}", JSON.toJSONString(resp)));
        }
        return resp.getSuccessResponse();
    }

    private static <K, V> LinkedHashMap<K, V> orderedMap(Object... kvPairs) {
        if (kvPairs.length % 2 != 0) {
            throw new IllegalArgumentException("Key-value pairs must be even");
        }
        int size = kvPairs.length / 2;
        LinkedHashMap<K, V> map = LinkedHashMap.newLinkedHashMap(size);

        for (int i = 0; i < kvPairs.length; i += 2) {
            @SuppressWarnings("unchecked")
            K key = (K) kvPairs[i];
            @SuppressWarnings("unchecked")
            V value = (V) kvPairs[i + 1];
            map.put(key, value);
        }
        return map;
    }

    private Logger getBbcLogger() {
        return ObjectUtil.isNull(this.bbcLogger) ? NOPLogger.NOP_LOGGER : this.bbcLogger;
    }

}
