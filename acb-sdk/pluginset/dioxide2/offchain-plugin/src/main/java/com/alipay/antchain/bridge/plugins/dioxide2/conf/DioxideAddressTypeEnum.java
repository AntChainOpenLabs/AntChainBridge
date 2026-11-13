package com.alipay.antchain.bridge.plugins.dioxide2.conf;

import lombok.Getter;

@Getter
public enum DioxideAddressTypeEnum {

    DEFAULT(0),
    HASH(8),
    NAME(9),
    DAPP(10),
    TOKEN(11);

    private final int value;

    DioxideAddressTypeEnum(int value) { this.value = value; }
}
