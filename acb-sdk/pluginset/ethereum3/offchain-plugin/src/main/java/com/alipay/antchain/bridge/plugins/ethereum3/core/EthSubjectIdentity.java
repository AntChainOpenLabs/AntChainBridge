package com.alipay.antchain.bridge.plugins.ethereum3.core;

import java.io.IOException;
import java.lang.reflect.Type;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec.DummySpecConfig;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec.Eth2ChainConfig;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec.Eth2ChainConfigDeserializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EthSubjectIdentity {

    public static EthSubjectIdentity fromJson(String json) {
        var subjectId = JSON.parseObject(json, EthSubjectIdentity.class);
        if (subjectId.getCurrentSyncCommittee().getPubkeys().size() != subjectId.getEth2ChainConfig().getSyncCommitteeSize()) {
            throw new RuntimeException("sync committee size not match with chain config");
        }
        return subjectId;
    }

    public static class SyncCommitteeDeserializer implements ObjectDeserializer {
        @Override
        public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
            var value = parser.parseObject(String.class);
            if (StrUtil.isEmpty(value) || StrUtil.equals(value, "{}")) {
                return null;
            }
            var jsonObject = JSONObject.parseObject(value);
            var config = new DummySpecConfig();
            config.setSyncCommitteeSize(jsonObject.getJSONArray("pubkeys").size());
            try {
                return (T) JsonUtil.parse(value, new SyncCommittee.SyncCommitteeSchema(config).getJsonTypeDefinition());
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int getFastMatchToken() {
            return JSONToken.LITERAL_STRING;
        }
    }

    public static class SyncCommitteeSerializer implements ObjectSerializer {
        @Override
        public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
            var syncCommittee = (SyncCommittee) object;
            serializer.write(
                    JsonUtil.serialize(syncCommittee, ((SyncCommittee.SyncCommitteeSchema) syncCommittee.getSchema()).getJsonTypeDefinition())
            );
        }
    }

    @JSONField(name = "current_sync_committee", deserializeUsing = SyncCommitteeDeserializer.class, serializeUsing = SyncCommitteeSerializer.class)
    private SyncCommittee currentSyncCommittee;

    @JSONField(name = "eth2_chain_config", deserializeUsing = Eth2ChainConfigDeserializer.class)
    private Eth2ChainConfig eth2ChainConfig;

    public String toJson() {
        return JSON.toJSONString(this);
    }
}
