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

import java.math.BigInteger;

import cn.hutool.core.map.MapUtil;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import com.alipay.antchain.bridge.commons.core.ptc.PTCVerifyAnchor;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.TestBase;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.ISystemConfigRepository;
import jakarta.annotation.Resource;
import org.junit.Assert;
import org.junit.Test;

public class SystemConfigRepositoryTest extends TestBase {

    @Resource
    private ISystemConfigRepository systemConfigRepository;

    @Test
    public void testPtcTrustRoot() {
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
        
        systemConfigRepository.setPtcTrustRoot(trustRoot);
        
        Assert.assertEquals(
                ptcCertObj.getCredentialSubjectInstance().getApplicant(),
                systemConfigRepository.getPtcTrustRoot().getPtcCredentialSubject().getApplicant()
        );
        Assert.assertEquals(
                BigInteger.ONE,
                systemConfigRepository.queryCurrentPtcAnchorVersion()
        );
    }

    @Test
    public void testSystemConfig() {
        systemConfigRepository.setSystemConfig("test", "test");
        Assert.assertEquals(
                "test",
                systemConfigRepository.getSystemConfig("test")
        );
    }
}
