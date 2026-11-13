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
public class MonitorVerifierContractEVM {

    private static final String MONITOR_VERIFIER_EVM_CONTRACT_PREFIX = "MONITOR_VERIFIER_EVM_CONTRACT_";
    private static final String MONITOR_VERIFIER_EVM_RUNTIME_PATH = "/contract/v1/solidity/MonitorVerifier.bin-runtime";
    private static final String SET_PTC_HUB_ADDRESS_SIGN = "setPtcHubAddress(identity)";
    private static final String GET_IMPLEMENTATION_VERSION_SIGN = "getImplementationVersion()";
    private static final BigInteger SUPPORTED_IMPLEMENTATION_VERSION = BigInteger.valueOf(2L);

    private final Mychain020Client mychain020Client;
    private final Logger logger;

    private String contractAddress;
    private ContractStatusEnum status;

    public MonitorVerifierContractEVM(Mychain020Client mychain020Client, Logger logger) {
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
                mychain020Client.getConfig().getMychainContractBinaryVersion()).getMonitorVerifierEvm();
        if (StrUtil.isEmpty(contractPath)) {
            logger.error("no monitor verifier binary code for this version of contracts");
            return false;
        }

        String contractName = MONITOR_VERIFIER_EVM_CONTRACT_PREFIX + UUID.randomUUID();
        if (mychain020Client.deployContract(contractPath, contractName, VMTypeEnum.EVM, new EVMParameter())) {
            this.contractAddress = contractName;
            this.status = ContractStatusEnum.CONTRACT_DEPLOYED;
            return true;
        }
        return false;
    }

    public boolean ensureImplementationSupported() {
        if (StrUtil.isEmpty(contractAddress)) {
            return false;
        }
        return ensureImplementationSupported(null);
    }

    public boolean ensureImplementationSupported(Identity recoveredIdentity) {
        if (isImplementationVersionSupported(recoveredIdentity)) {
            return true;
        }
        if (StrUtil.isEmpty(contractAddress) && recoveredIdentity == null) {
            logger.error("cannot upgrade an empty monitor verifier contract");
            return false;
        }
        boolean upgraded = recoveredIdentity == null
                ? mychain020Client.upgradeContract(MONITOR_VERIFIER_EVM_RUNTIME_PATH, contractAddress, VMTypeEnum.EVM)
                : mychain020Client.upgradeContract(MONITOR_VERIFIER_EVM_RUNTIME_PATH, recoveredIdentity, VMTypeEnum.EVM);
        if (!upgraded) {
            logger.error("failed to upgrade monitor verifier {}",
                    recoveredIdentity == null ? contractAddress : recoveredIdentity.hexStrValue());
            return false;
        }
        return isImplementationVersionSupported(recoveredIdentity);
    }

    public boolean isImplementationVersionSupported(Identity recoveredIdentity) {
        if (StrUtil.isEmpty(contractAddress) && recoveredIdentity == null) {
            return false;
        }
        try {
            TransactionReceipt receipt = recoveredIdentity == null
                    ? mychain020Client.localCallContract(contractAddress, new EVMParameter(GET_IMPLEMENTATION_VERSION_SIGN))
                    : mychain020Client.localCallContract(recoveredIdentity, new EVMParameter(GET_IMPLEMENTATION_VERSION_SIGN));
            if (ObjectUtil.isEmpty(receipt)
                    || receipt.getResult() != ErrorCode.SUCCESS.getErrorCode()
                    || ObjectUtil.isEmpty(receipt.getOutput())) {
                return false;
            }
            BigInteger version = new EVMOutput(Hex.toHexString(receipt.getOutput())).getUint();
            return version.compareTo(SUPPORTED_IMPLEMENTATION_VERSION) >= 0;
        } catch (Exception e) {
            logger.info("Monitor verifier does not expose a supported implementation version.");
            return false;
        }
    }

    public void setPtcHubAddress(String ptcHubContractName) {
        setPtcHubAddress(ptcHubContractName, null);
    }

    /** Read by the original identity when a persisted context has lost its name. */
    public Identity getPtcHubIdentity(Identity verifierContractIdentity) {
        TransactionReceipt receipt = verifierContractIdentity == null
                ? mychain020Client.localCallContract(contractAddress, new EVMParameter("getPtcHubAddress()"))
                : mychain020Client.localCallContract(verifierContractIdentity, new EVMParameter("getPtcHubAddress()"));
        if (receipt == null || receipt.getResult() != ErrorCode.SUCCESS.getErrorCode()
                || receipt.getOutput() == null || receipt.getOutput().length != 32) {
            throw new CallContractException(contractAddress, "", "monitor verifier PTC hub identity is unavailable");
        }
        return new Identity(new EVMOutput(Hex.toHexString(receipt.getOutput())).getIdentity());
    }

    public void setPtcHubAddress(String ptcHubContractName, Identity verifierContractIdentity) {
        EVMParameter parameters = new EVMParameter(SET_PTC_HUB_ADDRESS_SIGN);
        parameters.addIdentity(Utils.getIdentityByName(ptcHubContractName, mychain020Client.getConfig().getMychainHashType()));

        SendResponseResult result = verifierContractIdentity == null
                ? mychain020Client.callContract(this.contractAddress, parameters, true)
                : mychain020Client.callContract(verifierContractIdentity, parameters, true);
        if (!result.isSuccess()) {
            throw new CallContractException(this.contractAddress, result.getTxId(), result.getErrorMessage());
        }
        this.status = ContractStatusEnum.CONTRACT_READY;
    }

}
