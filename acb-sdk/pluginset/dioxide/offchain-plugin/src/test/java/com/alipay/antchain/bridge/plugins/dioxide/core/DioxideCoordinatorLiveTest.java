package com.alipay.antchain.bridge.plugins.dioxide.core;

import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.plugins.lib.transactions.JdbcTransactionCoordinator;
import com.alipay.antchain.bridge.plugins.dioxide.conf.DioxideConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Opt-in integration test against a local, disposable Dioxide node. */
public class DioxideCoordinatorLiveTest {
    private static final String RPC = "http://127.0.0.1:62222/api";

    private DioxideClient client;

    @After public void close() {
        if (client != null) { client.shutdown(); }
    }

    @Test public void nodeAcceptanceAdvancesExactlyOnce() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("dioxide.live"));
        String coordinatorConfig = System.getProperty("dioxide.live.coordinator");
        Assume.assumeTrue(coordinatorConfig != null && !coordinatorConfig.isEmpty());
        Assume.assumeTrue(privateKey() != null);

        client = newClient(RPC, coordinatorConfig);

        String account = client.getDioxideAccount().getAddressInString();
        long before = currentIsn(account);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("Amount", "1000000");
        Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("sender", account);
        tx.put("function", "core.coin.mint");
        tx.put("args", args);

        String operationId = "live-normal-" + UUID.randomUUID();
        String hash = client.sendTransaction(JSON.toJSONString(tx), false, operationId);
        assertFalse(hash.isEmpty());
        assertEquals(before + 1, currentIsn(account));

        // Stable operation identity must return the original hash without a new ISN.
        assertEquals(hash, client.sendTransaction(JSON.toJSONString(tx), false, operationId));
        assertEquals(before + 1, currentIsn(account));
    }

    @Test public void explicitRpcRejectionKeepsIsnAndSameOperationRetriesIt() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("dioxide.live"));
        String coordinatorConfig = System.getProperty("dioxide.live.coordinator");
        Assume.assumeTrue(coordinatorConfig != null && !coordinatorConfig.isEmpty());
        Assume.assumeTrue(privateKey() != null);

        HttpServer proxy = rejectionProxy(62223);
        proxy.start();
        DioxideClient rejectedClient = newClient("http://127.0.0.1:62223/api", coordinatorConfig);
        String account = rejectedClient.getDioxideAccount().getAddressInString();
        long before = currentIsn(account);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("Amount", "1000000");
        Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("sender", account);
        tx.put("function", "core.coin.mint");
        tx.put("args", args);
        String payload = JSON.toJSONString(tx);
        String operationId = "live-rejected-" + UUID.randomUUID();
        try {
            rejectedClient.sendTransaction(payload, false, operationId);
            org.junit.Assert.fail("injected tx.send rejection unexpectedly succeeded");
        } catch (Exception expected) {
            assertTrue(hasCause(expected, JdbcTransactionCoordinator.NodeRejectedException.class));
        } finally {
            rejectedClient.shutdown();
            proxy.stop(0);
        }
        assertEquals(before, currentIsn(account));

        client = newClient(RPC, coordinatorConfig);
        String hash = client.sendTransaction(payload, false, operationId);
        assertFalse(hash.isEmpty());
        assertEquals(before + 1, currentIsn(account));
    }

    @Test public void lostSendResponseRebroadcastsExactBytesWithoutAnotherIsn() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("dioxide.live"));
        String coordinatorConfig = System.getProperty("dioxide.live.coordinator");
        Assume.assumeTrue(coordinatorConfig != null && !coordinatorConfig.isEmpty());
        Assume.assumeTrue(privateKey() != null);

        HttpServer proxy = lostResponseProxy(62224);
        proxy.start();
        DioxideClient uncertainClient = newClient("http://127.0.0.1:62224/api", coordinatorConfig);
        String account = uncertainClient.getDioxideAccount().getAddressInString();
        long before = currentIsn(account);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("Amount", "1000000");
        Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("sender", account);
        tx.put("function", "core.coin.mint");
        tx.put("args", args);
        String payload = JSON.toJSONString(tx);
        String operationId = "live-lost-response-" + UUID.randomUUID();
        try {
            uncertainClient.sendTransaction(payload, false, operationId);
            org.junit.Assert.fail("dropped tx.send response unexpectedly succeeded");
        } catch (Exception expected) {
            assertFalse(hasCause(expected, JdbcTransactionCoordinator.NodeRejectedException.class));
        } finally {
            uncertainClient.shutdown();
            proxy.stop(0);
        }
        assertEquals(before + 1, currentIsn(account));

        client = newClient(RPC, coordinatorConfig);
        String recoveredHash = client.sendTransaction(payload, false, operationId);
        assertFalse(recoveredHash.isEmpty());
        assertEquals(before + 1, currentIsn(account));
    }

    private DioxideClient newClient(String rpc, String coordinatorConfig) {
        DioxideConfig config = new DioxideConfig();
        config.setRpcUrl(rpc);
        config.setWsRpc("ws://127.0.0.1:62222/api");
        config.setPrivateKey(privateKey());
        config.setDappName("ict007");
        config.setTxCoordinatorConfigFile(coordinatorConfig);
        return new DioxideClient(config, LoggerFactory.getLogger(getClass()));
    }

    private String privateKey() {
        String value = System.getProperty("dioxide.live.privateKey");
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private HttpServer rejectionProxy(int port) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        HttpClient forward = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        server.createContext("/api", exchange -> {
            try {
                String query = exchange.getRequestURI().getRawQuery();
                byte[] requestBody = exchange.getRequestBody().readAllBytes();
                byte[] response;
                if (query != null && query.contains("req=tx.send")) {
                    response = "{\"rsp\":\"tx.send\",\"err\":19999,\"ret\":\"injected rejection\"}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(RPC + (query == null ? "" : "?" + query)))
                            .timeout(Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                            .build();
                    response = forward.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (Exception e) {
                byte[] response = ("{\"rsp\":\"proxy\",\"err\":19998,\"ret\":\"" + e.getClass().getSimpleName() + "\"}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, response.length);
                exchange.getResponseBody().write(response);
            } finally {
                exchange.close();
            }
        });
        return server;
    }

    private HttpServer lostResponseProxy(int port) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        HttpClient forward = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        server.createContext("/api", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RPC + (query == null ? "" : "?" + query)))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();
            try {
                byte[] response = forward.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
                if (query != null && query.contains("req=tx.send")) {
                    exchange.close(); // The node accepted it, but the caller receives no response.
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exchange.close();
            } finally {
                exchange.close();
            }
        });
        return server;
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) { return true; }
        }
        return false;
    }

    private long currentIsn(String account) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RPC + "?req=dx.isn"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"address\":\"" + account + "\"}"))
                .build();
        String body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
        return JSON.parseObject(body).getJSONObject("ret").getLongValue("ISN");
    }
}
