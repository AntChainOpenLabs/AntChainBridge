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

package com.alipay.antchain.bridge.commons.bcdns;

import java.security.PublicKey;
import java.security.Security;

import com.alipay.antchain.bridge.commons.bcdns.utils.ObjectIdentityUtil;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.TLVTypeEnum;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.TLVUtils;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.annotation.TLVField;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BCDNSTrustRootCredentialSubject extends AbstractCredentialSubjectWithSingleKey {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static final short TLV_TYPE_NAME = 0x0000;

    public static final short TLV_TYPE_BCDNS_ROOT_OWNER = 0x0001;

    public static final short TLV_TYPE_SUBJECT_INFO = 0x0002;

    public static BCDNSTrustRootCredentialSubject decode(byte[] rawData) {
        return TLVUtils.decode(rawData, BCDNSTrustRootCredentialSubject.class);
    }

    @TLVField(tag = TLV_TYPE_NAME, type = TLVTypeEnum.STRING)
    private String name;

    @TLVField(tag = TLV_TYPE_BCDNS_ROOT_OWNER, type = TLVTypeEnum.BYTES, order = 1)
    private ObjectIdentity bcdnsRootOwner;

    @TLVField(tag = TLV_TYPE_SUBJECT_INFO, type = TLVTypeEnum.BYTES, order = 2)
    private byte[] bcdnsRootSubjectInfo;

    @Override
    public byte[] encode() {
        return TLVUtils.encode(this);
    }

    // TODO: Q: 超级节点给 issuer签发的证书；
    @Override
    public PublicKey getSubjectPublicKey() {
        return ObjectIdentityUtil.getPublicKeyFromSubject(bcdnsRootOwner, bcdnsRootSubjectInfo);
    }

    @Override
    public byte[] getRawSubjectPublicKey() {
        return ObjectIdentityUtil.getRawPublicKeyFromSubject(bcdnsRootOwner, bcdnsRootSubjectInfo);
    }

    @Override
    public ObjectIdentity getApplicant() {
        return bcdnsRootOwner;
    }

    @Override
    public byte[] getSubject() {
        return bcdnsRootSubjectInfo;
    }
}
