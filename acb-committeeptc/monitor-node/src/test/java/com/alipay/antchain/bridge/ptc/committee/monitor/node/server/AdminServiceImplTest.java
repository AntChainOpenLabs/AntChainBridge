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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.server;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.alipay.antchain.bridge.bcdns.factory.BlockChainDomainNameServiceFactory;
import com.alipay.antchain.bridge.bcdns.service.BCDNSTypeEnum;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import com.alipay.antchain.bridge.commons.core.ptc.PTCVerifyAnchor;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.TestBase;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.enums.BCDNSStateEnum;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.ISystemConfigRepository;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.server.grpc.*;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IBCDNSManageService;
import com.google.protobuf.ByteString;
import io.grpc.internal.testing.StreamRecorder;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class AdminServiceImplTest extends TestBase {

    private static final AbstractCrossChainCertificate BCDNS_ROOT_CERT_OBJ = CrossChainCertificateUtil.readCrossChainCertificateFromPem(BCDNS_ROOT_CERT.getBytes());

    @Resource
    private AdminServiceImpl adminService;

    @Resource
    private IBCDNSManageService bcdnsManageService;

    @Resource
    private ISystemConfigRepository systemConfigRepository;

    @Test
    @SneakyThrows
    public void testRegisterBcdnsService() {

        MockedStatic mockStaticObj = null;
        try {
            mockStaticObj = mockStatic(BlockChainDomainNameServiceFactory.class);
            when(BlockChainDomainNameServiceFactory.create(notNull(), any())).thenReturn(new DummyBcdnsServiceImpl());

            StreamRecorder<Response> responseObserver = StreamRecorder.create();
            adminService.registerBcdnsService(
                    RegisterBcdnsServiceRequest.newBuilder()
                            .setBcdnsType(BCDNSTypeEnum.EMBEDDED.getCode())
                            .setBcdnsRootCert(BCDNS_ROOT_CERT)
                            .setDomainSpace(CrossChainDomain.ROOT_DOMAIN_SPACE)
                            .setConfig(ByteString.empty())
                            .build(),
                    responseObserver
            );
            Assert.assertTrue(responseObserver.awaitCompletion(5, TimeUnit.SECONDS));
            Assert.assertNull(responseObserver.getError());

            var results = responseObserver.getValues();
            Assert.assertEquals(1, results.size());
            Assert.assertEquals(0, results.getFirst().getCode());

            Assert.assertTrue(bcdnsManageService.hasBCDNSServiceData(CrossChainDomain.ROOT_DOMAIN_SPACE));
            Assert.assertEquals(1, bcdnsManageService.countBCDNSService());
            Assert.assertNotNull(bcdnsManageService.getBCDNSClient(CrossChainDomain.ROOT_DOMAIN_SPACE));
        } finally {
            if (ObjectUtil.isNotNull(mockStaticObj)) {
                mockStaticObj.close();
            }
        }
    }

    @Test
    @SneakyThrows
    public void testGetBcdnsServiceInfo() {
        MockedStatic mockStaticObj = null;
        try {
            mockStaticObj = mockStatic(BlockChainDomainNameServiceFactory.class);
            when(BlockChainDomainNameServiceFactory.create(notNull(), any())).thenReturn(new DummyBcdnsServiceImpl());
            bcdnsManageService.registerBCDNSService(
                    CrossChainDomain.ROOT_DOMAIN_SPACE,
                    BCDNSTypeEnum.EMBEDDED,
                    "{}".getBytes(),
                    BCDNS_ROOT_CERT_OBJ
            );
            StreamRecorder<Response> responseObserver = StreamRecorder.create();
            adminService.getBcdnsServiceInfo(
                    GetBcdnsServiceInfoRequest.newBuilder()
                            .setDomainSpace(CrossChainDomain.ROOT_DOMAIN_SPACE)
                            .build(),
                    responseObserver
            );
            Assert.assertTrue(responseObserver.awaitCompletion(5, TimeUnit.SECONDS));
            Assert.assertNull(responseObserver.getError());

            var results = responseObserver.getValues();
            Assert.assertEquals(1, results.size());
            Assert.assertEquals(0, results.getFirst().getCode());

            Assert.assertTrue(JSONUtil.isTypeJSON(results.getFirst().getGetBcdnsServiceInfoResp().getInfoJson()));
        } finally {
            if (ObjectUtil.isNotNull(mockStaticObj)) {
                mockStaticObj.close();
            }
        }
    }

    @Test
    @SneakyThrows
    public void testStopAndRestart() {
        MockedStatic mockStaticObj = null;
        try {
            mockStaticObj = mockStatic(BlockChainDomainNameServiceFactory.class);
            when(BlockChainDomainNameServiceFactory.create(notNull(), any())).thenReturn(new DummyBcdnsServiceImpl());
            bcdnsManageService.registerBCDNSService(
                    CrossChainDomain.ROOT_DOMAIN_SPACE,
                    BCDNSTypeEnum.EMBEDDED,
                    "{}".getBytes(),
                    BCDNS_ROOT_CERT_OBJ
            );
            StreamRecorder<Response> responseObserver = StreamRecorder.create();
            adminService.stopBcdnsService(
                    StopBcdnsServiceRequest.newBuilder()
                            .setDomainSpace(CrossChainDomain.ROOT_DOMAIN_SPACE)
                            .build(),
                    responseObserver
            );
            Assert.assertTrue(responseObserver.awaitCompletion(5, TimeUnit.SECONDS));
            Assert.assertNull(responseObserver.getError());

            var results = responseObserver.getValues();
            Assert.assertEquals(1, results.size());
            Assert.assertEquals(0, results.getFirst().getCode());

            Assert.assertEquals(
                    BCDNSStateEnum.FROZEN,
                    bcdnsManageService.getBCDNSServiceData(CrossChainDomain.ROOT_DOMAIN_SPACE).getState()
            );

            adminService.restartBcdnsService(
                    RestartBcdnsServiceRequest.newBuilder()
                            .setDomainSpace(CrossChainDomain.ROOT_DOMAIN_SPACE)
                            .build(),
                    responseObserver
            );

            Assert.assertEquals(
                    BCDNSStateEnum.WORKING,
                    bcdnsManageService.getBCDNSServiceData(CrossChainDomain.ROOT_DOMAIN_SPACE).getState()
            );

            adminService.deleteBcdnsService(
                    DeleteBcdnsServiceRequest.newBuilder().setDomainSpace(CrossChainDomain.ROOT_DOMAIN_SPACE).build(),
                    responseObserver
            );
            Assert.assertFalse(
                    bcdnsManageService.hasBCDNSServiceData(CrossChainDomain.ROOT_DOMAIN_SPACE)
            );

            var ptcCertObj = NODE_PTC_CERT;

            var va = new PTCVerifyAnchor();
            va.setAnchor(new byte[]{});
            va.setVersion(BigInteger.ONE);

            var trustRoot = new PTCTrustRoot();
            trustRoot.setSig(new byte[]{});
            trustRoot.setNetworkInfo(new byte[]{});
            trustRoot.setPtcCrossChainCert(ptcCertObj);
            trustRoot.setIssuerBcdnsDomainSpace(new CrossChainDomain(CrossChainDomain.ROOT_DOMAIN_SPACE));
            trustRoot.setSigAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1);
            trustRoot.setVerifyAnchorMap(MapUtil.builder(BigInteger.ONE, va).build());

            adminService.addPtcTrustRoot(
                    AddPtcTrustRootRequest.newBuilder().setRawTrustRoot(ByteString.copyFrom(trustRoot.encode())).build(),
                    responseObserver
            );

            Assert.assertNotNull(
                    systemConfigRepository.getPtcTrustRoot()
            );

        } finally {
            if (ObjectUtil.isNotNull(mockStaticObj)) {
                mockStaticObj.close();
            }
        }
    }
}
