package com.alipay.antchain.bridge.commons.core.monitor;

import cn.hutool.core.util.ByteUtil;
import com.alipay.antchain.bridge.commons.exception.AntChainBridgeCommonsException;
import com.alipay.antchain.bridge.commons.exception.CommonsErrorCodeEnum;
import com.alipay.antchain.bridge.commons.utils.codec.EvmCoderUtil;
import com.alipay.antchain.bridge.commons.utils.codec.CoderResult;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class MonitorMessageV1 extends AbstractMonitorMessage {

    public static final int MY_VERSION = 1;

    @Override
    public void decode(byte[] rawMessage) {
        if (rawMessage == null || rawMessage.length < 68) {
            throw new AntChainBridgeCommonsException(
                    CommonsErrorCodeEnum.MONITOR_MESSAGE_DECODE_ERROR,
                    "monitor V1 envelope is shorter than its fixed fields"
            );
        }

        try {
            int offset = rawMessage.length;
            offset = extractMonitorType(rawMessage, offset);
            if (getMonitorType() != MONITOR_CLOSE
                    && getMonitorType() != MONITOR_OPEN
                    && getMonitorType() != MONITOR_ROLLBACK) {
                throw new IllegalArgumentException("invalid monitor V1 type: " + getMonitorType());
            }
            offset = extractMonitorMsg(rawMessage, offset);
            offset = extractPayload(rawMessage, offset);
            if (offset != 0) {
                throw new IllegalArgumentException("trailing data in monitor V1 envelope: " + offset);
            }
        } catch (AntChainBridgeCommonsException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeCommonsException(
                    CommonsErrorCodeEnum.MONITOR_MESSAGE_DECODE_ERROR,
                    "malformed monitor V1 envelope",
                    e
            );
        }
    }

    public int extractMonitorType(byte[] rawMessage, int offset) {
        offset -= 4;
        byte[] rawSeq = new byte[4];
        System.arraycopy(rawMessage, offset, rawSeq, 0, 4);
        this.setMonitorType(ByteUtil.bytesToInt(rawSeq, ByteOrder.BIG_ENDIAN));

        return offset;
    }

    public int extractMonitorMsg(byte[] rawMessage, int offset) {
        // offset -= 4;
        // byte[] rawMonitorMsgLen = new byte[4];
        // System.arraycopy(rawMessage, offset, rawMonitorMsgLen, 0, 4);

        // byte[] monitorMsg = new byte[ByteUtil.bytesToInt(rawMonitorMsgLen, ByteOrder.BIG_ENDIAN)];
        // offset -= monitorMsg.length;
        // if (offset < 0) {
        //     throw new AntChainBridgeCommonsException(
        //             CommonsErrorCodeEnum.MONITOR_MESSAGE_DECODE_ERROR,
        //             "length of error message in MonitorV1 is incorrect"
        //     );
        // }
        // System.arraycopy(rawMessage, offset, monitorMsg, 0, monitorMsg.length);
        // this.setMonitorMsg(new String(monitorMsg));

        // return offset;
        CoderResult<byte[]> result = EvmCoderUtil.parseVarBytes(rawMessage, offset);
        this.setMonitorMsg(new String(result.getResult(), StandardCharsets.UTF_8));
        return result.getOffset();
    }

    public int extractPayload(byte[] rawMessage, int offset) {
        // offset -= 4;
        // byte[] rawPayloadLen = new byte[4];
        // System.arraycopy(rawMessage, offset, rawPayloadLen, 0, 4);

        // byte[] payload = new byte[ByteUtil.bytesToInt(rawPayloadLen, ByteOrder.BIG_ENDIAN)];
        // offset -= payload.length;
        // if (offset < 0) {
        //     throw new AntChainBridgeCommonsException(
        //             CommonsErrorCodeEnum.MONITOR_MESSAGE_DECODE_ERROR,
        //             "wrong payload or length of payload in MonitorV1"
        //     );
        // }
        // System.arraycopy(rawMessage, offset, payload, 0, payload.length);
        // this.setPayload(payload);

        // return offset;
        CoderResult<byte[]> result = EvmCoderUtil.parseVarBytes(rawMessage, offset);
        this.setPayload(result.getResult());
        return result.getOffset();
    }

    @Override
    public byte[] encode() {
        // int rawMessageLen = 12 + this.getMonitorMsg().getBytes(StandardCharsets.UTF_8).length + this.getPayload().length;
        int rawMessageLen = 68 + EvmCoderUtil.calcBytesInEvmWord(this.getPayload().length) + EvmCoderUtil.calcBytesInEvmWord(this.getMonitorMsg().getBytes(StandardCharsets.UTF_8).length);
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
        // offset -= 4;
        // System.arraycopy(ByteUtil.intToBytes(this.getMonitorMsg().getBytes(StandardCharsets.UTF_8).length, ByteOrder.BIG_ENDIAN), 0, rawMessage, offset, 4);

        // offset -= this.getMonitorMsg().length();
        // System.arraycopy(this.getMonitorMsg().getBytes(), 0, rawMessage, offset, this.getMonitorMsg().length());

        // return offset;
        return EvmCoderUtil.sinkVarBytes(this.getMonitorMsg().getBytes(StandardCharsets.UTF_8), rawMessage, offset);
    }

    private int putPayload(byte[] rawMessage, int offset) {
        // offset -= 4;
        // System.arraycopy(ByteUtil.intToBytes(this.getPayload().length, ByteOrder.BIG_ENDIAN), 0, rawMessage, offset, 4);

        // offset -= this.getPayload().length;
        // System.arraycopy(this.getPayload(), 0, rawMessage, offset, this.getPayload().length);

        // return offset;
        return EvmCoderUtil.sinkVarBytes(this.getPayload(), rawMessage, offset);
    }

}
