package com.alipay.antchain.bridge.commons.core.monitor;

import com.alipay.antchain.bridge.commons.exception.AntChainBridgeCommonsException;
import com.alipay.antchain.bridge.commons.exception.CommonsErrorCodeEnum;

public class MonitorMessageFactory {

    public static IMonitorMessage createMonitorMessage(byte[] rawMessage) {
        IMonitorMessage monitorMessage = createAbstractMonitorMessage(MonitorMessageV1.MY_VERSION);
        monitorMessage.decode(rawMessage);
        return monitorMessage;
    }

    public static IMonitorMessage createMonitorMessage(int version, int monitorType, String monitorMsg, byte[] payload) {
        AbstractMonitorMessage monitorMessage = createAbstractMonitorMessage(version);

        monitorMessage.setMonitorType(monitorType);
        monitorMessage.setMonitorMsg(monitorMsg);
        monitorMessage.setPayload(payload);

        return monitorMessage;
    }

    public static AbstractMonitorMessage createAbstractMonitorMessage(int version) {
        AbstractMonitorMessage monitorMessage;
        switch (version) {
            case MonitorMessageV1.MY_VERSION:
                monitorMessage = new MonitorMessageV1();
                break;
            default:
                throw new AntChainBridgeCommonsException(
                        CommonsErrorCodeEnum.INCORRECT_MONITOR_MESSAGE_ERROR,
                        String.format("wrong version: %d", version)
                );
        }
        return monitorMessage;
    }

}
