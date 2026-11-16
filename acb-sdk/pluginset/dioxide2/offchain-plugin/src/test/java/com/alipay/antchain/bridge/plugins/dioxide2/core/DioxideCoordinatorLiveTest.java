package com.alipay.antchain.bridge.plugins.dioxide2.core;

import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.plugins.dioxide2.conf.DioxideConfig;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** Opt-in integration test against a local, disposable Dioxide node. */
public class DioxideCoordinatorLiveTest {
    private static final String RPC = "http://127.0.0.1:62222/api";

    private DioxideClient client;

    @After public void close() {
        if (client != null) { client.shutdown(); }
    }

    @Test public void secondPluginSharesTheNodeAcceptedCursor() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("dioxide.live"));
        String coordinatorConfig = System.getProperty("dioxide.live.coordinator");
        Assume.assumeTrue(coordinatorConfig != null && !coordinatorConfig.isEmpty());
        String privateKey = System.getProperty("dioxide.live.privateKey");
        Assume.assumeTrue(privateKey != null && !privateKey.trim().isEmpty());

        DioxideConfig config = new DioxideConfig();
        config.setRpcUrl(RPC);
        config.setWsRpc("ws://127.0.0.1:62222/api");
        config.setPrivateKey(privateKey.trim());
        config.setDappName("ict008");
        config.setTxCoordinatorConfigFile(coordinatorConfig);
        client = new DioxideClient(config, LoggerFactory.getLogger(getClass()));

        String account = client.getDioxideAccount().getAddressInString();
        long before = currentIsn(account);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("Amount", "1000000");
        Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("sender", account);
        tx.put("function", "core.coin.mint");
        tx.put("args", args);

        String operationId = "live-dioxide2-" + UUID.randomUUID();
        String hash = client.sendTransaction(JSON.toJSONString(tx), false, operationId);
        assertFalse(hash.isEmpty());
        assertEquals(before + 1, currentIsn(account));
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
