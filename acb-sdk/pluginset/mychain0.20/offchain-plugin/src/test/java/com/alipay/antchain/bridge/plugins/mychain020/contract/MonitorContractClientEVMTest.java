package com.alipay.antchain.bridge.plugins.mychain020.contract;

import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MonitorContractClientEVMTest {

    private static final String MONITOR_CONTRACT = "monitor_contract";

    private Mychain020Client mychain020Client;
    private MonitorContractClientEVM monitorContractClient;

    @Before
    public void setUp() {
        mychain020Client = mock(Mychain020Client.class);
        monitorContractClient = new MonitorContractClientEVM(
                mychain020Client,
                LoggerFactory.getLogger(getClass()));
        monitorContractClient.setContractAddress(MONITOR_CONTRACT);
        monitorContractClient.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
    }

    @Test
    public void implementationVersionNineShouldBeSupported() {
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        byte[] encodedVersion = new byte[32];
        encodedVersion[31] = 9;

        when(receipt.getResult()).thenReturn(0L);
        when(receipt.getOutput()).thenReturn(encodedVersion);
        when(mychain020Client.localCallContract(eq(MONITOR_CONTRACT), any())).thenReturn(receipt);

        Assert.assertTrue(monitorContractClient.isImplementationVersionSupported());
    }

    @Test
    public void versionNineShouldNotBeUpgradedAgain() {
        TransactionReceipt receipt = versionReceipt(9);
        when(mychain020Client.localCallContract(eq(MONITOR_CONTRACT), any()))
                .thenReturn(receipt);

        Assert.assertTrue(monitorContractClient.ensureImplementationSupported());
        verify(mychain020Client, never()).upgradeContract(
                any(String.class), any(String.class), any(com.alipay.mychain.sdk.common.VMTypeEnum.class));
    }

    @Test
    public void oldAndUnknownVersionsShouldNotPassTheExactVersionCheck() {
        for (int version : new int[]{7, 8, 10}) {
            TransactionReceipt receipt = versionReceipt(version);
            when(mychain020Client.localCallContract(eq(MONITOR_CONTRACT), any()))
                    .thenReturn(receipt);

            Assert.assertFalse("Unexpected supported version: " + version,
                    monitorContractClient.isImplementationVersionSupported());
        }
        verify(mychain020Client, never()).upgradeContract(
                any(String.class), any(String.class), any(com.alipay.mychain.sdk.common.VMTypeEnum.class));
    }

    @Test
    public void legacyContractWithoutVersionMethodShouldNotBeSupported() {
        when(mychain020Client.localCallContract(eq(MONITOR_CONTRACT), any()))
                .thenThrow(new RuntimeException("method not found"));

        Assert.assertFalse(monitorContractClient.isImplementationVersionSupported());
    }

    @Test
    public void legacyContractShouldBeUpgradedInPlace() {
        TransactionReceipt legacyReceipt = mock(TransactionReceipt.class);
        when(legacyReceipt.getResult()).thenReturn(10201L);

        TransactionReceipt upgradedReceipt = mock(TransactionReceipt.class);
        byte[] encodedVersion = new byte[32];
        encodedVersion[31] = 9;
        when(upgradedReceipt.getResult()).thenReturn(0L);
        when(upgradedReceipt.getOutput()).thenReturn(encodedVersion);

        when(mychain020Client.localCallContract(eq(MONITOR_CONTRACT), any()))
                .thenReturn(legacyReceipt, upgradedReceipt);
        when(mychain020Client.upgradeContract(any(String.class), eq(MONITOR_CONTRACT), eq(com.alipay.mychain.sdk.common.VMTypeEnum.EVM)))
                .thenReturn(true);

        Assert.assertTrue(monitorContractClient.ensureImplementationSupported());

        Assert.assertEquals(MONITOR_CONTRACT, monitorContractClient.getContractAddress());
        verify(mychain020Client, times(1)).upgradeContract(
                eq("/contract/v1/solidity/Monitor.bin-runtime"),
                eq(MONITOR_CONTRACT),
                eq(com.alipay.mychain.sdk.common.VMTypeEnum.EVM));
    }

    @Test
    public void versionSevenShouldUpgradeToNineWithoutChangingTheContractIdentity() {
        TransactionReceipt legacyReceipt = versionReceipt(7);
        TransactionReceipt upgradedReceipt = versionReceipt(9);
        when(mychain020Client.localCallContract(eq(MONITOR_CONTRACT), any()))
                .thenReturn(legacyReceipt, upgradedReceipt);
        when(mychain020Client.upgradeContract(any(String.class), eq(MONITOR_CONTRACT),
                eq(com.alipay.mychain.sdk.common.VMTypeEnum.EVM))).thenReturn(true);

        Assert.assertTrue(monitorContractClient.ensureImplementationSupported());
        Assert.assertEquals(MONITOR_CONTRACT, monitorContractClient.getContractAddress());
        verify(mychain020Client, times(1)).upgradeContract(
                eq("/contract/v1/solidity/Monitor.bin-runtime"), eq(MONITOR_CONTRACT),
                eq(com.alipay.mychain.sdk.common.VMTypeEnum.EVM));
    }

    @Test
    public void upgradeWhoseReadbackStillReturnsSevenShouldFail() {
        TransactionReceipt receipt = versionReceipt(7);
        when(mychain020Client.localCallContract(eq(MONITOR_CONTRACT), any()))
                .thenReturn(receipt);
        when(mychain020Client.upgradeContract(any(String.class), eq(MONITOR_CONTRACT),
                eq(com.alipay.mychain.sdk.common.VMTypeEnum.EVM))).thenReturn(true);

        Assert.assertFalse(monitorContractClient.ensureImplementationSupported());
        verify(mychain020Client, times(2)).localCallContract(eq(MONITOR_CONTRACT), any());
        verify(mychain020Client, times(1)).upgradeContract(
                eq("/contract/v1/solidity/Monitor.bin-runtime"), eq(MONITOR_CONTRACT),
                eq(com.alipay.mychain.sdk.common.VMTypeEnum.EVM));
    }

    @Test
    public void failedUpgradeShouldNotBeReportedAsSupported() {
        TransactionReceipt receipt = versionReceipt(7);
        when(mychain020Client.localCallContract(eq(MONITOR_CONTRACT), any()))
                .thenReturn(receipt);
        when(mychain020Client.upgradeContract(any(String.class), eq(MONITOR_CONTRACT),
                eq(com.alipay.mychain.sdk.common.VMTypeEnum.EVM))).thenReturn(false);

        Assert.assertFalse(monitorContractClient.ensureImplementationSupported());
        verify(mychain020Client, times(1)).localCallContract(eq(MONITOR_CONTRACT), any());
        verify(mychain020Client, times(1)).upgradeContract(
                eq("/contract/v1/solidity/Monitor.bin-runtime"), eq(MONITOR_CONTRACT),
                eq(com.alipay.mychain.sdk.common.VMTypeEnum.EVM));
    }

    private TransactionReceipt versionReceipt(int version) {
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        byte[] encodedVersion = new byte[32];
        encodedVersion[31] = (byte) version;
        when(receipt.getResult()).thenReturn(0L);
        when(receipt.getOutput()).thenReturn(encodedVersion);
        return receipt;
    }
}
