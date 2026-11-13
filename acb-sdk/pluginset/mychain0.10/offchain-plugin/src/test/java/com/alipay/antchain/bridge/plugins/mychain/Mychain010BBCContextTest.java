package com.alipay.antchain.bridge.plugins.mychain;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.AuthMessageContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.bbc.syscontract.PTCContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.SDPContract;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTypeEnum;
import com.alipay.antchain.bridge.plugins.mychain.contract.PtcContractEvm;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class Mychain010BBCContextTest {

    /** Pure offline probe: a getter records the read without any SDK client. */
    public static class CountingPtcClient extends PtcContractEvm {
        final AtomicInteger verifierReads = new AtomicInteger();
        final AtomicInteger typeReads = new AtomicInteger();

        public CountingPtcClient() {
            super(null, LoggerFactory.getLogger(CountingPtcClient.class));
        }

        @Override
        public String getCommitteeVerifier() {
            verifierReads.incrementAndGet();
            return "offline-verifier";
        }

        @Override
        public Set<PTCTypeEnum> getSupportedPTCTypes() {
            typeReads.incrementAndGet();
            return Collections.singleton(PTCTypeEnum.COMMITTEE);
        }
    }

    /** The old context exposed the runtime client to inherited serialization. */
    public static class LegacyContext extends AbstractBBCContext {
        private final CountingPtcClient client;

        public LegacyContext(CountingPtcClient client) {
            this.client = client;
        }

        public PtcContractEvm getPtcContractEvm() {
            return client;
        }
    }

    private DefaultBBCContext portableContext() {
        DefaultBBCContext base = new DefaultBBCContext();
        base.setAuthMessageContract(new AuthMessageContract("am-name", ContractStatusEnum.CONTRACT_READY));
        base.setSdpContract(new SDPContract("sdp-name", ContractStatusEnum.CONTRACT_READY));
        PTCContract ptc = new PTCContract();
        ptc.setContractAddress("ptc-name");
        ptc.setStatus(ContractStatusEnum.CONTRACT_READY);
        base.setPtcContract(ptc);
        base.setConfForBlockchainClient("{\"offline\":true}".getBytes(StandardCharsets.UTF_8));
        base.setReliable(true);
        return base;
    }

    @Test
    public void legacySerializationInvokesReadOnlyContractGetters() {
        CountingPtcClient client = new CountingPtcClient();
        JSONObject json = JSON.parseObject(new String(new LegacyContext(client).encodeToBytes(), StandardCharsets.UTF_8));
        Assert.assertTrue(json.containsKey("ptcContractEvm"));
        Assert.assertEquals(1, client.verifierReads.get());
        Assert.assertEquals(1, client.typeReads.get());
    }

    @Test
    public void portableSerializationPreservesConfigurationAndDoesNotReadChain() {
        DefaultBBCContext expected = portableContext();
        Mychain010BBCContext context = new Mychain010BBCContext(expected, LoggerFactory.getLogger(getClass()));
        CountingPtcClient client = new CountingPtcClient();
        context.setPtcContractEvm(client);
        context.setLatestBlockTimestamp(123L);
        context.setNonceBefore(456L);
        context.setFixedDelay(789L);

        byte[] encoded = context.encodeToBytes();
        Assert.assertArrayEquals(expected.encodeToBytes(), encoded);
        JSONObject json = JSON.parseObject(new String(encoded, StandardCharsets.UTF_8));
        for (String runtime : Arrays.asList("ptcContractEvm", "amContractClientEVM", "sdpContractClientEVM",
                "logger", "latestBlockTimestamp", "fixedDelay", "nonceBefore")) {
            Assert.assertFalse(runtime, json.containsKey(runtime));
        }
        DefaultBBCContext restored = new DefaultBBCContext();
        restored.decodeFromBytes(encoded);
        Assert.assertEquals("am-name", restored.getAuthMessageContract().getContractAddress());
        Assert.assertEquals("sdp-name", restored.getSdpContract().getContractAddress());
        Assert.assertEquals("ptc-name", restored.getPtcContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, restored.getPtcContract().getStatus());
        Assert.assertArrayEquals(expected.getConfForBlockchainClient(), restored.getConfForBlockchainClient());
        Assert.assertTrue(restored.isReliable());
        Assert.assertEquals(0, client.verifierReads.get());
        Assert.assertEquals(0, client.typeReads.get());
        Assert.assertSame(client, context.getPtcContractEvm());
        Assert.assertEquals(123L, context.getLatestBlockTimestamp());
        Assert.assertEquals(456L, context.getNonceBefore());
    }

    @Test
    public void emptyContextCanBeEncodedWithoutInitializingSdk() {
        Mychain010BBCContext context = new Mychain010BBCContext(new DefaultBBCContext(), LoggerFactory.getLogger(getClass()));
        Assert.assertArrayEquals(new DefaultBBCContext().encodeToBytes(), context.encodeToBytes());
    }

    @Test
    public void concurrentEncodingNeverTouchesRuntimeGetters() throws Exception {
        DefaultBBCContext expected = portableContext();
        Mychain010BBCContext context = new Mychain010BBCContext(expected, LoggerFactory.getLogger(getClass()));
        CountingPtcClient client = new CountingPtcClient();
        context.setPtcContractEvm(client);
        byte[] bytes = expected.encodeToBytes();
        ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            Callable<Boolean> encode = () -> Arrays.equals(bytes, context.encodeToBytes());
            List<Future<Boolean>> results = executor.invokeAll(Collections.nCopies(150, encode));
            for (Future<Boolean> result : results) {
                Assert.assertTrue(result.get());
            }
        } finally {
            executor.shutdownNow();
        }
        Assert.assertEquals(0, client.verifierReads.get());
        Assert.assertEquals(0, client.typeReads.get());
    }
}
