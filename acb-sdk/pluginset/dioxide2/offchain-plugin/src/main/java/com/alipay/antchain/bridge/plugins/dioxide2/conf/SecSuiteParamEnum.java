package com.alipay.antchain.bridge.plugins.dioxide2.conf;

import lombok.Getter;

@Getter
public enum SecSuiteParamEnum {

    DELEGATED_HASH_SIZE(32),
    DELEGATED_NAME_SIZEMIN(3),
    DELEGATED_NAME_SIZEMAX(32),
    DELEGATED_DAPP_SIZEMIN(4),
    DELEGATED_DAPP_SIZEMAX(8),
    DELEGATED_TOKEN_SIZEMIN(3),
    DELEGATED_TOKEN_SIZEMAX(8);

    private final int value;

    SecSuiteParamEnum(int value) {
        this.value = value;
    }
}
