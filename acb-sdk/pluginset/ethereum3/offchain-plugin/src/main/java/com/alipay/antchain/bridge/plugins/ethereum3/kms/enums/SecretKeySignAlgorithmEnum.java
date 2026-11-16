package com.alipay.antchain.bridge.plugins.ethereum3.kms.enums;

import lombok.Getter;

@Getter
public enum SecretKeySignAlgorithmEnum {

    RSA_PSS_SHA_256("RSA_PSS_SHA_256","SHA-256"),
    ECDSA_SHA_256("ECDSA_SHA_256","SHA-256");

    /**
     * 枚举值
     */
    private final String code;

    /**
     * 枚举值
     */
    private final String digest;

    SecretKeySignAlgorithmEnum(String code, String digest) {
        this.code = code;
        this.digest = digest;
    }

}
