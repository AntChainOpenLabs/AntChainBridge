package com.alipay.antchain.bridge.commons.bbc.syscontract;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MonitorContract {

    private String contractAddress;

    private ContractStatusEnum status;
}
