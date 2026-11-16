package com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MonitorTypeEnum {

    NONE(0),

    MONITOR_CLOSE(1),

    MONITOR_OPEN(2),

    MONITOR_ROLLBACK(3),

    MONITOR_ORDER(4);

    private final int code;
}
