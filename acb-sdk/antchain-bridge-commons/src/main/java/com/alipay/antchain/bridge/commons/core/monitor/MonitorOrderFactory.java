package com.alipay.antchain.bridge.commons.core.monitor;

import com.alipay.antchain.bridge.commons.exception.AntChainBridgeCommonsException;
import com.alipay.antchain.bridge.commons.exception.CommonsErrorCodeEnum;

public class MonitorOrderFactory {

    public static IMonitorOrder createMonitorOrder(int version, String product, String domain, long monitorOrderType,
                                                   String senderDomain, String fromAddress,
                                                   String receiverDomain, String toAddress,
                                                   String transactionContent, String extra) {
        AbstractMonitorOrder monitorOrder = createAbstractMonitorOrder(version);

        monitorOrder.setProduct(product);
        monitorOrder.setDomain(domain);
        monitorOrder.setMonitorOrderType(monitorOrderType);
        monitorOrder.setSenderDomain(senderDomain);
        monitorOrder.setFromAddress(fromAddress);
        monitorOrder.setReceiverDomain(receiverDomain);
        monitorOrder.setToAddress(toAddress);
        monitorOrder.setTransactionContent(transactionContent);
        monitorOrder.setExtra(extra);

        return monitorOrder;
    }

    private static AbstractMonitorOrder createAbstractMonitorOrder(int version) {
        AbstractMonitorOrder monitorOrder;
        switch (version) {
            case MonitorOrderV1.MY_VERSION:
                monitorOrder = new MonitorOrderV1();
                break;
            default:
                throw new AntChainBridgeCommonsException(
                        CommonsErrorCodeEnum.INCORRECT_MONITOR_ORDER,
                        String.format("wrong version: %d", version)
                );
        }
        return monitorOrder;
    }
}
