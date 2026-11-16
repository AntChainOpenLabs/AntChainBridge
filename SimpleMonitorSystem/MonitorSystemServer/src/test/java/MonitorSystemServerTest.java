import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.commons.core.base.UniformCrosschainPacket;
import com.alipay.antchain.bridge.ptc.committee.monitor.system.grpc.MonitorSystemResponse;
import com.alipay.antchain.bridge.ptc.committee.monitor.system.grpc.RelayUcpToMonitorSystemRequest;
import com.alipay.antchain.bridge.ptc.committee.monitor.system.grpc.VerifyCrossChainMessageInMonitorSystemRequest;
import com.google.protobuf.ByteString;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class MonitorSystemServerTest {

    private MonitorSystemServer server;

    private MonitorSystemServer.MonitorSystemServiceImpl service;

    private byte[] validRawUcp;

    @Before
    public void setUp() {
        server = new MonitorSystemServer();
        service = server.new MonitorSystemServiceImpl();
        validRawUcp = new UniformCrosschainPacket(
                new CrossChainDomain("test-domain"),
                CrossChainMessage.createCrossChainMessage(
                        CrossChainMessage.CrossChainMessageType.DEVELOPER_DESIGN,
                        1L,
                        1L,
                        new byte[]{1},
                        new byte[]{2},
                        new byte[]{3},
                        new byte[]{4},
                        new byte[]{5}
                ),
                null
        ).encode();
    }

    @Test
    public void testVerifySuccessAndFailureResponses() {
        VerifyCrossChainMessageInMonitorSystemRequest request = verifyRequest(validRawUcp);

        server.setVerifyMode(MonitorSystemServer.VerifyMode.SUCCESS);
        MonitorSystemResponse response = service.buildVerifyResponse(request);
        Assert.assertEquals(0, response.getCode());
        Assert.assertEquals("", response.getErrorMsg());
        Assert.assertTrue(response.hasVerifyCrossChainMessageInMonitorSystemResp());
        Assert.assertEquals(0, response.getVerifyCrossChainMessageInMonitorSystemResp().getResult());
        Assert.assertEquals("ok", response.getVerifyCrossChainMessageInMonitorSystemResp().getMsg());

        server.setVerifyMode(MonitorSystemServer.VerifyMode.FAILURE);
        response = service.buildVerifyResponse(request);
        Assert.assertEquals(0, response.getCode());
        Assert.assertEquals("", response.getErrorMsg());
        Assert.assertTrue(response.hasVerifyCrossChainMessageInMonitorSystemResp());
        Assert.assertEquals(1, response.getVerifyCrossChainMessageInMonitorSystemResp().getResult());
        Assert.assertFalse(response.getVerifyCrossChainMessageInMonitorSystemResp().getMsg().isEmpty());
    }

    @Test
    public void testInvalidRawUcpReturns400WithoutVerifyResponse() {
        MonitorSystemResponse response = service.buildVerifyResponse(verifyRequest(new byte[]{1, 2, 3, 4}));

        Assert.assertEquals(400, response.getCode());
        Assert.assertTrue(response.getErrorMsg().contains("failed to decode rawUcp"));
        Assert.assertFalse(response.hasVerifyCrossChainMessageInMonitorSystemResp());
    }

    @Test
    public void testForced500And503ResponsesDoNotContainVerifyResponse() {
        VerifyCrossChainMessageInMonitorSystemRequest request = verifyRequest(validRawUcp);

        server.setVerifyMode(MonitorSystemServer.VerifyMode.INTERNAL_ERROR);
        MonitorSystemResponse response = service.buildVerifyResponse(request);
        Assert.assertEquals(500, response.getCode());
        Assert.assertFalse(response.getErrorMsg().isEmpty());
        Assert.assertFalse(response.hasVerifyCrossChainMessageInMonitorSystemResp());

        server.setVerifyMode(MonitorSystemServer.VerifyMode.UNAVAILABLE);
        response = service.buildVerifyResponse(request);
        Assert.assertEquals(503, response.getCode());
        Assert.assertFalse(response.getErrorMsg().isEmpty());
        Assert.assertFalse(response.hasVerifyCrossChainMessageInMonitorSystemResp());
    }

    @Test
    public void testRelayAlwaysReturnsSuccessWithoutVerifyResponse() {
        for (byte[] rawUcp : new byte[][]{validRawUcp, new byte[]{1, 2, 3, 4}}) {
            MonitorSystemResponse response = service.buildRelayResponse(
                    RelayUcpToMonitorSystemRequest.newBuilder()
                            .setRawUcp(ByteString.copyFrom(rawUcp))
                            .setUcpId("ucp-id")
                            .build()
            );
            Assert.assertEquals(0, response.getCode());
            Assert.assertEquals("", response.getErrorMsg());
            Assert.assertFalse(response.hasVerifyCrossChainMessageInMonitorSystemResp());
        }

        MonitorSystemResponse responseAfterInternalError = service.buildRelayResponse(null);
        Assert.assertEquals(0, responseAfterInternalError.getCode());
        Assert.assertEquals("", responseAfterInternalError.getErrorMsg());
        Assert.assertFalse(responseAfterInternalError.hasVerifyCrossChainMessageInMonitorSystemResp());
    }

    @Test
    public void testDelayAppliesOnlyToVerify() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger requestedMillis = new AtomicInteger();
        MonitorSystemServer delayedServer = new MonitorSystemServer(37L, delayMillis -> {
            calls.incrementAndGet();
            requestedMillis.set((int) delayMillis);
        });
        MonitorSystemServer.MonitorSystemServiceImpl delayedService =
                delayedServer.new MonitorSystemServiceImpl();

        delayedService.buildVerifyResponse(verifyRequest(validRawUcp));
        Assert.assertEquals(1, calls.get());
        Assert.assertEquals(37, requestedMillis.get());

        delayedService.buildRelayResponse(
                RelayUcpToMonitorSystemRequest.newBuilder()
                        .setRawUcp(ByteString.copyFrom(validRawUcp))
                        .setUcpId("ucp-id")
                        .build()
        );
        Assert.assertEquals(1, calls.get());
    }

    @Test
    public void testDelayPropertyDefaultsToZeroAndRejectsInvalidValues() {
        String original = System.getProperty(MonitorSystemServer.VERIFY_DELAY_PROPERTY);
        try {
            System.clearProperty(MonitorSystemServer.VERIFY_DELAY_PROPERTY);
            Assert.assertEquals(0L, MonitorSystemServer.readVerifyDelayMillis());

            System.setProperty(MonitorSystemServer.VERIFY_DELAY_PROPERTY, "-1");
            assertInvalidDelayProperty();

            System.setProperty(MonitorSystemServer.VERIFY_DELAY_PROPERTY, "not-a-number");
            assertInvalidDelayProperty();
        } finally {
            if (original == null) {
                System.clearProperty(MonitorSystemServer.VERIFY_DELAY_PROPERTY);
            } else {
                System.setProperty(MonitorSystemServer.VERIFY_DELAY_PROPERTY, original);
            }
        }
    }

    private void assertInvalidDelayProperty() {
        try {
            MonitorSystemServer.readVerifyDelayMillis();
            Assert.fail("expected invalid delay property to be rejected");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains(MonitorSystemServer.VERIFY_DELAY_PROPERTY));
        }
    }

    private VerifyCrossChainMessageInMonitorSystemRequest verifyRequest(byte[] rawUcp) {
        return VerifyCrossChainMessageInMonitorSystemRequest.newBuilder()
                .setRawUcp(ByteString.copyFrom(rawUcp))
                .setUcpId("ucp-id")
                .build();
    }
}
