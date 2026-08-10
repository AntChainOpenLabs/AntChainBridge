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

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.CrossChainCertificateFactory;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.am.IAuthMessage;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.bta.BlockchainTrustAnchorFactory;
import com.alipay.antchain.bridge.commons.core.bta.IBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.monitor.IMonitorMessage;
import com.alipay.antchain.bridge.commons.core.monitor.MonitorMessageFactory;
import com.alipay.antchain.bridge.commons.core.sdp.ISDPMessage;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.commons.utils.crypto.HashAlgoEnum;
import com.alipay.antchain.bridge.ptc.committee.grpc.*;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.enums.MonitorTypeEnum;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.exception.*;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.TpBtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.IEndorseServiceRepository;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.server.interceptor.RequestTraceInterceptor;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IEndorserService;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IHcdvsPluginService;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IMonitorService;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@GrpcService(interceptors = RequestTraceInterceptor.class)
public class MonitorNodeServiceImpl extends CommitteeNodeServiceGrpc.CommitteeNodeServiceImplBase {

    @Value("${committee.id}")
    private String committeeId;

    @Value("${committee.node.id}")
    private String nodeId;

    @Resource
    private IHcdvsPluginService hcdvsPluginService;

    @Resource
    private IEndorserService endorserService;

    @Resource
    private IEndorseServiceRepository endorseServiceRepository;

    @Override
    public void heartbeat(HeartbeatRequest request, StreamObserver<Response> responseObserver) {
        try {
            responseObserver.onNext(
                    Response.newBuilder().setHeartbeatResp(
                            HeartbeatResponse.newBuilder()
                                    .setCommitteeId(committeeId)
                                    .setNodeId(nodeId)
                                    .addAllProducts(hcdvsPluginService.getAvailableProducts())
                    ).build()

            );
        } catch (CommitteeNodeException e) {
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(e.getCodeNum())
                            .setErrorMsg(e.getMsg())
                            .build()
            );
        } catch (Throwable t) {
            log.error("process heartbeat failed", t);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getErrorCodeNum())
                            .setErrorMsg(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getShortMsg())
                            .build()
            );
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void queryTpBta(QueryTpBtaRequest request, StreamObserver<Response> responseObserver) {
        try {
            log.info("query tpbta with lane (sender_domain:{}, receiver_domain:{}, sender_id:{}, receiver_id:{})",
                    request.getSenderDomain(), request.getReceiverDomain(), request.getSenderId(), request.getReceiverId());

            if (StrUtil.isEmpty(request.getSenderDomain())) {
                throw new InvalidRequestException("sender domain must not be empty");
            }
            TpBtaWrapper tpBtaWrapper = endorserService.queryMatchedTpBta(
                    new CrossChainLane(
                            new CrossChainDomain(request.getSenderDomain()),
                            StrUtil.isEmpty(request.getReceiverDomain()) ? null : new CrossChainDomain(request.getReceiverDomain()),
                            StrUtil.isEmpty(request.getSenderId()) ? null : CrossChainIdentity.fromHexStr(request.getSenderId()),
                            StrUtil.isEmpty(request.getReceiverId()) ? null : CrossChainIdentity.fromHexStr(request.getReceiverId())
                    )
            );
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(0)
                            .setQueryTpBtaResp(
                                    QueryTpBtaResponse.newBuilder()
                                            .setRawTpBta(ByteString.copyFrom(tpBtaWrapper.getTpbta().encode()))
                                            .build()
                            ).build()
            );
        } catch (CommitteeNodeException e) {
            log.error("query tpbta for lane (sender_domain:{}, receiver_domain:{}, sender_id:{}, receiver_id:{}) failed",
                    request.getSenderDomain(), request.getReceiverDomain(), request.getSenderId(), request.getReceiverId(), e);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(e.getCodeNum())
                            .setErrorMsg(e.getMsg())
                            .build()
            );
        } catch (Throwable t) {
            log.error("query tpbta for lane (sender_domain:{}, receiver_domain:{}, sender_id:{}, receiver_id:{}) failed with unexpected error: ",
                    request.getSenderDomain(), request.getReceiverDomain(), request.getSenderId(), request.getReceiverId(), t);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getErrorCodeNum())
                            .setErrorMsg(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getShortMsg())
                            .build()
            );
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void verifyBta(VerifyBtaRequest request, StreamObserver<Response> responseObserver) {
        IBlockchainTrustAnchor bta = null;
        try {
            AbstractCrossChainCertificate domainCert = CrossChainCertificateFactory.createCrossChainCertificate(request.getRawDomainCert().toByteArray());
            if (ObjectUtil.isNull(domainCert)) {
                throw new InvalidRequestException("domain cert is null");
            }
            bta = BlockchainTrustAnchorFactory.createBTA(request.getRawBta().toByteArray());
            if (ObjectUtil.isNull(bta)) {
                throw new InvalidRequestException("bta is null");
            }

            var tpBtaWrapper = endorserService.verifyBta(domainCert, bta);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(0)
                            .setVerifyBtaResp(
                                    VerifyBtaResponse.newBuilder()
                                            .setRawTpBta(ByteString.copyFrom(tpBtaWrapper.getTpbta().encode()))
                                            .build()
                            ).build()
            );
        } catch (InvalidBtaException e) {
            log.error("bta for domain {} can't pass the verification: ",
                    ObjectUtil.isNull(bta) || ObjectUtil.isNull(bta.getDomain()) ? "unknown" : bta.getDomain().getDomain(), e);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(e.getCodeNum())
                            .setErrorMsg(e.getMessage())
                            .build()
            );
        } catch (CommitteeNodeException e) {
            log.error("verify bta for domain {} failed",
                    ObjectUtil.isNull(bta) || ObjectUtil.isNull(bta.getDomain()) ? "unknown" : bta.getDomain().getDomain(), e);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(e.getCodeNum())
                            .setErrorMsg(e.getMsg())
                            .build()
            );
        } catch (Throwable t) {
            log.error("verify bta for domain {} failed with unexpected error: ",
                    ObjectUtil.isNull(bta) || ObjectUtil.isNull(bta.getDomain()) ? "unknown" : bta.getDomain().getDomain(), t);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getErrorCodeNum())
                            .setErrorMsg(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getShortMsg())
                            .build()
            );
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void commitAnchorState(CommitAnchorStateRequest request, StreamObserver<Response> responseObserver) {
        String domain = null;
        BigInteger height = null;
        String hash = null;
        try {
            var anchorState = ConsensusState.decode(request.getRawAnchorState().toByteArray());
            if (ObjectUtil.isNull(anchorState)) {
                throw new InvalidRequestException("anchor state is null");
            }
            var crossChainLane = CrossChainLane.decode(request.getCrossChainLane().toByteArray());
            if (ObjectUtil.isNull(crossChainLane)) {
                throw new InvalidRequestException("crossChainLane is null");
            }

            domain = anchorState.getDomain().getDomain();
            height = anchorState.getHeight();
            hash = HexUtil.encodeHexStr(anchorState.getHash());
            log.info("commit anchor state with height {} and hash {} for domain {}", height, hash, domain);

            var vcs = endorserService.commitAnchorState(crossChainLane, anchorState);

            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(0)
                            .setCommitAnchorStateResp(
                                    CommitAnchorStateResponse.newBuilder()
                                            .setRawValidatedConsensusState(ByteString.copyFrom(vcs.encode()))
                                            .build()
                            ).build()
            );
        } catch (CommitteeNodeException e) {
            log.error("commit anchor state with height {} and hash {} for domain {} failed", height, hash, domain, e);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(e.getCodeNum())
                            .setErrorMsg(e.getMsg())
                            .build()
            );
        } catch (Throwable t) {
            log.error("commit anchor state with height {} and hash {} for domain {} failed with unexpected error: ",
                    height, hash, domain, t);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getErrorCodeNum())
                            .setErrorMsg(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getShortMsg())
                            .build()
            );
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void commitConsensusState(CommitConsensusStateRequest request, StreamObserver<Response> responseObserver) {
        String domain = null;
        BigInteger height = null;
        String hash = null;
        try {
            var currState = ConsensusState.decode(request.getRawConsensusState().toByteArray());
            if (ObjectUtil.isNull(currState)) {
                throw new InvalidRequestException("consensus state is null");
            }
            var crossChainLane = CrossChainLane.decode(request.getCrossChainLane().toByteArray());
            if (ObjectUtil.isNull(crossChainLane)) {
                throw new InvalidRequestException("crossChainLane is null");
            }

            domain = currState.getDomain().getDomain();
            height = currState.getHeight();
            hash = HexUtil.encodeHexStr(currState.getHash());
            log.info("commit consensus state with height {} and hash {} for domain {}", height, hash, domain);

            var vcs = endorserService.commitConsensusState(crossChainLane, currState);

            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(0)
                            .setCommitConsensusStateResp(
                                    CommitConsensusStateResponse.newBuilder()
                                            .setRawValidatedConsensusState(ByteString.copyFrom(vcs.encode()))
                                            .build()
                            ).build()
            );
        } catch (InvalidConsensusStateException e) {
            log.error("verify consensus state with height {} and hash {} for domain {} failed: {}",
                    height, hash, domain, e.getMessage());
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(e.getCodeNum())
                            .setErrorMsg(e.getMessage())
                            .build()
            );
        } catch (CommitteeNodeException e) {
            log.error("commit consensus state with height {} and hash {} for domain {} failed", height, hash, domain, e);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(e.getCodeNum())
                            .setErrorMsg(e.getMsg())
                            .build()
            );
        } catch (Throwable t) {
            log.error("commit anchor state with height {} and hash {} for domain {} failed with unexpected error: ",
                    height, hash, domain, t);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getErrorCodeNum())
                            .setErrorMsg(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getShortMsg())
                            .build()
            );
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void verifyCrossChainMessage(VerifyCrossChainMessageRequest request, StreamObserver<Response> responseObserver) {
        String domain = null;
        CrossChainMessage.CrossChainMessageType msgType = null;
        String msgKey = null;
        try {
            var ucp = UniformCrosschainPacket.decode(request.getRawUcp().toByteArray());
            if (ObjectUtil.isNull(ucp)) {
                throw new InvalidRequestException("ucp is null");
            }
            var crossChainLane = CrossChainLane.decode(request.getCrossChainLane().toByteArray());
            if (ObjectUtil.isNull(crossChainLane)) {
                throw new InvalidRequestException("crossChainLane is null");
            }

            domain = ucp.getSrcDomain().getDomain();
            msgType = ucp.getSrcMessage().getType();
            msgKey = ObjectUtil.isNull(ucp.getCrossChainLane()) ?
                    HexUtil.encodeHexStr(ucp.getMessageHash(HashAlgoEnum.KECCAK_256))
                    : ucp.getCrossChainLane().getLaneKey();
            log.info("verify crosschain message (type: {}, msg: {}) from domain {}", msgType, msgKey, domain);

            IAuthMessage authMessage = AuthMessageFactory.createAuthMessage(ucp.getSrcMessage().getMessage());
            ISDPMessage sdpMessage = SDPMessageFactory.createSDPMessage(authMessage.getPayload());
            IMonitorMessage monitorMessage = MonitorMessageFactory.createMonitorMessage(sdpMessage.getPayload());

            CommitteeNodeProof proof = null;

            // 根据监管字段内容 判断是否需要监管
            if (monitorMessage.getMonitorType() == MonitorTypeEnum.MONITOR_OPEN.getCode()) {
                log.info("crosschain message: need monitor");
                proof = endorserService.verifyUcpWithMonitorSystem(crossChainLane, ucp);
            } else {
                // 不监管 把跨链消息发给监管系统即可
                log.info("crosschain message: don't need monitor");
                endorserService.relayUcpToMonitorSystem(ucp);
                proof = endorserService.verifyUcp(crossChainLane, ucp);
            }

            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(0)
                            .setVerifyCrossChainMessageResp(
                                    VerifyCrossChainMessageResponse.newBuilder()
                                            .setRawNodeProof(ByteString.copyFrom(proof.encode()))
                                            .build()
                            ).build()
            );
        } catch (InvalidCrossChainMessageException e) {
            log.error("crosschain message (type: {}, msg: {}) from domain {} 's verification not passed: {}",
                    msgType, msgKey, domain, e.getMessage());
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(e.getCodeNum())
                            .setErrorMsg(e.getMessage())
                            .build()
            );
        } catch (CommitteeNodeException e) {
            log.error("verify crosschain message (type: {}, msg: {}) from domain {} failed", msgType, msgKey, domain, e);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(e.getCodeNum())
                            .setErrorMsg(e.getMsg())
                            .build()
            );
        } catch (Throwable t) {
            log.error("verify crosschain message (type: {}, msg: {}) from domain {} failed with unexpected error: ",
                    msgType, msgKey, domain, t);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getErrorCodeNum())
                            .setErrorMsg(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getShortMsg())
                            .build()
            );
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void querySupportedBlockchainProducts(QuerySupportedBlockchainProductsRequest request, StreamObserver<Response> responseObserver) {
        try {
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(0)
                            .setQuerySupportedBlockchainProductsResp(
                                    QuerySupportedBlockchainProductsResponse.newBuilder()
                                            .addAllProducts(hcdvsPluginService.getAvailableProducts())
                                            .build()
                            ).build()
            );
        } catch (Throwable t) {
            log.error("query supported blockchain products failed with unexpected error: ", t);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getErrorCodeNum())
                            .setErrorMsg(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getShortMsg())
                            .build()
            );
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void queryBlockState(QueryBlockStateRequest request, StreamObserver<Response> responseObserver) {
        try {
            log.info("query latest block state for {}", request.getDomain());

            var wrapper = endorseServiceRepository.getLatestValidatedConsensusState(request.getDomain());
            if (ObjectUtil.isNotNull(wrapper)) {
                log.debug("get latest block state {} for {}", wrapper.getHeight().toString(), request.getDomain());
                responseObserver.onNext(
                        Response.newBuilder().setQueryBlockStateResp(
                                QueryBlockStateResponse.newBuilder().setRawValidatedBlockState(ByteString.copyFrom(
                                        new BlockState(
                                                wrapper.getValidatedConsensusState().getDomain(),
                                                wrapper.getValidatedConsensusState().getHash(),
                                                wrapper.getValidatedConsensusState().getHeight(),
                                                wrapper.getValidatedConsensusState().getStateTimestamp()
                                        ).encode()
                                ))
                        ).build()

                );
            }
        } catch (CommitteeNodeException e) {
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(e.getCodeNum())
                            .setErrorMsg(e.getMsg())
                            .build()
            );
        } catch (Throwable t) {
            log.error("process queryBlockState failed", t);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getErrorCodeNum())
                            .setErrorMsg(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getShortMsg())
                            .build()
            );
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void endorseBlockState(EndorseBlockStateRequest request, StreamObserver<Response> responseObserver) {
        String receiverDomain;
        String laneKey = null;
        BigInteger height = null;
        try {
            receiverDomain = request.getReceiverDomain();
            var tpbtaLane = CrossChainLane.decode(request.getCrossChainLane().toByteArray());
            height = new BigInteger(request.getHeight());
            laneKey = tpbtaLane.getLaneKey();

            log.info("endorse block state {} for {} with receiver domain {}", height, laneKey, receiverDomain);

            var resp = endorserService.endorseBlockState(tpbtaLane, receiverDomain, height);
            responseObserver.onNext(
                    Response.newBuilder().setEndorseBlockStateResp(
                            EndorseBlockStateResponse.newBuilder()
                                    .setBlockStateAuthMsg(ByteString.copyFrom(resp.getBlockStateAuthMsg().encode()))
                                    .setCommitteeNodeProof(ByteString.copyFrom(resp.getCommitteeNodeProof().encode()))
                                    .build()
                    ).build()
            );
        } catch (CommitteeNodeException e) {
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(e.getCodeNum())
                            .setErrorMsg(e.getMsg())
                            .build()
            );
        } catch (Throwable t) {
            log.error("process endorseBlockState failed for {} height {}", laneKey, StrUtil.toString(height), t);
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getErrorCodeNum())
                            .setErrorMsg(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR.getShortMsg())
                            .build()
            );
        } finally {
            responseObserver.onCompleted();
        }
    }
}
