package com.alipay.antchain.bridge.ptc.committee.monitor.node.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;
import com.alipay.antchain.bridge.commons.core.monitor.MonitorOrderV1;
import com.alipay.antchain.bridge.pluginserver.service.*;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.client.CrossChainServiceGrpcClientManager;
import com.alipay.antchain.bridge.ptc.committee.monitor.system.grpc.*;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IMonitorService;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.google.protobuf.ByteString;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import java.security.PrivateKey;
import java.util.Map;
import java.util.concurrent.*;

@Service
@Slf4j
public class MonitorServiceImpl implements IMonitorService {

    @Value("${committee.id}")
    private String committeeId;

    //Keccak256WithSecp256k1
    @Value("${committee.node.endorse.sign_algo:KECCAK256_WITH_SECP256K1}")
    private SignAlgoEnum nodeSignAlgo;

    @Resource
    private PrivateKey nodeKey;

    @Resource
    private CrossChainServiceGrpcClientManager crossChainServiceGrpcClientManager;

    @Override
    public void recvMonitorOrder(MonitorOrderV1 monitorOrder) {

        // sign
        byte[] encodedMonitorOrder = monitorOrder.encode();
        byte[] signature = nodeSignAlgo.getSigner().sign(nodeKey, encodedMonitorOrder);

        CrossChainServiceGrpc.CrossChainServiceBlockingStub crossChainServiceBlockingStub = crossChainServiceGrpcClientManager.getStub("plugin-server");
        Response responseFromPS = crossChainServiceBlockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(monitorOrder.getProduct())
                        .setDomain(monitorOrder.getDomain())
                        .setRelayMonitorOrderReq(
                                RelayMonitorOrderRequest.newBuilder()
                                        .setCommitteeId(committeeId)
                                        .setSignAlgo(nodeSignAlgo.getName())
                                        .setRawProof(ByteString.copyFrom(signature))
                                        .setRawMonitorOrder(ByteString.copyFrom(encodedMonitorOrder))
                        )
                        .build()
        );
//        Response responseFromPS = crossChainServiceBlockingStub.bbcCall(
//                CallBBCRequest.newBuilder()
//                        .setProduct(monitorOrder.getProduct())
//                        .setDomain(monitorOrder.getDomain())
//                        .setRelayMonitorOrderReq(
//                                RelayMonitorOrderRequest.newBuilder()
//                                        .setMonitorOrderType(monitorOrder.getMonitorOrderType())
//                                        .setSenderDomain(monitorOrder.getSenderDomain())
//                                        .setFromAddress(monitorOrder.getFromAddress())
//                                        .setReceiverDomain(monitorOrder.getReceiveDomain())
//                                        .setToAddress(monitorOrder.getToAddress())
//                                        .setTransactionContent(monitorOrder.getTransactionContent())
//                                        .setExtra(monitorOrder.getExtra())
//                        ).build()
//        );

        if (ObjectUtil.isNull(responseFromPS)) {
            throw new RuntimeException("null response from plugin server");
        }
        if (responseFromPS.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] relayMonitorOrder request failed for plugin server: %s",
                            monitorOrder.getDomain(), monitorOrder.getProduct(), responseFromPS.getErrorMsg())
            );
        }

        CrossChainMessageReceipt crossChainMessageReceipt = convertFromGRpcCrossChainMessageReceipt(responseFromPS.getBbcResp().getRelayMonitorOrderResp().getReceipt());

        // 如果监管指令消息未成功上链 目前仅抛出异常
        if (!crossChainMessageReceipt.isSuccessful()) {
            throw new RuntimeException(StrUtil.format("failed to commit monitor order: (error_msg: {})",
                    crossChainMessageReceipt.getErrorMsg()));
        }
    }

    private static CrossChainMessageReceipt convertFromGRpcCrossChainMessageReceipt(com.alipay.antchain.bridge.pluginserver.service.CrossChainMessageReceipt crossChainMessageReceipt) {
        CrossChainMessageReceipt receipt = new CrossChainMessageReceipt();
        receipt.setConfirmed(crossChainMessageReceipt.getConfirmed());
        receipt.setSuccessful(crossChainMessageReceipt.getSuccessful());
        receipt.setTxhash(crossChainMessageReceipt.getTxhash());
        receipt.setErrorMsg(crossChainMessageReceipt.getErrorMsg());
        receipt.setTxTimestamp(crossChainMessageReceipt.getTxTimestamp());
        receipt.setRawTx(crossChainMessageReceipt.getRawTx().toByteArray());

        return receipt;
    }
}
