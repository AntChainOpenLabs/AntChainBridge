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

package com.alipay.antchain.bridge.relayer.core.types.network.request;


import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class HelloStartRequest extends RelayerRequest {
    public static HelloStartRequest createFrom(RelayerRequest relayerRequest) {
        HelloStartRequest request = JSON.parseObject(relayerRequest.getRequestPayload(), HelloStartRequest.class);
        BeanUtil.copyProperties(relayerRequest, request);
        return request;
    }

    @JSONField
    private byte[] rand;

    @JSONField
    private String relayerNodeId;

    public HelloStartRequest(
            byte[] rand,
            String relayerNodeId
    ) {
        super(
                RelayerRequestType.HELLO_START
        );
        this.rand = rand;
        this.relayerNodeId = relayerNodeId;
        setRequestPayload(
                JSON.toJSONBytes(this)
        );
    }
}
