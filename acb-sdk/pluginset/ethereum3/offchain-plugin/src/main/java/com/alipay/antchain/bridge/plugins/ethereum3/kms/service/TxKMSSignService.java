package com.alipay.antchain.bridge.plugins.ethereum3.kms.service;

import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.PemUtil;
import com.alipay.antchain.bridge.commons.core.base.X509PubkeyInfoObjectIdentity;
import com.alipay.antchain.bridge.plugins.ethereum3.kms.enums.SecretKeySignAlgorithmEnum;
import com.aliyun.kms20160120.Client;
import com.aliyun.kms20160120.models.*;
import lombok.SneakyThrows;
import org.web3j.crypto.*;
import org.web3j.service.TxSignService;
import org.web3j.tx.ChainId;
import org.web3j.utils.Numeric;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.Objects;

import static org.web3j.crypto.TransactionEncoder.createEip155SignatureData;
import static org.web3j.crypto.TransactionEncoder.encode;

public class TxKMSSignService implements TxSignService {

    private Client kmsClient;

    private String keyId;

    private String keyVersionId;

    private String publicKey;

    private String address;

    public TxKMSSignService(Client kmsClient, String privateKeyId) throws Exception {
        this.kmsClient = kmsClient;
        this.keyId = privateKeyId;
        this.publicKey = setPublicKey();
        this.address = setAddress();
    }

    /**
     * Aliyun-KMS签名
     *
     * @param rawTransaction
     * @param chainId
     * @return
     * @throws Exception
     */
    @Override
    @SneakyThrows
    public byte[] sign(RawTransaction rawTransaction, long chainId) {
        // 获取key version id
        ListKeyVersionsRequest createKeyRequest = new ListKeyVersionsRequest()
                .setKeyId(this.keyId);
        ListKeyVersionsResponse versionRes = kmsClient.listKeyVersions(createKeyRequest);
        keyVersionId = versionRes.getBody().getKeyVersions().getKeyVersion().get(0).getKeyVersionId();

        // 生成消息摘要
        byte[] encodedTransaction;
        if (chainId > ChainId.NONE && !rawTransaction.getType().isEip1559()) {
            encodedTransaction = encode(rawTransaction, chainId);
        } else {
            encodedTransaction = encode(rawTransaction);
        }
        // boolean isLegacy = chainId > -1L && rawTransaction.getType().equals(TransactionType.LEGACY);
        byte[] messageHash = Hash.sha3(encodedTransaction);
        String digest = Base64.getEncoder().encodeToString(messageHash);

        // 签名
        AsymmetricSignRequest signRequest = new AsymmetricSignRequest()
                .setKeyId(keyId)
                .setKeyVersionId(keyVersionId)
                .setAlgorithm(SecretKeySignAlgorithmEnum.ECDSA_SHA_256.getCode())
                .setDigest(digest);
        AsymmetricSignResponse response = kmsClient.asymmetricSign(signRequest);
        String signatureString = response.getBody().getValue();
        byte[] signBytes = Base64.getDecoder().decode(signatureString);
        ECDSASignature signature = CryptoUtils.fromDerFormat(signBytes).toCanonicalised();
        BigInteger publicKey = new BigInteger(this.publicKey, 16);
        Sign.SignatureData signatureData = Sign.createSignatureData(signature, publicKey, Base64.getDecoder().decode(digest));
        Sign.SignatureData eip155SignatureData = createEip155SignatureData(signatureData, chainId);
        // TODO: EIP1559 gasPrice calculate
        // if (isLegacy) {
        //     signatureData = TransactionEncoder.createEip1559SignatureData(signatureData, chainId);
        // }
        // byte[] finalBytes = encode(rawTransaction, signatureData);
        return encode(rawTransaction, eip155SignatureData);
    }

    private String setPublicKey() throws Exception {
        // 获取key version id
        ListKeyVersionsRequest createKeyRequest = new ListKeyVersionsRequest()
                .setKeyId(keyId);
        ListKeyVersionsResponse versionRes = kmsClient.listKeyVersions(createKeyRequest);
        String keyVersionId = versionRes.getBody().getKeyVersions().getKeyVersion().get(0).getKeyVersionId();

        GetPublicKeyRequest request = new GetPublicKeyRequest();
        request.setKeyId(keyId);
        request.setKeyVersionId(keyVersionId);
        // request.setDryRun("true");
        String pubKeyPem = kmsClient.getPublicKey(request).getBody().getPublicKey();
        Objects.requireNonNull(pubKeyPem, "publicKey is null");

        // recover PublicKey from public key pem
        PublicKey pubKey = new X509PubkeyInfoObjectIdentity(
                PemUtil.readPem(
                        new ByteArrayInputStream(pubKeyPem.getBytes())
                )
        ).getPublicKey();
        BigInteger x = ((ECPublicKey) pubKey).getW().getAffineX();
        BigInteger y = ((ECPublicKey) pubKey).getW().getAffineY();
        BigInteger pubkey = x.multiply(BigInteger.valueOf(16).pow(64)).add(y);
        return HexUtil.encodeHexStr(pubkey.toByteArray());
    }

    public String getPublicKey() throws Exception {
        return this.publicKey;
    }

    private String setAddress() throws Exception {
        if(publicKey.isEmpty()) {
            setPublicKey();
        }
        BigInteger pubkey = new BigInteger(HexUtil.decodeHex(publicKey));
        return Numeric.prependHexPrefix(Keys.getAddress(pubkey));
    }

    @Override
    public String getAddress() {
        return this.address;
    }
}
