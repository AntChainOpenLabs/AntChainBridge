/* SPDX-License-Identifier: Apache-2.0 */
package tools.regulatory;

import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Config;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.crypto.hash.Hash;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.errorcode.ErrorCode;
import com.alipay.mychain.sdk.vm.EVMOutput;
import com.alipay.mychain.sdk.vm.EVMParameter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.helpers.NOPLogger;

/** Root-only live check for Mychain Monitor V6 and SDP V1/V2/V3 routing. */
public final class MychainMonitorV123LiveCheck {

    private static final String SENDER_BINARY =
            "/contracts/monitor_demo/MonitorV123SenderContract.bin";

    private MychainMonitorV123LiveCheck() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 7 || args.length > 8) {
            throw new IllegalArgumentException(
                    "expected CONFIG SDP MONITOR TARGET_DOMAIN TARGET_ID CONTROL LABEL [SENDER]");
        }
        Path configPath = Path.of(args[0]);
        String sdpContract = args[1];
        String monitorContract = args[2];
        String targetDomain = args[3];
        Identity target = new Identity(new Hash(Hex.decode(normalizeIdentity(args[4]))));
        int control = Integer.parseInt(args[5]);
        if (control != 1 && control != 2) {
            throw new IllegalArgumentException("CONTROL must be 1 (normal) or 2 (monitored)");
        }

        Mychain020Config config = Mychain020Config.fromJsonString(
                Files.readString(configPath, StandardCharsets.UTF_8));
        Mychain020Client client = new Mychain020Client(
                config.toJsonString().getBytes(StandardCharsets.UTF_8),
                NOPLogger.NOP_LOGGER);
        try {
            require(client.startup(), "startup mychain sdk failed");
            System.out.println("monitor.implementationVersion="
                    + localUint(client, monitorContract, "getImplementationVersion()"));
            System.out.println("sdp.monitorRoutingVersion="
                    + localUint(client, sdpContract, "getMonitorRoutingVersion()"));

            EVMParameter setControl = new EVMParameter("setMonitorControl(uint32)");
            setControl.addUint(BigInteger.valueOf(control));
            requireSuccess(client.callContract(monitorContract, setControl, true), "set monitor control");
            System.out.println("monitor.control="
                    + localUint(client, monitorContract, "getMonitorControl()"));

            String sender = args.length == 8
                    ? args[7]
                    : "MonitorV123Sender_" + UUID.randomUUID();
            if (args.length != 8) {
                require(
                        client.deployContract(
                                SENDER_BINARY, sender, VMTypeEnum.EVM, new EVMParameter()),
                        "deploy sender failed");
                EVMParameter bind = new EVMParameter("setContracts(identity,identity)");
                bind.addIdentity(Utils.getIdentityByName(
                        sdpContract, client.getConfig().getMychainHashType()));
                bind.addIdentity(Utils.getIdentityByName(
                        monitorContract, client.getConfig().getMychainHashType()));
                requireSuccess(client.callContract(sender, bind, true), "bind sender SDP");
            }

            Identity senderIdentity = Utils.getIdentityByName(
                    sender, client.getConfig().getMychainHashType());
            System.out.println("sender.contract=" + sender);
            System.out.println("sender.identity=" + senderIdentity.hexStrValue());

            String run = args[6] + "-" + Instant.now().getEpochSecond();
            EVMParameter v1 = new EVMParameter("sendUnordered(identity,string,bytes)");
            v1.addIdentity(target);
            v1.addString(targetDomain);
            v1.addBytes((run + "-sdp-v1").getBytes(StandardCharsets.UTF_8));
            emit("v1", client.callContract(sender, v1, true));

            EVMParameter v2 = new EVMParameter("sendUnorderedV2(identity,string,bool,bytes)");
            v2.addIdentity(target);
            v2.addString(targetDomain);
            v2.addBool(false);
            v2.addBytes((run + "-sdp-v2").getBytes(StandardCharsets.UTF_8));
            emit("v2", client.callContract(sender, v2, true));

            EVMParameter v3 = new EVMParameter(
                    "sendUnorderedV3(identity,string,bool,bytes,uint8,uint256)");
            v3.addIdentity(target);
            v3.addString(targetDomain);
            v3.addBool(false);
            v3.addBytes((run + "-sdp-v3").getBytes(StandardCharsets.UTF_8));
            v3.addUint(BigInteger.ZERO);
            v3.addUint(BigInteger.ZERO);
            emit("v3", client.callContract(sender, v3, true));
        } finally {
            client.shutdown();
        }
    }

    private static BigInteger localUint(
            Mychain020Client client, String contract, String signature) {
        TransactionReceipt receipt = client.localCallContract(contract, new EVMParameter(signature));
        require(receipt != null, "empty local call receipt for " + signature);
        require(receipt.getResult() == ErrorCode.SUCCESS.getErrorCode(),
                "local call failed for " + signature + ": " + receipt.getResult());
        return new EVMOutput(Hex.toHexString(receipt.getOutput())).getUint();
    }

    private static void emit(String version, SendResponseResult result) {
        requireSuccess(result, "send " + version);
        System.out.println(version + ".txHash=" + result.getTxId());
        System.out.println(version + ".confirmed=" + result.isConfirmed());
        System.out.println(version + ".success=" + result.isSuccess());
    }

    private static void requireSuccess(SendResponseResult result, String operation) {
        require(result != null, operation + " returned no response");
        require(result.isSuccess(), operation + " failed: tx=" + result.getTxId()
                + ", code=" + result.getErrorCode()
                + ", message=" + result.getErrorMessage());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static String normalizeIdentity(String value) {
        String normalized = value.startsWith("0x") ? value.substring(2) : value;
        if (!normalized.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("TARGET_ID must be a 32-byte hex identity");
        }
        return normalized;
    }
}
