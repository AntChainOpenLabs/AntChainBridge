package com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models;

import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MonitorNodeVerifyResult {

    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_ERROR = "error";

    private final CommitteeNodeProof nodeProof;
    private final String regulationStatus;
    private final String regulationReason;

    public static MonitorNodeVerifyResult approved(CommitteeNodeProof proof) {
        return new MonitorNodeVerifyResult(proof, STATUS_APPROVED, "");
    }

    public static MonitorNodeVerifyResult rejected(CommitteeNodeProof proof, String reason) {
        return new MonitorNodeVerifyResult(proof, STATUS_REJECTED, reason);
    }

    public static MonitorNodeVerifyResult error(CommitteeNodeProof proof, String reason) {
        return new MonitorNodeVerifyResult(proof, STATUS_ERROR, reason);
    }
}
