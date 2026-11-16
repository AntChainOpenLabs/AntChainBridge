package com.alipay.antchain.bridge.relayer.facade.admin.types;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysContractsInfo {

    @JSONField(name = "am_contract")
    private String amContract;

    @JSONField(name = "sdp_contract")
    private String sdpContract;

    @JSONField(name = "ptc_contract")
    private String ptcContract;

    @JSONField(name = "monitor_contract")
    private String monitorContract;

    @JSONField(name = "state")
    private String state;
}
