package com.alipay.antchain.bridge.plugins.ethereum3.core;

import java.util.concurrent.Callable;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/** Idempotent owner configuration: never silently replace an existing nonzero binding. */
final class ContractAddressBinding {
    private static final String ZERO = "0x0000000000000000000000000000000000000000";
    private ContractAddressBinding() { }
    private static void valid(String address) {
        if (address == null || !address.matches("(?i)0x[0-9a-f]{40}")) {
            throw new IllegalArgumentException("invalid contract binding address");
        }
    }
    static void ensure(String expected, String current, Callable<TransactionReceipt> write,
                       Callable<String> read) throws Exception {
        valid(expected);
        valid(current);
        if (ZERO.equalsIgnoreCase(expected)) {
            throw new IllegalArgumentException("zero expected contract binding");
        }
        if (expected.equalsIgnoreCase(current)) {
            return;
        }
        if (!ZERO.equalsIgnoreCase(current)) {
            throw new IllegalStateException("conflicting nonzero contract binding");
        }
        TransactionReceipt receipt = write.call();
        if (receipt == null || !receipt.isStatusOK()) {
            throw new IllegalStateException("binding transaction failed");
        }
        String actual = read.call();
        valid(actual);
        if (!expected.equalsIgnoreCase(actual)) {
            throw new IllegalStateException("contract binding readback mismatch");
        }
    }
}
