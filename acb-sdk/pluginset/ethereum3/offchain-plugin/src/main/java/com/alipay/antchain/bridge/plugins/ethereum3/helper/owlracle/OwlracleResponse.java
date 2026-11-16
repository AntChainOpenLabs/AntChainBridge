package com.alipay.antchain.bridge.plugins.ethereum3.helper.owlracle;

import lombok.*;

import java.util.List;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OwlracleResponse<T> {
    private String timestamp;

    private String lastBlock;

    private String avgTime;

    private String avgTx;

    private String avgGas;

    private List<T> speeds;
}