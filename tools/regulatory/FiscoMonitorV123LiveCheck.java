/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Root-only live validation helper for a deployed FISCO-BCOS BBC instance.
 * The blockchain configuration (including TLS/account material) is supplied by
 * path at runtime and must never be committed to source control.
 */
package tools.regulatory;

import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.plugins.fiscobcos3.FISCOBCOSBBCService;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.AppContract;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.Monitor;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;

/**
 * Deploys a disposable business contract and emits SDP V1/V2/V3 messages.
 *
 * <p>Usage:
 * <pre>
 * java ... tools.regulatory.FiscoMonitorV123LiveCheck \
 *   CONFIG_JSON SDP_ADDRESS MONITOR_ADDRESS TARGET_DOMAIN TARGET_APP_ADDRESS \
 *   CONTROL LABEL [EXISTING_APP_ADDRESS] [--control-only]
 * </pre>
 * CONTROL is the protocol value: 1 for normal forwarding (CLOSE), 2 for
 * monitored forwarding (OPEN), or "keep" to preserve the existing state.
 */
public final class FiscoMonitorV123LiveCheck {

    private FiscoMonitorV123LiveCheck() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 7 || args.length > 9) {
            throw new IllegalArgumentException(
                    "expected CONFIG_JSON SDP MONITOR TARGET_DOMAIN TARGET_APP CONTROL LABEL "
                            + "[EXISTING_APP] [--control-only]");
        }

        Path configPath = Path.of(args[0]);
        String sdpAddress = requireAddress(args[1], "SDP");
        String monitorAddress = requireAddress(args[2], "Monitor");
        String targetDomain = args[3];
        byte[] targetApp = addressToCrossChainId(requireAddress(args[4], "target app"));
        Integer control = "keep".equalsIgnoreCase(args[5]) ? null : Integer.valueOf(args[5]);
        if (control != null && control != 1 && control != 2) {
            throw new IllegalArgumentException("CONTROL must be 1 (normal), 2 (monitored), or keep");
        }
        String label = args[6];
        String existingAppAddress = args.length == 8 ? requireAddress(args[7], "existing app") : null;
        if (args.length == 9) {
            existingAppAddress = requireAddress(args[7], "existing app");
            if (!"--control-only".equals(args[8])) {
                throw new IllegalArgumentException("the ninth argument must be --control-only");
            }
        }
        boolean controlOnly = args.length == 9;

        DefaultBBCContext context = new DefaultBBCContext();
        context.setConfForBlockchainClient(Files.readAllBytes(configPath));

        FISCOBCOSBBCService service = new FISCOBCOSBBCService();
        boolean started = false;
        try {
            service.startup(context);
            started = true;

            Monitor monitor = Monitor.load(monitorAddress, service.getClient(), service.getKeyPair());
            System.out.println("callerAddress=" + service.getKeyPair().getAddress());
            System.out.println("monitorOwner=" + monitor.owner());
            System.out.println("monitorControl.before=" + monitor.getMonitorControl());
            if (control != null) {
                TransactionReceipt controlReceipt = monitor.setMonitorControl(BigInteger.valueOf(control));
                emit("monitorControl", controlReceipt);
                requireSuccessful(controlReceipt, "set monitor control");
                Thread.sleep(500L);
            }

            AppContract app;
            if (existingAppAddress == null) {
                app = AppContract.deploy(service.getClient(), service.getKeyPair());
                requireSuccessful(app.setProtocol(sdpAddress), "set app SDP");
                requireSuccessful(app.setMonitorContract(monitorAddress), "set app Monitor");
            } else {
                app = AppContract.load(existingAppAddress, service.getClient(), service.getKeyPair());
                if (!sdpAddress.equalsIgnoreCase(app.sdpAddress())) {
                    throw new IllegalStateException("existing app SDP address does not match");
                }
                if (!monitorAddress.equalsIgnoreCase(app.monitorAddress())) {
                    requireSuccessful(
                            app.setMonitorContract(monitorAddress),
                            "rebind existing app Monitor");
                    if (!monitorAddress.equalsIgnoreCase(app.monitorAddress())) {
                        throw new IllegalStateException("existing app Monitor rebind did not persist");
                    }
                }
            }

            System.out.println("appAddress=" + app.getContractAddress());
            System.out.println("monitorControl.after=" + monitor.getMonitorControl());
            System.out.println("sdpAddress=" + app.sdpAddress());
            System.out.println("monitorAddress=" + app.monitorAddress());
            if (controlOnly) {
                return;
            }

            String run = label + "-" + Instant.now().getEpochSecond();
            emit("v1", app.sendUnorderedMessage(
                    targetDomain,
                    targetApp,
                    (run + "-sdp-v1").getBytes(StandardCharsets.UTF_8)));
            emit("v2", app.sendUnorderedV2(
                    targetApp,
                    targetDomain,
                    false,
                    (run + "-sdp-v2").getBytes(StandardCharsets.UTF_8)));
            emit("v3", app.sendUnorderedV3(
                    targetApp,
                    targetDomain,
                    false,
                    (run + "-sdp-v3").getBytes(StandardCharsets.UTF_8),
                    BigInteger.ZERO,
                    BigInteger.ZERO));
        } finally {
            if (started) {
                service.shutdown();
            }
        }
    }

    private static void emit(String version, TransactionReceipt receipt) {
        if (receipt == null) {
            throw new IllegalStateException("send " + version + " failed: no receipt");
        }
        System.out.println(version + ".txHash=" + receipt.getTransactionHash());
        System.out.println(version + ".blockNumber=" + receipt.getBlockNumber());
        System.out.println(version + ".status=" + receipt.getStatus());
        System.out.println(version + ".message=" + receipt.getMessage());
        System.out.println(version + ".output=" + receipt.getOutput());
        requireSuccessful(receipt, "send " + version);
    }

    private static void requireSuccessful(TransactionReceipt receipt, String operation) {
        if (receipt == null || !receipt.isStatusOK()) {
            throw new IllegalStateException(
                    operation + " failed: " + (receipt == null ? "no receipt" : receipt.getStatus()));
        }
    }

    private static String requireAddress(String value, String label) {
        String normalized = value.startsWith("0x") ? value.substring(2) : value;
        if (!normalized.matches("[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException(label + " must be a 20-byte hex address");
        }
        return "0x" + normalized.toLowerCase();
    }

    private static byte[] addressToCrossChainId(String address) {
        String raw = address.substring(2);
        byte[] result = new byte[32];
        for (int i = 0; i < 20; i++) {
            result[12 + i] = (byte) Integer.parseInt(raw.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
