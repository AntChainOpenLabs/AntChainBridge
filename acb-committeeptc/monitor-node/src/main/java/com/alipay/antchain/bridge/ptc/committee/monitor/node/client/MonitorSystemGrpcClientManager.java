package com.alipay.antchain.bridge.ptc.committee.monitor.node.client;

import java.util.*;

import com.alipay.antchain.bridge.commons.utils.crypto.HashAlgoEnum;
import com.alipay.antchain.bridge.ptc.committee.monitor.system.grpc.MonitorSystemServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MonitorSystemGrpcClientManager {

    @Value("${grpc.clients.monitor-system.host:localhost}")
    private String host;

    @Value("${grpc.clients.monitor-system.port:50051}")
    private int port;

    private final Map<String, MonitorSystemServiceGrpc.MonitorSystemServiceBlockingStub> blockingStubMap = new HashMap<>();

    public MonitorSystemServiceGrpc.MonitorSystemServiceBlockingStub createStub(String clientName) {

        // 创建 gRPC 通道
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext() // 使用明文传输
                .build();

        // 创建并返回 gRPC 阻塞存根
        return MonitorSystemServiceGrpc.newBlockingStub(channel);
    }

    public MonitorSystemServiceGrpc.MonitorSystemServiceBlockingStub getStub(String clientName) {
        if (this.blockingStubMap.containsKey(clientName)) {
            return this.blockingStubMap.get(clientName);
        }
        this.blockingStubMap.put(clientName, createStub(clientName));
        return this.blockingStubMap.get(clientName);
    }
}
