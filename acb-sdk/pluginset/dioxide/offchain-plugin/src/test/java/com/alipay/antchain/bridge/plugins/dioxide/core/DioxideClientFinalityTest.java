package com.alipay.antchain.bridge.plugins.dioxide.core;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.plugins.dioxide.conf.DioxideTypes;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class DioxideClientFinalityTest {
    @Test public void completeReceiptScopesIndexedMembersAcrossRealRpcJson() throws Exception {
        String rootJson = "{\"Hash\":\"root\",\"Height\":1,\"State\":\"DUS_ARCHIVED\",\"Invocation\":{\"Status\":\"IVKRET_SUCCESS\",\"Relays\":[\"group:0\",\"group:1\"]}}";
        String groupJson = "{\"Hash\":\"group\",\"State\":\"DUS_ARCHIVED\",\"Relays\":["
                + "{\"Function\":\"lambda8\",\"Invocation\":{\"Status\":\"IVKRET_SUCCESS\"}},"
                + "{\"Function\":\"lambda9\",\"Invocation\":{\"Status\":\"IVKRET_SUCCESS\",\"Relays\":[\"event\"]}},"
                + "{\"Function\":\"unrelated\",\"Invocation\":{\"Status\":\"IVKRET_EXCEPTION_THROWN\"}}]}";
        String eventJson = "{\"Hash\":\"event\",\"State\":\"DUS_ARCHIVED\",\"Target\":\"recvMessageInProtocol:name\"}";
        java.util.Map<String,String> replies = java.util.Map.of("root", rootJson, "group", groupJson, "event", eventJson);
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            String input = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String value = "{\"HeadHeight\":10}";
            if (exchange.getRequestURI().getQuery().contains("dx.transaction")) {
                value = replies.getOrDefault(com.alibaba.fastjson.JSON.parseObject(input).getString("hash"), "{}");
            }
            byte[] response = ("{\"ret\":" + value + "}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (java.io.OutputStream stream = exchange.getResponseBody()) { stream.write(response); }
        });
        server.start();
        DioxideClient client = null;
        try {
            com.alipay.antchain.bridge.plugins.dioxide.conf.DioxideConfig config =
                    new com.alipay.antchain.bridge.plugins.dioxide.conf.DioxideConfig();
            config.setRpcUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
            config.setPrivateKey(java.util.Base64.getEncoder().encodeToString(new byte[32]));
            config.setDappName("Test");
            config.setTxCoordinatorConfigFile("/nonexistent/isn-test-config");
            client = new DioxideClient(config, org.slf4j.helpers.NOPLogger.NOP_LOGGER);
            com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt receipt =
                    client.getCrossChainMessageReceipt(client.getTransactionByHash("root"));
            Assert.assertTrue(receipt.getErrorMsg(), receipt.isConfirmed());
            Assert.assertTrue(receipt.getErrorMsg(), receipt.isSuccessful());
            Assert.assertEquals("root", receipt.getTxhash());
        } finally {
            if (client != null) { client.shutdown(); }
            server.stop(0);
        }
    }


    @Test public void scopingJsonMembersDoesNotMutateTheCachedGroup() {
        com.alibaba.fastjson.JSONObject group = com.alibaba.fastjson.JSON.parseObject(
                "{\"State\":\"DUS_ARCHIVED\",\"Relays\":[{\"Function\":\"first\"},{\"Function\":\"second\"}]}");
        Assert.assertEquals("first", DioxideClient.scopeRelayGroup(group, "group:0")
                .getJSONArray("Relays").getJSONObject(0).getString("Function"));
        Assert.assertEquals("second", DioxideClient.scopeRelayGroup(group, "group:1")
                .getJSONArray("Relays").getJSONObject(0).getString("Function"));
        Assert.assertEquals(2, group.getJSONArray("Relays").size());
    }

    @Test public void indexedGroupDoesNotInspectAnotherBusinessFailure() {
        DioxideTransaction bad = DioxideTransaction.builder().invocation(
                DioxideTransaction.Invocation.builder().status("IVKRET_EXCEPTION_THROWN").build()).build();
        DioxideTransaction good = DioxideTransaction.builder().invocation(
                DioxideTransaction.Invocation.builder().status("IVKRET_SUCCESS").relays(List.of("child")).build()).build();
        DioxideTransaction group = DioxideTransaction.builder().txHash("group").state("DUS_ARCHIVED")
                .embeddedRelays(List.of(bad, good)).build();
        DioxideTransaction child = DioxideTransaction.builder().txHash("child").state("DUS_ARCHIVED").build();
        DioxideTransaction root = DioxideTransaction.builder().txHash("root").state("DUS_ARCHIVED")
                .invocation(DioxideTransaction.Invocation.builder().relays(List.of("group:1")).build()).build();
        Map<String, DioxideTransaction> nodes = Map.of("group", group, "child", child);
        Assert.assertEquals(DioxideClient.TxFinalityState.FINALIZED,
                DioxideClient.evaluateTxFinalityWithRelays(root, nodes::get).state());
        root.getInvocation().setRelays(List.of("group:0"));
        Assert.assertEquals(DioxideClient.TxFinalityState.FAILED,
                DioxideClient.evaluateTxFinalityWithRelays(root, nodes::get).state());
        root.getInvocation().setRelays(List.of("group:9"));
        Assert.assertEquals(DioxideClient.TxFinalityState.PENDING,
                DioxideClient.evaluateTxFinalityWithRelays(root, nodes::get).state());
    }

    @Test public void differentMembersOfSameGroupRemainDistinctAndFetchOnce() {
        DioxideTransaction first = DioxideTransaction.builder().invocation(
                DioxideTransaction.Invocation.builder().status("IVKRET_SUCCESS").build()).build();
        DioxideTransaction second = DioxideTransaction.builder().invocation(
                DioxideTransaction.Invocation.builder().status("IVKRET_SUCCESS").relays(List.of("pending")).build()).build();
        DioxideTransaction group = DioxideTransaction.builder().state("DUS_ARCHIVED").embeddedRelays(List.of(first, second)).build();
        DioxideTransaction root = DioxideTransaction.builder().txHash("root").state("DUS_ARCHIVED")
                .invocation(DioxideTransaction.Invocation.builder().relays(List.of("group:0", "group:1")).build()).build();
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        Assert.assertEquals(DioxideClient.TxFinalityState.PENDING,
                DioxideClient.evaluateTxFinalityWithRelays(root, hash -> {
                    if ("group".equals(hash)) { reads.incrementAndGet(); return group; }
                    return null;
                }).state());
        Assert.assertEquals(1, reads.get());
    }

    @Test
    public void testEmbeddedInvocationFailureAndChildFinality() {
        DioxideTransaction root = DioxideTransaction.builder().txHash("root").confirmState("TXN_ARCHIVED")
                .embeddedRelays(List.of(DioxideTransaction.builder()
                        .invocation(DioxideTransaction.Invocation.builder().status("IVKRET_EXCEPTION_THROWN").build()).build())).build();
        Assert.assertEquals(DioxideClient.TxFinalityState.FAILED, evaluate(root, Map.of()).state());
        root.setEmbeddedRelays(List.of(DioxideTransaction.builder()
                .invocation(DioxideTransaction.Invocation.builder().status("IVKRET_SUCCESS").relays(List.of("child")).build()).build()));
        DioxideTransaction child = DioxideTransaction.builder().txHash("child").confirmState("TXN_READY").build();
        Assert.assertEquals(DioxideClient.TxFinalityState.PENDING, evaluate(root, Map.of("child", child)).state());
        child.setConfirmState("TXN_ARCHIVED");
        Assert.assertEquals(DioxideClient.TxFinalityState.FINALIZED, evaluate(root, Map.of("child", child)).state());
    }

    @Test
    public void testDiagnosticFieldsRetainUnsignedIsnAndSigner() {
        DioxideTransaction tx = com.alibaba.fastjson.JSON.parseObject(
                "{\"ISN\":4294967295,\"Signers\":[\"account:ed25519\"],\"Timestamp\":1788415666329}",
                DioxideTransaction.class);
        Assert.assertEquals(Long.valueOf(4294967295L), tx.getIsn());
        Assert.assertEquals(List.of("account:ed25519"), tx.getSigners());
    }

    @Test
    public void testContractVersionMatches() {
        Assert.assertTrue(DioxideClient.contractVersionMatches("1043692781569", 1043692781569L));
        Assert.assertFalse(DioxideClient.contractVersionMatches("1043692781569", 1043692781570L));
        Assert.assertFalse(DioxideClient.contractVersionMatches("not-a-cid", 1043692781569L));
    }

    @Test
    public void testIsTxFinalized() {
        Assert.assertFalse(DioxideClient.isTxFinalized(null));
        Assert.assertFalse(DioxideClient.isTxFinalized(transactionWithState(null)));
        Assert.assertFalse(DioxideClient.isTxFinalized(transactionWithState(
                DioxideTypes.TxnConfirmState.TXN_READY.name()
        )));
        Assert.assertFalse(DioxideClient.isTxFinalized(transactionWithState(
                DioxideTypes.TxnConfirmState.TXN_CONFIRMED.name()
        )));
        Assert.assertFalse(DioxideClient.isTxFinalized(transactionWithState("UNKNOWN")));
        Assert.assertTrue(DioxideClient.isTxFinalized(transactionWithState(
                DioxideTypes.TxnConfirmState.TXN_FINALIZED.name()
        )));
        Assert.assertTrue(DioxideClient.isTxFinalized(transactionWithState(
                DioxideTypes.TxnConfirmState.TXN_ARCHIVED.name()
        )));
        Assert.assertTrue(DioxideClient.isTxFinalized(transactionWithBlockState(
                DioxideTypes.BlockState.DUS_FINALIZED.name()
        )));
        Assert.assertTrue(DioxideClient.isTxFinalized(transactionWithBlockState(
                DioxideTypes.BlockState.DUS_ARCHIVED.name()
        )));
    }

    @Test
    public void testIsTxTerminalFailed() {
        Assert.assertFalse(DioxideClient.isTxTerminalFailed(null));
        Assert.assertFalse(DioxideClient.isTxTerminalFailed(transactionWithState(null)));
        Assert.assertFalse(DioxideClient.isTxTerminalFailed(transactionWithState(
                DioxideTypes.TxnConfirmState.TXN_CONFIRMED.name()
        )));
        Assert.assertTrue(DioxideClient.isTxTerminalFailed(transactionWithState(
                DioxideTypes.TxnConfirmState.TXN_RELAY_INVALIDED.name()
        )));
        Assert.assertTrue(DioxideClient.isTxTerminalFailed(transactionWithState(
                DioxideTypes.TxnConfirmState.TXN_ABORTED.name()
        )));
        Assert.assertTrue(DioxideClient.isTxTerminalFailed(transactionWithState(
                DioxideTypes.TxnConfirmState.TXN_EXPIRED.name()
        )));
        Assert.assertTrue(DioxideClient.isTxTerminalFailed(transactionWithBlockState(
                DioxideTypes.BlockState.DUS_ARCHIVED_UNCLE.name()
        )));
        Assert.assertFalse(DioxideClient.isTxTerminalFailed(transactionWithBlockState(
                DioxideTypes.BlockState.DUS_ARCHIVED.name()
        )));
    }

    @Test
    public void testEvaluateFinalityAcrossRelayTree() {
        DioxideTransaction root = transactionWithRelays(
                "root",
                DioxideTypes.TxnConfirmState.TXN_FINALIZED.name(),
                List.of("child")
        );
        DioxideTransaction pendingChild = transactionWithRelays(
                "child",
                DioxideTypes.TxnConfirmState.TXN_CONFIRMED.name(),
                List.of()
        );
        DioxideTransaction finalizedChild = transactionWithRelays(
                "child",
                DioxideTypes.TxnConfirmState.TXN_ARCHIVED.name(),
                List.of()
        );
        DioxideTransaction failedChild = transactionWithRelays(
                "child",
                DioxideTypes.TxnConfirmState.TXN_ABORTED.name(),
                List.of()
        );

        Assert.assertEquals(
                DioxideClient.TxFinalityState.PENDING,
                evaluate(root, Map.of("child", pendingChild)).state()
        );
        Assert.assertEquals(
                DioxideClient.TxFinalityState.FINALIZED,
                evaluate(root, Map.of("child", finalizedChild)).state()
        );
        Assert.assertEquals(
                DioxideClient.TxFinalityState.FAILED,
                evaluate(root, Map.of("child", failedChild)).state()
        );
        Assert.assertEquals(
                DioxideClient.TxFinalityState.PENDING,
                evaluate(root, Map.of()).state()
        );
        Assert.assertEquals(
                DioxideClient.TxFinalityState.PENDING,
                evaluate(
                        transactionWithRelays(
                                "root",
                                DioxideTypes.TxnConfirmState.TXN_FINALIZED.name(),
                                List.of("")
                        ),
                        Map.of()
                ).state()
        );
    }

    @Test
    public void testRelayHashNormalizationAndCycleProtection() {
        DioxideTransaction root = transactionWithRelays(
                "root",
                DioxideTypes.TxnConfirmState.TXN_FINALIZED.name(),
                List.of("child")
        );
        DioxideTransaction child = transactionWithRelays(
                "child",
                DioxideTypes.TxnConfirmState.TXN_FINALIZED.name(),
                List.of("root")
        );

        Assert.assertEquals("child", DioxideClient.normalizeRelayTxHash("child:7"));
        Assert.assertEquals(
                DioxideClient.TxFinalityState.FINALIZED,
                evaluate(root, Map.of("root", root, "child", child)).state()
        );
    }

    @Test
    public void testFinalizedRelayGroupReportsEmbeddedInvocationFailure() {
        JSONObject relayGroup = relayGroup(
                DioxideTypes.TxnConfirmState.TXN_ARCHIVED.name(),
                embeddedInvocation("AuthMsg.global.__relaylambda_8", "IVKRET_SUCCESS", List.of()),
                embeddedInvocation("AuthMsg.global.__relaylambda_9", "IVKRET_EXCEPTION_THROWN", List.of())
        );

        DioxideClient.RelayEventInspection inspection = DioxideClient.inspectRelayEvents(relayGroup, "relay-group");

        Assert.assertEquals(DioxideClient.RelayEventState.FAILED, inspection.state());
        Assert.assertTrue(inspection.eventTargetTxHashes().isEmpty());
        Assert.assertTrue(inspection.errorMessage().contains("IVKRET_EXCEPTION_THROWN"));
        Assert.assertTrue(inspection.errorMessage().contains("AuthMsg.global.__relaylambda_9"));
    }

    @Test
    public void testFinalizedRelayGroupReturnsProtocolEventTransactions() {
        JSONObject relayGroup = relayGroup(
                DioxideTypes.TxnConfirmState.TXN_ARCHIVED.name(),
                embeddedInvocation("AuthMsg.global.__relaylambda_8", "IVKRET_SUCCESS", List.of()),
                embeddedInvocation(
                        "AuthMsg.global.__relaylambda_9",
                        "IVKRET_SUCCESS",
                        List.of("target-a:0", "target-b", "target-a:1")
                )
        );

        DioxideClient.RelayEventInspection inspection = DioxideClient.inspectRelayEvents(relayGroup, "relay-group");

        Assert.assertEquals(DioxideClient.RelayEventState.READY, inspection.state());
        Assert.assertEquals(List.of("target-a:0", "target-b", "target-a:1"), inspection.eventTargetTxHashes());
        Assert.assertEquals("", inspection.errorMessage());
    }

    @Test
    public void testMissingRelayEventsRemainPendingOnlyBeforeFinality() {
        Assert.assertEquals(
                DioxideClient.RelayEventState.PENDING,
                DioxideClient.inspectRelayEvents(
                        relayGroup(DioxideTypes.TxnConfirmState.TXN_CONFIRMED.name()),
                        "relay-group"
                ).state()
        );
        Assert.assertEquals(
                DioxideClient.RelayEventState.FAILED,
                DioxideClient.inspectRelayEvents(
                        relayGroup(DioxideTypes.TxnConfirmState.TXN_ARCHIVED.name()),
                        "relay-group"
                ).state()
        );
    }

    private DioxideTransaction transactionWithState(String state) {
        return DioxideTransaction.builder().txHash("tx-hash").confirmState(state).build();
    }

    private DioxideTransaction transactionWithRelays(String txHash, String state, List<String> relays) {
        return DioxideTransaction.builder()
                .txHash(txHash)
                .confirmState(state)
                .invocation(DioxideTransaction.Invocation.builder().relays(relays).build())
                .build();
    }

    private DioxideTransaction transactionWithBlockState(String state) {
        return DioxideTransaction.builder().state(state).build();
    }

    private DioxideClient.TxFinalityResult evaluate(
            DioxideTransaction root,
            Map<String, DioxideTransaction> transactions
    ) {
        return DioxideClient.evaluateTxFinalityWithRelays(root, transactions::get);
    }

    private JSONObject relayGroup(String confirmState, JSONObject... embeddedInvocations) {
        JSONObject relayGroup = new JSONObject();
        relayGroup.put("Hash", "relay-group");
        relayGroup.put("ConfirmState", confirmState);
        JSONArray relays = new JSONArray();
        relays.addAll(List.of(embeddedInvocations));
        relayGroup.put("Relays", relays);
        return relayGroup;
    }

    private JSONObject embeddedInvocation(String function, String status, List<String> relays) {
        JSONObject invocation = new JSONObject();
        invocation.put("Status", status);
        invocation.put("Relays", relays);
        JSONObject embedded = new JSONObject();
        embedded.put("Function", function);
        embedded.put("Invocation", invocation);
        return embedded;
    }
}
