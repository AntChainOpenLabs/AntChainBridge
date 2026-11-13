/* SPDX-License-Identifier: Apache-2.0 */
package tools.regulatory;

import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.plugins.fiscobcos3.FISCOBCOSBBCService;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.Monitor;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.MonitorVerifier;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.PtcHub;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.AuthMsg;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.SDPMsg;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only live inspection of the FISCO PTC/MonitorVerifier bindings. */
public final class FiscoSystemBindingInspect {

    private FiscoSystemBindingInspect() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("expected CONFIG_JSON PTC_HUB MONITOR");
        }
        DefaultBBCContext context = new DefaultBBCContext();
        context.setConfForBlockchainClient(Files.readAllBytes(Path.of(args[0])));
        FISCOBCOSBBCService service = new FISCOBCOSBBCService();
        boolean started = false;
        try {
            service.startup(context);
            started = true;
            Monitor monitor = Monitor.load(args[2], service.getClient(), service.getKeyPair());
            String verifierAddress = monitor.getMonitorVerifier();
            PtcHub ptcHub = PtcHub.load(args[1], service.getClient(), service.getKeyPair());
            MonitorVerifier verifier = MonitorVerifier.load(
                    verifierAddress, service.getClient(), service.getKeyPair());
            System.out.println("monitor.version=" + monitor.getImplementationVersion());
            System.out.println("monitor.control=" + monitor.getMonitorControl());
            System.out.println("monitor.sdp=" + monitor.sdpAddress());
            System.out.println("monitor.verifier=" + verifierAddress);
            System.out.println("ptc.monitorVerifier=" + ptcHub.getMonitorVerifier());
            System.out.println("verifier.ptcHub=" + verifier.getPtcHubAddress());
            SDPMsg sdp = SDPMsg.load(monitor.sdpAddress(), service.getClient(), service.getKeyPair());
            AuthMsg am = AuthMsg.load(sdp.getAmAddress(), service.getClient(), service.getKeyPair());
            System.out.println("sdp.am=" + sdp.getAmAddress());
            System.out.println("sdp.monitor=" + sdp.getMonitorAddress());
            System.out.println("am.sdp=" + am.getProtocol(BigInteger.ZERO));
            System.out.println("am.ptc=" + am.ptcHubAddr());
            if (!args[2].equalsIgnoreCase(sdp.getMonitorAddress())
                    || !monitor.sdpAddress().equalsIgnoreCase(am.getProtocol(BigInteger.ZERO))
                    || !args[1].equalsIgnoreCase(am.ptcHubAddr())
                    || !verifierAddress.equalsIgnoreCase(ptcHub.getMonitorVerifier())
                    || !args[1].equalsIgnoreCase(verifier.getPtcHubAddress())
                    || !BigInteger.valueOf(2).equals(monitor.getMonitorControl())) {
                throw new IllegalStateException("FISCO system binding/control mismatch");
            }
        } finally {
            if (started) {
                service.shutdown();
            }
        }
    }
}
