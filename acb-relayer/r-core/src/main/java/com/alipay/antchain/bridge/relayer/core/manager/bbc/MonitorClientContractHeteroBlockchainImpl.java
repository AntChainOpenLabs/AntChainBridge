package com.alipay.antchain.bridge.relayer.core.manager.bbc;

import com.alipay.antchain.bridge.relayer.core.types.pluginserver.IBBCServiceClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonitorClientContractHeteroBlockchainImpl implements IMonitorClientContract {
    private IBBCServiceClient bbcServiceClient;

    public MonitorClientContractHeteroBlockchainImpl(IBBCServiceClient bbcServiceClient) {
        this.bbcServiceClient = bbcServiceClient;
    }

    @Override
    public void setProtocolInMonitor(String protocolContract) {
        this.bbcServiceClient.setProtocolInMonitor(protocolContract);
    }

    @Override
    public void setMonitorControl(int monitorType) {
        this.bbcServiceClient.setMonitorControl(monitorType);
    }

    @Override
    public void setPtcHubInMonitorVerifier(String contractAddress) {
        this.bbcServiceClient.setPtcHubInMonitorVerifier(contractAddress);
    }

    @Override
    public void deployContract() {
        this.bbcServiceClient.setupMonitorContract();
    }
}

