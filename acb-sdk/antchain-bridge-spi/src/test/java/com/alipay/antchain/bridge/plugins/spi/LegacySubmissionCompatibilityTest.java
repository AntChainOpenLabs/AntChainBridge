package com.alipay.antchain.bridge.plugins.spi;

import com.alipay.antchain.bridge.plugins.spi.bbc.core.write.IAMWriter;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class LegacySubmissionCompatibilityTest {
    @Test public void oldPluginsKeepTheirSingleArgumentImplementation() {
        AtomicInteger calls = new AtomicInteger();
        byte[] payload = new byte[]{1, 2, 3};
        CrossChainMessageReceipt expected = new CrossChainMessageReceipt();
        IAMWriter legacy = new IAMWriter() {
            public void setProtocol(String address, String type) { }
            public void setPtcContract(String address) { }
            public CrossChainMessageReceipt relayAuthMessage(byte[] message) {
                assertSame(payload, message); calls.incrementAndGet(); return expected;
            }
        };
        assertSame(expected, legacy.relayAuthMessage(payload, "stable-operation"));
        assertSame(expected, legacy.relayAuthMessage(payload, null));
        assertEquals(2, calls.get());
    }
}
