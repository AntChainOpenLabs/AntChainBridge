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

public class CommitteeNodeInternalException extends CommitteeNodeException {

    public CommitteeNodeInternalException(String message) {
        super(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR, message);
    }

    public CommitteeNodeInternalException(String format, Object... args) {
        super(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR, StrUtil.format(format, args));
    }

    public CommitteeNodeInternalException(Throwable t, String format, Object... args) {
        super(CommitteeNodeErrorCodeEnum.UNKNOWN_INTERNAL_ERROR, StrUtil.format(format, args), t);
    }
}
