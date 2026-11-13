package com.alipay.antchain.bridge.plugins.fiscobcos3;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.commons.core.base.ConsensusState;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.commons.core.bta.BlockchainTrustAnchorFactory;
import com.alipay.antchain.bridge.commons.core.bta.IBlockchainTrustAnchor;
import org.fisco.bcos.sdk.v3.utils.Hex;
import org.junit.Test;

import static org.junit.Assert.*;

/** Offline regressions; never creates a BBC client or connects to a chain. */
public class FISCOBCOSReceiptProofRegressionTest {
    private static Object fixture(String name) throws Exception {
        Field field = FISCOBCOSHCDVSServiceTest.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private FISCOBCOSHCDVSService service() throws Exception {
        return (FISCOBCOSHCDVSService) fixture("FISCOBCOS_HCDVS_SERVICE");
    }

    private CrossChainMessage message() throws Exception {
        return CrossChainMessage.decode(((CrossChainMessage) fixture("MSG1")).encode());
    }

    private ConsensusState state() throws Exception {
        return ConsensusState.decode(((ConsensusState) fixture("CS_WHERE_MSG1")).encode());
    }

    private JSONObject proof(CrossChainMessage message) {
        return JSON.parseObject(new String(message.getProvableData().getProof(), StandardCharsets.UTF_8));
    }

    @Test
    public void wrongMessageIsRejected() throws Exception {
        var msg = message();
        msg.setMessage(new byte[]{1, 2, 3});
        assertFalse(service().verifyCrossChainMessage(msg, state()).isSuccess());
    }

    @Test
    public void missingProofNodesAreRejected() throws Exception {
        var msg = message();
        var proof = proof(msg);
        proof.put("txReceiptProof", List.of());
        msg.getProvableData().setProof(proof.toJSONString().getBytes(StandardCharsets.UTF_8));
        assertFalse(service().verifyCrossChainMessage(msg, state()).isSuccess());
    }

    @Test
    public void receiptRootMustMatchConsensusState() throws Exception {
        var msg = message();
        var proof = proof(msg);
        proof.put("receiptsRoot", "0x" + "00".repeat(32));
        msg.getProvableData().setProof(proof.toJSONString().getBytes(StandardCharsets.UTF_8));
        assertFalse(service().verifyCrossChainMessage(msg, state()).isSuccess());
    }

    @Test
    public void wrongAnchorAmIdentityIsRejected() throws Exception {
        IBlockchainTrustAnchor original = (IBlockchainTrustAnchor) fixture("BTA");
        var changed = BlockchainTrustAnchorFactory.createBTAv1(original.getDomain().getDomain(),
                original.getSubjectProduct(), original.getSubjectVersion(), original.getBcOwnerPublicKey(),
                original.getBcOwnerSigAlgo(), original.getSubjectIdentity(), original.getInitHeight(),
                original.getInitBlockHash(), new byte[32], original.getPtcOid(), original.getExtension());
        var anchor = ConsensusState.decode(((ConsensusState) fixture("ANCHOR_CS")).encode());
        assertFalse(service().verifyAnchorConsensusState(changed, anchor).isSuccess());
    }

    @Test
    public void fullVerifierHandlesConcurrentMultiLeafProofsWithoutChangingInputs() throws Exception {
        var msg = message();
        var state = state();
        var proof = proof(msg);
        String leaf = proof.getString("receiptHash").replace("0x", "");
        String other = new PureJavaKeccak256().hash("another offline receipt");
        String root = new PureJavaKeccak256().hashBytes(Hex.decode(leaf + other));
        proof.put("txReceiptProof", List.of("00000002", leaf, other));
        proof.put("receiptsRoot", root);
        msg.getProvableData().setProof(proof.toJSONString().getBytes(StandardCharsets.UTF_8));
        var stateData = JSON.parseObject(new String(state.getStateData(), StandardCharsets.UTF_8));
        stateData.put("receiptsRoot", root);
        state.setStateData(stateData.toJSONString().getBytes(StandardCharsets.UTF_8));
        byte[] originalMessage = msg.encode(), originalState = state.encode();
        var verifier = service();
        var pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 150; i++) results.add(pool.submit(() -> {
                var copy = CrossChainMessage.decode(originalMessage);
                var stateCopy = ConsensusState.decode(originalState);
                return verifier.verifyCrossChainMessage(copy, stateCopy).isSuccess()
                        && java.util.Arrays.equals(originalMessage, copy.encode())
                        && java.util.Arrays.equals(originalState, stateCopy.encode());
            }));
            for (var result : results) assertTrue(result.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
