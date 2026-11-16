package com.alipay.antchain.bridge.plugins.ethereum3.helper.owlracle;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OwlracleGetGasPriceResult {

    private String acceptance;

    private String maxFeePerGas;

    private String maxPriorityFeePerGas;

    private String baseFee;

    private String estimatedFee;
}
