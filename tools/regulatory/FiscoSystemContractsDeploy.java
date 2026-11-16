/* SPDX-License-Identifier: Apache-2.0 */
package tools.regulatory;

import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.plugins.fiscobcos3.FISCOBCOSBBCService;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.Monitor;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.SDPMsg;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Deploys and wires a complete FISCO BBC contract set using an explicit,
 * persistent account configured in the supplied root-only JSON file.
 */
public final class FiscoSystemContractsDeploy {

    private FiscoSystemContractsDeploy() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("expected CONFIG_JSON LOCAL_DOMAIN");
        }

        DefaultBBCContext context = new DefaultBBCContext();
        context.setConfForBlockchainClient(Files.readAllBytes(Path.of(args[0])));

        FISCOBCOSBBCService service = new FISCOBCOSBBCService();
        try {
            service.startup(context);
            System.out.println("owner=" + service.getKeyPair().getAddress());

            service.setupAuthMessageContract();
            service.setupSDPMessageContract();
            service.setupMonitorContract();
            service.setupPTCContract();

            String am = context.getAuthMessageContract().getContractAddress();
            String sdp = context.getSdpContract().getContractAddress();
            String monitor = context.getMonitorContract().getContractAddress();
            String ptc = context.getPtcContract().getContractAddress();

            service.setProtocol(sdp, "0");
            service.setPtcContract(ptc);
            service.setAmContract(am);
            service.setLocalDomain(args[1]);
            service.setMonitorContract(monitor);
            service.setMonitorControl(2);
            service.setProtocolInMonitor(sdp);
            service.setPtcHubInMonitorVerifier(ptc);

            SDPMsg sdpContract = SDPMsg.load(sdp, service.getClient(), service.getKeyPair());
            Monitor monitorContract = Monitor.load(
                    monitor,
                    service.getClient(),
                    service.getKeyPair());

            System.out.println("am=" + am);
            System.out.println("sdp=" + sdp);
            System.out.println("monitor=" + monitor);
            System.out.println("ptc=" + ptc);
            System.out.println("sdp.routingVersion=" + sdpContract.getMonitorRoutingVersion());
            System.out.println("monitor.implementationVersion=" + monitorContract.getImplementationVersion());
            System.out.println("monitor.owner=" + monitorContract.owner());
            System.out.println("monitor.control=" + monitorContract.getMonitorControl());
            System.out.println("monitor.protocol=" + monitorContract.getProtocol());
        } finally {
            service.shutdown();
        }
    }
}
