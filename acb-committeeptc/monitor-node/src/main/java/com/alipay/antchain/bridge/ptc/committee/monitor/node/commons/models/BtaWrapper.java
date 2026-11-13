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

import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.bta.IBlockchainTrustAnchor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BtaWrapper {

    private IBlockchainTrustAnchor bta;

    public String getDomain() {
        return ObjectUtil.defaultIfNull(bta.getDomain(), new CrossChainDomain()).getDomain();
    }

    public String getProduct() {
        return bta.getSubjectProduct();
    }

    public int getSubjectVersion() {
        return bta.getSubjectVersion();
    }

    public int getBtaVersion() {
        return bta.getVersion();
    }


}
