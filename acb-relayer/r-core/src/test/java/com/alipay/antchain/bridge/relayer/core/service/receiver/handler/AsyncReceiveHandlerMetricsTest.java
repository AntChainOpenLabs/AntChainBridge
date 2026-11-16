package com.alipay.antchain.bridge.relayer.core.service.receiver.handler;

import java.lang.reflect.Field;
import java.util.Collections;

import com.alipay.antchain.bridge.relayer.commons.model.UniformCrosschainPacketContext;
import com.alipay.antchain.bridge.relayer.core.service.metrics.ExperimentMetricsRecorder;
import com.alipay.antchain.bridge.relayer.core.service.report.PlatformReportClient;
import com.alipay.antchain.bridge.relayer.dal.repository.ICrossChainMessageRepository;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AsyncReceiveHandlerMetricsTest {

    @Test
    public void recordsSourceOnlyAfterPersistenceSucceeds() throws Exception {
        AsyncReceiveHandler handler = new AsyncReceiveHandler();
        ICrossChainMessageRepository repository = mock(ICrossChainMessageRepository.class);
        ExperimentMetricsRecorder recorder = mock(ExperimentMetricsRecorder.class);
        PlatformReportClient platform = mock(PlatformReportClient.class);
        UniformCrosschainPacketContext context = mock(UniformCrosschainPacketContext.class);
        when(repository.putUniformCrosschainPackets(anyList())).thenReturn(1);
        when(context.isFromNetwork()).thenReturn(false);
        when(context.getUcpId()).thenReturn("ucp-1");

        set(handler, "crossChainMessageRepository", repository);
        set(handler, "experimentMetricsRecorder", recorder);
        set(handler, "platformReportClient", platform);
        handler.receiveUniformCrosschainPackets(Collections.singletonList(context));

        InOrder order = inOrder(repository, recorder);
        order.verify(repository).putUniformCrosschainPackets(anyList());
        order.verify(recorder).recordSourceAccepted(context);
    }

    @Test(expected = RuntimeException.class)
    public void persistenceFailureProducesNoMetric() throws Exception {
        AsyncReceiveHandler handler = new AsyncReceiveHandler();
        ICrossChainMessageRepository repository = mock(ICrossChainMessageRepository.class);
        ExperimentMetricsRecorder recorder = mock(ExperimentMetricsRecorder.class);
        when(repository.putUniformCrosschainPackets(anyList())).thenReturn(0);
        set(handler, "crossChainMessageRepository", repository);
        set(handler, "experimentMetricsRecorder", recorder);

        try {
            handler.receiveUniformCrosschainPackets(
                    Collections.singletonList(mock(UniformCrosschainPacketContext.class))
            );
        } finally {
            verify(recorder, never()).recordSourceAccepted(org.mockito.ArgumentMatchers.any());
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
