package com.alipay.antchain.bridge.plugins.dioxide2.core;

import com.alipay.antchain.bridge.plugins.dioxide2.conf.DioxideAddressTypeEnum;
import com.alipay.antchain.bridge.plugins.dioxide2.conf.SecSuiteParamEnum;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;

import com.alipay.antchain.bridge.plugins.dioxide2.utils.Crc32cPure;
import com.alipay.antchain.bridge.plugins.dioxide2.utils.Krock32Decoder;
import com.alipay.antchain.bridge.plugins.dioxide2.utils.Krock32Encoder;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class DioxideAddress {

    private byte[] addressBytes;
    private DioxideAddressTypeEnum type;

    public DioxideAddress(byte[] addressBytes, DioxideAddressTypeEnum type) {
        this.addressBytes = addressBytes;
        this.type = type;
    }

    public DioxideAddress(String addressString, DioxideAddressTypeEnum type) {
        this.type = type;
        setDelegateeFromString(addressString);
    }

    public void setDelegateeFromString(String addressString) {
        if (!isDelegateeNameValid(addressString)) {
            this.addressBytes = null;
            throw new IllegalArgumentException("Invalid address string: " + addressString);
        }
        // 填充到 32 字节（右填充 \x00）
        byte[] raw = addressString.getBytes(StandardCharsets.UTF_8);
        byte[] addr = new byte[32];
        Arrays.fill(addr, (byte) 0);
        System.arraycopy(raw, 0, addr, 0, Math.min(raw.length, 32));

        // sid + CRC32C
        int sid = type.getValue();
        int crcValue = Crc32cPure.crc32c(addr, sid);
        long crc = sid | (0xfffffff0L & crcValue);

        ByteBuffer buf = ByteBuffer.allocate(addr.length + 4);
        buf.put(addr);
        buf.order(ByteOrder.LITTLE_ENDIAN).putInt((int) crc);
        this.addressBytes = buf.array();
    }

    public String getAddressInString() {
        if (type == DioxideAddressTypeEnum.DEFAULT) {
            Krock32Encoder krock32Eecoder = new Krock32Encoder();
            krock32Eecoder.update(this.addressBytes);
            return krock32Eecoder.finalizeEncode().toLowerCase();
        } else {
            // 非 DEFAULT 类型用 Java 等价实现
            int valid = 0;
            while (valid < 32) {
                if (this.addressBytes[valid] == 0) break;
                valid++;
            }
            byte[] addr = new byte[valid];
            System.arraycopy(this.addressBytes, 0, addr, 0, valid);
            String addrStr = new String(addr, StandardCharsets.UTF_8);
            return addrStr + ":" + this.type.name().toLowerCase();
        }
    }

    public boolean isDelegateeNameValid(String name) {
        int minLen = 0, maxLen = 0;
        String regex = "";

        switch (this.type) {
            case DioxideAddressTypeEnum.DAPP -> {
                minLen = SecSuiteParamEnum.DELEGATED_DAPP_SIZEMIN.getValue();
                maxLen = SecSuiteParamEnum.DELEGATED_DAPP_SIZEMAX.getValue();
                regex = "[a-zA-Z0-9_]+"; // Python r'[a-z|A-Z|\d|_]+' → 简化为 a-zA-Z0-9_
            }
            case DioxideAddressTypeEnum.TOKEN -> {
                minLen = SecSuiteParamEnum.DELEGATED_TOKEN_SIZEMIN.getValue();
                maxLen = SecSuiteParamEnum.DELEGATED_TOKEN_SIZEMAX.getValue();
                regex = "[A-Z0-9#-]+"; // Python r'[A-Z|\d|-|#]+'
            }
            case DioxideAddressTypeEnum.NAME -> {
                minLen = SecSuiteParamEnum.DELEGATED_NAME_SIZEMIN.getValue();
                maxLen = SecSuiteParamEnum.DELEGATED_NAME_SIZEMAX.getValue();
                regex = "[\\w\\d_\\-!#$@&^*()\\[\\]{}<>,;?~]+";
                // Python r'[\w\d|_|-|!|#|$|@|&|^|*|(|)|\[|\]|{|}|<|>|,|;|?|~]+'
                // Java 正则无需 `|`
            }
            default -> {
                return false;
            }
        }

        if (name.length() < minLen || name.length() > maxLen) {
            return false;
        }

        return Pattern.matches(regex, name);
    }
}
