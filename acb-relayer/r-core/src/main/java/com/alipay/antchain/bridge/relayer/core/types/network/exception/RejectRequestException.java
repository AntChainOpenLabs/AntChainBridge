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

package com.alipay.antchain.bridge.relayer.core.types.network.exception;

import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.relayer.commons.exception.AntChainBridgeRelayerException;
import com.alipay.antchain.bridge.relayer.commons.exception.RelayerErrorCodeEnum;
import lombok.Getter;

@Getter
public class RejectRequestException extends AntChainBridgeRelayerException {

    private final int errorCode;

    private final String errorMsg;

    public RejectRequestException(int errorCode, String errorMsg) {
        super(
                RelayerErrorCodeEnum.SERVER_REQUEST_FROM_RELAYER_REJECT,
                "request reject: " + errorMsg
        );
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }

    public RejectRequestException(String message, int errorCode, String errorMsg) {
        super(
                RelayerErrorCodeEnum.SERVER_REQUEST_FROM_RELAYER_REJECT,
                StrUtil.format("request reject: {} - {}", errorMsg, message)
        );
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }
}
