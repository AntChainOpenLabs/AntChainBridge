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

import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.core.base.ConsensusState;
import com.alipay.antchain.bridge.commons.core.base.CrossChainLane;
import com.alipay.antchain.bridge.commons.core.base.UniformCrosschainPacket;
import com.alipay.antchain.bridge.commons.core.bta.IBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ValidatedConsensusState;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.exception.InvalidBtaException;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.TpBtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.EndorseBlockStateResp;

public interface IEndorserService {

    TpBtaWrapper queryMatchedTpBta(CrossChainLane lane);

    TpBtaWrapper verifyBta(AbstractCrossChainCertificate domainCert, IBlockchainTrustAnchor bta) throws InvalidBtaException;

    ValidatedConsensusState commitAnchorState(CrossChainLane crossChainLane, ConsensusState anchorState);

    ValidatedConsensusState commitConsensusState(CrossChainLane crossChainLane, ConsensusState currState);

    CommitteeNodeProof verifyUcp(CrossChainLane crossChainLane, UniformCrosschainPacket ucp);

    CommitteeNodeProof verifyUcpWithMonitorSystem(CrossChainLane crossChainLane, UniformCrosschainPacket ucp);

    void relayUcpToMonitorSystem(UniformCrosschainPacket ucp);

    EndorseBlockStateResp endorseBlockState(CrossChainLane crossChainLane, String receiverDomain, BigInteger height);
}
