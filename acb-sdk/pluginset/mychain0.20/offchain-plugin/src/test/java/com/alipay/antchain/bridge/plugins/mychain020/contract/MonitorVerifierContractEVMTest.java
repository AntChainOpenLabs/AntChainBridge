package com.alipay.antchain.bridge.plugins.mychain020.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;

public class MonitorVerifierContractEVMTest {

    @Test
    public void shouldUpgradeLegacyVerifierInPlace() {
        Mychain020Client client = mock(Mychain020Client.class);
        TransactionReceipt legacy = mock(TransactionReceipt.class);
        TransactionReceipt current = mock(TransactionReceipt.class);
        when(legacy.getResult()).thenReturn(10201L);
        when(current.getResult()).thenReturn(0L);
        byte[] encodedVersion = new byte[32];
        encodedVersion[31] = 2;
        when(current.getOutput()).thenReturn(encodedVersion);
        when(client.localCallContract(eq("monitor_verifier"), any()))
                .thenReturn(legacy, current);
        when(client.upgradeContract(
                "/contract/v1/solidity/MonitorVerifier.bin-runtime",
                "monitor_verifier",
                VMTypeEnum.EVM)).thenReturn(true);

        MonitorVerifierContractEVM verifier = new MonitorVerifierContractEVM(client, mock(Logger.class));
        verifier.setContractAddress("monitor_verifier");

        Assert.assertTrue(verifier.ensureImplementationSupported());
        verify(client).upgradeContract(
                "/contract/v1/solidity/MonitorVerifier.bin-runtime",
                "monitor_verifier",
                VMTypeEnum.EVM);
    }

    @Test
    public void shouldReuseCurrentVerifierAndPackageRuntime() {
        Mychain020Client client = mock(Mychain020Client.class);
        TransactionReceipt current = mock(TransactionReceipt.class);
        when(current.getResult()).thenReturn(0L);
        byte[] encodedVersion = new byte[32];
        encodedVersion[31] = 2;
        when(current.getOutput()).thenReturn(encodedVersion);
        when(client.localCallContract(eq("monitor_verifier"), any())).thenReturn(current);

        MonitorVerifierContractEVM verifier = new MonitorVerifierContractEVM(client, mock(Logger.class));
        verifier.setContractAddress("monitor_verifier");

        Assert.assertTrue(verifier.ensureImplementationSupported());
        verify(client, never()).upgradeContract(any(String.class), any(String.class), any(VMTypeEnum.class));
        Assert.assertNotNull(MonitorVerifierContractEVM.class.getResourceAsStream(
                "/contract/v1/solidity/MonitorVerifier.bin-runtime"));
    }
}
