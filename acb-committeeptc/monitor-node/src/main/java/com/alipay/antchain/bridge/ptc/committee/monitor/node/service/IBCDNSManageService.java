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

import java.util.List;
import java.util.Map;

import com.alipay.antchain.bridge.bcdns.service.BCDNSTypeEnum;
import com.alipay.antchain.bridge.bcdns.service.IBlockChainDomainNameService;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.BCDNSServiceDO;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.DomainSpaceCertWrapper;

public interface IBCDNSManageService {

    long countBCDNSService();

    IBlockChainDomainNameService getBCDNSClient(String domainSpace);

    void registerBCDNSService(String domainSpace, BCDNSTypeEnum bcdnsType, byte[] config, AbstractCrossChainCertificate bcdnsRootCert);

    IBlockChainDomainNameService startBCDNSService(BCDNSServiceDO bcdnsServiceDO);

    void restartBCDNSService(String domainSpace);

    void stopBCDNSService(String domainSpace);

    void saveBCDNSServiceData(BCDNSServiceDO bcdnsServiceDO);

    BCDNSServiceDO getBCDNSServiceData(String domainSpace);

    void deleteBCDNSServiceDate(String domainSpace);

    List<String> getAllBCDNSDomainSpace();

    boolean hasBCDNSServiceData(String domainSpace);

    Map<String, AbstractCrossChainCertificate> getTrustRootCertChain(String domainSpace);

    List<String> getDomainSpaceChain(String domainSpace);

    AbstractCrossChainCertificate getTrustRootCertForRootDomain();

    boolean validateCrossChainCertificate(AbstractCrossChainCertificate certificate);

    boolean validateCrossChainCertificate(
            AbstractCrossChainCertificate certificate,
            Map<String, AbstractCrossChainCertificate> domainSpaceCertPath
    );

    DomainSpaceCertWrapper getDomainSpaceCert(String domainSpace);

    DomainSpaceCertWrapper getDomainSpaceCert(ObjectIdentity ownerOid);

    void saveDomainSpaceCerts(Map<String, AbstractCrossChainCertificate> domainSpaceCerts);
}
