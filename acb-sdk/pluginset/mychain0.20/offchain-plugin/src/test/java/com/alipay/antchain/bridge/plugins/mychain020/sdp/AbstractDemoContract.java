package com.alipay.antchain.bridge.plugins.mychain020.sdp;

import com.alipay.antchain.bridge.plugins.mychain020.common.BizContractTypeEnum;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.domain.account.Identity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class AbstractDemoContract {

    private String domain;

    private String contractName;

    private Mychain020Client mychain020Client;

    private String sdpContractName;

//    private FabricHelper fabricHelper;


    public Identity getContractId() {
        return Utils.getIdentityByName(contractName);
    }

    public abstract BizContractTypeEnum getBizContractType();
}
