package com.alipay.antchain.bridge.plugins.ethereum3.kms.enums;

import lombok.Getter;

@Getter
public enum SecretKeyOriginEnum {
    KMS("Aliyun_KMS"),
    EXTERNAL("EXTERNAL");

    /**
     * 枚举值
     */
    private final String code;

    SecretKeyOriginEnum(String code) {
        this.code = code;
    }
}
