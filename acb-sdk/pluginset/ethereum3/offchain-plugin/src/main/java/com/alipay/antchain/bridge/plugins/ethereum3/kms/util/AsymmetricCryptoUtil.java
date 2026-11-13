package com.alipay.antchain.bridge.plugins.ethereum3.kms.util;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.util.EC5Util;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.*;
import java.util.Base64;
import java.util.Random;

@Slf4j
public class AsymmetricCryptoUtil {

    private static final Provider BC                 = new BouncyCastleProvider();
    private static final X9ECParameters x9ECParameters     = GMNamedCurves.getByName("sm2p256v1");
    private static final ECDomainParameters ecDomainParameters = new ECDomainParameters(x9ECParameters.getCurve(), x9ECParameters.getG(),
            x9ECParameters.getN());

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * 构建密钥（参考web3j的Keys.createEcKeyPair()）
     *
     * @param
     * @return
     */
    public static String generateSecp256k1KeyText() {
        try {
            KeyPair keyPair = generateSecp256k1KeyPair(null);
            return Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 构建密钥（参考web3j的Keys.createEcKeyPair()）
     *
     * @param random
     * @return
     */
    public static KeyPair generateSecp256k1KeyPair(SecureRandom random) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("ECDSA", "BC");
            ECGenParameterSpec ecGenParameterSpec = new ECGenParameterSpec("secp256k1");
            if (random != null) {
                keyPairGenerator.initialize(ecGenParameterSpec, new SecureRandom());
            }
            else {
                keyPairGenerator.initialize(ecGenParameterSpec);
            }

            return keyPairGenerator.generateKeyPair();
        } catch (Exception e) {
            log.error("generateSecp256k1KeyPair failed. ", e);
            return null;
        }

    }

    /**
     * 获取地址
     *
     * @param keyPair
     * @return
     */
    public static String getAddress(KeyPair keyPair) {
        try {
            ECKeyPair ecKeyPair = ECKeyPair.create(keyPair);
            // 3. 根据公钥生成以太坊地址
            String address = Keys.getAddress(ecKeyPair);
            return "0x" + address;
        } catch (Exception e) {
            log.error("getAddress failed. ", e);
            return null;
        }
    }

    /**
     * 获取地址
     *
     * @param privateKey
     * @return
     */
    public static String getAddress(PrivateKey privateKey) {
        try {
            BCECPrivateKey bcecPrivateKey = (BCECPrivateKey) privateKey;
            BigInteger privateKeyValue = bcecPrivateKey.getD();
            ECKeyPair ecKeyPair = ECKeyPair.create(privateKeyValue);
            // 3. 根据公钥生成以太坊地址
            String address = Keys.getAddress(ecKeyPair);
            return "0x" + address;
        } catch (Exception e) {
            log.error("getAddress failed. ", e);
            return null;
        }
    }

    /**
     * 获取公钥
     *
     * @param keyPair
     * @return
     */
    public static String getPublicKey(KeyPair keyPair) {
        try {
            ECKeyPair ecKeyPair = ECKeyPair.create(keyPair);
            return ecKeyPair.getPublicKey().toString(16);
        } catch (Exception e) {
            log.error("getPublicKey failed. ", e);
            return null;
        }
    }

    /**
     * 获取公钥
     *
     * @param privateKey
     * @return
     */
    public static String getPublicKey(PrivateKey privateKey) {
        try {
            BCECPrivateKey bcecPrivateKey = (BCECPrivateKey) privateKey;
            BigInteger privateKeyValue = bcecPrivateKey.getD();
            ECKeyPair ecKeyPair = ECKeyPair.create(privateKeyValue);
            return ecKeyPair.getPublicKey().toString(16);
        } catch (Exception e) {
            log.error("getPublicKey failed. ", e);
            return null;
        }
    }

    /**
     * 获取私钥
     *
     * @param keyPair
     * @return
     */
    public static String getPrivateKey(KeyPair keyPair) {
        try {
            ECKeyPair ecKeyPair = ECKeyPair.create(keyPair);
            return ecKeyPair.getPrivateKey().toString(16);
        } catch (Exception e) {
            log.error("getPublicKey failed. ", e);
            return null;
        }
    }

    public static byte[] generateEphemeralSymmetricKey(String ephemeralSymmetricKeySpec) throws Exception {
        //瞬时对称密钥是AES_256时，长度为32比特。
        //        int ephemeralSymmetricKeyLength = 32;
        //        if ("SM4".equals(ephemeralSymmetricKeySpec)) {
        //            ephemeralSymmetricKeyLength = 16;
        //        }
        byte[] key = new byte[32];
        new Random().nextBytes(key);

        return key;
    }

    public static byte[] generateTargetAsymmetricKey(String keySpec) throws Exception {
        PrivateKey privateKey = null;
        //生成SM2密钥，并获取私钥的D值。
        if ("EC_SM2".equals(keySpec)) {
            ECPrivateKey ecPrivateKey = (ECPrivateKey) generateSm2KeyPair().getPrivate();
            byte[] dT = ecPrivateKey.getS().toByteArray();
            byte[] d = new byte[32];
            if (dT.length == 33) {
                System.arraycopy(dT, 1, d, 0, 32);
            }
            return dT.length == 32 ? dT : d;
        }

        //生成RSA或者ECC私钥。
        if (keySpec.contains("RSA")) {
            String[] keySpecAttrs = keySpec.split("_");
            int bits = Integer.parseInt(keySpecAttrs[keySpecAttrs.length - 1]);
            privateKey = generateRsaKeyPair(bits).getPrivate();
        }
        else if (keySpec.contains("EC")) {
            if (keySpec.contains("P256K")) {
                //生成EC_P256K私钥。
                privateKey = generateEccKeyPair("secp256k1").getPrivate();
            }
            else {
                //生成EC_P256私钥。
                privateKey = generateEccKeyPair("secp256r1").getPrivate();
            }
        }
        if (privateKey != null) {
            //返回PKCS#8格式的私钥。
            return privateKey.getEncoded();
        }
        return null;
    }

    public static KeyPair generateEccKeyPair(String keySpec)
            throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, NoSuchProviderException {
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec(keySpec);

        // Create a key pair generator
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", "BC");

        // Initialize the key pair generator with the curve parameter specification and a secure random
        keyPairGenerator.initialize(spec);

        // Generate a key pair
        return keyPairGenerator.generateKeyPair();
    }

    public static KeyPair generateRsaKeyPair(int length) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(length);
        return keyGen.genKeyPair();
    }

    public static KeyPair generateSm2KeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC", "BC");
        keyGen.initialize(new ECGenParameterSpec("sm2p256v1"), new SecureRandom());
        return keyGen.genKeyPair();
    }

    public static byte[] encryptEphemeralSymmetricKey(String publicKeyBase64, String wrappingAlgorithm,
                                                      byte[] ephemeralSymmetricKeyPlaintext) throws Exception {
        PublicKey publickey = null;
        byte[] enchbk = null;
        if ("RSAES_OAEP_SHA_256_AES_256_ECB_PKCS7_PAD".equals(wrappingAlgorithm)) {
            publickey = parseDerPublicKey("RSA", publicKeyBase64);
            Cipher oaepFromAlgo = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            OAEPParameterSpec oaepParams = new OAEPParameterSpec("SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"),
                    PSource.PSpecified.DEFAULT);
            oaepFromAlgo.init(Cipher.ENCRYPT_MODE, publickey, oaepParams);
            enchbk = oaepFromAlgo.doFinal(ephemeralSymmetricKeyPlaintext);
        }
        else if ("SM2PKE_SM4_ECB".equals(wrappingAlgorithm)) {
            publickey = parseDerPublicKey("EC", publicKeyBase64, BC);
            BCECPublicKey localECPublicKey = (BCECPublicKey) publickey;
            ECPublicKeyParameters ecPublicKeyParameters = new ECPublicKeyParameters(localECPublicKey.getQ(), ecDomainParameters);
            SM2Engine sm2Engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            sm2Engine.init(true, new ParametersWithRandom(ecPublicKeyParameters));
            enchbk = sm2Engine.processBlock(ephemeralSymmetricKeyPlaintext, 0, ephemeralSymmetricKeyPlaintext.length);

        }
        else {
            throw new Exception("Invalid wrappingAlgorithm");
        }
        return enchbk;
    }

    public static PublicKey parseDerPublicKey(String keyType, String pemKey) throws Exception {
        byte[] derKey = DatatypeConverter.parseBase64Binary(pemKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(derKey);
        return KeyFactory.getInstance(keyType).generatePublic(keySpec);
    }

    public static PublicKey parseDerPublicKey(String keyType, String pemKey, Provider provider) throws Exception {
        byte[] derKey = DatatypeConverter.parseBase64Binary(pemKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(derKey);
        return KeyFactory.getInstance(keyType, provider).generatePublic(keySpec);
    }

    public static byte[] encryptTargetAsymmetricKey(byte[] secretKey, byte[] targetAsymmetricKeyPlaintext, String wrappingAlgorithm)
            throws Exception {
        if ("RSAES_OAEP_SHA_256_AES_256_ECB_PKCS7_PAD".equals(wrappingAlgorithm)) {
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS7Padding", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            return cipher.doFinal(targetAsymmetricKeyPlaintext);
        }
        else if ("SM2PKE_SM4_ECB".equals(wrappingAlgorithm)) {
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, "SM4");
            Cipher cipher = Cipher.getInstance("SM4/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            return cipher.doFinal(targetAsymmetricKeyPlaintext);
        }

        throw new Exception("Invalid WrappingAlgorithm");
    }

    // /**
    //  * 使用jasypt进行加密
    //  * @param message
    //  * @param password
    //  * @return
    //  */
    // public static String encryptByJasypt(String message, String password) {
    //     AES256TextEncryptor encryptor = new AES256TextEncryptor();
    //     encryptor.setPassword(password);
    //     return encryptor.encrypt(message);
    // }
    //
    // /**
    //  * 使用jasypt进行解密
    //  * @param encryptedMessage
    //  * @param password
    //  * @return
    //  */
    // public String decryptByJasypt(String encryptedMessage, String password) {
    //     AES256TextEncryptor encryptor = new AES256TextEncryptor();
    //     encryptor.setPassword(password);
    //     return encryptor.decrypt(encryptedMessage);
    // }

    /**
     * EOA钱包地址私钥转ECKeyPair
     * @param privateKey EOA钱包地址私钥
     * @return
     */
    public static ECKeyPair convertECKeyPair(String privateKey){
        // 确保私钥是64位的十六进制字符串
        if (privateKey.startsWith("0x")) {
            privateKey = privateKey.substring(2);
        }

        // 将十六进制字符串转换为字节数组
        byte[] privateKeyBytes = Numeric.hexStringToByteArray(privateKey);

        // 创建ECKeyPair对象
        ECKeyPair keyPair = ECKeyPair.create(privateKeyBytes);

        return keyPair;
    }

    /**
     * EOA钱包地址私钥转PeyPair
     * @param privateKey
     * @return
     * @throws Exception
     */
    public static KeyPair convertKeyPair(String privateKey) throws Exception{
        Security.addProvider(new BouncyCastleProvider());
        // 确保私钥是64位的十六进制字符串
        if (privateKey.startsWith("0x")) {
            privateKey = privateKey.substring(2);
        }

        // 将十六进制字符串转换为字节数组
        byte[] privateKeyBytes = Numeric.hexStringToByteArray(privateKey);

        // 获取 secp256k1 曲线参数
        ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec("secp256k1");

        ECCurve ecCurve = ecSpec.getCurve();

        BigInteger s = new BigInteger(1, privateKeyBytes);

        ECPrivateKeySpec ecPrivateKeySpec = new ECPrivateKeySpec(s, EC5Util.convertSpec(EC5Util.convertCurve(ecCurve, null), ecSpec));

        KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", "BC");

        // 计算公钥
        ECPoint g = ecSpec.getG();
        ECPoint publicKeyPoint = g.multiply(ecPrivateKeySpec.getS());

        ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(EC5Util.convertPoint(publicKeyPoint), ecPrivateKeySpec.getParams());

        PrivateKey priKey = keyFactory.generatePrivate(ecPrivateKeySpec);
        PublicKey pubKey = keyFactory.generatePublic(publicKeySpec);
        return new KeyPair(pubKey, priKey);
    }
}
