/*
 * Copyright 2024 Ant Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alipay.antchain.bridge.plugins.ethereum3.helper;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.SneakyThrows;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthEstimateGas;

import java.math.BigInteger;

@AllArgsConstructor
@Builder
public class EstimateGasLimitProvider implements IGasLimitProvider {

    /**
     * eth_estimateGas is evaluated against the node's latest state. Several relayer
     * transactions can be queued before that state advances, so an estimate that is
     * exact for the first transaction may be too small for a later transaction in
     * the same block. Keep a multiplicative margin in addition to the operator's
     * configured fixed margin.
     */
    static final BigInteger ESTIMATE_SAFETY_NUMERATOR = BigInteger.valueOf(6L);

    static final BigInteger ESTIMATE_SAFETY_DENOMINATOR = BigInteger.valueOf(5L);

    private Web3j web3j;

    private String fromAddress;

    private String toAddress;

    private String dataHex;

    private long extraGasLimit;

    @Override
    public BigInteger getGasLimit(String contractFunc) {
        return getGasLimitLogic(contractFunc);
    }

    @Override
    public BigInteger getGasLimit() {
        return getGasLimitLogic("");
    }

    @SneakyThrows
    private BigInteger getGasLimitLogic(String contractFunc) {
        EthEstimateGas ethEstimateGas;
        if (StrUtil.equals(contractFunc, "deploy")) {
            ethEstimateGas = web3j.ethEstimateGas(
                    Transaction.createEthCallTransaction(
                            fromAddress,
                            toAddress,
                            dataHex
                    )
            ).send();
        } else {
            ethEstimateGas = web3j.ethEstimateGas(
                    Transaction.createEthCallTransaction(
                            fromAddress,
                            toAddress,
                            dataHex
                    )
            ).send();
        }
        if (ethEstimateGas.hasError()) {
            throw new RuntimeException(StrUtil.format("failed to estimate gas for {} : {}", contractFunc, ethEstimateGas.getError().getMessage()));
        }

        return applySafetyMargin(ethEstimateGas.getAmountUsed(), extraGasLimit);
    }

    static BigInteger applySafetyMargin(BigInteger estimatedGas, long extraGasLimit) {
        if (estimatedGas == null || estimatedGas.signum() < 0) {
            throw new IllegalArgumentException("estimated gas must be a non-negative integer");
        }
        if (extraGasLimit < 0) {
            throw new IllegalArgumentException("extra gas limit must be non-negative");
        }
        return estimatedGas
                .multiply(ESTIMATE_SAFETY_NUMERATOR)
                .add(ESTIMATE_SAFETY_DENOMINATOR.subtract(BigInteger.ONE))
                .divide(ESTIMATE_SAFETY_DENOMINATOR)
                .add(BigInteger.valueOf(extraGasLimit));
    }
}
