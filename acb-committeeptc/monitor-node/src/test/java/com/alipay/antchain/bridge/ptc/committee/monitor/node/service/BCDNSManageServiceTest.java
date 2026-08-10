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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.bcdns.factory.BlockChainDomainNameServiceFactory;
import com.alipay.antchain.bridge.bcdns.service.BCDNSTypeEnum;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.TestBase;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.enums.BCDNSStateEnum;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.BCDNSServiceDO;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.DomainSpaceCertWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.IBCDNSRepository;
import jakarta.annotation.Resource;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class BCDNSManageServiceTest extends TestBase {

    private static MockedStatic mockStaticObj = null;

    @BeforeClass
    public static void beforeClass() throws Exception {
        try {
            mockStaticObj = mockStatic(BlockChainDomainNameServiceFactory.class);
            when(BlockChainDomainNameServiceFactory.create(notNull(), any())).thenReturn(new DummyBcdnsServiceImpl());
        } catch (Exception e) {
            if (ObjectUtil.isNotNull(mockStaticObj)) {
                mockStaticObj.close();
            }
        }
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (ObjectUtil.isNotNull(mockStaticObj)) {
            mockStaticObj.close();
        }
    }

    private static BCDNSServiceDO bcdnsServiceDO;

    static {
        var dsc = new DomainSpaceCertWrapper(
                CrossChainCertificateUtil.readCrossChainCertificateFromPem(BCDNS_ROOT_CERT.getBytes())
        );
        bcdnsServiceDO = new BCDNSServiceDO(
                CrossChainDomain.ROOT_DOMAIN_SPACE,
                dsc.getOwnerOid(),
                dsc,
                BCDNSTypeEnum.EMBEDDED,
                BCDNSStateEnum.WORKING,
                "{}".getBytes()
        );
    }

    @Resource
    private IBCDNSManageService bcdnsManageService;

    @MockBean
    private IBCDNSRepository bcdnsRepository;

    @Test
    public void countBCDNSService() {
        when(bcdnsRepository.countBCDNSService()).thenReturn(1L);
        assertEquals(
                1,
                bcdnsManageService.countBCDNSService()
        );
    }

    @Test
    public void getBCDNSClient() {
        when(bcdnsRepository.getBCDNSServiceDO(any())).thenReturn(bcdnsServiceDO);
        assertNotNull(bcdnsManageService.getBCDNSClient(CrossChainDomain.ROOT_DOMAIN_SPACE));
    }

    @Test
    public void registerBCDNSService() {
        bcdnsManageService.registerBCDNSService(
                bcdnsServiceDO.getDomainSpace(),
                bcdnsServiceDO.getType(),
                bcdnsServiceDO.getProperties(),
                bcdnsServiceDO.getDomainSpaceCertWrapper().getDomainSpaceCert()
        );
        Assert.assertNotNull(bcdnsManageService.getBCDNSClient(CrossChainDomain.ROOT_DOMAIN_SPACE));
    }

    @Test
    public void startBCDNSService() {
        assertNotNull(bcdnsManageService.startBCDNSService(bcdnsServiceDO));
    }

    @Test
    public void restartBCDNSService() {
        var frozenBcdnsDO = BeanUtil.copyProperties(bcdnsServiceDO, BCDNSServiceDO.class);
        frozenBcdnsDO.setState(BCDNSStateEnum.FROZEN);
        when(bcdnsRepository.getBCDNSServiceDO(any())).thenReturn(frozenBcdnsDO);
        bcdnsManageService.restartBCDNSService(frozenBcdnsDO.getDomainSpace());
    }

    @Test
    public void stopBCDNSService() {
        when(bcdnsRepository.hasBCDNSService(any())).thenReturn(true);
        bcdnsManageService.stopBCDNSService(bcdnsServiceDO.getDomainSpace());
    }
}