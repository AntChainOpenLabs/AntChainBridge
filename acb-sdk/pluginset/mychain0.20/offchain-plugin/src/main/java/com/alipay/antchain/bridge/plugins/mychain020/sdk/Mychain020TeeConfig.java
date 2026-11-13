package com.alipay.antchain.bridge.plugins.mychain020.sdk;

import com.alipay.mychain.sdk.crypto.keyoperator.Pkcs8KeyOperator;
import com.alipay.mychain.sdk.crypto.keypair.Keypair;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Mychain020TeeConfig {

    /**
     * TEE链的平台公钥
     */
    private List<byte[]> teePublicKeys = new ArrayList<>();

    /**
     * TEE链的加密私钥，直接初始化
     */
    private String teeSecretKey = "123456";

    public void setTeePublicKeys(byte[] keyBytes){
        Keypair keypair = new Pkcs8KeyOperator().loadPubkey(keyBytes);

        // 注意，这里要幂等(先clear再add)。因为start函数会被调用多次(有些奇怪)，导致teePublicKeys产生多个元素，
        //      进一步造成签名数量增多，调用TEE链返回60001
        // 虽然TEE链SDK接受teePublicKeys列表作为参数，但是其中只能有一个密钥。没有文档说明，一个隐藏坑
        teePublicKeys.clear();
        teePublicKeys.add(keypair.getPubkeyEncoded());
    }

}
