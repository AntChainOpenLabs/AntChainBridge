package org.antchainbridge.experiment;

public interface TransactionSender extends AutoCloseable {

    /** Resolved source application identity; no private SDK configuration is serialized. */
    default String appIdentity() { throw new UnsupportedOperationException("source identity required"); }

    /** Must not broadcast. Runs before the first durable sending intent. */
    default void preflight(byte[] firstPayload, String targetIdentity) throws Exception {}

    String send(byte[] payload) throws Exception;

    /** A receipt establishes failure, while timeouts/transport exceptions remain UNKNOWN. */
    final class SubmissionFailure extends Exception {
        public final String txHash;
        public final boolean definitive;
        public SubmissionFailure(String txHash, boolean definitive) {
            super(definitive ? "source receipt failed" : "source result unknown");
            this.txHash = txHash;
            this.definitive = definitive;
        }
    }

    @Override
    default void close() throws Exception {
    }
}
