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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.dal;

import cn.hutool.core.util.ArrayUtil;
import com.alipay.antchain.bridge.bcdns.service.BCDNSTypeEnum;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.TestBase;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.enums.BCDNSStateEnum;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.BCDNSServiceDO;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.DomainSpaceCertWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.IBCDNSRepository;
import jakarta.annotation.Resource;
import org.junit.Assert;
import org.junit.Test;

public class BCDNSRepositoryTest extends TestBase {

    @Resource
    private IBCDNSRepository bcdnsRepository;

    private final AbstractCrossChainCertificate bcdnsRootCertObj = CrossChainCertificateUtil.readCrossChainCertificateFromPem(BCDNS_ROOT_CERT.getBytes());

    @Test
    public void testDomainSpaceCert() {
        var dscObj = CrossChainCertificateUtil.readCrossChainCertificateFromPem(DOT_COM_DOMAIN_SPACE_CERT.getBytes());

        bcdnsRepository.saveDomainSpaceCert(new DomainSpaceCertWrapper(bcdnsRootCertObj));
        bcdnsRepository.saveDomainSpaceCert(new DomainSpaceCertWrapper(dscObj));

        Assert.assertEquals(
                bcdnsRootCertObj.getCredentialSubjectInstance().getApplicant(),
                bcdnsRepository.getDomainSpaceCert(CrossChainCertificateUtil.getCrossChainDomainSpace(bcdnsRootCertObj).getDomain()).getOwnerOid()
        );
        Assert.assertTrue(
                ArrayUtil.equals(
                        bcdnsRootCertObj.encode(),
                        bcdnsRepository.getDomainSpaceCert(bcdnsRootCertObj.getCredentialSubjectInstance().getApplicant()).getDomainSpaceCert().encode()
                )
        );
        Assert.assertTrue(
                bcdnsRepository.hasDomainSpaceCert(CrossChainCertificateUtil.getCrossChainDomainSpace(bcdnsRootCertObj).getDomain())
        );

        Assert.assertEquals(
                2,
                bcdnsRepository.getDomainSpaceCertChain(CrossChainCertificateUtil.getCrossChainDomainSpace(dscObj).getDomain()).size()
        );
        Assert.assertEquals(
                CrossChainCertificateUtil.getCrossChainDomainSpace(bcdnsRootCertObj).getDomain(),
                bcdnsRepository.getDomainSpaceCertChain(CrossChainCertificateUtil.getCrossChainDomainSpace(dscObj).getDomain())
                        .get(CrossChainDomain.ROOT_DOMAIN_SPACE).getDomainSpace()
        );
    }

    @Test
    public void testBcdns() {

        var bcdnsService = new BCDNSServiceDO();
        bcdnsService.setDomainSpaceCertWrapper(new DomainSpaceCertWrapper(bcdnsRootCertObj));
        bcdnsService.setDomainSpace(CrossChainCertificateUtil.getCrossChainDomainSpace(bcdnsRootCertObj).getDomain());
        bcdnsService.setType(BCDNSTypeEnum.EMBEDDED);
        bcdnsService.setState(BCDNSStateEnum.WORKING);
        bcdnsService.setOwnerOid(bcdnsRootCertObj.getCredentialSubjectInstance().getApplicant());
        bcdnsService.setProperties("{}".getBytes());

        bcdnsRepository.saveBCDNSServiceDO(bcdnsService);

        Assert.assertEquals(
                1,
                bcdnsRepository.getAllBCDNSDomainSpace().size()
        );
        Assert.assertNotNull(
                bcdnsRepository.getBCDNSServiceDO(bcdnsService.getDomainSpace())
        );
        Assert.assertEquals(
                bcdnsService.getOwnerOid(),
                bcdnsRepository.getBCDNSServiceDO(bcdnsService.getDomainSpace()).getOwnerOid()
        );
        Assert.assertEquals(
                bcdnsService.getState(),
                bcdnsRepository.getBCDNSServiceDO(bcdnsService.getDomainSpace()).getState()
        );
        Assert.assertTrue(
                ArrayUtil.equals(
                        bcdnsService.getProperties(),
                        bcdnsRepository.getBCDNSServiceDO(bcdnsService.getDomainSpace()).getProperties()
                )
        );
        Assert.assertTrue(
                bcdnsRepository.hasBCDNSService(bcdnsService.getDomainSpace())
        );
        Assert.assertEquals(1, bcdnsRepository.countBCDNSService());

        bcdnsRepository.updateBCDNSServiceState(bcdnsService.getDomainSpace(), BCDNSStateEnum.FROZEN);
        Assert.assertEquals(
                BCDNSStateEnum.FROZEN,
                bcdnsRepository.getBCDNSServiceDO(bcdnsService.getDomainSpace()).getState()
        );
        var newProp = """
                {"test": "test"}
                """;
        bcdnsRepository.updateBCDNSServiceProperties(bcdnsService.getDomainSpace(), newProp.getBytes());
        Assert.assertEquals(
                newProp,
                new String(bcdnsRepository.getBCDNSServiceDO(bcdnsService.getDomainSpace()).getProperties())
        );
    }
}
