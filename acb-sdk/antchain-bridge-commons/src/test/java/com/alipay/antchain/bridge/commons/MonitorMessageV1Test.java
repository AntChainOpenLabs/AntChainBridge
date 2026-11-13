package com.alipay.antchain.bridge.commons;

import com.alipay.antchain.bridge.commons.core.monitor.MonitorMessageV1;
import com.alipay.antchain.bridge.commons.exception.AntChainBridgeCommonsException;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MonitorMessageV1Test {

    @Test
    public void shouldRoundTripExactPayloadAcrossEvmWordBoundaries() {
        for (int length : new int[]{0, 1, 31, 32, 33, 63, 64, 65, 257}) {
            byte[] payload = new byte[length];
            for (int index = 0; index < payload.length; index++) {
                payload[index] = (byte) (index * 31 + 7);
            }

            MonitorMessageV1 encoded = new MonitorMessageV1();
            encoded.setMonitorType(2);
            encoded.setMonitorMsg("monitor-v1-监管");
            encoded.setPayload(payload);

            MonitorMessageV1 decoded = new MonitorMessageV1();
            decoded.decode(encoded.encode());

            Assert.assertEquals(2, decoded.getMonitorType());
            Assert.assertEquals("monitor-v1-监管", decoded.getMonitorMsg());
            Assert.assertArrayEquals(payload, decoded.getPayload());
        }
    }

    @Test
    public void payloadMustNotContainEnvelopeOrPadding() {
        byte[] payload = "business-payload".getBytes(StandardCharsets.UTF_8);
        MonitorMessageV1 encoded = new MonitorMessageV1();
        encoded.setMonitorType(1);
        encoded.setMonitorMsg("");
        encoded.setPayload(payload);

        byte[] envelope = encoded.encode();
        Assert.assertTrue(envelope.length > payload.length);
        Assert.assertFalse(Arrays.equals(envelope, payload));

        MonitorMessageV1 decoded = new MonitorMessageV1();
        decoded.decode(envelope);
        Assert.assertArrayEquals(payload, decoded.getPayload());
    }

    @Test(expected = AntChainBridgeCommonsException.class)
    public void shouldRejectTruncatedEnvelope() {
        new MonitorMessageV1().decode(new byte[67]);
    }

    @Test(expected = AntChainBridgeCommonsException.class)
    public void shouldRejectUnknownMonitorType() {
        MonitorMessageV1 encoded = new MonitorMessageV1();
        encoded.setMonitorType(99);
        encoded.setMonitorMsg("");
        encoded.setPayload(new byte[0]);

        new MonitorMessageV1().decode(encoded.encode());
    }

    @Test(expected = AntChainBridgeCommonsException.class)
    public void shouldRejectTrailingEnvelopeData() {
        MonitorMessageV1 encoded = new MonitorMessageV1();
        encoded.setMonitorType(1);
        encoded.setMonitorMsg("");
        encoded.setPayload("payload".getBytes(StandardCharsets.UTF_8));
        byte[] valid = encoded.encode();
        byte[] withTrailingPrefix = new byte[valid.length + 32];
        System.arraycopy(valid, 0, withTrailingPrefix, 32, valid.length);

        new MonitorMessageV1().decode(withTrailingPrefix);
    }
}
