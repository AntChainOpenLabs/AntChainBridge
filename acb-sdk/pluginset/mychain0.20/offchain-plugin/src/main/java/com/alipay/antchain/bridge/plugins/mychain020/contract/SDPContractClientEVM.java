package com.alipay.antchain.bridge.plugins.mychain020.contract;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.UUID;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
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
import com.alipay.mychain.sdk.errorcode.ErrorCode;
import com.alipay.mychain.sdk.vm.EVMOutput;
import com.alipay.mychain.sdk.vm.EVMParameter;
import lombok.Getter;
import lombok.Setter;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;

@Getter
@Setter
public class SDPContractClientEVM extends SDPContract implements AbstractSDPContractClient {

    private static final String SDP_EVM_CONTRACT_PREFIX = "SDP_EVM_CONTRACT_";

    private static final String SDP_EVM_RUNTIME_PATH = "/contract/v1/solidity/SDPMsg.bin-runtime";
    public static final int SUPPORTED_MONITOR_ROUTING_VERSION = 4;

    private static final String SET_AM_CONTRACT_AND_DOMAIN_SIGN = "SetAmContractAndDomain(identity,string)";
    private static final String SET_MONITOR_CONTRACT_SIGN = "setMonitorContract(identity)";
    private static final String GET_MONITOR_ROUTING_VERSION_SIGN = "getMonitorRoutingVersion()";
    private static final String RECV_OFF_CHAIN_EXCEPTION_SIGN = "recvOffChainException(bytes32,bytes)";
    private static final String UPDATE_VERIFIED_INFO_SIGN = "updateVerifiedInfo(string,bytes32,uint64,uint64)";
    private static final String QUERY_VALIDATED_BLOCK_STATE_BY_DOMAIN_SIGN = "queryValidatedBlockStateByDomain(string)";
    private static final String QUERY_SDP_SEQ_SIGN = "querySDPMessageSeq(string,bytes32,string,bytes32)";

    private static final String BLOCK_STATE_CLS = "(uint16,string,bytes32,uint256,uint64)";

    private static final long LEGACY_METHOD_NOT_FOUND_RESULT = 10201L;

    private Mychain020Client mychain020Client;

    private String localDomain;

    private final Logger logger;

    public SDPContractClientEVM(Mychain020Client mychain020Client, Logger logger) {
        this.mychain020Client = mychain020Client;
        this.logger = logger;
    }

    public String getLocalDomain() {
        return localDomain;
    }

    public void setLocalDomain(String localDomain) {
        this.localDomain = localDomain;
    }

    public void setMonitorContract(String monitorContractName) {
        EVMParameter parameters = new EVMParameter(SET_MONITOR_CONTRACT_SIGN);
        parameters.addIdentity(Utils.getIdentityByName(
                monitorContractName,
                mychain020Client.getConfig().getMychainHashType()));

        if (!mychain020Client.callContract(this.getContractAddress(), parameters, true).isSuccess()) {
            throw new RuntimeException(StrUtil.format(
                    "failed to set monitor contract {} into sdp {}",
                    monitorContractName,
                    this.getContractAddress()));
        }
    }

    /**
     * Upgrade an SDP contract deployed before regulatory monitor support was
     * added. The monitor address is appended after every legacy storage field
     * in the regulatory contract, so the existing domain, sequence and block
     * state data remain at their original slots.
     */
    public boolean ensureMonitorSupport() {
        if (isMonitorSupported()) {
            return true;
        }
        if (StrUtil.isEmpty(this.getContractAddress())) {
            logger.error("cannot upgrade an empty sdp contract");
            return false;
        }

        logger.info(
                "Upgrade legacy SDP {} with monitor-enabled runtime {}",
                this.getContractAddress(),
                SDP_EVM_RUNTIME_PATH);
        if (!mychain020Client.upgradeContract(
                SDP_EVM_RUNTIME_PATH,
                this.getContractAddress(),
                VMTypeEnum.EVM)) {
            logger.error("failed to upgrade legacy sdp {}", this.getContractAddress());
            return false;
        }

        boolean supported = isMonitorSupported();
        if (!supported) {
            logger.error("sdp {} still lacks monitor support after upgrade", this.getContractAddress());
        }
        return supported;
    }

    public boolean isMonitorSupported() {
        if (StrUtil.isEmpty(this.getContractAddress())) {
            return false;
        }

        try {
            TransactionReceipt receipt = mychain020Client.localCallContract(
                    this.getContractAddress(),
                    new EVMParameter(GET_MONITOR_ROUTING_VERSION_SIGN));
            if (ObjectUtil.isEmpty(receipt)) {
                throw new IllegalStateException("empty sdp capability probe receipt");
            }
            if (receipt.getResult() == LEGACY_METHOD_NOT_FOUND_RESULT) {
                return false;
            }
            if (receipt.getResult() != ErrorCode.SUCCESS.getErrorCode()
                    || ObjectUtil.isEmpty(receipt.getOutput())) {
                throw new IllegalStateException(
                        StrUtil.format(
                                "unexpected sdp capability probe result: {}",
                                receipt.getResult()));
            }
            BigInteger version = new EVMOutput(Hex.toHexString(receipt.getOutput())).getUint();
            return BigInteger.valueOf(SUPPORTED_MONITOR_ROUTING_VERSION).equals(version);
        } catch (Exception e) {
            throw new IllegalStateException(
                    StrUtil.format(
                            "failed to probe monitor support on sdp {}",
                            this.getContractAddress()),
                    e);
        }
    }

    @Override
    public boolean setAmContractAndDomain(String amContractName) {
        EVMParameter parameters = new EVMParameter(SET_AM_CONTRACT_AND_DOMAIN_SIGN);
        parameters.addIdentity(Utils.getIdentityByName(
                amContractName,
                mychain020Client.getConfig().getMychainHashType()));
        parameters.addString(localDomain);

        if (mychain020Client.callContract(
                        this.getContractAddress(),
                        parameters,
                        true)
                .isSuccess()) {
            this.setStatus(ContractStatusEnum.CONTRACT_READY);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public SendResponseResult recvOffChainException(String exceptionMsgAuthor, byte[] exceptionMsgPkg) {
        EVMParameter parameters = new EVMParameter(RECV_OFF_CHAIN_EXCEPTION_SIGN);
        parameters.addBytes32(HexUtil.decodeHex(exceptionMsgAuthor));
        parameters.addBytes(exceptionMsgPkg);

        return mychain020Client.callContract(this.getContractAddress(), parameters, true);
    }

    @Override
    public BlockState queryValidatedBlockStateByDomain(String receiverDomain) {
        EVMParameter parameters = new EVMParameter(QUERY_VALIDATED_BLOCK_STATE_BY_DOMAIN_SIGN);
        parameters.addString(receiverDomain);

        TransactionReceipt receipt = mychain020Client.localCallContract(this.getContractAddress(), parameters);
        if (null == receipt.getOutput()) {
            return new BlockState();
        }

        EVMOutput evmOutput = new EVMOutput(Hex.toHexString(receipt.getOutput()));
        ArrayList<Object> bsList = (ArrayList<Object>) evmOutput.getObject(BLOCK_STATE_CLS);

        return new BlockState(
                ((BigInteger) bsList.get(0)).shortValue(),
                new CrossChainDomain((String) bsList.get(1)),
                (byte[]) bsList.get(2),
                ((BigInteger) bsList.get(3)),
                ((BigInteger) bsList.get(4)).longValue()
        );
    }

    @Override
    public long queryP2PMsgSeqOnChain(String senderDomain,
                                      String from,
                                      String receiverDomain,
                                      String to) {
        EVMParameter parameters = new EVMParameter(QUERY_SDP_SEQ_SIGN);
        parameters.addString(senderDomain);
        parameters.addBytes32(new Hash(from).getValue());
        parameters.addString(receiverDomain);
        parameters.addBytes32(new Hash(to).getValue());

        // TODO: handle errors
        TransactionReceipt receipt = mychain020Client.localCallContract(
                this.getContractAddress(),
                parameters);
        if (null == receipt.getOutput()) {
            return 0;
        }

        EVMOutput evmOutput = new EVMOutput(Hex.toHexString(receipt.getOutput()));
        int value = evmOutput.getInt().intValue();
        return (long) value;
    }

    @Override
    public boolean deployContract() {
        if (StrUtil.isEmpty(this.getContractAddress())) {

            String contractPath = MychainContractBinaryVersionEnum.selectBinaryByVersion(
                    mychain020Client.getConfig().getMychainContractBinaryVersion()).getSdpEvm();
            String contractName = SDP_EVM_CONTRACT_PREFIX + UUID.randomUUID().toString();

            if (mychain020Client.deployContract(
                    contractPath,
                    contractName,
                    VMTypeEnum.EVM,
                    new EVMParameter())) {
                this.setContractAddress(contractName);
                this.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
                return true;
            } else {
                return false;
            }
        }

        return true;
    }

}
