package com.alipay.antchain.bridge.plugins.spi.bbc.core.write;

import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;

/**
 * Through {@code IMonitorWriter}, you can write data
 * to the storage of the MonitorContract.
 */
public interface IMonitorWriter {

    void setProtocolInMonitor(String contractAddress);

    void setMonitorControl(int monitorType);

    void setPtcHubInMonitorVerifier(String contractAddress);

    CrossChainMessageReceipt relayMonitorOrder(String committeeId, String signAlgo, byte[] rawProof, byte[] rawMonitorOrder);
}