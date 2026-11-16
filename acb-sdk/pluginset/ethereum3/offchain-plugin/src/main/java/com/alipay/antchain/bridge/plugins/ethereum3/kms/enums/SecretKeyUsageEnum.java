package com.alipay.antchain.bridge.plugins.ethereum3.kms.enums;

import lombok.Getter;

@Getter
public enum SecretKeyUsageEnum {
    SV("SIGN/VERIFY"),
    ED("ENCRYPT/DECRYPT");

    /**
     * 枚举值
     */
    private final String code;

    SecretKeyUsageEnum(String code) {
        this.code = code;
    }
}
