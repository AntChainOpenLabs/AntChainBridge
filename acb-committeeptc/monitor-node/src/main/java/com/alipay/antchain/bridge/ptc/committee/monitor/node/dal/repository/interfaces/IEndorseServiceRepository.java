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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces;

import java.math.BigInteger;

import com.alipay.antchain.bridge.commons.core.base.CrossChainLane;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.BtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.TpBtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.ValidatedConsensusStateWrapper;

public interface IEndorseServiceRepository {

    TpBtaWrapper getMatchedTpBta(CrossChainLane lane);

    TpBtaWrapper getMatchedTpBta(CrossChainLane lane, int tpbtaVersion);

    TpBtaWrapper getExactTpBta(CrossChainLane lane);

    TpBtaWrapper getExactTpBta(CrossChainLane lane, int tpbtaVersion);

    void setTpBta(TpBtaWrapper tpBtaWrapper);

    boolean hasTpBta(CrossChainLane lane, int tpbtaVersion);

    BtaWrapper getBta(String domain);

    BtaWrapper getBta(String domain, int subjectVersion);

    void setBta(BtaWrapper btaWrapper);

    boolean hasBta(String domain, int subjectVersion);

    ValidatedConsensusStateWrapper getLatestValidatedConsensusState(String domain);

    ValidatedConsensusStateWrapper getValidatedConsensusState(String domain, BigInteger height);

    ValidatedConsensusStateWrapper getValidatedConsensusState(String domain, String hash);

    void setValidatedConsensusState(ValidatedConsensusStateWrapper validatedConsensusStateWrapper);

    boolean hasValidatedConsensusState(String domain, BigInteger height);
}
