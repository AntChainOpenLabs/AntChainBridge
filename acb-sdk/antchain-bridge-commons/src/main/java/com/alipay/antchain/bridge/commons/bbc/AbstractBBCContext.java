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

package com.alipay.antchain.bridge.commons.bbc;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alipay.antchain.bridge.commons.bbc.syscontract.AuthMessageContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.MonitorContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.SDPContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.PTCContract;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class AbstractBBCContext implements IBBCContext {

    @JSONField(name = "am_contract")
    private AuthMessageContract authMessageContract;

    @JSONField(name = "ptc_contract")
    private PTCContract ptcContract;

    @JSONField(name = "sdp_contract")
    private SDPContract sdpContract;

    @JSONField(name = "monitor_contract")
    private MonitorContract monitorContract;

    @JSONField(name = "is_reliable")
    private boolean isReliable;

    @JSONField(name = "raw_conf")
    private byte[] confForBlockchainClient;

    @Override
    public void decodeFromBytes(byte[] raw) {
        AbstractBBCContext state = JSON.parseObject(raw, this.getClass());
        this.setSdpContract(state.getSdpContract());
        this.setMonitorContract(state.getMonitorContract());
        this.setPtcContract(state.getPtcContract());
        this.setAuthMessageContract(state.getAuthMessageContract());
        this.setReliable(state.isReliable());
        this.setConfForBlockchainClient(state.getConfForBlockchainClient());
    }

    @Override
    public byte[] encodeToBytes() {
        return JSON.toJSONBytes(this);
    }
}
