package com.alipay.antchain.bridge.plugins.mychain020;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.commons.core.base.CrossChainLane;
import com.alipay.antchain.bridge.commons.core.monitor.AbstractMonitorMessage;
import com.alipay.antchain.bridge.commons.core.monitor.IMonitorMessage;
import com.alipay.antchain.bridge.commons.core.monitor.MonitorMessageFactory;
import com.alipay.antchain.bridge.commons.core.sdp.ISDPMessage;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.plugins.mychain020.contract.AMContractClientEVM;
import com.alipay.antchain.bridge.plugins.mychain020.contract.PtcContractEvm;
import com.alipay.antchain.bridge.plugins.mychain020.model.ContractAddressInfo;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Config;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.crypto.hash.HashFactory;
import com.alipay.mychain.sdk.crypto.hash.HashTypeEnum;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.domain.transaction.LogEntry;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.errorcode.ErrorCode;
import com.alipay.mychain.sdk.message.query.QueryTransactionReceiptResponse;
import com.alipay.mychain.sdk.vm.EVMOutput;
import com.alipay.mychain.sdk.vm.EVMParameter;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.helpers.NOPLogger;

public final class MychainMonitorAppFlowTool {

    private static final String CONTRACT_BINARY = "/contracts/monitor_demo/MonitorSenderContract.bin";
    private static final String MONITOR_PROOF_EVENT =
            "MonitorNodeProofVerified(string,identity,identity,bool)";
    private static final String MONITOR_ROLLBACK_EVENT =
            "MonitorRollbackSent(identity,string,identity,bool)";

    private MychainMonitorAppFlowTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "inspect-receive".equals(args[0])) {
            if (args.length != 3) {
                printUsage();
                System.exit(2);
            }
            inspectReceive(args[1], args[2]);
            return;
        }
        if (args.length > 0 && "inspect-rejection".equals(args[0])) {
            if (args.length != 9) {
                printUsage();
                System.exit(2);
            }
            inspectRejection(
                    args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8]);
            return;
        }
        if (args.length > 0 && "refresh-tpbta-monitor-endorsement".equals(args[0])) {
            if (args.length != 5) {
                printUsage();
                System.exit(2);
            }
            refreshTpBtaMonitorEndorsement(
                    args[1], args[2], args[3], Integer.parseInt(args[4]));
            return;
        }
        if (args.length < 4 || (args.length > 5 && args.length != 8)) {
            printUsage();
            System.exit(2);
        }

        String configPath = args[0];
        String monitorContract = args[1];
        String receiverDomain = args[2];
        Identity receiverIdentity = new Identity(Hex.decode(normalizeIdentity(args[3])));
        boolean diagnoseOnly = args.length >= 5 && "--diagnose-only".equals(args[4]);
        String message = args.length >= 5 && !diagnoseOnly
                ? args[4]
                : "mychain monitored demo";

        Mychain020Config config = Mychain020Config.fromJsonString(
                new String(Files.readAllBytes(Paths.get(configPath)), StandardCharsets.UTF_8));
        if (args.length == 8) {
            config.setSdpContractName(new ContractAddressInfo(args[5], "").toJson());
            config.setPtcContractName(new ContractAddressInfo(args[6], "").toJson());
            config.setMonitorVerifierContractName(args[7]);
        }
        Mychain020Client client = new Mychain020Client(
                config.toJsonString().getBytes(StandardCharsets.UTF_8),
                NOPLogger.NOP_LOGGER);

        try {
            require(client.startup(), "startup mychain sdk failed");
            printSystemContractDiagnostics(client, config, monitorContract, receiverIdentity);
            if (diagnoseOnly) {
                return;
            }

            String senderContract = "MonitorSender_" + UUID.randomUUID();
            System.out.println("MONITOR_APP_SENDER_CONTRACT=" + senderContract);
            require(
                    client.deployContract(CONTRACT_BINARY, senderContract, VMTypeEnum.EVM, new EVMParameter()),
                    "deploy monitor sender failed");

            EVMParameter configureParameters = new EVMParameter("setMonitorContract(identity)");
            configureParameters.addIdentity(Utils.getIdentityByName(
                    monitorContract,
                    client.getConfig().getMychainHashType()));
            SendResponseResult configureResult = client.callContract(senderContract, configureParameters, true);
            printResponse("MONITOR_APP_CONFIGURE", configureResult);
            requireSuccess(configureResult, "configure monitor failed");

            EVMParameter sendParameters = new EVMParameter("sendUnordered(identity,string,bytes)");
            sendParameters.addIdentity(receiverIdentity);
            sendParameters.addString(receiverDomain);
            sendParameters.addBytes(message.getBytes(StandardCharsets.UTF_8));
            SendResponseResult sendResult = client.callContract(senderContract, sendParameters, true);
            printResponse("MONITOR_APP_SEND", sendResult);
            requireSuccess(sendResult, "send monitored message failed");

            System.out.println("MONITOR_APP_SENDER_IDENTITY=" + Utils.getIdentityByName(
                    senderContract,
                    client.getConfig().getMychainHashType()).hexStrValue());
            System.out.println("MONITOR_APP_TX_HASH=" + sendResult.getTxId());
            System.out.println("MONITOR_APP_RECEIVER_DOMAIN=" + receiverDomain);
            System.out.println("MONITOR_APP_RECEIVER_IDENTITY=" + receiverIdentity.hexStrValue());
            System.out.println("MONITOR_APP_MESSAGE=" + message);
        } finally {
            client.shutdown();
        }
    }

    private static void inspectReceive(String configPath, String senderContract) throws Exception {
        Mychain020Config config = Mychain020Config.fromJsonString(
                new String(Files.readAllBytes(Paths.get(configPath)), StandardCharsets.UTF_8));
        Mychain020Client client = new Mychain020Client(
                config.toJsonString().getBytes(StandardCharsets.UTF_8),
                NOPLogger.NOP_LOGGER);
        try {
            require(client.startup(), "startup mychain sdk failed");
            System.out.println("MONITOR_APP_SENDER_CONTRACT=" + senderContract);
            System.out.println("MONITOR_APP_SENDER_IDENTITY=" + Utils.getIdentityByName(
                    senderContract, client.getConfig().getMychainHashType()).hexStrValue());
            System.out.println("MONITOR_APP_RECEIVE_COUNT=" + localCallUint(
                    client, senderContract, "receiveCount()"));
            System.out.println("MONITOR_APP_LAST_RECEIVED_DOMAIN=" + localCallString(
                    client, senderContract, "lastReceivedDomain()"));
            System.out.println("MONITOR_APP_LAST_RECEIVED_AUTHOR=" + localCallIdentity(
                    client, senderContract, "lastReceivedAuthor()"));
            byte[] payload = localCallBytes(client, senderContract, "lastReceivedMessage()");
            System.out.println("MONITOR_APP_LAST_RECEIVED_HEX=" + Hex.toHexString(payload));
            System.out.println("MONITOR_APP_LAST_RECEIVED_UTF8="
                    + new String(payload, StandardCharsets.UTF_8));
        } finally {
            client.shutdown();
        }
    }

    private static void inspectRejection(
            String configPath,
            String monitorContract,
            String amContract,
            String txHash,
            String expectedSourceDomain,
            String expectedAuthorValue,
            String expectedReceiverValue,
            String expectedPayloadValue) throws Exception {
        Mychain020Config config = Mychain020Config.fromJsonString(
                new String(Files.readAllBytes(Paths.get(configPath)), StandardCharsets.UTF_8));
        Mychain020Client client = new Mychain020Client(
                config.toJsonString().getBytes(StandardCharsets.UTF_8),
                NOPLogger.NOP_LOGGER);
        byte[] expectedAuthor = Hex.decode(normalizeIdentity(expectedAuthorValue));
        byte[] expectedReceiver = Hex.decode(normalizeIdentity(expectedReceiverValue));
        byte[] expectedPayload = expectedPayloadValue.getBytes(StandardCharsets.UTF_8);
        try {
            require(client.startup(), "startup mychain sdk failed");
            TransactionReceipt receipt = queryReceipt(client, txHash);
            require(
                    ErrorCode.SUCCESS.getErrorCode() == receipt.getResult(),
                    "rejection transaction failed: result=" + receipt.getResult());

            String monitorIdentity = Utils.getIdentityByName(
                    monitorContract, config.getMychainHashType()).hexStrValue();
            String amIdentity = Utils.getIdentityByName(
                    amContract, config.getMychainHashType()).hexStrValue();
            String proofTopic = eventTopic(MONITOR_PROOF_EVENT);
            String rollbackTopic = eventTopic(MONITOR_ROLLBACK_EVENT);
            List<LogEntry> proofEvents = new ArrayList<LogEntry>();
            List<LogEntry> rollbackEvents = new ArrayList<LogEntry>();
            List<byte[]> outboundAuthMessages = new ArrayList<byte[]>();
            for (LogEntry entry : receipt.getLogs()) {
                if (entry.getTo() == null || entry.getTopics() == null || entry.getTopics().isEmpty()) {
                    continue;
                }
                String target = entry.getTo().hexStrValue();
                String topic0 = stripHexPrefix(entry.getTopics().get(0));
                if (monitorIdentity.equals(target) && proofTopic.equals(topic0)) {
                    proofEvents.add(entry);
                } else if (monitorIdentity.equals(target) && rollbackTopic.equals(topic0)) {
                    rollbackEvents.add(entry);
                } else if (amIdentity.equals(target)
                        && AMContractClientEVM.AM_MSG_SEND_SIGN_HEX.equals(topic0)) {
                    outboundAuthMessages.add(
                            new EVMOutput(Hex.toHexString(entry.getLogData())).getBytes());
                }
            }
            require(proofEvents.size() == 1, "expected exactly one MonitorNodeProofVerified event");
            require(rollbackEvents.size() == 1, "expected exactly one MonitorRollbackSent event");

            LogEntry proofEntry = proofEvents.get(0);
            require(proofEntry.getTopics().size() == 3, "unexpected proof event topics");
            EVMOutput proofData = new EVMOutput(Hex.toHexString(proofEntry.getLogData()));
            String proofDomain = proofData.getString();
            boolean proofResult = proofData.getBoolean();
            byte[] proofAuthor = topicIdentity(proofEntry.getTopics().get(1));
            byte[] proofReceiver = topicIdentity(proofEntry.getTopics().get(2));
            require(expectedSourceDomain.equals(proofDomain), "unexpected proof source domain");
            require(Arrays.equals(expectedAuthor, proofAuthor), "unexpected proof author");
            require(Arrays.equals(expectedReceiver, proofReceiver), "unexpected proof receiver");
            require(!proofResult, "expected failed monitor proof");

            LogEntry rollbackEntry = rollbackEvents.get(0);
            require(rollbackEntry.getTopics().size() == 3, "unexpected rollback event topics");
            EVMOutput rollbackData = new EVMOutput(Hex.toHexString(rollbackEntry.getLogData()));
            String rollbackDomain = rollbackData.getString();
            boolean rollbackUnordered = rollbackData.getBoolean();
            byte[] rollbackSender = topicIdentity(rollbackEntry.getTopics().get(1));
            byte[] rollbackReceiver = topicIdentity(rollbackEntry.getTopics().get(2));
            require(expectedSourceDomain.equals(rollbackDomain), "unexpected rollback target domain");
            require(Arrays.equals(expectedReceiver, rollbackSender), "unexpected rollback sender");
            require(Arrays.equals(expectedAuthor, rollbackReceiver), "unexpected rollback receiver");
            require(rollbackUnordered, "rollback must be unordered");

            int matchingRollbackMessages = 0;
            for (byte[] rawAuth : outboundAuthMessages) {
                ISDPMessage sdp = SDPMessageFactory.createSDPMessage(
                        AuthMessageFactory.createAuthMessage(rawAuth).getPayload());
                if (sdp.getVersion() != 1
                        || !expectedSourceDomain.equals(sdp.getTargetDomain().getDomain())
                        || !Arrays.equals(expectedAuthor, sdp.getTargetIdentity().getRawID())) {
                    continue;
                }
                IMonitorMessage monitor = MonitorMessageFactory.createMonitorMessage(sdp.getPayload());
                require(
                        monitor.getMonitorType() == AbstractMonitorMessage.MONITOR_ROLLBACK,
                        "unexpected outbound monitor type");
                require(
                        "MidMonitoring not pass".equals(monitor.getMonitorMsg()),
                        "unexpected outbound rollback reason");
                require(Arrays.equals(expectedPayload, monitor.getPayload()), "rollback payload mismatch");
                matchingRollbackMessages++;
            }
            require(matchingRollbackMessages == 1, "expected exactly one outbound rollback message");

            System.out.println("rejection.txHash=" + txHash);
            System.out.println("rejection.proofEventCount=" + proofEvents.size());
            System.out.println("rejection.proofResult=" + proofResult);
            System.out.println("rejection.sourceDomain=" + proofDomain);
            System.out.println("rejection.author=" + Hex.toHexString(proofAuthor));
            System.out.println("rejection.receiver=" + Hex.toHexString(proofReceiver));
            System.out.println("rejection.rollbackEventCount=" + rollbackEvents.size());
            System.out.println("rejection.rollbackDomain=" + rollbackDomain);
            System.out.println("rejection.rollbackSender=" + Hex.toHexString(rollbackSender));
            System.out.println("rejection.rollbackReceiver=" + Hex.toHexString(rollbackReceiver));
            System.out.println("rejection.rollbackUnordered=" + rollbackUnordered);
            System.out.println("rejection.rollbackMonitorType=" + AbstractMonitorMessage.MONITOR_ROLLBACK);
            System.out.println("rejection.rollbackReason=MidMonitoring not pass");
            System.out.println("rejection.rollbackPayload.hex=" + Hex.toHexString(expectedPayload));
            System.out.println("rejection.rollbackPayload.utf8=" + expectedPayloadValue);
        } finally {
            client.shutdown();
        }
    }

    private static void refreshTpBtaMonitorEndorsement(
            String configPath,
            String ptcContract,
            String laneKey,
            int tpbtaVersion) throws Exception {
        Mychain020Config config = Mychain020Config.fromJsonString(
                new String(Files.readAllBytes(Paths.get(configPath)), StandardCharsets.UTF_8));
        Mychain020Client client = new Mychain020Client(
                config.toJsonString().getBytes(StandardCharsets.UTF_8),
                NOPLogger.NOP_LOGGER);
        try {
            require(client.startup(), "startup mychain sdk failed");
            PtcContractEvm ptc = new PtcContractEvm(client, NOPLogger.NOP_LOGGER);
            ptc.setContractAddress(ptcContract);
            CrossChainLane lane = CrossChainLane.fromLaneKey(laneKey);
            ThirdPartyBlockchainTrustAnchor existing = ptc.getTpBta(lane, tpbtaVersion);
            require(existing != null, "tpbta not found on Mychain PtcHub");
            require(
                    laneKey.equals(existing.getCrossChainLane().getLaneKey()),
                    "on-chain tpbta lane mismatch");
            require(
                    tpbtaVersion == existing.getTpbtaVersion(),
                    "on-chain tpbta version mismatch");

            EVMParameter parameters = new EVMParameter("addTpBta(bytes)");
            parameters.addBytes(existing.encode());
            SendResponseResult result = client.callContract(ptcContract, parameters, true);
            printResponse("TPBTA_MONITOR_ENDORSEMENT_REFRESH", result);
            requireSuccess(result, "refresh tpbta monitor endorsement failed");
            System.out.println("TPBTA_MONITOR_ENDORSEMENT_LANE=" + laneKey);
            System.out.println("TPBTA_MONITOR_ENDORSEMENT_VERSION=" + tpbtaVersion);
        } finally {
            client.shutdown();
        }
    }

    private static TransactionReceipt queryReceipt(Mychain020Client client, String txHash)
            throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            QueryTransactionReceiptResponse response = client.getTxReceiptByTxhash(txHash);
            if (response != null && response.isSuccess() && response.getTransactionReceipt() != null) {
                return response.getTransactionReceipt();
            }
            Thread.sleep(500L);
        }
        throw new IllegalStateException("transaction receipt not found: " + txHash);
    }

    private static String eventTopic(String signature) {
        return Hex.toHexString(HashFactory.getHash(HashTypeEnum.Keccak).hash(
                signature.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] topicIdentity(String topic) {
        String normalized = stripHexPrefix(topic);
        require(normalized.length() >= 64, "invalid identity topic");
        return Hex.decode(normalized.substring(normalized.length() - 64));
    }

    private static String stripHexPrefix(String value) {
        return value.startsWith("0x") || value.startsWith("0X") ? value.substring(2) : value;
    }

    private static void printSystemContractDiagnostics(
            Mychain020Client client,
            Mychain020Config config,
            String monitorContract,
            Identity receiverIdentity) {
        String sdpContract = ContractAddressInfo.decode(config.getSdpContractName()).getEvmContractAddress();
        String expectedSdpIdentity = Utils.getIdentityByName(
                sdpContract,
                config.getMychainHashType()).hexStrValue();
        String expectedMonitorIdentity = Utils.getIdentityByName(
                monitorContract,
                config.getMychainHashType()).hexStrValue();
        String ptcContract = ContractAddressInfo.resolveEvmContractAddress(config.getPtcContractName());
        String monitorVerifierContract = config.getMonitorVerifierContractName();

        BigInteger monitorControl = localCallUint(client, monitorContract, "getMonitorControl()");
        String monitorProtocol = localCallIdentity(client, monitorContract, "getProtocol()");
        String sdpMonitor = localCallIdentity(client, sdpContract, "monitorAddress()");

        EVMParameter preMonitoringParameters = new EVMParameter("preMonitoring(identity)");
        preMonitoringParameters.addIdentity(receiverIdentity);
        boolean preMonitoring = localCallBoolean(client, monitorContract, preMonitoringParameters);

        System.out.println("MYCHAIN_MONITOR_CONTROL=" + monitorControl);
        System.out.println("MYCHAIN_MONITOR_PROTOCOL=" + monitorProtocol);
        System.out.println("MYCHAIN_EXPECTED_SDP_IDENTITY=" + expectedSdpIdentity);
        System.out.println("MYCHAIN_SDP_MONITOR=" + sdpMonitor);
        System.out.println("MYCHAIN_EXPECTED_MONITOR_IDENTITY=" + expectedMonitorIdentity);
        System.out.println("MYCHAIN_RECEIVER_ALLOWED=" + preMonitoring);
        if (ptcContract != null && !ptcContract.isEmpty()
                && monitorVerifierContract != null && !monitorVerifierContract.isEmpty()) {
            System.out.println("MYCHAIN_PTC_HUB_MONITOR_VERIFIER=" + localCallIdentity(
                    client,
                    ptcContract,
                    "getMonitorVerifier()"));
            System.out.println("MYCHAIN_EXPECTED_MONITOR_VERIFIER_IDENTITY=" + Utils.getIdentityByName(
                    monitorVerifierContract,
                    config.getMychainHashType()).hexStrValue());
            System.out.println("MYCHAIN_MONITOR_VERIFIER_PTC_HUB=" + localCallIdentity(
                    client,
                    monitorVerifierContract,
                    "getPtcHubAddress()"));
            System.out.println("MYCHAIN_EXPECTED_PTC_IDENTITY=" + Utils.getIdentityByName(
                    ptcContract,
                    config.getMychainHashType()).hexStrValue());
        }
    }

    private static BigInteger localCallUint(Mychain020Client client, String contract, String method) {
        TransactionReceipt receipt = localCall(client, contract, new EVMParameter(method));
        return new EVMOutput(Hex.toHexString(receipt.getOutput())).getUint();
    }

    private static String localCallIdentity(Mychain020Client client, String contract, String method) {
        TransactionReceipt receipt = localCall(client, contract, new EVMParameter(method));
        return new EVMOutput(Hex.toHexString(receipt.getOutput())).getIdentity().hexStrValue();
    }

    private static String localCallString(Mychain020Client client, String contract, String method) {
        TransactionReceipt receipt = localCall(client, contract, new EVMParameter(method));
        return new EVMOutput(Hex.toHexString(receipt.getOutput())).getString();
    }

    private static byte[] localCallBytes(Mychain020Client client, String contract, String method) {
        TransactionReceipt receipt = localCall(client, contract, new EVMParameter(method));
        return new EVMOutput(Hex.toHexString(receipt.getOutput())).getBytes();
    }

    private static boolean localCallBoolean(
            Mychain020Client client,
            String contract,
            EVMParameter parameters) {
        TransactionReceipt receipt = localCall(client, contract, parameters);
        return new EVMOutput(Hex.toHexString(receipt.getOutput())).getBoolean();
    }

    private static TransactionReceipt localCall(
            Mychain020Client client,
            String contract,
            EVMParameter parameters) {
        TransactionReceipt receipt = client.localCallContract(contract, parameters);
        require(receipt != null, "local call returned no receipt: " + contract);
        require(
                ErrorCode.SUCCESS.getErrorCode() == receipt.getResult(),
                "local call failed: " + contract + ", result=" + receipt.getResult());
        require(receipt.getOutput() != null, "local call returned no output: " + contract);
        return receipt;
    }

    private static void printResponse(String prefix, SendResponseResult result) {
        if (result == null) {
            System.out.println(prefix + "_RESULT=null");
            return;
        }
        System.out.println(prefix + "_TX_HASH=" + result.getTxId());
        System.out.println(prefix + "_CONFIRMED=" + result.isConfirmed());
        System.out.println(prefix + "_SUCCESS=" + result.isSuccess());
        System.out.println(prefix + "_ERROR_CODE=" + result.getErrorCode());
        System.out.println(prefix + "_ERROR_MESSAGE=" + result.getErrorMessage());
    }

    private static void requireSuccess(SendResponseResult result, String action) {
        require(result != null, action + ": empty response");
        require(
                result.isSuccess(),
                action
                        + ": tx=" + result.getTxId()
                        + ", confirmed=" + result.isConfirmed()
                        + ", errorCode=" + result.getErrorCode()
                        + ", errorMessage=" + result.getErrorMessage());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static String normalizeIdentity(String identity) {
        String normalized = identity.startsWith("0x") ? identity.substring(2) : identity;
        if (normalized.length() != 64) {
            throw new IllegalArgumentException("receiver identity must contain exactly 32 bytes");
        }
        return normalized;
    }

    private static void printUsage() {
        System.err.println("usage: MychainMonitorAppFlowTool <config> <monitor> <domain> "
                + "<receiver-identity> [message|--diagnose-only] [sdp ptc monitor-verifier]\n"
                + "   or: MychainMonitorAppFlowTool inspect-receive <config> <sender-contract>\n"
                + "   or: MychainMonitorAppFlowTool inspect-rejection <config> <monitor> <am> "
                + "<tx-hash> <expected-source-domain> <expected-author> <expected-receiver> "
                + "<expected-payload>\n"
                + "   or: MychainMonitorAppFlowTool refresh-tpbta-monitor-endorsement "
                + "<config> <ptc> <lane-key> <tpbta-version>");
    }
}
