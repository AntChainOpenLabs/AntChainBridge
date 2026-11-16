package com.alipay.antchain.bridge.plugins.ethereum3.kms.resp;

import lombok.Data;

@Data
public class KmsClientSignResp {

    /**
     * 签名结果（base64编码）
     */
    private String signature;


    /**
     * 消息摘要hash（base64编码）
     */
    private String messageHash;

}
