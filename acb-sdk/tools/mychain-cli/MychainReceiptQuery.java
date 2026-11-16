import java.math.BigInteger;
import java.nio.file.*;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.message.query.QueryTransactionReceiptResponse;
import org.slf4j.helpers.NOPLogger;

/** Read-only receipt diagnosis. A default receipt inside an error response is not on-chain evidence. */
public final class MychainReceiptQuery {
    private static final BigInteger UINT64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    static String normalizeHash(String value) {
        String normalized = value.replaceFirst("^0[xX]", "");
        if (!normalized.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("expected exactly 32 bytes of hex");
        return normalized.toLowerCase(java.util.Locale.ROOT);
    }

    static boolean found(QueryTransactionReceiptResponse response) {
        return response != null && response.isSuccess()
                && response.getBlockNumber() != null
                && response.getBlockNumber().signum() > 0
                && response.getBlockNumber().compareTo(UINT64_MAX) < 0
                && response.getTransactionReceipt() != null;
    }

    static String status(QueryTransactionReceiptResponse response) {
        if (response == null) return "UNAVAILABLE";
        if (found(response)) return response.getTransactionReceipt().getResult() == 0 ? "EXECUTED_SUCCESS" : "EXECUTED_FAILED";
        if (response.isSuccess()) return "UNCONFIRMED";
        if (response.getErrorCode() == null) return "QUERY_ERROR";
        int code = response.getErrorCode().getErrorCode();
        if (code == 404) return "NOT_FOUND";
        if (code == 408) return "VERIFICATION_REJECTED";
        if (code == 413 || code == 414) return "PENDING";
        return "QUERY_ERROR";
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("CONFIG TX_HASH");
        String hash = normalizeHash(args[1]);
        Mychain020Client client = new Mychain020Client(Files.readAllBytes(Paths.get(args[0])), NOPLogger.NOP_LOGGER);
        try {
            if (!client.startup()) throw new IllegalStateException("Mychain SDK startup failed");
            QueryTransactionReceiptResponse response = client.getTxReceiptByTxhash(hash);
            System.out.println("QUERY_MODE=NATIVE_READ_ONLY\nTRANSACTION_SENT=False\nTX_HASH=" + hash);
            System.out.println("QUERY_SUCCESS=" + (response != null && response.isSuccess()));
            System.out.println("QUERY_ERROR=" + (response == null ? "empty response" : response.getErrorCode()));
            System.out.println("BLOCK_NUMBER=" + (response == null ? "unknown" : response.getBlockNumber()));
            System.out.println("STATUS=" + status(response));
            System.out.println("NATIVE_RECEIPT_FOUND=" + (found(response) ? "True" : "False"));
            if (found(response)) System.out.println("EXECUTION_RESULT=" + response.getTransactionReceipt().getResult());
        } finally {
            client.shutdown();
        }
    }
}
