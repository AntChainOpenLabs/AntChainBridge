package com.alipay.antchain.bridge.plugins.ethereum3;

import java.math.BigInteger;
import java.util.List;
import java.nio.charset.StandardCharsets;

import com.alipay.antchain.bridge.plugins.ethereum3.core.EthAuthMessageLog;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.EthLog;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.EthLogTopic;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import static org.junit.Assert.*;

public class EthereumReceiptLogIndexTest {
    private static final String AM = "0x1111111111111111111111111111111111111111";
    private static final String OTHER = "0x2222222222222222222222222222222222222222";
    private static final String TOPIC = "0x79b7516b1b7a6a39fb4b7b22e8667cd3744e5c27425292f8a9f49d1042c0c651";

    private static Log log(int globalIndex, String body) {
        Log log = new Log();
        log.setLogIndex("0x" + Integer.toHexString(globalIndex));
        log.setTransactionIndex("0x1");
        log.setTransactionHash("0x" + "ab".repeat(32));
        log.setBlockHash("0x" + "cd".repeat(32));
        log.setAddress(AM);
        log.setTopics(List.of(TOPIC));
        log.setData(FunctionEncoder.encodeConstructor(List.of(new DynamicBytes(body.getBytes(StandardCharsets.UTF_8)))));
        if (!log.getData().startsWith("0x")) log.setData("0x" + log.getData());
        return log;
    }

    private static TransactionReceipt receipt(Log... logs) {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash(logs[0].getTransactionHash());
        receipt.setTransactionIndex("0x1");
        receipt.setLogs(List.of(logs));
        return receipt;
    }

    private static EthLog proofLog(Log log) {
        return new EthLog(Address.fromHexString(log.getAddress()), Bytes.fromHexString(log.getData()),
                log.getTopics().stream().map(EthLogTopic::fromHexString).toList());
    }

    private static void verify(EthAuthMessageLog ledger, List<EthLog> logs, String body) {
        ledger.verifyReceiptLog(logs, Address.fromHexString(AM), body.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void collectorUsesReceiptLocalIndexAndRetainsRpcGlobalIndex() {
        Log unrelated = log(62, "unrelated");
        unrelated.setAddress(OTHER);
        Log selected = log(63, "selected");
        EthAuthMessageLog ledger = EthAuthMessageLog.fromReceipt(receipt(unrelated, selected), selected);
        assertEquals(Integer.valueOf(1), ledger.getLogIndex());
        assertEquals(Integer.valueOf(1), ledger.getReceiptLogIndex());
        assertEquals(BigInteger.valueOf(63), ledger.getSendAuthMessageLog().getLogIndex());
        assertArrayEquals("selected".getBytes(StandardCharsets.UTF_8), ledger.decodeMessage());
        verify(EthAuthMessageLog.decodeFromJson(ledger.encodeToJson()), List.of(proofLog(unrelated), proofLog(selected)), "selected");
    }

    @Test public void eachFilterEventResolvesOnlyItsOwnPayload() {
        Log a = log(62, "one");
        Log b = log(63, "two");
        TransactionReceipt receipt = receipt(a, b);
        var results = List.of(a, b).stream().map(log -> EthAuthMessageLog.fromReceipt(receipt, log)).toList();
        assertEquals(2, results.size());
        assertEquals(Integer.valueOf(0), results.get(0).getReceiptLogIndex());
        assertEquals(Integer.valueOf(1), results.get(1).getReceiptLogIndex());
        assertArrayEquals("one".getBytes(StandardCharsets.UTF_8), results.get(0).decodeMessage());
        assertArrayEquals("two".getBytes(StandardCharsets.UTF_8), results.get(1).decodeMessage());
    }

    @Test public void legacyGlobalIndexCanExceedReceiptSize() {
        Log a = log(62, "one");
        var ledger = EthAuthMessageLog.builder().logIndex(62).sendAuthMessageLog(a).build();
        verify(ledger, List.of(proofLog(a), proofLog(log(63, "other"))), "one");
        assertEquals(Integer.valueOf(62), ledger.getLogIndex()); // Verification does not rewrite old ledger.
        assertNull(ledger.getReceiptLogIndex());
    }

    @Test public void legacyInRangeIndexCannotSelectAnotherEvent() {
        Log a = log(1, "one");
        var ledger = EthAuthMessageLog.builder().logIndex(1).sendAuthMessageLog(a).build();
        verify(ledger, List.of(proofLog(a), proofLog(log(2, "other"))), "one");
        ledger.setLogIndex(99);
        assertThrows(IllegalArgumentException.class, () -> verify(ledger, List.of(proofLog(a)), "one"));
    }

    @Test public void explicitInvalidIndexNeverFallsBack() {
        Log a = log(62, "one");
        var ledger = EthAuthMessageLog.fromReceipt(receipt(a, log(63, "other")), a);
        for (int index : new int[]{-1, 1, 62, Integer.MAX_VALUE}) {
            ledger.setLogIndex(index);
            ledger.setReceiptLogIndex(index);
            assertThrows(IllegalArgumentException.class, () -> verify(ledger, List.of(proofLog(a), proofLog(log(63, "other"))), "one"));
        }
        ledger.setLogIndex(0);
        ledger.setReceiptLogIndex(1);
        assertThrows(IllegalArgumentException.class, () -> verify(ledger, List.of(proofLog(a)), "one"));
    }

    @Test public void ambiguousLegacyEventsFailButExplicitPositionsRemainDistinct() {
        Log a = log(62, "same");
        Log b = log(63, "same");
        var ledger = EthAuthMessageLog.builder().logIndex(62).sendAuthMessageLog(a).build();
        assertThrows(IllegalArgumentException.class, () -> verify(ledger, List.of(proofLog(a), proofLog(b)), "same"));
        verify(EthAuthMessageLog.fromReceipt(receipt(a, b), a), List.of(proofLog(a), proofLog(b)), "same");
        verify(EthAuthMessageLog.fromReceipt(receipt(a, b), b), List.of(proofLog(a), proofLog(b)), "same");
    }

    @Test public void malformedOrTamperedEventCannotPass() {
        Log a = log(62, "one");
        var ledger = EthAuthMessageLog.fromReceipt(receipt(a), a);
        var proof = List.of(proofLog(a));
        assertThrows(IllegalArgumentException.class, () -> verify(ledger, proof, "tampered"));
        a.setAddress(OTHER);
        assertThrows(IllegalArgumentException.class, () -> verify(ledger, proof, "one"));
        a.setAddress(AM);
        a.setTopics(List.of(TOPIC, TOPIC));
        assertThrows(IllegalArgumentException.class, () -> verify(ledger, proof, "one"));
        a.setTopics(List.of());
        assertThrows(IllegalArgumentException.class, () -> verify(ledger, proof, "one"));
        a.setTopics(List.of(TOPIC));
        a.setData(log(62, "tampered").getData());
        assertThrows(IllegalArgumentException.class, () -> verify(ledger, proof, "tampered"));
        a.setData("0xzz");
        assertThrows(RuntimeException.class, () -> verify(ledger, proof, "one"));
    }

    @Test public void nullNegativeRemovedAndUnprovenEventsFail() {
        Log a = log(62, "one");
        var ledger = EthAuthMessageLog.builder().sendAuthMessageLog(a).build();
        var proof = List.of(proofLog(a));
        assertThrows(IllegalArgumentException.class, () -> verify(ledger, proof, "one"));
        ledger.setLogIndex(-1);
        assertThrows(IllegalArgumentException.class, () -> verify(ledger, proof, "one"));
        ledger.setLogIndex(62);
        assertThrows(IllegalArgumentException.class, () -> verify(ledger, List.of(proofLog(log(63, "other"))), "one"));
        a.setRemoved(true);
        assertThrows(IllegalArgumentException.class, () -> verify(ledger, proof, "one"));
    }

    @Test public void collectorRejectsMismatchAndAmbiguousRpcMetadata() {
        Log a = log(62, "one");
        TransactionReceipt receipt = receipt(a);
        assertThrows(IllegalArgumentException.class, () -> EthAuthMessageLog.fromReceipt(receipt, log(63, "one")));
        assertThrows(IllegalArgumentException.class, () -> EthAuthMessageLog.fromReceipt(receipt, log(62, "different")));
        assertThrows(IllegalArgumentException.class, () -> EthAuthMessageLog.fromReceipt(receipt(a, a), a));
        receipt.setTransactionHash("0x" + "ef".repeat(32));
        assertThrows(IllegalArgumentException.class, () -> EthAuthMessageLog.fromReceipt(receipt, a));
    }
}
