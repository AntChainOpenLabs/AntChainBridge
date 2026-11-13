/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2019 All Rights Reserved.
 */
package com.alipay.antchain.bridge.plugins.mychain020.contract;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.commons.bbc.syscontract.AuthMessageContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.plugins.mychain020.exceptions.CallContractException;
import com.alipay.antchain.bridge.plugins.mychain020.model.CrossChainMsgLedgerData;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.antchain.bridge.plugins.mychain020.utils.MychainUtils;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.domain.block.Block;
import com.alipay.mychain.sdk.domain.transaction.LogEntry;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.message.transaction.TransactionReceiptResponse;
import com.alipay.mychain.sdk.vm.EVMOutput;
import com.alipay.mychain.sdk.vm.WASMParameter;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;

/**
 * @author ccen
 * @version $Id: AMClientContractMY010WASM.java, v 0.1 2019-08-31 12:35 PM ccen Exp $$
 */
public class AMContractClientWASM extends AuthMessageContract  implements AbstractAMContractClient {
    // 合约名称前缀
    public static final String AM_WASM_CONTRACT_PREFIX = "AM_WASM_CONTRACT_";

    // 合约接口名
    public static final String ADD_RELAYERS_WASM_METHOD = "AddRelayers";
    public static final String SET_PROTOCOL_WASM_METHOD = "SetProtocol";
    private static final String SET_PTC_HUB_WASM_METHOD = "SetPtcHub";
    public static final String AM_RECV_PKG_WASM_METHOD = "RecvPkgFromRelayer";

    // 合约跨链事件
    public static final String AM_MSG_SEND_WASM_SIGN_RAW = "SendAuthMessage";
    public static final String AM_MSG_SEND_WASM_SIGN_HEX = Hex.toHexString(AM_MSG_SEND_WASM_SIGN_RAW.getBytes());

    public static final String AM_MSG_RECV_SIGN_HEX = Hex.toHexString("RecvAuthMessage".getBytes());

    protected Mychain020Client mychain020Client;

    private final AtomicInteger stateCounter = new AtomicInteger(0);

    private final Logger logger;

    public AMContractClientWASM(Mychain020Client mychain020Client, Logger logger) {
        this.mychain020Client = mychain020Client;
        this.logger = logger;
    }

    @Override
    public boolean addRelayers(String relayer) {
        WASMParameter parameters = new WASMParameter(ADD_RELAYERS_WASM_METHOD);
        parameters.addIdentity(Utils.getIdentityByName(
                relayer,
                mychain020Client.getConfig().getMychainHashType()));

        return doCallWasmContract(parameters,true).isSuccess();
    }

    /**
     * 设置上层协议信息
     *
     * @param protocolContractName 协议合约地址
     * @param protocolType         协议类型，协议类型为`0`时表示`p2p`协议
     * @return
     */
    @Override
    public boolean setProtocol(String protocolContractName, String protocolType) {
        WASMParameter parameters = new WASMParameter(SET_PROTOCOL_WASM_METHOD);
        parameters.addIdentity(Utils.getIdentityByName(
                protocolContractName,
                mychain020Client.getConfig().getMychainHashType()));
        parameters.addUInt32(
                BigInteger.valueOf(Integer.valueOf(protocolType)));

        if (doCallWasmContract(parameters, true).isSuccess()) {
            if (stateCounter.incrementAndGet() == 2) {
                logger.info("AM contract is ready after set protocol.");
                this.setStatus(ContractStatusEnum.CONTRACT_READY);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void setPtcHub(String ptcHubContractName) {
        WASMParameter parameters = new WASMParameter(SET_PTC_HUB_WASM_METHOD);
        parameters.addIdentity(Utils.getIdentityByName(ptcHubContractName, mychain020Client.getConfig().getMychainHashType()));

        SendResponseResult result = doCallWasmContract(parameters, true);
        if (!result.isSuccess()) {
            throw new CallContractException(getContractAddress(), result.getTxId(), result.getErrorMessage());
        }
        if (stateCounter.incrementAndGet() == 2) {
            logger.info("AM contract is ready after set ptc hub.");
            this.setStatus(ContractStatusEnum.CONTRACT_READY);
        }
    }

    @Override
    public SendResponseResult recvPkgFromRelayer(byte[] pkg) {
        WASMParameter parameters = new WASMParameter(AM_RECV_PKG_WASM_METHOD);
        parameters.addBytes(pkg);

        return doCallWasmContract(parameters, false);
    }

    @Override
    public boolean deployContract() {

        if (StringUtils.isEmpty(this.getContractAddress())) {
            String contractPath = MychainContractBinaryVersionEnum.selectBinaryByVersion(
                    mychain020Client.getConfig().getMychainContractBinaryVersion()).getAmClientWasm();
            String contractName = AM_WASM_CONTRACT_PREFIX + UUID.randomUUID().toString();

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


    protected TransactionReceipt doLocalCallWasmContract(WASMParameter parameters) {
        return mychain020Client.localCallContract(
                this.getContractAddress(),
                parameters);
    }

    @Override
    public List<CrossChainMessage> parseCrossChainMessage(Block block) {
        List<CrossChainMessage> msgList = Lists.newArrayList();

        Identity amContractId = Utils.getIdentityByName(
                this.getContractAddress(),
                mychain020Client.getConfig().getMychainHashType());

        byte[] receiptProof = null;
        // 每个区块里有多个receipt，每个receipt里可能有多个log
        for (int i = 0; i < block.getBlockBody().getReceiptList().size(); i++) {
            TransactionReceipt receipt = block.getBlockBody().getReceiptList().get(i);

            if (receipt.getResult() != 0) {
                logger.info("[notify] paas fail transaction, error code is {}", receipt.getResult());
                continue;
            }

            logger.debug("receipt log size {}", receipt.getLogs().size());

            for (int j = 0; j < receipt.getLogs().size(); j++) {
                LogEntry logEntry = receipt.getLogs().get(j);

                // 判断是否存在 AM 合约相关的消息
                if (!amContractId.hexStrValue()
                        .equals(logEntry.getTo().hexStrValue())) {
                    logger.debug("[notify] no am wasm contract address log. sp:{} - ex:{}",
                            amContractId.hexStrValue(),
                            logEntry.getTo().hexStrValue());
                    continue;
                }

                logger.debug("[notify] am wasm contract address log. {}", logEntry.getTo().hexStrValue());

                if (logEntry.getTopics().isEmpty()) {
                    continue;
                }

                String logSign = logEntry.getTopics().get(0);

                // 判断是否存在 AM 跨链消息
                if (!AM_MSG_SEND_WASM_SIGN_HEX.equals(logSign)) {
                    logger.debug("[notify] no AuthenticMessage log {}. sp: {} - ex:{}]",
                            AM_MSG_SEND_WASM_SIGN_RAW,
                            logSign,
                            AM_MSG_SEND_WASM_SIGN_HEX);
                    continue;
                }

                logger.debug("[notify] AuthenticMessage log. {}]", logSign);

                if (ObjectUtil.isEmpty(receiptProof)) {
                    receiptProof = MychainUtils.getReceiptProof(
                            block,
                            mychain020Client.getConfig().getMychainHashType()
                    );
                }

                msgList.add(CrossChainMessage.createCrossChainMessage(
                        CrossChainMessage.CrossChainMessageType.AUTH_MSG,
                        block.getBlockHeader().getNumber().longValue(),
                        block.getBlockHeader().getTimestamp(),
                        block.getBlockHeader().getHash().getValue(),
                        // evm 和 wasm 合约的不同之处
                        new EVMOutput(logEntry.getTopics().get(1)).getBytes(),
                        CrossChainMsgLedgerData.builder()
                                .receipt(receipt)
                                .logIndex(j)
                                .receiptIndex(i)
                                .logTopicHex(logSign)
                                .amContractIdHex(amContractId.hexStrValue())
                                .build()
                                .encode(),
                        receiptProof,
                        block.getBlockBody().getTransactionList().get(i).getHash().getValue()
                ));
            }
        }

        return msgList;
    }

}