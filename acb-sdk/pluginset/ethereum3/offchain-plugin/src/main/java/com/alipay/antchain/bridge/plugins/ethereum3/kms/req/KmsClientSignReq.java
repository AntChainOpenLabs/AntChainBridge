package com.alipay.antchain.bridge.plugins.ethereum3.kms.req;

import com.alipay.antchain.bridge.plugins.ethereum3.kms.enums.SecretKeySignAlgorithmEnum;
import lombok.Getter;

@Getter
public class KmsClientSignReq {
    /**
     * 需签名数据
     */
    private byte[] signData;


    /**
     * 密钥id
     */
    private String keyId;

    /**
     * 签名算法
     */
    private SecretKeySignAlgorithmEnum algorithm;
}
