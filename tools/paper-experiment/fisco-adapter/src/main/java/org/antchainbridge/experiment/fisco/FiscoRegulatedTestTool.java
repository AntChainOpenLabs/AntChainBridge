package org.antchainbridge.experiment.fisco;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.AuthMessageContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.bbc.syscontract.MonitorContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.SDPContract;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.base.ConsensusState;
import com.alipay.antchain.bridge.plugins.fiscobcos3.FISCOBCOSBBCService;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.AppContract;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.Monitor;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.MonitorVerifier;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.PtcHub;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.crypto.hash.Keccak256;

/**
 * Small operational helper for the regulated FISCO functional tests.
 *
 * <p>The chain configuration is supplied by path and is never printed. Supported commands:
 *
 * <pre>
 * deploy-app CONFIG_JSON SDP_ADDRESS MONITOR_ADDRESS
 * send-unordered CONFIG_JSON APP_ADDRESS TARGET_DOMAIN TARGET_IDENTITY PAYLOAD
 * inspect-app CONFIG_JSON APP_ADDRESS
 * inspect-rejection CONFIG_JSON MONITOR_ADDRESS APP_ADDRESS TX_HASH EXPECTED_SOURCE_DOMAIN EXPECTED_AUTHOR EXPECTED_RECEIVER EXPECTED_BASELINE
 * inspect-rollback CONFIG_JSON MONITOR_ADDRESS TX_HASH
 * inspect-return CONFIG_JSON MONITOR_ADDRESS APP_ADDRESS TX_HASH EXPECTED_DOMAIN EXPECTED_AUTHOR EXPECTED_PAYLOAD
 * inspect-success CONFIG_JSON MONITOR_ADDRESS APP_ADDRESS TX_HASH EXPECTED_DOMAIN EXPECTED_AUTHOR EXPECTED_PAYLOAD
 * chain-info CONFIG_JSON AM_ADDRESS
 * inspect-ptc CONFIG_JSON PTC_ADDRESS
 * inspect-cert CERT_PEM
 * deploy-ptc CONFIG_JSON ROOT_CERT AM_ADDRESS SDP_ADDRESS MONITOR_ADDRESS
 * </pre>
 */
public final class FiscoRegulatedTestTool {

    private FiscoRegulatedTestTool() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw usage();
        }
        switch (args[0]) {
            case "deploy-app":
                requireArgCount(args, 4);
                deployApp(Path.of(args[1]), args[2], args[3]);
                return;
            case "send-unordered":
                requireArgCount(args, 6);
                sendUnordered(Path.of(args[1]), args[2], args[3], args[4], args[5]);
                return;
            case "inspect-app":
                requireArgCount(args, 3);
                inspectApp(Path.of(args[1]), args[2]);
                return;
            case "inspect-rejection":
                requireArgCount(args, 9);
                inspectRejection(
                        Path.of(args[1]),
                        args[2],
                        args[3],
                        args[4],
                        args[5],
                        args[6],
                        args[7],
                        args[8]);
                return;
            case "inspect-rollback":
                requireArgCount(args, 4);
                inspectRollback(Path.of(args[1]), args[2], args[3]);
                return;
            case "inspect-return":
                requireArgCount(args, 8);
                inspectReturn(
                        Path.of(args[1]), args[2], args[3], args[4], args[5], args[6], args[7]);
                return;
            case "inspect-success":
                requireArgCount(args, 8);
                inspectSuccess(
                        Path.of(args[1]), args[2], args[3], args[4], args[5], args[6], args[7]);
                return;
            case "chain-info":
                requireArgCount(args, 3);
                printChainInfo(Path.of(args[1]), args[2]);
                return;
            case "inspect-ptc":
                requireArgCount(args, 3);
                inspectPtc(Path.of(args[1]), args[2]);
                return;
            case "inspect-cert":
                requireArgCount(args, 2);
                inspectCert(Path.of(args[1]));
                return;
            case "deploy-ptc":
                requireArgCount(args, 6);
                deployPtc(
                        Path.of(args[1]), Path.of(args[2]), args[3], args[4], args[5]);
                return;
            default:
                throw usage();
        }
    }

    private static void sendUnordered(
            Path configPath,
            String appValue,
            String targetDomain,
            String targetIdentity,
            String payloadValue) throws Exception {
        String appAddress = normaliseAddress(appValue);
        byte[] receiver = parseIdentity(targetIdentity);
        byte[] payload = payloadValue.getBytes(StandardCharsets.UTF_8);
        withService(configPath, service -> {
            AppContract app = AppContract.load(appAddress, service.getClient(), service.getKeyPair());
            TransactionReceipt receipt = app.sendUnorderedMessage(targetDomain, receiver, payload);
            // Preserve the submitted transaction identity even if a later assertion fails.
            System.out.println("send.txHash=" + receipt.getTransactionHash());
            System.out.flush();
            requireSuccessful(receipt, "send unordered monitored message");
            List<AppContract.SendCrosschainMsgEventResponse> events = app.getSendCrosschainMsgEvents(receipt);
            if (events.size() != 1) {
                throw new IllegalStateException("expected exactly one sendCrosschainMsg event");
            }
            AppContract.SendCrosschainMsgEventResponse event = events.get(0);
            if (!targetDomain.equals(event.receiverDomain)
                    || !Arrays.equals(receiver, event.receiver)
                    || !Arrays.equals(payload, event.message)
                    || Boolean.TRUE.equals(event.isOrdered)) {
                throw new IllegalStateException("unexpected sendCrosschainMsg event content");
            }
            System.out.println("send.blockNumber=" + receipt.getBlockNumber());
            System.out.println("send.app=" + appAddress);
            System.out.println("send.receiverDomain=" + event.receiverDomain);
            System.out.println("send.receiver=" + HexFormat.of().formatHex(event.receiver));
            System.out.println("send.unordered=" + !event.isOrdered);
            System.out.println("send.payload.hex=" + HexFormat.of().formatHex(event.message));
            System.out.println("send.payload.utf8=" + new String(event.message, StandardCharsets.UTF_8));
        });
    }

    private static void deployApp(Path configPath, String sdpValue, String monitorValue) throws Exception {
        String sdpAddress = normaliseAddress(sdpValue);
        String monitorAddress = normaliseAddress(monitorValue);
        withService(configPath, service -> {
            AppContract app = AppContract.deploy(service.getClient(), service.getKeyPair());
            TransactionReceipt protocolReceipt = app.setProtocol(sdpAddress);
            requireSuccessful(protocolReceipt, "set AppContract SDP");
            TransactionReceipt monitorReceipt = app.setMonitorContract(monitorAddress);
            requireSuccessful(monitorReceipt, "set AppContract Monitor");

            if (!sdpAddress.equalsIgnoreCase(app.sdpAddress())) {
                throw new IllegalStateException("AppContract SDP binding did not persist");
            }
            if (!monitorAddress.equalsIgnoreCase(app.monitorAddress())) {
                throw new IllegalStateException("AppContract Monitor binding did not persist");
            }

            System.out.println("app.address=" + app.getContractAddress());
            System.out.println("app.sdp=" + app.sdpAddress());
            System.out.println("app.monitor=" + app.monitorAddress());
            System.out.println("app.setProtocol.txHash=" + protocolReceipt.getTransactionHash());
            System.out.println("app.setMonitor.txHash=" + monitorReceipt.getTransactionHash());
        });
    }

    private static void inspectApp(Path configPath, String appValue) throws Exception {
        String appAddress = normaliseAddress(appValue);
        withService(configPath, service -> {
            AppContract app = AppContract.load(appAddress, service.getClient(), service.getKeyPair());
            byte[] payload = app.getLastUnorderedMsg();
            System.out.println("app.address=" + app.getContractAddress());
            System.out.println("app.sdp=" + app.sdpAddress());
            System.out.println("app.monitor=" + app.monitorAddress());
            System.out.println("app.monitorControl=" + Monitor.load(
                    app.monitorAddress(), service.getClient(), service.getKeyPair()).getMonitorControl());
            System.out.println("app.lastUnordered.hex=" + HexFormat.of().formatHex(payload));
            System.out.println("app.lastUnordered.utf8=" + new String(payload, StandardCharsets.UTF_8));
        });
    }

    private static void inspectRollback(Path configPath, String monitorValue, String txHash) throws Exception {
        String monitorAddress = normaliseAddress(monitorValue);
        withService(configPath, service -> {
            TransactionReceipt receipt = service.getClient()
                    .getTransactionReceipt(txHash, true)
                    .getTransactionReceipt();
            if (receipt == null) {
                throw new IllegalStateException("transaction receipt not found: " + txHash);
            }
            requireSuccessful(receipt, "rollback receive transaction");

            Monitor monitor = Monitor.load(monitorAddress, service.getClient(), service.getKeyPair());
            List<Monitor.ReceiveMonitorRollbackMessageEventResponse> events =
                    monitor.getReceiveMonitorRollbackMessageEvents(receipt);
            if (events.isEmpty()) {
                throw new IllegalStateException("receiveMonitorRollbackMessage event not found: " + txHash);
            }

            System.out.println("rollback.txHash=" + receipt.getTransactionHash());
            System.out.println("rollback.blockNumber=" + receipt.getBlockNumber());
            System.out.println("rollback.eventCount=" + events.size());
            for (int i = 0; i < events.size(); i++) {
                Monitor.ReceiveMonitorRollbackMessageEventResponse event = events.get(i);
                System.out.println("rollback[" + i + "].monitorType=" + event.monitorType);
                System.out.println("rollback[" + i + "].senderDomain=" + event.senderDomain);
                System.out.println("rollback[" + i + "].sender=" + HexFormat.of().formatHex(event.sender));
                System.out.println("rollback[" + i + "].receiver=" + HexFormat.of().formatHex(event.receiver));
                System.out.println("rollback[" + i + "].reason=" + event.monitorMsg);
            }
        });
    }

    private static void inspectReturn(
            Path configPath,
            String monitorValue,
            String appValue,
            String txHash,
            String expectedDomain,
            String expectedAuthorValue,
            String expectedPayloadValue) throws Exception {
        String monitorAddress = normaliseAddress(monitorValue);
        String appAddress = normaliseAddress(appValue);
        byte[] expectedAuthor = parseIdentity(expectedAuthorValue);
        byte[] expectedReceiver = parseIdentity(appAddress);
        byte[] expectedPayload = expectedPayloadValue.getBytes(StandardCharsets.UTF_8);
        withService(configPath, service -> {
            TransactionReceipt receipt = service.getClient()
                    .getTransactionReceipt(txHash, true)
                    .getTransactionReceipt();
            if (receipt == null) {
                throw new IllegalStateException("transaction receipt not found: " + txHash);
            }
            requireSuccessful(receipt, "rollback return transaction");

            Monitor monitor = Monitor.load(monitorAddress, service.getClient(), service.getKeyPair());
            AppContract app = AppContract.load(appAddress, service.getClient(), service.getKeyPair());
            List<Monitor.ReceiveMonitorRollbackMessageEventResponse> rollbackEvents =
                    monitor.getReceiveMonitorRollbackMessageEvents(receipt);
            List<AppContract.RecvCrosschainMsgEventResponse> receiveEvents =
                    app.getRecvCrosschainMsgEvents(receipt);
            if (rollbackEvents.size() != 1) {
                throw new IllegalStateException("expected exactly one rollback receive event");
            }
            if (receiveEvents.size() != 1) {
                throw new IllegalStateException("expected exactly one app receive event");
            }

            Monitor.ReceiveMonitorRollbackMessageEventResponse rollback = rollbackEvents.get(0);
            AppContract.RecvCrosschainMsgEventResponse received = receiveEvents.get(0);
            if (!BigInteger.valueOf(3).equals(rollback.monitorType)
                    || !expectedDomain.equals(rollback.senderDomain)
                    || !Arrays.equals(expectedAuthor, rollback.sender)
                    || !Arrays.equals(expectedReceiver, rollback.receiver)
                    || !"MidMonitoring not pass".equals(rollback.monitorMsg)) {
                throw new IllegalStateException("unexpected rollback receive event content");
            }
            if (!expectedDomain.equals(received.senderDomain)
                    || !Arrays.equals(expectedAuthor, received.author)
                    || !Arrays.equals(expectedPayload, received.message)
                    || Boolean.TRUE.equals(received.isOrdered)) {
                throw new IllegalStateException("unexpected app receive event content");
            }
            if (!Arrays.equals(expectedPayload, app.getLastUnorderedMsg())) {
                throw new IllegalStateException("AppContract last unordered payload does not match");
            }

            System.out.println("return.txHash=" + receipt.getTransactionHash());
            System.out.println("return.blockNumber=" + receipt.getBlockNumber());
            System.out.println("return.rollbackEventCount=" + rollbackEvents.size());
            System.out.println("return.monitorType=" + rollback.monitorType);
            System.out.println("return.senderDomain=" + rollback.senderDomain);
            System.out.println("return.sender=" + HexFormat.of().formatHex(rollback.sender));
            System.out.println("return.receiver=" + HexFormat.of().formatHex(rollback.receiver));
            System.out.println("return.reason=" + rollback.monitorMsg);
            System.out.println("return.appEventCount=" + receiveEvents.size());
            System.out.println("return.unordered=" + !received.isOrdered);
            System.out.println("return.payload.hex=" + HexFormat.of().formatHex(received.message));
            System.out.println("return.payload.utf8=" + new String(received.message, StandardCharsets.UTF_8));
        });
    }

    private static void inspectRejection(
            Path configPath,
            String monitorValue,
            String appValue,
            String txHash,
            String expectedSourceDomain,
            String expectedAuthorValue,
            String expectedReceiverValue,
            String expectedBaselineValue) throws Exception {
        String monitorAddress = normaliseAddress(monitorValue);
        String appAddress = normaliseAddress(appValue);
        byte[] expectedAuthor = parseIdentity(expectedAuthorValue);
        byte[] expectedReceiver = parseIdentity(expectedReceiverValue);
        byte[] appIdentity = parseIdentity(appAddress);
        byte[] expectedBaseline = expectedBaselineValue.getBytes(StandardCharsets.UTF_8);
        if (!Arrays.equals(expectedReceiver, appIdentity)) {
            throw new IllegalArgumentException("expected receiver does not match AppContract identity");
        }
        withService(configPath, service -> {
            TransactionReceipt receipt = service.getClient()
                    .getTransactionReceipt(txHash, true)
                    .getTransactionReceipt();
            if (receipt == null) {
                throw new IllegalStateException("transaction receipt not found: " + txHash);
            }
            requireSuccessful(receipt, "regulated rejection transaction");

            Monitor monitor = Monitor.load(monitorAddress, service.getClient(), service.getKeyPair());
            AppContract app = AppContract.load(appAddress, service.getClient(), service.getKeyPair());
            List<Monitor.VerifyMonitorNodeProofMessageEventResponse> proofEvents =
                    monitor.getVerifyMonitorNodeProofMessageEvents(receipt);
            List<Monitor.SendMonitorRollbakMessageEventResponse> rollbackEvents =
                    monitor.getSendMonitorRollbakMessageEvents(receipt);
            List<AppContract.RecvCrosschainMsgEventResponse> receiveEvents =
                    app.getRecvCrosschainMsgEvents(receipt);
            if (proofEvents.size() != 1 || !Boolean.FALSE.equals(proofEvents.get(0).result)) {
                throw new IllegalStateException(
                        "expected exactly one failed monitor proof event: " + txHash);
            }
            if (rollbackEvents.size() != 1) {
                throw new IllegalStateException(
                        "expected exactly one rollback send event: " + txHash);
            }

            Monitor.VerifyMonitorNodeProofMessageEventResponse proof = proofEvents.get(0);
            Monitor.SendMonitorRollbakMessageEventResponse rollback = rollbackEvents.get(0);
            if (!expectedSourceDomain.equals(proof.senderDomain)
                    || !Arrays.equals(expectedAuthor, proof.author)
                    || !appAddress.equalsIgnoreCase(proof.receiverID)) {
                throw new IllegalStateException("unexpected failed proof event content");
            }
            if (!BigInteger.valueOf(3).equals(rollback.monitorType)
                    || !Arrays.equals(appIdentity, rollback.sender)
                    || !expectedSourceDomain.equals(rollback.receiverDomain)
                    || !Arrays.equals(expectedAuthor, rollback.receiver)
                    || !"MidMonitoring not pass".equals(rollback.monitorMsg)) {
                throw new IllegalStateException("unexpected rollback send event content");
            }
            if (!receiveEvents.isEmpty()) {
                throw new IllegalStateException("rejected message unexpectedly reached AppContract");
            }
            if (!Arrays.equals(expectedBaseline, app.getLastUnorderedMsg())) {
                throw new IllegalStateException("FISCO AppContract last unordered payload changed");
            }

            System.out.println("rejection.txHash=" + receipt.getTransactionHash());
            System.out.println("rejection.blockNumber=" + receipt.getBlockNumber());
            System.out.println("rejection.proofEventCount=" + proofEvents.size());
            System.out.println("rejection.proofResult=" + proof.result);
            System.out.println("rejection.senderDomain=" + proof.senderDomain);
            System.out.println("rejection.author=" + HexFormat.of().formatHex(proof.author));
            System.out.println("rejection.receiver=" + proof.receiverID);
            System.out.println("rejection.rollbackEventCount=" + rollbackEvents.size());
            System.out.println("rejection.rollbackMonitorType=" + rollback.monitorType);
            System.out.println("rejection.rollbackSender=" + HexFormat.of().formatHex(rollback.sender));
            System.out.println("rejection.rollbackReceiverDomain=" + rollback.receiverDomain);
            System.out.println("rejection.rollbackReceiver=" + HexFormat.of().formatHex(rollback.receiver));
            System.out.println("rejection.rollbackReason=" + rollback.monitorMsg);
            System.out.println("rejection.appEventCount=" + receiveEvents.size());
            System.out.println("rejection.appBaselineUnchanged=true");
        });
    }

    private static void inspectSuccess(
            Path configPath,
            String monitorValue,
            String appValue,
            String txHash,
            String expectedDomain,
            String expectedAuthorValue,
            String expectedPayloadValue) throws Exception {
        String monitorAddress = normaliseAddress(monitorValue);
        String appAddress = normaliseAddress(appValue);
        byte[] expectedAuthor = parseIdentity(expectedAuthorValue);
        byte[] expectedPayload = expectedPayloadValue.getBytes(StandardCharsets.UTF_8);
        withService(configPath, service -> {
            TransactionReceipt receipt = service.getClient()
                    .getTransactionReceipt(txHash, true)
                    .getTransactionReceipt();
            if (receipt == null) {
                throw new IllegalStateException("transaction receipt not found: " + txHash);
            }
            requireSuccessful(receipt, "regulated success transaction");
            Monitor monitor = Monitor.load(monitorAddress, service.getClient(), service.getKeyPair());
            AppContract app = AppContract.load(appAddress, service.getClient(), service.getKeyPair());
            List<Monitor.VerifyMonitorNodeProofMessageEventResponse> proofEvents =
                    monitor.getVerifyMonitorNodeProofMessageEvents(receipt);
            List<Monitor.SendMonitorRollbakMessageEventResponse> rollbackEvents =
                    monitor.getSendMonitorRollbakMessageEvents(receipt);
            List<AppContract.RecvCrosschainMsgEventResponse> receiveEvents =
                    app.getRecvCrosschainMsgEvents(receipt);
            if (proofEvents.size() != 1 || !Boolean.TRUE.equals(proofEvents.get(0).result)) {
                throw new IllegalStateException("expected exactly one successful monitor proof event");
            }
            if (!rollbackEvents.isEmpty()) {
                throw new IllegalStateException("successful delivery unexpectedly emitted rollback");
            }
            if (receiveEvents.size() != 1) {
                throw new IllegalStateException("expected exactly one app receive event");
            }
            Monitor.VerifyMonitorNodeProofMessageEventResponse proof = proofEvents.get(0);
            AppContract.RecvCrosschainMsgEventResponse received = receiveEvents.get(0);
            if (!expectedDomain.equals(proof.senderDomain)
                    || !Arrays.equals(expectedAuthor, proof.author)
                    || !appAddress.equalsIgnoreCase(proof.receiverID)) {
                throw new IllegalStateException("unexpected successful proof event content");
            }
            if (!expectedDomain.equals(received.senderDomain)
                    || !Arrays.equals(expectedAuthor, received.author)
                    || !Arrays.equals(expectedPayload, received.message)
                    || Boolean.TRUE.equals(received.isOrdered)) {
                throw new IllegalStateException("unexpected app receive event content");
            }
            if (!Arrays.equals(expectedPayload, app.getLastUnorderedMsg())) {
                throw new IllegalStateException("AppContract last unordered payload does not match");
            }
            System.out.println("success.txHash=" + receipt.getTransactionHash());
            System.out.println("success.blockNumber=" + receipt.getBlockNumber());
            System.out.println("success.proofEventCount=" + proofEvents.size());
            System.out.println("success.proofResult=" + proof.result);
            System.out.println("success.rollbackEventCount=" + rollbackEvents.size());
            System.out.println("success.appEventCount=" + receiveEvents.size());
            System.out.println("success.senderDomain=" + received.senderDomain);
            System.out.println("success.author=" + HexFormat.of().formatHex(received.author));
            System.out.println("success.unordered=" + !received.isOrdered);
            System.out.println("success.payload.hex=" + HexFormat.of().formatHex(received.message));
            System.out.println("success.payload.utf8=" + new String(received.message, StandardCharsets.UTF_8));
        });
    }

    private static void printChainInfo(Path configPath, String amValue) throws Exception {
        String amAddress = normaliseAddress(amValue);
        withService(configPath, service -> {
            BigInteger height = BigInteger.valueOf(service.queryLatestHeight());
            ConsensusState state = service.readConsensusState(height);
            String consensusNodeInfo = new String(state.getConsensusNodeInfo(), StandardCharsets.UTF_8);
            String subjectIdentity = "{\"consensusNodeInfo\":" + consensusNodeInfo
                    + ",\"amContract\":\"" + amAddress + "\"}";

            System.out.println("chain.height=" + height);
            System.out.println("chain.hash=" + HexFormat.of().formatHex(state.getHash()));
            System.out.println("chain.consensusNodeInfo=" + consensusNodeInfo);
            System.out.println("chain.subjectIdentityBase64="
                    + Base64.getEncoder().encodeToString(subjectIdentity.getBytes(StandardCharsets.UTF_8)));
        });
    }

    private static void inspectPtc(Path configPath, String ptcValue) throws Exception {
        String ptcAddress = normaliseAddress(ptcValue);
        JSONObject config = JSON.parseObject(Files.readString(configPath, StandardCharsets.UTF_8));
        var configuredRoot = CrossChainCertificateUtil.readCrossChainCertificateFromPem(
                config.getString("bcdnsRootCertPem").getBytes(StandardCharsets.UTF_8));
        byte[] configuredRootCert = configuredRoot.encode();
        byte[] configuredRootOwner = configuredRoot.getCredentialSubjectInstance().getApplicant().encode();
        byte[] configuredRootOwnerKey = new Keccak256().hash(configuredRootOwner);
        withService(configPath, service -> {
            PtcHub ptcHub = PtcHub.load(ptcAddress, service.getClient(), service.getKeyPair());
            byte[] rootCert = ptcHub.bcdnsCertMap("root");
            System.out.println("ptc.address=" + ptcAddress);
            System.out.println("ptc.owner=" + ptcHub.owner());
            System.out.println("client.account=" + service.getKeyPair().getAddress());
            System.out.println("ptc.rootCert.length=" + rootCert.length);
            System.out.println("ptc.rootCert.sha256="
                    + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rootCert)));
            System.out.println("config.rootCert.sha256=" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(configuredRootCert)));
            System.out.println("ptc.rootMatchesConfig=" + java.util.Arrays.equals(rootCert, configuredRootCert));
            System.out.println("config.rootOwner="
                    + configuredRoot.getCredentialSubjectInstance().getApplicant().toHex());
            System.out.println("config.rootOwnerKey=" + HexFormat.of().formatHex(configuredRootOwnerKey));
            System.out.println("ptc.rootOwnerAlias="
                    + ptcHub.ownerOidToBcdnsDomainSpaceMap(configuredRootOwnerKey));
            System.out.println("ptc.supportedTypes=" + ptcHub.getSupportedPTCType());
        });
    }

    private static void inspectCert(Path certPath) throws Exception {
        var cert = CrossChainCertificateUtil.readCrossChainCertificateFromPem(Files.readAllBytes(certPath));
        byte[] encoded = cert.encode();
        byte[] owner = cert.getCredentialSubjectInstance().getApplicant().encode();
        System.out.println("cert.path=" + certPath.toAbsolutePath());
        System.out.println("cert.sha256="
                + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded)));
        System.out.println("cert.owner=" + cert.getCredentialSubjectInstance().getApplicant().toHex());
        System.out.println("cert.ownerKey=" + HexFormat.of().formatHex(new Keccak256().hash(owner)));
    }

    private static void deployPtc(
            Path configPath,
            Path rootCertPath,
            String amValue,
            String sdpValue,
            String monitorValue) throws Exception {
        String amAddress = normaliseAddress(amValue);
        String sdpAddress = normaliseAddress(sdpValue);
        String monitorAddress = normaliseAddress(monitorValue);
        JSONObject config = JSON.parseObject(Files.readString(configPath, StandardCharsets.UTF_8));
        config.put("bcdnsRootCertPem", Files.readString(rootCertPath, StandardCharsets.UTF_8));

        DefaultBBCContext context = new DefaultBBCContext();
        context.setConfForBlockchainClient(config.toJSONString().getBytes(StandardCharsets.UTF_8));
        AuthMessageContract am = new AuthMessageContract();
        am.setContractAddress(amAddress);
        am.setStatus(ContractStatusEnum.CONTRACT_READY);
        context.setAuthMessageContract(am);
        SDPContract sdp = new SDPContract();
        sdp.setContractAddress(sdpAddress);
        sdp.setStatus(ContractStatusEnum.CONTRACT_READY);
        context.setSdpContract(sdp);
        MonitorContract monitorContract = new MonitorContract();
        monitorContract.setContractAddress(monitorAddress);
        monitorContract.setStatus(ContractStatusEnum.CONTRACT_READY);
        context.setMonitorContract(monitorContract);

        FISCOBCOSBBCService service = new FISCOBCOSBBCService();
        boolean started = false;
        try {
            service.startup(context);
            started = true;
            service.setupPTCContract();
            String ptcAddress = service.getContext().getPtcContract().getContractAddress();
            service.setPtcHubInMonitorVerifier(ptcAddress);
            service.setPtcContract(ptcAddress);

            Monitor monitor = Monitor.load(monitorAddress, service.getClient(), service.getKeyPair());
            String verifierAddress = monitor.getMonitorVerifier();
            MonitorVerifier verifier = MonitorVerifier.load(
                    verifierAddress, service.getClient(), service.getKeyPair());
            PtcHub ptcHub = PtcHub.load(ptcAddress, service.getClient(), service.getKeyPair());
            if (!ptcAddress.equalsIgnoreCase(verifier.getPtcHubAddress())) {
                throw new IllegalStateException("MonitorVerifier does not reference the new PTC Hub");
            }
            if (!verifierAddress.equalsIgnoreCase(ptcHub.getMonitorVerifier())) {
                throw new IllegalStateException("new PTC Hub does not reference MonitorVerifier");
            }
            System.out.println("ptc.address=" + ptcAddress);
            System.out.println("ptc.monitorVerifier=" + verifierAddress);
            System.out.println("ptc.rootCert.sha256=" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(ptcHub.bcdnsCertMap("root"))));
            System.out.println("ptc.bindingsReady=true");
        } finally {
            if (started) {
                service.shutdown();
            }
        }
    }

    static String normaliseAddress(String value) {
        String raw = value.startsWith("0x") || value.startsWith("0X") ? value.substring(2) : value;
        if (!raw.matches("[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException("address must be a 20-byte hexadecimal value");
        }
        return "0x" + raw.toLowerCase();
    }

    private static byte[] parseIdentity(String value) {
        String raw = value.startsWith("0x") || value.startsWith("0X") ? value.substring(2) : value;
        if (!raw.matches("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("identity must be a 20-byte address or 32-byte identity");
        }
        byte[] parsed = HexFormat.of().parseHex(raw);
        if (parsed.length == 32) {
            return parsed;
        }
        byte[] result = new byte[32];
        System.arraycopy(parsed, 0, result, 12, parsed.length);
        return result;
    }

    private static void requireArgCount(String[] args, int expected) {
        if (args.length != expected) {
            throw usage();
        }
    }

    private static IllegalArgumentException usage() {
        return new IllegalArgumentException(
                "usage: deploy-app CONFIG SDP MONITOR | "
                        + "send-unordered CONFIG APP TARGET_DOMAIN TARGET_IDENTITY PAYLOAD | "
                        + "inspect-app CONFIG APP | "
                        + "inspect-rejection CONFIG MONITOR APP TX_HASH EXPECTED_SOURCE_DOMAIN "
                        + "EXPECTED_AUTHOR EXPECTED_RECEIVER EXPECTED_BASELINE | "
                        + "inspect-rollback CONFIG MONITOR TX_HASH | chain-info CONFIG AM | "
                        + "inspect-return CONFIG MONITOR APP TX_HASH EXPECTED_DOMAIN "
                        + "EXPECTED_AUTHOR EXPECTED_PAYLOAD | "
                        + "inspect-success CONFIG MONITOR APP TX_HASH EXPECTED_DOMAIN "
                        + "EXPECTED_AUTHOR EXPECTED_PAYLOAD | "
                        + "inspect-ptc CONFIG PTC | inspect-cert CERT_PEM | "
                        + "deploy-ptc CONFIG ROOT_CERT AM SDP MONITOR");
    }

    private static void requireSuccessful(TransactionReceipt receipt, String operation) {
        if (receipt == null || !receipt.isStatusOK()) {
            throw new IllegalStateException(operation + " failed: "
                    + (receipt == null ? "no receipt" : receipt.getStatus() + " " + receipt.getMessage()));
        }
    }

    private static void withService(Path configPath, ServiceAction action) throws Exception {
        DefaultBBCContext context = new DefaultBBCContext();
        context.setConfForBlockchainClient(Files.readAllBytes(configPath));

        FISCOBCOSBBCService service = new FISCOBCOSBBCService();
        boolean started = false;
        try {
            service.startup(context);
            started = true;
            action.run(service);
        } finally {
            if (started) {
                service.shutdown();
            }
        }
    }

    @FunctionalInterface
    private interface ServiceAction {
        void run(FISCOBCOSBBCService service) throws Exception;
    }
}
