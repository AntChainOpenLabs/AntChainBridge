import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.errorcode.ErrorCode;
import com.alipay.mychain.sdk.vm.EVMParameter;
import org.slf4j.helpers.NOPLogger;

/** Call an existing EVM sender by Identity, never by a re-hashed contract name. */
public final class MychainExistingSender {
    public static void main(String[] args) throws Exception {
        if (args.length < 6 || args.length > 7) {
            throw new IllegalArgumentException("CONFIG SOURCE_IDENTITY TARGET_DOMAIN TARGET_IDENTITY MESSAGE CHECK_ONLY|SEND_ONCE [ATTEMPT_FILE]");
        }
        String source = MychainReceiptQuery.normalizeHash(args[1]);
        String target = MychainReceiptQuery.normalizeHash(args[3]);
        boolean send = "SEND_ONCE".equals(args[5]);
        if ((!send && !"CHECK_ONLY".equals(args[5])) || (send && args.length != 7)) {
            throw new IllegalArgumentException("SEND_ONCE requires a new persistent ATTEMPT_FILE; use CHECK_ONLY to inspect");
        }
        if (args[2].trim().isEmpty() || args[4].isEmpty()) {
            throw new IllegalArgumentException("target domain and message must not be empty");
        }
        Mychain020Client client = new Mychain020Client(Files.readAllBytes(Paths.get(args[0])), NOPLogger.NOP_LOGGER);
        try {
            if (!client.startup()) throw new IllegalStateException("Mychain SDK startup failed");
            Identity contract = new Identity(source);
            System.out.println("SOURCE_IDENTITY=" + source);
            System.out.println("EFFECTIVE_CONTRACT_IDENTITY=" + contract.hexStrValue());
            VMTypeEnum type = client.getContractType(source);
            System.out.println("SOURCE_CONTRACT_TYPE=" + type);
            if (type != VMTypeEnum.EVM) throw new IllegalStateException("source identity is not an existing EVM contract");
            if (!send) {
                System.out.println("TRANSACTION_SENT=False");
                return;
            }
            EVMParameter parameters = new EVMParameter("sendUnordered(identity,string,bytes)");
            parameters.addIdentity(new Identity(target));
            parameters.addString(args[2]);
            parameters.addBytes(args[4].getBytes(StandardCharsets.UTF_8));

            Path attempt = Paths.get(args[6]);
            // CREATE_NEW is atomic: an interrupted or uncertain attempt must be reconciled, not resent.
            Files.createFile(attempt, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.write(attempt, ("SUBMISSION_STARTED=true\nDO_NOT_RESEND=true\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("TRANSACTION_SUBMISSION_STARTED=True");
            // The String overload takes a NAME. Supplying a hex Identity there hashes it a second time.
            SendResponseResult response = client.callContract(contract, parameters, true);
            if (response == null) throw new IllegalStateException("empty response; outcome unknown, inspect attempt before further action");
            String summary = "SOURCE_TX_HASH=" + response.getTxId()
                    + "\nSOURCE_TX_CONFIRMED=" + response.isConfirmed()
                    + "\nSOURCE_TX_SUCCESS=" + response.isSuccess()
                    + "\nSOURCE_TX_ERROR_CODE=" + response.getErrorCode() + "\n";
            Files.write(attempt, summary.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
            System.out.print(summary);
            try {
                System.out.println("SOURCE_TX_ERROR_DESCRIPTION=" + ErrorCode.forNumber(Integer.parseInt(response.getErrorCode())).getErrorDesc());
            } catch (RuntimeException ignored) {
                System.out.println("SOURCE_TX_ERROR_DESCRIPTION=unrecognized SDK result");
            }
            if (!response.isConfirmed() || !response.isSuccess()) {
                throw new IllegalStateException("source execution not confirmed successful; query original hash, do not resend this attempt");
            }
        } finally {
            client.shutdown();
        }
    }
}
