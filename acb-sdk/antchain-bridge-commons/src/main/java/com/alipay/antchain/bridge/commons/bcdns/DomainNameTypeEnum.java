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

import com.alipay.antchain.bridge.commons.utils.codec.tlv.annotation.TLVCreator;

public enum DomainNameTypeEnum {

    DOMAIN_NAME,

    DOMAIN_NAME_SPACE;

    @TLVCreator
    public static DomainNameTypeEnum valueOf(Byte value) {
        switch (value) {
            case 0:
                return DOMAIN_NAME;
            case 1:
                return DOMAIN_NAME_SPACE;
            default:
                return null;
        }
    }
}
