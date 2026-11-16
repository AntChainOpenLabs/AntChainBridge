package com.alipay.antchain.bridge.plugins.mychain020.sdp;

import java.math.BigInteger;
import java.util.UUID;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.plugins.mychain020.common.BizContractTypeEnum;
import com.alipay.antchain.bridge.plugins.mychain020.common.SDPMsgTypeEnum;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.vm.WASMOutput;
import com.alipay.mychain.sdk.vm.WASMParameter;

public class ReceiverWasm extends AbstractDemoReceiverContract {

    public static final String BYTE_CODE_PATH = "/contracts/demo_v2/receiver.wasc";

    public ReceiverWasm(String domainName, Mychain020Client mychain020Client, String sdpContractName) {
        this.setDomain(domainName);
        this.setMychain020Client(mychain020Client);
        this.setContractName("ReceiverWasm_" + UUID.randomUUID());
        this.setSdpContractName(sdpContractName);
        if (!deployOrUpgradeContract()) {
            throw new RuntimeException("Failed to setup ReceiverWasm.");
        }
    }

    @Override
    public BizContractTypeEnum getBizContractType() {
        return BizContractTypeEnum.WASM;
    }

    @Override
    public boolean setRecvSequence(String senderDomain, Identity senderContractID, int recvSeq) {
        String method = "SetMsgSeq";
        WASMParameter parameters = new WASMParameter(method);
        parameters.addString(senderDomain);
        parameters.addIdentity(senderContractID);
        parameters.addIdentity(getContractId());
        parameters.addUInt32(BigInteger.valueOf(recvSeq));
        return this.getMychain020Client().callContract(this.getSdpContractName(), parameters, true).isSuccess();
    }

    @Override
    public String getLastMsg(SDPMsgTypeEnum sdpType) {
        WASMParameter parameters = new WASMParameter(sdpType.getWasmMethodToGetLastMsg());
        TransactionReceipt receipt = this.getMychain020Client().localCallContract(this.getContractName(), parameters);
        if (ObjectUtil.isNull(receipt.getOutput())) {
            return null;
        }

        WASMOutput wasmOutput = new WASMOutput(HexUtil.encodeHexStr(receipt.getOutput()));
        return wasmOutput.getString();
    }

    private boolean deployOrUpgradeContract() {
        return this.getMychain020Client().deployContract(BYTE_CODE_PATH, getContractName(), VMTypeEnum.WASM, new WASMParameter()) ||
                this.getMychain020Client().upgradeContract(BYTE_CODE_PATH, getContractName(), VMTypeEnum.WASM);
    }
}
