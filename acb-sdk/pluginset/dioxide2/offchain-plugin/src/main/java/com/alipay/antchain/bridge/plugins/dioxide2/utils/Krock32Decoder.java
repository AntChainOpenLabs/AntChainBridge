package com.alipay.antchain.bridge.plugins.dioxide2.utils;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Java 重写的 krock32.Decoder（只实现你需要的解码相关逻辑）
 *
 * 使用：
 *   byte[] addr = Krock32Decoder.decodeAddress(key);
 *
 * 对应 Python:
 *   decoder = krock32.Decoder()
 *   decoder.update(key.split(":")[0])
 *   addr = decoder.finalize()
 */
public class Krock32Decoder {

    // Exceptions 对应 Python 中的自定义异常
    public static class DecoderAlreadyFinalizedException extends RuntimeException {
        public DecoderAlreadyFinalizedException(String msg) { super(msg); }
    }
    public static class DecoderInvalidStringLengthException extends IllegalArgumentException {
        public DecoderInvalidStringLengthException(String msg) { super(msg); }
    }
    public static class DecoderNonZeroCarryException extends IllegalStateException {
        public DecoderNonZeroCarryException(String msg) { super(msg); }
    }
    public static class DecoderChecksumException extends RuntimeException {
        public DecoderChecksumException(String msg) { super(msg); }
    }

    // 字母表字符串（与 Python 中一致）
    private static final String ALPHABET_STRING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ*~$=U";

    // 解码映射 char -> value
    private final Map<Character, Integer> alphabet = new HashMap<>();

    // 内部缓冲
    private final StringBuilder stringBuffer = new StringBuilder();
    private final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();

    private boolean isFinished = false;
    private final boolean doChecksum; // Python 默认这里传入的 checksum 参数，我们用 false
    private int checksum = 0;

    public Krock32Decoder() {
        this(false, true, false);
    }

    public Krock32Decoder(boolean strict, boolean ignoreNonAlphabet, boolean checksum) {
        this.doChecksum = checksum;
        makeAlphabet(ALPHABET_STRING, strict);
    }

    // 复制 Python _make_alphabet 逻辑（包含 I/L -> 1, O -> 0 映射）
    private void makeAlphabet(String alphabetString, boolean strict) {
        for (int i = 0; i < alphabetString.length(); i++) {
            char x = alphabetString.charAt(i);
            alphabet.put(Character.toUpperCase(x), i);
            if (!strict) {
                alphabet.put(Character.toLowerCase(x), i);
            }
        }
        if (!strict) {
            alphabet.put('O', 0); alphabet.put('o', 0);
            alphabet.put('I', 1); alphabet.put('i', 1);
            alphabet.put('L', 1); alphabet.put('l', 1);
        }
    }

    // 如果启用 checksum，这里实现与 Python 一致的更新逻辑
    private void updateChecksum(int b) {
        if (!doChecksum) return;
        checksum = ((checksum << 8) + (b & 0xFF)) % 37;
    }

    // 一个内部记录结构，等价 namedtuple("ProcessedByte", ["byte","carry"])
    private static final class PByte {
        final int byteVal; // 0..255
        final int carry;   // carry bits as int
        PByte(int b, int c) { this.byteVal = b; this.carry = c; }
    }

    // _decode_first_byte
    private PByte decodeFirstByte(char s0, char s1) {
        Integer v0 = alphabet.get(s0);
        Integer v1 = alphabet.get(s1);
        if (v0 == null || v1 == null) throw new IllegalArgumentException("Invalid symbol in input");
        int b = (v0 << 3);
        b += (v1 >> 2);
        int carry = v1 & 0b11;
        updateChecksum(b);
        return new PByte(b & 0xFF, carry);
    }

    // _decode_second_byte
    private PByte decodeSecondByte(char s0, char s1, int carry) {
        Integer v0 = alphabet.get(s0);
        Integer v1 = alphabet.get(s1);
        if (v0 == null || v1 == null) throw new IllegalArgumentException("Invalid symbol in input");
        int b = (v0 << 1) + (carry << 6);
        b += (v1 >> 4);
        int newCarry = v1 & 0b1111;
        updateChecksum(b);
        return new PByte(b & 0xFF, newCarry);
    }

    // _decode_third_byte
    private PByte decodeThirdByte(char s, int carry) {
        Integer v = alphabet.get(s);
        if (v == null) throw new IllegalArgumentException("Invalid symbol in input");
        int b = (v >> 1) + (carry << 4);
        int newCarry = v & 1;
        updateChecksum(b);
        return new PByte(b & 0xFF, newCarry);
    }

    // _decode_fourth_byte
    private PByte decodeFourthByte(char s0, char s1, int carry) {
        Integer v0 = alphabet.get(s0);
        Integer v1 = alphabet.get(s1);
        if (v0 == null || v1 == null) throw new IllegalArgumentException("Invalid symbol in input");
        int b = (v0 << 2) + (carry << 7);
        b += (v1 >> 3);
        int newCarry = v1 & 0b111;
        updateChecksum(b);
        return new PByte(b & 0xFF, newCarry);
    }

    // _decode_fifth_byte
    private PByte decodeFifthByte(char s, int carry) {
        Integer v = alphabet.get(s);
        if (v == null) throw new IllegalArgumentException("Invalid symbol in input");
        int b = (carry << 5) + v;
        updateChecksum(b);
        return new PByte(b & 0xFF, 0);
    }

    // _return_quantum 检查 carry 是否为 0
    private void returnQuantumOrThrow(String quantum, PByte p) {
        if (p.carry != 0) {
            throw new DecoderNonZeroCarryException(
                    String.format("Quantum %s decoded with non-zero carry %d", quantum, p.carry)
            );
        }
        // 否则正常（调用方将把 byte 写入）
    }

    // _decode_quantum 实现
    private byte[] decodeQuantum(String quantum) {
        int len = quantum.length();
        if (len != 2 && len != 4 && len != 5 && len != 7 && len != 8) {
            throw new DecoderInvalidStringLengthException("Quantum length must be one of 2,4,5,7,8");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // first 2 chars
        PByte p1 = decodeFirstByte(quantum.charAt(0), quantum.charAt(1));
        out.write(p1.byteVal);
        if (len == 2) {
            return checkReturnQuantum(quantum, p1, out);
        }

        // next 2 chars (positions 2..3)
        PByte p2 = decodeSecondByte(quantum.charAt(2), quantum.charAt(3), p1.carry);
        out.write(p2.byteVal);
        if (len == 4) {
            return checkReturnQuantum(quantum, p2, out);
        }

        // 5th char at index 4
        PByte p3 = decodeThirdByte(quantum.charAt(4), p2.carry);
        out.write(p3.byteVal);
        if (len == 5) {
            return checkReturnQuantum(quantum, p3, out);
        }

        // 6..6 (positions 5..6)
        PByte p4 = decodeFourthByte(quantum.charAt(5), quantum.charAt(6), p3.carry);
        out.write(p4.byteVal);
        if (len == 7) {
            return checkReturnQuantum(quantum, p4, out);
        }

        // final 8th char at index 7
        PByte p5 = decodeFifthByte(quantum.charAt(7), p4.carry);
        out.write(p5.byteVal);
        return out.toByteArray();
    }

    // wrapper used so we can check carry or return array
    private byte[] checkReturnQuantum(String quantum, PByte p, ByteArrayOutputStream out) {
        if (p.carry == 0) {
            return out.toByteArray();
        } else {
            throw new DecoderNonZeroCarryException(
                    String.format("Quantum %s decoded with non-zero carry %d", quantum, p.carry)
            );
        }
    }

    // _consume() - 对每一个完整 8 字符块(quantum)之外的部分保留到 buffer
    private void consume() {
        int tail = 0;
        int len = stringBuffer.length();
        for (int head = 8; head < len; head += 8) {
            String quantum = stringBuffer.substring(tail, head);
            byte[] decoded = decodeQuantum(quantum);
            byteArray.writeBytes(decoded);
            tail = head;
        }
        // 将剩余部分保留
        if (tail == 0) {
            // nothing consumed
        } else {
            // remove consumed part: keep substring from tail..end
            String remain = stringBuffer.substring(tail);
            stringBuffer.setLength(0);
            stringBuffer.append(remain);
            return;
        }
        // if nothing consumed, keep original buffer unchanged
    }

    /**
     * update 方法：等价 Python 的 update()
     * 它会把字符串追加到内部缓冲并尝试消费完整的 8 字符量子
     */
    public void update(String s) {
        if (isFinished) throw new DecoderAlreadyFinalizedException("Decoder already finalized");
        Objects.requireNonNull(s);
        stringBuffer.append(s);
        consume();
    }

    // _check_checksum (如果 doChecksum 为 true，这里会比较)
    private byte[] checkChecksum(char checkSymbol) {
        Integer expected = alphabet.get(checkSymbol);
        if (expected == null) throw new IllegalArgumentException("Invalid checksum symbol");
        if (checksum == expected) {
            return byteArray.toByteArray();
        } else {
            throw new DecoderChecksumException(
                    String.format("Calculated checksum %d, expected %d", checksum, expected)
            );
        }
    }

    /**
     * finalize 的 Java 实现（命名为 finalizeDecode 避免与 Object.finalize 冲突）
     */
    public byte[] finalizeDecode() {
        if (isFinished) throw new DecoderAlreadyFinalizedException("Decoder already finalized");
        isFinished = true;

        char checkSymbol = 0;
        if (doChecksum) {
            if (stringBuffer.length() == 0) {
                throw new DecoderChecksumException("No checksum symbol present");
            }
            checkSymbol = stringBuffer.charAt(stringBuffer.length() - 1);
            stringBuffer.setLength(stringBuffer.length() - 1);
        }

        if (stringBuffer.length() > 0) {
            byte[] decoded = decodeQuantum(stringBuffer.toString());
            byteArray.writeBytes(decoded);
        }

        if (doChecksum) {
            return checkChecksum(checkSymbol);
        } else {
            return byteArray.toByteArray();
        }
    }

    // 辅助静态方法：直接模拟你给出的三行代码
    public static byte[] decodeAddress(String key) {
        String part = key.split(":", 2)[0];
        Krock32Decoder dec = new Krock32Decoder();
        dec.update(part);
        return dec.finalizeDecode();
    }

    // 简单测试（示例） - 真实使用时请用单元测试验证
    public static void main(String[] args) {
        // 示例：请替换为真实的 krock32 编码字符串以测试
        String sampleKey = "CI2FM2DV:other";
        try {
            byte[] addr = Krock32Decoder.decodeAddress(sampleKey);
            System.out.println("Decoded bytes length = " + addr.length);
            System.out.print("Decoded bytes hex: ");
            for (byte b : addr) {
                System.out.printf("%02x", b);
            }
            System.out.println();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
