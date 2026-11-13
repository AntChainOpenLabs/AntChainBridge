package com.alipay.antchain.bridge.plugins.mychain020.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.crypto.hash.Hash;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;

public class CommitteePtcVerifierContractEvmTest {

    @Test
    public void shouldUpgradeRecoveredVerifierIdentityInPlace() {
        Mychain020Client client = mock(Mychain020Client.class);
        Identity identity = new Identity(new Hash(new byte[32]));
        TransactionReceipt legacy = mock(TransactionReceipt.class);
        TransactionReceipt current = mock(TransactionReceipt.class);
        when(legacy.getResult()).thenReturn(10201L);
        when(current.getResult()).thenReturn(0L);
        byte[] encodedVersion = new byte[32];
        encodedVersion[31] = 2;
        when(current.getOutput()).thenReturn(encodedVersion);
        when(client.localCallContract(eq(identity), any())).thenReturn(legacy, current);
        when(client.upgradeContract(
                "/contract/v1/solidity/CommitteePtcVerifier.bin-runtime",
                identity,
                VMTypeEnum.EVM)).thenReturn(true);

        CommitteePtcVerifierContractEvm verifier =
                new CommitteePtcVerifierContractEvm(client, mock(Logger.class));

        Assert.assertTrue(verifier.ensureImplementationSupported(identity));
        verify(client).upgradeContract(
                "/contract/v1/solidity/CommitteePtcVerifier.bin-runtime",
                identity,
                VMTypeEnum.EVM);
        Assert.assertNotNull(CommitteePtcVerifierContractEvm.class.getResourceAsStream(
                "/contract/v1/solidity/CommitteePtcVerifier.bin-runtime"));
    }
}
