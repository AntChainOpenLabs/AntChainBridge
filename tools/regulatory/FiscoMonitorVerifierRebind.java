/* SPDX-License-Identifier: Apache-2.0 */
package tools.regulatory;

import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.plugins.fiscobcos3.FISCOBCOSBBCService;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.Monitor;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.MonitorVerifier;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.PtcHub;
import java.nio.file.Files;
import java.nio.file.Path;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;

/** Rebinds a replacement Monitor to a previously populated MonitorVerifier. */
public final class FiscoMonitorVerifierRebind {

    private FiscoMonitorVerifierRebind() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("expected CONFIG_JSON MONITOR PTC_HUB MONITOR_VERIFIER");
        }
        DefaultBBCContext context = new DefaultBBCContext();
        context.setConfForBlockchainClient(Files.readAllBytes(Path.of(args[0])));
        FISCOBCOSBBCService service = new FISCOBCOSBBCService();
        boolean started = false;
        try {
            service.startup(context);
            started = true;
            Monitor monitor = Monitor.load(args[1], service.getClient(), service.getKeyPair());
            PtcHub ptcHub = PtcHub.load(args[2], service.getClient(), service.getKeyPair());
            MonitorVerifier verifier = MonitorVerifier.load(
                    args[3], service.getClient(), service.getKeyPair());
            if (!args[2].equalsIgnoreCase(verifier.getPtcHubAddress())) {
                throw new IllegalStateException("candidate MonitorVerifier is not bound to the requested PTC Hub");
            }
            System.out.println("monitor.verifier.before=" + monitor.getMonitorVerifier());
            System.out.println("ptc.monitorVerifier.before=" + ptcHub.getMonitorVerifier());
            requireSuccessful(monitor.setMonitorVerifier(args[3]), "rebind Monitor");
            requireSuccessful(ptcHub.setMonitorVerifier(args[3]), "rebind PTC Hub");
            System.out.println("monitor.verifier.after=" + monitor.getMonitorVerifier());
            System.out.println("ptc.monitorVerifier.after=" + ptcHub.getMonitorVerifier());
            System.out.println("verifier.ptcHub=" + verifier.getPtcHubAddress());
            if (!args[3].equalsIgnoreCase(monitor.getMonitorVerifier())
                    || !args[3].equalsIgnoreCase(ptcHub.getMonitorVerifier())) {
                throw new IllegalStateException("MonitorVerifier rebind did not persist");
            }
        } finally {
            if (started) {
                service.shutdown();
            }
        }
    }

    private static void requireSuccessful(TransactionReceipt receipt, String action) {
        if (receipt == null || !receipt.isStatusOK()) {
            throw new IllegalStateException(
                    action + " failed: " + (receipt == null ? "no receipt" : receipt.getStatus()));
        }
        System.out.println(action + ".txHash=" + receipt.getTransactionHash());
    }
}
