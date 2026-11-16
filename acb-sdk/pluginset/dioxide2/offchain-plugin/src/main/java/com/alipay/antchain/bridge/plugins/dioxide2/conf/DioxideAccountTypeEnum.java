package com.alipay.antchain.bridge.plugins.dioxide2.conf;

import lombok.Getter;

@Getter
public enum DioxideAccountTypeEnum {

    ETHEREUM(1),
    BITCOIN_P2PKH(2),
    ED25519(3),
    SM2(4),
    END(5);

    private final int value;

    DioxideAccountTypeEnum(int value) { this.value = value; }

}
