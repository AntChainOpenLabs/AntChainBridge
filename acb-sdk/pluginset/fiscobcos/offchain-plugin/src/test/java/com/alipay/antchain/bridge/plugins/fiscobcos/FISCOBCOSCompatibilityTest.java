package com.alipay.antchain.bridge.plugins.fiscobcos;

import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.plugins.fiscobcos.abi.CommitteePtcVerifier;
import com.alipay.antchain.bridge.plugins.fiscobcos.abi.PtcHub;
import com.alipay.antchain.bridge.plugins.fiscobcos.abi.SDPMsg;
import org.junit.Assert;
import org.junit.Test;

public class FISCOBCOSCompatibilityTest {

    @Test
    public void shouldExposeCompletePtcAbiWithoutMonitorRouting() {
        Assert.assertFalse(JSON.parseArray(SDPMsg.ABI).isEmpty());
        Assert.assertFalse(JSON.parseArray(PtcHub.ABI).isEmpty());
        Assert.assertFalse(JSON.parseArray(CommitteePtcVerifier.ABI).isEmpty());
        Assert.assertFalse(SDPMsg.ABI.contains("setMonitorContract"));
        Assert.assertFalse(PtcHub.ABI.contains("MonitorVerifier"));
        Assert.assertTrue(CommitteePtcVerifier.ABI.contains("verifyTpProof"));
    }

    @Test
    public void shouldDecodeCurrentAndLegacyAuthMessageEvents() {
        byte[] expected = new byte[] {0x01, 0x23, (byte) 0xab};
        Assert.assertArrayEquals(
                expected,
                FISCOBCOSBBCService.decodeAuthMessageEventPayload(expected));
        Assert.assertArrayEquals(
                expected,
                FISCOBCOSBBCService.decodeAuthMessageEventPayload("0x0123ab"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectMalformedLegacyAuthMessageEvent() {
        FISCOBCOSBBCService.decodeAuthMessageEventPayload("not-hex");
    }

    @Test
    public void shouldRetainExplicitAccountConfiguration() throws Exception {
        FISCOBCOSConfig config = FISCOBCOSConfig.fromJsonString(
                "{\"accountFilePath\":\"/secure/account.pem\",\"accountPassword\":\"secret\"}");
        Assert.assertEquals("/secure/account.pem", config.getAccountFilePath());
        Assert.assertEquals("secret", config.getAccountPassword());
    }
}
