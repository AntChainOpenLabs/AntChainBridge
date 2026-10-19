package com.alipay.antchain.bridge.plugins.dioxide.core;

import org.junit.Assert;
import org.junit.Test;
import java.util.List;
import java.util.Map;

public class DioxideIsnFinalityTest {
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
    @Test public void finalizedRootDoesNotHideFailedEmbeddedInvocation() {
        DioxideTransaction root = DioxideTransaction.builder().txHash("root").confirmState("TXN_ARCHIVED")
                .embeddedRelays(List.of(DioxideTransaction.builder().invocation(
                        DioxideTransaction.Invocation.builder().status("IVKRET_EXCEPTION_THROWN").build()).build())).build();
        Assert.assertEquals(DioxideClient.TxFinalityState.FAILED,
                DioxideClient.evaluateTxFinalityWithRelays(root, hash -> null).state());
    }
    @Test public void waitForAllChildrenAndRecognizeBothStateFamilies() {
        DioxideTransaction root = DioxideTransaction.builder().txHash("root").confirmState("TXN_ARCHIVED")
                .invocation(DioxideTransaction.Invocation.builder().status("IVKRET_SUCCESS").relays(List.of("child")).build()).build();
        DioxideTransaction child = DioxideTransaction.builder().txHash("child").confirmState("TXN_READY").build();
        Assert.assertEquals(DioxideClient.TxFinalityState.PENDING,
                DioxideClient.evaluateTxFinalityWithRelays(root, hash -> child).state());
        child.setConfirmState(null); child.setState("DUS_ARCHIVED");
        Assert.assertEquals(DioxideClient.TxFinalityState.FINALIZED,
                DioxideClient.evaluateTxFinalityWithRelays(root, hash -> child).state());
        child.setConfirmState("TXN_ABORTED");
        Assert.assertEquals(DioxideClient.TxFinalityState.FAILED,
                DioxideClient.evaluateTxFinalityWithRelays(root, hash -> child).state());
    }
    @Test public void missingOrBlankChildIsNotSuccess() {
        DioxideTransaction root = DioxideTransaction.builder().txHash("root").state("DUS_ARCHIVED")
                .invocation(DioxideTransaction.Invocation.builder().relays(List.of("missing")).build()).build();
        Assert.assertEquals(DioxideClient.TxFinalityState.PENDING,
                DioxideClient.evaluateTxFinalityWithRelays(root, hash -> null).state());
        Assert.assertFalse(DioxideClient.isTxFinalized(null));
    }
}
