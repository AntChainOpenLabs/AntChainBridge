package com.alipay.antchain.bridge.ptc.types;

import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyProof;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PTCVerifyCrossChainMessageResult {

    private final ThirdPartyProof thirdPartyProof;

    private final String regulationStatus;

    private final String regulationReason;
}
