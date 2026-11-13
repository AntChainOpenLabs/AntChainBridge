package com.alipay.antchain.bridge.plugins.mychain020;

import cn.hutool.core.util.ReflectUtil;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.plugins.mychain020.contract.*;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Config;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.crypto.hash.Hash;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import org.junit.*;
import org.slf4j.LoggerFactory;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Stateful offline simulation of the public BBC initialization sequence. */
public class Mychain020MonitorPtcBindingTest {
    private final Mychain020Config config = new Mychain020Config();
    private Mychain020BBCService service;
    private Mychain020BBCContext context;
    private Mychain020Client client;
    private MonitorContractClientEVM monitor;
    private MonitorVerifierContractEVM verifier;
    private PtcContractEvm ptc;
    private String monitorName, verifierName, ptcName;
    private Identity monitorVerifier, verifierHub, hubVerifier;
    private int hubWrites, verifierWrites, verifierDeploys;
    private boolean failGetter, failWrite, wrongReadBack;

    private Identity zero() { return new Identity(new Hash(new byte[32])); }
    private Identity id(String name) { return Utils.getIdentityByName(name, config.getMychainHashType()); }
    private void assertBound(String name) {
        Assert.assertEquals(id(name).hexStrValue(), verifierHub.hexStrValue());
        Assert.assertEquals(id("verifier").hexStrValue(), hubVerifier.hexStrValue());
    }
    private void failure(String message, Runnable operation) {
        try { operation.run(); Assert.fail("expected failure: " + message); }
        catch (RuntimeException e) {
            Throwable t = e;
            while (t.getCause() != null) t = t.getCause();
            Assert.assertTrue(t.toString(), t.toString().contains(message));
        }
    }

    @Before public void initialize() {
        monitorName=""; verifierName=""; ptcName="";
        monitorVerifier=zero();verifierHub=zero();hubVerifier=zero();
        client=mock(Mychain020Client.class);
        when(client.getConfig()).thenReturn(config);
        when(client.getPrimary()).thenReturn("offline-only");
        monitor=mock(MonitorContractClientEVM.class, invocation -> {
            switch(invocation.getMethod().getName()) {
                case "getContractAddress": return monitorName;
                case "deployContract": monitorName="monitor";return true;
                case "ensureImplementationSupported": return true;
                case "getStatus": return ContractStatusEnum.CONTRACT_DEPLOYED;
                case "getMonitorVerifierIdentity":case "getMonitorVerifierIdentityOrZero": return monitorVerifier;
                case "setMonitorVerifier":
                    Object v=invocation.getArgument(0);monitorVerifier=v instanceof Identity?(Identity)v:id((String)v);return null;
                default:return RETURNS_DEFAULTS.answer(invocation);
            }
        });
        // Dispatch by name keeps the public-API regression runnable with the old JAR.
        verifier=mock(MonitorVerifierContractEVM.class, invocation -> {
            switch(invocation.getMethod().getName()) {
                case "getContractAddress":return verifierName;
                case "deployContract":verifierName="verifier";verifierDeploys++;return true;
                case "ensureImplementationSupported":return true;
                case "getPtcHubIdentity":
                    if(failGetter)throw new IllegalStateException("offline getter failure");
                    return verifierHub;
                case "setPtcHubAddress":
                    if(failWrite)throw new IllegalStateException("offline write failure");
                    verifierWrites++;if(!wrongReadBack)verifierHub=id(invocation.getArgument(0));return null;
                default:return RETURNS_DEFAULTS.answer(invocation);
            }
        });
        ptc=mock(PtcContractEvm.class, invocation -> {
            switch(invocation.getMethod().getName()) {
                case "getContractAddress":return ptcName;
                case "deployContract":ptcName="hub";return true;
                case "getStatus":return ContractStatusEnum.CONTRACT_READY;
                case "reconcileRootBcdnsCert":case "ensureCommitteeVerifierMonitorSupport":case "ensureMonitorProofRoutingSupport":return true;
                case "getMonitorVerifierIdentity":return hubVerifier;
                case "setMonitorVerifier":
                    if(failWrite)throw new IllegalStateException("offline write failure");
                    hubWrites++;Object v=invocation.getArgument(0);if(!wrongReadBack)hubVerifier=v instanceof Identity?(Identity)v:id((String)v);return null;
                default:return RETURNS_DEFAULTS.answer(invocation);
            }
        });
        context=new Mychain020BBCContext(new DefaultBBCContext(), LoggerFactory.getLogger(getClass()));
        context.setMonitorContractClientEVM(monitor);context.setMonitorVerifierContractEVM(verifier);
        context.setPtcContractEvm(ptc);context.setSdpContractClientEVM(mock(SDPContractClientEVM.class));
        service=new Mychain020BBCService();ReflectUtil.setFieldValue(service,"context",context);ReflectUtil.setFieldValue(service,"mychain020Client",client);
    }

    @Test public void monitorThenPtcThenFinalBindingMustBindBothDirections() {
        service.setupMonitorContract();service.setupPTCContract();service.setPtcHubInMonitorVerifier("hub");
        assertBound("hub");Assert.assertEquals(1,hubWrites);Assert.assertEquals(1,verifierWrites);
    }
    @Test public void ptcThenMonitorMustBindBothDirections() {
        service.setupPTCContract();service.setupMonitorContract();assertBound("hub");
    }
    @Test public void recoverUnnamedVerifierAndMissingPtcBindingWithoutDeployment() {
        monitorName="monitor";ptcName="hub";monitorVerifier=id("verifier");verifierHub=id("hub");
        service.setPtcHubInMonitorVerifier("hub");assertBound("hub");
        Assert.assertEquals(0,verifierDeploys);Assert.assertEquals(0,verifierWrites);Assert.assertEquals(1,hubWrites);
        verify(ptc).setMonitorVerifier(same(monitorVerifier));
        Assert.assertNull(config.getMonitorVerifierContractName());
    }
    @Test public void repeatedInitializationMustNotRepeatBindingTransactions() {
        service.setupMonitorContract();service.setupPTCContract();service.setupMonitorContract();service.setupPTCContract();
        service.setPtcHubInMonitorVerifier("");assertBound("hub");Assert.assertEquals(1,hubWrites);Assert.assertEquals(1,verifierWrites);
    }
    @Test public void correctBindingsAreReadWithoutWrites() {
        ptcName="hub";verifierName="verifier";hubVerifier=id("verifier");verifierHub=id("hub");
        service.setPtcHubInMonitorVerifier("hub");Assert.assertEquals(0,hubWrites+verifierWrites);
    }
    @Test public void conflictingHubBindingMustFailBeforeAnyWrite() {
        ptcName="hub";verifierName="verifier";verifierHub=id("different-hub");
        failure("conflicting nonzero binding",()->service.setPtcHubInMonitorVerifier("hub"));Assert.assertEquals(0,hubWrites+verifierWrites);
    }
    @Test public void conflictingVerifierBindingMustFailBeforeAnyWrite() {
        ptcName="hub";verifierName="verifier";hubVerifier=id("different-verifier");
        failure("conflicting nonzero binding",()->service.setPtcHubInMonitorVerifier("hub"));Assert.assertEquals(0,hubWrites+verifierWrites);
    }
    @Test public void getterFailureMustNotBecomeZeroBinding() {
        ptcName="hub";verifierName="verifier";failGetter=true;
        failure("offline getter failure",()->service.setPtcHubInMonitorVerifier("hub"));Assert.assertEquals(0,hubWrites+verifierWrites);
    }
    @Test public void nullGetterMustStopBeforeWrites() {
        ptcName="hub";verifierName="verifier";hubVerifier=null;
        failure("no identity",()->service.setPtcHubInMonitorVerifier("hub"));Assert.assertEquals(0,hubWrites+verifierWrites);
    }
    @Test public void zeroRecoveredVerifierMustStopBeforeWrites() {
        monitorName="monitor";ptcName="hub";
        failure("not configured",()->service.setPtcHubInMonitorVerifier("hub"));Assert.assertEquals(0,hubWrites+verifierWrites);
    }
    @Test public void settingFailureMustNotReportInitializationSuccess() {
        ptcName="hub";verifierName="verifier";failWrite=true;
        failure("offline write failure",()->service.setPtcHubInMonitorVerifier("hub"));
    }
    @Test public void failedReadBackMustStopInitialization() {
        ptcName="hub";verifierName="verifier";wrongReadBack=true;
        failure("read-back mismatch",()->service.setPtcHubInMonitorVerifier("hub"));
    }
    @Test public void partialBindingCanBeCompletedByExplicitRetry() {
        ptcName="hub";verifierName="verifier";failWrite=true;verifierHub=id("hub");
        failure("offline write failure",()->service.setPtcHubInMonitorVerifier("hub"));
        Assert.assertEquals(id("hub").hexStrValue(),verifierHub.hexStrValue());
        failWrite=false;service.setPtcHubInMonitorVerifier("hub");assertBound("hub");Assert.assertEquals(0,verifierWrites);
    }
    @Test public void ptcSetupFailureMustNotPublishReadyContext() {
        monitorName="monitor";monitorVerifier=id("verifier");verifierName="verifier";failGetter=true;
        failure("offline getter failure",()->service.setupPTCContract());Assert.assertNull(context.getPtcContract());
    }
    @Test public void monitorGetterFailureMustNotDeployReplacementEvenWithoutPtc() {
        monitorName="monitor";
        when(monitor.getMonitorVerifierIdentityOrZero()).thenThrow(new IllegalStateException("monitor RPC failure"));
        failure("monitor RPC failure",()->service.setupMonitorContract());Assert.assertEquals(0,verifierDeploys);
        Assert.assertEquals(0,hubWrites+verifierWrites);
    }
    @Test public void monitorBindingConflictMustNotBeOverwritten() {
        monitorName="monitor";verifierName="verifier";monitorVerifier=id("different-verifier");
        failure("conflicting nonzero binding",()->service.setupMonitorContract());
        Assert.assertEquals(id("different-verifier").hexStrValue(),monitorVerifier.hexStrValue());
        Assert.assertEquals(0,hubWrites+verifierWrites);
    }
    @Test public void explicitJsonHubSelectsBothReadAndWriteTarget() {
        ptcName="old-hub";verifierName="verifier";
        TransactionReceipt receipt=mock(TransactionReceipt.class);when(receipt.getResult()).thenReturn(0L);
        when(receipt.getOutput()).thenAnswer(i->hubVerifier.getValue());
        when(client.localCallContract(eq("selected-hub"),any())).thenReturn(receipt);
        when(client.callContract(eq("selected-hub"),any(),eq(true))).thenAnswer(i->{hubWrites++;hubVerifier=id("verifier");return new SendResponseResult("offline",true,true,"0","",0L,new byte[0]);});
        service.setPtcHubInMonitorVerifier("{\"evm\":\"selected-hub\",\"wasm\":\"ignored\"}");assertBound("selected-hub");
        verify(ptc,never()).setMonitorVerifier(any(Identity.class));verify(client,never()).callContract(eq("old-hub"),any(),eq(true));
    }
}
