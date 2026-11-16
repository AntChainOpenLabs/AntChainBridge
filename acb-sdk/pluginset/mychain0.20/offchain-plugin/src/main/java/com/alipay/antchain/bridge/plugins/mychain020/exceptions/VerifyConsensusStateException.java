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
public class VerifyConsensusStateException extends MycPluginException {

    public VerifyConsensusStateException(BigInteger height, String hashHex, String message) {
        super(StrUtil.format("height: {}, hash: {}, message: {}", height.toString(), hashHex, message));
        this.height = height;
        this.hashHex = hashHex;
    }

    private final BigInteger height;
    private final String hashHex;
}
