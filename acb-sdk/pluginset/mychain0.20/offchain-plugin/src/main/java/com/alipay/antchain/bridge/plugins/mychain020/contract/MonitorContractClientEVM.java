package com.alipay.antchain.bridge.plugins.mychain020.contract;

import java.math.BigInteger;
import java.util.UUID;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.plugins.mychain020.exceptions.CallContractException;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.crypto.hash.Hash;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.errorcode.ErrorCode;
import com.alipay.mychain.sdk.vm.EVMOutput;
import com.alipay.mychain.sdk.vm.EVMParameter;
import org.bouncycastle.util.encoders.Hex;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;

@Getter
@Setter
public class MonitorContractClientEVM {

    public static final int SUPPORTED_IMPLEMENTATION_VERSION = 9;

    private static final String MONITOR_EVM_CONTRACT_PREFIX = "MONITOR_EVM_CONTRACT_";
    private static final String MONITOR_EVM_RUNTIME_PATH = "/contract/v1/solidity/Monitor.bin-runtime";
    private static final String GET_IMPLEMENTATION_VERSION_SIGN = "getImplementationVersion()";
    private static final String GET_MONITOR_VERIFIER_SIGN = "getMonitorVerifier()";
    private static final String SET_SDP_ADDRESS_SIGN = "setSdpAddress(identity)";
    private static final String SET_MONITOR_VERIFIER_ADDRESS_SIGN = "setMonitorVerifierAddress(identity)";
    private static final String SET_MONITOR_CONTROL_SIGN = "setMonitorControl(uint32)";
    private static final String RECV_MONITOR_ORDER_SIGN = "recvMonitorOrder(string,string,bytes,bytes)";

    private final Mychain020Client mychain020Client;
    private final Logger logger;

    private String contractAddress;
    private ContractStatusEnum status;

    public MonitorContractClientEVM(Mychain020Client mychain020Client, Logger logger) {
        this.mychain020Client = mychain020Client;
        this.logger = logger;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public void setContractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
    }

    public ContractStatusEnum getStatus() {
        return status;
    }

    public void setStatus(ContractStatusEnum status) {
        this.status = status;
    }

    public boolean deployContract() {
        if (StrUtil.isNotEmpty(this.contractAddress)) {
            return true;
        }

        String contractPath = MychainContractBinaryVersionEnum.selectBinaryByVersion(
                mychain020Client.getConfig().getMychainContractBinaryVersion()).getMonitorEvm();
        if (StrUtil.isEmpty(contractPath)) {
            logger.error("no monitor binary code for this version of contracts");
            return false;
        }

        String contractName = MONITOR_EVM_CONTRACT_PREFIX + UUID.randomUUID();
        if (mychain020Client.deployContract(contractPath, contractName, VMTypeEnum.EVM, new EVMParameter())) {
            this.contractAddress = contractName;
            this.status = ContractStatusEnum.CONTRACT_DEPLOYED;
            return true;
        }
        return false;
    }

    public boolean isImplementationVersionSupported() {
        if (StrUtil.isEmpty(this.contractAddress)) {
            return false;
        }

        try {
            TransactionReceipt receipt = mychain020Client.localCallContract(
                    this.contractAddress,
                    new EVMParameter(GET_IMPLEMENTATION_VERSION_SIGN));
            if (ObjectUtil.isEmpty(receipt)
                    || receipt.getResult() != ErrorCode.SUCCESS.getErrorCode()
                    || ObjectUtil.isEmpty(receipt.getOutput())) {
                return false;
            }

            BigInteger version = new EVMOutput(Hex.toHexString(receipt.getOutput())).getUint();
            return BigInteger.valueOf(SUPPORTED_IMPLEMENTATION_VERSION).equals(version);
        } catch (Exception e) {
            logger.info("Monitor contract {} does not expose a supported implementation version.",
                    this.contractAddress);
            return false;
        }
    }

    /**
     * Upgrade a legacy Monitor in place so that its identity, verifier,
     * protocol wiring and policy state are preserved. Monitor V4 only appends
     * receive-side entry points and does not change the existing storage
     * layout. Monitor V5 additionally routes SDP V2/V3 request and ACK paths
     * through the same monitor envelope used by V1, and exposes matching
     * receive-side entry points for all three SDP versions. Monitor V6 replaces
     * the compiler-sensitive assembly decoder so non-word-aligned business
     * payloads are preserved byte-for-byte on Mychain. Monitor V7 verifies the
     * monitor-node proof before delivery and restores Ethereum3-compatible
     * rollback/error handling. Monitor V8 consumes a message-bound, one-use
     * monitor proof result. Monitor V9 removes the V1 unordered auxiliary send
     * event and avoids temporary encoding objects while preserving wire bytes.
     */
    public boolean ensureImplementationSupported() {
        if (isImplementationVersionSupported()) {
            return true;
        }
        if (StrUtil.isEmpty(this.contractAddress)) {
            logger.error("cannot upgrade an empty monitor contract");
            return false;
        }

        logger.info(
                "Upgrade legacy Monitor {} with runtime {}",
                this.contractAddress,
                MONITOR_EVM_RUNTIME_PATH);
        if (!mychain020Client.upgradeContract(
                MONITOR_EVM_RUNTIME_PATH,
                this.contractAddress,
                VMTypeEnum.EVM)) {
            logger.error("failed to upgrade legacy Monitor {}", this.contractAddress);
            return false;
        }

        boolean supported = isImplementationVersionSupported();
        if (!supported) {
            logger.error("Monitor {} is still unsupported after upgrade", this.contractAddress);
        }
        return supported;
    }

    public Identity getMonitorVerifierIdentity() {
        Identity identity = getMonitorVerifierIdentityOrZero();
        if (identity.hexStrValue().matches("(?i)(?:0x)?0+")) {
            throw new CallContractException(
                    this.contractAddress,
                    "",
                    "monitor verifier identity is not configured");
        }
        return identity;
    }

    /** Distinguish a successful unset-binding read from an RPC/ABI failure. */
    public Identity getMonitorVerifierIdentityOrZero() {
        TransactionReceipt receipt = mychain020Client.localCallContract(
                this.contractAddress,
                new EVMParameter(GET_MONITOR_VERIFIER_SIGN));
        if (ObjectUtil.isEmpty(receipt)
                || receipt.getResult() != ErrorCode.SUCCESS.getErrorCode()
                || ObjectUtil.isEmpty(receipt.getOutput())
                || receipt.getOutput().length != 32) {
            throw new CallContractException(
                    this.contractAddress,
                    "",
                    "monitor verifier identity is unavailable");
        }
        return new Identity(new Hash(receipt.getOutput()));
    }

    public void setProtocol(String sdpContractName) {
        EVMParameter parameters = new EVMParameter(SET_SDP_ADDRESS_SIGN);
        parameters.addIdentity(Utils.getIdentityByName(sdpContractName, mychain020Client.getConfig().getMychainHashType()));
        call(parameters);
    }

    public void setMonitorVerifier(String monitorVerifierContractName) {
        EVMParameter parameters = new EVMParameter(SET_MONITOR_VERIFIER_ADDRESS_SIGN);
        parameters.addIdentity(Utils.getIdentityByName(monitorVerifierContractName, mychain020Client.getConfig().getMychainHashType()));
        call(parameters);
    }

    public void setMonitorVerifier(Identity monitorVerifierIdentity) {
        EVMParameter parameters = new EVMParameter(SET_MONITOR_VERIFIER_ADDRESS_SIGN);
        parameters.addIdentity(monitorVerifierIdentity);
        call(parameters);
    }

    public void setMonitorControl(int monitorType) {
        EVMParameter parameters = new EVMParameter(SET_MONITOR_CONTROL_SIGN);
        parameters.addUint(BigInteger.valueOf(monitorType));
        call(parameters);
        this.status = ContractStatusEnum.CONTRACT_READY;
    }

    public SendResponseResult relayMonitorOrder(String committeeId, String signAlgo, byte[] rawProof, byte[] rawMonitorOrder) {
        EVMParameter parameters = new EVMParameter(RECV_MONITOR_ORDER_SIGN);
        parameters.addString(committeeId);
        parameters.addString(signAlgo);
        parameters.addBytes(rawProof);
        parameters.addBytes(rawMonitorOrder);

        return mychain020Client.callContract(this.contractAddress, parameters, true);
    }

    private void call(EVMParameter parameters) {
        SendResponseResult result = mychain020Client.callContract(this.contractAddress, parameters, true);
        if (!result.isSuccess()) {
            throw new CallContractException(this.contractAddress, result.getTxId(), result.getErrorMessage());
        }
    }
}
