package com.alipay.antchain.bridge.relayer.core.service.anchor.tasks;

import org.junit.Assert;
import org.junit.Test;

public class BlockPollingTaskTest {

    @Test
    public void acceptsEqualOrIncreasingRemoteHeight() {
        BlockPollingTask.ensureNoRemoteHeightRollback(100L, 100L, "dioxide_diox11.id");
        BlockPollingTask.ensureNoRemoteHeightRollback(100L, 101L, "dioxide_diox11.id");
    }

    @Test
    public void rejectsRemoteHeightRollbackWithRecoveryContext() {
        try {
            BlockPollingTask.ensureNoRemoteHeightRollback(
                    9_936_510L,
                    4_086_996L,
                    "dioxide2_diox04.id"
            );
            Assert.fail("rollback must be rejected");
        } catch (IllegalStateException error) {
            Assert.assertTrue(error.getMessage().contains("height rollback detected"));
            Assert.assertTrue(error.getMessage().contains("dioxide2_diox04.id"));
            Assert.assertTrue(error.getMessage().contains("recorded=9936510"));
            Assert.assertTrue(error.getMessage().contains("remote=4086996"));
            Assert.assertTrue(error.getMessage().contains("reconcile polling/sync/notify cursors"));
        }
    }
}
