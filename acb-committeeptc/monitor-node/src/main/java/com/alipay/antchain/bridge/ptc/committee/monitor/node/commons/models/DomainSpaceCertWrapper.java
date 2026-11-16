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

import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DomainSpaceCertWrapper {

    public DomainSpaceCertWrapper(AbstractCrossChainCertificate domainSpaceCert) {
        this.domainSpaceCert = domainSpaceCert;
        this.domainSpace = CrossChainCertificateUtil.isBCDNSTrustRoot(domainSpaceCert) ?
                CrossChainDomain.ROOT_DOMAIN_SPACE : CrossChainCertificateUtil.getCrossChainDomainSpace(domainSpaceCert).getDomain();
        this.parentDomainSpace = CrossChainCertificateUtil.isBCDNSTrustRoot(domainSpaceCert) ?
                null : CrossChainCertificateUtil.getParentDomainSpace(domainSpaceCert).getDomain();
        this.ownerOid = domainSpaceCert.getCredentialSubjectInstance().getApplicant();
    }

    private String domainSpace;

    private String parentDomainSpace;

    private ObjectIdentity ownerOid;

    private AbstractCrossChainCertificate domainSpaceCert;
}
