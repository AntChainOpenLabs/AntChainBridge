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

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.exception.AntChainBridgeCommonsException;
import com.alipay.antchain.bridge.commons.exception.CommonsErrorCodeEnum;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.annotation.TLVMapping;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

@Getter
@Setter
@FieldNameConstants
@TLVMapping(fieldName = CrossChainDomain.Fields.domain)
@NoArgsConstructor
public class CrossChainDomain {

    public static final int MAX_DOMAIN_LENGTH = 0x00000080;

    /**
     * We use the empty string as the root domain name space.
     * Nothing means everything.
     */
    public static String ROOT_DOMAIN_SPACE = "";

    public static boolean isDerivedFrom(String sub, String parent) {
        return StrUtil.endWith(sub, parent);
    }

    private String domain;

    public CrossChainDomain(String domain) {
        if (domain.getBytes(StandardCharsets.UTF_8).length >= MAX_DOMAIN_LENGTH) {
            throw new AntChainBridgeCommonsException(
                    CommonsErrorCodeEnum.RULES_CHECK_ERROR,
                    StrUtil.format("crosschain domain must less than {} bytes", MAX_DOMAIN_LENGTH)
            );
        }
        this.domain = domain;
    }

    public byte[] toBytes() {
        return this.domain.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isDomainSpace() {
        return StrUtil.equals(ROOT_DOMAIN_SPACE, this.domain) || StrUtil.startWith(this.domain, ".");
    }

    public void setDomain(String domain) {
        if (domain.getBytes(StandardCharsets.UTF_8).length >= MAX_DOMAIN_LENGTH) {
            throw new AntChainBridgeCommonsException(
                    CommonsErrorCodeEnum.RULES_CHECK_ERROR,
                    StrUtil.format("crosschain domain must less than {} bytes", MAX_DOMAIN_LENGTH)
            );
        }
        this.domain = domain;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CrossChainDomain) {
            return StrUtil.equals(this.domain, ((CrossChainDomain) obj).getDomain());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain);
    }

    @Override
    public String toString() {
        return domain;
    }
}
