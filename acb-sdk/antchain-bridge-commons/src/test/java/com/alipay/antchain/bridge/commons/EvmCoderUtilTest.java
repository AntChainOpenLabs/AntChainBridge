package com.alipay.antchain.bridge.commons;

import java.nio.ByteOrder;

import cn.hutool.core.util.ByteUtil;
import com.alipay.antchain.bridge.commons.utils.codec.CoderResult;
import com.alipay.antchain.bridge.commons.utils.codec.EvmCoderUtil;
import org.junit.Assert;
import org.junit.Test;

public class EvmCoderUtilTest {

    @Test
    public void testRoundTripVarBytes() {
        byte[] input = new byte[33];
        input[0] = 1;
        input[32] = 2;
        byte[] encoded = new byte[96];
        int offset = EvmCoderUtil.sinkVarBytes(input, encoded, encoded.length);

        CoderResult<byte[]> decoded = EvmCoderUtil.parseVarBytes(encoded, encoded.length);
        Assert.assertEquals(offset, decoded.getOffset());
        Assert.assertArrayEquals(input, decoded.getResult());
    }

    @Test
    public void testRejectsLengthLargerThanInputBeforeAllocation() {
        byte[] malformed = new byte[32];
        System.arraycopy(
                ByteUtil.intToBytes(Integer.MAX_VALUE, ByteOrder.BIG_ENDIAN),
                0,
                malformed,
                28,
                4
        );

        try {
            EvmCoderUtil.parseVarBytes(malformed, malformed.length);
            Assert.fail("expected malformed length to be rejected");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("exceeds available input"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRejectsInvalidOffset() {
        EvmCoderUtil.parseVarBytes(new byte[32], 31);
    }
}
