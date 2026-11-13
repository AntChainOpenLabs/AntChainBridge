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

import com.alipay.antchain.bridge.plugins.ethereum3.helper.model.GasPriceProviderConfig;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.web3j.protocol.Web3j;

public class EthereumGasPriceProvider extends GasPriceProvider {

    public EthereumGasPriceProvider(
            Web3j web3j,
            GasPriceProviderConfig config,
            Logger bbcLogger
    ) {
        super(web3j, config, bbcLogger, false);
    }

    @Override
    public void updateGasPrice() {}

    @Override
    @SneakyThrows
    public BigInteger getGasPrice(String contractFunc) {
        /* maxFee = baseFee + maxPriorityFee */
        BigInteger baseFee = getWeb3j().ethGasPrice().send().getGasPrice();
        BigInteger maxPriorityFee = BigInteger.valueOf(2_000_000_000L);
        BigInteger maxFee = baseFee.add(maxPriorityFee);
        getLogger().debug("update gas price: {} wei", maxFee);
        return maxFee;
    }

    @Override
    @SneakyThrows
    public BigInteger getGasPrice() {
        return this.getGasPrice(null);
    }
}
