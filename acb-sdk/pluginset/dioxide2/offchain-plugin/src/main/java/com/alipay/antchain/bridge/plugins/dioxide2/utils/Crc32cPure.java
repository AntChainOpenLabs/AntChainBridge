package com.alipay.antchain.bridge.plugins.dioxide2.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Crc32cPure {

    // 反射用的多项式常量（Castagnoli 多项式的反射形式）
    private static final int POLY = 0x82F63B78;
    private static final int[] TABLE = makeTable();

    private Crc32cPure() {}

    // 生成 256 项查找表（table-driven CRC）
    private static int[] makeTable() {
        int[] table = new int[256];
        for (int i = 0; i < 256; i++) {
            int crc = i;
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ POLY;
                } else {
                    crc = crc >>> 1;
                }
            }
            table[i] = crc;
        }
        return table;
    }

    /**
     * 计算 CRC32C（Castagnoli），并支持 seed（初始 CRC 值）。
     *
     * 语义等价于：Python `crc32c.crc32c(data, seed)` 的行为，
     * 也即把 seed 当作先前的 CRC 值来继续计算。
     *
     * @param data 待计算的数据（非 null）
     * @param seed 初始 CRC 值（作为"之前的"crc），通常 0 或一个先前的 crc32c 返回值
     * @return 计算出的 CRC32C（32-bit int。若需无符号值，可用 `crc & 0xffffffffL`）
     */
    public static int crc32c(byte[] data, int seed) {
        // 标准做法：先与 0xFFFFFFFF 异或，再按字节处理，最后再异或回 0xFFFFFFFF
        int crc = seed ^ 0xFFFFFFFF;

        for (int i = 0; i < data.length; i++) {
            int b = data[i] & 0xFF;
            int tblIdx = (crc ^ b) & 0xFF;
            crc = (crc >>> 8) ^ TABLE[tblIdx];
        }

        return crc ^ 0xFFFFFFFF;
    }

    /**
     * 便捷方法：计算完整数组，seed = 0 的常见情况。
     */
    public static int crc32c(byte[] data) {
        return crc32c(data, 0);
    }

    /** 把 int 写成 4 字节的小端序数组 */
    public static byte[] intToLittleEndianBytes(int v) {
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(v);
        return bb.array();
    }
}
