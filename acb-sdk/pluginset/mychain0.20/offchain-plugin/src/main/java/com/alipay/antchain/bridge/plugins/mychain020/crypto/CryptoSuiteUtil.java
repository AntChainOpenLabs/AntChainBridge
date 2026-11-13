package com.alipay.antchain.bridge.plugins.mychain020.crypto;

import com.alipay.mychain.sdk.crypto.hash.HashTypeEnum;

public class CryptoSuiteUtil {
    public static HashTypeEnum getHashTypeEnum(CryptoSuiteEnum cryptoSuiteEnum) {
        switch (cryptoSuiteEnum) {
            case CRYPTO_SUITE_DEFAULT:
                return HashTypeEnum.SHA256;
            case CRYPTO_SUITE_SM:
                return HashTypeEnum.SM3;
            default:
                return HashTypeEnum.SHA256;
        }
    }
}
