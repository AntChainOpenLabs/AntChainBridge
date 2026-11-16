package com.alipay.antchain.bridge.plugins.mychain020.contract;

import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.crypto.hash.Hash;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class MonitorPtcBindingGetterTest {
    private TransactionReceipt receipt(long result, byte[] output) {
        TransactionReceipt r=mock(TransactionReceipt.class);
        when(r.getResult()).thenReturn(result);when(r.getOutput()).thenReturn(output);return r;
    }
    @Test public void ptcGetterAcceptsZeroAndRawIdentityWithoutWriting() {
        Mychain020Client c=mock(Mychain020Client.class);byte[] raw=new byte[32];raw[0]=(byte)0xab;
        TransactionReceipt empty=receipt(0,new byte[32]), bound=receipt(0,raw);
        when(c.localCallContract(eq("hub"),any())).thenReturn(empty,bound);
        PtcContractEvm p=new PtcContractEvm(c,mock(Logger.class));p.setContractAddress("hub");
        Assert.assertTrue(p.getMonitorVerifierIdentity().hexStrValue().matches("0+"));
        Assert.assertArrayEquals(raw,p.getMonitorVerifierIdentity().getValue());
        verify(c,never()).callContract(anyString(),any(),anyBoolean());
    }
    @Test public void verifierGetterUsesRecoveredRawIdentityWithoutHashing() {
        Mychain020Client c=mock(Mychain020Client.class);Identity identity=new Identity(new Hash(new byte[32]));
        byte[] raw=new byte[32];raw[31]=1;
        TransactionReceipt bound=receipt(0,raw);
        when(c.localCallContract(same(identity),any())).thenReturn(bound);
        MonitorVerifierContractEVM v=new MonitorVerifierContractEVM(c,mock(Logger.class));
        Assert.assertArrayEquals(raw,v.getPtcHubIdentity(identity).getValue());
        verify(c).localCallContract(same(identity),any());verify(c,never()).localCallContract(anyString(),any());
        verify(c,never()).callContract(any(Identity.class),any(),anyBoolean());
    }
    @Test public void verifierGetterUsesNameAndAcceptsZeroWithoutWriting() {
        Mychain020Client c=mock(Mychain020Client.class);
        TransactionReceipt empty=receipt(0,new byte[32]);
        when(c.localCallContract(eq("verifier"),any())).thenReturn(empty);
        MonitorVerifierContractEVM v=new MonitorVerifierContractEVM(c,mock(Logger.class));v.setContractAddress("verifier");
        Assert.assertTrue(v.getPtcHubIdentity(null).hexStrValue().matches("0+"));
        verify(c,never()).callContract(anyString(),any(),anyBoolean());
    }
    @Test public void invalidGetterResponsesNeverBecomeUnsetBindings() {
        for(TransactionReceipt r:new TransactionReceipt[]{null,receipt(99,new byte[32]),receipt(0,null),receipt(0,new byte[0]),receipt(0,new byte[31]),receipt(0,new byte[33])}) {
            Mychain020Client c=mock(Mychain020Client.class);when(c.localCallContract(anyString(),any())).thenReturn(r);
            PtcContractEvm p=new PtcContractEvm(c,mock(Logger.class));p.setContractAddress("hub");
            MonitorVerifierContractEVM v=new MonitorVerifierContractEVM(c,mock(Logger.class));v.setContractAddress("verifier");
            MonitorContractClientEVM m=new MonitorContractClientEVM(c,mock(Logger.class));m.setContractAddress("monitor");
            try {p.getMonitorVerifierIdentity();Assert.fail("invalid PTC getter response accepted");}catch(RuntimeException expected){}
            try {v.getPtcHubIdentity(null);Assert.fail("invalid verifier getter response accepted");}catch(RuntimeException expected){}
            try {m.getMonitorVerifierIdentityOrZero();Assert.fail("invalid monitor getter response accepted");}catch(RuntimeException expected){}
            verify(c,never()).callContract(anyString(),any(),anyBoolean());
        }
    }
    @Test public void rpcExceptionsRemainFailures() {
        Mychain020Client c=mock(Mychain020Client.class);when(c.localCallContract(anyString(),any())).thenThrow(new IllegalStateException("RPC offline"));
        PtcContractEvm p=new PtcContractEvm(c,mock(Logger.class));p.setContractAddress("hub");
        MonitorVerifierContractEVM v=new MonitorVerifierContractEVM(c,mock(Logger.class));v.setContractAddress("verifier");
        try {p.getMonitorVerifierIdentity();Assert.fail();}catch(IllegalStateException expected){Assert.assertEquals("RPC offline",expected.getMessage());}
        try {v.getPtcHubIdentity(null);Assert.fail();}catch(IllegalStateException expected){Assert.assertEquals("RPC offline",expected.getMessage());}
    }
    @Test public void monitorZeroReadIsDistinctFromRecoveryOfAnExistingVerifier() {
        Mychain020Client c=mock(Mychain020Client.class);TransactionReceipt empty=receipt(0,new byte[32]);
        when(c.localCallContract(eq("monitor"),any())).thenReturn(empty);
        MonitorContractClientEVM m=new MonitorContractClientEVM(c,mock(Logger.class));m.setContractAddress("monitor");
        Assert.assertTrue(m.getMonitorVerifierIdentityOrZero().hexStrValue().matches("0+"));
        try {m.getMonitorVerifierIdentity();Assert.fail("zero verifier recovered");}catch(RuntimeException expected){}
        verify(c,never()).callContract(anyString(),any(),anyBoolean());
    }
}
