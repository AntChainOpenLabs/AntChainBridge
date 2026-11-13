/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Root-only receipt inspection helper. Configuration and account material are
 * supplied by path at runtime and are never printed or committed.
 */
package tools.regulatory;

import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.plugins.fiscobcos3.FISCOBCOSBBCService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;

public final class FiscoReceiptInspect {

    private FiscoReceiptInspect() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("expected CONFIG_JSON TX_HASH [TX_HASH ...]");
        }

        DefaultBBCContext context = new DefaultBBCContext();
        context.setConfForBlockchainClient(Files.readAllBytes(Path.of(args[0])));

        FISCOBCOSBBCService service = new FISCOBCOSBBCService();
        boolean started = false;
        try {
            service.startup(context);
            started = true;
            for (int i = 1; i < args.length; i++) {
                TransactionReceipt receipt = service.getClient()
                        .getTransactionReceipt(args[i], true)
                        .getTransactionReceipt();
                if (receipt == null) {
                    System.out.println(args[i] + ".receipt=missing");
                    continue;
                }
                System.out.println(args[i] + ".status=" + receipt.getStatus());
                System.out.println(args[i] + ".blockNumber=" + receipt.getBlockNumber());
                System.out.println(args[i] + ".message=" + receipt.getMessage());
                System.out.println(args[i] + ".output=" + receipt.getOutput());
                System.out.println(args[i] + ".logCount=" + receipt.getLogEntries().size());
                for (int j = 0; j < receipt.getLogEntries().size(); j++) {
                    TransactionReceipt.Logs log = receipt.getLogEntries().get(j);
                    System.out.println(args[i] + ".log[" + j + "].address=" + log.getAddress());
                    System.out.println(args[i] + ".log[" + j + "].topics=" + log.getTopics());
                }
            }
        } finally {
            if (started) {
                service.shutdown();
            }
        }
    }
}
