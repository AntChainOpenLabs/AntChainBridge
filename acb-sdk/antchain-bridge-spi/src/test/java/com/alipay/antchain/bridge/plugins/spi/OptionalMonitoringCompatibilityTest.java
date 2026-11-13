package com.alipay.antchain.bridge.plugins.spi;

import com.alipay.antchain.bridge.plugins.spi.bbc.core.write.*;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import org.junit.Test;
import static org.junit.Assert.*;

public class OptionalMonitoringCompatibilityTest {
    @Test public void legacyImplementationsNeedNoMonitoringOverrides() {
        IMonitorWriter legacy = new IMonitorWriter() { };
        assertThrows(UnsupportedOperationException.class, () -> legacy.setProtocolInMonitor("monitor"));
        assertThrows(UnsupportedOperationException.class, () -> legacy.setMonitorControl(2));
        assertThrows(UnsupportedOperationException.class, () -> legacy.setPtcHubInMonitorVerifier("hub"));
        assertThrows(UnsupportedOperationException.class, () -> legacy.relayMonitorOrder("id", "algo", new byte[0], new byte[0]));
        ISDPWriter sdp = new ISDPWriter() {
            public void setAmContract(String address) { }
            public void setLocalDomain(String domain) { }
        };
        assertThrows(UnsupportedOperationException.class, () -> sdp.setMonitorContract("monitor"));
    }
    @Test public void upstreamVersionDetectionDefaultIsPreserved() throws Exception {
        assertTrue(IVerifierWriter.class.getMethod("updatePTCTrustRoot", PTCTrustRoot.class).isDefault());
        assertTrue(IAntChainBridgeDataWriter.class.getMethod("setupMonitorContract").isDefault());
    }
}
