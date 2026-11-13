package com.alipay.antchain.bridge.plugins.ethereum3;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Base64;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.PemUtil;
import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.core.base.X509PubkeyInfoObjectIdentity;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.AppContract;
import com.alipay.antchain.bridge.plugins.ethereum3.conf.EthereumConfig;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.GasLimitPolicyEnum;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.GasPricePolicyEnum;
import com.alipay.antchain.bridge.plugins.ethereum3.kms.client.KmsClient;
import com.alipay.antchain.bridge.plugins.ethereum3.kms.enums.SecretKeySignAlgorithmEnum;
import com.alipay.antchain.bridge.plugins.ethereum3.kms.resp.KmsClientSignResp;
import com.alipay.antchain.bridge.plugins.ethereum3.kms.service.TxKMSSignService;
import com.alipay.antchain.bridge.plugins.ethereum3.kms.util.AsymmetricCryptoUtil;
import com.alipay.antchain.bridge.plugins.spi.bbc.AbstractBBCService;
import com.aliyun.kms20160120.Client;
import com.aliyun.kms20160120.models.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.utils.Numeric;

@Slf4j
public class KmsClientTest {
    private static final String VALID_URL = "";

    private static final String INVALID_URL = "http://localhost:6545";

    // !!! replace to your test key
    private static final String APP_USER_ETH_PRIVATE_KEY = "";

    // NOTICE: Better use different private keys down here, especially with EthNoncePolicyEnum#FAST
    private static final String BBC_ETH_PRIVATE_KEY = APP_USER_ETH_PRIVATE_KEY;

    private static final String BBC_ETH_PRIVATE_KEY_2 = APP_USER_ETH_PRIVATE_KEY;

    private static final String BBC_ETH_PRIVATE_KEY_3 = APP_USER_ETH_PRIVATE_KEY;

    private static final String REMOTE_APP_CONTRACT = "0xdd11AA371492B94AB8CDEdf076F84ECCa72820e1";

    private static final EthereumConfig.CrossChainMessageScanPolicyEnum scanPolicy = EthereumConfig.CrossChainMessageScanPolicyEnum.LOG_FILTER;

    private static final int MAX_TX_RESULT_QUERY_TIME = 100;

    private static EthereumBBCService ethereumBBCService;

    private static AppContract appContract;

    private static boolean setupBBC;

    private static final GasPricePolicyEnum gasPricePolicy = GasPricePolicyEnum.STATIC;

    private static final GasLimitPolicyEnum gasLimitPolicy = GasLimitPolicyEnum.ESTIMATE;

    // ----------------------------------------分割线--------------------------------------------
    private static final String instanceId = "";

    // sgp:kms.ap-southeast-1.aliyuncs.com
    // hk:kms.cn-hongkong.aliyuncs.com
    // sh:kms.cn-shanghai.aliyuncs.com
    private static final String endpoint = "";

    private static final String accessKeyId = "";

    private static final String accessKeySecret = "";

    private static Client kmsClient;

    // test account info
    // --------------account 1--------------
    private static final String privateKeyId1 = "";

    private static final String privateKey1 = "";

    private static final String publicKey1 = "";

    private static final String address1 = "";
    // --------------account 2--------------
    private static final String privateKeyId2 = "";

    private static final String privateKey2 = "";

    private static final String publicKey2 = "";

    private static final String address2 = "";

    @BeforeClass
    public static void init() throws Exception {
        // 阿里云kms sdk初始化
        com.aliyun.teaopenapi.models.Config kmsConfig = new com.aliyun.teaopenapi.models.Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret)
                .setEndpoint(endpoint);
        kmsClient = new Client(kmsConfig);

        if (StrUtil.equals(APP_USER_ETH_PRIVATE_KEY, "YourPrivateKey")) {
            throw new IllegalArgumentException(
                    "You must set the variable `APP_USER_ETH_PRIVATE_KEY` a valid blockchain account private key. "
            );
        }

        Web3j web3j = Web3j.build(new HttpService(VALID_URL));
        Credentials credentials = Credentials.create(APP_USER_ETH_PRIVATE_KEY);

        RawTransactionManager rawTransactionManager = new RawTransactionManager(
                web3j, credentials, web3j.ethChainId().send().getChainId().longValue());

        /* appContract = AppContract.deploy(
                web3j,
                rawTransactionManager,
                new AcbGasProvider(
                        new StaticGasPriceProvider(BigInteger.valueOf(20000000000L)),
                        new StaticGasLimitProvider(BigInteger.valueOf(3000000))
                )
        ).send(); */

        ethereumBBCService = new EthereumBBCService();
        Method method = AbstractBBCService.class.getDeclaredMethod("setLogger", Logger.class);
        method.setAccessible(true);
        method.invoke(ethereumBBCService, log);
    }

    @Test
    public void testBBCServiceSign() throws Exception {
        // start up success
        AbstractBBCContext mockValidCtx = mockValidCtx();
        EthereumBBCService ethereumBBCService = new EthereumBBCService();
        ethereumBBCService.startup(mockValidCtx);
        Assert.assertNull(ethereumBBCService.getBbcContext().getAuthMessageContract());
        Assert.assertNull(ethereumBBCService.getBbcContext().getSdpContract());
    }

    private AbstractBBCContext mockValidCtx() {
        EthereumConfig mockConf = new EthereumConfig();
        mockConf.setUrl(VALID_URL);
        mockConf.setPrivateKey(BBC_ETH_PRIVATE_KEY);
        mockConf.setGasPrice(8000000000L);
        mockConf.setGasLimit(3000000);
        mockConf.setMsgScanPolicy(scanPolicy);
        mockConf.setGasLimitPolicy(gasLimitPolicy);
        mockConf.setGasPricePolicy(gasPricePolicy);
        // ---------------------------------------
        mockConf.setKmsService(true);
        mockConf.setKmsEndpoint(endpoint);
        mockConf.setKmsAccessKeyId(accessKeyId);
        mockConf.setKmsAccessKeySecret(accessKeySecret);
        mockConf.setKmsPrivateKeyId(privateKeyId1);
        // mockConf.setAddress(address1);
        // mockConf.setPublicKey(publicKey1);
        // mockConf.setEthNoncePolicy(EthNoncePolicyEnum.FAST);
        AbstractBBCContext mockCtx = new DefaultBBCContext();
        mockCtx.setConfForBlockchainClient(mockConf.toJsonString().getBytes());
        return mockCtx;
    }

    @Test
    public void testSign() throws Exception {
        String signData = "test";
        // 获取key version id
        ListKeyVersionsRequest createKeyRequest = new ListKeyVersionsRequest()
                .setKeyId(privateKeyId1);
        ListKeyVersionsResponse versionRes = kmsClient.listKeyVersions(createKeyRequest);
        String keyVersionId = versionRes.getBody().getKeyVersions().getKeyVersion().get(0).getKeyVersionId();

        // 生成消息摘要
        String digest = generateSignDigest(signData);
        // String digest = Base64.getEncoder().encodeToString(signData.getBytes());

        // 检查digest转换
        // String digestRecover = Base64.getEncoder().encodeToString(digest.getBytes());
        // Assert.assertEquals(digest, digestRecover);

        // 签名
        KmsClientSignResp rep = new KmsClientSignResp();
        AsymmetricSignRequest signRequest = new AsymmetricSignRequest()
                .setKeyId(privateKeyId1)
                .setKeyVersionId(keyVersionId)
                .setAlgorithm(SecretKeySignAlgorithmEnum.ECDSA_SHA_256.getCode())
                .setDigest(digest);
        AsymmetricSignResponse response = kmsClient.asymmetricSign(signRequest);
        rep.setSignature(response.getBody().getValue());
        rep.setMessageHash(digest);
        System.out.println(rep);

        // 验签
        String signatureString = response.getBody().getValue();
        byte[] signBytes = Base64.getDecoder().decode(signatureString);
        ECDSASignature signature = CryptoUtils.fromDerFormat(signBytes).toCanonicalised();

        // 构造BigInteger类型的publickKey
        BigInteger publicKey = new BigInteger(publicKey1, 16);
        System.out.println(publicKey);
        Sign.SignatureData signatureData = Sign.createSignatureData(signature, publicKey, Base64.getDecoder().decode(digest));

        // digest格式转换为sha3哈希检查
        byte[] messageBytes = signData.getBytes(StandardCharsets.UTF_8);
        byte[] hash = Hash.sha3(messageBytes);
        byte[] hashRecover = Base64.getDecoder().decode(digest);
        Assert.assertTrue(Arrays.equals(hash, hashRecover));
        BigInteger pubKeyRecovered = Sign.signedMessageHashToKey(Base64.getDecoder().decode(digest), signatureData);
        boolean isValid = publicKey.equals(pubKeyRecovered);
        Assert.assertTrue(isValid);
    }

    @Test
    public void testGetPublicKey() throws Exception {
        // 获取key version id
        ListKeyVersionsRequest createKeyRequest = new ListKeyVersionsRequest()
                .setKeyId(privateKeyId1);
        ListKeyVersionsResponse versionRes = kmsClient.listKeyVersions(createKeyRequest);
        String keyVersionId = versionRes.getBody().getKeyVersions().getKeyVersion().get(0).getKeyVersionId();

        GetPublicKeyRequest request = new GetPublicKeyRequest();
        request.setKeyId(privateKeyId1);
        request.setKeyVersionId(keyVersionId);
        // request.setDryRun("true");
        GetPublicKeyResponse publicKey = kmsClient.getPublicKey(request);
        System.out.println(publicKey.getBody().getPublicKey());
        Assert.assertNotNull(publicKey);

        String pubKeyPemFromKMS = publicKey.getBody().getPublicKey();
        PublicKey pubKey = new X509PubkeyInfoObjectIdentity(PemUtil.readPem(new ByteArrayInputStream(pubKeyPemFromKMS.getBytes()))).getPublicKey();
        String publicKeyStr = pubKey.toString();

        BigInteger x = ((ECPublicKey) pubKey).getW().getAffineX();
        BigInteger y = ((ECPublicKey) pubKey).getW().getAffineY();
        BigInteger pubkey = x.multiply(BigInteger.valueOf(16).pow(64)).add(y);
        String pubkeyStr = bytesToHex(pubkey.toByteArray());
        System.out.println(pubkeyStr);
        Assert.assertEquals(publicKey1, bytesToHex(pubkey.toByteArray()));

        String s = HexUtil.encodeHexStr(pubkey.toByteArray());
        Assert.assertEquals(publicKey1, s);

        byte[] byteArray = pubkey.toByteArray();
        byte[] bytes = HexUtil.decodeHex(s);
        Assert.assertArrayEquals(byteArray, bytes);

        BigInteger pubkeyRecover = new BigInteger(bytes);
        Assert.assertEquals(pubkey, pubkeyRecover);

        String address = Numeric.prependHexPrefix(Keys.getAddress(pubkey));
        System.out.println(address);
    }

    @Test
    public void testKMSIntegration() throws Exception {
        AbstractBBCContext mockValidCtx = mockValidCtx();
        EthereumConfig config = EthereumConfig.fromJsonString(new String(mockValidCtx.getConfForBlockchainClient()));
        com.aliyun.teaopenapi.models.Config kmsConfig = new com.aliyun.teaopenapi.models.Config()
                .setAccessKeyId(config.getKmsAccessKeyId())
                .setAccessKeySecret(config.getKmsAccessKeySecret())
                .setEndpoint(config.getKmsEndpoint());
        Client kmsClient = new Client(kmsConfig);
        TxKMSSignService txKMSSignService = new TxKMSSignService(kmsClient, config.getKmsPrivateKeyId());
        Assert.assertEquals(publicKey1, txKMSSignService.getPublicKey());
        Assert.assertEquals(address1.toLowerCase(), txKMSSignService.getAddress().toLowerCase());
    }

    @Test
    public void testGenerateSecp256k1KeyPair() throws Exception {
        KeyPair keyPair = AsymmetricCryptoUtil.generateSecp256k1KeyPair(null);
        System.out.println(AsymmetricCryptoUtil.getAddress(keyPair));
        System.out.println(AsymmetricCryptoUtil.getPublicKey(keyPair));
        System.out.println(AsymmetricCryptoUtil.getPrivateKey(keyPair));
    }

    @Test
    public void testCreateKmsClientImpl() throws Exception {
        KmsClient kmsClientImpl = new KmsClient();
        kmsClientImpl.setClient(kmsClient);
        kmsClientImpl.setInstanceId(instanceId);
        kmsClientImpl.setEndpoint(endpoint);
        kmsClientImpl.setAccessKeyId(accessKeyId);
        kmsClientImpl.setAccessKeySecret(accessKeySecret);
        Assert.assertNotNull(kmsClientImpl);
    }

    @Test
    public void testGetPublicKeyLocal() throws Exception {
        String privateKey = "";
        Credentials credentials = Credentials.create(privateKey);
        ECKeyPair keyPair = credentials.getEcKeyPair();
        byte[] publicKey = keyPair.getPublicKey().toByteArray();
        System.out.println("Public Key: " + bytesToHex(publicKey));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * 构建签名需要的摘要
     *
     * @param message
     * @return
     * @throws Exception
     */
    private String generateSignDigest(String message) throws Exception {
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

        // 使用web3j的sha3
        byte[] hash = Hash.sha3(messageBytes);

        return Base64.getEncoder().encodeToString(hash);
    }
}
