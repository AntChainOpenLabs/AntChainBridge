/*
 * Copyright 2023 Ant Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alipay.antchain.bridge.commons.utils.codec;

import java.nio.ByteOrder;

import cn.hutool.core.util.ByteUtil;

public class EvmCoderUtil {

    public static CoderResult<byte[]> parseVarBytes(byte[] rawMessage, int offset) {
        if (rawMessage == null) {
            throw new IllegalArgumentException("raw message is null");
        }
        if (offset < 32 || offset > rawMessage.length) {
            throw new IllegalArgumentException("invalid EVM var-bytes offset: " + offset);
        }

        offset -= 4;
        byte[] rawLen = new byte[4];
        System.arraycopy(rawMessage, offset, rawLen, 0, 4);
        int rawLength = ByteUtil.bytesToInt(rawLen, ByteOrder.BIG_ENDIAN);
        if (rawLength < 0) {
            throw new IllegalArgumentException("negative EVM var-bytes length");
        }

        long evmWordCount = ((long) rawLength + 31L) / 32L;
        long encodedLength = 32L + evmWordCount * 32L;
        long originalOffset = (long) offset + 4L;
        if (encodedLength > originalOffset) {
            throw new IllegalArgumentException(
                    "EVM var-bytes length " + rawLength + " exceeds available input"
            );
        }
        offset -= 28;

        byte[] raw = new byte[rawLength];

        int evmWordCnt = (int) evmWordCount;
        int index = 0;
        while (evmWordCnt-- > 0) {
            offset -= 32;
            int destPos = index++ << 5;
            System.arraycopy(rawMessage, offset, raw, destPos, evmWordCnt > 0 ? 32 : raw.length - destPos);
        }

        return new CoderResult<>(offset, raw);
    }

    public static int sinkVarBytes(byte[] input, byte[] output, int offset) {
        offset -= 4;
        System.arraycopy(ByteUtil.intToBytes(input.length, ByteOrder.BIG_ENDIAN), 0, output, offset, 4);
        offset -= 28;

        int evmWordCnt = calcEvmWordNum(input.length);
        int index = 0;
        while (evmWordCnt-- > 0) {
            offset -= 32;
            int destPos = index++ << 5;
            System.arraycopy(input, destPos, output, offset, evmWordCnt > 0 ? 32 : input.length - destPos);
        }

        return offset;
    }

    public static int calcBytesInEvmWord(int l) {
        return calcEvmWordNum(l) << 5;
    }

    public static int calcEvmWordNum(int l) {
        return (l >> 5) + ((l & 31) != 0 ? 1 : 0);
    }
}
