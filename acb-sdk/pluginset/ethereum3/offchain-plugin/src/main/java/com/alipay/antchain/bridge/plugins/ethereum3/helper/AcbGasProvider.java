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

import java.math.BigInteger;

import lombok.AllArgsConstructor;
import org.web3j.tx.gas.ContractGasProvider;

@AllArgsConstructor
public class AcbGasProvider implements ContractGasProvider {

    private IGasPriceProvider gasPriceProvider;

    private IGasLimitProvider gasLimitProvider;

    @Override
    public BigInteger getGasPrice(String contractFunc) {
        return gasPriceProvider.getGasPrice(contractFunc);
    }

    @Override
    public BigInteger getGasPrice() {
        return gasPriceProvider.getGasPrice();
    }

    @Override
    public BigInteger getGasLimit(String contractFunc) {
        return gasLimitProvider.getGasLimit(contractFunc);
    }

    @Override
    public BigInteger getGasLimit() {
        return gasLimitProvider.getGasLimit();
    }
}
