/* SPDX-License-Identifier: Apache-2.0 */
package tools.regulatory;

import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.plugins.ethereum3.EthereumBBCService;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.AppContract;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.Monitor;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/** Root-only live check for Ethereum Monitor V5 and SDP V1/V2/V3. */
public final class EthereumMonitorV123LiveCheck {

    private EthereumMonitorV123LiveCheck() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 8 || args.length > 9) {
            throw new IllegalArgumentException(
                    "expected CONFIG APP SDP MONITOR TARGET_DOMAIN TARGET_APP CONTROL LABEL "
                            + "[all|v1|v2|v3]");
        }
        Path configPath = Path.of(args[0]);
        boolean deployApp = "deploy".equalsIgnoreCase(args[1]);
        String appAddress = deployApp ? null : requireAddress(args[1], "app");
        String sdpAddress = requireAddress(args[2], "SDP");
        String monitorAddress = requireAddress(args[3], "Monitor");
        String targetDomain = args[4];
        byte[] targetApp = parseCrossChainId(args[5], "target app");
        int control = Integer.parseInt(args[6]);
        if (control < 1 || control > 3) {
            throw new IllegalArgumentException("CONTROL must be 1, 2, or 3");
        }
        String selectedVersion = args.length == 9 ? args[8].toLowerCase() : "all";
        if (!selectedVersion.matches("all|v1|v2|v3")) {
            throw new IllegalArgumentException("protocol selection must be all, v1, v2, or v3");
        }

        DefaultBBCContext context = new DefaultBBCContext();
        context.setConfForBlockchainClient(Files.readAllBytes(configPath));
        EthereumBBCService service = new EthereumBBCService();
        boolean started = false;
        try {
            service.startup(context);
            started = true;
            Monitor monitor = Monitor.load(
                    monitorAddress,
                    service.getAcbEthClient().getWeb3j(),
                    service.getAcbEthClient().getRawTransactionManager(),
                    new DefaultGasProvider());
            AppContract app;
            if (deployApp) {
                app = AppContract.deploy(
                        service.getAcbEthClient().getWeb3j(),
                        service.getAcbEthClient().getRawTransactionManager(),
                        new DefaultGasProvider()).send();
                requireSuccessful(app.setProtocol(sdpAddress).send(), "bind new app SDP");
                requireSuccessful(app.setMonitorContract(monitorAddress).send(), "bind new app Monitor");
            } else {
                app = AppContract.load(
                        appAddress,
                        service.getAcbEthClient().getWeb3j(),
                        service.getAcbEthClient().getRawTransactionManager(),
                        new DefaultGasProvider());
            }

            System.out.println("monitor.implementationVersion=" + monitor.getImplementationVersion().send());
            System.out.println("monitor.control.before=" + monitor.getMonitorControl().send());
            requireSuccessful(monitor.setMonitorControl(BigInteger.valueOf(control)).send(), "set control");
            System.out.println("monitor.control.after=" + monitor.getMonitorControl().send());
            String appSdpAddress = app.sdpAddress().send();
            String appMonitorAddress = app.monitorAddress().send();
            System.out.println("app.address=" + app.getContractAddress());
            System.out.println("app.sdp=" + appSdpAddress);
            System.out.println("app.monitor=" + appMonitorAddress);
            if (!sdpAddress.equalsIgnoreCase(appSdpAddress)) {
                requireSuccessful(app.setProtocol(sdpAddress).send(), "bind app SDP");
                appSdpAddress = app.sdpAddress().send();
                System.out.println("app.sdp.after=" + appSdpAddress);
                if (!sdpAddress.equalsIgnoreCase(appSdpAddress)) {
                    throw new IllegalStateException("app SDP bind did not persist");
                }
            }
            if (!monitorAddress.equalsIgnoreCase(appMonitorAddress)) {
                throw new IllegalStateException("app Monitor address does not match");
            }

            String run = args[7] + "-" + Instant.now().getEpochSecond();
            if ("all".equals(selectedVersion) || "v1".equals(selectedVersion)) {
                emit("v1", app.sendUnorderedMessage(
                        targetDomain,
                        targetApp,
                        (run + "-sdp-v1").getBytes(StandardCharsets.UTF_8)).send());
            }
            if ("all".equals(selectedVersion) || "v2".equals(selectedVersion)) {
                emit("v2", app.sendUnorderedV2(
                        targetApp,
                        targetDomain,
                        false,
                        (run + "-sdp-v2").getBytes(StandardCharsets.UTF_8)).send());
            }
            if ("all".equals(selectedVersion) || "v3".equals(selectedVersion)) {
                emit("v3", app.sendUnorderedV3(
                        targetApp,
                        targetDomain,
                        false,
                        (run + "-sdp-v3").getBytes(StandardCharsets.UTF_8),
                        BigInteger.ZERO,
                        BigInteger.ZERO).send());
            }
        } finally {
            if (started) {
                service.shutdown();
            }
        }
    }

    private static void emit(String version, TransactionReceipt receipt) {
        requireSuccessful(receipt, "send " + version);
        System.out.println(version + ".txHash=" + receipt.getTransactionHash());
        System.out.println(version + ".blockNumber=" + receipt.getBlockNumber());
        System.out.println(version + ".status=" + receipt.getStatus());
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
        byte[] result = new byte[32];
        String raw = address.substring(2);
        for (int i = 0; i < 20; i++) {
            result[12 + i] = (byte) Integer.parseInt(raw.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static byte[] parseCrossChainId(String value, String label) {
        String normalized = value.startsWith("0x") ? value.substring(2) : value;
        if (normalized.matches("[0-9a-fA-F]{40}")) {
            return addressToCrossChainId("0x" + normalized.toLowerCase());
        }
        if (!normalized.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(label + " must be a 20-byte address or 32-byte identity");
        }
        byte[] result = new byte[32];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
