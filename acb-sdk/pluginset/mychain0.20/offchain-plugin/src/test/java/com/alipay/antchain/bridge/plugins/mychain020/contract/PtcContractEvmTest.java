package com.alipay.antchain.bridge.plugins.mychain020.contract;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.errorcode.ErrorCode;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;

public class PtcContractEvmTest {

    @Test
    public void shouldRestoreReadyStatusForPredeployedContract() {
        PtcContractEvm contract = new PtcContractEvm(
                mock(Mychain020Client.class),
                mock(Logger.class));
        contract.setContractAddress("PTC_HUB_EVM_CONTRACT_predeployed");
        contract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);

        Assert.assertTrue(contract.deployContract("unused-for-predeployed-contract"));
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, contract.getStatus());
    }

    @Test
    public void shouldUpgradeLegacyPtcHubAndVerifyMonitorSupport() {
        Mychain020Client client = mock(Mychain020Client.class);
        TransactionReceipt legacyReceipt = mock(TransactionReceipt.class);
        TransactionReceipt upgradedReceipt = mock(TransactionReceipt.class);
        when(legacyReceipt.getResult()).thenReturn(10201L);
        when(upgradedReceipt.getResult()).thenReturn((long) ErrorCode.SUCCESS.getErrorCode());
        when(upgradedReceipt.getOutput()).thenReturn(new byte[] {3});
        when(client.localCallContract(eq("legacy_ptc_hub"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(legacyReceipt, upgradedReceipt);
        when(client.upgradeContract(
                "/contract/v1/solidity/PtcHub.bin-runtime",
                "legacy_ptc_hub",
                VMTypeEnum.EVM)).thenReturn(true);

        PtcContractEvm contract = new PtcContractEvm(client, mock(Logger.class));
        contract.setContractAddress("legacy_ptc_hub");

        Assert.assertTrue(contract.ensureMonitorVerifierSupport());
        verify(client).upgradeContract(
                "/contract/v1/solidity/PtcHub.bin-runtime",
                "legacy_ptc_hub",
                VMTypeEnum.EVM);
    }

    @Test
    public void shouldNotUpgradePtcHubThatAlreadySupportsMonitor() {
        Mychain020Client client = mock(Mychain020Client.class);
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getResult()).thenReturn((long) ErrorCode.SUCCESS.getErrorCode());
        when(receipt.getOutput()).thenReturn(new byte[] {3});
        when(client.localCallContract(eq("current_ptc_hub"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(receipt);

        PtcContractEvm contract = new PtcContractEvm(client, mock(Logger.class));
        contract.setContractAddress("current_ptc_hub");

        Assert.assertTrue(contract.ensureMonitorVerifierSupport());
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
        when(client.localCallContract(eq("unreachable_ptc_hub"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(receipt);

        PtcContractEvm contract = new PtcContractEvm(client, mock(Logger.class));
        contract.setContractAddress("unreachable_ptc_hub");

        try {
            contract.ensureMonitorVerifierSupport();
        } finally {
            verify(client, org.mockito.Mockito.never()).upgradeContract(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.any(VMTypeEnum.class));
        }
    }

    @Test
    public void shouldPackageMonitorEnabledPtcRuntime() {
        Assert.assertNotNull(PtcContractEvm.class.getResourceAsStream(
                "/contract/v1/solidity/PtcHub.bin-runtime"));
    }

    @Test
    public void shouldUpgradeLegacyPtcHubForRootReconciliation() {
        Mychain020Client client = mock(Mychain020Client.class);
        TransactionReceipt legacyReceipt = mock(TransactionReceipt.class);
        TransactionReceipt upgradedReceipt = mock(TransactionReceipt.class);
        when(legacyReceipt.getResult()).thenReturn(10201L);
        when(upgradedReceipt.getResult()).thenReturn((long) ErrorCode.SUCCESS.getErrorCode());
        when(upgradedReceipt.getOutput()).thenReturn(new byte[] {2});
        when(client.localCallContract(eq("legacy_root_ptc_hub"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(legacyReceipt, upgradedReceipt);
        when(client.upgradeContract(
                "/contract/v1/solidity/PtcHub.bin-runtime",
                "legacy_root_ptc_hub",
                VMTypeEnum.EVM)).thenReturn(true);

        PtcContractEvm contract = new PtcContractEvm(client, mock(Logger.class));
        contract.setContractAddress("legacy_root_ptc_hub");

        Assert.assertTrue(contract.ensureRootReconciliationSupport());
        verify(client).upgradeContract(
                "/contract/v1/solidity/PtcHub.bin-runtime",
                "legacy_root_ptc_hub",
                VMTypeEnum.EVM);
    }

    @Test
    public void shouldKeepCurrentPtcHubForRootReconciliation() {
        Mychain020Client client = mock(Mychain020Client.class);
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getResult()).thenReturn((long) ErrorCode.SUCCESS.getErrorCode());
        when(receipt.getOutput()).thenReturn(new byte[] {2});
        when(client.localCallContract(eq("current_root_ptc_hub"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(receipt);

        PtcContractEvm contract = new PtcContractEvm(client, mock(Logger.class));
        contract.setContractAddress("current_root_ptc_hub");

        Assert.assertTrue(contract.ensureRootReconciliationSupport());
        verify(client, org.mockito.Mockito.never()).upgradeContract(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(VMTypeEnum.class));
    }
}
