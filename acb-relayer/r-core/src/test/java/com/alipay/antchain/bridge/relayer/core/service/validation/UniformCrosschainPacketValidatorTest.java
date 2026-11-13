package com.alipay.antchain.bridge.relayer.core.service.validation;

import java.lang.reflect.Field;

import com.alipay.antchain.bridge.commons.core.am.AuthMessageV1;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.base.CrossChainIdentity;
import com.alipay.antchain.bridge.commons.core.base.CrossChainLane;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.commons.core.base.UniformCrosschainPacket;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyProof;
import com.alipay.antchain.bridge.ptc.service.IPTCService;
import com.alipay.antchain.bridge.ptc.types.PtcFeatureDescriptor;
import com.alipay.antchain.bridge.ptc.types.PTCVerifyCrossChainMessageResult;
import com.alipay.antchain.bridge.relayer.commons.model.TpBtaDO;
import com.alipay.antchain.bridge.relayer.commons.model.UniformCrosschainPacketContext;
import com.alipay.antchain.bridge.relayer.core.manager.ptc.PtcManager;
import com.alipay.antchain.bridge.relayer.core.service.report.PlatformReportClient;
import com.alipay.antchain.bridge.relayer.dal.repository.IBlockchainRepository;
import com.alipay.antchain.bridge.relayer.dal.repository.ICrossChainMessageRepository;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UniformCrosschainPacketValidatorTest {

    private static final String UCP_ID =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private ICrossChainMessageRepository crossChainMessageRepository;

    private PtcManager ptcManager;

    private IPTCService ptcService;

    private UniformCrosschainPacketValidator validator;

    @Before
    public void setUp() {
        crossChainMessageRepository = mock(ICrossChainMessageRepository.class);
        ptcManager = mock(PtcManager.class);
        ptcService = mock(IPTCService.class);
        validator = new UniformCrosschainPacketValidator();
        setField(validator, "crossChainMessageRepository", crossChainMessageRepository);
        setField(validator, "blockchainRepository", mock(IBlockchainRepository.class));
        setField(validator, "ptcManager", ptcManager);
        setField(validator, "platformReportClient", mock(PlatformReportClient.class));
    }

    @Test
    public void testPassUcpIdToPtcForEndorsedUcp() {
        UniformCrosschainPacketContext context = buildContext("ethereum3");
        CrossChainLane lane = new CrossChainLane(new CrossChainDomain("source.example"));
        context.setTpbtaLaneKey(lane.getLaneKey());
        context.setTpbtaVersion(1);

        TpBtaDO tpBtaDO = mock(TpBtaDO.class);
        ThirdPartyBlockchainTrustAnchor tpbta = mock(ThirdPartyBlockchainTrustAnchor.class);
        when(tpBtaDO.getPtcServiceId()).thenReturn("ptc01");
        when(tpBtaDO.getTpbta()).thenReturn(tpbta);
        when(ptcManager.getExactTpBta(any(), eq(1))).thenReturn(tpBtaDO);
        when(ptcManager.getPtcService("ptc01")).thenReturn(ptcService);
        PtcFeatureDescriptor featureDescriptor = new PtcFeatureDescriptor();
        featureDescriptor.enableStorage();
        when(ptcService.getPtcFeatureDescriptor()).thenReturn(featureDescriptor);
        when(ptcService.verifyCrossChainMessageWithResult(any(), isNull(), any(), eq(UCP_ID)))
                .thenReturn(new PTCVerifyCrossChainMessageResult(new ThirdPartyProof(), "approved", ""));

        validator.doProcess(context);

        verify(ptcService).verifyCrossChainMessageWithResult(
                eq(tpbta),
                isNull(),
                eq(context.getUcp()),
                eq(UCP_ID)
        );
    }

    @Test
    public void testPassUcpIdToPtcForDioxideUcp() {
        UniformCrosschainPacketContext context = buildContext("dioxide2");
        CrossChainLane lane = new CrossChainLane(new CrossChainDomain("diox.web3.03"));
        context.setTpbtaLaneKey(lane.getLaneKey());
        context.setTpbtaVersion(1);

        TpBtaDO tpBtaDO = mock(TpBtaDO.class);
        ThirdPartyBlockchainTrustAnchor tpbta = mock(ThirdPartyBlockchainTrustAnchor.class);
        when(tpBtaDO.getPtcServiceId()).thenReturn("ptc01");
        when(tpBtaDO.getTpbta()).thenReturn(tpbta);
        when(ptcManager.getExactTpBta(any(), eq(1))).thenReturn(tpBtaDO);
        when(ptcManager.getPtcService("ptc01")).thenReturn(ptcService);
        PtcFeatureDescriptor featureDescriptor = new PtcFeatureDescriptor();
        featureDescriptor.enableStorage();
        when(ptcService.getPtcFeatureDescriptor()).thenReturn(featureDescriptor);
        ThirdPartyProof proof = new ThirdPartyProof();
        when(ptcService.verifyCrossChainMessageWithResult(eq(tpbta), isNull(), any(), eq(UCP_ID)))
                .thenReturn(new PTCVerifyCrossChainMessageResult(proof, "approved", ""));

        validator.doProcess(context);

        verify(ptcService, times(1)).verifyCrossChainMessageWithResult(
                eq(tpbta),
                isNull(),
                eq(context.getUcp()),
                eq(UCP_ID)
        );
        verify(crossChainMessageRepository).putTpProof(UCP_ID, proof);
    }

    private static UniformCrosschainPacketContext buildContext(String product) {
        AuthMessageV1 authMessage = new AuthMessageV1();
        authMessage.setIdentity(new CrossChainIdentity(new byte[32]));
        authMessage.setUpperProtocol(0);
        authMessage.setPayload(new byte[0]);
        CrossChainMessage crossChainMessage = CrossChainMessage.createCrossChainMessage(
                CrossChainMessage.CrossChainMessageType.AUTH_MSG,
                1L,
                1L,
                new byte[]{0x01},
                authMessage.encode(),
                new byte[0],
                new byte[0],
                new byte[]{0x02}
        );
        UniformCrosschainPacketContext context = new UniformCrosschainPacketContext();
        context.setUcpId(UCP_ID);
        context.setProduct(product);
        context.setUcp(new UniformCrosschainPacket(
                new CrossChainDomain("source.example"),
                crossChainMessage,
                null
        ));
        return context;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to inject field " + fieldName, e);
        }
    }
}
