package com.alipay.antchain.bridge.plugins.ethereum3.core;
import org.junit.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ContractAddressBindingTest {
    private static final String A="0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String B="0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String Z="0x0000000000000000000000000000000000000000";
    private TransactionReceipt receipt(String status) { TransactionReceipt r=new TransactionReceipt();r.setStatus(status);return r; }
    @Test public void zeroCanBeBoundAndReadBack() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        ContractAddressBinding.ensure(A,Z,()->{calls.incrementAndGet();return receipt("0x1");},()->A);
        assertEquals(1,calls.get());
    }
    @Test public void existingCorrectBindingDoesNotSend() throws Exception {
        ContractAddressBinding.ensure(A,A.toUpperCase().replace("0X","0x"),()->{fail("unexpected send");return null;},()->{fail("unexpected query");return null;});
    }
    @Test public void conflictDoesNotSend() {
        assertThrows(IllegalStateException.class,()->ContractAddressBinding.ensure(A,B,()->{fail("unexpected send");return null;},()->A));
    }
    @Test public void failedReceiptIsRejected() {
        assertThrows(IllegalStateException.class,()->ContractAddressBinding.ensure(A,Z,()->receipt("0x0"),()->A));
    }
    @Test public void mismatchedReadbackIsRejected() {
        assertThrows(IllegalStateException.class,()->ContractAddressBinding.ensure(A,Z,()->receipt("0x1"),()->B));
    }
    @Test public void badAddressesDoNotSend() {
        for(String bad:new String[]{null,"",Z,"1234"}) assertThrows(IllegalArgumentException.class,()->ContractAddressBinding.ensure(bad,Z,()->{fail("unexpected send");return null;},()->A));
    }
    @Test public void queryAndSubmissionErrorsPropagate() {
        assertThrows(UnsupportedOperationException.class,()->ContractAddressBinding.ensure(A,Z,()->{throw new UnsupportedOperationException();},()->A));
        assertThrows(UnsupportedOperationException.class,()->ContractAddressBinding.ensure(A,Z,()->receipt("0x1"),()->{throw new UnsupportedOperationException();}));
    }
}
