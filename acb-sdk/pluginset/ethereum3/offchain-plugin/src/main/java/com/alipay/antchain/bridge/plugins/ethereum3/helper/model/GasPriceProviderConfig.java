package com.alipay.antchain.bridge.plugins.ethereum3.helper.model;

import com.alipay.antchain.bridge.plugins.ethereum3.helper.GasPriceProviderSupplierEnum;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GasPriceProviderConfig {
    private GasPriceProviderSupplierEnum gasPriceProviderSupplier;

    private String gasProviderUrl;

    private String apiKey;

    private long gasUpdateInterval;
}
