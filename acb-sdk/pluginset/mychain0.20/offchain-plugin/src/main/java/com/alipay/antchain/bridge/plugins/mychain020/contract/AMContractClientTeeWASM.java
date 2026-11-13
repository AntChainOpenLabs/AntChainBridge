/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2019 All Rights Reserved.
 */
package com.alipay.antchain.bridge.plugins.mychain020.contract;

import java.util.UUID;

import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.message.transaction.TransactionReceiptResponse;
import com.alipay.mychain.sdk.vm.WASMParameter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

/**
 * @author hanghang.whh
 */
public class AMContractClientTeeWASM extends AMContractClientWASM {

    private static final String AM_TEE_WASM_CONTRACT_PREFIX = "AM_TEE_WASM_CONTRACT_";

    public AMContractClientTeeWASM(Mychain020Client mychain020Client, Logger logger) {
        super(mychain020Client, logger);
    }

    @Override
    public boolean deployContract() {
        // 如果am client已部署，就不需要重新部署
        if (StringUtils.isEmpty(this.getContractAddress())) {

            String contractPath = MychainContractBinaryVersionEnum.selectBinaryByVersion(
                            mychain020Client.getConfig().getMychainContractBinaryVersion()).getAmClientTeeWasm();
            String contractName = AM_TEE_WASM_CONTRACT_PREFIX + UUID.randomUUID().toString();

            if (mychain020Client.deployContract(
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
        return mychain020Client.callTeeWasmContract(
                this.getContractAddress(),
                parameters,
                sync);
    }

    @Override
    protected TransactionReceipt doLocalCallWasmContract(WASMParameter parameters) {
        return mychain020Client.localCallTeeWasmContract(
                this.getContractAddress(),
                parameters);
    }

}