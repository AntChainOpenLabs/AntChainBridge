/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Root-only live scanner check. Blockchain credentials are supplied through a
 * server-side configuration path and are never printed.
 */
package tools.regulatory;

import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.AuthMessageContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.plugins.fiscobcos3.FISCOBCOSBBCService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class FiscoMessageScanInspect {

    private FiscoMessageScanInspect() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("expected CONFIG_JSON AM_ADDRESS HEIGHT [HEIGHT ...]");
        }

        DefaultBBCContext context = new DefaultBBCContext();
        context.setConfForBlockchainClient(Files.readAllBytes(Path.of(args[0])));
        context.setAuthMessageContract(
                new AuthMessageContract(args[1], ContractStatusEnum.CONTRACT_READY));

        FISCOBCOSBBCService service = new FISCOBCOSBBCService();
        try {
            service.startup(context);
            for (int i = 2; i < args.length; i++) {
                long height = Long.parseLong(args[i]);
                List<CrossChainMessage> messages = service.readCrossChainMessagesByHeight(height);
                System.out.println(height + ".messageCount=" + messages.size());
                for (int j = 0; j < messages.size(); j++) {
                    CrossChainMessage message = messages.get(j);
                    System.out.println(height + ".message[" + j + "].payloadBytes="
                            + message.getMessage().length);
                    System.out.println(height + ".message[" + j + "].txHashBytes="
                            + message.getProvableData().getTxHash().length);
                }
            }
        } finally {
            service.shutdown();
        }
    }
}
