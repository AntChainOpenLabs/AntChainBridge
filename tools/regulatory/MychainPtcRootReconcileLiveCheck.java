/* SPDX-License-Identifier: Apache-2.0 */
package tools.regulatory;

import com.alipay.antchain.bridge.plugins.mychain020.contract.PtcContractEvm;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Config;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.mychain.sdk.crypto.hash.HashFactory;
import com.alipay.mychain.sdk.crypto.hash.HashTypeEnum;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.errorcode.ErrorCode;
import com.alipay.mychain.sdk.vm.EVMOutput;
import com.alipay.mychain.sdk.vm.EVMParameter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.helpers.NOPLogger;

/** Root-only live reconciliation check for an existing Mychain PTC Hub. */
public final class MychainPtcRootReconcileLiveCheck {

    private MychainPtcRootReconcileLiveCheck() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("expected CONFIG PTC_HUB");
        }
        Mychain020Config config = Mychain020Config.fromJsonString(
                Files.readString(Path.of(args[0]), StandardCharsets.UTF_8));
        Mychain020Client client = new Mychain020Client(
                config.toJsonString().getBytes(StandardCharsets.UTF_8),
                NOPLogger.NOP_LOGGER);
        try {
            require(client.startup(), "startup mychain sdk failed");
            PtcContractEvm ptc = new PtcContractEvm(client, NOPLogger.NOP_LOGGER);
            ptc.setContractAddress(args[1]);
            AbstractCrossChainCertificate rootCert =
                    CrossChainCertificateUtil.readCrossChainCertificateFromPem(
                            config.getBcdnsRootCertPem().getBytes(StandardCharsets.UTF_8));
            byte[] ownerKey = HashFactory.getHash(HashTypeEnum.Keccak).hash(
                    rootCert.getCredentialSubjectInstance().getApplicant().encode());
            System.out.println("ptc.configRootOwner="
                    + rootCert.getCredentialSubjectInstance().getApplicant().toHex());
            System.out.println("ptc.implementationVersion.before="
                    + queryVersion(client, args[1]));
            printRootState(client, args[1], rootCert.encode(), ownerKey, "before");
            require(ptc.reconcileRootBcdnsCert(config.getBcdnsRootCertPem()),
                    "PTC Hub root reconciliation failed");
            System.out.println("ptc.implementationVersion.after="
                    + queryVersion(client, args[1]));
            printRootState(client, args[1], rootCert.encode(), ownerKey, "after");
            System.out.println("ptc.rootReconciled=true");
        } finally {
            client.shutdown();
        }
    }

    private static void printRootState(
            Mychain020Client client,
            String contract,
            byte[] expectedRoot,
            byte[] ownerKey,
            String phase) {
        EVMParameter rootParameters = new EVMParameter("bcdnsCertMap(string)");
        rootParameters.addString("root");
        TransactionReceipt rootReceipt = client.localCallContract(contract, rootParameters);
        require(rootReceipt != null && rootReceipt.getResult() == ErrorCode.SUCCESS.getErrorCode(),
                "root certificate query failed");
        byte[] onChainRoot = new EVMOutput(Hex.toHexString(rootReceipt.getOutput())).getBytes();

        EVMParameter ownerParameters =
                new EVMParameter("ownerOidToBcdnsDomainSpaceMap(bytes32)");
        ownerParameters.addBytes32(ownerKey);
        TransactionReceipt ownerReceipt = client.localCallContract(contract, ownerParameters);
        require(ownerReceipt != null && ownerReceipt.getResult() == ErrorCode.SUCCESS.getErrorCode(),
                "root owner query failed");
        String alias = new EVMOutput(Hex.toHexString(ownerReceipt.getOutput())).getString();
        System.out.println("ptc.rootMatchesConfig." + phase + "="
                + java.util.Arrays.equals(expectedRoot, onChainRoot));
        System.out.println("ptc.rootOwnerAlias." + phase + "=" + alias);
    }

    private static BigInteger queryVersion(Mychain020Client client, String contract) {
        TransactionReceipt receipt = client.localCallContract(
                contract, new EVMParameter("getImplementationVersion()"));
        require(receipt != null, "empty implementation version receipt");
        require(receipt.getResult() == ErrorCode.SUCCESS.getErrorCode(),
                "implementation version query failed: " + receipt.getResult());
        return new EVMOutput(Hex.toHexString(receipt.getOutput())).getUint();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
