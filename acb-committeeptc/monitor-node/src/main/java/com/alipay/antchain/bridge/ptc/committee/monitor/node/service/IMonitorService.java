package com.alipay.antchain.bridge.ptc.committee.monitor.node.service;

import com.alipay.antchain.bridge.commons.core.monitor.MonitorOrderV1;

public interface IMonitorService {

    void recvMonitorOrder(MonitorOrderV1 monitorOrder);

}
