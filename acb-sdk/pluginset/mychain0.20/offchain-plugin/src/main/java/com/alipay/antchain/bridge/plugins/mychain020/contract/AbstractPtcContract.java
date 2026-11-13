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

package com.alipay.antchain.bridge.plugins.mychain020.contract;

import java.math.BigInteger;
import java.util.Set;

import com.alipay.antchain.bridge.commons.core.base.CrossChainLane;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTypeEnum;
import com.alipay.antchain.bridge.commons.core.ptc.PTCVerifyAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;

public interface AbstractPtcContract {

    boolean deployContract(String bcdnsRootCertPem);

    void updatePTCTrustRoot(PTCTrustRoot ptcTrustRoot);

    PTCTrustRoot getPTCTrustRoot(ObjectIdentity ptcOwnerOid);

    boolean hasPTCTrustRoot(ObjectIdentity ptcOwnerOid);

    PTCVerifyAnchor getPTCVerifyAnchor(ObjectIdentity ptcOwnerOid, BigInteger versionNum);

    boolean hasPTCVerifyAnchor(ObjectIdentity ptcOwnerOid, BigInteger versionNum);

    void addTpBta(ThirdPartyBlockchainTrustAnchor tpBta);

    ThirdPartyBlockchainTrustAnchor getTpBta(CrossChainLane tpbtaLane, long tpBtaVersion);

    ThirdPartyBlockchainTrustAnchor getLatestTpBta(CrossChainLane tpbtaLane);

    boolean hasTpBta(CrossChainLane tpbtaLane, long tpBtaVersion);

    Set<PTCTypeEnum> getSupportedPTCTypes();
}
