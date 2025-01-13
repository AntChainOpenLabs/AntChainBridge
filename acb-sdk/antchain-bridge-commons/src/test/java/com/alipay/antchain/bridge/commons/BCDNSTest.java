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

package com.alipay.antchain.bridge.commons;

import java.io.StringWriter;
import java.nio.charset.Charset;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.Date;

import cn.ac.caict.bid.model.BIDDocumentOperation;
import cn.ac.caict.bid.model.BIDpublicKeyOperation;
import cn.bif.common.JsonUtils;
import cn.bif.module.encryption.model.KeyType;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.ECKeyUtil;
import com.alipay.antchain.bridge.commons.bcdns.*;
import com.alipay.antchain.bridge.commons.bcdns.utils.BIDHelper;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.base.BIDInfoObjectIdentity;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentityType;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTypeEnum;
import com.alipay.antchain.bridge.commons.utils.crypto.HashAlgoEnum;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPublicKey;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class BCDNSTest {

    private static KeyPair keyPair;

    private static PrivateKey privateKey;

    private static final SignAlgoEnum SIG_ALGO = SignAlgoEnum.KECCAK256_WITH_SECP256K1;
    
    private static final HashAlgoEnum HASH_ALGO = HashAlgoEnum.KECCAK_256;

    private static final ObjectIdentityType oidType = ObjectIdentityType.X509_PUBLIC_KEY_INFO;

    @BeforeClass
    public static void setUp() throws Exception {
        new ObjectIdentity();

        keyPair = SIG_ALGO.getSigner().generateKeyPair();

        // dump the private key into pem
        StringWriter stringWriter = new StringWriter(256);
        JcaPEMWriter jcaPEMWriter = new JcaPEMWriter(stringWriter);
        jcaPEMWriter.writeObject(keyPair.getPrivate());
        jcaPEMWriter.close();
        String privatePem = stringWriter.toString();
        System.out.println(privatePem);
        FileUtil.writeBytes(privatePem.getBytes(), "cc_certs/private_key.pem");

        // dump the private key into pem
        stringWriter = new StringWriter(256);
        jcaPEMWriter = new JcaPEMWriter(stringWriter);
        jcaPEMWriter.writeObject(keyPair.getPublic());
        jcaPEMWriter.close();
        String pubkeyPem = stringWriter.toString();
        System.out.println(pubkeyPem);
        FileUtil.writeBytes(pubkeyPem.getBytes(), "cc_certs/public_key.pem");

        privateKey = SIG_ALGO.getSigner().readPemPrivateKey(privatePem.getBytes());

        Assert.assertNotNull(privateKey);
    }

    @Test
    public void testCrossChainCertificate() throws Exception {

        // construct a bcdns root cert

        AbstractCrossChainCertificate certificate = CrossChainCertificateFactory.createCrossChainCertificate(
                CrossChainCertificateV1.MY_VERSION,
                "test",
                generateOID(),
                DateUtil.currentSeconds(),
                DateUtil.offsetDay(new Date(), 365).getTime() / 1000,
                new BCDNSTrustRootCredentialSubject(
                        "bif",
                        generateOID(),
                        generateSubjectInfo()
                )
        );

        // this is how to sign something with private key
        byte[] signature = SIG_ALGO.getSigner().sign(privateKey, certificate.getEncodedToSign());

        // this is how to verify the signature
        Assert.assertTrue(SIG_ALGO.getSigner().verify(keyPair.getPublic(), certificate.getEncodedToSign(), signature));

        certificate.setProof(
                new AbstractCrossChainCertificate.IssueProof(
                        HASH_ALGO,
                        HASH_ALGO.hash(certificate.getEncodedToSign()),
                        SIG_ALGO,
                        signature
                )
        );
        System.out.println(CrossChainCertificateUtil.formatCrossChainCertificateToPem(certificate));
        FileUtil.writeBytes(CrossChainCertificateUtil.formatCrossChainCertificateToPem(certificate).getBytes(), "cc_certs/trust_root.crt");

        // construct a domain cert
        AbstractCrossChainCertificate domainCert = CrossChainCertificateFactory.createCrossChainCertificate(
                CrossChainCertificateV1.MY_VERSION,
                "testdomain",
                generateOID(),
                DateUtil.currentSeconds(),
                DateUtil.offsetDay(new Date(), 365).getTime() / 1000,
                new DomainNameCredentialSubject(
                        DomainNameCredentialSubject.CURRENT_VERSION,
                        DomainNameTypeEnum.DOMAIN_NAME,
                        new CrossChainDomain(".com"),
                        new CrossChainDomain("antchain.com"),
                        generateOID(),
                        generateSubjectInfo()
                )
        );

        domainCert.setProof(
                new AbstractCrossChainCertificate.IssueProof(
                        HASH_ALGO,
                        HASH_ALGO.hash(domainCert.getEncodedToSign()),
                        SIG_ALGO,
                        SIG_ALGO.getSigner().sign(privateKey, domainCert.getEncodedToSign())
                )
        );
        System.out.println(CrossChainCertificateUtil.formatCrossChainCertificateToPem(domainCert));
        FileUtil.writeBytes(CrossChainCertificateUtil.formatCrossChainCertificateToPem(domainCert).getBytes(), "cc_certs/antchain.com.crt");

        // construct a domain space cert
        AbstractCrossChainCertificate domainSpaceCert = CrossChainCertificateFactory.createCrossChainCertificate(
                CrossChainCertificateV1.MY_VERSION,
                ".com",
                generateOID(),
                DateUtil.currentSeconds(),
                DateUtil.offsetDay(new Date(), 365).getTime() / 1000,
                new DomainNameCredentialSubject(
                        DomainNameCredentialSubject.CURRENT_VERSION,
                        DomainNameTypeEnum.DOMAIN_NAME_SPACE,
                        new CrossChainDomain(CrossChainDomain.ROOT_DOMAIN_SPACE),
                        new CrossChainDomain(".com"),
                        generateOID(),
                        generateSubjectInfo()
                )
        );

        domainSpaceCert.setProof(
                new AbstractCrossChainCertificate.IssueProof(
                        HASH_ALGO,
                        HASH_ALGO.hash(domainSpaceCert.getEncodedToSign()),
                        SIG_ALGO,
                        SIG_ALGO.getSigner().sign(privateKey, domainSpaceCert.getEncodedToSign())
                )
        );
        System.out.println(CrossChainCertificateUtil.formatCrossChainCertificateToPem(domainSpaceCert));
        FileUtil.writeBytes(CrossChainCertificateUtil.formatCrossChainCertificateToPem(domainSpaceCert).getBytes(), "cc_certs/x.com.crt");

        // construct a relayer cert
        AbstractCrossChainCertificate relayerCert = CrossChainCertificateFactory.createCrossChainCertificate(
                CrossChainCertificateV1.MY_VERSION,
                "antchain-relayer",
                generateOID(),
                DateUtil.currentSeconds(),
                DateUtil.offsetDay(new Date(), 365).getTime() / 1000,
                new RelayerCredentialSubject(
                        RelayerCredentialSubject.CURRENT_VERSION,
                        "antchain-relayer",
                        generateOID(),
                        generateSubjectInfo()
                )
        );

        relayerCert.setProof(
                new AbstractCrossChainCertificate.IssueProof(
                        HASH_ALGO,
                        HASH_ALGO.hash(relayerCert.getEncodedToSign()),
                        SIG_ALGO,
                        SIG_ALGO.getSigner().sign(privateKey, relayerCert.getEncodedToSign())
                )
        );
        System.out.println(CrossChainCertificateUtil.formatCrossChainCertificateToPem(relayerCert));
        FileUtil.writeBytes(CrossChainCertificateUtil.formatCrossChainCertificateToPem(relayerCert).getBytes(), "cc_certs/relayer.crt");

        if (SIG_ALGO == SignAlgoEnum.SM3_WITH_SM2 || SIG_ALGO == SignAlgoEnum.ED25519) {
            Assert.assertEquals(StrUtil.equalsIgnoreCase(SIG_ALGO.getName(), "Ed25519") ? 32 : 65,
                    ((ICredentialSubjectWithSingleKey) relayerCert.getCredentialSubjectInstance()).getRawSubjectPublicKey().length);
        }
        Assert.assertTrue(
                StrUtil.endWith(
                        HexUtil.encodeHexStr(keyPair.getPublic().getEncoded()),
                        HexUtil.encodeHexStr(((ICredentialSubjectWithSingleKey) relayerCert.getCredentialSubjectInstance()).getRawSubjectPublicKey())
                )
        );

        // construct a relayer cert
        AbstractCrossChainCertificate ptcCert = CrossChainCertificateFactory.createCrossChainCertificate(
                CrossChainCertificateV1.MY_VERSION,
                "antchain-ptc",
                generateOID(),
                DateUtil.currentSeconds(),
                DateUtil.offsetDay(new Date(), 365).getTime() / 1000,
                new PTCCredentialSubject(
                        PTCCredentialSubject.CURRENT_VERSION,
                        "committee-ptc",
                        PTCTypeEnum.COMMITTEE,
                        generateOID(),
                        generateSubjectInfo()
                )
        );

        ptcCert.setProof(
                new AbstractCrossChainCertificate.IssueProof(
                        HASH_ALGO,
                        HASH_ALGO.hash(ptcCert.getEncodedToSign()),
                        SIG_ALGO,
                        SIG_ALGO.getSigner().sign(privateKey, ptcCert.getEncodedToSign())
                )
        );
        System.out.println(CrossChainCertificateUtil.formatCrossChainCertificateToPem(ptcCert));
        FileUtil.writeBytes(CrossChainCertificateUtil.formatCrossChainCertificateToPem(ptcCert).getBytes(), "cc_certs/ptc.crt");
    }

    @Test
    public void testMultipleCerts() throws Exception {

        // construct a domain cert
        AbstractCrossChainCertificate domainCert = CrossChainCertificateFactory.createCrossChainCertificate(
                CrossChainCertificateV1.MY_VERSION,
                "testdomain1",
                generateOID(),
                DateUtil.currentSeconds(),
                DateUtil.offsetDay(new Date(), 365).getTime() / 1000,
                new DomainNameCredentialSubject(
                        DomainNameCredentialSubject.CURRENT_VERSION,
                        DomainNameTypeEnum.DOMAIN_NAME,
                        new CrossChainDomain(".com"),
                        new CrossChainDomain("catchain.com"),
                        generateOID(),
                        generateSubjectInfo()
                )
        );

        domainCert.setProof(
                new AbstractCrossChainCertificate.IssueProof(
                        HASH_ALGO,
                        HASH_ALGO.hash(domainCert.getEncodedToSign()),
                        SIG_ALGO,
                        SIG_ALGO.getSigner().sign(privateKey, domainCert.getEncodedToSign())
                )
        );
        System.out.println(CrossChainCertificateUtil.formatCrossChainCertificateToPem(domainCert));
        FileUtil.writeBytes(CrossChainCertificateUtil.formatCrossChainCertificateToPem(domainCert).getBytes(), "cc_certs/catchain.com.crt");

        // construct a domain cert
        domainCert = CrossChainCertificateFactory.createCrossChainCertificate(
                CrossChainCertificateV1.MY_VERSION,
                "testdomain2",
                generateOID(),
                DateUtil.currentSeconds(),
                DateUtil.offsetDay(new Date(), 365).getTime() / 1000,
                new DomainNameCredentialSubject(
                        DomainNameCredentialSubject.CURRENT_VERSION,
                        DomainNameTypeEnum.DOMAIN_NAME,
                        new CrossChainDomain(".com"),
                        new CrossChainDomain("dogchain.com"),
                        generateOID(),
                        generateSubjectInfo()
                )
        );

        domainCert.setProof(
                new AbstractCrossChainCertificate.IssueProof(
                        HASH_ALGO,
                        HASH_ALGO.hash(domainCert.getEncodedToSign()),
                        SIG_ALGO,
                        SIG_ALGO.getSigner().sign(privateKey, domainCert.getEncodedToSign())
                )
        );
        System.out.println(CrossChainCertificateUtil.formatCrossChainCertificateToPem(domainCert));
        FileUtil.writeBytes(CrossChainCertificateUtil.formatCrossChainCertificateToPem(domainCert).getBytes(), "cc_certs/dogchain.com.crt");

        // construct a domain cert
        domainCert = CrossChainCertificateFactory.createCrossChainCertificate(
                CrossChainCertificateV1.MY_VERSION,
                "testdomain3",
                generateOID(),
                DateUtil.currentSeconds(),
                DateUtil.offsetDay(new Date(), 365).getTime() / 1000,
                new DomainNameCredentialSubject(
                        DomainNameCredentialSubject.CURRENT_VERSION,
                        DomainNameTypeEnum.DOMAIN_NAME,
                        new CrossChainDomain(".com"),
                        new CrossChainDomain("birdchain.com"),
                        generateOID(),
                        generateSubjectInfo()
                )
        );

        domainCert.setProof(
                new AbstractCrossChainCertificate.IssueProof(
                        HASH_ALGO,
                        HASH_ALGO.hash(domainCert.getEncodedToSign()),
                        SIG_ALGO,
                        SIG_ALGO.getSigner().sign(privateKey, domainCert.getEncodedToSign())
                )
        );
        System.out.println(CrossChainCertificateUtil.formatCrossChainCertificateToPem(domainCert));
        FileUtil.writeBytes(CrossChainCertificateUtil.formatCrossChainCertificateToPem(domainCert).getBytes(), "cc_certs/birdchain.com.crt");
    }

    private ObjectIdentity generateOID() {
        return oidType == ObjectIdentityType.BID ? getBidOID() : getX509OID();
    }

    private byte[] generateSubjectInfo() {
        if (oidType == ObjectIdentityType.BID) {
            FileUtil.writeString(JsonUtils.toJSONString(getBid()), "cc_certs/bid_document.json", Charset.defaultCharset());
        }
        return oidType == ObjectIdentityType.BID ? JsonUtils.toJSONString(getBid()).getBytes() : new byte[]{};
    }

    private ObjectIdentity getX509OID() {
        return new ObjectIdentity(ObjectIdentityType.X509_PUBLIC_KEY_INFO, keyPair.getPublic().getEncoded());
    }

    private BIDDocumentOperation getBid() {
        PublicKey publicKey = keyPair.getPublic();
        byte[] rawPublicKey;
        if (StrUtil.equalsIgnoreCase(publicKey.getAlgorithm(), "Ed25519")) {
            if (publicKey instanceof BCEdDSAPublicKey) {
                rawPublicKey = ((BCEdDSAPublicKey) publicKey).getPointEncoding();
            } else {
                throw new RuntimeException("your Ed25519 public key class not support: " + publicKey.getClass().getName());
            }
        } else if (StrUtil.equalsAnyIgnoreCase(publicKey.getAlgorithm(), "SM2", "EC")) {
            if (publicKey instanceof ECPublicKey) {
                rawPublicKey = ECKeyUtil.toPublicParams(publicKey).getQ().getEncoded(false);
            } else {
                throw new RuntimeException("your SM2/EC public key class not support: " + publicKey.getClass().getName());
            }
        } else {
            throw new RuntimeException(publicKey.getAlgorithm() + " not support");
        }

        byte[] rawPublicKeyWithSignals = new byte[rawPublicKey.length + 3];
        System.arraycopy(rawPublicKey, 0, rawPublicKeyWithSignals, 3, rawPublicKey.length);
        rawPublicKeyWithSignals[0] = -80;
        rawPublicKeyWithSignals[1] = StrUtil.equalsIgnoreCase(publicKey.getAlgorithm(), "Ed25519") ? KeyType.ED25519_VALUE : KeyType.SM2_VALUE;
        rawPublicKeyWithSignals[2] = 102;

        BIDpublicKeyOperation[] biDpublicKeyOperation = new BIDpublicKeyOperation[1];
        biDpublicKeyOperation[0] = new BIDpublicKeyOperation();
        biDpublicKeyOperation[0].setPublicKeyHex(HexUtil.encodeHexStr(rawPublicKeyWithSignals));
        biDpublicKeyOperation[0].setType(StrUtil.equalsIgnoreCase(publicKey.getAlgorithm(), "Ed25519") ? KeyType.ED25519 : KeyType.SM2);
        BIDDocumentOperation bidDocumentOperation = new BIDDocumentOperation();
        bidDocumentOperation.setPublicKey(biDpublicKeyOperation);

        return bidDocumentOperation;
    }

    private byte[] getRawPublicKey() {
        PublicKey publicKey = keyPair.getPublic();
        byte[] rawPublicKey;
        KeyType keyType = BIDHelper.getKeyTypeFromPublicKey(publicKey);
        System.out.println("key type is " + keyType);
        if (StrUtil.equalsIgnoreCase(publicKey.getAlgorithm(), "Ed25519")) {
            if (publicKey instanceof BCEdDSAPublicKey) {
                rawPublicKey = ((BCEdDSAPublicKey) publicKey).getPointEncoding();
            } else {
                throw new RuntimeException("your Ed25519 public key class not support: " + publicKey.getClass().getName());
            }
        } else if (StrUtil.equalsAnyIgnoreCase(publicKey.getAlgorithm(), "SM2", "EC")) {
            if (publicKey instanceof ECPublicKey) {
                rawPublicKey = ECKeyUtil.toPublicParams(publicKey).getQ().getEncoded(false);
            } else {
                throw new RuntimeException("your SM2/EC public key class not support: " + publicKey.getClass().getName());
            }
        } else {
            throw new RuntimeException(publicKey.getAlgorithm() + " not support");
        }

        return rawPublicKey;
    }

    private BIDInfoObjectIdentity getBidOID() {
        byte[] rawPublicKey = getRawPublicKey();
        return new BIDInfoObjectIdentity(BIDHelper.encAddress(getBid().getPublicKey()[0].getType(), rawPublicKey));
    }
}
