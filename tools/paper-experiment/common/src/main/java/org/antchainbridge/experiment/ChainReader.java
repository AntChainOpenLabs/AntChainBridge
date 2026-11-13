package org.antchainbridge.experiment;

import java.util.List;

/** Query-only surface used by recovery and reception verification. */
public interface ChainReader extends AutoCloseable {
    record Head(long height, String hash) {}
    record Log(String address, List<String> topics, String data, int index) {}
    record Receipt(String hash, long height, String blockHash, boolean successful, List<Log> logs) {}
    String appIdentity();
    String eventTopic(String signature);
    Head finalizedHead() throws Exception;
    String blockHash(long height) throws Exception;
    List<Receipt> receipts(long height) throws Exception;
    Receipt receipt(String hash) throws Exception;
    default void close() throws Exception {}
}
