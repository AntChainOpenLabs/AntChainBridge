package com.alipay.antchain.bridge.plugins.ethereum3.helper;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Getter;

@Getter
public enum GasPriceProviderSupplierEnum {
    ETHERSCAN("etherscan"),

    OWLRACLE("owlracle"),

    ZAN("zan"),

    ETHEREUM("ethereum");

    private String name;

    GasPriceProviderSupplierEnum(String name) {
        this.name = name;
    }

    @JSONField
    public String getName() {
        return name;
    }
}
