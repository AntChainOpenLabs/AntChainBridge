/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2019 All Rights Reserved.
 */
package com.alipay.antchain.bridge.plugins.mychain.contract;

import java.util.UUID;

import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.plugins.mychain.sdk.Mychain010Client;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.message.transaction.TransactionReceiptResponse;
import com.alipay.mychain.sdk.vm.WASMParameter;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;

@Getter
@Setter
public class SDPContractClientTeeWASM extends SDPContractClientWASM {
    private static final String SDP_TEE_WASM_CONTRACT_PREFIX = "SDP_TEE_WASM_CONTRACT_";

    private String filePathAMP2PMsgClientContract = "";

    public SDPContractClientTeeWASM(Mychain010Client mychain010Client, Logger logger) {
        super(mychain010Client, logger);
    }

    @Override
    public boolean deployContract() {
        if (StrUtil.isEmpty(this.getContractAddress())) {

            String contractPath = MychainContractBinaryVersionEnum.selectBinaryByVersion(
                    mychain010Client.getConfig().getMychainContractBinaryVersion()).getSdpTeeWasm();
            String contractName = SDP_TEE_WASM_CONTRACT_PREFIX + UUID.randomUUID().toString();

            if (mychain010Client.deployContract(
                    contractPath,
                    contractName,
                    VMTypeEnum.WASM,
                    new WASMParameter("init"))) {
                this.setContractAddress(contractName);
                this.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
                return true;
            } else {
                return false;
            }
        }

        return true;
    }

    @Override
    protected SendResponseResult doCallWasmContract(WASMParameter parameters, boolean sync) {
        return mychain010Client.callTeeWasmContract(
                this.getContractAddress(),
                parameters,
                sync);
    }


    @Override
    protected TransactionReceipt doLocalCallContract(WASMParameter parameters) {
        return mychain010Client.localCallTeeWasmContract(
                this.getContractAddress(),
                parameters);
    }

}