package com.alipay.antchain.bridge.ptc.committee.monitor.node.server;

import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.commons.core.monitor.MonitorOrderV1;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.exception.CommitteeNodeErrorCodeEnum;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.exception.InvalidRequestException;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.server.interceptor.RequestTraceInterceptor;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IMonitorService;
import com.alipay.antchain.bridge.ptc.committee.monitor.system.grpc.*;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService(interceptors = RequestTraceInterceptor.class)
public class MonitorOrderServiceImpl extends MonitorOrderServiceGrpc.MonitorOrderServiceImplBase {

    @Resource
    private IMonitorService monitorService;

    @Override
    public void recvMonitorOrder(RecvMonitorOrderRequest request, StreamObserver<RecvMonitorOrderResponse> responseObserver) {
        try {
            MonitorOrderV1 monitorOrder = convertFromGRpcMonitorOrder(request.getMonitorOrder());
            if (ObjectUtil.isNull(monitorOrder)) {
                throw new InvalidRequestException("monitorOrder is null");
            }
            log.info("receive monitor order: " +
                    "product: {}, domain: {}, monitor_order_type: {}, sender_domain: {}, sender_identity: {}, " +
                    "receive_domain: {}, receive_identity: {}, transaction_content: {}, extra: {}",
                    monitorOrder.getProduct(), monitorOrder.getDomain(), monitorOrder.getMonitorOrderType(),
                    monitorOrder.getSenderDomain(), monitorOrder.getFromAddress(),
                    monitorOrder.getReceiverDomain(), monitorOrder.getToAddress(),
                    monitorOrder.getTransactionContent(), monitorOrder.getExtra());

            monitorService.recvMonitorOrder(monitorOrder);

            responseObserver.onNext(
                    RecvMonitorOrderResponse.newBuilder()
                            .setCode(0)
                            .setErrorMsg("")
                            .build()
            );

        } catch (Throwable t) {
            log.error("query supported blockchain products failed with unexpected error: ", t);
            responseObserver.onNext(
                    RecvMonitorOrderResponse.newBuilder()
                            .setCode(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getErrorCodeNum())
                            .setErrorMsg(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getShortMsg())
                            .build()
            );
        } finally {
            responseObserver.onCompleted();
        }
    }

    private static MonitorOrderV1 convertFromGRpcMonitorOrder(com.alipay.antchain.bridge.ptc.committee.monitor.system.grpc.MonitorOrder grpcMonitorOrder) {
        MonitorOrderV1 monitorOrder = new MonitorOrderV1();
        monitorOrder.setProduct(grpcMonitorOrder.getProduct());
        monitorOrder.setDomain(grpcMonitorOrder.getDomain());
        monitorOrder.setMonitorOrderType(grpcMonitorOrder.getMonitorOrderType());
        monitorOrder.setSenderDomain(grpcMonitorOrder.getSenderDomain());
        monitorOrder.setFromAddress(grpcMonitorOrder.getFromAddress());
        monitorOrder.setReceiverDomain(grpcMonitorOrder.getReceiverDomain());
        monitorOrder.setToAddress(grpcMonitorOrder.getToAddress());
        monitorOrder.setTransactionContent(grpcMonitorOrder.getTransactionContent());
        monitorOrder.setExtra(grpcMonitorOrder.getExtra());
        return monitorOrder;
    }

}
