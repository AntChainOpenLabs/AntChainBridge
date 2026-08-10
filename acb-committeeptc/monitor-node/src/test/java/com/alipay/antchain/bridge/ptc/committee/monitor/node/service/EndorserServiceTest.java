/*
 * Copyright 2024 Ant Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alipay.antchain.bridge.ptc.committee.monitor.node.service;

import java.math.BigInteger;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.RandomUtil;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.PTCCredentialSubject;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.bta.BlockchainTrustAnchorV1;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTypeEnum;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchorV1;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyProof;
import com.alipay.antchain.bridge.commons.core.ptc.ValidatedConsensusStateV1;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.commons.utils.crypto.HashAlgoEnum;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.plugins.spi.ptc.IHeteroChainDataVerifierService;
import com.alipay.antchain.bridge.plugins.spi.ptc.core.VerifyResult;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.TestBase;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.BtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.DomainSpaceCertWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.TpBtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.ValidatedConsensusStateWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.IBCDNSRepository;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.IEndorseServiceRepository;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.ISystemConfigRepository;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeEndorseProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.EndorseBlockStateResp;
import com.alipay.antchain.bridge.ptc.committee.types.basic.NodePublicKeyEntry;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.CommitteeEndorseRoot;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.NodeEndorseInfo;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.OptionalEndorsePolicy;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.VerifyBtaExtension;
import jakarta.annotation.Resource;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.Mockito.*;

public class EndorserServiceTest extends TestBase {

    private static final ThirdPartyBlockchainTrustAnchorV1 tpbta;

    private static final CrossChainLane crossChainLane;

    private static final ObjectIdentity oid;

    private static final BlockchainTrustAnchorV1 bta;

    private static final AbstractCrossChainCertificate domainCert;

    private static final ConsensusState anchorState;

    private static final ConsensusState currState;

    private static final UniformCrosschainPacket ucp;

    static {
        oid = new X509PubkeyInfoObjectIdentity(
                RAW_NODE_PTC_PUBLIC_KEY
        );

        var policy = new OptionalEndorsePolicy();
        policy.setThreshold(new OptionalEndorsePolicy.Threshold(OptionalEndorsePolicy.OperatorEnum.GREATER_THAN, -1));
        var nodeEndorseInfo = new NodeEndorseInfo();
        nodeEndorseInfo.setNodeId("node1");
        nodeEndorseInfo.setRequired(true);
        var nodePubkeyEntry = new NodePublicKeyEntry("default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());
        nodeEndorseInfo.setPublicKey(nodePubkeyEntry);
        crossChainLane = new CrossChainLane(new CrossChainDomain("test"), new CrossChainDomain("test"), CrossChainIdentity.fromHexStr("0000000000000000000000000000000000000000000000000000000000000001"), CrossChainIdentity.fromHexStr("0000000000000000000000000000000000000000000000000000000000000001"));
        tpbta = new ThirdPartyBlockchainTrustAnchorV1(
                1,
                BigInteger.ONE,
                (PTCCredentialSubject) NODE_PTC_CERT.getCredentialSubjectInstance(),
                crossChainLane,
                1,
                HashAlgoEnum.KECCAK_256,
                new CommitteeEndorseRoot(
                        "1",
                        policy,
                        ListUtil.toList(nodeEndorseInfo)
                ).encode(),
                CommitteeEndorseProof.builder()
                        .committeeId("1")
                        .sigs(ListUtil.toList(new CommitteeNodeProof(
                                "test",
                                SignAlgoEnum.KECCAK256_WITH_SECP256K1,
                                "".getBytes()
                        ))).build().encode()
        );

        bta = new BlockchainTrustAnchorV1();
        bta.setBcOwnerPublicKey(RAW_NODE_PTC_PUBLIC_KEY);
        bta.setDomain(new CrossChainDomain("antchain.com"));
        bta.setSubjectIdentity("test".getBytes());
        bta.setBcOwnerSigAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1);
        bta.setPtcOid(NODE_PTC_CERT.getCredentialSubjectInstance().getApplicant());
        bta.setInitHeight(BigInteger.valueOf(100L));
        bta.setInitBlockHash(RandomUtil.randomBytes(32));
        bta.setSubjectProduct("mychain");
        bta.setExtension(
                new VerifyBtaExtension(
                        CommitteeEndorseRoot.decode(tpbta.getEndorseRoot()),
                        crossChainLane
                ).encode()
        );
        bta.setSubjectVersion(0);
        bta.setAmId(RandomUtil.randomBytes(32));

        bta.sign(NODE_PTC_PRIVATE_KEY);

        domainCert = CrossChainCertificateUtil.readCrossChainCertificateFromPem(ANTCHAIN_DOT_COM_CERT.getBytes());

        anchorState = new ConsensusState(
                crossChainLane.getSenderDomain(),
                BigInteger.valueOf(100L),
                bta.getInitBlockHash(),
                RandomUtil.randomBytes(32),
                System.currentTimeMillis(),
                "{}".getBytes(),
                "{}".getBytes(),
                "{}".getBytes()
        );

        currState = new ConsensusState(
                crossChainLane.getSenderDomain(),
                BigInteger.valueOf(101L),
                RandomUtil.randomBytes(32),
                anchorState.getParentHash(),
                System.currentTimeMillis(),
                "{}".getBytes(),
                "{}".getBytes(),
                "{}".getBytes()
        );

        var sdpMessage = SDPMessageFactory.createSDPMessage(
                1,
                new byte[32],
                crossChainLane.getReceiverDomain().getDomain(),
                crossChainLane.getReceiverId().getRawID(),
                -1,
                "awesome antchain-bridge".getBytes()
        );

        var am = AuthMessageFactory.createAuthMessage(
                1,
                crossChainLane.getSenderId().getRawID(),
                0,
                sdpMessage.encode()
        );

        ucp = new UniformCrosschainPacket(
                crossChainLane.getSenderDomain(),
                CrossChainMessage.createCrossChainMessage(
                        CrossChainMessage.CrossChainMessageType.AUTH_MSG,
                        BigInteger.valueOf(101L),
                        DateUtil.current(),
                        currState.getHash(),
                        am.encode(),
                        "event".getBytes(),
                        "merkle proof".getBytes(),
                        RandomUtil.randomBytes(32)
                ),
                NODE_PTC_CERT.getCredentialSubjectInstance().getApplicant()
        );
    }

    @Value("${committee.id}")
    private String committeeId;

    @Value("${committee.node.id}")
    private String nodeId;

    @Resource
    private IEndorserService endorserService;

    @MockBean
    private IEndorseServiceRepository endorseServiceRepository;

    @MockBean
    private IBCDNSRepository bcdnsRepository;

    @MockBean
    private ISystemConfigRepository systemConfigRepository;

    @MockBean
    private IHcdvsPluginService hcdvsPluginService;

    @Resource
    private AbstractCrossChainCertificate ptcCrossChainCert;

    @Test
    public void testQueryTpBta() {
        when(endorseServiceRepository.getMatchedTpBta(any())).thenReturn(new TpBtaWrapper(tpbta));
        Assert.assertTrue(
                ArrayUtil.equals(
                        tpbta.encode(),
                        endorserService.queryMatchedTpBta(crossChainLane).getTpbta().encode()
                )
        );
    }

    @Test
    public void testVerifyBta() {

        when(bcdnsRepository.getDomainSpaceCert(anyString())).thenReturn(
                new DomainSpaceCertWrapper(
                        CrossChainCertificateUtil.readCrossChainCertificateFromPem(DOT_COM_DOMAIN_SPACE_CERT.getBytes())
                )
        );

        when(systemConfigRepository.queryCurrentPtcAnchorVersion()).thenReturn(BigInteger.ONE);

        var tpbtaWrapper = endorserService.verifyBta(domainCert, bta);

        Assert.assertTrue(
                ArrayUtil.equals(
                        tpbta.getEndorseRoot(),
                        tpbtaWrapper.getEndorseRoot().encode()
                )
        );
        Assert.assertEquals(
                tpbta.getCrossChainLane().getLaneKey(),
                tpbtaWrapper.getCrossChainLane().getLaneKey()
        );
        Assert.assertTrue(
                ArrayUtil.equals(
                        ptcCrossChainCert.getCredentialSubject(),
                        tpbtaWrapper.getTpbta().getSignerPtcCredentialSubject().encode()
                )
        );
        Assert.assertEquals(
                committeeId,
                tpbtaWrapper.getEndorseProof().getCommitteeId()
        );
        Assert.assertEquals(
                1,
                tpbtaWrapper.getEndorseRoot().getEndorsers().size()
        );
        Assert.assertEquals(
                nodeId,
                tpbtaWrapper.getEndorseRoot().getEndorsers().getFirst().getNodeId()
        );
        Assert.assertEquals(
                "default",
                tpbtaWrapper.getEndorseRoot().getEndorsers().getFirst().getPublicKey().getKeyId()
        );
        Assert.assertTrue(
                tpbtaWrapper.getEndorseRoot().check(tpbtaWrapper.getEndorseProof(), tpbtaWrapper.getTpbta().getEncodedToSign())
        );
    }

    @Test
    public void testCommitAnchorState() {
        var hcdvs = mock(IHeteroChainDataVerifierService.class);
        when(hcdvs.verifyAnchorConsensusState(any(), any())).thenReturn(VerifyResult.builder().success(true).build());
        when(endorseServiceRepository.getMatchedTpBta(any())).thenReturn(new TpBtaWrapper(tpbta));
        when(endorseServiceRepository.getBta(anyString())).thenReturn(new BtaWrapper(bta));
        when(hcdvsPluginService.getHCDVSService(anyString())).thenReturn(hcdvs);

        var vcs = BeanUtil.copyProperties(anchorState, ValidatedConsensusStateV1.class);
        vcs.setPtcOid(ptcCrossChainCert.getCredentialSubjectInstance().getApplicant());
        vcs.setTpbtaVersion(tpbta.getTpbtaVersion());
        vcs.setPtcType(PTCTypeEnum.COMMITTEE);

        var vcsSigned = endorserService.commitAnchorState(crossChainLane, anchorState);
        Assert.assertTrue(
                new TpBtaWrapper(tpbta).getEndorseRoot().check(
                        CommitteeEndorseProof.decode(vcsSigned.getPtcProof()),
                        vcs.getEncodedToSign()
                )
        );
    }

    @Test
    public void testCommitConsensusState() {
        var vcs = BeanUtil.copyProperties(anchorState, ValidatedConsensusStateV1.class);
        vcs.setPtcOid(ptcCrossChainCert.getCredentialSubjectInstance().getApplicant());
        vcs.setTpbtaVersion(tpbta.getTpbtaVersion());
        vcs.setPtcType(PTCTypeEnum.COMMITTEE);

        var currVcs = BeanUtil.copyProperties(currState, ValidatedConsensusStateV1.class);
        currVcs.setPtcOid(ptcCrossChainCert.getCredentialSubjectInstance().getApplicant());
        currVcs.setTpbtaVersion(tpbta.getTpbtaVersion());
        currVcs.setPtcType(PTCTypeEnum.COMMITTEE);

        var hcdvs = mock(IHeteroChainDataVerifierService.class);
        when(hcdvs.verifyConsensusState(any(), any())).thenReturn(VerifyResult.builder().success(true).build());
        when(endorseServiceRepository.getMatchedTpBta(any())).thenReturn(new TpBtaWrapper(tpbta));
        when(endorseServiceRepository.getBta(anyString())).thenReturn(new BtaWrapper(bta));
        when(endorseServiceRepository.getValidatedConsensusState(anyString(), anyString())).thenReturn(new ValidatedConsensusStateWrapper(vcs));
        when(hcdvsPluginService.getHCDVSService(anyString())).thenReturn(hcdvs);

        var vcsSigned = endorserService.commitConsensusState(crossChainLane, currState);
        Assert.assertTrue(
                new TpBtaWrapper(tpbta).getEndorseRoot().check(
                        CommitteeEndorseProof.decode(vcsSigned.getPtcProof()),
                        currVcs.getEncodedToSign()
                )
        );
    }

    @Test
    public void testVerifyUcp() {
        var currVcs = BeanUtil.copyProperties(currState, ValidatedConsensusStateV1.class);
        currVcs.setPtcOid(ptcCrossChainCert.getCredentialSubjectInstance().getApplicant());
        currVcs.setTpbtaVersion(tpbta.getTpbtaVersion());
        currVcs.setPtcType(PTCTypeEnum.COMMITTEE);

        var hcdvs = mock(IHeteroChainDataVerifierService.class);
        when(hcdvs.verifyCrossChainMessage(any(), any())).thenReturn(VerifyResult.builder().success(true).build());
        when(hcdvs.parseMessageFromLedgerData(any())).thenReturn(ucp.getSrcMessage().getMessage());
        when(endorseServiceRepository.getExactTpBta(any())).thenReturn(new TpBtaWrapper(tpbta));
        when(endorseServiceRepository.getBta(anyString())).thenReturn(new BtaWrapper(bta));
        when(endorseServiceRepository.getValidatedConsensusState(anyString(), anyString())).thenReturn(new ValidatedConsensusStateWrapper(currVcs));
        when(hcdvsPluginService.getHCDVSService(anyString())).thenReturn(hcdvs);

        var nodeProof = endorserService.verifyUcp(crossChainLane, ucp);
        Assert.assertTrue(
                new TpBtaWrapper(tpbta).getEndorseRoot().check(
                        CommitteeEndorseProof.builder()
                                .committeeId(committeeId)
                                .sigs(ListUtil.toList(nodeProof))
                                .build(),
                        ThirdPartyProof.create(
                                tpbta.getTpbtaVersion(),
                                ucp.getSrcMessage().getMessage(),
                                crossChainLane
                        ).getEncodedToSign()
                )
        );
    }

    @Test
    public void testEndorseBlockState() {
        var currVcs = BeanUtil.copyProperties(currState, ValidatedConsensusStateV1.class);
        currVcs.setPtcOid(ptcCrossChainCert.getCredentialSubjectInstance().getApplicant());
        currVcs.setTpbtaVersion(tpbta.getTpbtaVersion());
        currVcs.setPtcType(PTCTypeEnum.COMMITTEE);

        when(endorseServiceRepository.getExactTpBta(any())).thenReturn(new TpBtaWrapper(tpbta));
        when(endorseServiceRepository.getValidatedConsensusState(anyString(), any(BigInteger.class))).thenReturn(new ValidatedConsensusStateWrapper(currVcs));
        when(endorseServiceRepository.hasValidatedConsensusState(anyString(), any())).thenReturn(true);

        EndorseBlockStateResp resp = endorserService.endorseBlockState(tpbta.getCrossChainLane(), "receiverdomain", currVcs.getHeight());
        Assert.assertEquals(
                "receiverdomain",
                SDPMessageFactory.createSDPMessage(resp.getBlockStateAuthMsg().getPayload()).getTargetDomain().getDomain()
        );
        Assert.assertArrayEquals(
                currVcs.getHash(),
                resp.getBlockState().getHash()
        );
        Assert.assertEquals(
                currVcs.getHeight(),
                resp.getBlockState().getHeight()
        );
        Assert.assertEquals(
                currVcs.getStateTimestamp(),
                resp.getBlockState().getTimestamp()
        );
        Assert.assertTrue(
                new TpBtaWrapper(tpbta).getEndorseRoot().check(
                        CommitteeEndorseProof.builder()
                                .committeeId(committeeId)
                                .sigs(ListUtil.toList(resp.getCommitteeNodeProof()))
                                .build(),
                        ThirdPartyProof.create(
                                tpbta.getTpbtaVersion(),
                                resp.getBlockStateAuthMsg().encode(),
                                crossChainLane
                        ).getEncodedToSign()
                )
        );
    }
}
