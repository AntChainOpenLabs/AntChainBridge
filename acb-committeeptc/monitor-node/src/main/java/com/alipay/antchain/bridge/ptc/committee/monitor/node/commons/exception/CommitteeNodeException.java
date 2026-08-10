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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.exception;

import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.exception.base.AntChainBridgeBaseException;

public class CommitteeNodeException extends AntChainBridgeBaseException {

    public CommitteeNodeException(CommitteeNodeErrorCodeEnum errorCode, String longMsg) {
        super(errorCode.getErrorCode(), errorCode.getShortMsg(), longMsg);
    }

    public CommitteeNodeException(CommitteeNodeErrorCodeEnum errorCode, String formatStr, Object... objects) {
        super(errorCode.getErrorCode(), errorCode.getShortMsg(), StrUtil.format(formatStr, objects));
    }

    public CommitteeNodeException(CommitteeNodeErrorCodeEnum errorCode, Throwable throwable, String formatStr, Object... objects) {
        super(errorCode.getErrorCode(), errorCode.getShortMsg(), StrUtil.format(formatStr, objects), throwable);
    }

    public CommitteeNodeException(CommitteeNodeErrorCodeEnum errorCode, String longMsg, Throwable throwable) {
        super(errorCode.getErrorCode(), errorCode.getShortMsg(), longMsg, throwable);
    }

    public int getCodeNum() {
        return Integer.parseInt(this.getCode(), 16);
    }
}
