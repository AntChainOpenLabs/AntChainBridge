package com.alipay.antchain.bridge.commons.core.monitor;

import cn.hutool.core.util.ByteUtil;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class MonitorOrderV1 extends AbstractMonitorOrder {

    public static final int MY_VERSION = 1;

    // no need to do this
    @Override
    public void decode(byte[] rawMessage) { }

    @Override
    public byte[] encode() {
        // monitorOrderType only needs 4 bytes
        int rawMessageLen = 28 + this.getProduct().getBytes(StandardCharsets.UTF_8).length +
                this.getDomain().getBytes(StandardCharsets.UTF_8).length +
                this.getSenderDomain().getBytes(StandardCharsets.UTF_8).length +
                this.getRawFromAddress().length +
                this.getReceiverDomain().getBytes(StandardCharsets.UTF_8).length +
                this.getRawToAddress().length +
                this.getTransactionContent().getBytes(StandardCharsets.UTF_8).length +
                this.getExtra().getBytes(StandardCharsets.UTF_8).length;
        byte[] rawMessage = new byte[rawMessageLen];

        int offset = putProduct(rawMessage, rawMessageLen);
        offset = putDomain(rawMessage, offset);
        offset = putMonitorOrderType(rawMessage, offset);
        offset = putSenderDomain(rawMessage, offset);
        offset = putRawFromAddress(rawMessage, offset);
        offset = putReceiverDomain(rawMessage, offset);
        offset = putRawToAddress(rawMessage, offset);
        offset = putTransactionContent(rawMessage, offset);
        putExtra(rawMessage, offset);

        return rawMessage;
    }

    private int putProduct(byte[] rawMessage, int offset) {
        offset -= 4;
        System.arraycopy(ByteUtil.intToBytes(this.getProduct().getBytes(StandardCharsets.UTF_8).length, ByteOrder.BIG_ENDIAN), 0, rawMessage, offset, 4);

        offset -= this.getProduct().length();
        System.arraycopy(this.getProduct().getBytes(), 0, rawMessage, offset, this.getProduct().length());

        return offset;
    }

    private int putDomain(byte[] rawMessage, int offset) {
        offset -= 4;
        System.arraycopy(ByteUtil.intToBytes(this.getDomain().getBytes(StandardCharsets.UTF_8).length, ByteOrder.BIG_ENDIAN), 0, rawMessage, offset, 4);

        offset -= this.getDomain().length();
        System.arraycopy(this.getDomain().getBytes(), 0, rawMessage, offset, this.getDomain().length());

        return offset;
    }

    private int putMonitorOrderType(byte[] rawMessage, int offset) {
        offset -= 4;
        System.arraycopy(ByteUtil.intToBytes((int)this.getMonitorOrderType(), ByteOrder.BIG_ENDIAN), 0, rawMessage, offset, 4);

        return offset;
    }

    private int putSenderDomain(byte[] rawMessage, int offset) {
        offset -= 4;
        System.arraycopy(ByteUtil.intToBytes(this.getSenderDomain().getBytes(StandardCharsets.UTF_8).length, ByteOrder.BIG_ENDIAN), 0, rawMessage, offset, 4);

        offset -= this.getSenderDomain().length();
        System.arraycopy(this.getSenderDomain().getBytes(), 0, rawMessage, offset, this.getSenderDomain().length());

        return offset;
    }

    private int putRawFromAddress(byte[] rawMessage, int offset) {
        offset -= 32;
        System.arraycopy(this.getRawFromAddress(), 0, rawMessage, offset, 32);

        return offset;
    }

    private int putReceiverDomain(byte[] rawMessage, int offset) {
        offset -= 4;
        System.arraycopy(ByteUtil.intToBytes(this.getReceiverDomain().getBytes(StandardCharsets.UTF_8).length, ByteOrder.BIG_ENDIAN), 0, rawMessage, offset, 4);

        offset -= this.getReceiverDomain().length();
        System.arraycopy(this.getReceiverDomain().getBytes(), 0, rawMessage, offset, this.getReceiverDomain().length());

        return offset;
    }

    private int putRawToAddress(byte[] rawMessage, int offset) {
        offset -= 32;
        System.arraycopy(this.getRawToAddress(), 0, rawMessage, offset, 32);

        return offset;
    }

    private int putTransactionContent(byte[] rawMessage, int offset) {
        offset -= 4;
        System.arraycopy(ByteUtil.intToBytes(this.getTransactionContent().getBytes(StandardCharsets.UTF_8).length, ByteOrder.BIG_ENDIAN), 0, rawMessage, offset, 4);

        offset -= this.getTransactionContent().length();
        System.arraycopy(this.getTransactionContent().getBytes(), 0, rawMessage, offset, this.getTransactionContent().length());

        return offset;
    }

    private int putExtra(byte[] rawMessage, int offset) {
        offset -= 4;
        System.arraycopy(ByteUtil.intToBytes(this.getExtra().getBytes(StandardCharsets.UTF_8).length, ByteOrder.BIG_ENDIAN), 0, rawMessage, offset, 4);

        offset -= this.getExtra().length();
        System.arraycopy(this.getExtra().getBytes(), 0, rawMessage, offset, this.getExtra().length());

        return offset;
    }

}
