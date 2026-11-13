package com.alipay.antchain.bridge.plugins.dioxide2.utils;

import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * Java 重写的 krock32.Encoder
 *
 * 对应 Python:
 *   encoder = krock32.Encoder()
 *   encoder.update(data)
 *   encoded = encoder.finalize()
 *
 */
public class Krock32Encoder {

    // 异常定义
    public static class EncoderAlreadyFinalizedException extends RuntimeException {
        public EncoderAlreadyFinalizedException(String msg) { super(msg); }
    }

    // 字母表字符串
    private static final String ALPHABET_STRING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ*~$=U";

    // value → char 的映射（与 Python 一致）
    private final Map<Integer, Character> alphabet = new HashMap<>();

    // 内部缓冲
    private final ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
    private final List<String> stringList = new ArrayList<>();

    private boolean isFinished = false;
    private final boolean doChecksum;
    private int checksum = 0;

    public Krock32Encoder() {
        this(false);
    }

    public Krock32Encoder(boolean checksum) {
        this.doChecksum = checksum;
        makeAlphabet(ALPHABET_STRING);
    }

    // 复制 Python _make_alphabet
    private void makeAlphabet(String alphabetString) {
        for (int i = 0; i < alphabetString.length(); i++) {
            alphabet.put(i, alphabetString.charAt(i));
        }
    }

    // _update_checksum
    private void updateChecksum(int b) {
        if (!doChecksum) return;
        checksum = ((checksum << 8) + (b & 0xFF)) % 37;
    }

    // 对应 Python 的 namedtuple("ProcessedQuin", ["sym", "rem"])
    private static final class PQuin {
        final String sym;
        final int rem;
        PQuin(String s, int r) { this.sym = s; this.rem = r; }
    }

    // _encode_first_quin
    private PQuin encodeFirstQuin(int b) {
        updateChecksum(b);
        int quin = (b >> 3);
        int rem = (b & 0b111) << 2;
        return new PQuin(String.valueOf(alphabet.get(quin)), rem);
    }

    // _encode_second_quin
    private PQuin encodeSecondQuin(int b, int remainder) {
        updateChecksum(b);
        StringBuilder sym = new StringBuilder();
        int quin = (b >> 6) + remainder;
        sym.append(alphabet.get(quin));
        quin = (b >> 1) & 0b11111;
        int rem = (b & 0b1) << 4;
        sym.append(alphabet.get(quin));
        return new PQuin(sym.toString(), rem);
    }

    // _encode_third_quin
    private PQuin encodeThirdQuin(int b, int remainder) {
        updateChecksum(b);
        int quin = (b >> 4) + remainder;
        int rem = (b & 0b1111) << 1;
        return new PQuin(String.valueOf(alphabet.get(quin)), rem);
    }

    // _encode_fourth_quin
    private PQuin encodeFourthQuin(int b, int remainder) {
        updateChecksum(b);
        StringBuilder sym = new StringBuilder();
        int quin = (b >> 7) + remainder;
        sym.append(alphabet.get(quin));
        quin = (b >> 2) & 0b11111;
        sym.append(alphabet.get(quin));
        int rem = (b & 0b11) << 3;
        return new PQuin(sym.toString(), rem);
    }

    // _encode_fifth_quin
    private PQuin encodeFifthQuin(int b, int remainder) {
        updateChecksum(b);
        StringBuilder sym = new StringBuilder();
        int quin = (b >> 5) + remainder;
        sym.append(alphabet.get(quin));
        quin = b & 0b11111;
        sym.append(alphabet.get(quin));
        return new PQuin(sym.toString(), 0);
    }

    // _encode_quantum
    private List<String> encodeQuantum(byte[] quantum) {
        List<String> slist = new ArrayList<>();

        // 第一个字节
        PQuin p1 = encodeFirstQuin(quantum[0] & 0xFF);
        slist.add(p1.sym);
        if (quantum.length == 1) {
            slist.add(String.valueOf(alphabet.get(p1.rem)));
            return slist;
        }

        // 第二个字节
        PQuin p2 = encodeSecondQuin(quantum[1] & 0xFF, p1.rem);
        slist.add(p2.sym);
        if (quantum.length == 2) {
            slist.add(String.valueOf(alphabet.get(p2.rem)));
            return slist;
        }

        // 第三个字节
        PQuin p3 = encodeThirdQuin(quantum[2] & 0xFF, p2.rem);
        slist.add(p3.sym);
        if (quantum.length == 3) {
            slist.add(String.valueOf(alphabet.get(p3.rem)));
            return slist;
        }

        // 第四个字节
        PQuin p4 = encodeFourthQuin(quantum[3] & 0xFF, p3.rem);
        slist.add(p4.sym);
        if (quantum.length == 4) {
            slist.add(String.valueOf(alphabet.get(p4.rem)));
            return slist;
        }

        // 第五个字节
        PQuin p5 = encodeFifthQuin(quantum[4] & 0xFF, p4.rem);
        slist.add(p5.sym);
        return slist;
    }

    // _consume
    private void consume() {
        byte[] buffer = byteBuffer.toByteArray();
        int tail = 0;
        for (int head = 5; head < buffer.length; head += 5) {
            byte[] quantum = Arrays.copyOfRange(buffer, tail, head);
            stringList.addAll(encodeQuantum(quantum));
            tail = head;
        }
        byte[] remaining = Arrays.copyOfRange(buffer, tail, buffer.length);
        byteBuffer.reset();
        try {
            byteBuffer.write(remaining);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // update
    public void update(byte[] data) {
        if (isFinished) throw new EncoderAlreadyFinalizedException("Encoder already finalized");
        Objects.requireNonNull(data);
        try {
            byteBuffer.write(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        consume();
    }

    // finalizeEncode (对应 Python 的 finalize)
    public String finalizeEncode() {
        if (isFinished) throw new EncoderAlreadyFinalizedException("Encoder already finalized");
        isFinished = true;

        byte[] remaining = byteBuffer.toByteArray();
        if (remaining.length > 0) {
            stringList.addAll(encodeQuantum(remaining));
        }

        if (doChecksum) {
            stringList.add(String.valueOf(alphabet.get(checksum)));
        }

        return String.join("", stringList);
    }

    /**
     * 静态便捷方法：直接编码
     */
    public static String encode(byte[] data) {
        Krock32Encoder enc = new Krock32Encoder();
        enc.update(data);
        return enc.finalizeEncode();
    }
}
