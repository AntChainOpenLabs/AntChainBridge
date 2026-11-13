package com.alipay.antchain.bridge.plugins.mychain020.contract;

import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Config;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.crypto.hash.HashTypeEnum;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AMContractClientEVMTest {

    private static final String AM_CONTRACT = "am_contract";
    private static final String SDP_CONTRACT = "sdp_contract";

    @Test
    public void setProtocolShouldReuseExistingRoute() {
        Mychain020Client mychain020Client = mock(Mychain020Client.class);
        Mychain020Config config = mock(Mychain020Config.class);
        TransactionReceipt receipt = mock(TransactionReceipt.class);

        when(mychain020Client.getConfig()).thenReturn(config);
        when(config.getMychainHashType()).thenReturn(HashTypeEnum.SHA256);
        when(receipt.getOutput()).thenReturn(Hex.decode(
                Utils.getIdentityByName(SDP_CONTRACT, HashTypeEnum.SHA256).hexStrValue()));
        when(mychain020Client.localCallContract(eq(AM_CONTRACT), any())).thenReturn(receipt);

        AMContractClientEVM contract = new AMContractClientEVM(
                mychain020Client,
                LoggerFactory.getLogger(getClass()));
        contract.setContractAddress(AM_CONTRACT);

        Assert.assertTrue(contract.setProtocol(SDP_CONTRACT, "0"));
        verify(mychain020Client, never()).callContract(eq(AM_CONTRACT), any(), eq(true));
    }

    @Test
    public void setProtocolShouldWriteWhenRouteDoesNotMatch() {
        Mychain020Client mychain020Client = mock(Mychain020Client.class);
        Mychain020Config config = mock(Mychain020Config.class);
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        SendResponseResult result = mock(SendResponseResult.class);

        when(mychain020Client.getConfig()).thenReturn(config);
        when(config.getMychainHashType()).thenReturn(HashTypeEnum.SHA256);
        when(receipt.getOutput()).thenReturn(new byte[32]);
        when(mychain020Client.localCallContract(eq(AM_CONTRACT), any())).thenReturn(receipt);
        when(result.isSuccess()).thenReturn(true);
        when(mychain020Client.callContract(eq(AM_CONTRACT), any(), eq(true))).thenReturn(result);

        AMContractClientEVM contract = new AMContractClientEVM(
                mychain020Client,
                LoggerFactory.getLogger(getClass()));
        contract.setContractAddress(AM_CONTRACT);

        Assert.assertTrue(contract.setProtocol(SDP_CONTRACT, "0"));
        verify(mychain020Client).callContract(eq(AM_CONTRACT), any(), eq(true));
    }
}
