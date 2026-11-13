package com.alipay.antchain.bridge.plugins.dioxide2.core;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;

public class DioxideTransactionGasOfferedTest {
    @Test
    public void parsesUnsigned32BitGasLimitWithoutOverflow() {
        DioxideTransaction transaction = JSON.parseObject(
                "{\"GasOffered\":4294967295,\"State\":\"DUS_ARCHIVED\"}",
                DioxideTransaction.class);
        Assert.assertEquals(Long.valueOf(4294967295L), transaction.getGasOffered());
        Assert.assertEquals("DUS_ARCHIVED", transaction.getState());
    }

    @Test
    public void retainsCompatibilityWithSmallAndMissingGasLimits() {
        Assert.assertEquals(Long.valueOf(21000L), JSON.parseObject(
                "{\"GasOffered\":21000}", DioxideTransaction.class).getGasOffered());
        Assert.assertNull(JSON.parseObject("{}", DioxideTransaction.class).getGasOffered());
    }
}
