/* SPDX-License-Identifier: Apache-2.0 */
package tools.regulatory;

import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Config;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.errorcode.ErrorCode;
import com.alipay.mychain.sdk.vm.EVMOutput;
import com.alipay.mychain.sdk.vm.EVMParameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.helpers.NOPLogger;

/** Deploys or inspects the Mychain demo receiver used by cross-chain V1/V2/V3 checks. */
public final class MychainReceiverLiveCheck {

    private static final String RECEIVER_BINARY = "/contracts/demo_v2/receiver.bin";

    private MychainReceiverLiveCheck() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("expected CONFIG [RECEIVER]");
        }
        Mychain020Config config = Mychain020Config.fromJsonString(
                Files.readString(Path.of(args[0]), StandardCharsets.UTF_8));
        Mychain020Client client = new Mychain020Client(
                config.toJsonString().getBytes(StandardCharsets.UTF_8),
                NOPLogger.NOP_LOGGER);
        try {
            require(client.startup(), "startup mychain sdk failed");
            String receiver = args.length == 2
                    ? args[1]
                    : "MonitorV123Receiver_" + UUID.randomUUID();
            if (args.length != 2) {
                require(client.deployContract(
                        RECEIVER_BINARY, receiver, VMTypeEnum.EVM, new EVMParameter()),
                        "deploy receiver failed");
            }
            System.out.println("receiver.contract=" + receiver);
            System.out.println("receiver.identity=" + Utils.getIdentityByName(
                    receiver, client.getConfig().getMychainHashType()).hexStrValue());
            TransactionReceipt receipt = client.localCallContract(
                    receiver, new EVMParameter("getLastUnorderedMsg()"));
            require(receipt != null && receipt.getResult() == ErrorCode.SUCCESS.getErrorCode(),
                    "query receiver failed");
            byte[] output = new EVMOutput(Hex.toHexString(receipt.getOutput())).getBytes();
            System.out.println("receiver.lastUnordered.utf8="
                    + new String(output, StandardCharsets.UTF_8));
            System.out.println("receiver.lastUnordered.hex=" + Hex.toHexString(output));
        } finally {
            client.shutdown();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
