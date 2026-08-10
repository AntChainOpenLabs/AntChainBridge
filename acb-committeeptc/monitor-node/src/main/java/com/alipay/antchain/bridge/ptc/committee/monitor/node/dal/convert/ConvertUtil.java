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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.convert;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.bcdns.service.BCDNSTypeEnum;
import com.alipay.antchain.bridge.commons.bcdns.CrossChainCertificateFactory;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import com.alipay.antchain.bridge.commons.core.bta.BlockchainTrustAnchorFactory;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ValidatedConsensusState;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.*;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.entities.*;

public class ConvertUtil {

    @SuppressWarnings("unchecked")
    public static <F, T> T convertFrom(F from) {
        return switch (from) {
            case TpBtaEntity entity -> (T) convertFrom(entity);
            case TpBtaWrapper wrapper -> (T) convertFrom(wrapper);
            case BtaEntity entity -> (T) convertFrom(entity);
            case BtaWrapper wrapper -> (T) convertFrom(wrapper);
            case ValidatedConsensusStatesEntity entity -> (T) convertFrom(entity);
            case ValidatedConsensusStateWrapper wrapper -> (T) convertFrom(wrapper);
            case BCDNSServiceDO serviceDO -> (T) convertFrom(serviceDO);
            case BCDNSServiceEntity entity -> (T) convertFrom(entity);
            case DomainSpaceCertWrapper wrapper -> (T) convertFrom(wrapper);
            case DomainSpaceCertEntity entity -> (T) convertFrom(entity);
            default -> throw new IllegalArgumentException("Unsupported type: " + from.getClass().getName());
        };
    }

    private static TpBtaWrapper convertFrom(TpBtaEntity entity) {
        return new TpBtaWrapper(
                ThirdPartyBlockchainTrustAnchor.decode(entity.getRawTpBta())
        );
    }

    private static TpBtaEntity convertFrom(TpBtaWrapper wrapper) {
        return TpBtaEntity.builder()
                .tpbtaVersion(wrapper.getTpbta().getTpbtaVersion())
                .ptcVerifyAnchorVersion(wrapper.getTpbta().getPtcVerifyAnchorVersion().longValue())
                .btaSubjectVersion(wrapper.getTpbta().getBtaSubjectVersion())
                .senderDomain(wrapper.getCrossChainLane().getSenderDomain().getDomain())
                .senderId(ObjectUtil.isNull(wrapper.getCrossChainLane().getSenderId()) ? "" : wrapper.getCrossChainLane().getSenderIdHex())
                .receiverDomain(ObjectUtil.isNull(wrapper.getCrossChainLane().getReceiverDomain()) ? "" : wrapper.getCrossChainLane().getReceiverDomain().getDomain())
                .receiverId(ObjectUtil.isNull(wrapper.getCrossChainLane().getReceiverId()) ? "" : wrapper.getCrossChainLane().getReceiverIdHex())
                .rawTpBta(wrapper.getTpbta().encode())
                .build();
    }

    private static BtaWrapper convertFrom(BtaEntity entity) {
        return new BtaWrapper(BlockchainTrustAnchorFactory.createBTA(entity.getRawBta()));
    }

    private static BtaEntity convertFrom(BtaWrapper wrapper) {
        return BtaEntity.builder()
                .domain(wrapper.getDomain())
                .product(wrapper.getProduct())
                .btaVersion(wrapper.getBtaVersion())
                .subjectVersion(wrapper.getSubjectVersion())
                .rawBta(wrapper.getBta().encode())
                .build();
    }

    private static ValidatedConsensusStateWrapper convertFrom(ValidatedConsensusStatesEntity entity) {
        return new ValidatedConsensusStateWrapper(ValidatedConsensusState.decode(entity.getRawVcs()));
    }

    private static ValidatedConsensusStatesEntity convertFrom(ValidatedConsensusStateWrapper wrapper) {
        return ValidatedConsensusStatesEntity.builder()
                .csVersion(wrapper.getValidatedConsensusState().getCsVersion())
                .hash(HexUtil.encodeHexStr(wrapper.getValidatedConsensusState().getHash()))
                .height(wrapper.getValidatedConsensusState().getHeight().toString())
                .domain(wrapper.getDomain())
                .rawVcs(wrapper.getValidatedConsensusState().encode())
                .parentHash(wrapper.getParentHash())
                .build();
    }


    private static BCDNSServiceEntity convertFrom(BCDNSServiceDO serviceDO) {
        return BCDNSServiceEntity.builder()
                .domainSpace(serviceDO.getDomainSpace())
                .type(serviceDO.getType().getCode())
                .parentSpace(serviceDO.getDomainSpaceCertWrapper().getParentDomainSpace())
                .ownerOid(HexUtil.encodeHexStr(serviceDO.getOwnerOid().encode()))
                .state(serviceDO.getState())
                .properties(serviceDO.getProperties())
                .build();
    }

    private static BCDNSServiceDO convertFrom(BCDNSServiceEntity entity) {
        return new BCDNSServiceDO(
                entity.getDomainSpace(),
                ObjectIdentity.decode(HexUtil.decodeHex(entity.getOwnerOid())),
                null,
                BCDNSTypeEnum.parseFromValue(entity.getType()),
                entity.getState(),
                entity.getProperties()
        );
    }

    private static DomainSpaceCertEntity convertFrom(DomainSpaceCertWrapper wrapper) {
        return DomainSpaceCertEntity.builder()
                .domainSpace(wrapper.getDomainSpace())
                .parentSpace(wrapper.getParentDomainSpace())
                .ownerOidHex(HexUtil.encodeHexStr(wrapper.getOwnerOid().encode()))
                .domainSpaceCert(wrapper.getDomainSpaceCert().encode())
                .build();
    }

    private static DomainSpaceCertWrapper convertFrom(DomainSpaceCertEntity entity) {
        return new DomainSpaceCertWrapper(CrossChainCertificateFactory.createCrossChainCertificate(entity.getDomainSpaceCert()));
    }
}
