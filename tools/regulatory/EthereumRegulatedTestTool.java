/* SPDX-License-Identifier: Apache-2.0 */
package tools.regulatory;

import com.alipay.antchain.bridge.plugins.ethereum3.abi.AppContract;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.AuthMsg;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.Monitor;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.MonitorVerifier;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.PtcHub;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.SDPMsg;
import com.alipay.antchain.bridge.plugins.ethereum3.conf.EthereumConfig;
import com.alipay.antchain.bridge.plugins.ethereum3.core.AcbEthClient;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.AcbGasProvider;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.StaticGasLimitProvider;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.helpers.NOPLogger;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.ContractGasProvider;

/** Operational helper for the eth07 regulated cross-chain test. */
public final class EthereumRegulatedTestTool {

    private static final BigInteger TX_GAS_LIMIT = BigInteger.valueOf(10_000_000L);

    private EthereumRegulatedTestTool() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw usage();
        }
        switch (args[0]) {
            case "chain-info":
                requireArgCount(args, 2);
                withClient(Path.of(args[1]), EthereumRegulatedTestTool::printChainInfo);
                return;
            case "inspect-system":
                requireArgCount(args, 7);
                withClient(Path.of(args[1]), client -> inspectSystem(
                        client, args[2], args[3], args[4], args[5], args[6]));
                return;
            case "deploy-app":
                requireArgCount(args, 4);
                withClient(Path.of(args[1]), client -> deployApp(client, args[2], args[3]));
                return;
            case "inspect-app":
                requireArgCount(args, 3);
                withClient(Path.of(args[1]), client -> inspectApp(client, args[2]));
                return;
            case "send-unordered":
                requireArgCount(args, 6);
                withClient(Path.of(args[1]), client -> sendUnordered(
                        client, args[2], args[3], args[4], args[5]));
                return;
            case "inspect-return":
                requireArgCount(args, 9);
                withClient(Path.of(args[1]), client -> inspectReturn(
                        client,
                        args[2],
                        args[3],
                        args[4],
                        args[5],
                        args[6],
                        args[7],
                        args[8]));
                return;
            default:
                throw usage();
        }
    }

    private static void printChainInfo(AcbEthClient client) throws Exception {
        System.out.println("chain.id=" + client.getWeb3j().ethChainId().send().getChainId());
        System.out.println("chain.block=" + client.queryLatestBlockNumber());
        System.out.println("chain.finalizedSlot=" + client.queryLatestSlot());
        System.out.println("chain.account=" + client.getCredentials().getAddress());
    }

    private static void inspectSystem(
            AcbEthClient client,
            String amValue,
            String sdpValue,
            String ptcValue,
            String monitorValue,
            String verifierValue) throws Exception {
        String amAddress = normaliseAddress(amValue);
        String sdpAddress = normaliseAddress(sdpValue);
        String ptcAddress = normaliseAddress(ptcValue);
        String monitorAddress = normaliseAddress(monitorValue);
        String verifierAddress = normaliseAddress(verifierValue);
        ContractGasProvider gas = gasProvider(client);
        AuthMsg am = AuthMsg.load(amAddress, client.getWeb3j(), client.getRawTransactionManager(), gas);
        SDPMsg sdp = SDPMsg.load(sdpAddress, client.getWeb3j(), client.getRawTransactionManager(), gas);
        Monitor monitor = Monitor.load(
                monitorAddress, client.getWeb3j(), client.getRawTransactionManager(), gas);
        MonitorVerifier verifier = MonitorVerifier.load(
                verifierAddress, client.getWeb3j(), client.getRawTransactionManager(), gas);
        PtcHub ptc = PtcHub.load(ptcAddress, client.getWeb3j(), client.getRawTransactionManager(), gas);

        requireAddressEquals(sdpAddress, am.getProtocol(BigInteger.ZERO).send(), "AM -> SDP");
        requireAddressEquals(amAddress, sdp.getAmAddress().send(), "SDP -> AM");
        requireAddressEquals(monitorAddress, sdp.getMonitorAddress().send(), "SDP -> Monitor");
        requireAddressEquals(sdpAddress, monitor.getProtocol().send(), "Monitor -> SDP");
        requireAddressEquals(verifierAddress, monitor.getMonitorVerifier().send(), "Monitor -> verifier");
        requireAddressEquals(ptcAddress, verifier.getPtcHubAddress().send(), "verifier -> PtcHub");
        requireAddressEquals(verifierAddress, ptc.getMonitorVerifier().send(), "PtcHub -> verifier");

        System.out.println("system.am=" + amAddress);
        System.out.println("system.sdp=" + sdpAddress);
        System.out.println("system.ptc=" + ptcAddress);
        System.out.println("system.monitor=" + monitorAddress);
        System.out.println("system.monitorVerifier=" + verifierAddress);
        System.out.println("system.sdp.monitorRoutingVersion=" + sdp.getMonitorRoutingVersion().send());
        System.out.println("system.monitor.implementationVersion=" + monitor.getImplementationVersion().send());
        System.out.println("system.monitor.control=" + monitor.getMonitorControl().send());
        System.out.println("system.bindingsReady=true");
    }

    private static void deployApp(AcbEthClient client, String sdpValue, String monitorValue)
            throws Exception {
        String sdpAddress = normaliseAddress(sdpValue);
        String monitorAddress = normaliseAddress(monitorValue);
        ContractGasProvider gas = gasProvider(client);
        AppContract app = AppContract.deploy(
                client.getWeb3j(), client.getRawTransactionManager(), gas).send();
        requireSuccessful(app.getTransactionReceipt().orElse(null), "deploy AppContract");
        TransactionReceipt sdpReceipt = app.setProtocol(sdpAddress).send();
        requireSuccessful(sdpReceipt, "bind AppContract SDP");
        TransactionReceipt monitorReceipt = app.setMonitorContract(monitorAddress).send();
        requireSuccessful(monitorReceipt, "bind AppContract Monitor");
        requireAddressEquals(sdpAddress, app.sdpAddress().send(), "AppContract -> SDP");
        requireAddressEquals(monitorAddress, app.monitorAddress().send(), "AppContract -> Monitor");
        System.out.println("app.address=" + app.getContractAddress());
        System.out.println("app.identity=" + identityHex(app.getContractAddress()));
        System.out.println("app.deploy.txHash=" + app.getTransactionReceipt().orElseThrow().getTransactionHash());
        System.out.println("app.setProtocol.txHash=" + sdpReceipt.getTransactionHash());
        System.out.println("app.setMonitor.txHash=" + monitorReceipt.getTransactionHash());
        System.out.println("app.sdp=" + app.sdpAddress().send());
        System.out.println("app.monitor=" + app.monitorAddress().send());
    }

    private static void inspectApp(AcbEthClient client, String appValue) throws Exception {
        String appAddress = normaliseAddress(appValue);
        AppContract app = AppContract.load(
                appAddress, client.getWeb3j(), client.getRawTransactionManager(), gasProvider(client));
        byte[] payload = app.getLastUnorderedMsg().send();
        System.out.println("app.address=" + appAddress);
        System.out.println("app.identity=" + identityHex(appAddress));
        System.out.println("app.sdp=" + app.sdpAddress().send());
        System.out.println("app.monitor=" + app.monitorAddress().send());
        System.out.println("app.lastUnordered.hex=" + HexFormat.of().formatHex(payload));
        System.out.println("app.lastUnordered.utf8=" + new String(payload, StandardCharsets.UTF_8));
    }

    private static void sendUnordered(
            AcbEthClient client,
            String appValue,
            String targetDomain,
            String targetIdentity,
            String payloadValue) throws Exception {
        String appAddress = normaliseAddress(appValue);
        byte[] receiver = parseIdentity(targetIdentity);
        byte[] payload = payloadValue.getBytes(StandardCharsets.UTF_8);
        AppContract app = AppContract.load(
                appAddress, client.getWeb3j(), client.getRawTransactionManager(), gasProvider(client));
        TransactionReceipt receipt = app.sendUnorderedMessage(targetDomain, receiver, payload).send();
        requireSuccessful(receipt, "send unordered monitored message");
        List<AppContract.SendCrosschainMsgEventResponse> events =
                AppContract.getSendCrosschainMsgEvents(receipt);
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
        System.out.println("send.txHash=" + receipt.getTransactionHash());
        System.out.println("send.blockNumber=" + receipt.getBlockNumber());
        System.out.println("send.app=" + appAddress);
        System.out.println("send.author=" + identityHex(appAddress));
        System.out.println("send.receiverDomain=" + event.receiverDomain);
        System.out.println("send.receiver=" + HexFormat.of().formatHex(event.receiver));
        System.out.println("send.unordered=" + !event.isOrdered);
        System.out.println("send.payload.hex=" + HexFormat.of().formatHex(event.message));
        System.out.println("send.payload.utf8=" + new String(event.message, StandardCharsets.UTF_8));
    }

    private static void inspectReturn(
            AcbEthClient client,
            String monitorValue,
            String appValue,
            String txHash,
            String expectedDomain,
            String expectedAuthorValue,
            String expectedReceiverValue,
            String expectedPayloadValue) throws Exception {
        String monitorAddress = normaliseAddress(monitorValue);
        String appAddress = normaliseAddress(appValue);
        byte[] expectedAuthor = parseIdentity(expectedAuthorValue);
        byte[] expectedReceiver = parseIdentity(expectedReceiverValue);
        byte[] appIdentity = parseIdentity(appAddress);
        byte[] expectedPayload = expectedPayloadValue.getBytes(StandardCharsets.UTF_8);
        if (!Arrays.equals(expectedReceiver, appIdentity)) {
            throw new IllegalArgumentException("expected receiver does not match AppContract identity");
        }

        TransactionReceipt receipt = client.getWeb3j()
                .ethGetTransactionReceipt(txHash)
                .send()
                .getTransactionReceipt()
                .orElseThrow(() -> new IllegalStateException("transaction receipt not found: " + txHash));
        requireSuccessful(receipt, "rollback return transaction");

        AppContract app = AppContract.load(
                appAddress, client.getWeb3j(), client.getRawTransactionManager(), gasProvider(client));
        List<Monitor.ReceiveMonitorRollbackMessageEventResponse> rollbackEvents =
                Monitor.getReceiveMonitorRollbackMessageEvents(receipt);
        List<AppContract.RecvCrosschainMsgEventResponse> receiveEvents =
                AppContract.getRecvCrosschainMsgEvents(receipt);
        if (rollbackEvents.size() != 1) {
            throw new IllegalStateException("expected exactly one rollback receive event");
        }
        if (receiveEvents.size() != 1) {
            throw new IllegalStateException("expected exactly one app receive event");
        }

        Monitor.ReceiveMonitorRollbackMessageEventResponse rollback = rollbackEvents.get(0);
        AppContract.RecvCrosschainMsgEventResponse received = receiveEvents.get(0);
        if (!monitorAddress.equalsIgnoreCase(rollback.log.getAddress())
                || !BigInteger.valueOf(3).equals(rollback.monitorType)
                || !expectedDomain.equals(rollback.senderDomain)
                || !Arrays.equals(expectedAuthor, rollback.sender)
                || !Arrays.equals(expectedReceiver, rollback.receiver)
                || !"MidMonitoring not pass".equals(rollback.monitorMsg)) {
            throw new IllegalStateException("unexpected rollback receive event content");
        }
        if (!appAddress.equalsIgnoreCase(received.log.getAddress())
                || !expectedDomain.equals(received.senderDomain)
                || !Arrays.equals(expectedAuthor, received.author)
                || !Arrays.equals(expectedPayload, received.message)
                || Boolean.TRUE.equals(received.isOrdered)) {
            throw new IllegalStateException("unexpected app receive event content");
        }
        if (!Arrays.equals(expectedPayload, app.getLastUnorderedMsg().send())) {
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
    }

    private static ContractGasProvider gasProvider(AcbEthClient client) {
        return new AcbGasProvider(
                client.getContractGasPriceProvider(), new StaticGasLimitProvider(TX_GAS_LIMIT));
    }

    private static void withClient(Path configPath, ClientAction action) throws Exception {
        EthereumConfig config = EthereumConfig.fromJsonString(
                Files.readString(configPath, StandardCharsets.UTF_8));
        AcbEthClient client = new AcbEthClient(config, NOPLogger.NOP_LOGGER);
        try {
            action.run(client);
        } finally {
            client.shutdown();
        }
    }

    private static String normaliseAddress(String value) {
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
        byte[] identity = new byte[32];
        System.arraycopy(parsed, 0, identity, 12, parsed.length);
        return identity;
    }

    private static String identityHex(String address) {
        return HexFormat.of().formatHex(parseIdentity(address));
    }

    private static void requireAddressEquals(String expected, String actual, String label) {
        if (!normaliseAddress(expected).equalsIgnoreCase(normaliseAddress(actual))) {
            throw new IllegalStateException(label + " mismatch: " + actual);
        }
    }

    private static void requireSuccessful(TransactionReceipt receipt, String operation) {
        if (receipt == null || !receipt.isStatusOK()) {
            throw new IllegalStateException(operation + " failed: "
                    + (receipt == null ? "no receipt" : receipt.getStatus()));
        }
    }

    private static void requireArgCount(String[] args, int expected) {
        if (args.length != expected) {
            throw usage();
        }
    }

    private static IllegalArgumentException usage() {
        return new IllegalArgumentException(
                "usage: chain-info CONFIG | inspect-system CONFIG AM SDP PTC MONITOR VERIFIER | "
                        + "deploy-app CONFIG SDP MONITOR | inspect-app CONFIG APP | "
                        + "send-unordered CONFIG APP TARGET_DOMAIN TARGET_IDENTITY PAYLOAD | "
                        + "inspect-return CONFIG MONITOR APP TX_HASH EXPECTED_DOMAIN "
                        + "EXPECTED_AUTHOR EXPECTED_RECEIVER EXPECTED_PAYLOAD");
    }

    @FunctionalInterface
    private interface ClientAction {
        void run(AcbEthClient client) throws Exception;
    }
}
