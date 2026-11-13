package com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.core;

import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MonitorContract {

    private String monitorContractAddress;

    private ContractStatusEnum status;
}