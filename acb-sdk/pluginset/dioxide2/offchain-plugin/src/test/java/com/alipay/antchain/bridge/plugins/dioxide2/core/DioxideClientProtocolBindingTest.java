package com.alipay.antchain.bridge.plugins.dioxide2.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.plugins.dioxide2.conf.DioxideConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.slf4j.helpers.NOPLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DioxideClientProtocolBindingTest {

    private DioxideClient liveClient;

    @After
    public void closeLiveClient() {
        if (liveClient != null) {
            liveClient.shutdown();
        }
    }

    @Test
    public void unconfiguredBindingSubmitsOnce() throws Exception {
        AtomicInteger submissions = new AtomicInteger();

        String txHash = DioxideClient.ensureProtocolBinding(
                356501159937L,
                0,
                JSONObject::new,
                () -> {
                    submissions.incrementAndGet();
                    return "tx-hash";
                }
        );

        Assert.assertEquals("tx-hash", txHash);
        Assert.assertEquals(1, submissions.get());
    }

    @Test
    public void matchingBindingSkipsSubmissionIncludingAddressCaseDifferences() throws Exception {
        AtomicInteger submissions = new AtomicInteger();
        JSONObject state = matchingState(356501159937L, 0);
        state.getJSONObject("protocolRoutes").put("0", "0X0000005301200001:CONTRACT");
        JSONObject subProtocols = state.getJSONObject("subProtocols");
        JSONObject binding = subProtocols.getJSONObject("0x0000005301200001:contract");
        subProtocols.remove("0x0000005301200001:contract");
        binding.put("protocolAddress", "0X0000005301200001:CONTRACT");
        subProtocols.put("0X0000005301200001:CONTRACT", binding);

        String txHash = DioxideClient.ensureProtocolBinding(
                356501159937L,
                0,
                () -> state,
                () -> {
                    submissions.incrementAndGet();
                    return "must-not-run";
                }
        );

        Assert.assertNull(txHash);
        Assert.assertEquals(0, submissions.get());
    }

    @Test
    public void conflictingRouteIsRejectedBeforeSubmission() throws Exception {
        AtomicInteger submissions = new AtomicInteger();
        JSONObject state = matchingState(356501159938L, 0);

        try {
            DioxideClient.ensureProtocolBinding(
                    356501159937L,
                    0,
                    () -> state,
                    () -> {
                        submissions.incrementAndGet();
                        return "must-not-run";
                    }
            );
            Assert.fail("expected protocol binding conflict");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("binding conflict"));
        }
        Assert.assertEquals(0, submissions.get());
    }

    @Test
    public void sameAddressWithDifferentTypeOrCidIsRejected() {
        JSONObject wrongType = matchingState(356501159937L, 0);
        wrongType.getJSONObject("subProtocols")
                .getJSONObject("0x0000005301200001:contract")
                .put("protocolType", 1);
        DioxideClient.ProtocolBindingInspection typeInspection =
                DioxideClient.inspectProtocolBinding(wrongType, 356501159937L, 0);

        Assert.assertEquals(DioxideClient.ProtocolBindingState.CONFLICT, typeInspection.state());

        JSONObject wrongCid = matchingState(356501159937L, 0);
        wrongCid.getJSONObject("subProtocols")
                .getJSONObject("0x0000005301200001:contract")
                .put("protocolID", 356501159938L);
        DioxideClient.ProtocolBindingInspection cidInspection =
                DioxideClient.inspectProtocolBinding(wrongCid, 356501159937L, 0);

        Assert.assertEquals(DioxideClient.ProtocolBindingState.CONFLICT, cidInspection.state());
        Assert.assertTrue(cidInspection.detail().contains("binding conflict"));
    }

    @Test
    public void partialBindingIsRejected() {
        JSONObject state = new JSONObject();
        JSONObject routes = new JSONObject();
        routes.put("0", "0x0000005301200001:contract");
        state.put("protocolRoutes", routes);
        state.put("protocolIDs", new JSONObject());
        state.put("subProtocols", new JSONObject());

        DioxideClient.ProtocolBindingInspection inspection =
                DioxideClient.inspectProtocolBinding(state, 356501159937L, 0);

        Assert.assertEquals(DioxideClient.ProtocolBindingState.CONFLICT, inspection.state());
    }

    @Test
    public void orphanSubProtocolForSameTypeIsRejected() {
        JSONObject state = matchingState(356501159938L, 0);
        state.put("protocolRoutes", new JSONObject());
        state.put("protocolIDs", new JSONObject());

        DioxideClient.ProtocolBindingInspection inspection =
                DioxideClient.inspectProtocolBinding(state, 356501159937L, 0);

        Assert.assertEquals(DioxideClient.ProtocolBindingState.CONFLICT, inspection.state());
        Assert.assertTrue(inspection.detail().contains("relatedSubProtocols=1"));
    }

    @Test
    public void submissionFailureConvergesWhenConcurrentCallerCompletesBinding() throws Exception {
        AtomicReference<JSONObject> state = new AtomicReference<>(new JSONObject());

        String txHash = DioxideClient.ensureProtocolBinding(
                356501159937L,
                0,
                state::get,
                () -> {
                    state.set(matchingState(356501159937L, 0));
                    throw new RuntimeException("simulated duplicate submission");
                }
        );

        Assert.assertNull(txHash);
    }

    @Test
    public void submissionFailureIsPreservedWhenBindingRemainsAbsent() {
        RuntimeException expected = new RuntimeException("simulated transport failure");

        try {
            DioxideClient.ensureProtocolBinding(
                    356501159937L,
                    0,
                    JSONObject::new,
                    () -> {
                        throw expected;
                    }
            );
            Assert.fail("expected original submission failure");
        } catch (Exception actual) {
            Assert.assertSame(expected, actual);
        }
    }

    @Test
    public void livePredeployedBindingIsReadOnlyAndRejectsConflictBeforeSubmission() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("dioxide.protocol.live"));
        String configPath = System.getProperty("dioxide.protocol.live.config");
        Assume.assumeTrue(configPath != null && !configPath.isBlank());

        DioxideConfig config = DioxideConfig.fromJsonString(Files.readString(Path.of(configPath)));
        liveClient = new DioxideClient(config, NOPLogger.NOP_LOGGER);
        String account = liveClient.getDioxideAccount().getAddressInString();
        long before = currentIsn(config.getRpcUrl(), account);

        liveClient.setProtocolToAuthMsg(config.getSdpContractAddressDeployed(), "0");
        Assert.assertEquals(before, currentIsn(config.getRpcUrl(), account));

        try {
            liveClient.setProtocolToAuthMsg(
                    Long.toString(Long.parseLong(config.getSdpContractAddressDeployed()) + 1L),
                    "0"
            );
            Assert.fail("expected conflicting protocol binding");
        } catch (RuntimeException e) {
            Assert.assertTrue(rootMessage(e).contains("binding conflict"));
        }
        Assert.assertEquals(before, currentIsn(config.getRpcUrl(), account));
    }

    private static JSONObject matchingState(long protocolCid, int protocolType) {
        String address = String.format("0x%016X:contract", protocolCid);
        JSONObject route = new JSONObject();
        route.put(Integer.toString(protocolType), address);
        JSONObject ids = new JSONObject();
        ids.put(Integer.toString(protocolType), protocolCid);
        JSONObject binding = new JSONObject();
        binding.put("protocolType", protocolType);
        binding.put("protocolID", protocolCid);
        binding.put("protocolAddress", address);
        binding.put("exist", true);
        JSONObject subProtocols = new JSONObject();
        subProtocols.put(address, binding);
        JSONObject state = new JSONObject();
        state.put("protocolRoutes", route);
        state.put("protocolIDs", ids);
        state.put("subProtocols", subProtocols);
        return state;
    }

    private static long currentIsn(String rpcUrl, String account) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(rpcUrl + "?req=dx.isn"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"address\":\"" + account + "\"}"))
                .build();
        String body = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString())
                .body();
        return JSON.parseObject(body).getJSONObject("ret").getLongValue("ISN");
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
