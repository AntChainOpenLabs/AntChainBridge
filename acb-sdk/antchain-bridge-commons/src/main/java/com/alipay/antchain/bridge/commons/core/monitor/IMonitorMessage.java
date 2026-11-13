package com.alipay.antchain.bridge.commons.core.monitor;

import com.alipay.antchain.bridge.commons.core.base.IMessage;

public interface IMonitorMessage extends IMessage {

    int getMonitorType();

    String getMonitorMsg();

    byte[] getPayload();
}
