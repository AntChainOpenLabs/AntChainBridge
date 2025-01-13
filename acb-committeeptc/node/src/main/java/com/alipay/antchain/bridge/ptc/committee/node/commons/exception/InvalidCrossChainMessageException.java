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

package com.alipay.antchain.bridge.ptc.committee.node.commons.exception;

import cn.hutool.core.util.StrUtil;

public class InvalidCrossChainMessageException extends CommitteeNodeException {

    public InvalidCrossChainMessageException(String longMsg) {
        super(CommitteeNodeErrorCodeEnum.SERVER_VERIFY_CROSSCHAIN_MESSAGE_ERROR, longMsg);
    }

    public InvalidCrossChainMessageException(String formatStr, Object... objects) {
        super(CommitteeNodeErrorCodeEnum.SERVER_VERIFY_CROSSCHAIN_MESSAGE_ERROR, StrUtil.format(formatStr, objects));
    }

    public InvalidCrossChainMessageException(Throwable throwable, String formatStr, Object... objects) {
        super(CommitteeNodeErrorCodeEnum.SERVER_VERIFY_CROSSCHAIN_MESSAGE_ERROR, StrUtil.format(formatStr, objects), throwable);
    }

    public InvalidCrossChainMessageException(String longMsg, Throwable throwable) {
        super(CommitteeNodeErrorCodeEnum.SERVER_VERIFY_CROSSCHAIN_MESSAGE_ERROR, longMsg, throwable);
    }
}
