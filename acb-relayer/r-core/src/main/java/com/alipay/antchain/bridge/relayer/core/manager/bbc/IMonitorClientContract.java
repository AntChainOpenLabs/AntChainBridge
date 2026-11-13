package com.alipay.antchain.bridge.relayer.core.manager.bbc;

public interface IMonitorClientContract {

    void setProtocolInMonitor(String protocolContract);

    void setMonitorControl(int monitorType);

    void setPtcHubInMonitorVerifier(String contractAddress);

    void deployContract();

}
