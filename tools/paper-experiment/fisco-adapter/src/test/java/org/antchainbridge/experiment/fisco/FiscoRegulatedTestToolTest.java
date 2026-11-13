package org.antchainbridge.experiment.fisco;

import org.junit.Assert;
import org.junit.Test;

public class FiscoRegulatedTestToolTest {

    @Test
    public void shouldNormaliseFiscoAddress() {
        Assert.assertEquals(
                "0x0123456789abcdef0123456789abcdef01234567",
                FiscoRegulatedTestTool.normaliseAddress("0X0123456789ABCDEF0123456789ABCDEF01234567"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectCrossChainIdentityAsAddress() {
        FiscoRegulatedTestTool.normaliseAddress(
                "0000000000000000000000000123456789abcdef0123456789abcdef01234567");
    }
}
