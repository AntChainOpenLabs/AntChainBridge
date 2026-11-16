/* SPDX-License-Identifier: Apache-2.0 */
package tools.regulatory;

import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.plugins.ethereum3.EthereumBBCService;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.AppContract;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bouncycastle.util.encoders.Hex;
import org.web3j.tx.gas.DefaultGasProvider;

/** Reads the exact byte payload held by a deployed Ethereum demo receiver. */
public final class EthereumReceiverLiveCheck {

    private EthereumReceiverLiveCheck() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("expected CONFIG_JSON APP_ADDRESS");
        }

        DefaultBBCContext context = new DefaultBBCContext();
        context.setConfForBlockchainClient(Files.readAllBytes(Path.of(args[0])));

        EthereumBBCService service = new EthereumBBCService();
        boolean started = false;
        try {
            service.startup(context);
            started = true;
            AppContract app = AppContract.load(
                    args[1],
                    service.getAcbEthClient().getWeb3j(),
                    service.getAcbEthClient().getRawTransactionManager(),
                    new DefaultGasProvider());
            byte[] output = app.getLastUnorderedMsg().send();
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
