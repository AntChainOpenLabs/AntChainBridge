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

import java.util.Objects;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.model.GasPriceProviderConfig;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.owlracle.OwlracleGetGasPriceResult;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.owlracle.OwlracleResponse;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.web3j.protocol.Web3j;
import org.web3j.utils.Convert;

public class OwlracleGasPriceProvider extends GasPriceProvider {

    public OwlracleGasPriceProvider(
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
                                .get()
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
            OwlracleResponse<OwlracleGetGasPriceResult> resp = JSON.parseObject(
                    Objects.requireNonNull(response.body(), "empty resp body").string(),
                    new TypeReference<OwlracleResponse<OwlracleGetGasPriceResult>>() {
                    }
            );
            getLogger().info("update gas price: {} gwei", resp.getSpeeds().get(3).getMaxFeePerGas());
            setGasPrice(Convert.toWei(resp.getSpeeds().get(3).getMaxFeePerGas(), Convert.Unit.GWEI).toBigInteger());
            // System.out.println(StrUtil.format("update gas price: {} wei from {}", getGasPrice(), getGasPriceProvider().getGasProviderUrl() + getGasPriceProvider().getApiKey()));
        } catch (Throwable t) {
            getLogger().error("gas oracle from Owlracle error", t);
        }
    }
}
