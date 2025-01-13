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

package com.alipay.antchain.bridge.commons.exception;

import cn.hutool.core.util.StrUtil;

public class IllegalCrossChainCertException extends AntChainBridgeCommonsException {
    public IllegalCrossChainCertException(String longMsg) {
        super(CommonsErrorCodeEnum.ILLEGAL_CROSSCHAIN_CERT, longMsg);
    }

    public IllegalCrossChainCertException(String format, Object... args) {
        super(CommonsErrorCodeEnum.ILLEGAL_CROSSCHAIN_CERT, StrUtil.format(format, args));
    }
}
