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

import cn.hutool.core.util.StrUtil;
import lombok.Getter;

@Getter
public class CallContractException extends MycPluginException {

    private final String contractNameOrId;

    private final String txhash;

    public CallContractException(String contractNameOrId, String txhash, String strFormat, Object... args) {
        super(StrUtil.concat(true, "Call contract ", contractNameOrId, " failed with tx ", txhash, ": ", StrUtil.format(strFormat, args)));
        this.contractNameOrId = contractNameOrId;
        this.txhash = txhash;
    }

    public CallContractException(String contractNameOrId, String txhash, String msg) {
        super(StrUtil.concat(true, "Call contract ", contractNameOrId, " failed with tx ", txhash, ": ", msg));
        this.contractNameOrId = contractNameOrId;
        this.txhash = txhash;
    }
}
