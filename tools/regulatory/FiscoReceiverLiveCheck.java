/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Root-only receiver inspection helper. Configuration and account material are
 * supplied by path at runtime and are never printed or committed.
 */
package tools.regulatory;

import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.plugins.fiscobcos3.FISCOBCOSBBCService;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.AppContract;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bouncycastle.util.encoders.Hex;

/** Reads the exact byte payload held by a deployed FISCO demo receiver. */
public final class FiscoReceiverLiveCheck {

    private FiscoReceiverLiveCheck() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("expected CONFIG_JSON APP_ADDRESS");
        }

        DefaultBBCContext context = new DefaultBBCContext();
        context.setConfForBlockchainClient(Files.readAllBytes(Path.of(args[0])));

        FISCOBCOSBBCService service = new FISCOBCOSBBCService();
        boolean started = false;
        try {
            service.startup(context);
            started = true;

            AppContract app = AppContract.load(args[1], service.getClient(), service.getKeyPair());
            byte[] output = app.getLastUnorderedMsg();
            System.out.println("receiver.contract=" + app.getContractAddress());
            System.out.println("receiver.lastUnordered.utf8="
                    + new String(output, StandardCharsets.UTF_8));
            System.out.println("receiver.lastUnordered.hex=" + Hex.toHexString(output));
        } finally {
            if (started) {
                service.shutdown();
            }
        }
    }
}
