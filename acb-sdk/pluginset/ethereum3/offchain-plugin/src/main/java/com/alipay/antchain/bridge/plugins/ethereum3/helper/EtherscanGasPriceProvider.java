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
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.model.GasPriceProviderConfig;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.etherscan.EtherscanGetGasOracleResult;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.etherscan.EtherscanResponse;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.web3j.protocol.Web3j;
import org.web3j.utils.Convert;

import java.util.Objects;

public class EtherscanGasPriceProvider extends GasPriceProvider {

    public EtherscanGasPriceProvider(
            Web3j web3j,
            GasPriceProviderConfig config,
            Logger bbcLogger
    ) {
        super(web3j, config, bbcLogger, true);
    }

    @Override
    protected void updateGasPrice() {
        try (
                Response response = getClient().newCall(
                        new Request.Builder()
                                .url(getGasPriceProvider().getGasProviderUrl() + getGasPriceProvider().getApiKey())
                                .build()
                ).execute()
        ) {
            if (!response.isSuccessful()) {
                throw new RuntimeException(
                        StrUtil.format(
                                "http request failed: {} - {}",
                                response.code(), response.message()
                        )
                );
            }
            EtherscanResponse<EtherscanGetGasOracleResult> resp = JSON.parseObject(
                    Objects.requireNonNull(response.body(), "empty resp body").string(),
                    new TypeReference<EtherscanResponse<EtherscanGetGasOracleResult>>() {
                    }
            );
            if (!StrUtil.equals(resp.getStatus(), "1")) {
                throw new RuntimeException(
                        StrUtil.format(
                                "etherscan api error: {} - {}",
                                resp.getStatus(), resp.getMessage()
                        )
                );
            }
            getLogger().info("update gas price: {} gwei", resp.getResult().getProposeGasPrice());
            setGasPrice(Convert.toWei(resp.getResult().getProposeGasPrice(), Convert.Unit.GWEI).toBigInteger());
            // System.out.println(StrUtil.format("update gas price: {} gwei from {}", resp.getResult().getProposeGasPrice(), gasProviderUrl + apiKey));
        } catch (Throwable t) {
            getLogger().error("gas oracle from etherscan error", t);
        }
    }
}
