package com.alipay.antchain.bridge.plugins.mychain020.contract;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.errorcode.ErrorCode;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;

public class SDPContractClientEVMTest {

    @Test
    public void shouldUpgradeLegacySdpAndVerifyMonitorSupport() {
        Mychain020Client client = mock(Mychain020Client.class);
        TransactionReceipt legacyReceipt = mock(TransactionReceipt.class);
        TransactionReceipt upgradedReceipt = mock(TransactionReceipt.class);
        when(legacyReceipt.getResult()).thenReturn(10201L);
        when(upgradedReceipt.getResult()).thenReturn((long) ErrorCode.SUCCESS.getErrorCode());
        when(upgradedReceipt.getOutput()).thenReturn(uint256(4));
        when(client.localCallContract(eq("legacy_sdp"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(legacyReceipt, upgradedReceipt);
        when(client.upgradeContract(
                "/contract/v1/solidity/SDPMsg.bin-runtime",
                "legacy_sdp",
                VMTypeEnum.EVM)).thenReturn(true);

        SDPContractClientEVM contract = new SDPContractClientEVM(client, mock(Logger.class));
        contract.setContractAddress("legacy_sdp");

        Assert.assertTrue(contract.ensureMonitorSupport());
        verify(client).upgradeContract(
                "/contract/v1/solidity/SDPMsg.bin-runtime",
                "legacy_sdp",
                VMTypeEnum.EVM);
    }

    @Test
    public void shouldNotUpgradeSdpThatAlreadySupportsMonitor() {
        Mychain020Client client = mock(Mychain020Client.class);
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getResult()).thenReturn((long) ErrorCode.SUCCESS.getErrorCode());
        when(receipt.getOutput()).thenReturn(uint256(4));
        when(client.localCallContract(eq("current_sdp"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(receipt);

        SDPContractClientEVM contract = new SDPContractClientEVM(client, mock(Logger.class));
        contract.setContractAddress("current_sdp");

        Assert.assertTrue(contract.ensureMonitorSupport());
        verify(client, org.mockito.Mockito.never()).upgradeContract(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(VMTypeEnum.class));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldNotUpgradeWhenCapabilityProbeFailsUnexpectedly() {
        Mychain020Client client = mock(Mychain020Client.class);
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getResult()).thenReturn(99L);
        when(client.localCallContract(eq("unreachable_sdp"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(receipt);

        SDPContractClientEVM contract = new SDPContractClientEVM(client, mock(Logger.class));
        contract.setContractAddress("unreachable_sdp");

        try {
            contract.ensureMonitorSupport();
        } finally {
            verify(client, org.mockito.Mockito.never()).upgradeContract(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.any(VMTypeEnum.class));
        }
    }

    @Test
    public void shouldPackageMonitorEnabledSdpRuntime() {
        Assert.assertNotNull(SDPContractClientEVM.class.getResourceAsStream(
                "/contract/v1/solidity/SDPMsg.bin-runtime"));
    }

    private static byte[] uint256(int value) {
        byte[] encoded = new byte[32];
        encoded[31] = (byte) value;
        return encoded;
    }
}
