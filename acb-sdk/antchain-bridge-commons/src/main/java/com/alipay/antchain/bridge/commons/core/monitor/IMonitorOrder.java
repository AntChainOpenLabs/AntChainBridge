package com.alipay.antchain.bridge.commons.core.monitor;

import com.alipay.antchain.bridge.commons.core.base.IMessage;

public interface IMonitorOrder extends IMessage {

    String getProduct();

    String getDomain();

    long getMonitorOrderType();

    String getSenderDomain();

    String getFromAddress();

    String getReceiverDomain();

    String getToAddress();

    String getTransactionContent();

    String getExtra();

}
