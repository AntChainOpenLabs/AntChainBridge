package com.alipay.antchain.bridge.plugins.ethereum3.core.eth;

import lombok.SneakyThrows;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeader;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateSchema;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;

public record LightClientUpdateWrapper(LightClientUpdate lightClientUpdate) {

    public LightClientHeader getAttestedHeader() {
        return (LightClientHeader) lightClientUpdate.get(0);
    }

    public SyncCommittee getNextSyncCommittee() {
        return (SyncCommittee) lightClientUpdate.get(1);
    }

    public SszBytes32Vector getNextSyncCommitteeBranch() {
        return (SszBytes32Vector) lightClientUpdate.get(2);
    }

    public SyncAggregate getSyncAggregate() {
        return (SyncAggregate) lightClientUpdate.get(5);
    }

    public UInt64 getSignatureSlot() {
        return ((SszUInt64) lightClientUpdate.get(6)).get();
    }

    @SneakyThrows
    public String toJson() {
        return JsonUtil.serialize(lightClientUpdate, ((LightClientUpdateSchema) lightClientUpdate.getSchema()).getJsonTypeDefinition());
    }
}
