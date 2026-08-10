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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.service.impl;

import java.math.BigInteger;
import java.security.PrivateKey;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.*;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.DomainNameCredentialSubject;
import com.alipay.antchain.bridge.commons.bcdns.PTCCredentialSubject;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.am.IAuthMessage;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.bta.BlockchainTrustAnchorV1;
import com.alipay.antchain.bridge.commons.core.bta.IBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.monitor.IMonitorMessage;
import com.alipay.antchain.bridge.commons.core.monitor.MonitorMessageFactory;
import com.alipay.antchain.bridge.commons.core.ptc.*;
import com.alipay.antchain.bridge.commons.core.sdp.ISDPMessage;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.commons.exception.IllegalCrossChainCertException;
import com.alipay.antchain.bridge.commons.utils.crypto.HashAlgoEnum;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.plugins.spi.ptc.core.VerifyResult;
import com.alipay.antchain.bridge.pluginserver.service.CallBBCRequest;
import com.alipay.antchain.bridge.pluginserver.service.CrossChainServiceGrpc;
import com.alipay.antchain.bridge.pluginserver.service.Response;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.client.CrossChainServiceGrpcClientManager;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.client.MonitorSystemGrpcClientManager;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.exception.*;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.BtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.TpBtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.ValidatedConsensusStateWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.IBCDNSRepository;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.IEndorseServiceRepository;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.ISystemConfigRepository;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IEndorserService;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IHcdvsPluginService;
import com.alipay.antchain.bridge.ptc.committee.monitor.system.grpc.*;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeEndorseProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.EndorseBlockStateResp;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.CommitteeEndorseRoot;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.VerifyBtaExtension;
import com.google.protobuf.ByteString;
import jakarta.annotation.Resource;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EndorserServiceImpl implements IEndorserService {

    @Resource
    private IHcdvsPluginService hcdvsPluginService;

    @Resource
    private IEndorseServiceRepository endorseServiceRepository;

    @Resource
    private ISystemConfigRepository systemConfigRepository;

    @Resource
    private IBCDNSRepository bcdnsRepository;

    @Resource
    private PrivateKey nodeKey;

    @Resource
    private AbstractCrossChainCertificate ptcCrossChainCert;

    @Resource
    private MonitorSystemGrpcClientManager monitorSystemGrpcClientManager;

    @Resource
    private CrossChainServiceGrpcClientManager crossChainServiceGrpcClientManager;

    @Value("${committee.node.endorse.ucp_hash_algo:KECCAK_256}")
    private HashAlgoEnum ucpHashAlgo;

    @Value("${committee.node.endorse.sign_algo:KECCAK256_WITH_SECP256K1}")
    private SignAlgoEnum nodeSignAlgo;

    @Value("${committee.node.id}")
    private String committeeNodeId;

    @Value("${committee.id}")
    private String committeeId;

    @Override
    public TpBtaWrapper queryMatchedTpBta(CrossChainLane lane) {
        return endorseServiceRepository.getMatchedTpBta(lane);
    }

    @Override
    public TpBtaWrapper verifyBta(AbstractCrossChainCertificate domainCert, IBlockchainTrustAnchor bta) throws InvalidBtaException {
        log.info("verify BTA for domain {} now", bta.getDomain().getDomain());
        var credentialSubject = (DomainNameCredentialSubject) domainCert.getCredentialSubjectInstance();

        var domainSpaceCertWrapper = bcdnsRepository.getDomainSpaceCert(credentialSubject.getParentDomainSpace().getDomain());
        if (ObjectUtil.isNull(domainSpaceCertWrapper)) {
            throw new InvalidBtaException("domain space cert for {} not found", credentialSubject.getParentDomainSpace().getDomain());
        }
        if (!ArrayUtil.equals(domainSpaceCertWrapper.getOwnerOid().getRawId(), domainCert.getIssuer().getRawId())) {
            throw new InvalidBtaException("illegal domain space cert issuer for {}", credentialSubject.getParentDomainSpace().getDomain());
        }

        try {
            domainCert.validate(domainSpaceCertWrapper.getDomainSpaceCert().getCredentialSubjectInstance());
        } catch (IllegalCrossChainCertException e) {
            throw new InvalidBtaException("domain cert validation failed: {}", e.getMessage());
        }

        if (!bta.getDomain().equals(credentialSubject.getDomainName())) {
            throw new InvalidBtaException("domain name mismatch");
        }
        if (!ArrayUtil.equals(credentialSubject.getSubjectPublicKey().getEncoded(), bta.getBcOwnerPublicKeyObj().getEncoded())) {
            throw new InvalidBtaException("owner public key mismatch");
        }
        if (ObjectUtil.isEmpty(bta.getAmId())) {
            throw new InvalidBtaException("am id is empty");
        }
        if (!bta.validate()) {
            throw new InvalidBtaException("bta sig verification failed");
        }

        var verifyBtaExtension = VerifyBtaExtension.decode(bta.getExtension());
        if (ObjectUtil.isNull(verifyBtaExtension)) {
            throw new InvalidBtaException("extension decode failed");
        }
        if (!verifyBtaExtension.getCrossChainLane().isValidated()) {
            throw new InvalidBtaException("cross chain lane is invalid");
        }
        if (!checkIfTpBTAIntersection(verifyBtaExtension.getCrossChainLane())) {
            throw new InvalidBtaException("tpbta intersection check failed");
        }

        var latestTpBta = endorseServiceRepository.getExactTpBta(verifyBtaExtension.getCrossChainLane());
        var tpbta = new ThirdPartyBlockchainTrustAnchorV1(
                ObjectUtil.isNull(latestTpBta) ? 1 : latestTpBta.getTpbta().getTpbtaVersion() + 1,
                systemConfigRepository.queryCurrentPtcAnchorVersion(),
                (PTCCredentialSubject) ptcCrossChainCert.getCredentialSubjectInstance(),
                verifyBtaExtension.getCrossChainLane(),
                bta.getSubjectVersion(),
                ucpHashAlgo,
                verifyBtaExtension.getCommitteeEndorseRoot().encode(),
                new byte[]{}
        );
        tpbta.setEndorseProof(
                CommitteeEndorseProof.builder()
                        .committeeId(committeeId)
                        .sigs(ListUtil.toList(
                                CommitteeNodeProof.builder()
                                        .nodeId(committeeNodeId)
                                        .signAlgo(nodeSignAlgo)
                                        .signature(nodeSignAlgo.getSigner().sign(nodeKey, tpbta.getEncodedToSign()))
                                        .build()
                        )).build().encode()
        );
        var tpBtaWrapper = new TpBtaWrapper(tpbta);
        endorseServiceRepository.setBta(new BtaWrapper(bta));
        endorseServiceRepository.setTpBta(tpBtaWrapper);

        return tpBtaWrapper;
    }

    private boolean checkIfTpBTAIntersection(CrossChainLane tpbtaLane) {
        var wrapper = endorseServiceRepository.getMatchedTpBta(tpbtaLane);
        if (ObjectUtil.isEmpty(wrapper) || wrapper.getCrossChainLane().equals(tpbtaLane)) {
            return true;
        }
        return wrapper.getTpbta().type().ordinal() > ThirdPartyBlockchainTrustAnchor.TypeEnum.parseFrom(tpbtaLane).ordinal();
    }

    @Override
    public ValidatedConsensusState commitAnchorState(CrossChainLane crossChainLane, ConsensusState anchorState) {
        var tpbta = endorseServiceRepository.getMatchedTpBta(crossChainLane);
        if (ObjectUtil.isNull(tpbta)) {
            throw new InvalidConsensusStateException("tpbta not found for {}", crossChainLane.getLaneKey());
        }
        var bta = endorseServiceRepository.getBta(crossChainLane.getSenderDomain().getDomain(), tpbta.getTpbta().getBtaSubjectVersion());
        if (ObjectUtil.isNull(bta)) {
            throw new InvalidConsensusStateException("bta not found for {}", crossChainLane.getSenderDomain().getDomain());
        }

        var hcdvs = hcdvsPluginService.getHCDVSService(bta.getProduct());
        if (ObjectUtil.isNull(hcdvs)) {
            throw new CommitteeNodeInternalException("hcdvs not found for {}", bta.getProduct());
        }

        if (!bta.getBta().getInitHeight().equals(anchorState.getHeight())) {
            throw new InvalidConsensusStateException("invalid height: bta's is {} and yours {}",
                    bta.getBta().getInitHeight().toString(), anchorState.getHeight().toString());
        }
        if (!ArrayUtil.equals(bta.getBta().getInitBlockHash(), anchorState.getHash())) {
            throw new InvalidConsensusStateException("invalid block hash: bta's is {} and yours {}",
                    HexUtil.encodeHexStr(bta.getBta().getInitBlockHash()), anchorState.getHashHex());
        }

        return processValidatedConsensusState(anchorState, tpbta, hcdvs.verifyAnchorConsensusState(bta.getBta(), anchorState));
    }

    @Override
    public ValidatedConsensusState commitConsensusState(CrossChainLane crossChainLane, ConsensusState currState) {
        var tpbta = endorseServiceRepository.getMatchedTpBta(crossChainLane);
        if (ObjectUtil.isNull(tpbta)) {
            throw new InvalidConsensusStateException("tpbta not found for {}", crossChainLane.getLaneKey());
        }
        var bta = endorseServiceRepository.getBta(crossChainLane.getSenderDomain().getDomain(), tpbta.getTpbta().getBtaSubjectVersion());
        if (ObjectUtil.isNull(bta)) {
            throw new InvalidConsensusStateException("bta not found for {}", crossChainLane.getSenderDomain().getDomain());
        }
        var parentConsensusState = endorseServiceRepository.getValidatedConsensusState(
                currState.getDomain().getDomain(),
                currState.getHeight().subtract(BigInteger.ONE)
        );
        if (ObjectUtil.isNull(parentConsensusState)) {
            throw new InvalidConsensusStateException("parent consensus state not found for {}", currState.getParentHashHex());
        }

        var hcdvs = hcdvsPluginService.getHCDVSService(bta.getProduct());
        if (ObjectUtil.isNull(hcdvs)) {
            throw new CommitteeNodeInternalException("hcdvs not found for {}", bta.getProduct());
        }

        return processValidatedConsensusState(currState, tpbta, hcdvs.verifyConsensusState(currState, parentConsensusState.getValidatedConsensusState()));
    }

    @Override
    public CommitteeNodeProof verifyUcp(CrossChainLane crossChainLane, UniformCrosschainPacket ucp) {
        var tpbta = endorseServiceRepository.getExactTpBta(crossChainLane);
        if (ObjectUtil.isNull(tpbta)) {
            throw new InvalidCrossChainMessageException("tpbta not found for {}", crossChainLane.getLaneKey());
        }
        var bta = endorseServiceRepository.getBta(crossChainLane.getSenderDomain().getDomain(), tpbta.getTpbta().getBtaSubjectVersion());
        if (ObjectUtil.isNull(bta)) {
            throw new InvalidCrossChainMessageException("bta not found for {}", crossChainLane.getSenderDomain().getDomain());
        }
        var consensusState = endorseServiceRepository.getValidatedConsensusState(
                ucp.getSrcDomain().getDomain(),
                ucp.getSrcMessage().getProvableData().getBlockHashHex()
        );
        if (ObjectUtil.isNull(consensusState)) {
            throw new InvalidCrossChainMessageException("consensus state not found for {}", ucp.getSrcMessage().getProvableData().getBlockHashHex());
        }
        if (!ArrayUtil.equals(consensusState.getValidatedConsensusState().getHash(), ucp.getSrcMessage().getProvableData().getBlockHash())) {
            throw new InvalidCrossChainMessageException("expected block hash {} but get {}",
                    consensusState.getValidatedConsensusState().getHash(), ucp.getSrcMessage().getProvableData().getBlockHash());
        }
        if (!ObjectUtil.equals(consensusState.getValidatedConsensusState().getHeight(), ucp.getSrcMessage().getProvableData().getHeightVal())) {
            throw new InvalidCrossChainMessageException("expected block height {} but get {}",
                    consensusState.getValidatedConsensusState().getHeight(), ucp.getSrcMessage().getProvableData().getHeightVal());
        }

        var hcdvs = hcdvsPluginService.getHCDVSService(bta.getProduct());
        if (ObjectUtil.isNull(hcdvs)) {
            throw new CommitteeNodeInternalException("hcdvs not found for {}", bta.getProduct());
        }
        if (!ArrayUtil.equals(
                ucp.getSrcMessage().getMessage(),
                hcdvs.parseMessageFromLedgerData(ucp.getSrcMessage().getProvableData().getLedgerData())
        )) {
            throw new InvalidCrossChainMessageException("message decoded from ledger data not equal to message inside UCP");
        }

        var verifyResult = hcdvs.verifyCrossChainMessage(ucp.getSrcMessage(), consensusState.getValidatedConsensusState());
        if (ObjectUtil.isNull(verifyResult) || !verifyResult.isSuccess()) {
            throw new InvalidCrossChainMessageException("cross chain message verification failed: {}", verifyResult.getErrorMsg());
        }

        return CommitteeNodeProof.builder()
                .nodeId(committeeNodeId)
                .signAlgo(nodeSignAlgo)
                .signature(nodeSignAlgo.getSigner().sign(
                        nodeKey,
                        ThirdPartyProof.create(
                                tpbta.getTpbta().getTpbtaVersion(),
                                ucp.getSrcMessage().getMessage(),
                                crossChainLane
                        ).getEncodedToSign()
                )).build();
    }

    @Override
    public CommitteeNodeProof verifyUcpWithMonitorSystem(CrossChainLane crossChainLane, UniformCrosschainPacket ucp) {

        var tpbta = endorseServiceRepository.getExactTpBta(crossChainLane);
        if (ObjectUtil.isNull(tpbta)) {
            throw new InvalidCrossChainMessageException("tpbta not found for {}", crossChainLane.getLaneKey());
        }
        var bta = endorseServiceRepository.getBta(crossChainLane.getSenderDomain().getDomain(), tpbta.getTpbta().getBtaSubjectVersion());
        if (ObjectUtil.isNull(bta)) {
            throw new InvalidCrossChainMessageException("bta not found for {}", crossChainLane.getSenderDomain().getDomain());
        }

        log.info("verify ucp with monitor system for domain {} now", bta.getDomain());
        // log.info("crosslane[senderDomain-recvDomain-senderID-recvID]: {}, {}, {}, {}", 
        //     crossChainLane.getSenderDomain().getDomain(), crossChainLane.getReceiverDomain().getDomain(),
        //     crossChainLane.getSenderIdHex(), crossChainLane.getReceiverIdHex());
//        log.info("verify ucp with monitor system for domain {} now", crossChainLane.getSenderDomain().getDomain());

        MonitorSystemServiceGrpc.MonitorSystemServiceBlockingStub monitorSystemServiceBlockingStub = monitorSystemGrpcClientManager.getStub("monitor-system");

        MonitorSystemResponse responseFromMonitorSystem = monitorSystemServiceBlockingStub.verifyCrossChainMessageInMonitorSystem(
                VerifyCrossChainMessageInMonitorSystemRequest.newBuilder()
                        .setRawUcp(ByteString.copyFrom(ucp.encode()))
                        .build()
        );

        if (ObjectUtil.isNull(responseFromMonitorSystem)) {
            throw new RuntimeException("null response from monitor system");
        }
        if (responseFromMonitorSystem.getCode() != 0) {
            throw new RuntimeException(String.format("[MonitorSystemGRpcClient] verifyCrossChainMessageInMonitorSystem request failed for plugin server: %s",
                    responseFromMonitorSystem.getErrorMsg()));
        }
        if (responseFromMonitorSystem.getVerifyCrossChainMessageInMonitorSystemResp().getResult() == 0) {
            // 监管通过 流程正常
//            log.info("verify ucp with monitor system for domain {}: success", bta.getDomain());
            log.info("verify ucp with monitor system for domain {}: success", crossChainLane.getSenderDomain().getDomain());
            return CommitteeNodeProof.builder()
                    .nodeId(committeeNodeId)
                    .signAlgo(nodeSignAlgo)
                    .signature(nodeSignAlgo.getSigner().sign(
                            nodeKey,
                            ThirdPartyProof.create(
                                    tpbta.getTpbta().getTpbtaVersion(),
                                    ucp.getSrcMessage().getMessage(),
                                    crossChainLane
                            ).getEncodedToSign()
                    )).build();
        } else {
            // [监管回滚的v1版本逻辑]
            // 监管未通过 直接向监管合约发送回滚交易 并且不跑出异常 而是返回一个签名
            // 目前是返回一个正确的签名, 保证在监管不通过时系统的稳定运行; 在8~9月开发的最终版本中会返回一个空签名, 实现完整的逻辑
            log.info("verify ucp with monitor system for domain {}: failure", bta.getDomain());
            // log.info("verify ucp with monitor system for domain {}: failure", crossChainLane.getSenderDomain().getDomain());
            // IAuthMessage authMessage = AuthMessageFactory.createAuthMessage(ucp.getSrcMessage().getMessage());
            // ISDPMessage sdpMessage = SDPMessageFactory.createSDPMessage(authMessage.getPayload());
            // byte[] monitorMessage = sdpMessage.getPayload();

            // CrossChainServiceGrpc.CrossChainServiceBlockingStub crossChainServiceBlockingStub = crossChainServiceGrpcClientManager.getStub("plugin-server");
            // Response responseFromPS = crossChainServiceBlockingStub.bbcCall(
            //         CallBBCRequest.newBuilder()
            //                 .setProduct(bta.getProduct())
            //                 .setDomain(bta.getDomain())
            //                 .setRelayMonitorRollbackMessageReq(
            //                         RelayMonitorRollbackMessageRequest.newBuilder()
            //                                 .setReceiverDomain(ucp.getSrcDomain().getDomain())
            //                                 .setToAddress(ucp.getCrossChainLane().getSenderIdHex())
            //                                 // .setToAddress(authMessage.getIdentity().toHex())
            //                                 .setRawMessage(ByteString.copyFrom(monitorMessage))
            //                 ).build()
            // );

            // if (ObjectUtil.isNull(responseFromPS)) {
            //     throw new RuntimeException("null response from plugin server");
            // }
            // if (responseFromPS.getCode() != 0) {
            //     throw new RuntimeException(
            //             String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] relayMonitorRollbackMessage request failed for plugin server: %s",
            //                     bta.getDomain(), bta.getProduct(), responseFromPS.getErrorMsg())
            //     );
            // }

            // CrossChainMessageReceipt crossChainMessageReceipt = convertFromGRpcCrossChainMessageReceipt(responseFromPS.getBbcResp().getRelayMonitorRollbackMessageResp().getReceipt());

            // 如果回滚消息未成功上链 目前仅抛出异常
            // if (!crossChainMessageReceipt.isSuccessful()) {
            //     throw new RuntimeException(StrUtil.format("failed to commit monitor rollback message: ( error_msg: {})",
            //             crossChainMessageReceipt.getErrorMsg()));
            // }

            // [监管回滚的v2版本逻辑]
            // 返回一个ethereum格式(65字节)的空签名 由目的链的监管合约验证签名时识别为监管失败 构造监管回滚消息
            return CommitteeNodeProof.builder()
                    .nodeId(committeeNodeId)
                    .signAlgo(nodeSignAlgo)
                    .signature(new byte[65]).build();

            // throw new InvalidCrossChainMessageException("[monitor system] illegal crosschain message(block hash: {}): {}",
            //         ucp.getSrcMessage().getProvableData().getBlockHashHex(), responseFromMonitorSystem.getVerifyCrossChainMessageInMonitorSystemResp().getMsg());
        }
    }

    @Override
    public void relayUcpToMonitorSystem(UniformCrosschainPacket ucp) {
        MonitorSystemServiceGrpc.MonitorSystemServiceBlockingStub monitorSystemServiceBlockingStub = monitorSystemGrpcClientManager.getStub("monitor-system");

        MonitorSystemResponse responseFromMonitorSystem = monitorSystemServiceBlockingStub.relayUcpToMonitorSystem(
                RelayUcpToMonitorSystemRequest.newBuilder()
                        .setRawUcp(ByteString.copyFrom(ucp.encode()))
                        .build()
        );

        if (ObjectUtil.isNull(responseFromMonitorSystem)) {
            throw new RuntimeException("null response from monitor system");
        }
        if (responseFromMonitorSystem.getCode() != 0) {
            throw new RuntimeException(String.format("[MonitorSystemGRpcClient] relayUcpToMonitorSystem request failed: %s",
                    responseFromMonitorSystem.getErrorMsg()));
        }
    }

    @Override
    public EndorseBlockStateResp endorseBlockState(CrossChainLane crossChainLane, String receiverDomain, BigInteger height) {
        var tpbta = endorseServiceRepository.getExactTpBta(crossChainLane);
        if (ObjectUtil.isNull(tpbta)) {
            throw new InvalidCrossChainMessageException("tpbta not found for {}", crossChainLane.getLaneKey());
        }

        if (!endorseServiceRepository.hasValidatedConsensusState(crossChainLane.getSenderDomain().toString(), height)) {
            throw new BlockStateNotValidatedYetException("no block validated for height {}", height.toString());
        }

        var vcs = endorseServiceRepository.getValidatedConsensusState(crossChainLane.getSenderDomain().toString(), height);
        IAuthMessage am = AuthMessageFactory.createAuthMessage(
                1,
                CrossChainIdentity.ZERO_ID.getRawID(),
                0,
                SDPMessageFactory.createValidatedBlockStateSDPMsg(
                        new CrossChainDomain(receiverDomain),
                        new BlockState(
                                crossChainLane.getSenderDomain(),
                                vcs.getValidatedConsensusState().getHash(),
                                vcs.getHeight(),
                                vcs.getValidatedConsensusState().getStateTimestamp()
                        )
                ).encode()
        );
        return new EndorseBlockStateResp(
                am,
                CommitteeNodeProof.builder()
                        .nodeId(committeeNodeId)
                        .signAlgo(nodeSignAlgo)
                        .signature(nodeSignAlgo.getSigner().sign(
                                nodeKey,
                                ThirdPartyProof.create(
                                        tpbta.getTpbta().getTpbtaVersion(),
                                        am.encode(),
                                        crossChainLane
                                ).getEncodedToSign()
                        )).build()
        );
    }

    @NonNull
    private ValidatedConsensusState processValidatedConsensusState(ConsensusState currState, TpBtaWrapper tpbta, VerifyResult verifyResult) {
        if (ObjectUtil.isNull(verifyResult) || !verifyResult.isSuccess()) {
            throw new InvalidConsensusStateException("consensus state verification failed: {}", verifyResult.getErrorMsg());
        }

        var vcs = BeanUtil.copyProperties(currState, ValidatedConsensusStateV1.class);
        vcs.setPtcOid(ptcCrossChainCert.getCredentialSubjectInstance().getApplicant());
        vcs.setTpbtaVersion(tpbta.getTpbta().getTpbtaVersion());
        vcs.setPtcType(PTCTypeEnum.COMMITTEE);

        if (!endorseServiceRepository.hasValidatedConsensusState(currState.getDomain().getDomain(), currState.getHeight())) {
            endorseServiceRepository.setValidatedConsensusState(new ValidatedConsensusStateWrapper(vcs));
        }

        var nodeProof = CommitteeNodeProof.builder()
                .nodeId(committeeNodeId)
                .signAlgo(nodeSignAlgo)
                .signature(nodeSignAlgo.getSigner().sign(nodeKey, vcs.getEncodedToSign()))
                .build();
        var proof = new CommitteeEndorseProof();
        proof.setCommitteeId(committeeId);
        proof.setSigs(ListUtil.toList(nodeProof));
        vcs.setPtcProof(proof.encode());

        return vcs;
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
