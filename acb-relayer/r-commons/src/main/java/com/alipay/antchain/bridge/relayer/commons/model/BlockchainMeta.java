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

package com.alipay.antchain.bridge.relayer.commons.model;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.relayer.commons.constant.BlockchainStateEnum;
import com.alipay.antchain.bridge.relayer.commons.constant.Constants;
import com.alipay.antchain.bridge.relayer.commons.constant.OnChainServiceStatusEnum;
import com.alipay.antchain.bridge.relayer.commons.utils.HeteroBBCContextDeserializer;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BlockchainMeta {

    public static String createMetaKey(String product, String blockchainId) {
        return product + "_" + blockchainId;
    }

    @Getter
    @Setter
    public static class BlockchainProperties {

        public static BlockchainProperties decode(byte[] rawData) {
            JSONObject jsonObject = JSON.parseObject(new String(rawData));
            BlockchainProperties properties = jsonObject.toJavaObject(BlockchainProperties.class);
            if (ObjectUtil.isNull(properties)) {
                return null;
            }
            jsonObject.keySet().forEach(
                    key -> {
                        if (jsonFieldNameSet.contains(key)) {
                            return;
                        }
                        Object val = jsonObject.get(key);
                        if (val instanceof String) {
                            properties.getExtraProperties().put(key, (String) val);
                        }
                    }
            );
            return properties;
        }

        private static final Set<String> jsonFieldNameSet = CollectionUtil.newHashSet(
                "am_client_contract_address",
                "sdp_msg_contract_address",
                "ptc_contract_address",
                "monitor_contract_address",
                "anchor_runtime_status",
                "init_block_height",
                "is_domain_registered",
                Constants.HETEROGENEOUS_BBC_CONTEXT,
                "plugin_server_id",
                Constants.AM_SERVICE_STATUS,
                "extra_properties"
        );

        @JSONField(name = "am_client_contract_address")
        private String amClientContractAddress;

        @JSONField(name = "sdp_msg_contract_address")
        private String sdpMsgContractAddress;

        @JSONField(name = "ptc_contract_address")
        private String ptcContractAddress;

        @JSONField(name = "monitor_contract_address")
        private String monitorContractAddress;

        @JSONField(name = "anchor_runtime_status")
        private BlockchainStateEnum anchorRuntimeStatus;

        @JSONField(name = "init_block_height")
        private Long initBlockHeight;

        @JSONField(name = "is_domain_registered")
        private Boolean isDomainRegistered;

        @JSONField(name = Constants.HETEROGENEOUS_BBC_CONTEXT, deserializeUsing = HeteroBBCContextDeserializer.class)
        private DefaultBBCContext bbcContext;

        @JSONField(name = "plugin_server_id")
        private String pluginServerId;

        @JSONField(name = Constants.AM_SERVICE_STATUS)
        private OnChainServiceStatusEnum amServiceStatus;

        @JSONField(name = "extra_properties")
        private Map<String, String> extraProperties = MapUtil.newHashMap();

        public byte[] encode() {
            return JSON.toJSONBytes(this);
        }
    }

    private String product;

    private String blockchainId;

    private String alias;

    private String desc;

    private BlockchainProperties properties;

    public BlockchainMeta(
            String product,
            String blockchainId,
            String alias,
            String desc,
            byte[] rawProperties
    ) {
        this(product, blockchainId, alias, desc, BlockchainProperties.decode(rawProperties));
    }

    public BlockchainMeta(
            String product,
            String blockchainId,
            String alias,
            String desc,
            BlockchainProperties properties
    ) {
        this.product = product;
        this.blockchainId = blockchainId;
        this.alias = alias;
        this.desc = desc;
        this.properties = properties;
    }

    public String getMetaKey() {
        return createMetaKey(this.product, this.blockchainId);
    }

    public String getPluginServerId() {
        return properties.getPluginServerId();
    }

    public void updateProperties(BlockchainProperties properties) {
        if (StrUtil.isNotEmpty(properties.getAmClientContractAddress())) {
            this.properties.setAmClientContractAddress(properties.getAmClientContractAddress());
        }
        if (StrUtil.isNotEmpty(properties.getSdpMsgContractAddress())) {
            this.properties.setSdpMsgContractAddress(properties.getSdpMsgContractAddress());
        }
        if (StrUtil.isNotEmpty(properties.getPtcContractAddress())) {
            this.properties.setPtcContractAddress(properties.getPtcContractAddress());
        }
        if (StrUtil.isNotEmpty(properties.getMonitorContractAddress())) {
            this.properties.setMonitorContractAddress(properties.getMonitorContractAddress());
        }
        if (ObjectUtil.isNotNull(properties.getAnchorRuntimeStatus())) {
            this.properties.setAnchorRuntimeStatus(properties.getAnchorRuntimeStatus());
        }
        if (ObjectUtil.isNotNull(properties.getInitBlockHeight())) {
            this.properties.setInitBlockHeight(properties.getInitBlockHeight());
        }
        if (ObjectUtil.isNotNull(properties.getIsDomainRegistered())) {
            this.properties.setIsDomainRegistered(properties.getIsDomainRegistered());
        }
        if (ObjectUtil.isNotNull(properties.getBbcContext())) {
            this.properties.setBbcContext(properties.getBbcContext());
        }
        if (StrUtil.isNotEmpty(properties.getPluginServerId())) {
            this.properties.setPluginServerId(properties.getPluginServerId());
        }
        if (ObjectUtil.isNotNull(properties.getAmServiceStatus())) {
            this.properties.setAmServiceStatus(properties.getAmServiceStatus());
        }
        if (ObjectUtil.isNotEmpty(properties.getExtraProperties())) {
            this.properties.getExtraProperties().putAll(properties.getExtraProperties());
        }
    }

    public void updateProperty(String key, String value) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(key, value);
        BlockchainProperties properties = Objects.requireNonNull(
                BlockchainProperties.decode(JSON.toJSONBytes(jsonObject))
        );
        updateProperties(properties);
    }

    public boolean isRunning() {
        return this.properties.getAnchorRuntimeStatus() == BlockchainStateEnum.RUNNING;
    }
}
