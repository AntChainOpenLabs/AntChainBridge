package com.alipay.antchain.bridge.plugins.ethereum3.kms.enums;

import lombok.Getter;

@Getter
public enum SecretKeySpecEnum {
    //对称密钥规格
    Ali_AES_256("Aliyun_AES_256"),
    Ali_AES_192("Aliyun_AES_192"),
    Ali_AES_128("Aliyun_AES_128"),
    Ali_SM4("Aliyun_SM4"),


    //非对称密钥规格
    RSA_2048("RSA_2048"),
    RSA_3072("RSA_3072"),
    RSA_4096("RSA_4096"),
    EC_P256("EC_P256"),
    EC_P256K("EC_P256K"),
    EC_SM2("EC_SM2");

    /**
     * 枚举值
     */
    private final String code;

    SecretKeySpecEnum(String code) {
        this.code = code;
    }

    /**
     * 获取通过代码
     *
     * @param code 代码
     * @return {@link SecretKeySpecEnum}
     */
    public static SecretKeySpecEnum getByCode(String code) {
        for (SecretKeySpecEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
