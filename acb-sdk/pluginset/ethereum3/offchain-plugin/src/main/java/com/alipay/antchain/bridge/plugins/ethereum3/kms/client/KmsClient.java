/*
 * Ant Group
 * Copyright (c) 2004-2024 All Rights Reserved.
 */
package com.alipay.antchain.bridge.plugins.ethereum3.kms.client;

import com.alipay.antchain.bridge.plugins.ethereum3.kms.IKmsClient;
import com.alipay.antchain.bridge.plugins.ethereum3.kms.enums.SecretKeyOriginEnum;
import com.alipay.antchain.bridge.plugins.ethereum3.kms.enums.SecretKeySpecEnum;
import com.alipay.antchain.bridge.plugins.ethereum3.kms.req.KmsClientCreateAndImportReq;
import com.alipay.antchain.bridge.plugins.ethereum3.kms.req.KmsClientCreateKeyReq;
import com.alipay.antchain.bridge.plugins.ethereum3.kms.req.KmsClientGetSecretReq;
import com.alipay.antchain.bridge.plugins.ethereum3.kms.req.KmsClientSignReq;
import com.alipay.antchain.bridge.plugins.ethereum3.kms.resp.KmsClientSignResp;
import com.aliyun.kms20160120.Client;
import com.aliyun.kms20160120.models.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.web3j.crypto.Hash;

import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.alipay.antchain.bridge.plugins.ethereum3.kms.util.AsymmetricCryptoUtil.*;

@RequiredArgsConstructor
@Data
public class KmsClient implements IKmsClient {

    private final static String ENCRYPT_KEY_ALGORITHM = "RSAES_OAEP_SHA_256_AES_256_ECB_PKCS7_PAD";

    private final static String EPHEMERAL_KEY_SPEC = "AES_256";

    private Client client;

    private String instanceId;

    private String endpoint;

    private String accessKeyId;

    private String accessKeySecret;

    public void init() throws Exception {
        // 阿里云kms sdk初始化
        com.aliyun.teaopenapi.models.Config kmsConfig = new com.aliyun.teaopenapi.models.Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret)
                .setEndpoint(endpoint);
        client = new Client(kmsConfig);
    }

    @Override
    @SneakyThrows
    public String createKey(KmsClientCreateKeyReq req) {
        CreateKeyRequest createKeyRequest = new CreateKeyRequest()
                .setDKMSInstanceId(instanceId)
                .setKeySpec(req.getKeySpec().toString())
                .setKeyUsage(req.getKeyUsage().getCode())
                .setOrigin(req.getOrigin().getCode());
        CreateKeyResponse createKeyResponse = client.createKey(createKeyRequest);
        return createKeyResponse.getBody().getKeyMetadata().getKeyId();
    }

    @Override
    @SneakyThrows
    public String deleteKey(String keyId) {
        ScheduleKeyDeletionRequest scheduleKeyDeletionRequest = new ScheduleKeyDeletionRequest()
                .setKeyId(keyId)
                .setPendingWindowInDays(7);
        ScheduleKeyDeletionResponse scheduleKeyDeletion = client.scheduleKeyDeletion(scheduleKeyDeletionRequest);
        return scheduleKeyDeletion.getBody().getRequestId();
    }

    @Override
    @SneakyThrows
    public String createAndImportKey(KmsClientCreateAndImportReq req) {
        // 创建密钥
        KmsClientCreateKeyReq createKeyReq = new KmsClientCreateKeyReq();
        createKeyReq.setKeySpec(req.getKeySpec());
        createKeyReq.setKeyUsage(req.getKeyUsage());
        createKeyReq.setOrigin(SecretKeyOriginEnum.EXTERNAL);
        String keyId = createKey(createKeyReq);

        // 获取导入所需参数
        GetParametersForImportRequest importParamRequest = new GetParametersForImportRequest()
                .setWrappingAlgorithm(ENCRYPT_KEY_ALGORITHM)
                .setWrappingKeySpec(SecretKeySpecEnum.RSA_2048.getCode())
                .setKeyId(keyId);
        GetParametersForImportResponse importParamResponse = client.getParametersForImport(importParamRequest);
        String importToken = importParamResponse.getBody().getImportToken();
        String publicKeyBase64 = importParamResponse.getBody().getPublicKey();

        // 使用公钥加密密钥材料
        String encryptedKeyMaterial = encryptedKeyMaterial(req.getKeyPlaintext(), publicKeyBase64);

        // 导入密钥材料
        ImportKeyMaterialRequest importKeyMaterialRequest = new ImportKeyMaterialRequest()
                .setKeyId(keyId)
                .setEncryptedKeyMaterial(encryptedKeyMaterial)
                .setImportToken(importToken);
        client.importKeyMaterial(importKeyMaterialRequest);
        return keyId;
    }

    @Override
    @SneakyThrows
    public KmsClientSignResp sign(KmsClientSignReq req) {
        // 获取key version
        ListKeyVersionsRequest createKeyRequest = new ListKeyVersionsRequest()
                .setKeyId(req.getKeyId());
        ListKeyVersionsResponse versionRes = client.listKeyVersions(createKeyRequest);
        String keyVersionId = versionRes.getBody().getKeyVersions().getKeyVersion().get(0).getKeyVersionId();

        // 生成消息摘要
        // String digest = generateSignDigest(req.getSignData(), req.getAlgorithm().getDigest());
        String digest = Base64.getEncoder().encodeToString(req.getSignData());

        // 签名
        KmsClientSignResp rep = new KmsClientSignResp();
        AsymmetricSignRequest signRequest = new AsymmetricSignRequest()
                .setKeyId(req.getKeyId())
                .setKeyVersionId(keyVersionId)
                .setAlgorithm(req.getAlgorithm().getCode())
                .setDigest(digest);
        AsymmetricSignResponse response = client.asymmetricSign(signRequest);
        rep.setSignature(response.getBody().getValue());
        rep.setMessageHash(digest);
        return rep;
    }

    @Override
    public String getSecret(KmsClientGetSecretReq req) throws Exception {
        // 未使用SalTemplate模版调用，避免日志输出密钥
        GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest();
        getSecretValueRequest.setSecretName(req.getName());
        getSecretValueRequest.setVersionId(req.getVersion());
        GetSecretValueResponse response = client.getSecretValue(getSecretValueRequest);
        return response.getBody().getSecretData();
    }

    /**
     * 加密密钥材料
     *
     * @param publicKeyBase64
     * @param keyPlaintext
     * @return
     * @throws Exception
     */
    private String encryptedKeyMaterial(String keyPlaintext, String publicKeyBase64) throws Exception {
        byte[] keyPlainByte = Base64.getDecoder().decode(keyPlaintext);
        // 生成瞬时对称密钥。
        byte[] ephemeralSymmetricKeyPlaintext = generateEphemeralSymmetricKey(EPHEMERAL_KEY_SPEC);
        // 使用加密公钥加密瞬时对称密钥。
        byte[] ephemeralSymmetricKeyCipher = encryptEphemeralSymmetricKey(publicKeyBase64,
                ENCRYPT_KEY_ALGORITHM, ephemeralSymmetricKeyPlaintext);

        // 使用瞬时对称密钥加密目标非对称密钥。
        byte[] targetAsymmetricKeyCipher = encryptTargetAsymmetricKey(ephemeralSymmetricKeyPlaintext, keyPlainByte,
                ENCRYPT_KEY_ALGORITHM);

        // 生成密钥材料。
        byte[] encryptedKeyMaterial = new byte[ephemeralSymmetricKeyCipher.length + targetAsymmetricKeyCipher.length];
        System.arraycopy(ephemeralSymmetricKeyCipher, 0, encryptedKeyMaterial, 0, ephemeralSymmetricKeyCipher.length);
        System.arraycopy(targetAsymmetricKeyCipher, 0, encryptedKeyMaterial, ephemeralSymmetricKeyCipher.length, targetAsymmetricKeyCipher.length);
        return DatatypeConverter.printBase64Binary(encryptedKeyMaterial);
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

