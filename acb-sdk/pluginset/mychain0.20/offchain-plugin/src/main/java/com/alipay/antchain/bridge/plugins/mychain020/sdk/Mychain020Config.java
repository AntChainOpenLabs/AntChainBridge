package com.alipay.antchain.bridge.plugins.mychain020.sdk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alipay.antchain.bridge.plugins.mychain020.crypto.CryptoSuiteEnum;
import com.alipay.antchain.bridge.plugins.mychain020.crypto.CryptoSuiteUtil;
import com.alipay.antchain.bridge.plugins.mychain020.utils.CryptoUtils;
import com.alipay.mychain.sdk.api.env.*;
import com.alipay.mychain.sdk.crypto.MyCrypto;
import com.alipay.mychain.sdk.crypto.hash.HashTypeEnum;
import com.alipay.mychain.sdk.crypto.keyoperator.Pkcs8KeyOperator;
import com.alipay.mychain.sdk.crypto.keypair.KeyTypeEnum;
import com.alipay.mychain.sdk.crypto.keypair.Keypair;
import com.alipay.mychain.sdk.crypto.signer.SM2SignerV1;
import com.alipay.mychain.sdk.crypto.signer.SignerBase;
import com.alipay.mychain.sdk.type.BaseFixedSizeByteArray;
import com.google.common.collect.Maps;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import static com.alipay.antchain.bridge.plugins.mychain020.crypto.CryptoSuiteEnum.*;

@Getter
@Setter
public class Mychain020Config {
    @JSONField(name = "mychain_primary")
    private String mychainPrimary;

    @JSONField(name = "mychain_secondaries")
    private String mychainSecondaries;

    @JSONField(name = "mychain_sslKey")
    private String mychainSslKey;

    @JSONField(name = "mychain_sslKeyPass")
    private String mychainSslKeyPassword;

    @JSONField(name = "mychain_sslCert")
    private String mychainSslCert;

    @JSONField(name = "mychain_trustStore")
    private String mychainSslTrustStore;

    @JSONField(name = "mychain_trustStorePassword")
    private String mychainSslTrustStorePassword;

    @JSONField(name = "mychain_anchor_account")
    private String mychainAnchorAccount;

    @JSONField(name = "mychain_anchor_account_pri_key")
    private String mychainAccountPriKey;

    @JSONField(name = "mychain_anchor_account_pub_key")
    private String mychainAccountPubKey;

    @JSONField(name = "mychain_tee_publicKey")
    private String mychainTeePublicKey;

    @JSONField(name = "mychain_crypto_suite")
    private String mychainCryptoSuite;

    @JSONField(name = "mychain_anchor_account_subnet_id")
    private String subnetId;

    @JSONField(name = "txGas")
    private BigInteger txGas;

    @JSONField(name = "ext_meta")
    private Map<String, String> extMeta = Maps.newHashMap();

    // 合约相关
    @JSONField(name = "wasm")
    private boolean isWasmSupported;

    @JSONField(name = "wasm_sys_contract_all_in_one")
    private boolean isWasmSysContractAllInOne;

    @JSONField(name = "contract_binary_version")
    private String mychainContractBinaryVersion;

    // 合约名称，应当为包含evm合约和wasm合约的json字符串，如：
    // { "evm": "amContratEvm", "wasm": "amContractWasm" }
    @JSONField(name = "am_client_contract_address")
    private String amContractName;
    @JSONField(name = "am_p2p_msg_contract_address")
    private String sdpContractName;
    @JSONField(name = "ptc_contract_address")
    private String ptcContractName;
    @JSONField(name = "monitor_contract_address")
    private String monitorContractName;
    @JSONField(name = "monitor_verifier_contract_address")
    private String monitorVerifierContractName;

    @JSONField(name = "bcdns_root_cert_pem")
    private String bcdnsRootCertPem;

    /**
     * 从json字符串反序列化
     *
     * @param jsonString raw json
     */
    public static Mychain020Config fromJsonString(String jsonString) throws IOException {
        Mychain020Config config = JSON.parseObject(jsonString, Mychain020Config.class);

        if(ObjectUtil.isEmpty(config.getTxGas())) {
            config.setTxGas(BigInteger.ZERO);
        }

        return config;
    }

    /**
     * json序列化为字符串
     */
    public String toJsonString() {
        return JSON.toJSONString(this);
    }

    /**
     * 根据 config 信息构造返回env
     * 此方法可能就更新 config 中的crypto_suite 字段
     * @return
     */
    public ClientEnv buildMychainEnvWithConfigUpdate() {
        // build socket addr
        InetSocketAddress inetSocketAddress = InetSocketAddress.createUnresolved(
                mychainPrimary.split(" ")[0],
                Integer.valueOf(mychainPrimary.split(" ")[1]));
        List<InetSocketAddress> socketAddressArrayList = new ArrayList<InetSocketAddress>();
        socketAddressArrayList.add(inetSocketAddress);

        if (!StringUtils.isEmpty(mychainSecondaries)) {
            for (String secondary : mychainSecondaries.split("\\^")) {
                String[] infos = secondary.split(":");
                InetSocketAddress secondarySocketAddress = InetSocketAddress.createUnresolved(infos[0], Integer.valueOf(infos[1]));
                socketAddressArrayList.add(secondarySocketAddress);
            }
        }

        // load user key
        Security.addProvider(new BouncyCastleProvider());
        Keypair userKeypair = null;
        Pkcs8KeyOperator pkcs8KeyOperator = new Pkcs8KeyOperator();
        try {
            userKeypair = pkcs8KeyOperator.load(
                    CryptoUtils.addPass(
                            mychainAccountPriKey.getBytes(),
                            "111"),
                    "111");
        } catch (Exception e) {
            throw new RuntimeException("fail to load prikey", e);
        }

        // set smSupport
        if ((userKeypair.getType().equals(KeyTypeEnum.KEY_SM2_PKCS8)) &&
                (StringUtils.isEmpty(mychainCryptoSuite) || StringUtils.equals(mychainCryptoSuite, CRYPTO_SUITE_UNKNOW.getName()) ||
                        StringUtils.equals(mychainCryptoSuite, CRYPTO_SUITE_SM.getName()))) {

            setMychainCryptoSuite(CRYPTO_SUITE_SM.getName());

        } else if ((!userKeypair.getType().equals(KeyTypeEnum.KEY_SM2_PKCS8)) &&
                (StringUtils.isEmpty(mychainCryptoSuite) || StringUtils.equals(mychainCryptoSuite, CRYPTO_SUITE_UNKNOW.getName()) ||
                        StringUtils.equals(mychainCryptoSuite, CRYPTO_SUITE_DEFAULT.getName()))) {

            setMychainCryptoSuite(CRYPTO_SUITE_DEFAULT.getName());

        } else {
            throw new RuntimeException(StrUtil.format(
                    "The crypto suite configuration ({}) is inconsistent with the key type ({}) for {}",
                    mychainCryptoSuite,
                    userKeypair.getType().toString(),
                    mychainPrimary));
        }

        // build ssl option
        ISslOption sslOption = new SslBytesOption.Builder()
                .keyBytes(mychainSslKey.getBytes())
                .certBytes(mychainSslCert.getBytes())
                .keyPassword(mychainSslKeyPassword)
                .trustStorePassword(mychainSslTrustStorePassword)
                .trustStoreBytes(Base64.getDecoder().decode(mychainSslTrustStore))
                .smTLSSupport(isSMChain())
                .build();

        // build signer option
        List<SignerBase> signerBaseList = new ArrayList<SignerBase>();
        SignerBase signerBase = null;
        if (isSMChain()) {
            // Set the national secret signature algorithm to SM2 and the hash national secret algorithm to SM3.
            signerBase = new SM2SignerV1(userKeypair, HashTypeEnum.SM3);
        } else {
            signerBase = MyCrypto.getInstance().createSigner(userKeypair);
        }
        signerBaseList.add(signerBase);
        SignerOption signerOption = new SignerOption();
        signerOption.setSigners(signerBaseList);

        // build env
        ClientEnv env = ClientEnv.build(socketAddressArrayList, sslOption, signerOption);

        // set digest option
        if (isSMChain()) {
            DigestOption digestOption = new DigestOption();
            digestOption.setDefaultDigestType(HashTypeEnum.SM3);
            env.setDigestOption(digestOption);
        }

        // set chainId option
        if (!StringUtils.isEmpty(subnetId)) {
            ChainIdOption chainIdOption = new ChainIdOption(BaseFixedSizeByteArray.Fixed4ByteArray.valueOf(subnetId));
            env.getNetworkOption().setChainIdOption(chainIdOption);
        }

        // mychain多链节点接入
        OptimalNetworkLinkOption optimalNetworkLinkOption = new OptimalNetworkLinkOption();
        optimalNetworkLinkOption.setEnable(true);
        env.getNetworkOption().setOptimalNetworkLinkOption(optimalNetworkLinkOption);

        // 在sdk版本0.10.2.10，低配链拉大区块的时候，会出现timeout；ccen 20200826
        env.getNetworkOption().setHeartbeatIntervalMs(30000);
        env.getNetworkOption().setRetryHeartbeatTimes(10);

        return env;
    }

    public HashTypeEnum getMychainHashType() {
        return CryptoSuiteUtil.getHashTypeEnum(CryptoSuiteEnum.fromName(
                StrUtil.isEmpty(mychainCryptoSuite) ?
                        CRYPTO_SUITE_DEFAULT.getName() :
                        mychainCryptoSuite));
    }

    public boolean isTEEChain() {
        return this.getMychainTeePublicKey() != null && !this.getMychainTeePublicKey().isEmpty();
    }

    public boolean isSMChain() {
        return ObjectUtil.equals(CryptoSuiteEnum.CRYPTO_SUITE_SM.getName(), this.mychainCryptoSuite);
    }

    /**
     * 请尽量不要使用 getMychainCryptoSuite() 而使用 getMychainCryptoSuiteEnum
     * 使用 getMychainCryptoSuite 时请注意若字符串不存在则使用默认crypto_suite
     * @return
     */
    public CryptoSuiteEnum getMychainCryptoSuiteEnum() {
        return StrUtil.isEmpty(mychainCryptoSuite) ? CRYPTO_SUITE_DEFAULT : CryptoSuiteEnum.fromName(mychainCryptoSuite);
    }

    /////////////////////////////////////////////////////

    public static String readFileJson(String fileName) {
        StringBuilder jsonStringBuilder = new StringBuilder();

        try {
            // 使用ClassLoader获取资源文件的输入流
            InputStream inputStream = Mychain020Config.class.getClassLoader().getResourceAsStream(fileName);

            // 使用BufferedReader逐行读取文件内容
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                jsonStringBuilder.append(line);
            }

            // 关闭资源
            reader.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return jsonStringBuilder.toString();
    }
}
