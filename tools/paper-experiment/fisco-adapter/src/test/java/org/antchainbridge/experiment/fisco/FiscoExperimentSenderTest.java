package org.antchainbridge.experiment.fisco;

import org.junit.Assert;
import org.junit.Test;

public class FiscoExperimentSenderTest {

    @Test
    public void shouldSelectBaseAndRegulatedProducts() {
        Assert.assertEquals(
                FiscoExperimentSender.FiscoVariant.BASE,
                FiscoExperimentSender.requireSourceProduct("fiscobcos"));
        Assert.assertEquals(
                FiscoExperimentSender.FiscoVariant.XUN,
                FiscoExperimentSender.requireSourceProduct("fiscobcos3"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectAmbiguousFiscoProduct() {
        FiscoExperimentSender.requireSourceProduct("fiscobcos2");
    }
}
