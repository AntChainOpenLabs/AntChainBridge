/*
 * Copyright 2024 Ant Group
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

package com.alipay.antchain.bridge.relayer.core.service.confirm;

import java.util.concurrent.CompletableFuture;

import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;
import com.alipay.antchain.bridge.relayer.commons.model.SDPMsgCommitResult;
import com.alipay.antchain.bridge.relayer.commons.model.SDPMsgWrapper;
import org.junit.Assert;
import org.junit.Test;

public class AMConfirmServiceTest {

    @Test
    public void failedReceiptShouldRemainPendingWithoutAbortingTheBatch() {
        CompletableFuture<ConfirmResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("native transaction temporarily unavailable"));

        Assert.assertNull(AMConfirmService.resolveConfirmResult(failed, "dioxide2", "diox04.id"));
    }

    @Test
    public void interruptedReceiptShouldAbortAndPreserveInterruptFlag() {
        CompletableFuture<ConfirmResult> pending = new CompletableFuture<>();
        Thread.currentThread().interrupt();
        try {
            AMConfirmService.resolveConfirmResult(pending, "dioxide2", "diox04.id");
            Assert.fail("expected the interrupted confirmation pass to abort");
        } catch (RuntimeException expected) {
            Assert.assertTrue(Thread.currentThread().isInterrupted());
            Assert.assertTrue(expected.getCause() instanceof InterruptedException);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void buildCommitResultShouldKeepPendingRowIdentityForNativeHash() {
        SDPMsgWrapper message = new SDPMsgWrapper();
        message.setId(2700L);

        CrossChainMessageReceipt receipt = new CrossChainMessageReceipt();
        receipt.setTxhash("yh7jj8ntyz8ervas66rfv04e7zsjxc6x4ftedvdqk2hz4kbatq7g");
        receipt.setSuccessful(true);
        receipt.setConfirmed(true);
        receipt.setErrorMsg("");

        SDPMsgCommitResult result = AMConfirmService.buildCommitResult(
                "dioxide2",
                "diox04.id",
                new ConfirmResult(receipt, message)
        );

        Assert.assertEquals(Long.valueOf(2700L), result.getSdpMsgId());
        Assert.assertEquals(receipt.getTxhash(), result.getTxHash());
        Assert.assertTrue(result.isCommitSuccess());
    }
}
