package com.alipay.antchain.bridge.plugins.ethereum3.helper;

import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.model.GasPriceProviderConfig;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.web3j.protocol.Web3j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.math.BigInteger;
import java.util.Timer;
import java.util.TimerTask;

@Setter
@Getter
public abstract class GasPriceProvider implements IGasPriceProvider {
    public static GasPriceProvider create(Web3j web3j, GasPriceProviderConfig config, Logger logger) {
        switch (config.getGasPriceProviderSupplier()) {
            case ETHERSCAN:
                return new EtherscanGasPriceProvider(web3j, config, logger);
            case OWLRACLE:
                return new OwlracleGasPriceProvider(web3j, config, logger);
            case ZAN:
                return new ZanGasPriceProvider(web3j, config, logger);
            case ETHEREUM:
                return new EthereumGasPriceProvider(web3j, config, logger);
            default:
                logger.warn("use {} to get gas price not implemented yet", config.getGasPriceProviderSupplier());
                throw new RuntimeException(StrUtil.format("use {} to get gas price not implemented yet", config.getGasPriceProviderSupplier().toString()));
        }
    }

    private static TrustManager[] buildTrustManagers() {
        return new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
        };
    }

    private Web3j web3j;

    private Logger logger;

    private OkHttpClient client;

    private Timer timer;

    private GasPriceProviderConfig gasPriceProvider;

    private BigInteger gasPrice;

    @SneakyThrows
    public GasPriceProvider(
            Web3j web3j,
            GasPriceProviderConfig config,
            Logger bbcLogger,
            boolean ifStartTimer
    ) {
        TrustManager[] trustAllCerts = buildTrustManagers();
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        client = new OkHttpClient.Builder()
                .sslSocketFactory(
                        sslContext.getSocketFactory(),
                        (X509TrustManager) trustAllCerts[0]
                ).hostnameVerifier((hostname, session) -> true)
                .build();
        this.web3j = web3j;
        this.gasPriceProvider = config;
        this.logger = bbcLogger;

        updateGasPrice();
        if(ifStartTimer) {
            timer = new Timer();
            timer.scheduleAtFixedRate(
                    new TimerTask() {
                        @Override
                        public void run() {
                            updateGasPrice();
                        }
                    },
                    0, this.gasPriceProvider.getGasUpdateInterval() // Owlracle can request our data up to 100 times per hour for free.
            );
        }
    }

    protected abstract void updateGasPrice();

    @Override
    public BigInteger getGasPrice(String contractFunc) {
        return gasPrice;
    }

    @Override
    public BigInteger getGasPrice() {
        return gasPrice;
    }
}
