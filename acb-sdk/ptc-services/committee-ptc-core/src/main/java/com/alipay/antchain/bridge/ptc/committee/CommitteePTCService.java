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

package com.alipay.antchain.bridge.ptc.committee;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.bta.IBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyProof;
import com.alipay.antchain.bridge.commons.core.ptc.ValidatedConsensusState;
import com.alipay.antchain.bridge.ptc.committee.config.CommitteePtcConfig;
import com.alipay.antchain.bridge.ptc.committee.exception.*;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeEndorseProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.EndorseBlockStateResp;
import com.alipay.antchain.bridge.ptc.committee.types.network.EndpointInfo;
import com.alipay.antchain.bridge.ptc.committee.types.network.nodeclient.HeartbeatResp;
import com.alipay.antchain.bridge.ptc.committee.types.network.nodeclient.Node;
import com.alipay.antchain.bridge.ptc.committee.types.network.nodeclient.NodeVerifyCrossChainMessageResult;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.CommitteeEndorseRoot;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.NodeEndorseInfo;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.OptionalEndorsePolicy;
import com.alipay.antchain.bridge.ptc.service.IPTCService;
import com.alipay.antchain.bridge.ptc.types.PtcFeatureDescriptor;
import com.alipay.antchain.bridge.ptc.types.PTCVerifyCrossChainMessageResult;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
@NoArgsConstructor
public class CommitteePTCService implements IPTCService {

    private static final PtcFeatureDescriptor ptcFeatureDescriptor;

    static {
        ptcFeatureDescriptor = new PtcFeatureDescriptor();
        ptcFeatureDescriptor.enableStorage();
    }

    private CommitteePtcConfig config;

    private ExecutorService reqExecutorService;

    private final ScheduledExecutorService heartbeatExecutorService = new ScheduledThreadPoolExecutor(
            1, new ThreadFactoryBuilder().setNameFormat("committee-ptc-heartbeat-%d").build()
    );

    private final Map<String, Node> nodeMap = new ConcurrentHashMap<>();

    @Override
    public void startup(byte[] rawConf) {
        config = CommitteePtcConfig.parseFrom(rawConf);
        reqExecutorService = new ThreadPoolExecutor(
                config.getRequestThreadsPoolCoreSize(),
                config.getRequestThreadsPoolMaxSize(),
                3000,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32),
                new ThreadFactoryBuilder().setNameFormat("committee-ptc-service-%d").build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        connectCommitteeNodes();
        startHeartbeatWork();
    }

    @Override
    public PtcFeatureDescriptor getPtcFeatureDescriptor() {
        return ptcFeatureDescriptor;
    }

    /**
     * 查询ptc对于某个跨链通道的背书，背书已经存在，直接查询即可
     * 会向每个节点请求跨链通道背书queryTpBta，最后请求的数量需要大于一定阈值认为请求成功
     *
     * @param lane
     * @return
     */
    @Override
    public ThirdPartyBlockchainTrustAnchor queryThirdPartyBlockchainTrustAnchor(CrossChainLane lane) {
        try {
            checkNodes();

            List<Future<ThirdPartyBlockchainTrustAnchor>> resFutureList = reqExecutorService.invokeAll(
                    nodeMap.entrySet().stream()
                            .filter(entry -> entry.getValue().isAvailable())
                            .map(
                                    entry -> (Callable<ThirdPartyBlockchainTrustAnchor>) () -> {
                                        log.debug("Query third party blockchain trust anchor with node {}", entry.getKey());
                                        return entry.getValue().getNodeClient().queryTpBta(lane);
                                    }
                            ).collect(Collectors.toList())
            );
            List<ThirdPartyBlockchainTrustAnchor> resList = resFutureList.stream().map(
                    future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new RuntimeException("failed to get tpbta from future object: ", e);
                        }
                    }
            ).collect(Collectors.toList());
            if (ObjectUtil.isEmpty(resList)) {
                throw new RuntimeException(
                        StrUtil.format("none tpbta from committee {} with {} nodes active",
                                config.getCommitteeNetworkInfo().getCommitteeId(), nodeMap.size())
                );
            }

            ThirdPartyBlockchainTrustAnchor tpbta = assembleTpBta(resList);
            CommitteeEndorseProof proof = CommitteeEndorseProof.decode(tpbta.getEndorseProof());
            if (!isThresholdSatisfied(
                    proof.getSigs().size(),
                    config.getCommitteeNetworkInfo().getNodes().size()
            )) {
                throw new EndorsementNotEnoughException(
                        "Threshold not satisfied: tpbta requires at least over 2/3 endorsements, but only {}/{}",
                        proof.getSigs().size(),
                        config.getCommitteeNetworkInfo().getNodes().size()
                );
            }

            return tpbta;
        } catch (Exception e) {
            throw new RuntimeException(
                    StrUtil.format("Failed to query TPBTA for {} from committee {}: ",
                            lane.getLaneKey(), config.getCommitteeNetworkInfo().getCommitteeId()),
                    e
            );
        }
    }

    /**
     * 验证指定域名链的跨链背书（BTA），返回验证后的TP-BTA
     * 需要向每个节点请求bta验证（verifyBta），最后请求的数量需要大于一定阈值认为查询成功
     *
     * @param domainCert
     * @param blockchainTrustAnchor
     * @return
     */
    @Override
    public ThirdPartyBlockchainTrustAnchor verifyBlockchainTrustAnchor(AbstractCrossChainCertificate domainCert, IBlockchainTrustAnchor blockchainTrustAnchor) {
        try {
            checkNodes();

            List<Future<ThirdPartyBlockchainTrustAnchor>> resFutureList = reqExecutorService.invokeAll(
                    nodeMap.entrySet().stream()
                            .filter(entry -> entry.getValue().isAvailable())
                            .map(
                                    entry -> (Callable<ThirdPartyBlockchainTrustAnchor>) () -> {
                                        log.debug("Verify third party blockchain trust anchor with node {}", entry.getKey());
                                        return entry.getValue().getNodeClient().verifyBta(domainCert, blockchainTrustAnchor);
                                    }
                            ).collect(Collectors.toList())
            );
            List<ThirdPartyBlockchainTrustAnchor> resList = resFutureList.stream().map(
                    future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new RuntimeException("failed to get tpbta from future object: ", e);
                        }
                    }
            ).collect(Collectors.toList());
            if (ObjectUtil.isEmpty(resList)) {
                throw new RuntimeException(
                        StrUtil.format("none tpbta verified from committee {} with {} nodes active",
                                config.getCommitteeNetworkInfo().getCommitteeId(), getAvailableNodesCount())
                );
            }
            ThirdPartyBlockchainTrustAnchor tpbta = assembleTpBta(resList);
            CommitteeEndorseProof proof = CommitteeEndorseProof.decode(tpbta.getEndorseProof());
            if (!isThresholdSatisfied(
                    proof.getSigs().size(),
                    config.getCommitteeNetworkInfo().getNodes().size()
            )) {
                throw new EndorsementNotEnoughException(
                        "Threshold not satisfied: tpbta requires at least over 2/3 endorsements, but only {}/{}",
                        proof.getSigs().size(),
                        config.getCommitteeNetworkInfo().getNodes().size()
                );
            }

            return tpbta;
        } catch (CommitteeBaseException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(
                    StrUtil.format("Failed to verify BTA for {} from committee {}: ",
                            blockchainTrustAnchor.getDomain().getDomain(), config.getCommitteeNetworkInfo().getCommitteeId()),
                    t
            );
        }
    }

    /**
     * 验证链的初始共识状态，向所有节点提交链的共识状态 commitAnchorState
     * 最终返回已验证共识状态ValidatedConsensusState
     *
     * @param bta         链本身的验证信息
     * @param tpbta       ptc关于链bta的背书
     * @param anchorState 链的共识状态
     * @return
     */
    @Override
    public ValidatedConsensusState commitAnchorState(IBlockchainTrustAnchor bta, ThirdPartyBlockchainTrustAnchor tpbta, ConsensusState anchorState) {
        try {
            CommitteeEndorseRoot root = CommitteeEndorseRoot.decode(tpbta.getEndorseRoot());
            List<String> requiredNodeIds = root.getEndorsers().stream().filter(NodeEndorseInfo::isRequired).map(NodeEndorseInfo::getNodeId).collect(Collectors.toList());
            List<String> optionalNodeIds = root.getEndorsers().stream().filter(NodeEndorseInfo::isOptional).map(NodeEndorseInfo::getNodeId).collect(Collectors.toList());

            checkNodes(requiredNodeIds, optionalNodeIds, root.getPolicy());

            List<Future<ValidatedConsensusState>> resFutureList = reqExecutorService.invokeAll(
                    nodeMap.entrySet().stream()
                            .filter(entry -> entry.getValue().isAvailable())
                            .filter(
                                    entry -> requiredNodeIds.contains(entry.getValue().getEndpointInfo().getNodeId())
                                            || optionalNodeIds.contains(entry.getValue().getEndpointInfo().getNodeId())
                            ).map(
                                    entry -> (Callable<ValidatedConsensusState>) () -> {
                                        log.debug("Verify anchor state anchor with node {} {}", entry.getKey(), entry.getValue().getEndpointInfo().getEndpoint().getUrl());
                                        return entry.getValue().getNodeClient().commitAnchorState(tpbta.getCrossChainLane(), anchorState);
                                    }
                            ).collect(Collectors.toList())
            );
            ValidatedConsensusState finalVcs = collectValidatedConsensusStateResults(resFutureList);

            checkEndorsementsOrThrow(requiredNodeIds, optionalNodeIds, root.getPolicy(), CommitteeEndorseProof.decode(finalVcs.getPtcProof()));

            return finalVcs;
        } catch (CommitteeBaseException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(
                    StrUtil.format("Failed to verify anchor state ( domain: {}, height: {}, hash: {} ) from committee {}: ",
                            anchorState.getDomain().getDomain(),
                            anchorState.getHeight().toString(),
                            anchorState.getHashHex(),
                            config.getCommitteeNetworkInfo().getCommitteeId()
                    ), t
            );
        }
    }

    /**
     * 提交共识状态以获取验证过的共识状态，返回已验证共识状态（非初始共识状态） commitConsensusState
     *
     * @param tpbta
     * @param preValidatedConsensusState
     * @param consensusState
     * @return
     */
    @Override
    public ValidatedConsensusState commitConsensusState(ThirdPartyBlockchainTrustAnchor tpbta, ValidatedConsensusState preValidatedConsensusState, ConsensusState consensusState) {
        try {
            CommitteeEndorseRoot root = CommitteeEndorseRoot.decode(tpbta.getEndorseRoot());
            List<String> requiredNodeIds = root.getEndorsers().stream()
                    .filter(NodeEndorseInfo::isRequired)
                    .map(NodeEndorseInfo::getNodeId)
                    .collect(Collectors.toList());
            List<String> optionalNodeIds = root.getEndorsers().stream()
                    .filter(NodeEndorseInfo::isOptional)
                    .map(NodeEndorseInfo::getNodeId)
                    .collect(Collectors.toList());

            checkNodes(requiredNodeIds, optionalNodeIds, root.getPolicy());

            List<Future<ValidatedConsensusState>> resFutureList = reqExecutorService.invokeAll(
                    nodeMap.entrySet().stream()
                            .filter(entry -> entry.getValue().isAvailable())
                            .filter(
                                    entry -> requiredNodeIds.contains(entry.getValue().getEndpointInfo().getNodeId())
                                            || optionalNodeIds.contains(entry.getValue().getEndpointInfo().getNodeId())
                            ).map(
                                    entry -> (Callable<ValidatedConsensusState>) () -> {
                                        log.debug("Verify consensus state anchor with node {} {}", entry.getKey(), entry.getValue().getEndpointInfo().getEndpoint().getUrl());
                                        return entry.getValue().getNodeClient().commitConsensusState(tpbta.getCrossChainLane(), consensusState);
                                    }
                            ).collect(Collectors.toList())
            );
            ValidatedConsensusState finalVcs = collectValidatedConsensusStateResults(resFutureList);

            checkEndorsementsOrThrow(requiredNodeIds, optionalNodeIds, root.getPolicy(), CommitteeEndorseProof.decode(finalVcs.getPtcProof()));

            return finalVcs;
        } catch (CommitteeBaseException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(
                    StrUtil.format("Failed to verify consensus state ( domain: {}, height: {}, hash: {} ) from committee {}: ",
                            consensusState.getDomain().getDomain(),
                            consensusState.getHeight().toString(),
                            consensusState.getHashHex(),
                            config.getCommitteeNetworkInfo().getCommitteeId()
                    ), t
            );
        }
    }

    /**
     * 验证跨链消息
     *
     * @param tpbta
     * @param validatedConsensusState
     * @param ucp
     * @return
     */
    @Override
    public ThirdPartyProof verifyCrossChainMessage(ThirdPartyBlockchainTrustAnchor tpbta, ValidatedConsensusState validatedConsensusState, UniformCrosschainPacket ucp) {
        return verifyCrossChainMessageWithResult(tpbta, validatedConsensusState, ucp).getThirdPartyProof();
    }

    @Override
    public PTCVerifyCrossChainMessageResult verifyCrossChainMessageWithResult(
            ThirdPartyBlockchainTrustAnchor tpbta,
            ValidatedConsensusState validatedConsensusState,
            UniformCrosschainPacket ucp
    ) {
        return verifyCrossChainMessageWithResult(tpbta, validatedConsensusState, ucp, "");
    }

    @Override
    public PTCVerifyCrossChainMessageResult verifyCrossChainMessageWithResult(
            ThirdPartyBlockchainTrustAnchor tpbta,
            ValidatedConsensusState validatedConsensusState,
            UniformCrosschainPacket ucp,
            String ucpId
    ) {
        try {
            CommitteeEndorseRoot root = CommitteeEndorseRoot.decode(tpbta.getEndorseRoot());
            List<String> requiredNodeIds = root.getEndorsers().stream()
                    .filter(NodeEndorseInfo::isRequired)
                    .map(NodeEndorseInfo::getNodeId)
                    .collect(Collectors.toList());
            List<String> optionalNodeIds = root.getEndorsers().stream()
                    .filter(NodeEndorseInfo::isOptional)
                    .map(NodeEndorseInfo::getNodeId)
                    .collect(Collectors.toList());

            checkNodes(requiredNodeIds, optionalNodeIds, root.getPolicy());

            List<Future<NodeVerifyCrossChainMessageResult>> resFutureList = reqExecutorService.invokeAll(
                    nodeMap.entrySet().stream()
                            .filter(entry -> entry.getValue().isAvailable())
                            .filter(
                                    entry -> requiredNodeIds.contains(entry.getValue().getEndpointInfo().getNodeId())
                                            || optionalNodeIds.contains(entry.getValue().getEndpointInfo().getNodeId())
                            ).map(
                                    entry -> (Callable<NodeVerifyCrossChainMessageResult>) () -> {
                                        log.debug("Verify crosschain msg with node {} {}", entry.getKey(), entry.getValue().getEndpointInfo().getEndpoint().getUrl());
                                        return entry.getValue().getNodeClient().verifyCrossChainMessageWithResult(tpbta.getCrossChainLane(), ucp, ucpId);
                                    }
                            ).collect(Collectors.toList())
            );
            List<NodeVerifyCrossChainMessageResult> nodeResults = collectNodeVerifyResults(resFutureList);
            List<CommitteeNodeProof> resList = nodeResults.stream()
                    .map(NodeVerifyCrossChainMessageResult::getNodeProof)
                    .collect(Collectors.toList());
            CommitteeEndorseProof proof = new CommitteeEndorseProof();
            ThirdPartyProof tpProof = assembleTpProof(tpbta, resList, ucp.getSrcMessage().getMessage(), proof);

            checkEndorsementsOrThrow(requiredNodeIds, optionalNodeIds, root.getPolicy(), proof);

            NodeVerifyCrossChainMessageResult regulationResult = selectRegulationResult(nodeResults);
            return new PTCVerifyCrossChainMessageResult(
                    tpProof,
                    ObjectUtil.isNull(regulationResult) ? "" : regulationResult.getRegulationStatus(),
                    ObjectUtil.isNull(regulationResult) ? "" : regulationResult.getRegulationReason()
            );
        } catch (CommitteeBaseException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(
                    StrUtil.format("Failed to verify crosschain message {} from committee {}: ",
                            tpbta.getCrossChainLane().getLaneKey(),
                            config.getCommitteeNetworkInfo().getCommitteeId()
                    ), t
            );
        }
    }

    @Override
    public Set<String> querySupportedBlockchainProducts() {
        Set<String> res = new HashSet<>();
        for (Node node : nodeMap.values()) {
            if (node.isAvailable()) {
                log.debug("Query supported blockchain products from node {} : {}",
                        node.getEndpointInfo().getNodeId(), StrUtil.join(",", node.getProductsSupported()));

                if (res.isEmpty()) {
                    res.addAll(node.getNodeClient().heartbeat().getProducts());
                } else {
                    res.retainAll(node.getNodeClient().heartbeat().getProducts());
                }
            }
        }
        return res;
    }

    @Override
    public BlockState queryCurrVerifiedBlockState(ThirdPartyBlockchainTrustAnchor tpbta) {
        try {
            CommitteeEndorseRoot root = CommitteeEndorseRoot.decode(tpbta.getEndorseRoot());
            List<String> requiredNodeIds = root.getEndorsers().stream()
                    .filter(NodeEndorseInfo::isRequired)
                    .map(NodeEndorseInfo::getNodeId)
                    .collect(Collectors.toList());
            List<String> optionalNodeIds = root.getEndorsers().stream()
                    .filter(NodeEndorseInfo::isOptional)
                    .map(NodeEndorseInfo::getNodeId)
                    .collect(Collectors.toList());

            if (!requiredNodeIds.isEmpty()) {
                List<Future<BlockState>> resFutureList = reqExecutorService.invokeAll(
                        nodeMap.values().stream()
                                .filter(node -> node.isAvailable() && requiredNodeIds.contains(node.getEndpointInfo().getNodeId()))
                                .map(
                                        node -> (Callable<BlockState>) () ->
                                                node.getNodeClient().queryBlockState(tpbta.getCrossChainLane().getSenderDomain())
                                ).collect(Collectors.toList())
                );
                return composeBlockStateFromRequired(resFutureList);
            }

            if (optionalNodeIds.isEmpty()) {
                throw new RuntimeException(
                        StrUtil.format("empty optional and required nodes for tpbta {}:{}", tpbta.getCrossChainLane().getLaneKey(), tpbta.getTpbtaVersion())
                );
            }

            List<Future<BlockState>> resFutureList = reqExecutorService.invokeAll(
                    nodeMap.values().stream()
                            .filter(Node::isAvailable)
                            .filter(node -> optionalNodeIds.contains(node.getEndpointInfo().getNodeId()))
                            .map(node -> (Callable<BlockState>) () ->
                                    node.getNodeClient().queryBlockState(tpbta.getCrossChainLane().getSenderDomain())
                            ).collect(Collectors.toList())
            );
            return composeBlockStateFromOptional(root.getPolicy().getThreshold(), resFutureList);
        } catch (CommitteeBaseException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(
                    StrUtil.format("Failed to query block state {} from committee {}: ",
                            tpbta.getCrossChainLane().getLaneKey(),
                            config.getCommitteeNetworkInfo().getCommitteeId()
                    ), t
            );
        }
    }

    @Override
    public ThirdPartyProof endorseBlockState(ThirdPartyBlockchainTrustAnchor tpbta, CrossChainDomain receiverDomain, ValidatedConsensusState validatedConsensusState) {
        try {
            CommitteeEndorseRoot root = CommitteeEndorseRoot.decode(tpbta.getEndorseRoot());
            List<String> requiredNodeIds = root.getEndorsers().stream()
                    .filter(NodeEndorseInfo::isRequired)
                    .map(NodeEndorseInfo::getNodeId)
                    .collect(Collectors.toList());
            List<String> optionalNodeIds = root.getEndorsers().stream()
                    .filter(NodeEndorseInfo::isOptional)
                    .map(NodeEndorseInfo::getNodeId)
                    .collect(Collectors.toList());

            checkNodes(requiredNodeIds, optionalNodeIds, root.getPolicy());

            List<Future<EndorseBlockStateResp>> resFutureList = reqExecutorService.invokeAll(
                    nodeMap.entrySet().stream()
                            .filter(entry -> entry.getValue().isAvailable())
                            .filter(
                                    entry -> requiredNodeIds.contains(entry.getValue().getEndpointInfo().getNodeId())
                                            || optionalNodeIds.contains(entry.getValue().getEndpointInfo().getNodeId())
                            ).map(
                                    entry -> (Callable<EndorseBlockStateResp>) () -> {
                                        log.debug("Endorse block state {} with node {} {}",
                                                validatedConsensusState.getHeight().toString(), entry.getKey(), entry.getValue().getEndpointInfo().getEndpoint().getUrl());
                                        return entry.getValue().getNodeClient().endorseBlockState(tpbta.getCrossChainLane(), receiverDomain, validatedConsensusState.getHeight());
                                    }
                            ).collect(Collectors.toList())
            );

            List<EndorseBlockStateResp> resList = collectEndorseBlockStateResp(resFutureList);
            if (ObjectUtil.isEmpty(resList)) {
                throw new RuntimeException("none EndorseBlockStateResp found");
            }

            CommitteeEndorseProof proof = new CommitteeEndorseProof();
            ThirdPartyProof tpProof = assembleTpProof(
                    tpbta,
                    resList.stream().map(EndorseBlockStateResp::getCommitteeNodeProof).collect(Collectors.toList()),
                    resList.get(0).getBlockStateAuthMsg().encode(),
                    proof
            );

            checkEndorsementsOrThrow(requiredNodeIds, optionalNodeIds, root.getPolicy(), proof);

            return tpProof;
        } catch (CommitteeBaseException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(
                    StrUtil.format("Failed to endorse block state {} of {} from committee {}: ",
                            validatedConsensusState.getHeight().toString(),
                            tpbta.getCrossChainLane().getLaneKey(),
                            config.getCommitteeNetworkInfo().getCommitteeId()
                    ), t
            );
        }
    }

    private List<EndorseBlockStateResp> collectEndorseBlockStateResp(List<Future<EndorseBlockStateResp>> resFutureList) {
        List<EndorseBlockStateResp> resList = resFutureList.stream().map(
                future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        throw new RuntimeException("failed to collect EndorseBlockStateResp from future object: ", e);
                    }
                }
        ).collect(Collectors.toList());
        if (ObjectUtil.isEmpty(resList)) {
            throw new RuntimeException(
                    StrUtil.format("none endorsements received with {} nodes active",
                            config.getCommitteeNetworkInfo().getCommitteeId(), getAvailableNodesCount())
            );
        }
        return resList;
    }

    private void connectCommitteeNodes() {
        for (EndpointInfo endpointInfo : config.getCommitteeNetworkInfo().getNodes()) {
            Node node = new Node(endpointInfo);
            nodeMap.put(endpointInfo.getNodeId(), node);
            if (
                    !NetUtil.isOpen(
                            NetUtil.createAddress(endpointInfo.getEndpoint().getHost(), endpointInfo.getEndpoint().getPort()),
                            3_000
                    )
            ) {
                log.warn("Committee node {} {} is not available", endpointInfo.getNodeId(), endpointInfo.getEndpoint().getUrl());
                continue;
            }
            node.connect(config);
        }
        if (!isNodesThresholdSatisfied()) {
            throw new RuntimeException("active nodes not enough for service requirement, at least 2/3 nodes is active");
        }
    }

    private void startHeartbeatWork() {
        heartbeatExecutorService.scheduleWithFixedDelay(
                () -> {
                    try {
                        for (Map.Entry<String, Node> entry : nodeMap.entrySet()) {
                            log.debug("Heartbeat with node {}", entry.getKey());

                            try {
                                if (
                                        !NetUtil.isOpen(
                                                NetUtil.createAddress(
                                                        entry.getValue().getEndpointInfo().getEndpoint().getHost(),
                                                        entry.getValue().getEndpointInfo().getEndpoint().getPort()
                                                ), 3_000
                                        )
                                ) {
                                    log.debug("Committee node {} {} is not available",
                                            entry.getValue().getEndpointInfo().getNodeId(),
                                            entry.getValue().getEndpointInfo().getEndpoint().getUrl());
                                    if (entry.getValue().isAvailable()) {
                                        entry.getValue().setNodeState(Node.NodeState.UNAVAILABLE);
                                        log.error("Committee node {} {} is lost",
                                                entry.getValue().getEndpointInfo().getNodeId(),
                                                entry.getValue().getEndpointInfo().getEndpoint().getUrl());
                                    }
                                    continue;
                                }

                                if (entry.getValue().getNodeState() == Node.NodeState.UNAVAILABLE) {
                                    entry.getValue().connect(config);
                                }
                                HeartbeatResp resp = entry.getValue().getNodeClient().heartbeat();
                                if (ObjectUtil.isNull(resp)) {
                                    throw new RuntimeException("Heartbeat resp is null with node " + entry.getKey());
                                }
                                entry.getValue().getProductsSupported().clear();
                                entry.getValue().getProductsSupported().addAll(resp.getProducts());
                            } catch (Throwable t) {
                                log.warn("Heartbeat with node {} failed: ", entry.getKey(), t);
                            }
                        }

                        if (!isNodesThresholdSatisfied()) {
                            log.warn("Active nodes not enough for service requirement, at least 2/3 nodes is active: ( committee_id: {}, active nodes: {} )",
                                    config.getCommitteeNetworkInfo().getCommitteeId(),
                                    nodeMap.values().stream().filter(Node::isAvailable)
                                            .map(node -> node.getEndpointInfo().getEndpoint().getUrl())
                                            .collect(Collectors.joining(", "))
                            );
                        }
                    } catch (Throwable t) {
                        log.warn("Heartbeat work failed: ", t);
                    }
                },
                3_000,
                config.getHeartbeatInterval(),
                TimeUnit.MILLISECONDS
        );
    }

    private ThirdPartyBlockchainTrustAnchor assembleTpBta(List<ThirdPartyBlockchainTrustAnchor> tpbtaList) {
        if (ObjectUtil.isEmpty(tpbtaList)) {
            throw new RuntimeException("none tpbta found from list");
        }
        tpbtaList.get(0).setEndorseProof(
                tpbtaList.stream()
                        .filter(ObjectUtil::isNotNull)
                        .map(o -> CommitteeEndorseProof.decode(o.getEndorseProof()))
                        .reduce(
                                (o1, o2) -> {
                                    o1.getSigs().add(o2.getSigs().get(0));
                                    return o1;
                                }
                        ).orElseThrow(() -> new RuntimeException("none endorse proof found from list")).encode()
        );
        return tpbtaList.get(0);
    }

    private boolean meetRequiredAndOptionalPolicy(List<String> requiredNodeIds, List<String> optionalNodeIds, OptionalEndorsePolicy policy, CommitteeEndorseProof proof) {
        Set<String> whoSigned = proof.getSigs().stream().map(CommitteeNodeProof::getNodeId).collect(Collectors.toSet());
        if (!whoSigned.containsAll(requiredNodeIds)) {
            return false;
        }
        return policy.getThreshold().check((int) optionalNodeIds.stream().map(whoSigned::contains).filter(Boolean::booleanValue).count());
    }

    private ThirdPartyProof assembleTpProof(
            ThirdPartyBlockchainTrustAnchor tpbta,
            @NonNull List<CommitteeNodeProof> proofList,
            byte[] message,
            @NonNull CommitteeEndorseProof proof
    ) {
        proof.setCommitteeId(config.getCommitteeNetworkInfo().getCommitteeId());
        proof.setSigs(proofList);

        ThirdPartyProof tpProof = ThirdPartyProof.create(
                tpbta.getTpbtaVersion(),
                message,
                tpbta.getCrossChainLane()
        );
        tpProof.setRawProof(proof.encode());

        return tpProof;
    }

    private ValidatedConsensusState collectValidatedConsensusStateResults(List<Future<ValidatedConsensusState>> resFutureList) {
        List<ValidatedConsensusState> resList = resFutureList.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        // A committee may contain optional nodes that are temporarily unavailable
                        // or lag behind the required nodes. Their failed futures must not abort the
                        // whole round before the endorsement policy is evaluated below. Required
                        // node failures are still rejected by checkEndorsementsOrThrow because the
                        // resulting proof will be missing their signatures.
                        log.warn("failed to collect a validated consensus state from committee node; "
                                + "continue with successful endorsements and enforce the TpBTA policy: {}",
                                e.getMessage());
                        return null;
                    }
                })
                .filter(ObjectUtil::isNotNull)
                .collect(Collectors.toList());
        if (ObjectUtil.isEmpty(resList)) {
            throw new RuntimeException(
                    StrUtil.format("none endorsements received with {} nodes active",
                            config.getCommitteeNetworkInfo().getCommitteeId(), getAvailableNodesCount())
            );
        }
        byte[] firstOneWithoutSig = resList.get(0).getEncodedToSign();
        CommitteeEndorseProof nodeProof = CommitteeEndorseProof.decode(resList.get(0).getPtcProof());
        for (ValidatedConsensusState validatedConsensusState : ListUtil.sub(resList, 1, resList.size())) {
            if (!ArrayUtil.equals(validatedConsensusState.getEncodedToSign(), firstOneWithoutSig)) {
                throw new RuntimeException(
                        StrUtil.format("validated consensus states without proof are supposed to be same but not: committee id {}",
                                config.getCommitteeNetworkInfo().getCommitteeId())
                );
            }
            nodeProof.getSigs().addAll(
                    CommitteeEndorseProof.decode(validatedConsensusState.getPtcProof()).getSigs()
            );
        }
        resList.get(0).setPtcProof(nodeProof.encode());

        return resList.get(0);
    }

    private List<CommitteeNodeProof> collectNodeProof(List<Future<CommitteeNodeProof>> resFutureList) {
        List<CommitteeNodeProof> resList = resFutureList.stream().map(
                future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        log.warn("failed to collect a committee node proof; continue with successful "
                                + "endorsements and enforce the TpBTA policy: {}", e.getMessage());
                        return null;
                    }
                }
        ).filter(ObjectUtil::isNotNull).collect(Collectors.toList());
        if (ObjectUtil.isEmpty(resList)) {
            throw new RuntimeException(
                    StrUtil.format("none endorsements received with {} nodes active",
                            config.getCommitteeNetworkInfo().getCommitteeId(), getAvailableNodesCount())
            );
        }
        return resList;
    }

    private List<NodeVerifyCrossChainMessageResult> collectNodeVerifyResults(
            List<Future<NodeVerifyCrossChainMessageResult>> resultFutures
    ) {
        List<NodeVerifyCrossChainMessageResult> results = resultFutures.stream().map(
                future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        // Optional committee nodes may not have indexed the requested consensus
                        // state yet. Keep the successful results here and let
                        // checkEndorsementsOrThrow enforce required endorsers and the optional
                        // threshold. This is the same policy used when collecting consensus states.
                        log.warn("failed to collect a committee node verification result; continue "
                                + "with successful endorsements and enforce the TpBTA policy: {}",
                                e.getMessage());
                        return null;
                    }
                }
        ).filter(ObjectUtil::isNotNull).collect(Collectors.toList());
        if (ObjectUtil.isEmpty(results)) {
            throw new RuntimeException(
                    StrUtil.format("none endorsements received with {} nodes active",
                            config.getCommitteeNetworkInfo().getCommitteeId(), getAvailableNodesCount())
            );
        }
        return results;
    }

    private NodeVerifyCrossChainMessageResult selectRegulationResult(
            List<NodeVerifyCrossChainMessageResult> results
    ) {
        List<NodeVerifyCrossChainMessageResult> regulationResults = results.stream()
                .filter(result -> StrUtil.isNotEmpty(result.getRegulationStatus()))
                .collect(Collectors.toList());
        if (regulationResults.isEmpty()) {
            return null;
        }
        int priority = regulationResults.stream()
                .mapToInt(result -> regulationStatusPriority(result.getRegulationStatus()))
                .min()
                .orElse(2);
        NodeVerifyCrossChainMessageResult selected = regulationResults.stream()
                .filter(result -> regulationStatusPriority(result.getRegulationStatus()) == priority)
                .findFirst()
                .orElse(regulationResults.get(0));
        String reasons = regulationResults.stream()
                .filter(result -> regulationStatusPriority(result.getRegulationStatus()) == priority)
                .map(NodeVerifyCrossChainMessageResult::getRegulationReason)
                .filter(StrUtil::isNotEmpty)
                .distinct()
                .collect(Collectors.joining("; "));
        return new NodeVerifyCrossChainMessageResult(
                selected.getNodeProof(),
                selected.getRegulationStatus(),
                reasons
        );
    }

    private int regulationStatusPriority(String status) {
        if (StrUtil.equalsIgnoreCase("rejected", status)) {
            return 0;
        }
        if (StrUtil.equalsIgnoreCase("error", status)) {
            return 1;
        }
        return 2;
    }

    @SuppressWarnings("all")
    private void checkEndorsementsOrThrow(List<String> requiredNodeIds, List<String> optionalNodeIds, OptionalEndorsePolicy policy, CommitteeEndorseProof proof) {
        if (!meetRequiredAndOptionalPolicy(
                requiredNodeIds,
                optionalNodeIds,
                policy,
                proof
        )) {
            throw new EndorsementNotEnoughException(
                    "Policy not satisfied: ( policy {}, required: {}, optional: {}, who signed: {} )",
                    JSON.toJSONString(policy),
                    StrUtil.join(",", requiredNodeIds),
                    StrUtil.join(",", optionalNodeIds),
                    proof.getSigs().stream().map(CommitteeNodeProof::getNodeId).collect(Collectors.joining(","))
            );
        }
    }

    private boolean isThresholdSatisfied(int m, int n) {
        return m * 3 > n * 2;
    }

    private boolean isNodesThresholdSatisfied() {
        return isThresholdSatisfied(
                getAvailableNodesCount(),
                this.nodeMap.size()
        );
    }

    private int getAvailableNodesCount() {
        return (int) this.nodeMap.values().stream().filter(Node::isAvailable).count();
    }

    private void checkNodes() {
        if (!isNodesThresholdSatisfied()) {
            throw new AvailableNodesNotEnoughException();
        }
    }

    private void checkNodes(List<String> requiredNodeIds, List<String> optionalNodeIds, OptionalEndorsePolicy policy) {
        Set<String> availableNodeIds = nodeMap.entrySet().stream().filter(entry -> entry.getValue().isAvailable()).map(Map.Entry::getKey).collect(Collectors.toSet());
        if (!availableNodeIds.containsAll(requiredNodeIds)) {
            throw new NotAllRequiredNodesAvailableException();
        }

        long availableOptionalCnt = optionalNodeIds.stream().map(availableNodeIds::contains).filter(Boolean::booleanValue).count();
        if (!policy.getThreshold().check((int) availableOptionalCnt)) {
            throw new NotEnoughOptionalNodesAvailableException();
        }
    }

    private BlockState composeBlockStateFromRequired(List<Future<BlockState>> resFutureList) {
        List<BlockState> resList = resFutureList.stream().map(
                future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        throw new RuntimeException("failed to collect block state from future object: ", e);
                    }
                }
        ).collect(Collectors.toList());
        if (resList.isEmpty()) {
            throw new RuntimeException("unexpected empty block states from required nodes");
        }

        BlockState blockState = null;
        for (BlockState curr : resList) {
            if (ObjectUtil.isNull(curr)) {
                continue;
            }
            if (ObjectUtil.isNull(blockState) || blockState.getHeight().compareTo(curr.getHeight()) > 0) {
                blockState = curr;
            }
        }

        return blockState;
    }

    private BlockState composeBlockStateFromOptional(OptionalEndorsePolicy.Threshold threshold, List<Future<BlockState>> resFutureList) {
        List<BlockState> resList = resFutureList.stream().map(
                future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        throw new RuntimeException("failed to collect block state from future object: ", e);
                    }
                }
        ).collect(Collectors.toList());
        if (resList.isEmpty()) {
            throw new RuntimeException("unexpected empty block states from optional nodes");
        }

        return resList.stream().filter(ObjectUtil::isNotNull).collect(Collectors.groupingBy(BlockState::getHeight))
                .entrySet().stream()
                .filter(x -> threshold.check(x.getValue().size()))
                .max(Comparator.comparing(x -> x.getValue().size()))
                .orElseThrow(() -> new RuntimeException("no valid block state found")).getValue().get(0);
    }
}
