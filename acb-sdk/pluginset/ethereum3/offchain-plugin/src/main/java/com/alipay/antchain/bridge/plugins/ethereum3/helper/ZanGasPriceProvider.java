package com.alipay.antchain.bridge.plugins.ethereum3.helper;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.model.GasPriceProviderConfig;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.zan.ZanResponse;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.web3j.protocol.Web3j;
import org.web3j.utils.Convert;

import java.math.BigInteger;
import java.util.Objects;

public class ZanGasPriceProvider extends GasPriceProvider {
    public ZanGasPriceProvider(
            Web3j web3j,
            GasPriceProviderConfig config,
            Logger bbcLogger
    ) {
        super(web3j, config, bbcLogger, true);
    }

    @Override
    public void updateGasPrice() {
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, "{\"id\":1,\"jsonrpc\":\"2.0\",\"method\":\"eth_gasPrice\"}");
        try (
                Response response = getClient().newCall(
                        new Request.Builder()
                                .url(getGasPriceProvider().getGasProviderUrl() + getGasPriceProvider().getApiKey())
                                .post(body)
                                .addHeader("accept", "application/json")
                                .addHeader("content-type", "application/json")
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
            ZanResponse resp = JSON.parseObject(
                    Objects.requireNonNull(response.body(), "empty resp body").string(),
                    new TypeReference<ZanResponse>() {
                    }
            );
            if (!StrUtil.equals(resp.getId(), "1")) {
                throw new RuntimeException(
                        StrUtil.format(
                                "zan node service api error"
                        )
                );
            }
            String newGasPriceStr = resp.getResult();
            if(newGasPriceStr.startsWith("0x")) {
                newGasPriceStr = newGasPriceStr.substring(2, newGasPriceStr.length());
            }
            BigInteger newGasPrice = new BigInteger(newGasPriceStr, 16);
            getLogger().info("update gas price: {} wei", Convert.toWei(newGasPrice.toString(), Convert.Unit.WEI).toBigInteger());
            setGasPrice(newGasPrice);
        } catch (Throwable t) {
            getLogger().error("gas oracle from zan node error", t);
        }
    }
}
