package com.alipay.antchain.bridge.plugins.ethereum3;

import java.lang.reflect.Field;
import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.commons.core.base.ConsensusState;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.plugins.ethereum3.core.EthAuthMessageLog;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class EthereumReceiptProofCompatibilityTest {
    private EthereumHcdvsService service;
    private ConsensusState state;
    private CrossChainMessage message;

    private static Object fixture(String name) throws Exception {
        Field field = EthereumHcdvsTest.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    @Before public void setup() throws Exception {
        service = (EthereumHcdvsService) fixture("ETHEREUM_HCDVS_SERVICE");
        state = ConsensusState.decode(((ConsensusState) fixture("CS_WHERE_MSG1")).encode());
        ConsensusState parent = ConsensusState.decode(((ConsensusState) fixture("PARENT_CS_WHERE_MSG1")).encode());
        assertTrue(service.verifyConsensusState(state, parent).isSuccess());
        message = CrossChainMessage.decode(((CrossChainMessage) fixture("MSG1")).encode());
    }

    private EthAuthMessageLog ledger() {
        return EthAuthMessageLog.decodeFromJson(new String(message.getProvableData().getLedgerData()));
    }

    private void ledger(EthAuthMessageLog ledger) {
        message.getProvableData().setLedgerData(ledger.encodeToJson().getBytes());
    }

    @Test public void legacyGlobalIndexPassesOnlyWithOriginalProofAndMessage() {
        var ledger = ledger();
        ledger.setLogIndex(99);
        ledger.getSendAuthMessageLog().setLogIndex("0x63");
        ledger(ledger);
        byte[] original = message.encode();
        assertTrue(service.verifyCrossChainMessage(message, state).isSuccess());
        assertArrayEquals(original, message.encode());
    }

    @Test public void explicitOutOfRangeIsRejectedEvenIfContentExists() {
        var ledger = ledger();
        ledger.setLogIndex(99);
        ledger.setReceiptLogIndex(99);
        ledger(ledger);
        assertFalse(service.verifyCrossChainMessage(message, state).isSuccess());
    }

    @Test public void wrongTransactionIndexAndMessageAreRejected() {
        var ledger = ledger();
        ledger.getSendAuthMessageLog().setTransactionIndex("0xff");
        ledger(ledger);
        assertFalse(service.verifyCrossChainMessage(message, state).isSuccess());
        ledger.getSendAuthMessageLog().setTransactionIndex("0x0");
        ledger(ledger);
        message.setMessage(new byte[]{1, 2, 3});
        assertFalse(service.verifyCrossChainMessage(message, state).isSuccess());
    }

    @Test public void legacyLookupNeverBypassesTrustedReceiptRoot() {
        var ledger = ledger();
        ledger.setLogIndex(99);
        ledger.getSendAuthMessageLog().setLogIndex("0x63");
        ledger(ledger);
        var stateJson = JSON.parseObject(new String(state.getStateData()));
        var header = JSON.parseObject(stateJson.getString("execution_payload_header"));
        header.put("receipts_root", "0x" + "ef".repeat(32));
        stateJson.put("execution_payload_header", header.toJSONString());
        state.setStateData(stateJson.toJSONString().getBytes());
        assertFalse(service.verifyCrossChainMessage(message, state).isSuccess());
    }

    @Test public void malformedProofCannotBeEndorsed() {
        var proof = JSON.parseObject(new String(message.getProvableData().getProof()));
        proof.put("proofRelatedNodes", java.util.List.of());
        message.getProvableData().setProof(proof.toJSONString().getBytes());
        try {
            assertFalse(service.verifyCrossChainMessage(message, state).isSuccess());
        } catch (RuntimeException rejected) {
            // The PTC caller maps invalid proof exceptions to a failed verification RPC.
        }
    }
}
