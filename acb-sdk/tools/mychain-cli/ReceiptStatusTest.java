import java.math.BigInteger;
import com.alibaba.fastjson.JSON;
import com.alipay.mychain.sdk.message.query.QueryTransactionReceiptResponse;

/** Regression uses the SDK's real JSON decoder, including its default receipt on a 404 response. */
public class ReceiptStatusTest {
    private static int cases;
    private static void check(int code, String block, int result, boolean found, String status) {
        QueryTransactionReceiptResponse response = new QueryTransactionReceiptResponse();
        response.fromJson(JSON.parseObject("{\"return_code\":" + code + ",\"block_number\":" + block
                + ",\"transaction_index\":0,\"receipt\":{\"result\":" + result + ",\"output\":\"\",\"gas_used\":0,\"logs\":[]}}"));
        if (MychainReceiptQuery.found(response) != found || !MychainReceiptQuery.status(response).equals(status)) {
            throw new AssertionError("code=" + code + ", block=" + block + ", actual=" + MychainReceiptQuery.status(response));
        }
        cases++;
    }
    public static void main(String[] args) {
        check(404, "0", 0, false, "NOT_FOUND");
        check(408, "0", 0, false, "VERIFICATION_REJECTED");
        check(413, "0", 0, false, "PENDING");
        check(414, "0", 0, false, "PENDING");
        check(0, "0", 0, false, "UNCONFIRMED");
        check(0, "100", 0, true, "EXECUTED_SUCCESS");
        check(0, "100", 10201, true, "EXECUTED_FAILED");
        check(0, BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE).toString(), 0, false, "UNCONFIRMED");
        if (MychainReceiptQuery.found(null) || !"UNAVAILABLE".equals(MychainReceiptQuery.status(null))) throw new AssertionError("null response");
        System.out.println("PASS " + (cases + 1) + " receipt cases");
    }
}
