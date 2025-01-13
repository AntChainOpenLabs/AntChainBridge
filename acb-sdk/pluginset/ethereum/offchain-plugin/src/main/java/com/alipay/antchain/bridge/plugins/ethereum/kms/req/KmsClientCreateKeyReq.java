package com.alipay.antchain.bridge.plugins.ethereum.kms.req;

import com.alipay.antchain.bridge.plugins.ethereum.kms.enums.SecretKeyOriginEnum;
import com.alipay.antchain.bridge.plugins.ethereum.kms.enums.SecretKeySpecEnum;
import com.alipay.antchain.bridge.plugins.ethereum.kms.enums.SecretKeyUsageEnum;
import lombok.Data;

@Data
public class KmsClientCreateKeyReq {
    /**
     * 密钥规格
     */
    private SecretKeySpecEnum keySpec;

    /**
     * 密钥的用途
     */
    private SecretKeyUsageEnum keyUsage;

    /**
     * 密钥材料来源
     */
    private SecretKeyOriginEnum origin;

}
