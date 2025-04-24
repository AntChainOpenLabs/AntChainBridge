package com.alipay.antchain.bridge.plugins.bifchain;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

import cn.bif.api.BIFSDK;
import cn.bif.common.Constant;
import cn.bif.common.JsonUtils;
import cn.bif.exception.EncException;
import cn.bif.model.request.*;
import cn.bif.model.response.*;
import cn.bif.model.response.result.data.BIFLogInfo;
import cn.bif.model.response.result.data.BIFOperation;
import cn.bif.model.response.result.data.BIFTransactionHistory;
import cn.bif.module.encryption.key.PrivateKeyManager;
import cn.bif.utils.generator.response.Log;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.AuthMessageContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.bbc.syscontract.PTCContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.SDPContract;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.BCDNSTrustRootCredentialSubject;
import com.alipay.antchain.bridge.commons.bcdns.CrossChainCertificateFactory;
import com.alipay.antchain.bridge.commons.bcdns.PTCCredentialSubject;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.ptc.*;
import com.alipay.antchain.bridge.commons.core.rcc.ReliableCrossChainMessage;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.plugins.lib.BBCService;
import com.alipay.antchain.bridge.plugins.spi.bbc.AbstractBBCService;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeEndorseProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.CommitteeEndorseRoot;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.NodeEndorseInfo;
import com.alipay.antchain.bridge.ptc.committee.types.trustroot.CommitteeVerifyAnchor;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import lombok.Getter;

@BBCService(products = "bifchain", pluginId = "plugin-simple-bifchain")
@Getter
public class BifchainBBCService extends AbstractBBCService {
    private BifchainConfig config;

    private BIFSDK sdk;

    private AbstractBBCContext bbcContext;

    private static final OkHttpClient client = new OkHttpClient();

    @Override
    public void startup(AbstractBBCContext context) {
        getBBCLogger().info("Bif BBCService startup with context: {}", new String(context.getConfForBlockchainClient()));

        if (ObjectUtil.isNull(context)) {
            throw new RuntimeException("null bbc context");
        }
        if (ObjectUtil.isEmpty(context.getConfForBlockchainClient())) {
            throw new RuntimeException("empty blockchain client conf");
        }

        // 1. Obtain the configuration information
        try {
            config = BifchainConfig.fromJsonString(new String(context.getConfForBlockchainClient()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(StrUtil.isEmpty(config.getPrivateKey())){
            throw new RuntimeException("private key is empty");
        }

        try {
            PrivateKeyManager privateKeyManager = new PrivateKeyManager(config.getPrivateKey());
            this.config.setAddress(privateKeyManager.getEncAddress());
        } catch (EncException e) {
            throw new RuntimeException(e);
        }

        if (StrUtil.isEmpty(config.getUrl())) {
            throw new RuntimeException("url is empty");
        }

        // 2. Connect to the bif network
        this.sdk = BIFSDK.getInstance(config.getUrl());
        try {
            BIFBlockGetNumberInfoRequest request = new BIFBlockGetNumberInfoRequest();
            BIFBlockGetNumberResponse response = sdk.getBIFBlockService().getBlockNumber(request);
            if (response.getErrorCode() != 0) {
                throw new RuntimeException(String.format("failed to connect bif (url: %s)", config.getUrl()));
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("failed to connect bif (url: %s)", config.getUrl()), e);
        }

        this.bbcContext = context;
        if (ObjectUtil.isNull(context.getAuthMessageContract())
                && StrUtil.isNotEmpty(this.config.getAmContractAddressDeployed())) {
            AuthMessageContract authMessageContract = new AuthMessageContract();
            authMessageContract.setContractAddress(this.config.getAmContractAddressDeployed());
            authMessageContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            this.bbcContext.setAuthMessageContract(authMessageContract);
        }

        if (ObjectUtil.isNull(context.getSdpContract())
                && StrUtil.isNotEmpty(this.config.getSdpContractAddressDeployed())) {
            SDPContract sdpContract = new SDPContract();
            sdpContract.setContractAddress(this.config.getSdpContractAddressDeployed());
            sdpContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            this.bbcContext.setSdpContract(sdpContract);
        }

        if (ObjectUtil.isNull(context.getPtcContract())
                && StrUtil.isNotEmpty(this.config.getPtcContractAddressDeployed())) {
            PTCContract ptcContract = new PTCContract();
            ptcContract.setContractAddress(this.config.getPtcContractAddressDeployed());
            ptcContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            this.bbcContext.setPtcContract(ptcContract);
        }
    }

    @Override
    public void shutdown() {
        getBBCLogger().info("shut down bif BBCService!");
    }

    @Override
    public AbstractBBCContext getContext() {
        if (ObjectUtil.isNull(this.bbcContext)){
            throw new RuntimeException("empty bbc context");
        }

        getBBCLogger().debug("BIF BBCService context (amAddress: {}, amStatus: {}, sdpAddress: {}, sdpStatus: {}), ptcAddress: {}, ptcStatus: {})",
                this.bbcContext.getAuthMessageContract() != null ? this.bbcContext.getAuthMessageContract().getContractAddress() : "",
                this.bbcContext.getAuthMessageContract() != null ? this.bbcContext.getAuthMessageContract().getStatus() : "",
                this.bbcContext.getSdpContract() != null ? this.bbcContext.getSdpContract().getContractAddress() : "",
                this.bbcContext.getSdpContract() != null ? this.bbcContext.getSdpContract().getStatus() : "",
                this.bbcContext.getPtcContract() != null ? this.bbcContext.getPtcContract().getContractAddress() : "",
                this.bbcContext.getPtcContract() != null ? this.bbcContext.getPtcContract().getStatus() : ""
        );
        return this.bbcContext;
    }

    private Boolean queryTxResult(String txHash) {
        BIFTransactionGetInfoRequest bifTransactionGetInfoRequest = new BIFTransactionGetInfoRequest();
        bifTransactionGetInfoRequest.setHash(txHash);
        int maxRetries = 10; // 设置最大重试次数
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                BIFTransactionGetInfoResponse bifTransactionGetInfoResponse = sdk.getBIFTransactionService().getTransactionInfo(bifTransactionGetInfoRequest);

                if (ObjectUtil.isNotNull(bifTransactionGetInfoResponse.getResult()) && bifTransactionGetInfoResponse.getResult().getTransactions().length > 0) {
                    BIFTransactionHistory history = bifTransactionGetInfoResponse.getResult().getTransactions()[0];
                    boolean res = history.getErrorCode() == 0;
                    if (!res) {
                        getBBCLogger().error("tx failed: (txHash: {}, errorCode: {}, desc: {})",
                                txHash, history.getErrorCode(), history.getErrorDesc());
                    }
                    return res;
                }

                Thread.sleep(400L); // 等待400毫秒后再尝试
                retryCount++;
                getBBCLogger().info("Failed to query tx, retrying... (" + retryCount + "/" + maxRetries + ")");

            } catch (Throwable e) {
                throw new RuntimeException("Failed to query tx", e);
            }
        }
        throw new RuntimeException(StrUtil.format("query tx {} result out of retry", txHash));
    }

    private BIFContractCreateRequest createBIFContractCreateRequest(String contractByteCode) {
        BIFContractCreateRequest request = new BIFContractCreateRequest();
        request.setSenderAddress(this.config.getAddress());
        request.setPrivateKey(this.config.getPrivateKey());
        request.setInitBalance(0L);
        request.setPayload(contractByteCode);
        request.setRemarks("create contract");
        request.setType(1);
        request.setFeeLimit(this.config.getGasLimit());
        request.setGasPrice(this.config.getGasPrice());
        return request;
    }

    private BIFContractInvokeRequest createBIFContractInvokeRequest(String contractAddress, String invokeInput) {
        BIFContractInvokeRequest request = new BIFContractInvokeRequest();
        request.setSenderAddress(this.config.getAddress());
        request.setPrivateKey(this.config.getPrivateKey());
        request.setContractAddress(contractAddress);
        request.setBIFAmount(this.config.getAmount());
        request.setRemarks("contract invoke");
        request.setInput(invokeInput);
        request.setFeeLimit(this.config.getGasLimit());
        request.setGasPrice(this.config.getGasPrice());
        request.setNonceType(Constant.INIT_ONE);
        return request;
    }

    @Override
    public void setupAuthMessageContract() {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)){
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNotNull(this.bbcContext.getAuthMessageContract())
                && StrUtil.isNotEmpty(this.bbcContext.getAuthMessageContract().getContractAddress())) {
            // If the contract has been pre-deployed and the contract address is configured in the configuration file,
            // there is no need to redeploy.
            return;
        }

        // 2. deploy contract
        String txHash;
        try {
            BIFContractCreateRequest request = createBIFContractCreateRequest(
                    FileUtil.readString(this.getClass().getClassLoader().getResource("am.bin"), Charset.defaultCharset())
            );
            BIFContractCreateResponse response = sdk.getBIFContractService().contractCreate(request);
            if (response.getErrorCode() == 0) {
                txHash = response.getResult().getHash();
                boolean result = queryTxResult(txHash);
                if (!result) {
                    throw new RuntimeException("transaction executing failed");
                }
            } else {
                throw new RuntimeException("failed to deploy Auth contract");
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to deploy Auth contract", e);
        }

        // 3. get contract address and set contract to context
        String address;
        try {
            BIFContractGetAddressRequest request = new BIFContractGetAddressRequest();
            request.setHash(txHash);

            BIFContractGetAddressResponse response = sdk.getBIFContractService().getContractAddress(request);
            if (response.getErrorCode() == 0) {
                address = response.getResult().getContractAddressInfos().get(0).getContractAddress();
                AuthMessageContract authMessageContract = new AuthMessageContract();
                authMessageContract.setContractAddress(address);
                authMessageContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
                bbcContext.setAuthMessageContract(authMessageContract);
                getBBCLogger().info("setup Auth contract successful: {}", address);
            } else {
                throw new RuntimeException("failed to get Auth contract address");
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to get Auth contract address", e);
        }
    }

    @Override
    public void setupSDPMessageContract() {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)){
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNotNull(this.bbcContext.getSdpContract())
                && StrUtil.isNotEmpty(this.bbcContext.getSdpContract().getContractAddress())) {
            // If the contract has been pre-deployed and the contract address is configured in the configuration file,
            // there is no need to redeploy.
            return;
        }

        // 2. deploy contract
        String txHash;
        try {
            BIFContractCreateRequest request = createBIFContractCreateRequest(
                    FileUtil.readString(this.getClass().getClassLoader().getResource("sdp.bin"), Charset.defaultCharset())
            );
            BIFContractCreateResponse response = sdk.getBIFContractService().contractCreate(request);
            if (response.getErrorCode() == 0) {
                txHash = response.getResult().getHash();
                boolean result = queryTxResult(txHash);
                if (!result) {
                    throw new RuntimeException("transaction executing failed");
                }
            } else {
                throw new RuntimeException("failed to deploy sdp contract");
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to deploy sdp contract", e);
        }

        // 3. get contract address and set contract to context
        String address;
        try {
            BIFContractGetAddressRequest request = new BIFContractGetAddressRequest();
            request.setHash(txHash);

            BIFContractGetAddressResponse response = sdk.getBIFContractService().getContractAddress(request);
            if (response.getErrorCode() == 0) {
                address = response.getResult().getContractAddressInfos().get(0).getContractAddress();
                SDPContract sdpContract = new SDPContract();
                sdpContract.setContractAddress(address);
                sdpContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
                bbcContext.setSdpContract(sdpContract);
                getBBCLogger().info("setup sdp contract successful: {}", address);
            } else {
                throw new RuntimeException("failed to get sdp contract address");
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to get sdp contract address", e);
        }
    }

    @Override
    public void setupPTCContract() {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)){
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNotNull(this.bbcContext.getPtcContract())
                && StrUtil.isNotEmpty(this.bbcContext.getPtcContract().getContractAddress())) {
            // If the contract has been pre-deployed and the contract address is configured in the configuration file,
            // there is no need to redeploy.
            return;
        }

        // 2. deploy contract
        String txHash;
        try {
            //todo:check input root cert
            String initInput = StrUtil.format("{\"function\":\"constructor(bytes)\",\"args\":\"'{}'\"}", this.config.getPtcContractInitInput());
            BIFContractCreateRequest request = createBIFContractCreateRequest(
                    FileUtil.readString(this.getClass().getClassLoader().getResource("ptchub.bin"), Charset.defaultCharset())
            );
            request.setInitInput(initInput);
            BIFContractCreateResponse response = sdk.getBIFContractService().contractCreate(request);
            if (response.getErrorCode() == 0) {
                txHash = response.getResult().getHash();
                boolean result = queryTxResult(txHash);
                if (!result) {
                    throw new RuntimeException("transaction executing failed");
                }
            } else {
                throw new RuntimeException("failed to deploy ptc contract");
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to deploy ptc contract", e);
        }

        // 3. get contract address and set contract to context
        String address;
        try {
            BIFContractGetAddressRequest request = new BIFContractGetAddressRequest();
            request.setHash(txHash);

            BIFContractGetAddressResponse response = sdk.getBIFContractService().getContractAddress(request);
            if (response.getErrorCode() == 0) {
                address = response.getResult().getContractAddressInfos().get(0).getContractAddress();
                PTCContract ptcContract = new PTCContract();
                ptcContract.setContractAddress(address);
                ptcContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
                bbcContext.setPtcContract(ptcContract);
                getBBCLogger().info("setup ptc contract successful: {}", address);
            } else {
                throw new RuntimeException("failed to get ptc contract address");
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to get ptc contract address", e);
        }
    }

    @Override
    public ConsensusState readConsensusState(BigInteger height) {
        byte[] hash;
        byte[] parentHash;
        long stateTimestamp;
        byte[] stateData;
        byte[] consensusNodeInfo;
        byte[] endorsements;

        try {
            BIFBlockGetInfoRequest request = new BIFBlockGetInfoRequest();
            request.setBlockNumber(height.longValue());

            BIFBlockGetInfoResponse response = sdk.getBIFBlockService().getBlockInfo(request);
            if (response.getErrorCode() == 0) {
                hash = HexUtil.decodeHex(response.getResult().getHeader().getHash());
                parentHash = HexUtil.decodeHex(response.getResult().getHeader().getPreviousHash());
                stateTimestamp = response.getResult().getHeader().getConfirmTime() / 1000;
                stateData = HexUtil.decodeHex(response.getResult().getHeader().getConsensusValueHash());
            } else {
                throw new RuntimeException("failed to get block info");
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to get block info", e);
        }

        try {
            BIFBlockGetValidatorsRequest request = new BIFBlockGetValidatorsRequest();
            request.setBlockNumber(height.longValue());
            BIFBlockGetValidatorsResponse response = sdk.getBIFBlockService().getValidators(request);
            if (response.getErrorCode() == 0) {
                String[] validators = response.getResult().getValidators();
                String joinedString = String.join(",", validators);
                consensusNodeInfo = joinedString.getBytes();
            } else {
                throw new RuntimeException("failed to get validator info");
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to get validator info", e);
        }

        try {
            String urlStr = this.config.getUrl() + "/getLedger?with_consvalue=true&seq=" + height.toString();
            Request request = new Request.Builder()
                    .url(urlStr)
                    .build();

            Response response = client.newCall(request).execute();
            String jsonString = response.body().string();
            JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();
            if (jsonObject.get("error_code").getAsInt() == 0) {
                JsonObject consensusHeader = jsonObject.getAsJsonObject("result").getAsJsonObject("consensus_value").getAsJsonObject("consensus_header");
                String previousProof = consensusHeader.get("previous_proof").getAsString();
                endorsements = HexUtil.decodeHex(previousProof);
            } else {
                throw new RuntimeException("failed to get consensus value");
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to get consensus value", e);
        }
        return new ConsensusState(
                height,
                hash,
                parentHash,
                stateTimestamp,
                stateData,
                consensusNodeInfo,
                endorsements
        );
    }

    @Override
    public long querySDPMessageSeq(String senderDomain, String senderID, String receiverDomain, String receiverID) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)){
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getSdpContract())){
            throw new RuntimeException("empty sdp contract in bbc context");
        }

        // 2. query sequence
        long seq;
        try {
            String contractAddress = this.bbcContext.getSdpContract().getContractAddress();
            String callInput = StrUtil.format("{\"function\":\"querySDPMessageSeq(string,bytes32,string,bytes32)\",\"args\":\"'{}','{}','{}','{}'\",\"return\":\"returns(uint32)\"}", senderDomain, senderID.getBytes(), receiverDomain, receiverID.getBytes());
            BIFContractCallRequest request = new BIFContractCallRequest();
            request.setContractAddress(contractAddress);
            request.setInput(callInput);

            BIFContractCallResponse response = sdk.getBIFContractService().contractQuery(request);
            if (response.getErrorCode() == 0) {
                Map<String, Map<String, String>> resMap = (Map<String, Map<String, String>>) (response.getResult().getQueryRets().get(0));
                String res = resMap.get("result").get("data").trim();
                res = StrUtil.removeSuffix(
                        StrUtil.removePrefix(res, "[").trim(),
                        "]"
                ).trim();
                if (HexUtil.isHexNumber(res)) {
                    res = StrUtil.removePrefix(res.trim(), "0x");
                }
                seq = Long.parseLong(res);
                getBBCLogger().info("sdpMsg seq: {} (senderDomain: {}, senderID: {}, receiverDomain: {}, receiverID: {})",
                        seq,
                        senderDomain,
                        senderID,
                        receiverDomain,
                        receiverID
                );
            } else {
                throw new RuntimeException("failed to query sdpMsg seq");
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "failed to query sdpMsg seq (senderDomain: %s, senderID: %s, receiverDomain: %s, receiverID: %s)",
                    senderDomain,
                    senderID,
                    receiverDomain,
                    receiverID
            ), e);
        }

        return seq;
    }

    @Override
    public void setProtocol(String protocolAddress, String protocolType) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())){
            throw new RuntimeException("empty auth contract in bbc context");
        }

        // 3. set protocol to auth
        try {
            String contractAddress = this.bbcContext.getAuthMessageContract().getContractAddress();
            String invokeInput = StrUtil.format("{\"function\":\"setProtocol(address,uint32)\",\"args\":\"{},{}\"}", protocolAddress, BigInteger.valueOf(Long.parseLong(protocolType)));
            BIFContractInvokeRequest request = createBIFContractInvokeRequest(contractAddress, invokeInput);
            BIFContractInvokeResponse response = sdk.getBIFContractService().contractInvoke(request);
            if (response.getErrorCode() == 0) {
                if (queryTxResult(response.getResult().getHash())) {
                    this.bbcContext.getAuthMessageContract().setStatus(ContractStatusEnum.CONTRACT_READY);
                    getBBCLogger().info(
                            "set protocol (address: {}, type: {}) to auth {} by tx {} ",
                            protocolAddress, protocolType,
                            contractAddress,
                            response.getResult().getHash()
                    );
                } else {
                    throw new RuntimeException("failed to set protocol, transaction executing failed");
                }
            } else {
                throw new RuntimeException("failed to set protocol, transaction sending failed");
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to set protocol (address: %s, type: %s) to AM %s",
                            protocolAddress, protocolType, this.bbcContext.getAuthMessageContract().getContractAddress()
                    ), e
            );
        }
    }

    @Override
    public void setPtcContract(String ptcContractAddress) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())){
            throw new RuntimeException("empty am contract in bbc context");
        }

        // 3. set ptc to am
        try {
            String contractAddress = this.bbcContext.getAuthMessageContract().getContractAddress();
            String invokeInput = StrUtil.format("{\"function\":\"setPtcHub(address)\",\"args\":\"{}\"}", ptcContractAddress);
            BIFContractInvokeRequest request = createBIFContractInvokeRequest(contractAddress, invokeInput);
            BIFContractInvokeResponse response = sdk.getBIFContractService().contractInvoke(request);
            if (response.getErrorCode() == 0) {
                if (queryTxResult(response.getResult().getHash())) {
                    this.bbcContext.getAuthMessageContract().setStatus(ContractStatusEnum.CONTRACT_READY);
                    getBBCLogger().info(
                            "set ptc contract (address: {}) to AM {} by tx {}",
                            contractAddress,
                            ptcContractAddress,
                            response.getResult().getHash()
                    );
                } else {
                    throw new RuntimeException("failed to set ptc contract, transaction executing failed");
                }
            } else {
                throw new RuntimeException("failed to set ptc contract, transaction sending failed");
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to set ptc contract (address: %s) to AM %s",
                            ptcContractAddress,
                            this.bbcContext.getAuthMessageContract().getContractAddress()
                    ), e
            );
        }
    }

    @Override
    public void setAmContract(String amContractAddress) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getSdpContract())){
            throw new RuntimeException("empty sdp contract in bbc context");
        }

        // 3. set protocol to sdp
        try {
            String contractAddress = this.bbcContext.getSdpContract().getContractAddress();
            String invokeInput = StrUtil.format("{\"function\":\"setAmContract(address)\",\"args\":\"{}\"}", amContractAddress);
            BIFContractInvokeRequest request = createBIFContractInvokeRequest(contractAddress, invokeInput);
            BIFContractInvokeResponse response = sdk.getBIFContractService().contractInvoke(request);
            if (response.getErrorCode() == 0) {
                if (queryTxResult(response.getResult().getHash())) {
                    this.bbcContext.getSdpContract().setStatus(ContractStatusEnum.CONTRACT_READY);
                    getBBCLogger().info(
                            "set am contract (address: {}) to SDP {} by tx {}",
                            contractAddress,
                            amContractAddress,
                            response.getResult().getHash()
                    );
                } else {
                    throw new RuntimeException("failed to set protocol, transaction executing failed");
                }
            } else {
                throw new RuntimeException("failed to set protocol, transaction sending failed");
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to set auth contract (address: %s) to SDP %s",
                            amContractAddress,
                            this.bbcContext.getSdpContract().getContractAddress()
                    ), e
            );
        }
    }

    @Override
    public void setLocalDomain(String domain) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (StrUtil.isEmpty(this.bbcContext.getSdpContract().getContractAddress())) {
            throw new RuntimeException("none sdp contract address");
        }

        // 3. set domain to sdp
        try {
            String contractAddress = this.bbcContext.getSdpContract().getContractAddress();
            String invokeInput = StrUtil.format("{\"function\":\"setLocalDomain(string)\",\"args\":\"'{}'\"}", domain);
            BIFContractInvokeRequest request = createBIFContractInvokeRequest(contractAddress, invokeInput);
            BIFContractInvokeResponse response = sdk.getBIFContractService().contractInvoke(request);
            if (response.getErrorCode() == 0) {
                if (queryTxResult(response.getResult().getHash())) {
                    this.bbcContext.getSdpContract().setStatus(ContractStatusEnum.CONTRACT_READY);
                    getBBCLogger().info(
                            "set domain ({}) to SDP {} by tx {}",
                            domain,
                            contractAddress,
                            response.getResult().getHash()
                    );
                } else {
                    throw new RuntimeException("transaction executing failed, transaction executing failed");
                }
            } else {
                throw new RuntimeException("failed to set domain, transaction sending failed");
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to set domain (%s) to SDP %s",
                            domain,
                            this.bbcContext.getSdpContract().getContractAddress()
                    ), e
            );
        }
    }

    @Override
    public Long queryLatestHeight() {
        Long l;
        try {
            BIFBlockGetNumberInfoRequest request = new BIFBlockGetNumberInfoRequest();
            BIFBlockGetNumberResponse response = sdk.getBIFBlockService().getBlockNumber(request);
            l = response.getResult().getHeader().getBlockNumber();
            getBBCLogger().debug("latest height: {}", l);
        } catch (Exception e) {
            throw new RuntimeException("failed to query latest height", e);
        }
        return l;
    }

    @Override
    public CrossChainMessageReceipt readCrossChainMessageReceipt(String txHash) {
        CrossChainMessageReceipt crossChainMessageReceipt = new CrossChainMessageReceipt();
        try {

            BIFTransactionGetInfoRequest request = new BIFTransactionGetInfoRequest();
            request.setHash(txHash);
            BIFTransactionGetInfoResponse response = sdk.getBIFTransactionService().getTransactionInfo(request);
            if (response.getResult().getTransactions()[0].getErrorCode() == 0) {
                crossChainMessageReceipt.setConfirmed(true);
                crossChainMessageReceipt.setSuccessful(true);
                crossChainMessageReceipt.setTxhash(response.getResult().getTransactions()[0].getHash());
                crossChainMessageReceipt.setErrorMsg(response.getResult().getTransactions()[0].getErrorDesc());
            } else {
                crossChainMessageReceipt.setConfirmed(response.getResult().getTransactions()[0].getConfirmTime() > 0);
                crossChainMessageReceipt.setSuccessful(false);
                crossChainMessageReceipt.setTxhash(response.getResult().getTransactions()[0].getHash());
                crossChainMessageReceipt.setErrorMsg(response.getResult().getTransactions()[0].getErrorDesc());
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to read cross chain message receipt (txHash: %s)", txHash
                    ), e
            );
        }
        getBBCLogger().info("cross chain message receipt for txHash {} : {}", txHash, JSON.toJSONString(crossChainMessageReceipt));
        return crossChainMessageReceipt;
    }

    @Override
    public CrossChainMessageReceipt relayAuthMessage(byte[] rawMessage) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())){
            throw new RuntimeException("empty am contract in bbc context");
        }
        getBBCLogger().info("relay AM {} to {} ",
                HexUtil.encodeHexStr(rawMessage), this.bbcContext.getAuthMessageContract().getContractAddress());

        try {
            // 2. relay auth message
            String contractAddress = this.bbcContext.getAuthMessageContract().getContractAddress();
            String invokeInput = StrUtil.format("{\"function\":\"recvPkgFromRelayer(bytes)\",\"args\":\"'{}'\"}", HexUtil.encodeHexStr(rawMessage));
            BIFContractInvokeRequest request = createBIFContractInvokeRequest(contractAddress, invokeInput);
            BIFContractInvokeResponse response = sdk.getBIFContractService().contractInvoke(request);
            CrossChainMessageReceipt crossChainMessageReceipt = new CrossChainMessageReceipt();
            if (response.getErrorCode() == 0) {
                if (queryTxResult(response.getResult().getHash())) {
                    getBBCLogger().info("relay auth message successful tx {}", response.getResult().getHash());
                    crossChainMessageReceipt.setConfirmed(true);
                    crossChainMessageReceipt.setSuccessful(true);
                    crossChainMessageReceipt.setErrorMsg("");
                } else {
                    getBBCLogger().error("relay auth message failed tx {}", response.getResult().getHash());
                    crossChainMessageReceipt.setConfirmed(false);
                    crossChainMessageReceipt.setSuccessful(false);
                    crossChainMessageReceipt.setErrorMsg(response.getErrorDesc());
                }
            } else {
                throw new RuntimeException(response.getErrorDesc());
            }

            crossChainMessageReceipt.setTxhash(response.getResult().getHash());
            return crossChainMessageReceipt;
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("failed to relay AM %s to %s",
                            HexUtil.encodeHexStr(rawMessage), this.bbcContext.getAuthMessageContract().getContractAddress()
                    ), e
            );
        }
    }

    @Override
    public List<CrossChainMessage> readCrossChainMessagesByHeight(long height) {
        if (ObjectUtil.isNull(this.bbcContext)){
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())){
            throw new RuntimeException("empty am contract in bbc context");
        }

        try {
            // 1. get transactions
            BIFBlockGetTransactionsRequest request = new BIFBlockGetTransactionsRequest();
            request.setBlockNumber(height);
            BIFBlockGetTransactionsResponse response = sdk.getBIFBlockService().getTransactions(request);

            //获取区块信息
            BIFBlockGetInfoRequest blockGetInfoRequest = new BIFBlockGetInfoRequest();
            blockGetInfoRequest.setBlockNumber(height);
            BIFBlockGetInfoResponse blockGetInfoResponse = sdk.getBIFBlockService().getBlockInfo(blockGetInfoRequest);

            List<CrossChainMessage> messageList = ListUtil.toList();
            Arrays.stream(response.getResult().getTransactions()).forEach(
                    transaction -> {
                        BIFTransactionGetInfoRequest bifTransactionGetInfoRequest = new BIFTransactionGetInfoRequest();
                        bifTransactionGetInfoRequest.setHash(transaction.getHash());
                        BIFTransactionGetInfoResponse bifTransactionGetInfoResponse = sdk.getBIFTransactionService().getTransactionInfo(bifTransactionGetInfoRequest);

                        BIFOperation[] bifOperations = bifTransactionGetInfoResponse.getResult().getTransactions()[0].getTransaction().getOperations();
                        if (ObjectUtil.isEmpty(bifOperations)) {
                            return;
                        }

                        BIFLogInfo logInfo = bifOperations[0].getLog();
                        if (ObjectUtil.isNull(logInfo)) {
                            return;
                        }

                        if (StrUtil.equals(logInfo.getTopic(), "79b7516b1b7a6a39fb4b7b22e8667cd3744e5c27425292f8a9f49d1042c0c651")
                                && transaction.getTransaction().getSourceAddress().equals(this.bbcContext.getAuthMessageContract().getContractAddress())) {
                            String json = JsonUtils.toJSONString(logInfo);
                            AuthMsg authMsg = new AuthMsg();
                            Log log = authMsg.jsonToLog(json);
                            AuthMsg.SendAuthMessageEventResponse sendAuthMessageEventResponse = AuthMsg.getSendAuthMessageEventFromLog(log);

                            JsonObject jsonObject = new JsonObject();
                            jsonObject.addProperty("url", this.config.getUrl());
                            jsonObject.addProperty("txHash", transaction.getHash());

                            //构建跨链信息
                            CrossChainMessage crossChainMessage = CrossChainMessage.createCrossChainMessage(
                                    CrossChainMessage.CrossChainMessageType.AUTH_MSG,
                                    height,
                                    transaction.getConfirmTime() / 1000,
                                    HexUtil.decodeHex(blockGetInfoResponse.getResult().getHeader().getHash()),
                                    sendAuthMessageEventResponse.result.pkg,
                                    jsonObject.toString().getBytes(),
                                    "".getBytes(),
                                    HexUtil.decodeHex(transaction.getHash())
                            );
                            messageList.add(crossChainMessage);
                        }
                    }
            );
            if (!messageList.isEmpty()) {
                getBBCLogger().info("read cross chain messages (height: {}, msg_size: {})", height, messageList.size());
                getBBCLogger().debug("read cross chain messages (height: {}, msgs: {})",
                        height,
                        messageList.stream().map(JSON::toJSONString).collect(Collectors.joining(","))
                );
            }
            return messageList;
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to readCrossChainMessagesByHeight (Height: %d, contractAddr: %s, topic: %s)",
                            height,
                            this.bbcContext.getAuthMessageContract().getContractAddress(),
                            "79b7516b1b7a6a39fb4b7b22e8667cd3744e5c27425292f8a9f49d1042c0c651"
                    ), e
            );
        }
    }

    @Override
    public void updatePTCTrustRoot(PTCTrustRoot ptcTrustRoot) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getPtcContract())){
            throw new RuntimeException("empty ptc contract in bbc context");
        }

        try {
            // 2. check verify ptc trust root
            // 2.1 get bcdns cert
            String contractAddress = this.bbcContext.getPtcContract().getContractAddress();
            String callInput = StrUtil.format("{\"function\":\"getBCDNSTrustRoot()\",\"args\":\"\",\"return\":\"returns(bytes)\"}");
            BIFContractCallRequest request = new BIFContractCallRequest();
            request.setContractAddress(contractAddress);
            request.setInput(callInput);
            request.setFeeLimit(50000000L);
            BIFContractCallResponse response = sdk.getBIFContractService().contractQuery(request);
            AbstractCrossChainCertificate bcdnsCert;
            if (response.getErrorCode() == 0) {
                Map<String, Map<String, String>> resMap = (Map<String, Map<String, String>>) (response.getResult().getQueryRets().get(0));
                String res = resMap.get("result").get("data").trim();
                res = StrUtil.removeSuffix(
                        StrUtil.removePrefix(res, "[").trim(),
                        "]"
                ).trim();
                if (HexUtil.isHexNumber(res)) {
                    res = StrUtil.removePrefix(res.trim(), "0x");
                }

                bcdnsCert = CrossChainCertificateFactory.createCrossChainCertificate(HexUtil.decodeHex(res));
            } else {
                throw new RuntimeException("failed to query bcdns cert");
            }

            // 2.2 check verify ptc cert
            BCDNSTrustRootCredentialSubject bcdnsTrustRootCredentialSubject = BCDNSTrustRootCredentialSubject.decode(bcdnsCert.getCredentialSubject());
            if (!ptcTrustRoot.getPtcCrossChainCert().getIssuer().equals(bcdnsTrustRootCredentialSubject.getBcdnsRootOwner())) {
                throw new RuntimeException("ptc cert is invalid: wrong signer");
            }

            AbstractCrossChainCertificate ptcCert = ptcTrustRoot.getPtcCrossChainCert();
            if (!SignAlgoEnum.getByName(ptcCert.getProof().getSigAlgo().getName())
                    .getSigner()
                    .verify(bcdnsTrustRootCredentialSubject.getSubjectPublicKey(), ptcCert.getEncodedToSign(), ptcCert.getProof().getRawProof())) {
                throw new RuntimeException("ptc cert is invalid: invalid sig");
            }

            // 2.3 check verify ptc trust root sig
            PTCCredentialSubject ptcCredentialSubject = PTCCredentialSubject.decode(ptcCert.getCredentialSubject());
            if (!SignAlgoEnum.getByName(ptcTrustRoot.getSigAlgo().getName())
                    .getSigner()
                    .verify(ptcCredentialSubject.getSubjectPublicKey(), ptcTrustRoot.getEncodedToSign(), ptcTrustRoot.getSig())) {
                throw new RuntimeException("ptc trust root sig invalid");
            }

            // 3. update ptc trust root
            String invokeInput = StrUtil.format("{\"function\":\"updatePTCTrustRoot(bytes)\",\"args\":\"'{}'\"}", HexUtil.encodeHexStr(ptcTrustRoot.encode()));
            BIFContractInvokeRequest bifContractInvokeRequest = createBIFContractInvokeRequest(contractAddress, invokeInput);
            BIFContractInvokeResponse bifContractInvokeResponse = sdk.getBIFContractService().contractInvoke(bifContractInvokeRequest);
            if (bifContractInvokeResponse.getErrorCode() == 0) {
                if (queryTxResult(bifContractInvokeResponse.getResult().getHash())) {
                    getBBCLogger().info("update ptc root successful tx {}", bifContractInvokeResponse.getResult().getHash());
                } else {
                    throw new RuntimeException("failed to update ptc root, transaction executing failed");
                }
            } else {
                throw new RuntimeException("failed to update ptc root, transaction sending failed");
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("failed to update ptc trust root %s to PTC %s",
                            HexUtil.encodeHexStr(ptcTrustRoot.encode()), this.bbcContext.getPtcContract().getContractAddress()
                    ), e
            );
        }
    }

    @Override
    public boolean hasPTCTrustRoot(ObjectIdentity ptcOwnerOid) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)){
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getPtcContract())){
            throw new RuntimeException("empty ptc contract in bbc context");
        }

        // 2. query trust root
        boolean result;
        try {
            String contractAddress = this.bbcContext.getPtcContract().getContractAddress();
            String callInput = StrUtil.format("{\"function\":\"hasPTCTrustRoot(bytes)\",\"args\":\"'{}'\",\"return\":\"returns(bool)\"}", HexUtil.encodeHexStr(ptcOwnerOid.encode()));
            BIFContractCallRequest request = new BIFContractCallRequest();
            request.setContractAddress(contractAddress);
            request.setInput(callInput);
            BIFContractCallResponse response = sdk.getBIFContractService().contractQuery(request);
            if (response.getErrorCode() == 0) {
                Map<String, Map<String, String>> resMap = (Map<String, Map<String, String>>) (response.getResult().getQueryRets().get(0));
                String res = resMap.get("result").get("data").trim();
                res = StrUtil.removeSuffix(
                        StrUtil.removePrefix(res, "[").trim(),
                        "]"
                ).trim();

                result = res.equals("true");
                getBBCLogger().info("PTC trust root is existed: {}", result);
            } else {
                throw new RuntimeException("failed to query PTC trust root");
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "failed to query PTC trust root: %s", ptcOwnerOid.toHex()
            ), e);
        }
        return result;
    }

    @Override
    public PTCTrustRoot getPTCTrustRoot(ObjectIdentity ptcOwnerOid) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)){
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getPtcContract())){
            throw new RuntimeException("empty ptc contract in bbc context");
        }

        // 2. query trust root
        PTCTrustRoot ptcTrustRoot;
        try {
            String contractAddress = this.bbcContext.getPtcContract().getContractAddress();
            String callInput = StrUtil.format("{\"function\":\"getPTCTrustRoot(bytes)\",\"args\":\"'{}'\",\"return\":\"returns(bytes)\"}", HexUtil.encodeHexStr(ptcOwnerOid.encode()));
            BIFContractCallRequest request = new BIFContractCallRequest();
            request.setContractAddress(contractAddress);
            request.setInput(callInput);
            request.setFeeLimit(50000000L);
            BIFContractCallResponse response = sdk.getBIFContractService().contractQuery(request);
            if (response.getErrorCode() == 0) {
                Map<String, Map<String, String>> resMap = (Map<String, Map<String, String>>) (response.getResult().getQueryRets().get(0));
                String res = resMap.get("result").get("data").trim();
                res = StrUtil.removeSuffix(
                        StrUtil.removePrefix(res, "[").trim(),
                        "]"
                ).trim();
                if (HexUtil.isHexNumber(res)) {
                    res = StrUtil.removePrefix(res.trim(), "0x");
                }

                ptcTrustRoot = PTCTrustRoot.decode(HexUtil.decodeHex(res));
                getBBCLogger().info("PTC trust root: {} ", ptcTrustRoot.toString());
                return ptcTrustRoot;
            } else {
                throw new RuntimeException("failed to query PTC trust root");
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "failed to query PTC trust root: %s", ptcOwnerOid.toHex()
            ), e);
        }
    }

    @Override
    public boolean hasTpBta(CrossChainLane tpbtaLane, int tpBtaVersion) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)){
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getPtcContract())){
            throw new RuntimeException("empty ptc contract in bbc context");
        }

        // 2. query trust root
        boolean result;
        try {
            String contractAddress = this.bbcContext.getPtcContract().getContractAddress();
            String callInput = StrUtil.format("{\"function\":\"hasTpBta(bytes,uint32)\",\"args\":\"'{}',{}\",\"return\":\"returns(bool)\"}", HexUtil.encodeHexStr(tpbtaLane.encode()), tpBtaVersion);
            BIFContractCallRequest request = new BIFContractCallRequest();
            request.setContractAddress(contractAddress);
            request.setInput(callInput);

            BIFContractCallResponse response = sdk.getBIFContractService().contractQuery(request);
            if (response.getErrorCode() == 0) {
                Map<String, Map<String, String>> resMap = (Map<String, Map<String, String>>) (response.getResult().getQueryRets().get(0));
                String res = resMap.get("result").get("data").trim();
                res = StrUtil.removeSuffix(
                        StrUtil.removePrefix(res, "[").trim(),
                        "]"
                ).trim();

                result = res.equals("true");
                getBBCLogger().info("TP-BTA is existed: {}", result);
            } else {
                throw new RuntimeException("failed to query TP-BTA");
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "failed to query TB-BTA, tpbtaLane(%s), tpBtaVersion(%d)", HexUtil.encodeHexStr(tpbtaLane.encode()), tpBtaVersion
            ), e);
        }
        return result;
    }

    @Override
    public ThirdPartyBlockchainTrustAnchor getTpBta(CrossChainLane tpbtaLane, int tpBtaVersion) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)){
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getPtcContract())){
            throw new RuntimeException("empty ptc contract in bbc context");
        }

        // 2. query third party blockchain trust anchor
        ThirdPartyBlockchainTrustAnchor thirdPartyBlockchainTrustAnchor;
        try {
            String contractAddress = this.bbcContext.getPtcContract().getContractAddress();
            String callInput = StrUtil.format("{\"function\":\"getTpBta(bytes,uint32)\",\"args\":\"'{}',{}\",\"return\":\"returns(bytes)\"}", HexUtil.encodeHexStr(tpbtaLane.encode()), tpBtaVersion);
            BIFContractCallRequest request = new BIFContractCallRequest();
            request.setContractAddress(contractAddress);
            request.setInput(callInput);
            request.setFeeLimit(50000000L);

            BIFContractCallResponse response = sdk.getBIFContractService().contractQuery(request);
            if (response.getErrorCode() == 0) {
                Map<String, Map<String, String>> resMap = (Map<String, Map<String, String>>) (response.getResult().getQueryRets().get(0));
                String res = resMap.get("result").get("data").trim();
                res = StrUtil.removeSuffix(
                        StrUtil.removePrefix(res, "[").trim(),
                        "]"
                ).trim();
                if (HexUtil.isHexNumber(res)) {
                    res = StrUtil.removePrefix(res.trim(), "0x");
                }

                thirdPartyBlockchainTrustAnchor = ThirdPartyBlockchainTrustAnchor.decode(HexUtil.decodeHex(res));
                getBBCLogger().info("Third party blockchain trust anchor: {} ", HexUtil.encodeHexStr(thirdPartyBlockchainTrustAnchor.encode()));
            } else {
                throw new RuntimeException("failed to query third party blockchain trust anchor");
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "failed to query third party blockchain trust anchor, tpbtaLane(%s), tpBtaVersion(%d)",
                    HexUtil.encodeHexStr(tpbtaLane.encode()),
                    tpBtaVersion
            ), e);
        }
        return thirdPartyBlockchainTrustAnchor;
    }

    @Override
    public void addTpBta(ThirdPartyBlockchainTrustAnchor tpbta) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getPtcContract())){
            throw new RuntimeException("empty ptc contract in bbc context");
        }

        try {
            //2. check verify tp-bta
            if (!hasPTCTrustRoot(tpbta.getSignerPtcCredentialSubject().getApplicant())) {
                throw new RuntimeException("no ptc trust root found");
            }

            if (!hasPTCVerifyAnchor(tpbta.getSignerPtcCredentialSubject().getApplicant(), tpbta.getPtcVerifyAnchorVersion())) {
                throw new RuntimeException("no ptc verify anchor found");
            }
            PTCVerifyAnchor ptcVerifyAnchor = getPTCVerifyAnchor(tpbta.getSignerPtcCredentialSubject().getApplicant(), tpbta.getPtcVerifyAnchorVersion());

            if (!ptcVerifyAnchor.getVersion().equals(tpbta.getPtcVerifyAnchorVersion())) {
                throw new RuntimeException("verify anchor version not equal");
            }

            CommitteeVerifyAnchor committeeVerifyAnchor = CommitteeVerifyAnchor.decode(ptcVerifyAnchor.getAnchor());
            CommitteeEndorseProof committeeEndorseProof = CommitteeEndorseProof.decode(tpbta.getEndorseProof());
            if (!StrUtil.equals(committeeVerifyAnchor.getCommitteeId(), committeeEndorseProof.getCommitteeId())) {
                throw new RuntimeException("committee id in proof not equal with the one in verify anchor");
            }

            byte[] encodedToSign = tpbta.getEncodedToSign();
            int correct = 0;
            for (int i = 0; i < committeeEndorseProof.getSigs().size(); i++) {
                CommitteeNodeProof info = committeeEndorseProof.getSigs().get(i);
                for (int j = 0; j < committeeVerifyAnchor.getAnchors().size(); j++) {
                    if (StrUtil.equals(info.getNodeId(), committeeVerifyAnchor.getAnchors().get(j).getNodeId())) {
                        boolean res = false;
                        for (int k = 0; k < committeeVerifyAnchor.getAnchors().get(j).getNodePublicKeys().size(); k++) {
                            res = SignAlgoEnum.getByName(info.getSignAlgo().getName())
                                    .getSigner()
                                    .verify(committeeVerifyAnchor.getAnchors().get(j).getNodePublicKeys().get(k).getPublicKey(), encodedToSign, info.getSig());
                            if (res) {
                                break;
                            }
                        }
                        if (res) {
                            correct++;
                            break;
                        }
                    }
                }
            }

            if (3*correct <= 2*committeeVerifyAnchor.getAnchors().size()) {
                throw new RuntimeException("the number of signatures is less than 2/3 ");
            }

            //3. update tp-bta
            String contractAddress = this.bbcContext.getPtcContract().getContractAddress();
            String invokeInput = StrUtil.format("{\"function\":\"addTpBta(bytes)\",\"args\":\"'{}'\"}", HexUtil.encodeHexStr(tpbta.encode()));
            BIFContractInvokeRequest request = createBIFContractInvokeRequest(contractAddress, invokeInput);
            BIFContractInvokeResponse response = sdk.getBIFContractService().contractInvoke(request);
            if (response.getErrorCode() == 0) {
                if (queryTxResult(response.getResult().getHash())) {
                    getBBCLogger().info("add TB-BTA successful tx {}", response.getResult().getHash());
                } else {
                    throw new RuntimeException("failed to add tp-bta, transaction executing failed");
                }
            } else {
                throw new RuntimeException("failed to add tp-bta, transaction sending failed");
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("failed to add tp-bta: %s", HexUtil.encodeHexStr(tpbta.encode())), e
            );
        }
    }

    @Override
    public Set<PTCTypeEnum> getSupportedPTCType() {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)){
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getPtcContract())){
            throw new RuntimeException("empty ptc contract in bbc context");
        }

        // 2. query ptc type
//        try {
//            String contractAddress = this.bbcContext.getPtcContract().getContractAddress();
//            String callInput = StrUtil.format("{\"function\":\"getSupportedPTCType()\",\"args\":\"\"}");
//            BIFContractCallRequest request = new BIFContractCallRequest();
//            request.setContractAddress(contractAddress);
//            request.setInput(callInput);
//
//            BIFContractCallResponse response = sdk.getBIFContractService().contractQuery(request);
//            if (response.getErrorCode() == 0) {
//                Map<String, Map<String, String>> resMap = (Map<String, Map<String, String>>) (response.getResult().getQueryRets().get(0));
//                String res = resMap.get("result").get("data").trim();
//                res = res.substring(2,res.length() - 2);
//                String[] strArray = res.split(",");
//                List<BigInteger> bigIntegerList = new ArrayList<>();
//                for (String numStr : strArray) {
//                    bigIntegerList.add(new BigInteger(numStr));
//                }
//                getBBCLogger().info("get support ptc type: {}", bigIntegerList);
//                return bigIntegerList.stream().map(x -> PTCTypeEnum.valueOf(x.byteValueExact())).collect(Collectors.toSet());
//            } else {
//                throw new RuntimeException("failed to query PTC type");
//            }
//        } catch (Exception e) {
//            throw new RuntimeException("failed to query PTC type", e);
//        }
        return CollectionUtil.newHashSet(PTCTypeEnum.COMMITTEE);
    }

    @Override
    public boolean hasPTCVerifyAnchor(ObjectIdentity ptcOwnerOid, BigInteger version) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)){
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getPtcContract())){
            throw new RuntimeException("empty ptc contract in bbc context");
        }

        // 2. query ptc verify anchor
        boolean result;
        try {
            String contractAddress = this.bbcContext.getPtcContract().getContractAddress();
            String callInput = StrUtil.format("{\"function\":\"hasPTCVerifyAnchor(bytes,uint256)\",\"args\":\"'{}',{}\",\"return\":\"returns(bool)\"}", HexUtil.encodeHexStr(ptcOwnerOid.encode()), version.longValue());
            BIFContractCallRequest request = new BIFContractCallRequest();
            request.setContractAddress(contractAddress);
            request.setInput(callInput);

            BIFContractCallResponse response = sdk.getBIFContractService().contractQuery(request);
            if (response.getErrorCode() == 0) {
                Map<String, Map<String, String>> resMap = (Map<String, Map<String, String>>) (response.getResult().getQueryRets().get(0));
                String res = resMap.get("result").get("data").trim();
                res = StrUtil.removeSuffix(
                        StrUtil.removePrefix(res, "[").trim(),
                        "]"
                ).trim();

                result = res.equals("true");
                getBBCLogger().info("PTC verify anchor is existed: {}", result);
            } else {
                throw new RuntimeException("failed to query PTC verify anchor");
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "query ptc verify anchor, ptcOwnerOid(%s), version(%d)",
                    HexUtil.encodeHexStr(ptcOwnerOid.encode()),
                    version
            ), e);
        }
        return result;
    }

    @Override
    public PTCVerifyAnchor getPTCVerifyAnchor(ObjectIdentity ptcOwnerOid, BigInteger version) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)){
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getPtcContract())){
            throw new RuntimeException("empty ptc contract in bbc context");
        }

        // 2. query ptc verify anchor
        PTCVerifyAnchor ptcVerifyAnchor;
        try {
            String contractAddress = this.bbcContext.getPtcContract().getContractAddress();
            String callInput = StrUtil.format("{\"function\":\"getPTCVerifyAnchor(bytes,uint256)\",\"args\":\"'{}',{}\",\"return\":\"returns(bytes)\"}", HexUtil.encodeHexStr(ptcOwnerOid.encode()), version.intValue());
            BIFContractCallRequest request = new BIFContractCallRequest();
            request.setContractAddress(contractAddress);
            request.setInput(callInput);
            request.setFeeLimit(50000000L);

            BIFContractCallResponse response = sdk.getBIFContractService().contractQuery(request);
            if (response.getErrorCode() == 0) {
                Map<String, Map<String, String>> resMap = (Map<String, Map<String, String>>) (response.getResult().getQueryRets().get(0));
                String res = resMap.get("result").get("data").trim();
                res = StrUtil.removeSuffix(
                        StrUtil.removePrefix(res, "[").trim(),
                        "]"
                ).trim();
                if (HexUtil.isHexNumber(res)) {
                    res = StrUtil.removePrefix(res.trim(), "0x");
                }

                ptcVerifyAnchor = PTCVerifyAnchor.decode(HexUtil.decodeHex(res));
                getBBCLogger().info("query ptc verify anchor: {} ", HexUtil.encodeHexStr(ptcVerifyAnchor.encode()));
            } else {
                throw new RuntimeException("failed to query ptc verify anchor");
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "query ptc verify anchor, ptcOwnerOid(%s), version(%d)",
                    HexUtil.encodeHexStr(ptcOwnerOid.encode()),
                    version
            ), e);
        }
        return ptcVerifyAnchor;
    }

    @Override
    public BlockState queryValidatedBlockStateByDomain(CrossChainDomain recvDomain) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)){
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getSdpContract())){
            throw new RuntimeException("empty sdp contract in bbc context");
        }

        // 2. query validate block state
        BlockState blockState = new BlockState();
        try {
            String contractAddress = this.bbcContext.getSdpContract().getContractAddress();
            String callInput = StrUtil.format("{\"function\":\"queryValidatedBlockStateByDomain(string)\",\"args\":\"'{}'\",\"return\":\"returns(bytes32,uint256,uint64)\"}", recvDomain.getDomain());
            BIFContractCallRequest request = new BIFContractCallRequest();
            request.setContractAddress(contractAddress);
            request.setInput(callInput);

            BIFContractCallResponse response = sdk.getBIFContractService().contractQuery(request);
            if (response.getErrorCode() == 0) {
                Map<String, Map<String, String>> resMap = (Map<String, Map<String, String>>) (response.getResult().getQueryRets().get(0));
                String res = resMap.get("result").get("data").trim();
                res = StrUtil.removeSuffix(
                        StrUtil.removePrefix(res, "[").trim(),
                        "]"
                ).trim();

                String[] parts = res.split(",");

                blockState.setDomain(recvDomain);
                blockState.setHash(HexUtil.decodeHex(parts[0]));
                blockState.setHeight(new BigInteger(parts[1]));
                blockState.setTimestamp(Long.parseLong(parts[2]));

                return blockState;
            } else {
                throw new RuntimeException("failed to query sdp validate block state");
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "query sdp validate block state, recvDomain(%s)",
                    recvDomain.getDomain()
            ), e);
        }
    }

    @Override
    public CrossChainMessageReceipt recvOffChainException(String exceptionMsgAuthor, byte[] exceptionMsgPkg) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)){
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getSdpContract())){
            throw new RuntimeException("empty sdp contract in bbc context");
        }

        // 2. recv off-chain exception
        CrossChainMessageReceipt crossChainMessageReceipt = new CrossChainMessageReceipt();
        try {
            String contractAddress = this.bbcContext.getSdpContract().getContractAddress();
            String invokeInput = StrUtil.format("{\"function\":\"recvOffChainException(bytes32,bytes)\",\"args\":\"'{}','{}'\"}", exceptionMsgAuthor, HexUtil.encodeHexStr(exceptionMsgPkg));
            BIFContractInvokeRequest request = createBIFContractInvokeRequest(contractAddress, invokeInput);
            BIFContractInvokeResponse response = sdk.getBIFContractService().contractInvoke(request);
            if (response.getErrorCode() == 0) {
                crossChainMessageReceipt.setTxhash(response.getResult().getHash());
                crossChainMessageReceipt.setConfirmed(true);
                crossChainMessageReceipt.setSuccessful(queryTxResult(response.getResult().getHash()));
                if (!crossChainMessageReceipt.isSuccessful()) {
                    crossChainMessageReceipt.setErrorMsg("TX FAILED");
                }
                return crossChainMessageReceipt;
            } else {
                throw new RuntimeException("failed to recv off-chain exception, transaction sending failed: " + response.getErrorDesc());
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to recv off-chain exception to sdp contract(%s)",
                            this.bbcContext.getSdpContract().getContractAddress()
                    ), e
            );
        }
    }

    @Override
    public CrossChainMessageReceipt reliableRetry(ReliableCrossChainMessage msg) {
        return new CrossChainMessageReceipt();
    }

    private ThirdPartyProof decodeTpProofFromMsg(byte[] proofsData) {
        int _len = proofsData.length;
        int _offset = 0;
        // hints len
        byte[] hints_len_bytes = new byte[4];
        System.arraycopy(proofsData, _offset, hints_len_bytes, 0, 4);
        _offset += 4;
        int hints_len = (int) extractUint32(hints_len_bytes, 4);
        // hints
        byte[] hints = new byte[hints_len];
        System.arraycopy(proofsData, _offset, hints, 0, hints_len);
        _offset += hints_len;

        // proof lens
        byte[] proof_len_bytes = new byte[4];
        System.arraycopy(proofsData, _offset, proof_len_bytes, 0, 4);
        _offset += 4;
        int proof_len = (int) extractUint32(proof_len_bytes, 4);
        // proof
        byte[] proof = new byte[proof_len];
        System.arraycopy(proofsData, _offset, proof, 0, proof_len);
        _offset += proof_len;

        return ThirdPartyProof.decode(proof);
    }

    private long extractUint32(byte[] b, int offset) {
        long l = 0;
        for (int bit = 4; bit > 0; bit--) {
            l <<= 8;
            l |= b[offset - bit] & 0xFF;
        }
        return l;
    }
}