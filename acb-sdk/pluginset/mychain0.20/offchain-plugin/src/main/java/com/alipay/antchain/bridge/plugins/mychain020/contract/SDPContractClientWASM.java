/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2019 All Rights Reserved.
 */
package com.alipay.antchain.bridge.plugins.mychain020.contract;

import java.math.BigInteger;
import java.util.UUID;

import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.bbc.syscontract.SDPContract;
import com.alipay.antchain.bridge.commons.core.base.BlockState;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.crypto.hash.Hash;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.message.transaction.TransactionReceiptResponse;
import com.alipay.mychain.sdk.vm.WASMOutput;
import com.alipay.mychain.sdk.vm.WASMParameter;
import lombok.Getter;
import lombok.Setter;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;

@Getter
@Setter
public class SDPContractClientWASM extends SDPContract implements AbstractSDPContractClient {
    private static final String SDP_WASM_CONTRACT_PREFIX = "SDP_WASM_CONTRACT_";
    private static final String SET_AM_CONTRACT_AND_DOMAIN_WASM_SIGN = "SetAmContractAndDomain";
    private static final String RECV_OFF_CHAIN_EXCEPTION_WASM_SIGN = "RecvOffChainException";  // todo:ly
    private static final String QUERY_VALIDATED_BLOCK_STATE_BY_DOMAIN_WASM_SIGN = "QueryValidatedBlockStateByDomain";    // todo:ly
    private static final String QUERY_SDP_SEQ_WASM_SIGN = "QueryP2PMsgSeqOnChain";

    protected Mychain020Client mychain020Client;

    private String localDomain;

    private final Logger logger;

    public SDPContractClientWASM(Mychain020Client mychain020Client, Logger logger) {
        this.mychain020Client = mychain020Client;
        this.logger = logger;
    }

    @Override
    public boolean setAmContractAndDomain(String amContractName) {
        WASMParameter parameters = new WASMParameter(SET_AM_CONTRACT_AND_DOMAIN_WASM_SIGN);
        parameters.addIdentity(Utils.getIdentityByName(
                amContractName,
                mychain020Client.getConfig().getMychainHashType()));
        parameters.addString(localDomain);

        if (doCallWasmContract(parameters, true).isSuccess()) {
            this.setStatus(ContractStatusEnum.CONTRACT_READY);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public SendResponseResult recvOffChainException(String exceptionMsgAuthor, byte[] exceptionMsgPkg) {
        WASMParameter parameters = new WASMParameter(RECV_OFF_CHAIN_EXCEPTION_WASM_SIGN);
        parameters.addString(exceptionMsgAuthor);
        parameters.addBytes(exceptionMsgPkg);

        return doCallWasmContract(parameters, false);
    }

    @Override
    public BlockState queryValidatedBlockStateByDomain(String receiverDomain){
        WASMParameter parameters = new WASMParameter(QUERY_VALIDATED_BLOCK_STATE_BY_DOMAIN_WASM_SIGN);
        parameters.addString(receiverDomain);

        TransactionReceipt receipt = doLocalCallContract(parameters);
        WASMOutput output = new WASMOutput(Hex.toHexString(receipt.getOutput()));

        return new BlockState(
                output.getUint16().shortValue(),
                new CrossChainDomain(output.getString()),
                output.getBytes(),
                output.getUint128(),    // todo:ly 取256位
                output.getUint64().longValue()
        );
    }

    @Override
    public long queryP2PMsgSeqOnChain(String senderDomain,
                                      String from,
                                      String receiverDomain,
                                      String to) {
        WASMParameter parameters = new WASMParameter(QUERY_SDP_SEQ_WASM_SIGN);
        parameters.addString(senderDomain);
        parameters.addIdentity(new Hash(from));
        parameters.addString(receiverDomain);
        parameters.addIdentity(new Hash(to));

        // TODO: handle errors
        TransactionReceipt receipt = doLocalCallContract(parameters);
        WASMOutput output = new WASMOutput(Hex.toHexString(receipt.getOutput()));
        return output.getUint32().intValue();
    }

    @Override
    public boolean deployContract() {
        if (StrUtil.isEmpty(this.getContractAddress())) {

            String contractPath = MychainContractBinaryVersionEnum.selectBinaryByVersion(
                    mychain020Client.getConfig().getMychainContractBinaryVersion()).getSdpWasm();
            String contractName = SDP_WASM_CONTRACT_PREFIX + UUID.randomUUID().toString();

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

    protected SendResponseResult doCallWasmContract(WASMParameter parameters, boolean sync) {
        return mychain020Client.callContract(
                this.getContractAddress(),
                parameters,
                sync);
    }


    protected TransactionReceipt doLocalCallContract(WASMParameter parameters) {
        return mychain020Client.localCallContract(
                this.getContractAddress(),
                parameters);
    }
}