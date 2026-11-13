package com.alipay.antchain.bridge.plugins.ethereum3.core;

import com.alibaba.fastjson.JSONObject;
import lombok.*;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EthConsensusEndorsements {

    public static EthConsensusEndorsements fromJson(String json, int syncCommitteeSize) {
        try {
            JSONObject jsonObject = JSONObject.parseObject(json);
            return new EthConsensusEndorsements(
                    JsonUtil.parse(jsonObject.getString("sync_aggregate"), SyncAggregateSchema.create(syncCommitteeSize).getJsonTypeDefinition())
            );
        } catch (Exception e) {
            throw new RuntimeException("failed to parse EthConsensusEndorsements from json: ", e);
        }
    }

    private SyncAggregate syncAggregate;

    @SneakyThrows
    public String toJson() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("sync_aggregate", JsonUtil.serialize(syncAggregate, syncAggregate.getSchema().getJsonTypeDefinition()));
        return jsonObject.toJSONString();
    }
}
