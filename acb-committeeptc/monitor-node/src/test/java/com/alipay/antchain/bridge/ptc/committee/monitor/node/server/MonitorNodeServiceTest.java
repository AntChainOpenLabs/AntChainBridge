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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.server;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

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
import com.alipay.antchain.bridge.commons.core.ptc.*;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.commons.core.monitor.MonitorMessageFactory;
import com.alipay.antchain.bridge.commons.utils.crypto.HashAlgoEnum;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.plugins.spi.ptc.IHeteroChainDataVerifierService;
import com.alipay.antchain.bridge.plugins.spi.ptc.core.VerifyResult;
import com.alipay.antchain.bridge.ptc.committee.grpc.*;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.TestBase;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.BtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.DomainSpaceCertWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.TpBtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.ValidatedConsensusStateWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.IBCDNSRepository;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.IEndorseServiceRepository;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.ISystemConfigRepository;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IEndorserService;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IHcdvsPluginService;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeEndorseProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.EndorseBlockStateResp;
import com.alipay.antchain.bridge.ptc.committee.types.basic.NodePublicKeyEntry;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.CommitteeEndorseRoot;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.NodeEndorseInfo;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.OptionalEndorsePolicy;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.VerifyBtaExtension;
import com.google.protobuf.ByteString;
import io.grpc.internal.testing.StreamRecorder;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MonitorNodeServiceTest extends TestBase {

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
        nodeEndorseInfo.setNodeId("monitor-node");
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
        bta.setDomain(new CrossChainDomain("ether.com"));
        bta.setSubjectIdentity("test".getBytes());
        bta.setBcOwnerSigAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1);
        bta.setPtcOid(NODE_PTC_CERT.getCredentialSubjectInstance().getApplicant());
        bta.setSubjectProduct("ethereum2");
        bta.setInitHeight(BigInteger.valueOf(100L));
        bta.setInitBlockHash(RandomUtil.randomBytes(32));
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

        // need to replace "awesome antchain-bridge" with monitor message
        var monitorMessage = MonitorMessageFactory.createMonitorMessage(
                1,
                2,
                "this is a monitorMsg",
                "awesome antchain-bridge".getBytes()
        );

        var sdpMessage = SDPMessageFactory.createSDPMessage(
                1,
                new byte[32],
                crossChainLane.getReceiverDomain().getDomain(),
                crossChainLane.getReceiverId().getRawID(),
                -1,
                monitorMessage.encode()
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

    @Resource
    private MonitorNodeServiceImpl committeeNodeService;

    @Test
    @SneakyThrows
    public void testQueryTpBta() {
        when(endorseServiceRepository.getMatchedTpBta(any())).thenReturn(new TpBtaWrapper(tpbta));

        StreamRecorder<Response> responseObserver = StreamRecorder.create();
        committeeNodeService.queryTpBta(
                QueryTpBtaRequest.newBuilder()
                        .setSenderDomain(crossChainLane.getSenderDomain().getDomain())
                        .setSenderId(crossChainLane.getSenderIdHex())
                        .setReceiverDomain(crossChainLane.getReceiverDomain().getDomain())
                        .setReceiverId(crossChainLane.getReceiverIdHex())
                        .build(),
                responseObserver
        );
        Assert.assertTrue(responseObserver.awaitCompletion(5, TimeUnit.SECONDS));
        Assert.assertNull(responseObserver.getError());
        var results = responseObserver.getValues();
        Assert.assertEquals(1, results.size());
        Assert.assertArrayEquals(
                tpbta.encode(),
                results.getFirst().getQueryTpBtaResp().getRawTpBta().toByteArray()
        );
    }

    @Test
    @SneakyThrows
    public void testHeartbeat() {
        when(hcdvsPluginService.getAvailableProducts()).thenReturn(ListUtil.toList("mychain"));

        StreamRecorder<Response> responseObserver = StreamRecorder.create();
        committeeNodeService.heartbeat(
                HeartbeatRequest.newBuilder().build(),
                responseObserver
        );

        Assert.assertTrue(responseObserver.awaitCompletion(5, TimeUnit.SECONDS));
        Assert.assertNull(responseObserver.getError());

        var results = responseObserver.getValues();
        Assert.assertEquals(1, results.size());

        Assert.assertEquals(committeeId, results.getFirst().getHeartbeatResp().getCommitteeId());
        Assert.assertEquals("monitor-node", results.getFirst().getHeartbeatResp().getNodeId());
        Assert.assertEquals(1, results.getFirst().getHeartbeatResp().getProductsCount());
        Assert.assertEquals("mychain", results.getFirst().getHeartbeatResp().getProductsList().getFirst());
    }

    @Test
    @SneakyThrows
    public void testVerifyBta() {

        when(bcdnsRepository.getDomainSpaceCert(anyString())).thenReturn(
                new DomainSpaceCertWrapper(
                        CrossChainCertificateUtil.readCrossChainCertificateFromPem(DOT_COM_DOMAIN_SPACE_CERT.getBytes())
                )
        );

        when(systemConfigRepository.queryCurrentPtcAnchorVersion()).thenReturn(BigInteger.ONE);
        StreamRecorder<Response> responseObserver = StreamRecorder.create();
        committeeNodeService.verifyBta(
                VerifyBtaRequest.newBuilder()
                        .setRawBta(ByteString.copyFrom(bta.encode()))
                        .setRawDomainCert(ByteString.copyFrom(domainCert.encode()))
                        .build(),
                responseObserver
        );

        Assert.assertTrue(responseObserver.awaitCompletion(5, TimeUnit.SECONDS));
        Assert.assertNull(responseObserver.getError());

        var results = responseObserver.getValues();
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(0, results.getFirst().getCode());

        var tpbtaInResp = ThirdPartyBlockchainTrustAnchor.decode(results.getFirst().getVerifyBtaResp().getRawTpBta().toByteArray());
        Assert.assertTrue(
                ArrayUtil.equals(
                        tpbta.getEndorseRoot(),
                        tpbtaInResp.getEndorseRoot()
                )
        );
        Assert.assertEquals(
                tpbta.getCrossChainLane().getLaneKey(),
                tpbtaInResp.getCrossChainLane().getLaneKey()
        );
        Assert.assertTrue(
                ArrayUtil.equals(
                        ptcCrossChainCert.getCredentialSubject(),
                        tpbtaInResp.getSignerPtcCredentialSubject().encode()
                )
        );

        var endorseProof = CommitteeEndorseProof.decode(tpbtaInResp.getEndorseProof());
        Assert.assertEquals(
                committeeId,
                endorseProof.getCommitteeId()
        );

        var endorseRoot = CommitteeEndorseRoot.decode(tpbtaInResp.getEndorseRoot());
        Assert.assertEquals(
                1,
                endorseRoot.getEndorsers().size()
        );
        Assert.assertEquals(
                nodeId,
                endorseRoot.getEndorsers().getFirst().getNodeId()
        );
        Assert.assertEquals(
                "default",
                endorseRoot.getEndorsers().getFirst().getPublicKey().getKeyId()
        );
        Assert.assertTrue(
                endorseRoot.check(endorseProof, tpbtaInResp.getEncodedToSign())
        );
    }

    @Test
    @SneakyThrows
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

        StreamRecorder<Response> responseObserver = StreamRecorder.create();
        committeeNodeService.commitAnchorState(
                CommitAnchorStateRequest.newBuilder()
                        .setCrossChainLane(ByteString.copyFrom(crossChainLane.encode()))
                        .setRawAnchorState(ByteString.copyFrom(anchorState.encode()))
                        .build(),
                responseObserver
        );

        Assert.assertTrue(responseObserver.awaitCompletion(5, TimeUnit.SECONDS));
        Assert.assertNull(responseObserver.getError());

        var results = responseObserver.getValues();
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(0, results.getFirst().getCode());

        var vcsSigned = ValidatedConsensusState.decode(results.getFirst().getCommitAnchorStateResp().getRawValidatedConsensusState().toByteArray());
        Assert.assertTrue(
                new TpBtaWrapper(tpbta).getEndorseRoot().check(
                        CommitteeEndorseProof.decode(vcsSigned.getPtcProof()),
                        vcs.getEncodedToSign()
                )
        );
    }

    @Test
    @SneakyThrows
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

        StreamRecorder<Response> responseObserver = StreamRecorder.create();
        committeeNodeService.commitConsensusState(
                CommitConsensusStateRequest.newBuilder()
                        .setCrossChainLane(ByteString.copyFrom(crossChainLane.encode()))
                        .setRawConsensusState(ByteString.copyFrom(currState.encode()))
                        .build(),
                responseObserver
        );

        Assert.assertTrue(responseObserver.awaitCompletion(5, TimeUnit.SECONDS));
        Assert.assertNull(responseObserver.getError());

        var results = responseObserver.getValues();
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(0, results.getFirst().getCode());

        var vcsSigned = ValidatedConsensusState.decode(results.getFirst().getCommitConsensusStateResp().getRawValidatedConsensusState().toByteArray());
        Assert.assertTrue(
                new TpBtaWrapper(tpbta).getEndorseRoot().check(
                        CommitteeEndorseProof.decode(vcsSigned.getPtcProof()),
                        currVcs.getEncodedToSign()
                )
        );
    }

    @Test
    @SneakyThrows
    public void testVerifyCrossChainMessage() {
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

        StreamRecorder<Response> responseObserver = StreamRecorder.create();
        committeeNodeService.verifyCrossChainMessage(
                VerifyCrossChainMessageRequest.newBuilder()
                        .setCrossChainLane(ByteString.copyFrom(crossChainLane.encode()))
                        .setRawUcp(ByteString.copyFrom(ucp.encode()))
                        .build(),
                responseObserver
        );

        Assert.assertTrue(responseObserver.awaitCompletion(5, TimeUnit.SECONDS));
        Assert.assertNull(responseObserver.getError());

        var results = responseObserver.getValues();
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(0, results.getFirst().getCode());

        var nodeProof = CommitteeNodeProof.decode(results.getFirst().getVerifyCrossChainMessageResp().getRawNodeProof().toByteArray());
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
    @SneakyThrows
    public void testQueryBlockState() {
        var currVcs = BeanUtil.copyProperties(currState, ValidatedConsensusStateV1.class);
        currVcs.setPtcOid(ptcCrossChainCert.getCredentialSubjectInstance().getApplicant());
        currVcs.setTpbtaVersion(tpbta.getTpbtaVersion());
        currVcs.setPtcType(PTCTypeEnum.COMMITTEE);

        when(endorseServiceRepository.getLatestValidatedConsensusState(anyString())).thenReturn(new ValidatedConsensusStateWrapper(currVcs));

        StreamRecorder<Response> responseObserver = StreamRecorder.create();
        committeeNodeService.queryBlockState(
                QueryBlockStateRequest.newBuilder()
                        .setDomain(currVcs.getDomain().toString())
                        .build(),
                responseObserver
        );

        Assert.assertTrue(responseObserver.awaitCompletion(5, TimeUnit.SECONDS));
        Assert.assertNull(responseObserver.getError());

        var results = responseObserver.getValues();
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(0, results.getFirst().getCode());

        var blockState = BlockState.decode(results.getFirst().getQueryBlockStateResp().getRawValidatedBlockState().toByteArray());
        Assert.assertEquals(currVcs.getHeight(), blockState.getHeight());
        Assert.assertEquals(currVcs.getDomain(), blockState.getDomain());
        Assert.assertEquals(currVcs.getStateTimestamp(), blockState.getTimestamp());
        Assert.assertArrayEquals(currVcs.getHash(), blockState.getHash());
    }

    @Test
    @SneakyThrows
    public void testEndorseBlockState() {
        var currVcs = BeanUtil.copyProperties(currState, ValidatedConsensusStateV1.class);
        currVcs.setPtcOid(ptcCrossChainCert.getCredentialSubjectInstance().getApplicant());
        currVcs.setTpbtaVersion(tpbta.getTpbtaVersion());
        currVcs.setPtcType(PTCTypeEnum.COMMITTEE);

        when(endorseServiceRepository.getExactTpBta(any())).thenReturn(new TpBtaWrapper(tpbta));
        when(endorseServiceRepository.getValidatedConsensusState(anyString(), any(BigInteger.class))).thenReturn(new ValidatedConsensusStateWrapper(currVcs));
        when(endorseServiceRepository.hasValidatedConsensusState(anyString(), any())).thenReturn(true);

        StreamRecorder<Response> responseObserver = StreamRecorder.create();
        committeeNodeService.endorseBlockState(
                EndorseBlockStateRequest.newBuilder()
                        .setCrossChainLane(ByteString.copyFrom(crossChainLane.encode()))
                        .setHeight(currState.getHeight().toString())
                        .setReceiverDomain("receiverdomain")
                        .build(),
                responseObserver
        );

        Assert.assertTrue(responseObserver.awaitCompletion(5, TimeUnit.SECONDS));
        Assert.assertNull(responseObserver.getError());

        var results = responseObserver.getValues();
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(0, results.getFirst().getCode());

        var resp = new EndorseBlockStateResp(
                AuthMessageFactory.createAuthMessage(results.getFirst().getEndorseBlockStateResp().getBlockStateAuthMsg().toByteArray()),
                CommitteeNodeProof.decode(results.getFirst().getEndorseBlockStateResp().getCommitteeNodeProof().toByteArray())
        );
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
