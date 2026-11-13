package com.alipay.antchain.bridge.relayer.core.types.pluginserver;

import java.util.concurrent.TimeUnit;

import com.alipay.antchain.bridge.pluginserver.service.CrossChainServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.Assert;
import org.junit.Test;

public class GRpcBBCServiceClientTest {

    @Test
    public void receiptQueryStubHasFiniteDeadline() {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("127.0.0.1", 1)
                .usePlaintext()
                .build();
        try {
            CrossChainServiceGrpc.CrossChainServiceBlockingStub stub =
                    CrossChainServiceGrpc.newBlockingStub(channel);
            CrossChainServiceGrpc.CrossChainServiceBlockingStub deadlineStub =
                    GRpcBBCServiceClient.withReceiptQueryDeadline(stub);

            Assert.assertNotNull(deadlineStub.getCallOptions().getDeadline());
            long remainingSeconds = deadlineStub.getCallOptions().getDeadline()
                    .timeRemaining(TimeUnit.SECONDS);
            Assert.assertTrue(remainingSeconds >= 0);
            Assert.assertTrue(remainingSeconds <= GRpcBBCServiceClient.RECEIPT_QUERY_DEADLINE_SECONDS);
        } finally {
            channel.shutdownNow();
        }
    }
}
