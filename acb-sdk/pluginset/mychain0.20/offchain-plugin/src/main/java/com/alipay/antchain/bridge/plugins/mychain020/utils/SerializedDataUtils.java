package com.alipay.antchain.bridge.plugins.mychain020.utils;

public class SerializedDataUtils {

    public static final int sizeOfUint32() {
       return 4;
    }


    public static final int sizeOfIdentity() {
        return 32;
    }

    public static final int sizeOfBytes(byte[] content) {
        int header_size = 32;
        return header_size + content.length / 32 * 32 + ((content.length % 32 != 0) ? 32 : 0) ;
    }

    public static final long extractUint32(byte[] b, int offset) {
        long l = 0;
        for (int bit = 4; bit > 0; bit--) {
            l <<= 8;
            l |= b[offset - bit] & 0xFF;
        }
        return l;
    }


    public static final void putUint32(int offset, int i, byte[] b) {
        b[offset-1] = (byte)(i & 0xFF);
        b[offset-2] = (byte)((i >>> 8)  & 0xFF);
        b[offset-3] = (byte)((i >>> 16) & 0xFF);
        b[offset-4] = (byte)((i >>> 24) & 0xFF);
    }

    public static final byte[] extractIdentity(byte[] b, int offset) {
        byte[] re = new byte[32];
        System.arraycopy(b, offset - 32, re, 0, 32);
        return re;
    }

    public static final void putIdentity(int offset, byte[] identity, byte[] b) {
        for (int i = 1; i <= 32; i++) {
            b[offset - i] = identity[32 - i];
        }
    }


    public static byte[] extractBytes(byte[] b, int offset) {

        int l = 0;
        for (int bit = 32; bit > 0; bit--) {
            l <<= 8;
            l |= b[offset - bit] & 0xFF;
        }
        byte[] re = new byte[l];
        offset -= (l == 0 ? 0 : 32); // 跳过长度部分
        int w_offset = 0;
        while (l > 32) {
            System.arraycopy(b, offset - 32, re, w_offset, 32);
            l -= 32;
            offset -= 32;
            w_offset += 32;
        }
        System.arraycopy(b, offset - 32, re, w_offset, l);
        return re;
    }

    public static void putBytes(int offset, byte[] content, byte[] b) {
        int content_len = content.length;
        putUint32(offset, content_len, b);
        offset-=4;
        for (int i = 0; i < 7; i++) {
            putUint32(offset, 0, b);
            offset-=4;
        }
        int content_offset = 0;
        while (content_len > 0) {
            if (content_len > 32) {
                System.arraycopy(content, content_offset, b, offset - 32, 32);
                content_len -= 32;
                content_offset += 32;
                offset -= 32;
            } else {
                System.arraycopy(content, content_offset, b, offset - 32, content_len);
                break;
            }
        }
    }



    // TODO: bytes size: length 对32向上取整
    public static int bytesToBytesSize(byte[] b, int offset) {

        int size = 0;
        int l = 0;
        for (int bit = 32; bit > 0; bit--) {
            l <<= 8;
            l |= b[offset - bit] & 0xFF;
        }
        byte[] re = new byte[l];
        offset -= 32; // 跳过长度部分
        size += 32;
        int w_offset = 0;
        while (l > 32) {
            System.arraycopy(b, offset - 32, re, w_offset, 32);
            l -= 32;
            offset -= 32;
            size += 32;
            w_offset += 32;
        }
        System.arraycopy(b, offset - 32, re, w_offset, l);
        size += (l == 0 ? 0 : 32);
        return size;
    }
}
