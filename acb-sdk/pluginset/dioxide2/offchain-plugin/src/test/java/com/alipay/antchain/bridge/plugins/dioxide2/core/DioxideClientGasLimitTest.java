package com.alipay.antchain.bridge.plugins.dioxide2.core;

import java.util.LinkedHashMap;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class DioxideClientGasLimitTest {

    @Test
    public void largePtcWritesUseHighGasLimit() {
        for (String function : List.of("updatePTCTrustRoot", "updatePTCVerifyAnchor", "addTpBta")) {
            Assert.assertEquals(
                    Integer.valueOf(DioxideClient.HIGH_GAS_LIMIT),
                    DioxideClient.gasLimitForContractFunction(function)
            );
            Assert.assertEquals(
                    DioxideClient.HIGH_GAS_LIMIT,
                    DioxideClient.contractInvocationRequest(
                            "test-account",
                            "ict108",
                            "PtcHub",
                            function,
                            new LinkedHashMap<>()
                    ).get("gaslimit")
            );
        }
    }

    @Test
    public void ordinaryContractCallsKeepNodeDefaultGasLimit() {
        Assert.assertNull(DioxideClient.gasLimitForContractFunction("setPtcHub"));
        Assert.assertFalse(
                DioxideClient.contractInvocationRequest(
                        "test-account",
                        "ict108",
                        "AuthMsg",
                        "setPtcHub",
                        new LinkedHashMap<>()
                ).containsKey("gaslimit")
        );
    }
}
