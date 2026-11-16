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

package com.alipay.antchain.bridge.plugins.mychain020.model;

import java.math.BigInteger;
import java.util.List;

import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.TLVTypeEnum;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.TLVUtils;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.annotation.TLVField;
import com.alipay.antchain.bridge.plugins.mychain020.crypto.CryptoSuiteEnum;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MychainSubjectIdentity {
    private static final short TLV_TYPE_POA_NUM = 0x0000;
    private static final short TLV_TYPE_POA_CERTS_PUB_KEY_HASH = 0x0001;
    private static final short TLV_TYPE_PROD_VERSION = 0x0002;
    private static final short TLV_TYPE_BLOCK_NUM_BIGINTEGER = 0x0008;
    private static final short TLV_TYPE_BLOCK_HASH = 0x0004;
    private static final short TLV_TYPE_CRYPTO_SUITE = 0x0007;

    public static MychainSubjectIdentity decode(byte[] data) {
        return TLVUtils.decode(data, MychainSubjectIdentity.class);
    }

    public static MychainSubjectIdentity decodeFromJson(byte[] data) {
        return JSON.parseObject(data, MychainSubjectIdentity.class);
    }

    @TLVField(tag = TLV_TYPE_POA_CERTS_PUB_KEY_HASH, type = TLVTypeEnum.BYTES_ARRAY, order = TLV_TYPE_POA_CERTS_PUB_KEY_HASH)
    private List<byte[]> poaCertsPubKeyHash;

    @TLVField(tag = TLV_TYPE_BLOCK_NUM_BIGINTEGER, type = TLVTypeEnum.VAR_INT, order = TLV_TYPE_BLOCK_NUM_BIGINTEGER)
    private BigInteger blockHeight;

    @TLVField(tag = TLV_TYPE_BLOCK_HASH, type = TLVTypeEnum.BYTES, order = TLV_TYPE_BLOCK_HASH)
    private byte[] blockHash;

    @TLVField(tag = TLV_TYPE_CRYPTO_SUITE, type = TLVTypeEnum.UINT32, order = TLV_TYPE_CRYPTO_SUITE)
    private CryptoSuiteEnum cryptoSuite;

    public byte[] encode() {
        return TLVUtils.encode(this);
    }
}
