package com.alipay.antchain.bridge.plugins.ethereum3.conf;

import lombok.Getter;
import org.web3j.protocol.core.DefaultBlockParameterName;

@Getter
public enum BlockHeightPolicyEnum {

    LATEST(DefaultBlockParameterName.LATEST),

    SAFE(DefaultBlockParameterName.SAFE),

    FINALIZED(DefaultBlockParameterName.FINALIZED);

    BlockHeightPolicyEnum(DefaultBlockParameterName defaultBlockParameterName) {
        this.defaultBlockParameterName = defaultBlockParameterName;
    }

    private final DefaultBlockParameterName defaultBlockParameterName;
}
