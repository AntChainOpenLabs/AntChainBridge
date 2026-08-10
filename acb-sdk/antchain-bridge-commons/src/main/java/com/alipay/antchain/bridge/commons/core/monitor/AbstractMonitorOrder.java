package com.alipay.antchain.bridge.commons.core.monitor;

import cn.hutool.core.util.HexUtil;
import com.alipay.antchain.bridge.commons.exception.AntChainBridgeCommonsException;
import com.alipay.antchain.bridge.commons.exception.CommonsErrorCodeEnum;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class AbstractMonitorOrder implements IMonitorOrder {

    public static final int IDENTITY_LENGTH = 32;

    private String product;

    private String domain;

    private long monitorOrderType;

    private String senderDomain;

    private String fromAddress;

    private String receiverDomain;

    private String toAddress;

    private String transactionContent;

    private String extra;

    public byte[] getRawFromAddress() {
        byte[] rawID = HexUtil.decodeHex(this.fromAddress);
        if (rawID.length != IDENTITY_LENGTH) {
            throw new AntChainBridgeCommonsException(
                    CommonsErrorCodeEnum.CROSS_CHAIN_IDENTITY_DECODE_ERROR,
                    String.format("expected id with length %d but got %d", IDENTITY_LENGTH, rawID.length)
            );
        }
        return rawID;
    }

    public byte[] getRawToAddress() {
        byte[] rawID = HexUtil.decodeHex(this.toAddress);
        if (rawID.length != IDENTITY_LENGTH) {
            throw new AntChainBridgeCommonsException(
                    CommonsErrorCodeEnum.CROSS_CHAIN_IDENTITY_DECODE_ERROR,
                    String.format("expected id with length %d but got %d", IDENTITY_LENGTH, rawID.length)
            );
        }
        return rawID;
    }

    public void setFromAddressFromBytes(byte[] fromAddress) {
        if (fromAddress.length != IDENTITY_LENGTH) {
            throw new AntChainBridgeCommonsException(
                    CommonsErrorCodeEnum.CROSS_CHAIN_IDENTITY_DECODE_ERROR,
                    String.format("expected id with length %d but got %d", IDENTITY_LENGTH, fromAddress.length)
            );
        }
        this.fromAddress = HexUtil.encodeHexStr(fromAddress);
    }

    public void setToAddressFromBytes(byte[] toAddress) {
        if (toAddress.length != IDENTITY_LENGTH) {
            throw new AntChainBridgeCommonsException(
                    CommonsErrorCodeEnum.CROSS_CHAIN_IDENTITY_DECODE_ERROR,
                    String.format("expected id with length %d but got %d", IDENTITY_LENGTH, toAddress.length)
            );
        }
        this.toAddress = HexUtil.encodeHexStr(toAddress);
    }
}
