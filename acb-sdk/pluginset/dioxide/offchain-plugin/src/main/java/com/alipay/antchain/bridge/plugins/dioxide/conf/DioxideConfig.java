package com.alipay.antchain.bridge.plugins.dioxide.conf;

import java.io.IOException;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DioxideConfig {

    /**
     * 从json字符串反序列化
     *
     * @param jsonString raw json
     */
    public static DioxideConfig fromJsonString(String jsonString) throws IOException {
        return JSON.parseObject(jsonString, DioxideConfig.class);
    }

    // [client]
    @JSONField
    private String rpcUrl = "http://127.0.0.1:62222/api";

    @JSONField
    private String wsRpc = "ws://127.0.0.1:62222/api";

    @JSONField
    private String privateKey;

    // Optional per-domain path; production normally shares the process-level config file.
    private String txCoordinatorConfigFile;

    // [address / Id]
    @JSONField
    private String amContractAddressDeployed;

    @JSONField
    private String sdpContractAddressDeployed;

    @JSONField
    private String dappName = "AcbDapp";

    @JSONField
    private String amContractName = "AuthMsg";

    @JSONField
    private String sdpContractName = "SDPMsg";

    @JSONField
    private Boolean isPreContractDeployed = false;

    /**
     * json序列化为字符串
     */
    public String toJsonString() {
        return JSON.toJSONString(this);
    }
    
}
