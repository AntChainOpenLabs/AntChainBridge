package com.alipay.antchain.bridge.pluginserver.server;

import com.alipay.antchain.bridge.plugins.spi.bbc.IBBCService;
import com.alipay.antchain.bridge.pluginserver.service.RelayAuthMessageRequest;
import com.alipay.antchain.bridge.pluginserver.service.Response;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class StableSubmissionForwardingTest {
    @Test public void forwardsStableIdentityWithoutDerivingItFromPayload() throws Exception {
        for (String id : new String[]{"ucp-a-target-x", "ucp-b-target-x", ""}) {
            IBBCService plugin = mock(IBBCService.class);
            byte[] payload = new byte[]{1,2,3};
            CrossChainMessageReceipt receipt = new CrossChainMessageReceipt();
            receipt.setTxhash("native-hash");
            when(plugin.relayAuthMessage(payload, id)).thenReturn(receipt);
            Method method = CrossChainServiceImpl.class.getDeclaredMethod("handleRelayAuthMessage",
                    IBBCService.class, RelayAuthMessageRequest.class, String.class, String.class);
            method.setAccessible(true);
            Response response = (Response) method.invoke(new CrossChainServiceImpl(), plugin,
                    RelayAuthMessageRequest.newBuilder().setRawMessage(ByteString.copyFrom(payload)).setSubmissionId(id).build(),
                    "offline", "target");
            assertEquals(0, response.getCode());
            verify(plugin).relayAuthMessage(payload, id);
            verify(plugin, never()).relayAuthMessage(payload);
        }
    }
}
