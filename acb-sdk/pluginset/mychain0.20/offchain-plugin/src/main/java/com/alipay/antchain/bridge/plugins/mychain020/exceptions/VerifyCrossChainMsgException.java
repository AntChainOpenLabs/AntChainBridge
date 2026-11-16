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

package com.alipay.antchain.bridge.plugins.mychain020.exceptions;

import java.math.BigInteger;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;

@Getter
public class VerifyCrossChainMsgException extends MycPluginException {

    public VerifyCrossChainMsgException(BigInteger height, int receiptIndex, int logIndex, String message) {
        super(StrUtil.format("height: {}, receiptIndex: {}, logIndex: {}, message: {}",
                height.toString(), receiptIndex, logIndex, message));
        this.height = height;
        this.receiptIndex = receiptIndex;
        this.logIndex = logIndex;
    }

    public VerifyCrossChainMsgException(BigInteger height, int receiptIndex, int logIndex, String message, Throwable t) {
        super(StrUtil.format("height: {}, receiptIndex: {}, logIndex: {}, message: {}",
                height.toString(), receiptIndex, logIndex, message), t);
        this.height = height;
        this.receiptIndex = receiptIndex;
        this.logIndex = logIndex;
    }

    private final BigInteger height;
    private final int receiptIndex;
    private final int logIndex;
}
