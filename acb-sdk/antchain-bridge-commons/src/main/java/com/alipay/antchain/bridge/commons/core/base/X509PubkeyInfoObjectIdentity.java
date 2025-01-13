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

package com.alipay.antchain.bridge.commons.core.base;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.ECKeyUtil;
import cn.hutool.crypto.KeyUtil;
import cn.hutool.jwt.signers.AlgorithmUtil;
import com.alipay.antchain.bridge.commons.exception.AntChainBridgeCommonsException;
import com.alipay.antchain.bridge.commons.exception.CommonsErrorCodeEnum;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPublicKey;

public class X509PubkeyInfoObjectIdentity extends ObjectIdentity implements IObjectIdentityWithPublicKey {

    private SubjectPublicKeyInfo subjectPublicKeyInfo;

    public X509PubkeyInfoObjectIdentity(byte[] rawSubjectPubkeyInfo) {
        super(ObjectIdentityType.X509_PUBLIC_KEY_INFO, rawSubjectPubkeyInfo);
    }

    public X509PubkeyInfoObjectIdentity(ObjectIdentity objectIdentity) {
        super(objectIdentity.getType(), objectIdentity.getRawId());
    }

    public SubjectPublicKeyInfo getSubjectPublicKeyInfo() {
        if (ObjectUtil.isNull(subjectPublicKeyInfo)) {
            this.subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(this.getRawId());
        }
        return subjectPublicKeyInfo;
    }

    public PublicKey getPublicKey() {
        try {
            return KeyUtil.generatePublicKey(
                    AlgorithmUtil.getAlgorithm(getSubjectPublicKeyInfo().getAlgorithm().getAlgorithm().getId()),
                    getSubjectPublicKeyInfo().getEncoded()
            );
        } catch (Exception e) {
            throw new AntChainBridgeCommonsException(
                    CommonsErrorCodeEnum.BCDNS_OID_X509_PUBLIC_KEY_INFO_ERROR,
                    "failed to get public key from subject pubkey info",
                    e
            );
        }
    }

    public byte[] getRawPublicKey() {
        PublicKey publicKey = getPublicKey();
        if (StrUtil.equalsIgnoreCase(publicKey.getAlgorithm(), "Ed25519")) {
            if (publicKey instanceof BCEdDSAPublicKey) {
                return ((BCEdDSAPublicKey) publicKey).getPointEncoding();
            }
            throw new RuntimeException("your Ed25519 public key class not support: " + publicKey.getClass().getName());
        } else if (StrUtil.equalsAnyIgnoreCase(publicKey.getAlgorithm(), "SM2", "EC")) {
            if (publicKey instanceof ECPublicKey) {
                return ECKeyUtil.toPublicParams(publicKey).getQ().getEncoded(false);
            }
            throw new RuntimeException("your SM2/EC public key class not support: " + publicKey.getClass().getName());
        } else if (StrUtil.equalsIgnoreCase(publicKey.getAlgorithm(), "ECDSA")) {
            if (!(publicKey instanceof ECPublicKey)) {
                throw new RuntimeException("Unsupported public key type for ECDSA: " + publicKey.getClass().getName());
            }
            BigInteger x = ((ECPublicKey) publicKey).getW().getAffineX();
            BigInteger y = ((ECPublicKey) publicKey).getW().getAffineY();
            BigInteger pubkey = x.multiply(BigInteger.valueOf(16).pow(64)).add(y);
            return pubkey.toByteArray();
        }
        throw new RuntimeException(
                StrUtil.format("your public key algo {} don't support this function for now", publicKey.getAlgorithm())
        );
    }
}
