package com.alipay.antchain.bridge.plugins.dioxide;

import com.alipay.antchain.bridge.plugins.dioxide.conf.DioxideConfig;
import com.alipay.antchain.bridge.plugins.dioxide.core.DioxideClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class DioxidePlainConsensusTest {
    @Test public void consensusReaderPreservesNativeHashAndNeverSubmits() throws Exception {
        AtomicInteger writes = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api", exchange -> {
            String method=exchange.getRequestURI().getQuery();
            exchange.getRequestBody().readAllBytes();
            String value="{}";
            if(method.contains("tx.")) writes.incrementAndGet();
            if(method.contains("dx.transaction_block")) value="{\"Height\":12,\"Hash\":\"native-hash\",\"Prev\":\"native-parent\",\"Timestamp\":123}";
            if(method.contains("dx.contract_state")) value="{\"State\":{\"amContractId\":9,\"amAddress\":\"0x0000000000000009:contract\",\"localDomain\":[100,105,111,120,98,97,115,101,48,49],\"protocolIDs\":{\"0\":9},\"protocolRoutes\":{\"0\":\"0x0000000000000009:contract\"}}}";
            if(method.contains("dx.consensus_header")) value="{\"Timestamp\":124,\"Hash\":\"header\"}";
            byte[] bytes=("{\"ret\":"+value+"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);
            try(var out=exchange.getResponseBody()){out.write(bytes);}
        });
        server.start();
        DioxideClient client=null;
        try {
            DioxideConfig config=new DioxideConfig();
            config.setRpcUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/api");
            config.setPrivateKey(Base64.getEncoder().encodeToString(new byte[32]));
            config.setDappName("ict214");
            client=new DioxideClient(config,org.slf4j.helpers.NOPLogger.NOP_LOGGER);
            DioxideBBCService service=new DioxideBBCService();service.setDioxideClient(client);
            var state=service.readConsensusState(BigInteger.valueOf(12));
            assertEquals(BigInteger.valueOf(12),state.getHeight());
            assertArrayEquals("native-hash".getBytes(StandardCharsets.UTF_8),state.getHash());
            assertArrayEquals("native-parent".getBytes(StandardCharsets.UTF_8),state.getParentHash());
            assertEquals(123,state.getStateTimestamp());
            assertThrows(IllegalArgumentException.class,()->service.readConsensusState(null));
            assertThrows(IllegalArgumentException.class,()->service.readConsensusState(BigInteger.valueOf(-1)));
            assertThrows(UnsupportedOperationException.class,service::setupPTCContract);
            assertThrows(UnsupportedOperationException.class,()->service.setPtcContract("fake"));
            client.setProtocolToAuthMsg("9","0");
            client.setAmContractToSdp("9");
            client.setLocalDomainToSdp("dioxbase01");
            final DioxideClient currentClient=client;
            assertThrows(IllegalStateException.class,()->currentClient.setProtocolToAuthMsg("10","0"));
            assertThrows(IllegalStateException.class,()->currentClient.setAmContractToSdp("10"));
            assertEquals(0,writes.get());
        } finally {if(client!=null)client.shutdown();server.stop(0);}
    }
}
