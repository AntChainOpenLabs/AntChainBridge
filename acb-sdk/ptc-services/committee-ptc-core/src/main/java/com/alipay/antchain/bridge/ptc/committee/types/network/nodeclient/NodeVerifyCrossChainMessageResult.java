package com.alipay.antchain.bridge.ptc.committee.types.network.nodeclient;

import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NodeVerifyCrossChainMessageResult {

    private final CommitteeNodeProof nodeProof;

    private final String regulationStatus;

    private final String regulationReason;
}
