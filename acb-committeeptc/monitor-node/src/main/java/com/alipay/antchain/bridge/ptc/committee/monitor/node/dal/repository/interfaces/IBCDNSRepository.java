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

import java.util.List;
import java.util.Map;

import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.enums.BCDNSStateEnum;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.BCDNSServiceDO;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.DomainSpaceCertWrapper;

public interface IBCDNSRepository {

    long countBCDNSService();

    boolean hasDomainSpaceCert(String domainSpace);

    void saveDomainSpaceCert(DomainSpaceCertWrapper domainSpaceCertWrapper);

    DomainSpaceCertWrapper getDomainSpaceCert(String domainSpace);

    DomainSpaceCertWrapper getDomainSpaceCert(ObjectIdentity ownerOid);

    Map<String, DomainSpaceCertWrapper> getDomainSpaceCertChain(String leafDomainSpace);

    List<String> getDomainSpaceChain(String leafDomainSpace);

    boolean hasBCDNSService(String domainSpace);

    BCDNSServiceDO getBCDNSServiceDO(String domainSpace);

    void deleteBCDNSServiceDO(String domainSpace);

    List<String> getAllBCDNSDomainSpace();

    void saveBCDNSServiceDO(BCDNSServiceDO bcdnsServiceDO);

    void updateBCDNSServiceState(String domainSpace, BCDNSStateEnum stateEnum);

    void updateBCDNSServiceProperties(String domainSpace, byte[] rawProp);
}
