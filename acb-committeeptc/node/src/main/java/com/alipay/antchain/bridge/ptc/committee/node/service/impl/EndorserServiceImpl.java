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

package com.alipay.antchain.bridge.ptc.committee.node.service.impl;

import java.math.BigInteger;
import java.security.PrivateKey;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.DomainNameCredentialSubject;
import com.alipay.antchain.bridge.commons.bcdns.PTCCredentialSubject;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.am.IAuthMessage;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.bta.IBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.*;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.commons.exception.IllegalCrossChainCertException;
import com.alipay.antchain.bridge.commons.utils.crypto.HashAlgoEnum;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.plugins.spi.ptc.core.VerifyResult;
import com.alipay.antchain.bridge.ptc.committee.node.commons.exception.*;
import com.alipay.antchain.bridge.ptc.committee.node.commons.models.BtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.node.commons.models.TpBtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.node.commons.models.ValidatedConsensusStateWrapper;
import com.alipay.antchain.bridge.ptc.committee.node.dal.repository.interfaces.IBCDNSRepository;
import com.alipay.antchain.bridge.ptc.committee.node.dal.repository.interfaces.IEndorseServiceRepository;
import com.alipay.antchain.bridge.ptc.committee.node.dal.repository.interfaces.ISystemConfigRepository;
import com.alipay.antchain.bridge.ptc.committee.node.service.IEndorserService;
import com.alipay.antchain.bridge.ptc.committee.node.service.IHcdvsPluginService;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeEndorseProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.EndorseBlockStateResp;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.VerifyBtaExtension;
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

        if (!bta.getBta().getInitHeight().equals(anchorState.getHeight())) {
            throw new InvalidConsensusStateException("invalid height: bta's is {} and yours {}",
                    bta.getBta().getInitHeight().toString(), anchorState.getHeight().toString());
        }
        if (!ArrayUtil.equals(bta.getBta().getInitBlockHash(), anchorState.getHash())) {
            throw new InvalidConsensusStateException("invalid block hash: bta's is {} and yours {}",
                    HexUtil.encodeHexStr(bta.getBta().getInitBlockHash()), anchorState.getHashHex());
        }


        var hcdvs = hcdvsPluginService.getHCDVSService(bta.getProduct());
        if (ObjectUtil.isNull(hcdvs)) {
            throw new CommitteeNodeInternalException("hcdvs not found for {}", bta.getProduct());
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

        return signUcp(tpbta, crossChainLane, ucp);
    }

    private CommitteeNodeProof signUcp(TpBtaWrapper tpbta, CrossChainLane crossChainLane, UniformCrosschainPacket ucp) {
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
}
