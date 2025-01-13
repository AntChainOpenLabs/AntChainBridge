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

package com.alipay.antchain.bridge.plugins.eos;

import java.io.IOException;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.Getter;
import lombok.Setter;

/**
 * Eos's configuration information
 * - Url for Eos node rpc
 * - Private key
 */
@Getter
@Setter
public class EosConfig {

    /**
     * 从json字符串反序列化
     *
     * @param jsonString raw json
     */
    public static EosConfig fromJsonString(String jsonString) throws IOException {
        return JSON.parseObject(jsonString, EosConfig.class);
    }

    @JSONField
    private String url;

    /**
     * The account name assigned by the blockchain to the relayer
     */
    @JSONField
    private String userName;

    /**
     * The private key hold by the relayer signing the transactions
     */
    @JSONField
    private String userPriKey;

    /**
     * The AuthMessage contract name has been deployed before initialize the BBC
     */
    @JSONField
    private String amContractAddressDeployed;

    /**
     * The SDP contract name has been deployed before initialize the BBC
     */
    @JSONField
    private String sdpContractAddressDeployed;

    /**
     * Wait until your tx is irreversible
     */
    @JSONField
    private boolean waitUtilTxIrreversible = false;

    /**
     * If the query count reaches {@code maxIrreversibleWaitCount}, break the loop.
     */
    @JSONField
    private int maxIrreversibleWaitCount = 30;

    /**
     * How many milliseconds for waiting once.
     */
    @JSONField
    private int waitTimeOnce = 500;

    /**
     * json序列化为字符串
     */
    public String toJsonString() {
        return JSON.toJSONString(this);
    }
}
