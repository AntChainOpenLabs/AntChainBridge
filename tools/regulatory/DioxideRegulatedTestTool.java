package tools.regulatory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.monitor.MonitorMessageFactory;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only, sanitized inspection; never submits transactions or prints raw AM/UCP data. */
public final class DioxideRegulatedTestTool {
    private final JSONObject config;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private DioxideRegulatedTestTool(Path path) throws Exception {
        config = JSON.parseObject(Files.readString(path));
    }
    private Object rpc(String method, JSONObject body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(config.getString("rpcUrl") + "?req=" + method))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString())).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        require(response.statusCode() == 200, "RPC HTTP error");
        JSONObject result = JSON.parseObject(response.body());
        require(!result.containsKey("err"), "RPC rejected " + method);
        return result.get("ret");
    }
    private static JSONObject body(String key, Object value) {
        JSONObject result = new JSONObject(); result.put(key, value); return result;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private void inspectApp(String app) throws Exception {
        var info = (JSONObject) rpc("dx.contract_info", body("contract", app));
        var state = ((JSONObject) rpc("dx.contract_state", body("contract_with_scope", app + ".global")))
                .getJSONObject("State");
        JSONObject report = new JSONObject(true);
        report.put("contract", app); report.put("cid", info.getString("ContractVersionID"));
        report.put("monitor", state.get("monitorAddress"));
        for (String key : new String[]{"orderedMessages", "unorderedMessages"}) {
            JSONArray values = state.getJSONArray(key);
            require(values != null, "missing App array " + key);
            report.put(key + ".count", values.size());
            report.put(key + ".sha256", digest(values.toJSONString().getBytes(StandardCharsets.UTF_8)));
            if (!values.isEmpty()) {
                JSONArray last = values.getJSONArray(values.size() - 1);
                byte[] bytes = new byte[last.size()];
                for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) last.getIntValue(i);
                report.put(key + ".last.hex", HexFormat.of().formatHex(bytes));
            }
        }
        System.out.println(report.toJSONString());
    }
    static byte[] eventBytes(String input) {
        byte[] bytes = HexFormat.of().parseHex(input.startsWith("0x") ? input.substring(2) : input);
        require(bytes.length >= 4, "truncated AM event");
        int size = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        require(size >= 0 && size == bytes.length - 4, "invalid AM event length");
        return Arrays.copyOfRange(bytes, 4, bytes.length);
    }
    static JSONObject select(JSONObject group, String index) {
        if (index.isEmpty()) return group;
        JSONArray relays = group.getJSONArray("Relays");
        int n = Integer.parseInt(index);
        require(relays != null && n >= 0 && n < relays.size(), "invalid relay group index");
        return relays.getJSONObject(n);
    }
    private void visit(String reference, Map<String, JSONObject> rows) throws Exception {
        if (rows.containsKey(reference)) return;
        String[] parts = reference.split(":", 2);
        JSONObject group = (JSONObject) rpc("dx.transaction", body("hash", parts[0]));
        require("DUS_ARCHIVED".equals(group.getString("State")), "transaction not archived: " + reference);
        JSONObject tx = select(group, parts.length == 2 ? parts[1] : "");
        JSONObject invocation = tx.getJSONObject("Invocation");
        if (invocation != null) {
            require("IVKRET_SUCCESS".equals(invocation.getString("Status")), "relay invocation failed: " + reference);
            require(invocation.get("ErrorCode") == null || "NoException".equals(invocation.getString("ErrorCode")),
                    "relay execution error: " + reference);
        }
        rows.put(reference, tx);
        JSONObject safe = new JSONObject(true);
        safe.put("reference", reference); safe.put("state", group.getString("State"));
        safe.put("height", group.get("Height")); safe.put("function", tx.get("Function"));
        safe.put("target", tx.get("Target")); safe.put("status", invocation == null ? null : invocation.get("Status"));
        System.out.println("relay=" + safe.toJSONString());
        if (invocation != null && invocation.getJSONArray("Relays") != null) {
            for (Object child : invocation.getJSONArray("Relays")) visit(child.toString(), rows);
        }
        // A grouped root owns its embedded relays; an indexed reference owns only that branch.
        if (parts.length == 1 && tx.getJSONArray("Relays") != null) {
            for (int i = 0; i < tx.getJSONArray("Relays").size(); i++) visit(parts[0] + ":" + i, rows);
        }
    }
    private void inspectRollback(String root, String domain, String author, String receiver, String payload) throws Exception {
        byte[] expectedPayload = payload.getBytes(StandardCharsets.UTF_8);
        Map<String, JSONObject> rows = new LinkedHashMap<>(); visit(root, rows);
        int matches = 0, amEvents = 0;
        String amName = config.getString("dappName") + ".AuthMsg";
        for (JSONObject tx : rows.values()) {
            if (!"SendAuthMessage:name".equals(tx.getString("Target"))
                    || tx.getString("Function") == null || !tx.getString("Function").contains(amName)) continue;
            amEvents++;
            var am = AuthMessageFactory.createAuthMessage(eventBytes(tx.getString("Input")));
            var sdp = SDPMessageFactory.createSDPMessage(am.getPayload());
            var monitor = MonitorMessageFactory.createMonitorMessage(sdp.getPayload());
            require(sdp.getVersion() == 1 && sdp.getSequence() == -1, "rollback must be V1 unordered");
            require(monitor.getMonitorType() == 3, "outbound AM is not rollback");
            require("MidMonitoring not pass".equals(monitor.getMonitorMsg()), "rollback reason mismatch");
            require(domain.equals(sdp.getTargetDomain().getDomain()), "rollback domain mismatch");
            require(author.equalsIgnoreCase(HexFormat.of().formatHex(am.getIdentity().getRawID())), "rollback author mismatch");
            require(receiver.equalsIgnoreCase(HexFormat.of().formatHex(sdp.getTargetIdentity().getRawID())), "rollback receiver mismatch");
            require(Arrays.equals(expectedPayload, monitor.getPayload()), "rollback payload mismatch");
            matches++;
        }
        require(matches == 1 && amEvents == 1, "expected exactly one rollback AM event");
        JSONObject report = new JSONObject(true);
        report.put("root", root); report.put("relayCount", rows.size()); report.put("rollbackCount", matches);
        report.put("monitorType", 3); report.put("reason", "MidMonitoring not pass");
        report.put("targetDomain", domain); report.put("author", author); report.put("receiver", receiver);
        report.put("unordered", true); report.put("payloadSha256", digest(expectedPayload));
        report.put("allChecksPassed", true); System.out.println(report.toJSONString());
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "self-test".equals(args[0])) {
            require(Arrays.equals(new byte[]{1, 2}, eventBytes("020000000102")), "event decoder test");
            boolean rejected = false;
            try { eventBytes("030000000102"); } catch (IllegalStateException e) { rejected = true; }
            require(rejected, "length validation test");
            JSONObject group = JSON.parseObject("{\"Relays\":[{\"Target\":\"a\"},{\"Target\":\"b\"}]}");
            require("b".equals(select(group, "1").getString("Target")), "group index test");
            require(select(group, "") == group, "single/root selection test");
            System.out.println("selfTestPassed=true"); return;
        }
        require(args.length >= 3, "expected inspect-app CONFIG APP or inspect-rollback CONFIG ROOT DOMAIN AUTHOR RECEIVER PAYLOAD");
        var tool = new DioxideRegulatedTestTool(Path.of(args[1]));
        if ("inspect-app".equals(args[0]) && args.length == 3) tool.inspectApp(args[2]);
        else if ("inspect-rollback".equals(args[0]) && args.length == 7)
            tool.inspectRollback(args[2], args[3], args[4], args[5], args[6]);
        else throw new IllegalArgumentException("invalid mode or argument count");
    }
}
