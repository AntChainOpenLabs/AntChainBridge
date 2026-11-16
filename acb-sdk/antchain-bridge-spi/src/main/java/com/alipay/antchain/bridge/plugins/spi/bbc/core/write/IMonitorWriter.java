package com.alipay.antchain.bridge.plugins.spi.bbc.core.write;

import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;

/**
 * Through {@code IMonitorWriter}, you can write data
 * to the storage of the MonitorContract.
 */
public interface IMonitorWriter {

    default void setProtocolInMonitor(String contractAddress) {
        throw new UnsupportedOperationException("setProtocolInMonitor is not supported by this BBC");
    }

    default void setMonitorControl(int monitorType) {
        throw new UnsupportedOperationException("setMonitorControl is not supported by this BBC");
    }

    default void setPtcHubInMonitorVerifier(String contractAddress) {
        throw new UnsupportedOperationException("setPtcHubInMonitorVerifier is not supported by this BBC");
    }

    default CrossChainMessageReceipt relayMonitorOrder(String committeeId, String signAlgo, byte[] rawProof, byte[] rawMonitorOrder) {
        throw new UnsupportedOperationException("relayMonitorOrder is not supported by this BBC");
    }
}