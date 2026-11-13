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

package com.alipay.antchain.bridge.ptc.service;

import java.util.Set;

import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.bta.IBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyProof;
import com.alipay.antchain.bridge.commons.core.ptc.ValidatedConsensusState;
import com.alipay.antchain.bridge.ptc.types.PtcFeatureDescriptor;
import com.alipay.antchain.bridge.ptc.types.PTCVerifyCrossChainMessageResult;

public interface IPTCService {

    void startup(byte[] config);

    PtcFeatureDescriptor getPtcFeatureDescriptor();

    ThirdPartyBlockchainTrustAnchor queryThirdPartyBlockchainTrustAnchor(CrossChainLane lane);

    ThirdPartyBlockchainTrustAnchor verifyBlockchainTrustAnchor(
            AbstractCrossChainCertificate domainCert,
            IBlockchainTrustAnchor blockchainTrustAnchor
    );

    ValidatedConsensusState commitAnchorState(IBlockchainTrustAnchor bta, ThirdPartyBlockchainTrustAnchor tpbta, ConsensusState anchorState);

    ValidatedConsensusState commitConsensusState(
            ThirdPartyBlockchainTrustAnchor tpbta,
            ValidatedConsensusState preValidatedConsensusState,
            ConsensusState consensusState
    );

    ThirdPartyProof verifyCrossChainMessage(
            ThirdPartyBlockchainTrustAnchor tpbta,
            ValidatedConsensusState validatedConsensusState,
            UniformCrosschainPacket ucp
    );

    default PTCVerifyCrossChainMessageResult verifyCrossChainMessageWithResult(
            ThirdPartyBlockchainTrustAnchor tpbta,
            ValidatedConsensusState validatedConsensusState,
            UniformCrosschainPacket ucp
    ) {
        return new PTCVerifyCrossChainMessageResult(
                verifyCrossChainMessage(tpbta, validatedConsensusState, ucp),
                "",
                ""
        );
    }

    default PTCVerifyCrossChainMessageResult verifyCrossChainMessageWithResult(
            ThirdPartyBlockchainTrustAnchor tpbta,
            ValidatedConsensusState validatedConsensusState,
            UniformCrosschainPacket ucp,
            String ucpId
    ) {
        return verifyCrossChainMessageWithResult(tpbta, validatedConsensusState, ucp);
    }

    Set<String> querySupportedBlockchainProducts();

    BlockState queryCurrVerifiedBlockState(ThirdPartyBlockchainTrustAnchor tpbta);

    ThirdPartyProof endorseBlockState(
            ThirdPartyBlockchainTrustAnchor tpbta,
            CrossChainDomain receiverDomain,
            ValidatedConsensusState validatedConsensusState
    );
}
