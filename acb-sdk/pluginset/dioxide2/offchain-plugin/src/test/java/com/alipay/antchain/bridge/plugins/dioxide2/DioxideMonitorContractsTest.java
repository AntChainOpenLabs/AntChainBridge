package com.alipay.antchain.bridge.plugins.dioxide2;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.lang.reflect.Field;

import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.AuthMessageContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.SDPContract;

import com.alipay.antchain.bridge.plugins.dioxide2.conf.DioxideConfig;
import com.alipay.antchain.bridge.plugins.dioxide2.gcl.Contracts;
import com.alipay.antchain.bridge.plugins.dioxide2.gcl.Interfaces;
import org.junit.Assert;
import org.junit.Test;

public class DioxideMonitorContractsTest {

    @Test
    public void contextPersistsFreshlyDeployedAmAndSdpCids() throws Exception {
        DioxideBBCService service = new DioxideBBCService();
        DioxideConfig config = new DioxideConfig();
        DefaultBBCContext context = new DefaultBBCContext();
        AuthMessageContract am = new AuthMessageContract();
        am.setContractAddress("631378018305");
        SDPContract sdp = new SDPContract();
        sdp.setContractAddress("631379066881");
        context.setAuthMessageContract(am);
        context.setSdpContract(sdp);
        Field configField = DioxideBBCService.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(service, config);
        Field contextField = DioxideBBCService.class.getDeclaredField("bbcContext");
        contextField.setAccessible(true);
        contextField.set(service, context);
        DioxideConfig persisted = DioxideConfig.fromJsonString(new String(
                service.getContext().getConfForBlockchainClient(), StandardCharsets.UTF_8));
        Assert.assertEquals(am.getContractAddress(), persisted.getAmContractAddressDeployed());
        Assert.assertEquals(sdp.getContractAddress(), persisted.getSdpContractAddressDeployed());
    }

    @Test
    public void generatedContractsContainMonitorProofAndRollbackFlow() {
        String ptcHub = decode(Contracts.PTCHUB);
        String verifier = decode(Contracts.MONITORVERIFIER);
        String authMsg = decode(Contracts.AUTHMSG);
        String monitor = decode(Contracts.MONITOR);

        Assert.assertTrue(ptcHub.contains("TP_PROOF_RAW_PROOF = 0x01ffu16"));
        Assert.assertTrue(ptcHub.contains("_isMonitorNode"));
        Assert.assertTrue(ptcHub.contains("while (offset < signatures.length())"));
        Assert.assertFalse(ptcHub.contains("offset < signatures.length() && !monitorFound"));
        Assert.assertTrue(ptcHub.contains("if (!monitorFound && _isMonitorNode(nodeId))"));
        Assert.assertTrue(ptcHub.contains("verifier.receiveMonitorNodeProof(signature)"));
        Assert.assertFalse(ptcHub.contains("verifySig"));
        Assert.assertTrue(ptcHub.contains("updatePTCTrustRoot"));
        Assert.assertTrue(ptcHub.contains("getPTCVerifyAnchor"));
        Assert.assertTrue(ptcHub.contains("addTpBta"));
        Assert.assertTrue(ptcHub.contains("getSupportedPTCType"));

        Assert.assertTrue(verifier.contains("pendingSignature[i] != 0u8"));
        Assert.assertTrue(verifier.contains("pendingSignature.set_length(0u32)"));
        Assert.assertTrue(authMsg.contains("ptcHub.verifyProof(dr.rawTpProof)"));

        Assert.assertTrue(monitor.contains("MONITOR_ROLLBACK = 3u32"));
        Assert.assertTrue(monitor.contains("sdp.sendMessageFromMonitor(senderDomain, author, rawRollback, receiverID)"));
        Assert.assertTrue(monitor.contains("sdp.sendUnorderedMessageFromMonitor(senderDomain, author, rawRollback, receiverID)"));

        Assert.assertTrue(decode(Interfaces.IPTCHUB).contains("verifyProof"));
        Assert.assertTrue(decode(Interfaces.IMONITORVERIFIER).contains("verifyMonitorNodeProofMessage"));
    }

    @Test
    public void configRoundTripKeepsNewContractLocations() throws Exception {
        DioxideConfig config = new DioxideConfig();
        config.setPtcHubContractAddressDeployed("101");
        config.setMonitorVerifierContractAddressDeployed("102");

        DioxideConfig decoded = DioxideConfig.fromJsonString(config.toJsonString());

        Assert.assertEquals("101", decoded.getPtcHubContractAddressDeployed());
        Assert.assertEquals("102", decoded.getMonitorVerifierContractAddressDeployed());
        Assert.assertEquals("PtcHub", decoded.getPtcHubContractName());
        Assert.assertEquals("MonitorVerifier", decoded.getMonitorVerifierContractName());
    }

    private static String decode(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}
