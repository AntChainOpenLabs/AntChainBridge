package com.alipay.antchain.bridge.plugins.mychain020.sdp;

import cn.hutool.core.util.HexUtil;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.plugins.mychain020.common.BizContractTypeEnum;
import com.alipay.antchain.bridge.plugins.mychain020.common.SDPMsgTypeEnum;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.vm.WASMOutput;
import com.alipay.mychain.sdk.vm.WASMParameter;

import java.util.Objects;
import java.util.UUID;

public class SenderWasm extends AbstractDemoSenderContract {

    public static final String BYTE_CODE_PATH = "/contracts/demo_v2/sender.wasc";

    public SenderWasm(String domainName, Mychain020Client mychain020Client, String sdpContractName) {
        this.setDomain(domainName);
        this.setMychain020Client(mychain020Client);
        this.setContractName("SenderWasm_" + UUID.randomUUID());
        this.setSdpContractName(sdpContractName);
        if (!deployOrUpgradeContract() || !setSDPMSGAddress()) {
            throw new RuntimeException("Failed to setup ReceiverWasm.");
        }
    }

    @Override
    public BizContractTypeEnum getBizContractType() {
        return BizContractTypeEnum.WASM;
    }

    @Override
    public SendResponseResult sendMsgV1To(String receiverDomain, Identity receiverContractID, String msg, SDPMsgTypeEnum sdpType) {
        WASMParameter parameters = new WASMParameter(sdpType.getWasmMethodToSendV1Msg());
        parameters.addString(receiverDomain);
        parameters.addString(receiverContractID.hexStrValue());
        parameters.addString(msg);
        return this.getMychain020Client().callContract(getContractName(), parameters, true);
    }

    @Override
    public boolean sendMsgV2To(String receiverDomain, Identity receiverContractID, String msg, boolean isAtomic, SDPMsgTypeEnum sdpType) {
        WASMParameter parameters = new WASMParameter(sdpType.getWasmMethodToSendV2Msg());
        parameters.addString(receiverDomain);
        parameters.addString(receiverContractID.hexStrValue());
        parameters.addBool(isAtomic);
        parameters.addString(msg);
        return this.getMychain020Client().callContract(getContractName(), parameters, true).isSuccess();
    }

    @Override
    public String getLatestMsgIdSentUnorder() {
        TransactionReceipt receipt = Objects.requireNonNull(
                this.getMychain020Client().localCallContract(
                        getContractName(),
                        new WASMParameter(SDPMsgTypeEnum.UNORDERED.getWasmMethodToGetLastMsgIdSent())
                )
        );
        return new WASMOutput(HexUtil.encodeHexStr(receipt.getOutput())).getString();
    }

    @Override
    public String getLatestMsgIdSentOrder() {
        TransactionReceipt receipt = Objects.requireNonNull(
                this.getMychain020Client().localCallContract(
                        getContractName(),
                        new WASMParameter(SDPMsgTypeEnum.ORDERED.getWasmMethodToGetLastMsgIdSent())
                )
        );
        return new WASMOutput(HexUtil.encodeHexStr(receipt.getOutput())).getString();
    }

    @Override
    public String getLatestMsgIdAckSuccess() {
        TransactionReceipt receipt = Objects.requireNonNull(
                this.getMychain020Client().localCallContract(
                        getContractName(),
                        new WASMParameter("GetLatestMsgIdAckSuccess")
                )
        );
        return new WASMOutput(HexUtil.encodeHexStr(receipt.getOutput())).getString();
    }

    @Override
    public String getLatestMsgIdAckError() {
        TransactionReceipt receipt = Objects.requireNonNull(
                this.getMychain020Client().localCallContract(
                        getContractName(),
                        new WASMParameter("GetLatestMsgIdAckError")
                )
        );
        return new WASMOutput(HexUtil.encodeHexStr(receipt.getOutput())).getString();
    }

    @Override
    public String getLatestMsgError() {
        TransactionReceipt receipt = Objects.requireNonNull(
                this.getMychain020Client().localCallContract(
                        getContractName(),
                        new WASMParameter("GetLatestMsgError")
                )
        );
        return new WASMOutput(HexUtil.encodeHexStr(receipt.getOutput())).getString();
    }

    private boolean deployOrUpgradeContract() {
        return this.getMychain020Client().deployContract(BYTE_CODE_PATH, getContractName(), VMTypeEnum.WASM, new WASMParameter()) ||
                this.getMychain020Client().upgradeContract(BYTE_CODE_PATH, getContractName(), VMTypeEnum.WASM);
    }

    private boolean setSDPMSGAddress() {
        String method = "SetSDPMSGAddress";
        WASMParameter parameters = new WASMParameter(method);
        parameters.addIdentity(Utils.getIdentityByName(getSdpContractName()));
        return this.getMychain020Client().callContract(this.getContractName(), parameters, true).isSuccess();
    }
}
