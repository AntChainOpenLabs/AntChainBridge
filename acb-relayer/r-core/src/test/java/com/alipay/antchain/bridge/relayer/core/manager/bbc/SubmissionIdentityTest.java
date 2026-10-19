package com.alipay.antchain.bridge.relayer.core.manager.bbc;

import org.junit.Test;
import static org.junit.Assert.*;

public class SubmissionIdentityTest {
    @Test public void identityUsesUcpAndTargetNotPayloadOrZeroMessageId() {
        String first = AMClientContractHeteroBlockchainImpl.stableSubmissionId("ucp-a", "diox04");
        assertEquals(first, AMClientContractHeteroBlockchainImpl.stableSubmissionId("ucp-a", "diox04"));
        assertNotEquals(first, AMClientContractHeteroBlockchainImpl.stableSubmissionId("ucp-b", "diox04"));
        assertNotEquals(first, AMClientContractHeteroBlockchainImpl.stableSubmissionId("ucp-a", "diox11"));
        assertEquals(64, first.length());
    }
}
