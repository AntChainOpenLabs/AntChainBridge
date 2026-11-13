package com.alipay.antchain.bridge.plugins.dioxide2.core;

import org.junit.Assert;
import org.junit.Test;

public class DioxideClientStorageKeyTest {

    @Test
    public void derivesTheSameLowercaseMapKeyAsDioxide() {
        Assert.assertEquals(
                "0e85hhqjr35mjb2k7c54t57fez60yy5bsk7daa3xgjgt408wze0g",
                DioxideClient.ptcStorageKey(new byte[]{1, 2, 3})
        );
    }
}
