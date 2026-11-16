package com.alipay.antchain.bridge.ptc.committee.types.network.nodeclient;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.bta.IBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ValidatedConsensusState;
import com.alipay.antchain.bridge.ptc.committee.grpc.*;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.EndorseBlockStateResp;
import com.alipay.antchain.bridge.ptc.committee.types.network.EndpointInfo;
import com.google.protobuf.ByteString;
import io.grpc.TlsChannelCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import sun.security.util.DerValue;
import sun.security.util.HostnameChecker;
import sun.security.x509.X500Name;

@Slf4j
public class GrpcNodeClient implements INodeClient {

    private CommitteeNodeServiceGrpc.CommitteeNodeServiceBlockingStub stub;

    private EndpointInfo endpointInfo;

    public GrpcNodeClient(
            EndpointInfo endpointInfo,
            String tlsClientPemPkcs8Key,
            String tlsClientPemCert
    ) {
        createStub(endpointInfo, tlsClientPemPkcs8Key, tlsClientPemCert);
        this.endpointInfo = endpointInfo;
    }

    @SneakyThrows
    private void createStub(
            EndpointInfo endpointInfo,
            String tlsClientPemPkcs8Key,
            String tlsClientPemCert
    ) {
        DerValue derValue = HostnameChecker.getSubjectX500Name(endpointInfo.getX509TlsCert())
                .findMostSpecificAttribute(X500Name.commonName_oid);
        String commonName = derValue.getAsString();

        TlsChannelCredentials.Builder tlsBuilder = TlsChannelCredentials.newBuilder();
        if (StrUtil.isAllNotEmpty(tlsClientPemPkcs8Key, tlsClientPemCert)) {
            tlsBuilder.keyManager(
                    new ByteArrayInputStream(tlsClientPemCert.getBytes()),
                    new ByteArrayInputStream(tlsClientPemPkcs8Key.getBytes())
            );
        }
        tlsBuilder.trustManager(
                new ByteArrayInputStream(endpointInfo.getTlsCert().getBytes())
        );
        NettyChannelBuilder channelBuilder = NettyChannelBuilder.forAddress(
                endpointInfo.getEndpoint().getHost(),
                endpointInfo.getEndpoint().getPort(),
                tlsBuilder.build()
        ).overrideAuthority(commonName);
        stub = CommitteeNodeServiceGrpc.newBlockingStub(channelBuilder.build());
    }

    @Override
    public HeartbeatResp heartbeat() {
        Response resp = stub.heartbeat(HeartbeatRequest.getDefaultInstance());
        if (ObjectUtil.isNull(resp)) {
            throw new RuntimeException("null response from committee node grpc client");
        }
        if (resp.getCode() != 0) {
            throw new RuntimeException(StrUtil.format("response shows request failed: ( code: {}, msg: {})", resp.getCode(), resp.getErrorMsg()));
        }
        return new HeartbeatResp(
                resp.getHeartbeatResp().getCommitteeId(),
                resp.getHeartbeatResp().getNodeId(),
                resp.getHeartbeatResp().getProductsList()
        );
    }

    @Override
    public ThirdPartyBlockchainTrustAnchor queryTpBta(CrossChainLane lane) {
        Response response = stub.queryTpBta(
                QueryTpBtaRequest.newBuilder()
                        .setSenderDomain(lane.getSenderDomain().getDomain())
                        .setSenderId(lane.getSenderIdHex())
                        .setReceiverDomain(lane.getReceiverDomain().getDomain())
                        .setReceiverId(lane.getReceiverIdHex())
                        .build()
        );
        if (ObjectUtil.isNull(response)) {
            throw new RuntimeException("null response from committee node grpc client");
        }
        if (response.getCode() != 0) {
            throw new RuntimeException(StrUtil.format("response shows request failed: ( code: {}, msg: {})", response.getCode(), response.getErrorMsg()));
        }
        return ThirdPartyBlockchainTrustAnchor.decode(response.getQueryTpBtaResp().getRawTpBta().toByteArray());
    }

    @Override
    public ThirdPartyBlockchainTrustAnchor verifyBta(AbstractCrossChainCertificate domainCert, IBlockchainTrustAnchor bta) {
        Response response = stub.verifyBta(
                VerifyBtaRequest.newBuilder()
                        .setRawDomainCert(ByteString.copyFrom(domainCert.encode()))
                        .setRawBta(ByteString.copyFrom(bta.encode()))
                        .build()
        );
        if (ObjectUtil.isNull(response)) {
            throw new RuntimeException("null response from committee node grpc client");
        }
        if (response.getCode() != 0) {
            throw new RuntimeException(StrUtil.format("response shows request failed: ( code: {}, msg: {})", response.getCode(), response.getErrorMsg()));
        }
        return ThirdPartyBlockchainTrustAnchor.decode(response.getVerifyBtaResp().getRawTpBta().toByteArray());
    }

    @Override
    public ValidatedConsensusState commitAnchorState(CrossChainLane crossChainLane, ConsensusState consensusState) {
        Response response = stub.commitAnchorState(
                CommitAnchorStateRequest.newBuilder()
                        .setRawAnchorState(ByteString.copyFrom(consensusState.encode()))
                        .setCrossChainLane(ByteString.copyFrom(crossChainLane.encode()))
                        .build()
        );
        if (ObjectUtil.isNull(response)) {
            throw new RuntimeException("null response from committee node grpc client");
        }
        if (response.getCode() != 0) {
            throw new RuntimeException(StrUtil.format("response shows request failed: ( code: {}, msg: {})", response.getCode(), response.getErrorMsg()));
        }
        return ValidatedConsensusState.decode(response.getCommitAnchorStateResp().getRawValidatedConsensusState().toByteArray());
    }

    @Override
    public ValidatedConsensusState commitConsensusState(CrossChainLane crossChainLane, ConsensusState consensusState) {
        Response response = stub.commitConsensusState(
                CommitConsensusStateRequest.newBuilder()
                        .setRawConsensusState(ByteString.copyFrom(consensusState.encode()))
                        .setCrossChainLane(ByteString.copyFrom(crossChainLane.encode()))
                        .build()
        );
        if (ObjectUtil.isNull(response)) {
            throw new RuntimeException("null response from committee node grpc client");
        }
        if (response.getCode() != 0) {
            throw new RuntimeException(StrUtil.format("response shows request failed: ( code: {}, msg: {})", response.getCode(), response.getErrorMsg()));
        }
        return ValidatedConsensusState.decode(response.getCommitConsensusStateResp().getRawValidatedConsensusState().toByteArray());
    }

    @Override
    public CommitteeNodeProof verifyCrossChainMessage(CrossChainLane crossChainLane, UniformCrosschainPacket packet) {
        return verifyCrossChainMessageWithResult(crossChainLane, packet).getNodeProof();
    }

    @Override
    public NodeVerifyCrossChainMessageResult verifyCrossChainMessageWithResult(
            CrossChainLane crossChainLane,
            UniformCrosschainPacket packet
    ) {
        return verifyCrossChainMessageWithResult(crossChainLane, packet, "");
    }

    @Override
    public NodeVerifyCrossChainMessageResult verifyCrossChainMessageWithResult(
            CrossChainLane crossChainLane,
            UniformCrosschainPacket packet,
            String ucpId
    ) {
        Response response = stub.verifyCrossChainMessage(
                VerifyCrossChainMessageRequest.newBuilder()
                        .setRawUcp(ByteString.copyFrom(packet.encode()))
                        .setCrossChainLane(ByteString.copyFrom(crossChainLane.encode()))
                        .setUcpId(StrUtil.nullToEmpty(ucpId))
                        .build()
        );
        if (ObjectUtil.isNull(response)) {
            throw new RuntimeException("null response from committee node grpc client");
        }
        if (response.getCode() != 0) {
            throw new RuntimeException(StrUtil.format("response shows request failed: ( code: {}, msg: {})", response.getCode(), response.getErrorMsg()));
        }
        VerifyCrossChainMessageResponse verifyResponse = response.getVerifyCrossChainMessageResp();
        return new NodeVerifyCrossChainMessageResult(
                CommitteeNodeProof.decode(verifyResponse.getRawNodeProof().toByteArray()),
                verifyResponse.getRegulationStatus(),
                verifyResponse.getRegulationReason()
        );
    }

    @Override
    public BlockState queryBlockState(CrossChainDomain blockchainDomain) {
        Response response = stub.queryBlockState(
                QueryBlockStateRequest.newBuilder()
                        .setDomain(blockchainDomain.getDomain())
                        .build()
        );
        if (ObjectUtil.isNull(response)) {
            throw new RuntimeException("null response from committee node grpc client");
        }
        if (response.getCode() != 0) {
            throw new RuntimeException(StrUtil.format("response shows request failed: ( code: {}, msg: {})", response.getCode(), response.getErrorMsg()));
        }
        if (!response.hasQueryBlockStateResp() || response.getQueryBlockStateResp().getRawValidatedBlockState().isEmpty()) {
            log.debug("null block state for domain {} from node {}", blockchainDomain.getDomain(), this.endpointInfo.getNodeId());
            return null;
        }
        return BlockState.decode(response.getQueryBlockStateResp().getRawValidatedBlockState().toByteArray());
    }

    @Override
    public EndorseBlockStateResp endorseBlockState(CrossChainLane tpbtaLane, CrossChainDomain receiverDomain, BigInteger height) {
        Response response = stub.endorseBlockState(
                EndorseBlockStateRequest.newBuilder()
                        .setCrossChainLane(ByteString.copyFrom(tpbtaLane.encode()))
                        .setHeight(height.toString())
                        .setReceiverDomain(receiverDomain.getDomain())
                        .build()
        );
        if (ObjectUtil.isNull(response)) {
            throw new RuntimeException("null response from committee node grpc client");
        }
        if (response.getCode() != 0) {
            throw new RuntimeException(StrUtil.format("response shows request failed: ( code: {}, msg: {})", response.getCode(), response.getErrorMsg()));
        }
        return new EndorseBlockStateResp(
                AuthMessageFactory.createAuthMessage(response.getEndorseBlockStateResp().getBlockStateAuthMsg().toByteArray()),
                CommitteeNodeProof.decode(response.getEndorseBlockStateResp().getCommitteeNodeProof().toByteArray())
        );
    }
}
