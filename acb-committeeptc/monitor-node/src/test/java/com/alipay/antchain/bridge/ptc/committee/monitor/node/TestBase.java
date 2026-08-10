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

package com.alipay.antchain.bridge.ptc.committee.monitor.node;


import java.io.ByteArrayInputStream;
import java.security.PrivateKey;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.PemUtil;
import com.alipay.antchain.bridge.bcdns.service.IBlockChainDomainNameService;
import com.alipay.antchain.bridge.bcdns.types.base.DomainRouter;
import com.alipay.antchain.bridge.bcdns.types.exception.AntChainBridgeBCDNSException;
import com.alipay.antchain.bridge.bcdns.types.req.*;
import com.alipay.antchain.bridge.bcdns.types.resp.*;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IScheduledTaskService;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = NodeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Sql(scripts = {"classpath:data/ddl.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:data/drop_all.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public abstract class TestBase {

    public static final String BCDNS_ROOT_CERT = """
            -----BEGIN BCDNS TRUST ROOT CERTIFICATE-----
            AADWAQAAAAABAAAAMQEABAAAAHRlc3QCAAEAAAAAAwBrAAAAAABlAAAAAAABAAAA
            AAEAWAAAADBWMBAGByqGSM49AgEGBSuBBAAKA0IABPSyWJiXGQUhIzdqzRq7hdcy
            CKuSS40qpcGUNsTXJtky9Ka1hXWqbdAVawAqWsNDIrSp2I5HL9eqpvl1GxSvxN8E
            AAgAAADSJb9mAAAAAAUACAAAAFJZoGgAAAAABgCGAAAAAACAAAAAAAADAAAAYmlm
            AQBrAAAAAABlAAAAAAABAAAAAAEAWAAAADBWMBAGByqGSM49AgEGBSuBBAAKA0IA
            BPSyWJiXGQUhIzdqzRq7hdcyCKuSS40qpcGUNsTXJtky9Ka1hXWqbdAVawAqWsND
            IrSp2I5HL9eqpvl1GxSvxN8CAAAAAAAHAJ8AAAAAAJkAAAAAAAoAAABLRUNDQUst
            MjU2AQAgAAAAvSTYE3fohb8st2Hu6eGR0uR+HI+Fr+ig4A/wR/c7ahMCABYAAABL
            ZWNjYWsyNTZXaXRoU2VjcDI1NmsxAwBBAAAAsGsuR7geJEPmaO9udja1wW+da1ex
            KNVhpk7oi66g3UNNpYSoJK3wzibTKBj/cRfZCY/FkZdp95j6mMcK2oHsAAA=
            -----END BCDNS TRUST ROOT CERTIFICATE-----
            """;

    public static final String DOT_COM_DOMAIN_SPACE_CERT = """
            -----BEGIN DOMAIN NAME CERTIFICATE-----
            AADtAQAAAAABAAAAMQEABAAAAC5jb20CAAEAAAABAwBrAAAAAABlAAAAAAABAAAA
            AAEAWAAAADBWMBAGByqGSM49AgEGBSuBBAAKA0IABPSyWJiXGQUhIzdqzRq7hdcy
            CKuSS40qpcGUNsTXJtky9Ka1hXWqbdAVawAqWsNDIrSp2I5HL9eqpvl1GxSvxN8E
            AAgAAADSJb9mAAAAAAUACAAAAFJZoGgAAAAABgCdAAAAAACXAAAAAAADAAAAMS4w
            AQABAAAAAQIAAAAAAAMABAAAAC5jb20EAGsAAAAAAGUAAAAAAAEAAAAAAQBYAAAA
            MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAE9LJYmJcZBSEjN2rNGruF1zIIq5JLjSql
            wZQ2xNcm2TL0prWFdapt0BVrACpaw0MitKnYjkcv16qm+XUbFK/E3wUAAAAAAAcA
            nwAAAAAAmQAAAAAACgAAAEtFQ0NBSy0yNTYBACAAAADTK+miwHYLK8NTN2okHMfo
            mEShYXWhzkrjivLNXDGt/wIAFgAAAEtlY2NhazI1NldpdGhTZWNwMjU2azEDAEEA
            AAAWu0d+MaWZfLOUVBnDT2/uC+IxKUyZqxdjsNXy2x7n7zJYSgof+ujJWE7r8qWT
            1tBHkbDC/YHXA8QLgVPC2NfMAQ==
            -----END DOMAIN NAME CERTIFICATE-----
            """;

    public static final String ANTCHAIN_DOT_COM_CERT = """
            -----BEGIN DOMAIN NAME CERTIFICATE-----
            AAD/AQAAAAABAAAAMQEACgAAAHRlc3Rkb21haW4CAAEAAAABAwBrAAAAAABlAAAA
            AAABAAAAAAEAWAAAADBWMBAGByqGSM49AgEGBSuBBAAKA0IABPSyWJiXGQUhIzdq
            zRq7hdcyCKuSS40qpcGUNsTXJtky9Ka1hXWqbdAVawAqWsNDIrSp2I5HL9eqpvl1
            GxSvxN8EAAgAAADSJb9mAAAAAAUACAAAAFJZoGgAAAAABgCpAAAAAACjAAAAAAAD
            AAAAMS4wAQABAAAAAAIABAAAAC5jb20DAAwAAABhbnRjaGFpbi5jb20EAGsAAAAA
            AGUAAAAAAAEAAAAAAQBYAAAAMFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAE9LJYmJcZ
            BSEjN2rNGruF1zIIq5JLjSqlwZQ2xNcm2TL0prWFdapt0BVrACpaw0MitKnYjkcv
            16qm+XUbFK/E3wUAAAAAAAcAnwAAAAAAmQAAAAAACgAAAEtFQ0NBSy0yNTYBACAA
            AAD2j0+ge6shN1piGmDyb+YY7E3Fs4E7SMeQGxvOiJC5sgIAFgAAAEtlY2NhazI1
            NldpdGhTZWNwMjU2azEDAEEAAAB8QQjC0e6/Xl4PaTcx+BtX/fg3BQN9b0YdXEXR
            oYklgXW6KHQ7YLt7farETz2inRjlT0eJka4LvJUSinX53WXVAA==
            -----END DOMAIN NAME CERTIFICATE-----
            """;

    public static final AbstractCrossChainCertificate NODE_PTC_CERT = CrossChainCertificateUtil.readCrossChainCertificateFromPem(
            FileUtil.readBytes("ptc.crt")
    );

    public static final PrivateKey NODE_PTC_PRIVATE_KEY = SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().readPemPrivateKey(
            FileUtil.readBytes("private_key.pem")
    );

    public static final byte[] RAW_NODE_PTC_PUBLIC_KEY = PemUtil.readPem(new ByteArrayInputStream(FileUtil.readBytes("public_key.pem")));

    @MockBean
    private IScheduledTaskService scheduledTaskService;

    public static class DummyBcdnsServiceImpl implements IBlockChainDomainNameService {
        @Override
        public QueryBCDNSTrustRootCertificateResponse queryBCDNSTrustRootCertificate() {
            return null;
        }

        @Override
        public ApplyRelayerCertificateResponse applyRelayerCertificate(AbstractCrossChainCertificate abstractCrossChainCertificate) {
            return null;
        }

        @Override
        public ApplicationResult queryRelayerCertificateApplicationResult(String s) {
            return null;
        }

        @Override
        public ApplyPTCCertificateResponse applyPTCCertificate(AbstractCrossChainCertificate abstractCrossChainCertificate) {
            return null;
        }

        @Override
        public ApplicationResult queryPTCCertificateApplicationResult(String s) {
            return null;
        }

        @Override
        public ApplyDomainNameCertificateResponse applyDomainNameCertificate(AbstractCrossChainCertificate abstractCrossChainCertificate) {
            return null;
        }

        @Override
        public ApplicationResult queryDomainNameCertificateApplicationResult(String s) {
            return null;
        }

        @Override
        public QueryRelayerCertificateResponse queryRelayerCertificate(QueryRelayerCertificateRequest queryRelayerCertificateRequest) {
            return null;
        }

        @Override
        public QueryPTCCertificateResponse queryPTCCertificate(QueryPTCCertificateRequest queryPTCCertificateRequest) {
            return null;
        }

        @Override
        public QueryDomainNameCertificateResponse queryDomainNameCertificate(QueryDomainNameCertificateRequest queryDomainNameCertificateRequest) {
            return null;
        }

        @Override
        public void registerDomainRouter(RegisterDomainRouterRequest registerDomainRouterRequest) throws AntChainBridgeBCDNSException {

        }

        @Override
        public void registerThirdPartyBlockchainTrustAnchor(RegisterThirdPartyBlockchainTrustAnchorRequest registerThirdPartyBlockchainTrustAnchorRequest) throws AntChainBridgeBCDNSException {

        }

        @Override
        public DomainRouter queryDomainRouter(QueryDomainRouterRequest queryDomainRouterRequest) {
            return null;
        }

        @Override
        public ThirdPartyBlockchainTrustAnchor queryThirdPartyBlockchainTrustAnchor(QueryThirdPartyBlockchainTrustAnchorRequest queryThirdPartyBlockchainTrustAnchorRequest) {
            return null;
        }

        @Override
        public PTCTrustRoot queryPTCTrustRoot(ObjectIdentity objectIdentity) {
            return null;
        }

        @Override
        public void addPTCTrustRoot(PTCTrustRoot ptcTrustRoot) {

        }
    }
}
