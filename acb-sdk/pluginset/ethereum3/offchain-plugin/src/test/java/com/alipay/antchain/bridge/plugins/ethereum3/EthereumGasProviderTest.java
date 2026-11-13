package com.alipay.antchain.bridge.plugins.ethereum3;

import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.plugins.ethereum3.conf.EthereumConfig;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.*;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.*;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.model.GasPriceProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@Slf4j
public class EthereumGasProviderTest {
    private static final String VALID_URL = "";

    // Etherscan.io only support ethereum mainnet
    private static final String ETHERSCAN_API_KEY = "YourAPIKey"; // 100,000 tms/d, 5 calls/s

    private static final String ETHERSCAN_ETH_MAINNET_URL = "https://api.etherscan.io/api?module=gastracker&action=gasoracle&apikey=";

    // support stopping
    private static final String ETHERSCAN_ETH_SEPOLIA_URL = "https://api-sepolia.etherscan.io/api?module=gastracker&action=gasoracle&apikey=";

    // Owracle limit request 100 tms/h
    private static final String OWLRACLE_API_KEY = "YourAPIKey";

    private static final String OWLRACLE_ETH_SEPOLIA_URL = "https://api.owlracle.info/v4/sepolia/gas?apikey=";

    private static final String OWLRACLE_ETH_MAINNET_URL = "https://api.owlracle.info/v4/eth/gas?apikey=";

    private static final String OWLRACLE_ARB_MAINNET_URL = "https://api.owlracle.info/v4/arb/gas?apikey=";

    // ZAN Node Service
    private static final String ZAN_API_KEY_1 = "YourAPIKey";

    private static final String ZAN_API_KEY_2 = "YourAPIKey"; // whitelist

    private static final String ZAN_ETH_SEPOLIA_URL = "https://api.zan.top/node/v1/eth/sepolia/";

    private static final String ZAN_ETH_MAINNET_URL = "https://api.zan.top/node/v1/eth/mainnet/";

    private static final String ZAN_ARB_SEPOLIA_URL = "https://api.zan.top/node/v1/arb/sepolia/";

    private static final String ZAN_ARB_ONE_URL = "https://api.zan.top/node/v1/arb/one/";

    private static final EthereumConfig ethereumConfig = new EthereumConfig();

    private static EthereumBBCService ethereumBBCService;

    @BeforeClass
    public static void init() {
    }

    private Web3j initWeb3j(String url) {
        return Web3j.build(new HttpService(url));
    }

    private GasPriceProviderConfig initGasPriceProvider(
            GasPriceProviderSupplierEnum gasPriceProviderSupplier,
            String gasProviderUrl,
            String apiKey,
            long gasUpdateInterval
    ) {
        GasPriceProviderConfig gasPriceProvider = new GasPriceProviderConfig();
        gasPriceProvider.setGasPriceProviderSupplier(gasPriceProviderSupplier);
        gasPriceProvider.setGasProviderUrl(gasProviderUrl);
        gasPriceProvider.setApiKey(apiKey);
        gasPriceProvider.setGasUpdateInterval(gasUpdateInterval);
        return gasPriceProvider;
    }

    @Test
    public void main() throws Exception {
    }

    @Test
    public void owlracleGasProviderTestByDirectConnect() throws Exception {
        URL url = new URL(String.format("https://api.owlracle.info/v4/%s/gas?apikey=%s", "sepolia", OWLRACLE_API_KEY));
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:221.0) Gecko/20100101 Firefox/31.0");
        connection.connect();
        HttpsURLConnection httpsURLConnection = (HttpsURLConnection) connection;
        if(httpsURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            System.out.println(StrUtil.format("connect node: {} success!", url.toString()));
        }
        InputStream inputStream = connection.getInputStream();
        BufferedReader readIn = new BufferedReader(new InputStreamReader(inputStream));
        String inputLine;
        while ((inputLine = readIn.readLine()) != null) {
            System.out.println(inputLine);
        }
        readIn.close();
    }

    @Test
    public void etherscanGasProviderTest() throws Exception {
        String url = ETHERSCAN_ETH_MAINNET_URL + ETHERSCAN_API_KEY;
        EtherscanGasPriceProvider etherscanGasProvider = new EtherscanGasPriceProvider(
                initWeb3j(url),
                initGasPriceProvider(GasPriceProviderSupplierEnum.ETHERSCAN, ETHERSCAN_ETH_MAINNET_URL, ETHERSCAN_API_KEY, 1500),
                log
        );
    }

    @Test
    public void owlracleGasProviderTest() throws Exception {
        String url = OWLRACLE_ETH_SEPOLIA_URL + OWLRACLE_API_KEY;
        OwlracleGasPriceProvider owlracleGasProvider = new OwlracleGasPriceProvider(
                initWeb3j(url),
                initGasPriceProvider(GasPriceProviderSupplierEnum.OWLRACLE, OWLRACLE_ETH_SEPOLIA_URL, OWLRACLE_API_KEY, 40000),
                log
        );
    }

    @Test
    public void zanGasProviderTest() throws Exception {
        String url = ZAN_ETH_MAINNET_URL + ZAN_API_KEY_2;
        ZanGasPriceProvider zanGasProvider = new ZanGasPriceProvider(
                initWeb3j(url),
                initGasPriceProvider(GasPriceProviderSupplierEnum.ZAN, ZAN_ETH_MAINNET_URL, ZAN_API_KEY_2, 4000),
                log
        );
    }

    @Test
    public void ethereumGasProviderTest() throws Exception {
        String url = VALID_URL;
        Web3j web3j;
        try {
            web3j = Web3j.build(new HttpService(url));
        } catch (Exception e) {
            throw new RuntimeException(String.format("failed to connect ethereum (url: %s)", url), e);
        }
        EthereumGasPriceProvider ethereumGasProvider = new EthereumGasPriceProvider(
                web3j,
                initGasPriceProvider(GasPriceProviderSupplierEnum.ETHEREUM, null, null, Long.MAX_VALUE),
                log
        );
        ethereumGasProvider.getGasPrice(null);
    }
}
