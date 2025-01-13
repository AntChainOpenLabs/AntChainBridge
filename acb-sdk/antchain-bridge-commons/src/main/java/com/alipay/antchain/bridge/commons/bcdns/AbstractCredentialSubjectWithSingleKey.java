/*
 * Copyright 2023 Ant Group
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

package com.alipay.antchain.bridge.commons.bcdns;

import cn.hutool.core.util.ObjectUtil;

public abstract class AbstractCredentialSubjectWithSingleKey implements ICredentialSubjectWithSingleKey {
    @Override
    public boolean verifyIssueProof(byte[] encoded, AbstractCrossChainCertificate.IssueProof issueProof) {
        if (ObjectUtil.isNull(issueProof)) {
            return false;
        }
        try {
            return issueProof.getSigAlgo().getSigner().verify(getSubjectPublicKey(), encoded, issueProof.getRawProof());
        } catch (Exception e) {
            throw new RuntimeException("failed to verify proof: ", e);
        }
    }
}
