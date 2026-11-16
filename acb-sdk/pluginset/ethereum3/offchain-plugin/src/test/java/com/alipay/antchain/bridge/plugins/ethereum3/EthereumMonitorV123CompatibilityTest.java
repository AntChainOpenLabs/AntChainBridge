package com.alipay.antchain.bridge.plugins.ethereum3;

import com.alipay.antchain.bridge.plugins.ethereum3.abi.AppContract;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.Monitor;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.SDPMsg;
import org.junit.Assert;
import org.junit.Test;

public class EthereumMonitorV123CompatibilityTest {

    @Test
    public void generatedSdpRoutesV2AndV3ThroughMonitor() {
        Assert.assertEquals("getMonitorRoutingVersion", SDPMsg.FUNC_GETMONITORROUTINGVERSION);
        Assert.assertEquals("sendMessageV2FromMonitor", SDPMsg.FUNC_SENDMESSAGEV2FROMMONITOR);
        Assert.assertEquals("sendUnorderedMessageV2FromMonitor", SDPMsg.FUNC_SENDUNORDEREDMESSAGEV2FROMMONITOR);
        Assert.assertEquals("sendMessageV3FromMonitor", SDPMsg.FUNC_SENDMESSAGEV3FROMMONITOR);
        Assert.assertEquals("sendUnorderedMessageV3FromMonitor", SDPMsg.FUNC_SENDUNORDEREDMESSAGEV3FROMMONITOR);
        Assert.assertFalse(SDPMsg.BINARY.isEmpty());
    }

    @Test
    public void generatedMonitorUnwrapsEveryRequestAndAckVersion() {
        Assert.assertEquals("recvMessageFromSDP", Monitor.FUNC_RECVMESSAGEFROMSDP);
        Assert.assertEquals("recvUnorderedMessageFromSDP", Monitor.FUNC_RECVUNORDEREDMESSAGEFROMSDP);
        Assert.assertEquals("recvMessageV2FromSDP", Monitor.FUNC_RECVMESSAGEV2FROMSDP);
        Assert.assertEquals("recvUnorderedMessageV2FromSDP", Monitor.FUNC_RECVUNORDEREDMESSAGEV2FROMSDP);
        Assert.assertEquals("recvMessageV3FromSDP", Monitor.FUNC_RECVMESSAGEV3FROMSDP);
        Assert.assertEquals("recvUnorderedMessageV3FromSDP", Monitor.FUNC_RECVUNORDEREDMESSAGEV3FROMSDP);
        Assert.assertEquals("ackOnSuccessFromSDP", Monitor.FUNC_ACKONSUCCESSFROMSDP);
        Assert.assertEquals("ackOnErrorFromSDP", Monitor.FUNC_ACKONERRORFROMSDP);
        Assert.assertEquals("sendV3", AppContract.FUNC_SENDV3);
        Assert.assertEquals("sendUnorderedV3", AppContract.FUNC_SENDUNORDEREDV3);
    }
}
