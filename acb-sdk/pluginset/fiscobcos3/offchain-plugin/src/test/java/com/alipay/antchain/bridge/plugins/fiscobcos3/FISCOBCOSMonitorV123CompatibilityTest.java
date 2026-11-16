/*
 * Copyright 2023 Ant Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.alipay.antchain.bridge.plugins.fiscobcos3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.Monitor;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.MonitorVerifier;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.PtcHub;
import com.alipay.antchain.bridge.plugins.fiscobcos3.abi.SDPMsg;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

public class FISCOBCOSMonitorV123CompatibilityTest {

    @Test
    public void generatedContractsExposeMonitorRoutingForEverySdpVersion() throws Exception {
        Assert.assertTrue(SDPMsg.ABI_ARRAY.length > 1);
        Assert.assertTrue(new ObjectMapper().readTree(SDPMsg.ABI).isArray());
        Assert.assertTrue(SDPMsg.ABI.contains("getMonitorRoutingVersion"));
        Assert.assertTrue(SDPMsg.ABI.contains("setMonitorContract"));
        Assert.assertTrue(SDPMsg.ABI.contains("sendMessageV2"));
        Assert.assertTrue(SDPMsg.ABI.contains("sendMessageV3"));
        Assert.assertTrue(SDPMsg.ABI.contains("sendMessageV2FromMonitor"));
        Assert.assertTrue(SDPMsg.ABI.contains("sendMessageV3FromMonitor"));
        Assert.assertTrue(SDPMsg.ABI.contains("sendUnorderedMessageV2"));
        Assert.assertTrue(SDPMsg.ABI.contains("sendUnorderedMessageV3"));
        Assert.assertTrue(SDPMsg.ABI.contains("sendUnorderedMessageV2FromMonitor"));
        Assert.assertTrue(SDPMsg.ABI.contains("sendUnorderedMessageV3FromMonitor"));
        Assert.assertFalse(SDPMsg.BINARY.isEmpty());
    }

    @Test
    public void generatedMonitorSupportsExactReceiveSideUnwrapAndPtcVerifier() {
        Assert.assertTrue(Monitor.ABI.contains("getImplementationVersion"));
        Assert.assertTrue(Monitor.ABI.contains("recvMessage"));
        Assert.assertTrue(Monitor.ABI.contains("recvUnorderedMessage"));
        Assert.assertTrue(Monitor.ABI.contains("recvMessageV2FromSDP"));
        Assert.assertTrue(Monitor.ABI.contains("recvMessageV3FromSDP"));
        Assert.assertTrue(Monitor.ABI.contains("recvUnorderedMessageV2FromSDP"));
        Assert.assertTrue(Monitor.ABI.contains("recvUnorderedMessageV3FromSDP"));
        Assert.assertTrue(Monitor.ABI.contains("ackOnSuccessFromSDP"));
        Assert.assertTrue(Monitor.ABI.contains("ackOnErrorFromSDP"));
        Assert.assertTrue(Monitor.ABI.contains("setMonitorVerifier"));
        Assert.assertTrue(MonitorVerifier.ABI.contains("setPtcHubAddress"));
        Assert.assertTrue(PtcHub.ABI.contains("getMonitorVerifier"));
        Assert.assertTrue(PtcHub.ABI.contains("setMonitorVerifier"));
        Assert.assertFalse(Monitor.BINARY.isEmpty());
        Assert.assertFalse(MonitorVerifier.BINARY.isEmpty());
        Assert.assertFalse(PtcHub.BINARY.isEmpty());
    }

    @Test
    public void bbcMonitorControlUsesProtocolEnum() {
        Assert.assertEquals(
                BigInteger.ONE,
                FISCOBCOSBBCService.requireProtocolMonitorControl(1));
        Assert.assertEquals(
                BigInteger.valueOf(2),
                FISCOBCOSBBCService.requireProtocolMonitorControl(2));
        Assert.assertEquals(
                BigInteger.valueOf(3),
                FISCOBCOSBBCService.requireProtocolMonitorControl(3));
        try {
            FISCOBCOSBBCService.requireProtocolMonitorControl(0);
            Assert.fail("unsupported BBC monitor control must be rejected");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("expected 1"));
        }
    }

    @Test
    public void configPreservesExplicitPersistentAccountFile() throws Exception {
        FISCOBCOSConfig config = new FISCOBCOSConfig();
        config.setAccountFilePath("/run/secrets/fisco04/account.pem");
        config.setAccountPassword("test-password");

        FISCOBCOSConfig decoded = FISCOBCOSConfig.fromJsonString(config.toJsonString());
        Assert.assertEquals(config.getAccountFilePath(), decoded.getAccountFilePath());
        Assert.assertEquals(config.getAccountPassword(), decoded.getAccountPassword());
    }

    @Test
    public void sendAuthMessagePayloadSupportsBytesAndStringHex() {
        byte[] message = "fisco-auth-message".getBytes(StandardCharsets.UTF_8);
        Assert.assertArrayEquals(
                message,
                FISCOBCOSBBCService.decodeAuthMessageEventPayload(message));
        Assert.assertArrayEquals(
                message,
                FISCOBCOSBBCService.decodeAuthMessageEventPayload(
                        "0x666973636f2d617574682d6d657373616765"));

        try {
            FISCOBCOSBBCService.decodeAuthMessageEventPayload("not-hex");
            Assert.fail("malformed string event payload must be rejected");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("valid hex"));
        }
    }
}
