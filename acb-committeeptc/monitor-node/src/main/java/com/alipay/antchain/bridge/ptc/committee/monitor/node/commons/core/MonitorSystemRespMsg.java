package com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MonitorSystemRespMsg {
    @JSONField(name = "result")
    private int result;

    @JSONField(name = "error_msg")
    private String errorMsg;

    public byte[] encode() {
        return JSON.toJSONBytes(this);
    }

    public static MonitorSystemRespMsg decode(byte[] rawData) {
        return JSON.parseObject(rawData, MonitorSystemRespMsg.class);
    }
}