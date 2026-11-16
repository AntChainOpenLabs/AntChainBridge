package com.alipay.antchain.bridge.plugins.ethereum3.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.AuthMsg;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.EthLog;
import lombok.*;
import org.hyperledger.besu.datatypes.Address;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.utils.Numeric;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class EthAuthMessageLog {

    private static final String TOPIC = "0x79b7516b1b7a6a39fb4b7b22e8667cd3744e5c27425292f8a9f49d1042c0c651";

    public static EthAuthMessageLog decodeFromJson(String json) {
        return JSON.parseObject(json, EthAuthMessageLog.class);
    }

    // Receipt-local for new messages. Old collectors populated this with the block-global index.
    private Integer logIndex;

    // Explicit marker: validators must not fall back to content lookup when this is present.
    private Integer receiptLogIndex;

    // Preserve the original RPC metadata, including its block-global logIndex.
    private Log sendAuthMessageLog;

    public static EthAuthMessageLog fromReceipt(TransactionReceipt receipt, Log selected) {
        require(receipt != null && receipt.getLogs() != null && selected != null, "missing receipt/log");
        require(!selected.isRemoved(), "removed log");
        require(selected.getLogIndex() != null && selected.getLogIndex().signum() >= 0
                && selected.getTransactionIndex() != null && selected.getTransactionIndex().signum() >= 0,
                "missing or negative RPC index");
        require(sameHex(receipt.getTransactionHash(), selected.getTransactionHash())
                && Objects.equals(receipt.getTransactionIndex(), selected.getTransactionIndex()),
                "log transaction does not match receipt");
        int found = -1;
        for (int i = 0; i < receipt.getLogs().size(); i++) {
            Log candidate = receipt.getLogs().get(i);
            if (Objects.equals(candidate.getLogIndex(), selected.getLogIndex())
                    && sameHex(candidate.getTransactionHash(), selected.getTransactionHash())
                    && sameHex(candidate.getBlockHash(), selected.getBlockHash())
                    && sameRpcContent(candidate, selected)) {
                require(found == -1, "ambiguous RPC log");
                found = i;
            }
        }
        require(found >= 0, "selected log missing from receipt");
        return EthAuthMessageLog.builder().logIndex(found).receiptLogIndex(found)
                .sendAuthMessageLog(receipt.getLogs().get(found)).build();
    }

    public byte[] decodeMessage() {
        require(sendAuthMessageLog != null && sendAuthMessageLog.getTopics() != null
                && sendAuthMessageLog.getTopics().size() == 1
                && TOPIC.equalsIgnoreCase(sendAuthMessageLog.getTopics().getFirst()), "invalid AM event topic");
        var event = Contract.staticExtractEventParameters(AuthMsg.SENDAUTHMESSAGE_EVENT, sendAuthMessageLog);
        require(event != null && event.getNonIndexedValues().size() == 1, "invalid AM event data");
        return (byte[]) event.getNonIndexedValues().getFirst().getValue();
    }

    /**
     * Call only after validating the receipt proof against the trusted consensus receipts root.
     * Legacy compatibility searches authenticated receipt content, never a node/RPC response.
     */
    public EthLog verifyReceiptLog(List<? extends EthLog> logs, Address amContract, byte[] message) {
        require(logs != null && sendAuthMessageLog != null && logIndex != null && logIndex >= 0,
                "missing or negative log index");
        require(!sendAuthMessageLog.isRemoved(), "removed log");
        require(Arrays.equals(amContract.toArray(), hex(sendAuthMessageLog.getAddress())), "logger not AM contract");
        require(Arrays.equals(message, decodeMessage()), "message does not match ledger event");

        if (receiptLogIndex != null) {
            require(receiptLogIndex.equals(logIndex) && receiptLogIndex >= 0 && receiptLogIndex < logs.size(),
                    "receipt log index out of range or inconsistent");
            EthLog selected = logs.get(receiptLogIndex);
            require(matchesProof(selected), "indexed receipt log does not match ledger event");
            return selected;
        }

        // Old producers used inconsistent index semantics. Only a UNIQUE complete event match is safe.
        EthLog match = null;
        int matchedIndex = -1;
        for (int i = 0; i < logs.size(); i++) {
            EthLog candidate = logs.get(i);
            if (matchesProof(candidate)) {
                require(match == null, "ambiguous legacy receipt log");
                match = candidate;
                matchedIndex = i;
            }
        }
        require(match != null, "ledger event missing from proven receipt");
        require(logIndex == matchedIndex || java.math.BigInteger.valueOf(logIndex).equals(sendAuthMessageLog.getLogIndex()),
                "inconsistent legacy log index");
        return match;
    }

    private boolean matchesProof(EthLog proofLog) {
        if (!Arrays.equals(proofLog.getLogger().toArray(), hex(sendAuthMessageLog.getAddress()))
                || !Arrays.equals(proofLog.getData().toArray(), hex(sendAuthMessageLog.getData()))
                || proofLog.getTopics().size() != sendAuthMessageLog.getTopics().size()) {
            return false;
        }
        for (int i = 0; i < proofLog.getTopics().size(); i++) {
            if (!Arrays.equals(proofLog.getTopics().get(i).toArray(), hex(sendAuthMessageLog.getTopics().get(i)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameRpcContent(Log a, Log b) {
        if (!sameHex(a.getAddress(), b.getAddress()) || !sameHex(a.getData(), b.getData())
                || a.getTopics() == null || b.getTopics() == null || a.getTopics().size() != b.getTopics().size()) {
            return false;
        }
        for (int i = 0; i < a.getTopics().size(); i++) {
            if (!sameHex(a.getTopics().get(i), b.getTopics().get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameHex(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private static byte[] hex(String value) {
        require(value != null && value.matches("(?i)0x(?:[0-9a-f]{2})*"), "invalid hex in ledger event");
        return Numeric.hexStringToByteArray(value);
    }

    private static void require(boolean valid, String error) {
        if (!valid) {
            throw new IllegalArgumentException(error);
        }
    }

    public String encodeToJson() {
        return JSON.toJSONString(this);
    }
}
