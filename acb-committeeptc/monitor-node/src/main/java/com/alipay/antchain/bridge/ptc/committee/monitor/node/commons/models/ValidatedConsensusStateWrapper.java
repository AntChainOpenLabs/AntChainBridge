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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models;

import java.math.BigInteger;

import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.ptc.ValidatedConsensusState;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ValidatedConsensusStateWrapper {

    private ValidatedConsensusState validatedConsensusState;

    public String getDomain() {
        return ObjectUtil.defaultIfNull(validatedConsensusState.getDomain(), new CrossChainDomain()).getDomain();
    }

    public BigInteger getHeight() {
        return validatedConsensusState.getHeight();
    }

    public String getParentHash() {
        return validatedConsensusState.getParentHashHex();
    }
}
