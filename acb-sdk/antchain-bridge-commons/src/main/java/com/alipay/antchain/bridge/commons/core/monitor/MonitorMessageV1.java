package com.alipay.antchain.bridge.commons.core.monitor;

import cn.hutool.core.util.ByteUtil;
import com.alipay.antchain.bridge.commons.exception.AntChainBridgeCommonsException;
import com.alipay.antchain.bridge.commons.exception.CommonsErrorCodeEnum;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class MonitorMessageV1 extends AbstractMonitorMessage {

    public static final int MY_VERSION = 1;

    @Override
    public void decode(byte[] rawMessage) {
        int offset = rawMessage.length;

        offset = extractMonitorType(rawMessage, offset);
        offset = extractMonitorMsg(rawMessage, offset);
        extractPayload(rawMessage, offset);
    }

    public int extractMonitorType(byte[] rawMessage, int offset) {
        offset -= 4;
        byte[] rawSeq = new byte[4];
        System.arraycopy(rawMessage, offset, rawSeq, 0, 4);
        this.setMonitorType(ByteUtil.bytesToInt(rawSeq, ByteOrder.BIG_ENDIAN));

        return offset;
    }

    public int extractMonitorMsg(byte[] rawMessage, int offset) {
        offset -= 4;
        byte[] rawMonitorMsgLen = new byte[4];
        System.arraycopy(rawMessage, offset, rawMonitorMsgLen, 0, 4);

        byte[] monitorMsg = new byte[ByteUtil.bytesToInt(rawMonitorMsgLen, ByteOrder.BIG_ENDIAN)];
        offset -= monitorMsg.length;
        if (offset < 0) {
            throw new AntChainBridgeCommonsException(
                    CommonsErrorCodeEnum.MONITOR_MESSAGE_DECODE_ERROR,
                    "length of error message in MonitorV1 is incorrect"
            );
        }
        System.arraycopy(rawMessage, offset, monitorMsg, 0, monitorMsg.length);
        this.setMonitorMsg(new String(monitorMsg));

        return offset;
    }

    public int extractPayload(byte[] rawMessage, int offset) {
        offset -= 4;
        byte[] rawPayloadLen = new byte[4];
        System.arraycopy(rawMessage, offset, rawPayloadLen, 0, 4);

        byte[] payload = new byte[ByteUtil.bytesToInt(rawPayloadLen, ByteOrder.BIG_ENDIAN)];
        offset -= payload.length;
        if (offset < 0) {
            throw new AntChainBridgeCommonsException(
                    CommonsErrorCodeEnum.MONITOR_MESSAGE_DECODE_ERROR,
                    "wrong payload or length of payload in MonitorV1"
            );
        }
        System.arraycopy(rawMessage, offset, payload, 0, payload.length);
        this.setPayload(payload);

        return offset;
    }

    @Override
    public byte[] encode() {
        int rawMessageLen = 12 + this.getMonitorMsg().getBytes(StandardCharsets.UTF_8).length + this.getPayload().length;
        byte[] rawMessage = new byte[rawMessageLen];

        int offset = putMonitorType(rawMessage, rawMessageLen);
        offset = putMonitorMsg(rawMessage, offset);
        putPayload(rawMessage, offset);

        return rawMessage;
    }

    private int putMonitorType(byte[] rawMessage, int offset) {
        offset -= 4;
        System.arraycopy(ByteUtil.intToBytes(this.getMonitorType(), ByteOrder.BIG_ENDIAN), 0, rawMessage, offset, 4);

        return offset;
    }

    private int putMonitorMsg(byte[] rawMessage, int offset) {
        offset -= 4;
        System.arraycopy(ByteUtil.intToBytes(this.getMonitorMsg().getBytes(StandardCharsets.UTF_8).length, ByteOrder.BIG_ENDIAN), 0, rawMessage, offset, 4);

        offset -= this.getMonitorMsg().length();
        System.arraycopy(this.getMonitorMsg().getBytes(), 0, rawMessage, offset, this.getMonitorMsg().length());

        return offset;
    }

    private int putPayload(byte[] rawMessage, int offset) {
        offset -= 4;
        System.arraycopy(ByteUtil.intToBytes(this.getPayload().length, ByteOrder.BIG_ENDIAN), 0, rawMessage, offset, 4);

        offset -= this.getPayload().length;
        System.arraycopy(this.getPayload(), 0, rawMessage, offset, this.getPayload().length);

        return offset;
    }

}
