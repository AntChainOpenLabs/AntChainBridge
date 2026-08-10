package com.alipay.antchain.bridge.commons.core.monitor;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public abstract class AbstractMonitorMessage implements IMonitorMessage {

    public static int MONITOR_CLOSE = 1;

    public static int MONITOR_OPEN = 2;

    public static int MONITOR_ROLLBACK = 3;

    public static int MONITOR_ORDER = 4;

    private int monitorType;

    private String monitorMsg;

    private byte[] payload;

}
