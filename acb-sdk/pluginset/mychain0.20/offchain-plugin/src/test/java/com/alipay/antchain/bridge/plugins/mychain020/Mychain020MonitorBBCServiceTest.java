package com.alipay.antchain.bridge.plugins.mychain020;

import cn.hutool.core.util.ReflectUtil;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.bbc.syscontract.SDPContract;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.plugins.mychain020.contract.MonitorContractClientEVM;
import com.alipay.antchain.bridge.plugins.mychain020.contract.MonitorVerifierContractEVM;
import com.alipay.antchain.bridge.plugins.mychain020.contract.PtcContractEvm;
import com.alipay.antchain.bridge.plugins.mychain020.contract.SDPContractClientEVM;
import com.alipay.antchain.bridge.plugins.mychain020.contract.AMContractClientEVM;
import com.alipay.antchain.bridge.plugins.mychain020.model.ContractAddressInfo;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Config;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.crypto.hash.Hash;
import com.alipay.mychain.sdk.api.utils.Utils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;

public class Mychain020MonitorBBCServiceTest {

    private static final String MONITOR_CONTRACT = "monitor_contract";
    private static final String MONITOR_VERIFIER_CONTRACT = "monitor_verifier_contract";
    private static final String SDP_CONTRACT = "sdp_contract";
    private static final String PTC_HUB_CONTRACT = "ptc_hub_contract";

    private Mychain020BBCService service;
    private Mychain020BBCContext context;
    private Mychain020Client mychain020Client;
    private Mychain020Config mychain020Config;
    private MonitorContractClientEVM monitorContractClientEVM;
    private MonitorVerifierContractEVM monitorVerifierContractEVM;
    private SDPContractClientEVM sdpContractClientEVM;
    private PtcContractEvm ptcContractEvm;
    private AMContractClientEVM amContractClientEVM;
    private Identity hubBinding;
    private Identity verifierBinding;
    private Identity monitorBinding;

    @Before
    public void setUp() {
        service = new Mychain020BBCService();
        context = new Mychain020BBCContext(new DefaultBBCContext(), LoggerFactory.getLogger(getClass()));

        mychain020Client = mock(Mychain020Client.class);
        mychain020Config = new Mychain020Config();
        monitorContractClientEVM = mock(MonitorContractClientEVM.class);
        monitorVerifierContractEVM = mock(MonitorVerifierContractEVM.class);
        sdpContractClientEVM = mock(SDPContractClientEVM.class);
        ptcContractEvm = mock(PtcContractEvm.class);
        amContractClientEVM = mock(AMContractClientEVM.class);
        hubBinding = new Identity(new Hash(new byte[32]));
        verifierBinding = new Identity(new Hash(new byte[32]));
        monitorBinding = new Identity(new Hash(new byte[32]));
        when(monitorContractClientEVM.getMonitorVerifierIdentityOrZero()).thenAnswer(i -> monitorBinding);
        doAnswer(i -> { monitorBinding = Utils.getIdentityByName(i.getArgument(0), mychain020Config.getMychainHashType()); return null; })
                .when(monitorContractClientEVM).setMonitorVerifier(any(String.class));
        doAnswer(i -> { monitorBinding = i.getArgument(0); return null; })
                .when(monitorContractClientEVM).setMonitorVerifier(any(Identity.class));
        when(ptcContractEvm.getContractAddress()).thenReturn(PTC_HUB_CONTRACT);
        when(ptcContractEvm.getMonitorVerifierIdentity()).thenAnswer(i -> verifierBinding);
        when(monitorVerifierContractEVM.getPtcHubIdentity(any())).thenAnswer(i -> hubBinding);
        doAnswer(i -> { verifierBinding = i.getArgument(0); return null; })
                .when(ptcContractEvm).setMonitorVerifier(any(Identity.class));
        doAnswer(i -> { hubBinding = Utils.getIdentityByName(i.getArgument(0), mychain020Config.getMychainHashType()); return null; })
                .when(monitorVerifierContractEVM).setPtcHubAddress(any(String.class));
        doAnswer(i -> { hubBinding = Utils.getIdentityByName(i.getArgument(0), mychain020Config.getMychainHashType()); return null; })
                .when(monitorVerifierContractEVM).setPtcHubAddress(any(String.class), any(Identity.class));

        when(mychain020Client.getPrimary()).thenReturn("mychain-monitor-test");
        when(mychain020Client.isTeeChain()).thenReturn(false);
        when(mychain020Client.getConfig()).thenReturn(mychain020Config);
        when(ptcContractEvm.ensureCommitteeVerifierMonitorSupport()).thenReturn(true);
        when(ptcContractEvm.ensureMonitorProofRoutingSupport()).thenReturn(true);
        when(monitorVerifierContractEVM.deployContract()).thenReturn(true);
        when(monitorVerifierContractEVM.ensureImplementationSupported()).thenReturn(true);
        when(monitorContractClientEVM.deployContract()).thenReturn(true);
        when(monitorContractClientEVM.ensureImplementationSupported()).thenReturn(true);
        when(sdpContractClientEVM.ensureMonitorSupport()).thenReturn(true);

        context.setMonitorContractClientEVM(monitorContractClientEVM);
        context.setMonitorVerifierContractEVM(monitorVerifierContractEVM);
        context.setSdpContractClientEVM(sdpContractClientEVM);
        context.setPtcContractEvm(ptcContractEvm);
        context.setAmContractClientEVM(amContractClientEVM);

        SDPContract sdpContract = new SDPContract();
        sdpContract.setContractAddress(SDP_CONTRACT);
        sdpContract.setStatus(ContractStatusEnum.CONTRACT_READY);
        context.setSdpContract(sdpContract);

        ReflectUtil.setFieldValue(service, "context", context);
        ReflectUtil.setFieldValue(service, "mychain020Client", mychain020Client);
    }

    @Test
    public void setupMonitorContractShouldDeployAndWireContracts() throws Exception {
        when(monitorVerifierContractEVM.deployContract()).thenReturn(true);
        when(monitorContractClientEVM.deployContract()).thenReturn(true);
        when(monitorVerifierContractEVM.getContractAddress()).thenReturn(MONITOR_VERIFIER_CONTRACT);
        when(monitorContractClientEVM.getContractAddress()).thenReturn(MONITOR_CONTRACT);
        when(monitorContractClientEVM.getStatus()).thenReturn(ContractStatusEnum.CONTRACT_DEPLOYED);
        when(sdpContractClientEVM.getContractAddress()).thenReturn(SDP_CONTRACT);

        service.setupMonitorContract();

        verify(monitorVerifierContractEVM).deployContract();
        verify(monitorContractClientEVM).deployContract();
        verify(monitorContractClientEVM).setMonitorVerifier(MONITOR_VERIFIER_CONTRACT);
        verify(sdpContractClientEVM).ensureMonitorSupport();
        verify(monitorContractClientEVM).setProtocol(SDP_CONTRACT);
        verify(sdpContractClientEVM).setMonitorContract(MONITOR_CONTRACT);

        Assert.assertNotNull(context.getMonitorContract());
        Assert.assertEquals(MONITOR_CONTRACT, context.getMonitorContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, context.getMonitorContract().getStatus());
        Assert.assertEquals(MONITOR_CONTRACT, mychain020Config.getMonitorContractName());
        Assert.assertEquals(MONITOR_VERIFIER_CONTRACT, mychain020Config.getMonitorVerifierContractName());

        Mychain020Config persistedConfig = Mychain020Config.fromJsonString(
                new String(context.getConfForBlockchainClient(), StandardCharsets.UTF_8));
        Assert.assertEquals(MONITOR_CONTRACT, persistedConfig.getMonitorContractName());
        Assert.assertEquals(MONITOR_VERIFIER_CONTRACT, persistedConfig.getMonitorVerifierContractName());
    }

    @Test
    public void setupMonitorContractShouldUpgradeLegacyMonitorContracts() {
        when(monitorContractClientEVM.getContractAddress()).thenReturn(MONITOR_CONTRACT);
        when(monitorContractClientEVM.ensureImplementationSupported()).thenReturn(true);
        when(monitorVerifierContractEVM.deployContract()).thenReturn(true);
        when(monitorContractClientEVM.deployContract()).thenReturn(true);
        when(monitorVerifierContractEVM.getContractAddress()).thenReturn(MONITOR_VERIFIER_CONTRACT);
        when(monitorContractClientEVM.getStatus()).thenReturn(ContractStatusEnum.CONTRACT_DEPLOYED);
        when(sdpContractClientEVM.getContractAddress()).thenReturn(SDP_CONTRACT);
        when(ptcContractEvm.getContractAddress()).thenReturn(PTC_HUB_CONTRACT);

        service.setupMonitorContract();

        verify(monitorContractClientEVM).ensureImplementationSupported();
        verify(ptcContractEvm).ensureCommitteeVerifierMonitorSupport();
        verify(ptcContractEvm).ensureMonitorProofRoutingSupport();
        verify(monitorVerifierContractEVM).deployContract();
        verify(monitorContractClientEVM).deployContract();
        verify(monitorContractClientEVM).setMonitorVerifier(MONITOR_VERIFIER_CONTRACT);
        verify(monitorVerifierContractEVM).setPtcHubAddress(PTC_HUB_CONTRACT);
        verify(ptcContractEvm).setMonitorVerifier(any(Identity.class));
        verify(monitorContractClientEVM).setProtocol(SDP_CONTRACT);
        verify(sdpContractClientEVM).setMonitorContract(MONITOR_CONTRACT);

        InOrder upgradeOrder = inOrder(
                monitorVerifierContractEVM,
                ptcContractEvm,
                monitorContractClientEVM,
                sdpContractClientEVM);
        upgradeOrder.verify(monitorVerifierContractEVM).ensureImplementationSupported();
        upgradeOrder.verify(ptcContractEvm).ensureCommitteeVerifierMonitorSupport();
        upgradeOrder.verify(ptcContractEvm).ensureMonitorProofRoutingSupport();
        upgradeOrder.verify(monitorContractClientEVM).ensureImplementationSupported();
        upgradeOrder.verify(sdpContractClientEVM).ensureMonitorSupport();
    }

    @Test
    public void setupMonitorContractShouldReuseSupportedMonitorContracts() {
        when(monitorContractClientEVM.getContractAddress()).thenReturn(MONITOR_CONTRACT);
        when(monitorContractClientEVM.ensureImplementationSupported()).thenReturn(true);
        when(monitorVerifierContractEVM.deployContract()).thenReturn(true);
        when(monitorContractClientEVM.deployContract()).thenReturn(true);
        when(monitorVerifierContractEVM.getContractAddress()).thenReturn(MONITOR_VERIFIER_CONTRACT);

        service.setupMonitorContract();

        verify(monitorContractClientEVM).ensureImplementationSupported();
        verify(monitorVerifierContractEVM, times(1)).deployContract();
        verify(monitorContractClientEVM, times(1)).deployContract();
    }

    @Test
    public void setupMonitorContractShouldRecoverAndUpgradeVerifierByIdentity() {
        Identity verifierIdentity = mock(Identity.class);
        when(verifierIdentity.hexStrValue()).thenReturn(String.format("%064d", 1));
        when(monitorContractClientEVM.getContractAddress()).thenReturn(MONITOR_CONTRACT);
        when(monitorVerifierContractEVM.getContractAddress()).thenReturn("");
        when(monitorContractClientEVM.getMonitorVerifierIdentity()).thenReturn(verifierIdentity);
        when(monitorContractClientEVM.getMonitorVerifierIdentityOrZero()).thenReturn(verifierIdentity);
        when(monitorVerifierContractEVM.ensureImplementationSupported(verifierIdentity)).thenReturn(true);
        when(monitorContractClientEVM.getStatus()).thenReturn(ContractStatusEnum.CONTRACT_DEPLOYED);

        service.setupMonitorContract();

        verify(monitorVerifierContractEVM, never()).deployContract();
        verify(monitorVerifierContractEVM).ensureImplementationSupported(verifierIdentity);
        verify(monitorContractClientEVM, never()).setMonitorVerifier(verifierIdentity);
        Assert.assertNull(mychain020Config.getMonitorVerifierContractName());
    }

    @Test(expected = RuntimeException.class)
    public void setupMonitorContractShouldNotReplaceUnrecoverableVerifier() {
        when(monitorContractClientEVM.getContractAddress()).thenReturn(MONITOR_CONTRACT);
        when(monitorVerifierContractEVM.getContractAddress()).thenReturn("");
        when(monitorContractClientEVM.getMonitorVerifierIdentity()).thenReturn(null);
        when(ptcContractEvm.getContractAddress()).thenReturn(PTC_HUB_CONTRACT);

        try {
            service.setupMonitorContract();
        } finally {
            verify(monitorVerifierContractEVM, never()).deployContract();
            verify(monitorContractClientEVM, never()).deployContract();
        }
    }

    @Test(expected = RuntimeException.class)
    public void setupMonitorContractShouldStopBeforeDeployWhenPtcUpgradeFails() {
        when(ptcContractEvm.getContractAddress()).thenReturn(PTC_HUB_CONTRACT);
        when(ptcContractEvm.ensureCommitteeVerifierMonitorSupport()).thenReturn(false);

        try {
            service.setupMonitorContract();
        } finally {
            verify(ptcContractEvm).ensureCommitteeVerifierMonitorSupport();
            verify(monitorContractClientEVM, never()).deployContract();
        }
    }

    @Test(expected = RuntimeException.class)
    public void setupMonitorContractShouldStopBeforeDeployWhenSdpUpgradeFails() {
        when(sdpContractClientEVM.getContractAddress()).thenReturn(SDP_CONTRACT);
        when(sdpContractClientEVM.ensureMonitorSupport()).thenReturn(false);

        try {
            service.setupMonitorContract();
        } finally {
            verify(sdpContractClientEVM).ensureMonitorSupport();
            verify(monitorVerifierContractEVM).deployContract();
            verify(monitorContractClientEVM).deployContract();
        }
    }

    @Test
    public void setProtocolShouldAutomaticallyUpgradeLegacyMonitor() {
        when(monitorContractClientEVM.getContractAddress()).thenReturn("legacy_monitor_contract");
        when(monitorContractClientEVM.isImplementationVersionSupported()).thenReturn(false);
        when(amContractClientEVM.getStatus()).thenReturn(ContractStatusEnum.CONTRACT_READY);
        when(sdpContractClientEVM.getContractAddress()).thenReturn(SDP_CONTRACT);

        Mychain020BBCService serviceSpy = spy(service);
        doNothing().when(serviceSpy).setupMonitorContract();

        serviceSpy.setProtocol(SDP_CONTRACT, "0");

        verify(serviceSpy).setupMonitorContract();
    }

    @Test
    public void setProtocolShouldNotUpgradeSupportedMonitor() {
        when(monitorContractClientEVM.getContractAddress()).thenReturn(MONITOR_CONTRACT);
        when(monitorContractClientEVM.isImplementationVersionSupported()).thenReturn(true);
        when(amContractClientEVM.getStatus()).thenReturn(ContractStatusEnum.CONTRACT_READY);
        when(sdpContractClientEVM.getContractAddress()).thenReturn(SDP_CONTRACT);

        Mychain020BBCService serviceSpy = spy(service);

        serviceSpy.setProtocol(SDP_CONTRACT, "0");

        verify(serviceSpy, never()).setupMonitorContract();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void setupMonitorContractShouldRejectTeeChain() {
        when(mychain020Client.isTeeChain()).thenReturn(true);

        service.setupMonitorContract();
    }

    @Test
    public void setupMonitorContractShouldSkipSdpWireWhenSdpIsNotDeployed() {
        when(monitorVerifierContractEVM.deployContract()).thenReturn(true);
        when(monitorContractClientEVM.deployContract()).thenReturn(true);
        when(monitorVerifierContractEVM.getContractAddress()).thenReturn(MONITOR_VERIFIER_CONTRACT);
        when(monitorContractClientEVM.getContractAddress()).thenReturn(MONITOR_CONTRACT);
        when(sdpContractClientEVM.getContractAddress()).thenReturn("");

        service.setupMonitorContract();

        verify(monitorContractClientEVM).setMonitorVerifier(MONITOR_VERIFIER_CONTRACT);
        verify(monitorContractClientEVM, never()).setProtocol(SDP_CONTRACT);
        verify(sdpContractClientEVM, never()).setMonitorContract(MONITOR_CONTRACT);
    }

    @Test
    public void setProtocolInMonitorShouldUseExplicitAddress() {
        when(monitorContractClientEVM.getContractAddress()).thenReturn(MONITOR_CONTRACT);

        service.setProtocolInMonitor("custom_sdp_contract");

        verify(monitorContractClientEVM).setProtocol("custom_sdp_contract");
    }

    @Test
    public void setProtocolInMonitorShouldExtractEvmAddressFromPersistedContext() {
        when(monitorContractClientEVM.getContractAddress()).thenReturn(MONITOR_CONTRACT);

        service.setProtocolInMonitor(
                "{\"evm\":\"sdp_evm_contract\",\"wasm\":\"sdp_wasm_contract\"}");

        verify(monitorContractClientEVM).setProtocol("sdp_evm_contract");
    }

    @Test
    public void setProtocolInMonitorShouldUseContextSdpAddressByDefault() {
        when(monitorContractClientEVM.getContractAddress()).thenReturn(MONITOR_CONTRACT);
        when(sdpContractClientEVM.getContractAddress()).thenReturn(SDP_CONTRACT);

        service.setProtocolInMonitor("");

        verify(monitorContractClientEVM).setProtocol(SDP_CONTRACT);
    }

    @Test(expected = RuntimeException.class)
    public void setProtocolInMonitorShouldRejectMissingMonitorContract() {
        when(monitorContractClientEVM.getContractAddress()).thenReturn("");

        service.setProtocolInMonitor(SDP_CONTRACT);
    }

    @Test
    public void setMonitorContractShouldUseExplicitAddress() {
        when(sdpContractClientEVM.getContractAddress()).thenReturn(SDP_CONTRACT);

        service.setMonitorContract("custom_monitor_contract");

        verify(sdpContractClientEVM).setMonitorContract("custom_monitor_contract");
    }

    @Test
    public void setMonitorContractShouldDecodeFormattedEvmAddress() {
        when(sdpContractClientEVM.getContractAddress()).thenReturn(SDP_CONTRACT);

        service.setMonitorContract(new ContractAddressInfo(MONITOR_CONTRACT, null).toJson());

        verify(sdpContractClientEVM).setMonitorContract(MONITOR_CONTRACT);
    }

    @Test
    public void setMonitorContractShouldUseContextMonitorAddressByDefault() {
        when(sdpContractClientEVM.getContractAddress()).thenReturn(SDP_CONTRACT);
        when(monitorContractClientEVM.getContractAddress()).thenReturn(MONITOR_CONTRACT);

        service.setMonitorContract("");

        verify(sdpContractClientEVM).setMonitorContract(MONITOR_CONTRACT);
    }

    @Test(expected = RuntimeException.class)
    public void setMonitorContractShouldRejectMissingSdpContract() {
        when(sdpContractClientEVM.getContractAddress()).thenReturn("");

        service.setMonitorContract(MONITOR_CONTRACT);
    }

    @Test
    public void setMonitorControlShouldForwardMonitorType() {
        when(monitorContractClientEVM.getContractAddress()).thenReturn(MONITOR_CONTRACT);

        service.setMonitorControl(1);

        verify(monitorContractClientEVM).setMonitorControl(1);
    }

    @Test(expected = RuntimeException.class)
    public void setMonitorControlShouldRejectMissingMonitorContract() {
        when(monitorContractClientEVM.getContractAddress()).thenReturn("");

        service.setMonitorControl(1);
    }

    @Test
    public void setPtcHubInMonitorVerifierShouldUseExplicitAddress() {
        when(monitorVerifierContractEVM.getContractAddress()).thenReturn(MONITOR_VERIFIER_CONTRACT);
        when(ptcContractEvm.getContractAddress()).thenReturn("custom_ptc_hub_contract");

        service.setPtcHubInMonitorVerifier("custom_ptc_hub_contract");

        verify(monitorVerifierContractEVM).setPtcHubAddress("custom_ptc_hub_contract");
    }

    @Test
    public void setPtcHubInMonitorVerifierShouldExtractEvmAddressFromPersistedContext() {
        when(monitorVerifierContractEVM.getContractAddress()).thenReturn(MONITOR_VERIFIER_CONTRACT);
        when(ptcContractEvm.getContractAddress()).thenReturn("ptc_evm_contract");

        service.setPtcHubInMonitorVerifier(
                "{\"evm\":\"ptc_evm_contract\",\"wasm\":\"\"}");

        verify(monitorVerifierContractEVM).setPtcHubAddress("ptc_evm_contract");
    }

    @Test
    public void setPtcHubInMonitorVerifierShouldUseContextPtcHubAddressByDefault() {
        when(monitorVerifierContractEVM.getContractAddress()).thenReturn(MONITOR_VERIFIER_CONTRACT);
        when(ptcContractEvm.getContractAddress()).thenReturn(PTC_HUB_CONTRACT);

        service.setPtcHubInMonitorVerifier("");

        verify(monitorVerifierContractEVM).setPtcHubAddress(PTC_HUB_CONTRACT);
    }

    @Test
    public void setPtcHubInMonitorVerifierShouldRecoverVerifierIdentityAfterRestart() {
        Identity verifierIdentity = mock(Identity.class);
        when(verifierIdentity.hexStrValue()).thenReturn(String.format("%064d", 1));
        when(monitorVerifierContractEVM.getContractAddress()).thenReturn("");
        when(monitorContractClientEVM.getContractAddress()).thenReturn(MONITOR_CONTRACT);
        when(monitorContractClientEVM.getMonitorVerifierIdentity()).thenReturn(verifierIdentity);

        service.setPtcHubInMonitorVerifier(PTC_HUB_CONTRACT);

        verify(monitorVerifierContractEVM).setPtcHubAddress(PTC_HUB_CONTRACT, verifierIdentity);
    }

    @Test(expected = RuntimeException.class)
    public void setPtcHubInMonitorVerifierShouldRejectMissingVerifierContract() {
        when(monitorVerifierContractEVM.getContractAddress()).thenReturn("");

        service.setPtcHubInMonitorVerifier(PTC_HUB_CONTRACT);
    }

    @Test
    public void relayMonitorOrderShouldReturnReceiptFromSendResponse() {
        byte[] rawProof = new byte[]{1, 2};
        byte[] rawMonitorOrder = new byte[]{3, 4};
        byte[] rawTx = new byte[]{5, 6};
        when(monitorContractClientEVM.getContractAddress()).thenReturn(MONITOR_CONTRACT);
        when(monitorContractClientEVM.relayMonitorOrder("committee", "KECCAK256_WITH_SECP256K1", rawProof, rawMonitorOrder))
                .thenReturn(new SendResponseResult("tx_hash", true, true, "0", "", 123456L, rawTx));

        CrossChainMessageReceipt receipt = service.relayMonitorOrder(
                "committee",
                "KECCAK256_WITH_SECP256K1",
                rawProof,
                rawMonitorOrder);

        Assert.assertEquals("tx_hash", receipt.getTxhash());
        Assert.assertTrue(receipt.isConfirmed());
        Assert.assertTrue(receipt.isSuccessful());
        Assert.assertEquals("", receipt.getErrorMsg());
        Assert.assertEquals(123456L, receipt.getTxTimestamp());
        Assert.assertArrayEquals(rawTx, receipt.getRawTx());
    }

    @Test(expected = RuntimeException.class)
    public void relayMonitorOrderShouldRejectMissingMonitorContract() {
        when(monitorContractClientEVM.getContractAddress()).thenReturn("");

        service.relayMonitorOrder("committee", "algo", new byte[]{1}, new byte[]{2});
    }
}
