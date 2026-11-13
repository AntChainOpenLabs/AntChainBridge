package com.alipay.antchain.bridge.plugins.mychain020.sdk;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.bcdns.CrossChainCertificateTypeEnum;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.plugins.mychain020.contract.AMContractClientEVM;
import com.alipay.antchain.bridge.plugins.mychain020.contract.AMContractClientWASM;
import com.alipay.antchain.bridge.plugins.mychain020.crypto.CryptoSuiteEnum;
import com.alipay.antchain.bridge.plugins.mychain020.model.ConsensusNodeInfo;
import com.alipay.antchain.bridge.plugins.mychain020.model.ContractAddressInfo;
import com.alipay.antchain.bridge.plugins.mychain020.utils.MychainUtils;
import com.alipay.mychain.sdk.api.MychainClient;
import com.alipay.mychain.sdk.api.env.ClientEnv;
import com.alipay.mychain.sdk.api.utils.ConfidentialUtil;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.common.Parameters;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.crypto.PublicKey;
import com.alipay.mychain.sdk.crypto.hash.Hash;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.domain.block.Block;
import com.alipay.mychain.sdk.domain.block.BlockBody;
import com.alipay.mychain.sdk.domain.block.BlockHeader;
import com.alipay.mychain.sdk.domain.contract.Contract;
import com.alipay.mychain.sdk.domain.spv.BlockHeaderInfo;
import com.alipay.mychain.sdk.domain.transaction.LogEntry;
import com.alipay.mychain.sdk.domain.transaction.Transaction;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.errorcode.ErrorCode;
import com.alipay.mychain.sdk.message.query.QueryAccountResponse;
import com.alipay.mychain.sdk.message.query.QueryContractResponse;
import com.alipay.mychain.sdk.message.query.QueryLastBlockHeaderResponse;
import com.alipay.mychain.sdk.message.query.QueryTransactionReceiptResponse;
import com.alipay.mychain.sdk.message.spv.QueryBlockBodiesResponse;
import com.alipay.mychain.sdk.message.spv.QueryBlockReceiptsResponse;
import com.alipay.mychain.sdk.message.status.QueryContractConfigStatusResponse;
import com.alipay.mychain.sdk.message.transaction.AbstractTransactionRequest;
import com.alipay.mychain.sdk.message.transaction.TransactionReceiptResponse;
import com.alipay.mychain.sdk.message.transaction.confidential.ConfidentialRequest;
import com.alipay.mychain.sdk.message.transaction.confidential.ConfidentialResponse;
import com.alipay.mychain.sdk.message.transaction.contract.*;
import com.alipay.mychain.sdk.rlp.Rlp;
import com.alipay.mychain.sdk.rlp.RlpList;
import com.alipay.mychain.sdk.type.BaseFixedSizeUnsignedInteger;
import com.alipay.mychain.sdk.utils.RandomUtil;
import com.alipay.mychain.sdk.vm.EVMOutput;
import com.alipay.mychain.sdk.vm.EVMParameter;
import com.alipay.mychain.sdk.vm.WASMOutput;
import com.alipay.mychain.sdk.vm.WASMParameter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;

public class Mychain020Client {

    private static final int AUTH_MAP_WEIGHT = 100;
    private Mychain020Config config;
    private Mychain020TeeConfig teeConfig;
    private ClientEnv env = null;
    private final MychainClient sdk;

    private final Logger logger;

    public Mychain020Client(byte[] configData, Logger logger) {
        try {
            this.config = Mychain020Config.fromJsonString(new String(configData));
        } catch (IOException e) {
            throw new RuntimeException("[Mychain020BBCService] build config info error, ", e);
        }

        if (config.isTEEChain()) {
            this.teeConfig.setTeePublicKeys(
                    config.getMychainTeePublicKey().getBytes());
        }

        if (CrossChainCertificateUtil.readCrossChainCertificateFromPem(this.config.getBcdnsRootCertPem().getBytes()).getType()
                != CrossChainCertificateTypeEnum.BCDNS_TRUST_ROOT_CERTIFICATE) {
            throw new RuntimeException("[Mychain020BBCService] incorrect bcdns root cert");
        }

        this.env = config.buildMychainEnvWithConfigUpdate();
        this.sdk = new MychainClient();
        this.logger = logger;
    }

    public boolean startup() {
        if (!sdk.init(env)) {
            logger.error("[Mychain020BBCService] init mychain sdk fail for {}.",
                    config.getMychainPrimary());
            sdk.shutDown();
            return false;
        }

        if (!ifAccountValid()) {
            logger.error("[Mychain020BBCService] account is invalid for {}, account: {},  pubkey: {}",
                    config.getMychainPrimary(),
                    config.getMychainAnchorAccount(),
                    config.getMychainAccountPubKey()
            );
            return false;
        }

        logger.info("[Mychain020Client] startup mychain0.20 sdk success for {}, isSM:{}",
                config.getMychainPrimary(),
                config.isSMChain());
        return true;
    }

    public boolean shutdown() {
        sdk.shutDown();

        logger.info("[Mychain020Client] shutdown mychain0.20 sdk success for {}, isSM:{}",
                config.getMychainPrimary(),
                config.isSMChain());

        return true;
    }

    // ==================================================== 合约相关接口
    public SendResponseResult callContract(String contractName, Parameters parameters, boolean sync) {
        return callContract(
                Utils.getIdentityByName(contractName, config.getMychainHashType()),
                contractName,
                parameters,
                sync);
    }

    /**
     * Calls a contract whose Mychain identity was recovered from another
     * on-chain system contract. This is required for legacy contexts which did
     * not persist the randomly generated monitor-verifier contract name.
     */
    public SendResponseResult callContract(Identity contractIdentity, Parameters parameters, boolean sync) {
        return callContract(contractIdentity, contractIdentity.hexStrValue(), parameters, sync);
    }

    private SendResponseResult callContract(Identity contractIdentity,
                                            String contractLabel,
                                            Parameters parameters,
                                            boolean sync) {
        String account = config.getMychainAnchorAccount();

        VMTypeEnum vmType = MychainUtils.getSupportedParameterType(parameters);
        logger.info("call {} contractName {}.{}",
                vmType,
                contractLabel,
                vmType == VMTypeEnum.EVM ?
                        ((EVMParameter) parameters).getMethodSignature() :
                        ((WASMParameter) parameters).getMethodSignature());

        try {
            CallContractRequest request = new CallContractRequest(
                    Utils.getIdentityByName(account, config.getMychainHashType()),
                    contractIdentity,
                    parameters,
                    BigInteger.ZERO,
                    vmType
            );
            request.setHashTypeEnum(config.getMychainHashType());
            request.setTxGas(config.getTxGas());
            refreshRequestTimestampAndNonce(request);
            request.complete();

            return sendCallContractRequest(request, sync);
        } catch (Exception e) {
            logger.error("call contract error.", e);
            throw new RuntimeException(e);
        }
    }

    public SendResponseResult sendCallContractRequest(CallContractRequest request, boolean sync) {
        try {
            if (sync) {
                CallContractResponse callContractResponse = sdk.getContractService().callContract(request);

                // 合约调用异常（无结果）
                if (ObjectUtil.isEmpty(callContractResponse)) {
                    throw new RuntimeException("sendCallContractRequest with empty result: " + request.getContractId());
                }

                // 合约调用交易失败，返回交易失败结果
                if (!callContractResponse.isSuccess()) {
                    logger.info("call contract fail tx {}, {} - {} - {} - {} - detail code:{}",
                            request.getTransaction().getHash().hexStrValue(),
                            callContractResponse.isSuccess(),
                            callContractResponse.getErrorCode().getErrorCode(),
                            callContractResponse.getErrorCode().getErrorDesc(),
                            callContractResponse.getTransactionReceipt(),
                            callContractResponse.getTransactionReceipt().getResult());

                    return new SendResponseResult(
                            request.getTransaction().getHash().hexStrValue(),
                            false,
                            callContractResponse.isSuccess(),
                            String.valueOf(callContractResponse.getErrorCode().getErrorCode()),
                            callContractResponse.getExceptionMessage(),
                            request.getTransaction().getTimestamp(),
                            request.getTransaction().toRlp());
                }

                // 合约调用交易成功且有receipt（交易成功但合约调用不一定成功，取决于receipt里面的result）
                if (callContractResponse.getTransactionReceipt() != null) {
                    logger.info("call contract tx {}, receipt {}",
                            request.getTransaction().getHash().hexStrValue(),
                            callContractResponse.getTransactionReceipt().toString());

                    long errorCode = callContractResponse.getTransactionReceipt().getResult();
                    String errorMsg = "";
                    if (ErrorCode.VM_REVERT.getErrorCode() == errorCode
                            && callContractResponse.getTransactionReceipt().getOutput() != null) {
                        // 合约调用revert，取出receipt中包含的revert信息
                        switch (request.getVmTypeEnum()) {
                            case EVM:
                                byte[] output = callContractResponse.getTransactionReceipt().getOutput();
                                if (0 != output.length) {
                                    EVMOutput evmOutput = new EVMOutput(Hex.toHexString(output).substring(8));
                                    errorMsg = evmOutput.getString();
                                }
                                break;
                            case WASM:
                                WASMOutput wasmOutput = new WASMOutput(Hex.toHexString(callContractResponse.getTransactionReceipt().getOutput()));
                                errorMsg = wasmOutput.getString();
                                break;
                        }
                    } else {
                        // 其他情况，直接取出可能包含的信息
                        errorMsg = callContractResponse.getExceptionMessage();
                    }

                    logger.info("call contract tx {}, result {} - {}",
                            request.getTransaction().getHash().hexStrValue(),
                            errorCode,
                            errorMsg);
                    return new SendResponseResult(
                            request.getTransaction().getHash().hexStrValue(),
                            true,
                            ErrorCode.SUCCESS.getErrorCode() == errorCode,
                            String.valueOf(errorCode),
                            errorMsg,
                            request.getTransaction().getTimestamp(),
                            request.getTransaction().toRlp());
                } else {
                    // 合约调用交易成功但没有receipt
                    logger.info("call contract tx {}, no receipt",
                            request.getTransaction().getHash().hexStrValue());
                    return new SendResponseResult(
                            request.getTransaction().getHash().hexStrValue(),
                            false,
                            callContractResponse.isSuccess(),
                            String.valueOf(callContractResponse.getErrorCode().getErrorCode()),
                            callContractResponse.getExceptionMessage(),
                            request.getTransaction().getTimestamp(),
                            request.getTransaction().toRlp()
                    );
                }
            } else {
                long async_ts = System.currentTimeMillis();
                int ret = sdk.getContractService().asyncCallContract(request, (errorCode, response) -> {
                            logger.info("async call contract async result callback: errorCode:{}, result:{}", errorCode, response.getErrorCode());
                        }
                );

                logger.info("async call contract tx {} no receipt, cost {} ms",
                        request.getTransaction().getHash().hexStrValue(),
                        System.currentTimeMillis() - async_ts);

                // 异步调用注意需要关注是否调用成功
                return new SendResponseResult(
                        request.getTransaction().getHash().hexStrValue(),
                        false,
                        ErrorCode.forNumber(ret).isSuccess(),
                        String.valueOf(ErrorCode.forNumber(ret).getErrorCode()),
                        ErrorCode.forNumber(ret).getErrorDesc(),
                        request.getTransaction().getTimestamp(),
                        request.getTransaction().toRlp());
            }
        } catch (Exception e) {
            logger.error("send call contract request error.", e);
            throw new RuntimeException(e);
        }
    }

    public SendResponseResult callTeeWasmContract(String contractName, WASMParameter parameters, boolean sync) {
        String account = config.getMychainAnchorAccount();

        logger.info("call tee contractName {}.{}", contractName, parameters.getMethodSignature());

        try {
            CallContractRequest request = new CallContractRequest(
                    Utils.getIdentityByName(account, config.getMychainHashType()),
                    Utils.getIdentityByName(contractName, config.getMychainHashType()),
                    parameters,
                    BigInteger.ZERO,
                    VMTypeEnum.WASM
            );
            request.setHashTypeEnum(config.getMychainHashType());
            request.setTxGas(config.getTxGas());
            generateTeeRequest(request);

            byte[] transactionKey = ConfidentialUtil.keyGenerate(
                    teeConfig.getTeeSecretKey(),
                    request.getTransaction().getHash().getValue(),
                    config.getMychainHashType());
            ConfidentialRequest confidentialRequest = new ConfidentialRequest(
                    request,
                    teeConfig.getTeePublicKeys(),
                    transactionKey);
            confidentialRequest.setHashTypeEnum(config.getMychainHashType());

            return sendCallTeeWasmContractRequest(confidentialRequest, sync);
        } catch (Exception e) {
            logger.error("call tee contract error.", e);
            throw new RuntimeException(e);
        }
    }

    public SendResponseResult sendCallTeeWasmContractRequest(ConfidentialRequest request, boolean sync) {
        try {
            if (sync) {
                ConfidentialResponse confidentialResponse = sdk.getConfidentialService().confidentialRequest(request);

                if (ObjectUtil.isEmpty(confidentialResponse)) {
                    throw new RuntimeException("sendCallTeeWasmContractRequest with empty result: " +
                            ((CallContractRequest) request.getRequest()).getContractId());
                }

                if (!confidentialResponse.isSuccess()) {
                    logger.info("call tee contract fail tx {}, {} - {} - {} - {} - detail code:{}",
                            request.getTransaction().getHash().hexStrValue(),
                            confidentialResponse.isSuccess(),
                            confidentialResponse.getErrorCode().getErrorCode(),
                            confidentialResponse.getErrorCode().getErrorDesc(),
                            confidentialResponse.getTransactionReceipt(),
                            confidentialResponse.getTransactionReceipt().getResult());

                    return new SendResponseResult(
                            request.getTransaction().getHash().hexStrValue(),
                            false,
                            confidentialResponse.isSuccess(),
                            String.valueOf(confidentialResponse.getErrorCode().getErrorCode()),
                            confidentialResponse.getExceptionMessage(),
                            request.getTransaction().getTimestamp(),
                            request.getTransaction().toRlp());
                }

                if (confidentialResponse.getTransactionReceipt() != null) {
                    TransactionReceipt receipt = confidentialResponse.getReceipt(request.getUserKey());
                    logger.debug("call tee contract tx {}, receipt {}",
                            request.getTransaction().getHash().hexStrValue(),
                            receipt.toString());

                    long errorCode = receipt.getResult();
                    String errorMsg = "";
                    if (ErrorCode.VM_REVERT.getErrorCode() == errorCode
                            && receipt.getOutput() != null) {
                        WASMOutput wasmOutput = new WASMOutput(Hex.toHexString(receipt.getOutput()));
                        errorMsg = wasmOutput.getString();
                    } else {
                        errorMsg = confidentialResponse.getExceptionMessage();
                    }

                    logger.info("call tee contract tx {}, result {} - {}",
                            request.getTransaction().getHash().hexStrValue(),
                            errorCode,
                            errorMsg);
                    return new SendResponseResult(
                            request.getTransaction().getHash().hexStrValue(),
                            true,
                            ErrorCode.SUCCESS.getErrorCode() == errorCode,
                            String.valueOf(errorCode),
                            errorMsg,
                            request.getTransaction().getTimestamp(),
                            request.getTransaction().toRlp());
                } else {
                    logger.info("call tee contract tx {}, no receipt",
                            request.getTransaction().getHash().hexStrValue());
                    return new SendResponseResult(
                            request.getTransaction().getHash().hexStrValue(),
                            false,
                            confidentialResponse.isSuccess(),
                            String.valueOf(confidentialResponse.getErrorCode().getErrorCode()),
                            confidentialResponse.getExceptionMessage(),
                            request.getTransaction().getTimestamp(),
                            request.getTransaction().toRlp()
                    );
                }
            } else {
                int ret = sdk.getConfidentialService().asyncConfidentialRequest(request, (errorCode, response) ->
                        logger.info("async call tee contract, errorCode:{}, response:{}", errorCode, response.getErrorCode()));

                logger.info("async call tee contract tx {}, no receipt",
                        request.getTransaction().getHash().hexStrValue());

                return new SendResponseResult(
                        request.getTransaction().getHash().hexStrValue(),
                        false,
                        ErrorCode.forNumber(ret).isSuccess(),
                        String.valueOf(ErrorCode.forNumber(ret).getErrorCode()),
                        ErrorCode.forNumber(ret).getErrorDesc(),
                        request.getTransaction().getTimestamp(),
                        request.getTransaction().toRlp());
            }
        } catch (Exception e) {
            logger.error("send call tee wasm contract request error.", e);
            throw new RuntimeException(e);
        }
    }

    public TransactionReceipt localCallContract(String contractName, Parameters parameters) {
        return localCallContract(
                Utils.getIdentityByName(contractName, config.getMychainHashType()),
                contractName,
                parameters);
    }

    public TransactionReceipt localCallContract(Identity contractIdentity, Parameters parameters) {
        return localCallContract(contractIdentity, contractIdentity.hexStrValue(), parameters);
    }

    private TransactionReceipt localCallContract(Identity contractIdentity,
                                                 String contractLabel,
                                                 Parameters parameters) {
        String account = config.getMychainAnchorAccount();

        VMTypeEnum vmType = MychainUtils.getSupportedParameterType(parameters);
        logger.info("local call {} contractName {}.{}",
                vmType,
                contractLabel,
                vmType == VMTypeEnum.EVM ?
                        ((EVMParameter) parameters).getMethodSignature() :
                        ((WASMParameter) parameters).getMethodSignature());

        CallContractRequest request = new CallContractRequest(
                Utils.getIdentityByName(account, config.getMychainHashType()),
                contractIdentity,
                parameters,
                BigInteger.ZERO,
                vmType
        );
        request.setTxGas(config.getTxGas());
        request.setHashTypeEnum(config.getMychainHashType());

        try {
            request.setLocal();

            logger.info("call detail {}", request.toString());

            CallContractResponse result = sdk.getContractService().callContract(request);

            logger.info("call result {}", result.toString());

            return result.getTransactionReceipt();

        } catch (Exception e) {
            logger.error("send local call error.", e);
            throw new RuntimeException(e);
        }
    }

    public TransactionReceipt localCallTeeWasmContract(String contractName, WASMParameter parameters) {
        String account = config.getMychainAnchorAccount();

        logger.info("local call tee contractName {}.{}", contractName, parameters.getMethodSignature());
        CallContractRequest request = new CallContractRequest(
                Utils.getIdentityByName(account, config.getMychainHashType()),
                Utils.getIdentityByName(contractName, config.getMychainHashType()),
                parameters,
                BigInteger.ZERO,
                VMTypeEnum.WASM
        );
        request.setTxGas(config.getTxGas());
        request.setHashTypeEnum(config.getMychainHashType());

        try {
            request.setLocal();
            logger.info("tee local call detail {}", request.toString());

            generateTeeRequest(request);

            byte[] transactionKey = ConfidentialUtil.keyGenerate(
                    teeConfig.getTeeSecretKey(),
                    request.getTransaction().getHash().getValue());

            ConfidentialRequest confidentialRequest = new ConfidentialRequest(
                    request,
                    teeConfig.getTeePublicKeys(),
                    transactionKey);
            confidentialRequest.setHashTypeEnum(config.getMychainHashType());

            ConfidentialResponse response = sdk.getConfidentialService()
                    .confidentialRequest(confidentialRequest);

            return response.getReceipt(transactionKey);
        } catch (Exception e) {
            logger.error("send tee local call error.", e);
            throw new RuntimeException(e);
        }
    }


    private VMTypeEnum getSupportedParameterType(Parameters parameters) {
        if (parameters instanceof EVMParameter) {
            return VMTypeEnum.EVM;
        } else if (parameters instanceof WASMParameter) {
            return VMTypeEnum.WASM;
        } else {
            throw new RuntimeException("Unsupported parameter type: " + parameters.getClass().getTypeName());
        }
    }

    public boolean deployContract(String contractFilename, String contractName, VMTypeEnum vmType, Parameters parameters) {
        logger.info("deploy contract {} {}", contractName, contractFilename);
        String account = config.getMychainAnchorAccount();

        byte[] contractCode = readFileBytes(contractFilename);
        if (vmType == VMTypeEnum.EVM) {
            contractCode = Hex.decode(new String(contractCode));
        }

        DeployContractRequest request = new DeployContractRequest(
                Utils.getIdentityByName(account, config.getMychainHashType()),
                Utils.getIdentityByName(contractName, config.getMychainHashType()),
                contractCode,
                vmType,
                parameters,
                BigInteger.ZERO);
        request.setHashTypeEnum(config.getMychainHashType());
        request.setTxGas(config.getTxGas());

        //deploy contract
        DeployContractResponse response = sdk.getContractService().deployContract(request);

        logger.info("chain {} with crypto_suite {} deploy contract {} txHash {} - {} - {} - {} ",
                config.getMychainPrimary(),
                config.getMychainCryptoSuiteEnum().getName(),
                contractName,
                response.getTxHash().hexStrValue(),
                response.isSuccess(),
                response.getErrorCode().getErrorCode(),
                response.getErrorCode().getErrorDesc());
        if (!response.isSuccess()) {
            logger.error("failed to deploy contract {} txHash {} - {} - {}",
                    contractName, response.getTxHash().toString(),
                    response.getErrorCode().toString(), response.getExceptionMessage());
            return false;
        }

        logger.info("deploy receipt : {}", response.toString());
        return ErrorCode.SUCCESS.getErrorCode() == response.getTransactionReceipt().getResult();
    }

    public boolean deployTeeWasmContract(String contractFilename, String contractName, WASMParameter parameters) {
        logger.info("deploy tee contract {} {}", contractName, contractFilename);
        String account = config.getMychainAnchorAccount();

        byte[] contractCode = this.readFileBytes(contractFilename);
        DeployContractRequest request = new DeployContractRequest(
                Utils.getIdentityByName(account, config.getMychainHashType()),
                Utils.getIdentityByName(contractName, config.getMychainHashType()),
                contractCode,
                VMTypeEnum.WASM,
                parameters,
                BigInteger.ZERO);
        request.setTxGas(config.getTxGas());
        request.setHashTypeEnum(config.getMychainHashType());

        generateTeeRequest(request);

        byte[] transactionKey = ConfidentialUtil.keyGenerate(
                teeConfig.getTeeSecretKey(),
                request.getTransaction().getHash().getValue());
        ConfidentialRequest confidentialRequest = new ConfidentialRequest(
                request,
                teeConfig.getTeePublicKeys(),
                transactionKey);
        if (!confidentialRequest.isValid()) {
            return false;
        }
        confidentialRequest.complete();

        //deploy contract
        ConfidentialResponse response = sdk.getConfidentialService().confidentialRequest(confidentialRequest);

        logger.info("deploy tee wasm contract {}~{} txHash {} - {} - {} - {}", account, contractName,
                response.getTxHash().hexStrValue(),
                response.isSuccess(), response.getErrorCode().getErrorCode(), response.getErrorCode().getErrorDesc());

        if (!response.isSuccess()) {
            return false;
        }

        TransactionReceipt receipt = response.getReceipt(transactionKey);
        logger.info("deploy tee wasm receipt : {}-{}", receipt.getResult(), receipt.toString());

        return receipt.getResult() == ErrorCode.SUCCESS.getErrorCode();
    }

    public boolean upgradeTeeWasmContract(String contractFilename, String contractName) {
        return this.upgradeTeeWasmContract(this.readFileBytes(contractFilename), contractName);
    }

    public boolean upgradeTeeWasmContract(byte[] contractCode, String contractName) {
        if (StringUtils.isEmpty(contractName)) {
            logger.info("tee contract do not be deployed yet : {}", contractName);
            return false;
        }
        UpdateContractRequest request = new UpdateContractRequest(
                Utils.getIdentityByName(contractName, config.getMychainHashType()),
                contractCode,
                VMTypeEnum.WASM
        );
        request.setHashTypeEnum(config.getMychainHashType());

        generateTeeRequest(request);

        byte[] transactionKey = ConfidentialUtil.keyGenerate(
                teeConfig.getTeeSecretKey(),
                request.getTransaction().getHash().getValue());
        ConfidentialRequest confidentialRequest = new ConfidentialRequest(
                request,
                teeConfig.getTeePublicKeys(),
                transactionKey);

        //upgrade contract
        ConfidentialResponse response = sdk.getConfidentialService().confidentialRequest(confidentialRequest);

        logger.info("upgrade tee wasm contract {} txHash {} - {} - {} - {}", contractName,
                response.getTxHash().hexStrValue(),
                response.isSuccess(), response.getErrorCode().getErrorCode(), response.getErrorCode().getErrorDesc());

        if (!response.isSuccess()) {
            return false;
        }

        TransactionReceipt receipt = response.getReceipt(transactionKey);
        logger.info("upgrade tee wasm receipt : {}", receipt.toString());

        return receipt.getResult() == ErrorCode.SUCCESS.getErrorCode();
    }

    public boolean upgradeContract(String contractFilename, String contractName, VMTypeEnum type) {
        return this.upgradeContract(this.readFileBytes(contractFilename), contractName, type);
    }

    public boolean upgradeContract(String contractFilename, Identity contractIdentity, VMTypeEnum type) {
        return this.upgradeContract(this.readFileBytes(contractFilename), contractIdentity, type);
    }

    public boolean upgradeContract(byte[] contractCode, String contractName, VMTypeEnum type) {
        if (StringUtils.isEmpty(contractName)) {
            logger.error("contract do not be deployed yet : {}", contractName);
            return false;
        }
        return upgradeContract(
                contractCode,
                Utils.getIdentityByName(contractName, config.getMychainHashType()),
                type,
                contractName);
    }

    public boolean upgradeContract(byte[] contractCode, Identity contractIdentity, VMTypeEnum type) {
        if (contractIdentity == null) {
            logger.error("contract identity is empty");
            return false;
        }
        return upgradeContract(contractCode, contractIdentity, type, contractIdentity.hexStrValue());
    }

    private boolean upgradeContract(byte[] contractCode,
                                    Identity contractIdentity,
                                    VMTypeEnum type,
                                    String contractLabel) {
        if (type == VMTypeEnum.EVM) {
            contractCode = Hex.decode(new String(contractCode));
        }

        UpdateContractRequest request = new UpdateContractRequest(
                contractIdentity,
                contractCode,
                type
        );
        request.setHashTypeEnum(config.getMychainHashType());

        UpdateContractResponse response = sdk.getContractService().updateContract(request);
        if (!response.isSuccess()) {
            logger.error(
                    "upgrade contract failed: {} - {} - {}",
                    contractLabel,
                    response.getErrorCode().getErrorCode(),
                    response.getErrorCode().getErrorDesc()
            );
            return false;
        }
        logger.info(
                "upgrade contract {} txHash {} - {} - {} - {}",
                contractLabel,
                response.getTxHash().hexStrValue(),
                response.isSuccess(),
                response.getErrorCode().getErrorCode(),
                response.getErrorCode().getErrorDesc()
        );

        logger.info("upgrade contract receipt : {}", response.getTransactionReceipt());

        return response.getTransactionReceipt().getResult() == (long) ErrorCode.SUCCESS.getErrorCode();
    }

    private void generateTeeRequest(AbstractTransactionRequest request) {
        long ts = sdk.getNetwork().getSystemTimestamp();
        request.setTxTimeNonce(ts, BaseFixedSizeUnsignedInteger.Fixed64BitUnsignedInteger
                .valueOf(RandomUtil.randomize(ts + request.getTransaction().hashCode())), true);
        request.complete();
        sdk.getConfidentialService().signRequest(
                env.getSignerOption().getSigners(),
                request);
    }

    private byte[] readFileBytes(String filePath) {
        try {
            InputStream stream = this.getClass().getResourceAsStream(filePath);
            if (stream == null) {
                String msg = StrUtil.format("load contract file fail. filePath={}", filePath);
                logger.error(msg);
                throw new RuntimeException(msg);
            }
            return IOUtils.toByteArray(stream);
        } catch (IOException e) {
            logger.error("load contract error.", e);
            throw new RuntimeException(e);
        }
    }

    // ==================================================== 链上数据（交易、区块）相关接口

    /**
     * 根据交易哈希查询交易回执
     *
     * @param txHash
     * @return
     */
    public QueryTransactionReceiptResponse getTxReceiptByTxhash(String txHash) {
        return sdk.getQueryService().queryTransactionReceipt(new Hash(txHash));
    }

    /**
     * 根据区块高度获取区块
     *
     * @param height
     */
    public Block getBlockByHeight(long height) {
        return sdk.getQueryService().queryBlock(BigInteger.valueOf(height)).getBlock();
    }

    /**
     * 根据区块高度获取区块头信息
     *
     * @param height
     * @return
     */
    public BlockHeaderInfo getRawBlockHeaderInfoByHeight(BigInteger height) {
        List<BlockHeaderInfo> res = sdk.getQueryService().queryBlockHeaderInfos(height, 1, 0).getBlockHeaderInfoList();
        if (ObjectUtil.isEmpty(res)) {
            return null;
        }
        return res.get(0);
    }

    public List<TransactionReceipt> getRawBlockReceiptListByHash(Hash blockHash) {

        List<Hash> hashList = new ArrayList<>();
        hashList.add(blockHash);
        QueryBlockReceiptsResponse response =
                sdk.getQueryService().queryBlockReceipts(hashList);

        if (!response.isSuccess() || response.getBlockReceiptInfos().isEmpty()) {
            logger.error("queryBlockTxReceipts fail for blockHash: {} : {}", blockHash, response.getExceptionMessage());
            return ListUtil.empty();
        }

        return response.getBlockReceiptInfos().get(0).getReceipts();
    }

    public BlockBody getBlockBodyByHash(Hash blockHash) {
        QueryBlockBodiesResponse response = sdk.getQueryService().queryBlockBodies(ListUtil.toList(blockHash));
        if (!response.isSuccess() || response.getBlockBodyInfos().isEmpty()) {
            throw new RuntimeException(StrUtil.format("queryBlockBodies fail for blockHash: {} : {}", blockHash, response.getExceptionMessage()));
        }
        return response.getBlockBodyInfos().get(0).getBody();
    }

    public ConsensusNodeInfo generateConsensusNodeInfo(String amContractId, BlockHeader blockHeader) {
        ContractAddressInfo amAddressInfo = ContractAddressInfo.decode(amContractId);
        if (!MychainUtils.bloomTopicsMatch(MychainUtils.CONSENSUS_UPDATE_EVENT_TOPICS_LIST, this.config.getMychainHashType(), blockHeader.getLogBloom())) {
            return ConsensusNodeInfo.builder()
                    .mychainHashType(config.getMychainHashType())
                    .amContractIds(amAddressInfo.toSet())
                    .build();
        }

        BlockBody blockBody = getBlockBodyByHash(blockHeader.getHash());
        return ConsensusNodeInfo.builder()
                .mychainHashType(config.getMychainHashType())
                .amContractIds(amAddressInfo.toSet())
                .transactionReceipts(blockBody.getReceiptList())
                .transactions(blockBody.getTransactionList())
                .build();
    }

    /**
     * 获取最新区块高度
     *
     * @return
     */
    public Long queryLatestHeight() {
        return sdk.getQueryService().queryLastBlock().getBlock().getBlockHeader().getNumber().longValue();
    }

    public long getLatestBlockTimestamp() {
        QueryLastBlockHeaderResponse response = sdk.getQueryService().queryLastBlockHeader();
        if (response != null && response.isSuccess()) {
            logger.info("last block number is {}, timestamp is {}", response.getHeader().getNumber(), response.getHeader().getTimestamp());
            return response.getHeader().getTimestamp();
        }
        return 0;
    }

    public long queryNonceBefore() {
        long nonceBefore = 0;
        // 一开始启动的时候获取配置，获取不到需要重复知道成功，因此是一个死循环
        while (nonceBefore == 0) {
            QueryContractConfigStatusResponse response = sdk.getQueryService().queryContractConfigStatus();
            if (response != null && response.isSuccess()) {
                String value = response.getContractConfig().getConfigs().get("nonce.before");
                try {
                    nonceBefore = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    logger.warn("nonce before is a invalid value {}", value, e);
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.info("interrupted", e);
            }
        }
        return nonceBefore;
    }

    // ==================================================== 其他查询接口
    public Mychain020Config getConfig() {
        return config;
    }

    public String getPrimary() {
        return config.getMychainPrimary();
    }

    public boolean isTeeChain() {
        return config.getMychainTeePublicKey() != null && !config.getMychainTeePublicKey().isEmpty();
    }

    public boolean isSMChain() {
        return ObjectUtil.equals(
                CryptoSuiteEnum.CRYPTO_SUITE_SM,
                this.config.getMychainCryptoSuiteEnum());
    }

    /**
     * 入参为合约id，即合约名称的哈希字符串，不能直接传合约名称！！！
     *
     * @param contractId
     * @return
     */
    public VMTypeEnum getContractType(String contractId) {
        if (isTeeChain()) {
            return null;
        }

        QueryContractResponse response = sdk.getQueryService().queryContract(new Identity(contractId));
        if (!response.isSuccess()
                && response.getErrorCode().getErrorCode() != ErrorCode.SERVICE_QUERY_NO_RESULT.getErrorCode()) {
            throw new RuntimeException("query mychain020 contract type fail for " + contractId);
        }

        Contract contract = response.getContract();
        if (null == contract || ArrayUtils.isEmpty(contract.getCode())) {
            return null;
        }

        return VMTypeEnum.valueOf(contract.getCode()[0]);
    }

    public boolean ifAccountValid() {
        try {
            MychainUtils.checkKeyPair(
                    config.getMychainAccountPriKey(),
                    config.getMychainAccountPubKey(),
                    config.getMychainCryptoSuiteEnum()
            );
        } catch (Exception e) {
            logger.error("invalid key pair.", e);
            return false;
        }

        QueryAccountResponse replayAccount = sdk.getQueryService().queryAccount(
                Utils.getIdentityByName(
                        config.getMychainAnchorAccount(),
                        config.getMychainHashType()));

        logger.info("Query mychain account: {} {} {} {}",
                config.getMychainAnchorAccount(),
                Utils.getIdentityByName(
                        config.getMychainAnchorAccount(),
                        config.getMychainHashType()),
                replayAccount.isSuccess(),
                replayAccount.getErrorCode());

        if (!replayAccount.isSuccess()) {
            return false;
        }

        Map<PublicKey, Integer> authMap = replayAccount.getAccount().getAuthMap().getAuthMap();

        if (authMap.size() != 1) {
            logger.info("auth map size must only has itself. size {}", authMap.size());
            return false;
        }

        PublicKey publicKey = (PublicKey) authMap.keySet().toArray()[0];

        // Check whether the pubkey weights are consistent according to the auth_map
        if (authMap.get(publicKey) != AUTH_MAP_WEIGHT) {
            logger.info("auth weight must be 100, but it's {}", authMap.get(publicKey));
            return false;
        }

        // Check whether the pubkey info is consistent with pubkey.
        // The length of the normal pubkey is 64,
        // and the length of the sm pubkey is 67 (prefix 000304).
        if (publicKey.getData().length != 64 && publicKey.getData().length != 67) {
            logger.info("invalid publickey from sdk");
            return false;
        }

        if (!publicKey.hexStrValue().equals(config.getMychainAccountPubKey())) {
            logger.info("auth map size must only has itself. size {}", authMap.size());
            return false;
        }

        return true;
    }

    // ==================================================== 交易可靠上链相关接口
    public Transaction refreshTransaction(byte[] rawTx) {

        CallContractRequest request = getRequestFromBytes(rawTx);

        // 其他值不变，只修改为最新的时间戳和替换nonce
        refreshRequestTimestampAndNonce(request);
        // 有数据变更，需要刷新
        request.getTransaction().setGroupId(request.getGroupId());
        request.getTransaction().calcHash();
        // 这里还没签名

        return request.getTransaction();
    }

    public CrossChainMessageReceipt submitSyncTransactionByRawTx(byte[] rawTx) {
        CallContractRequest request = null;
        CrossChainMessageReceipt crossChainMessageReceipt = new CrossChainMessageReceipt();
        // 默认情况下，confirm和successful都为false
        crossChainMessageReceipt.setSuccessful(false);
        crossChainMessageReceipt.setConfirmed(false);

        try {
            logger.info("submit raw Tx {}", HexUtil.encodeHexStr(rawTx));
            request = getRequestFromBytes(rawTx);
        } catch (Exception e) {
            logger.error("decode raw tx error", e);
            return crossChainMessageReceipt;
        }

        logger.info("submit tx, hash {}", request.getTransaction().getHash().hexStrValue());

        request.getTransaction().getSignatureList().clear();
        TransactionReceiptResponse response = sdk.getContractService().sendSyncTransaction(request);

        if (response != null && response.isSuccess()) {
            // 区块高度大于0认为收录进区块，否则不认为
            if (response.getBlockNumber().longValue() > 0) {
                crossChainMessageReceipt.setConfirmed(true);
                // 落块的时候更新交易hash
                crossChainMessageReceipt.setTxhash(request.getTransaction().getHash().hexStrValue());
                crossChainMessageReceipt.setRawTx(rawTx);
                crossChainMessageReceipt.setTxTimestamp(request.getTransaction().getTimestamp());
                crossChainMessageReceipt.setSuccessful(response.getTransactionReceipt().getResult() == 0);
                crossChainMessageReceipt.setErrorMsg(getErrorMsgFromReceipt(response.getTransactionReceipt()));
                logger.info("tx hash {} is completed", request.getTransaction().getHash().hexStrValue());
            } else {
                crossChainMessageReceipt.setConfirmed(false);
                logger.info("tx hash {} is not completed", request.getTransaction().getHash().hexStrValue());
            }
        }
        return crossChainMessageReceipt;
    }

    private CallContractRequest getRequestFromBytes(byte[] rawTx) {
        CallContractRequest request = new CallContractRequest();
        request.setHashTypeEnum(config.getMychainHashType());
        RlpList list = (RlpList) Rlp.decode2(rawTx).get(0);
        request.getTransaction().fromRlp(list);
        request.setEnableRequestValidVerify(false);
        request.setEnableComplete(false);
        return request;
    }

    private void refreshRequestTimestampAndNonce(CallContractRequest request) {
        long ts = sdk.getNetwork().getSystemTimestamp();
        request.setTxTimeNonce(ts, BaseFixedSizeUnsignedInteger.Fixed64BitUnsignedInteger.valueOf(
                RandomUtil.randomize(ts + (long) request.hashCode() + System.nanoTime())), true);
    }

    private String getErrorMsgFromReceipt(TransactionReceipt receipt) {
        if (ObjectUtil.isEmpty(receipt.getOutput())) {
            return "";
        }
        for (LogEntry log : receipt.getLogs()) {
            if (log.getTopics().isEmpty()) {
                continue;
            }
            if (log.getTopics().get(0).equals(AMContractClientWASM.AM_MSG_RECV_SIGN_HEX) &&
                    log.getTo().equals(
                            Utils.getIdentityByName(
                                    ContractAddressInfo.decode(config.getAmContractName()).getWasmContractAddress(),
                                    config.getMychainHashType()))) {
                return new WASMOutput(HexUtil.encodeHexStr(receipt.getOutput())).getString();
            }
            if (log.getTopics().get(0).equals(AMContractClientEVM.AM_MSG_RECV_SIGN_HEX) &&
                    log.getTo().equals(
                            Utils.getIdentityByName(
                                    ContractAddressInfo.decode(config.getAmContractName()).getEvmContractAddress(),
                                    config.getMychainHashType())
                    )) {
                return new EVMOutput(HexUtil.encodeHexStr(receipt.getOutput()).substring(8)).getString();
            }
        }
        return cn.hutool.core.codec.Base64.encode(receipt.getOutput());
    }
}
