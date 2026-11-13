package com.alipay.antchain.bridge.ptc.committee.node.service;

import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.ptc.committee.node.commons.models.*;
import com.alipay.antchain.bridge.ptc.committee.node.commons.exception.CommitteeNodeInternalException;
import com.alipay.antchain.bridge.ptc.committee.node.dal.repository.interfaces.IEndorseServiceRepository;
import com.alipay.antchain.bridge.ptc.committee.node.service.impl.EndorserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** No business chain, database or signing key is used. */
public class DioxideFunctionalProductTest {
    @Test public void missingHcdvsNeverBecomesAnApprovalOrSignature() {
        for (String product : new String[]{"dioxide", "dioxide2", "ethereum2"}) {
            EndorserServiceImpl service = new EndorserServiceImpl();
            IEndorseServiceRepository repo = mock(IEndorseServiceRepository.class);
            IHcdvsPluginService plugins = mock(IHcdvsPluginService.class);
            ReflectionTestUtils.setField(service, "endorseServiceRepository", repo);
            ReflectionTestUtils.setField(service, "hcdvsPluginService", plugins);
            CrossChainDomain domain = new CrossChainDomain("offline.example");
            CrossChainLane lane = new CrossChainLane(domain);
            TpBtaWrapper trust = mock(TpBtaWrapper.class, RETURNS_DEEP_STUBS);
            BtaWrapper bta = mock(BtaWrapper.class, RETURNS_DEEP_STUBS);
            when(bta.getProduct()).thenReturn(product);
            byte[] hash = new byte[]{1, 2};
            when(bta.getBta().getInitHeight()).thenReturn(BigInteger.ONE);
            when(bta.getBta().getInitBlockHash()).thenReturn(hash);
            when(repo.getMatchedTpBta(lane)).thenReturn(trust);
            when(repo.getExactTpBta(lane)).thenReturn(trust);
            when(repo.getBta(eq(domain.getDomain()), anyInt())).thenReturn(bta);
            ConsensusState anchor = new ConsensusState(BigInteger.ONE, hash, new byte[0], 1L,
                    new byte[0], new byte[0], new byte[0]);
            assertThrows(CommitteeNodeInternalException.class, () -> service.commitAnchorState(lane, anchor));
            ValidatedConsensusStateWrapper parent = mock(ValidatedConsensusStateWrapper.class, RETURNS_DEEP_STUBS);
            when(parent.getValidatedConsensusState().getHeight()).thenReturn(BigInteger.ONE);
            when(repo.getValidatedConsensusState(eq(domain.getDomain()), any(BigInteger.class))).thenReturn(parent);
            ConsensusState next = new ConsensusState(BigInteger.TWO, new byte[]{3}, hash, 2L,
                    new byte[0], new byte[0], new byte[0]);
            next.setDomain(domain);
            assertThrows(CommitteeNodeInternalException.class, () -> service.commitConsensusState(lane, next));
            UniformCrosschainPacket ucp = mock(UniformCrosschainPacket.class, RETURNS_DEEP_STUBS);
            when(ucp.getSrcDomain()).thenReturn(domain);
            when(ucp.getSrcMessage().getProvableData().getHeightVal()).thenReturn(BigInteger.ONE);
            when(ucp.getSrcMessage().getProvableData().getBlockHashHex()).thenReturn("0102");
            when(repo.getValidatedConsensusState(domain.getDomain(), "0102")).thenReturn(parent);
            assertThrows(CommitteeNodeInternalException.class, () -> service.verifyUcp(lane, ucp));
            verify(plugins, times(3)).getHCDVSService(product);
            verify(repo, never()).setValidatedConsensusState(any());
        }
    }
}
