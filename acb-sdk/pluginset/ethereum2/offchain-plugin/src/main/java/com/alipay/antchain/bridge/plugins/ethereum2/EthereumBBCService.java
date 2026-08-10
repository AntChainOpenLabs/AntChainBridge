/*
 * Copyright 2023 Ant Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alipay.antchain.bridge.plugins.ethereum2;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.*;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.CrossChainCertificateTypeEnum;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTypeEnum;
import com.alipay.antchain.bridge.commons.core.ptc.PTCVerifyAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.rcc.ReliableCrossChainMessage;
import com.alipay.antchain.bridge.plugins.ethereum2.abi.SDPMsg;
import com.alipay.antchain.bridge.plugins.ethereum2.conf.EthereumConfig;
import com.alipay.antchain.bridge.plugins.ethereum2.core.AcbEthClient;
import com.alipay.antchain.bridge.plugins.ethereum2.core.EthConsensusEndorsements;
import com.alipay.antchain.bridge.plugins.lib.BBCService;
import com.alipay.antchain.bridge.plugins.spi.bbc.AbstractBBCService;
import lombok.Getter;
import lombok.NonNull;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.abi.datatypes.Address;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;

import static com.alipay.antchain.bridge.plugins.ethereum2.abi.AuthMsg.SENDAUTHMESSAGE_EVENT;

@BBCService(products = "ethereum2", pluginId = "plugin-ethereum2")
@Getter
public class EthereumBBCService extends AbstractBBCService {

    private EthereumConfig config;

    private AbstractBBCContext bbcContext;

    private AcbEthClient acbEthClient;

    @Override
    public void startup(AbstractBBCContext abstractBBCContext) {
        getBBCLogger().info("ETH BBCService startup with context: {}", new String(abstractBBCContext.getConfForBlockchainClient()));

        if (ObjectUtil.isNull(abstractBBCContext)) {
            throw new RuntimeException("null bbc context");
        }
        if (ObjectUtil.isEmpty(abstractBBCContext.getConfForBlockchainClient())) {
            throw new RuntimeException("empty blockchain client conf");
        }

        // 1. Obtain the configuration information
        try {
            config = EthereumConfig.fromJsonString(new String(abstractBBCContext.getConfForBlockchainClient()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!config.isKmsService() && StrUtil.isEmpty(config.getPrivateKey())) {
            throw new RuntimeException("private key is empty");
        }

        if (StrUtil.isEmpty(config.getUrl())) {
            throw new RuntimeException("ethereum url is empty");
        }

        this.acbEthClient = new AcbEthClient(config, getBBCLogger());

        // 6. set context
        this.bbcContext = abstractBBCContext;

        // 7. set the pre-deployed contracts into context
        if (ObjectUtil.isNull(abstractBBCContext.getAuthMessageContract())
                && StrUtil.isNotEmpty(this.config.getAmContractAddressDeployed())) {
            AuthMessageContract authMessageContract = new AuthMessageContract();
            authMessageContract.setContractAddress(this.config.getAmContractAddressDeployed());
            authMessageContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            this.bbcContext.setAuthMessageContract(authMessageContract);
            getBBCLogger().info("set the pre-deployed am contracts into context: {}: ", this.config.getAmContractAddressDeployed());
        }

        if (ObjectUtil.isNull(abstractBBCContext.getSdpContract())
                && StrUtil.isNotEmpty(this.config.getSdpContractAddressDeployed())) {
            SDPContract sdpContract = new SDPContract();
            sdpContract.setContractAddress(this.config.getSdpContractAddressDeployed());
            sdpContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            this.bbcContext.setSdpContract(sdpContract);
            getBBCLogger().info("set the pre-deployed sdp contracts into context: {}: ", this.config.getSdpContractAddressDeployed());
        }

        if (ObjectUtil.isNull(abstractBBCContext.getMonitorContract())
                && StrUtil.isNotEmpty(this.config.getMonitorContractAddressDeployed())) {
            MonitorContract monitorContract = new MonitorContract();
            monitorContract.setContractAddress(this.config.getMonitorContractAddressDeployed());
            monitorContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            this.bbcContext.setMonitorContract(monitorContract);
            getBBCLogger().info("set the pre-deployed monitor contracts into context: {}: ", this.config.getMonitorContractAddressDeployed());
        }

        if (ObjectUtil.isNull(abstractBBCContext.getPtcContract())
            && StrUtil.isNotEmpty(this.config.getPtcHubContractAddressDeployed())) {
            PTCContract ptcContract = new PTCContract();
            ptcContract.setContractAddress(this.config.getPtcHubContractAddressDeployed());
            ptcContract.setStatus(ContractStatusEnum.CONTRACT_READY);
            this.bbcContext.setPtcContract(ptcContract);
            getBBCLogger().info("set the pre-deployed ptc contracts into context: {}: ", this.config.getPtcHubContractAddressDeployed());
        }

        if (ObjectUtil.isEmpty(this.config.getProxyAdmin()) && this.config.isUpgradableContracts()) {
            var proxyAdmin = this.acbEthClient.deployProxyAdmin();
            this.config.setProxyAdmin(proxyAdmin.getContractAddress());
            this.bbcContext.setConfForBlockchainClient(this.config.toJsonString().getBytes());
            getBBCLogger().info("deploy proxy admin contract success! address: {}", proxyAdmin.getContractAddress());
        }
    }

    @Override
    public void shutdown() {
        getBBCLogger().info("shut down ETH BBCService!");
        this.acbEthClient.shutdown();
    }

    @Override
    public AbstractBBCContext getContext() {
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }

        getBBCLogger().debug("ETH BBCService context (amAddr: {}, amStatus: {}, sdpAddr: {}, sdpStatus: {}, monitorAddr: {}, monitorStatus: {})",
                this.bbcContext.getAuthMessageContract() != null ? this.bbcContext.getAuthMessageContract().getContractAddress() : "",
                this.bbcContext.getAuthMessageContract() != null ? this.bbcContext.getAuthMessageContract().getStatus() : "",
                this.bbcContext.getSdpContract() != null ? this.bbcContext.getSdpContract().getContractAddress() : "",
                this.bbcContext.getSdpContract() != null ? this.bbcContext.getSdpContract().getStatus() : "",
                this.bbcContext.getMonitorContract() != null ? this.bbcContext.getMonitorContract().getContractAddress() : "",
                this.bbcContext.getMonitorContract() != null ? this.bbcContext.getMonitorContract().getStatus() : ""
        );

        this.bbcContext.setConfForBlockchainClient(this.config.toJsonString().getBytes());
        return this.bbcContext;
    }

    @Override
    public CrossChainMessageReceipt readCrossChainMessageReceipt(String txHash) {
        // 1. Obtain Ethereum receipt according to transaction hash
        TransactionReceipt transactionReceipt = acbEthClient.queryTransactionReceipt(txHash);

        // 2. Construct cross-chain message receipt
        CrossChainMessageReceipt crossChainMessageReceipt = getCrossChainMessageReceipt(transactionReceipt);
        getBBCLogger().info("cross chain message receipt for txhash {} : {}", txHash, JSON.toJSONString(crossChainMessageReceipt));

        return crossChainMessageReceipt;
    }

    private CrossChainMessageReceipt getCrossChainMessageReceipt(TransactionReceipt transactionReceipt) {
        CrossChainMessageReceipt crossChainMessageReceipt = new CrossChainMessageReceipt();
        if (transactionReceipt == null) {
            // If the transaction is not packaged, the return receipt is empty
            crossChainMessageReceipt.setConfirmed(false);
            crossChainMessageReceipt.setSuccessful(false);
            crossChainMessageReceipt.setTxhash("");
            crossChainMessageReceipt.setErrorMsg("");
            return crossChainMessageReceipt;
        }

        BigInteger currBlockNum = acbEthClient.queryLatestBlockNumber();
        if (transactionReceipt.getBlockNumber().compareTo(currBlockNum) > 0) {
            crossChainMessageReceipt.setConfirmed(false);
            crossChainMessageReceipt.setSuccessful(true);
            crossChainMessageReceipt.setTxhash(transactionReceipt.getTransactionHash());
            crossChainMessageReceipt.setErrorMsg("");
            return crossChainMessageReceipt;
        }

        List<SDPMsg.ReceiveMessageEventResponse> receiveMessageEventResponses = SDPMsg.getReceiveMessageEvents(transactionReceipt);
        if (ObjectUtil.isNotEmpty(receiveMessageEventResponses)) {
            SDPMsg.ReceiveMessageEventResponse response = receiveMessageEventResponses.get(0);
            crossChainMessageReceipt.setConfirmed(true);
            crossChainMessageReceipt.setSuccessful(transactionReceipt.isStatusOK() && response.result);
            crossChainMessageReceipt.setTxhash(transactionReceipt.getTransactionHash());
            crossChainMessageReceipt.setErrorMsg(
                    transactionReceipt.isStatusOK() ? StrUtil.format(
                            "SDP calls biz contract: {}", response.result ? "SUCCESS" : response.errMsg
                    ) : StrUtil.emptyToDefault(transactionReceipt.getRevertReason(), "")
            );
            getBBCLogger().info(
                    "event receiveMessage from SDP contract is found in no.{} tx {} of block {} : " +
                    "( send_domain: {}, sender: {}, receiver: {}, biz_call: {}, err_msg: {} )",
                    transactionReceipt.getTransactionIndex().toString(), transactionReceipt.getTransactionHash(), transactionReceipt.getBlockHash(),
                    response.senderDomain, HexUtil.encodeHexStr(response.senderID), response.receiverID, response.result.toString(),
                    response.errMsg
            );
            return crossChainMessageReceipt;
        }

        crossChainMessageReceipt.setConfirmed(true);
        crossChainMessageReceipt.setSuccessful(transactionReceipt.isStatusOK());
        crossChainMessageReceipt.setTxhash(transactionReceipt.getTransactionHash());
        crossChainMessageReceipt.setErrorMsg(StrUtil.emptyToDefault(transactionReceipt.getRevertReason(), ""));

        return crossChainMessageReceipt;
    }

    @Override
    public List<CrossChainMessage> readCrossChainMessagesByHeight(long slot) {
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())) {
            throw new RuntimeException("empty am contract in bbc context");
        }

        try {
            return acbEthClient.readAuthMessagesFromBlock(BigInteger.valueOf(slot), this.bbcContext.getAuthMessageContract().getContractAddress());
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to readCrossChainMessagesByHeight (slot: %d, contractAddr: %s, topic: %s)",
                            slot,
                            this.bbcContext.getAuthMessageContract().getContractAddress(),
                            SENDAUTHMESSAGE_EVENT
                    ), e
            );
        }
    }

    @Override
    public Long queryLatestHeight() {
        return acbEthClient.queryLatestSlot().longValue();
    }

    @Override
    public void setupAuthMessageContract() {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (this.config.isUpgradableContracts() && StrUtil.isEmpty(this.config.getProxyAdmin())) {
            throw new RuntimeException("empty proxy admin");
        }
        if (ObjectUtil.isNotNull(this.bbcContext.getAuthMessageContract())
                && StrUtil.isNotEmpty(this.bbcContext.getAuthMessageContract().getContractAddress())) {
            // If the contract has been pre-deployed and the contract address is configured in the configuration file,
            // there is no need to redeploy.
            return;
        }

        // 2. deploy contract
        String amContractAddr = acbEthClient.deployAuthMsg();

        AuthMessageContract authMessageContract = new AuthMessageContract();
        authMessageContract.setContractAddress(amContractAddr);
        authMessageContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
        bbcContext.setAuthMessageContract(authMessageContract);

        getBBCLogger().info("setup am contract successful: {}", amContractAddr);
    }

    @Override
    public void setupSDPMessageContract() {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (this.config.isUpgradableContracts() && StrUtil.isEmpty(this.config.getProxyAdmin())) {
            throw new RuntimeException("empty proxy admin");
        }
        if (ObjectUtil.isNotNull(this.bbcContext.getSdpContract())
            && StrUtil.isNotEmpty(this.bbcContext.getSdpContract().getContractAddress())) {
            // If the contract has been pre-deployed and the contract address is configured in the configuration file,
            // there is no need to redeploy.
            return;
        }

        // 2. deploy contract
        var sdpContractAddr = acbEthClient.deploySdpContract();

        SDPContract sdpContract = new SDPContract();
        sdpContract.setContractAddress(sdpContractAddr);
        sdpContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
        bbcContext.setSdpContract(sdpContract);
        getBBCLogger().info("setup sdp contract successful: {}", sdpContractAddr);
    }

    @Override
    public void setupMonitorContract() {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (this.config.isUpgradableContracts() && StrUtil.isEmpty(this.config.getProxyAdmin())) {
            throw new RuntimeException("empty proxy admin");
        }
        if (ObjectUtil.isNotNull(this.bbcContext.getMonitorContract())
                && StrUtil.isNotEmpty(this.bbcContext.getMonitorContract().getContractAddress())) {
            // If the contract has been pre-deployed and the contract address is configured in the configuration file,
            // there is no need to redeploy.
            return;
        }

        // 2. deploy contract
        var monitorContractAddr = acbEthClient.deployMonitorContract(acbEthClient.deployMonitorVerifierContract());

        MonitorContract monitorContract = new MonitorContract();
        monitorContract.setContractAddress(monitorContractAddr);
        monitorContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
        bbcContext.setMonitorContract(monitorContract);
        getBBCLogger().info("setup monitor contract successful: {}", monitorContractAddr);
    }

    @Override
    public long querySDPMessageSeq(String senderDomain, String senderID, String receiverDomain, String receiverID) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getSdpContract())) {
            throw new RuntimeException("empty sdp contract in bbc context");
        }

        return acbEthClient.querySdpSeq(this.bbcContext.getSdpContract().getContractAddress(), senderDomain, senderID, receiverDomain, receiverID);
    }

    @Override
    public void setProtocol(String protocolAddress, String protocolType) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())) {
            throw new RuntimeException("empty am contract in bbc context");
        }

        acbEthClient.setProtocolToAuthMsg(this.bbcContext.getAuthMessageContract().getContractAddress(), protocolAddress, protocolType);

        // 4. update am contract status
        try {
            if (!StrUtil.isEmpty(acbEthClient.getProtocolFromAuthMsg(this.bbcContext.getAuthMessageContract().getContractAddress(), protocolType))) {
                this.bbcContext.getAuthMessageContract().setStatus(ContractStatusEnum.CONTRACT_READY);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to update am contract status (address: %s)",
                            this.bbcContext.getAuthMessageContract().getContractAddress()
                    ), e);
        }
    }

    @Override
    public void setAmContract(String contractAddress) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getSdpContract())) {
            throw new RuntimeException("empty sdp contract in bbc context");
        }

        acbEthClient.setAmContractToSdp(this.bbcContext.getSdpContract().getContractAddress(), contractAddress);

        // 4. update sdp contract status
        try {
            if (!StrUtil.isEmpty(acbEthClient.getAmContractFromSdp(this.bbcContext.getSdpContract().getContractAddress()))
                    && !isByteArrayZero(acbEthClient.getLocalDomainFromSdp(this.bbcContext.getSdpContract().getContractAddress()))
                    && !"0x0000000000000000000000000000000000000000".equalsIgnoreCase(acbEthClient.getMonitorContractFromSdp(this.bbcContext.getSdpContract().getContractAddress()))
            ) {
                this.bbcContext.getSdpContract().setStatus(ContractStatusEnum.CONTRACT_READY);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to update sdp contract status (address: %s)",
                            this.bbcContext.getSdpContract().getContractAddress()
                    ), e);
        }
    }

    @Override
    public void setMonitorContract(String contractAddress) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getSdpContract())) {
            throw new RuntimeException("empty sdp contract in bbc context");
        }

        acbEthClient.setMonitorContractToSdp(this.bbcContext.getSdpContract().getContractAddress(), contractAddress);

        // 4. update sdp contract status
        try {
            if (!StrUtil.isEmpty(acbEthClient.getAmContractFromSdp(this.bbcContext.getSdpContract().getContractAddress()))
                    && !isByteArrayZero(acbEthClient.getLocalDomainFromSdp(this.bbcContext.getSdpContract().getContractAddress()))
                    && !"0x0000000000000000000000000000000000000000".equalsIgnoreCase(acbEthClient.getMonitorContractFromSdp(this.bbcContext.getSdpContract().getContractAddress()))
            ) {
                this.bbcContext.getSdpContract().setStatus(ContractStatusEnum.CONTRACT_READY);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to update sdp contract status (address: %s)",
                            this.bbcContext.getSdpContract().getContractAddress()
                    ), e);
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

        acbEthClient.setLocalDomainToSdp(this.bbcContext.getSdpContract().getContractAddress(), domain);

        // 4. update sdp contract status
        try {
            if (!StrUtil.isEmpty(acbEthClient.getAmContractFromSdp(this.bbcContext.getSdpContract().getContractAddress()))
                    && !isByteArrayZero(acbEthClient.getLocalDomainFromSdp(this.bbcContext.getSdpContract().getContractAddress()))
                    && !"0x0000000000000000000000000000000000000000".equalsIgnoreCase(acbEthClient.getMonitorContractFromSdp(this.bbcContext.getSdpContract().getContractAddress()))
            ) {
                this.bbcContext.getSdpContract().setStatus(ContractStatusEnum.CONTRACT_READY);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to update sdp contract status (address: %s)",
                            this.bbcContext.getSdpContract().getContractAddress()
                    ), e);
        }
    }

    @Override
    public void setProtocolInMonitor(String protocolAddress) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getMonitorContract())) {
            throw new RuntimeException("empty monitor contract in bbc context");
        }

        acbEthClient.setProtocolToMonitor(this.bbcContext.getMonitorContract().getContractAddress(), protocolAddress);

        // 4. update monitor contract status
        try {
            String monitorVerifier = acbEthClient.getMonitorVerifierFromMonitor(this.bbcContext.getMonitorContract().getContractAddress());
            if (!"0x0000000000000000000000000000000000000000".equalsIgnoreCase(monitorVerifier)
                    && !"0x0000000000000000000000000000000000000000".equalsIgnoreCase(acbEthClient.getPtcHubFromMonitorVerifier(monitorVerifier))) {
                if (!"0x0000000000000000000000000000000000000000".equalsIgnoreCase(acbEthClient.getProtocolFromMonitor(this.bbcContext.getMonitorContract().getContractAddress()))
                        && acbEthClient.getMonitorControlFromMonitor(this.bbcContext.getMonitorContract().getContractAddress()) != 0) {
                    this.bbcContext.getMonitorContract().setStatus(ContractStatusEnum.CONTRACT_READY);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to update monitor contract status (address: %s)",
                            this.bbcContext.getMonitorContract().getContractAddress()
                    ), e);
        }
    }

    @Override
    public void setMonitorControl(int monitorType) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getMonitorContract())) {
            throw new RuntimeException("empty monitor contract in bbc context");
        }

        acbEthClient.setMonitorControl(this.bbcContext.getMonitorContract().getContractAddress(), monitorType);

        // 4. update monitor contract status
        try {
            String monitorVerifier = acbEthClient.getMonitorVerifierFromMonitor(this.bbcContext.getMonitorContract().getContractAddress());
            if (!"0x0000000000000000000000000000000000000000".equalsIgnoreCase(monitorVerifier)
                    && !"0x0000000000000000000000000000000000000000".equalsIgnoreCase(acbEthClient.getPtcHubFromMonitorVerifier(monitorVerifier))) {
                if (!"0x0000000000000000000000000000000000000000".equalsIgnoreCase(acbEthClient.getProtocolFromMonitor(this.bbcContext.getMonitorContract().getContractAddress()))
                        && acbEthClient.getMonitorControlFromMonitor(this.bbcContext.getMonitorContract().getContractAddress()) != 0) {
                    this.bbcContext.getMonitorContract().setStatus(ContractStatusEnum.CONTRACT_READY);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to update monitor contract status (address: %s)",
                            this.bbcContext.getMonitorContract().getContractAddress()
                    ), e);
        }
    }

    @Override
    public void setPtcHubInMonitorVerifier(String contractAddress) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getMonitorContract())) {
            throw new RuntimeException("empty monitor contract in bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getPtcContract())) {
            throw new RuntimeException("empty PtcHub contract in bbc context");
        }
        String monitorVerifier = acbEthClient.getMonitorVerifierFromMonitor(this.bbcContext.getMonitorContract().getContractAddress());
        if ("0x0000000000000000000000000000000000000000".equalsIgnoreCase(monitorVerifier)) {
            throw new RuntimeException("not set monitor verifier contract in monitor contract yet");
        }

        acbEthClient.setPtcHubToMonitorVerifier(monitorVerifier, contractAddress);

        // 4. update monitor contract status
        try {
            if (!"0x0000000000000000000000000000000000000000".equalsIgnoreCase(acbEthClient.getPtcHubFromMonitorVerifier(monitorVerifier)) &&
                    !"0x0000000000000000000000000000000000000000".equalsIgnoreCase(acbEthClient.getProtocolFromMonitor(this.bbcContext.getMonitorContract().getContractAddress()))
                    && acbEthClient.getMonitorControlFromMonitor(this.bbcContext.getMonitorContract().getContractAddress()) != 0 ) {
                this.bbcContext.getMonitorContract().setStatus(ContractStatusEnum.CONTRACT_READY);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to update monitor contract status (address: %s)",
                            this.bbcContext.getMonitorContract().getContractAddress()
                    ), e);
        }

    }

    @Override
    public CrossChainMessageReceipt relayMonitorOrder(String committeeId, String signAlgo, byte[] rawProof, byte[] rawMonitorOrder) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getMonitorContract())) {
            throw new RuntimeException("empty monitor contract in bbc context");
        }

        getBBCLogger().debug("relay MonitorOrder to {} ", this.bbcContext.getMonitorContract().getContractAddress());

        return acbEthClient.relayMonitorOrderToMonitor(this.bbcContext.getMonitorContract().getContractAddress(), committeeId, signAlgo, rawProof, rawMonitorOrder);
    }


    @Override
    public CrossChainMessageReceipt relayAuthMessage(byte[] rawMessage) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())) {
            throw new RuntimeException("empty am contract in bbc context");
        }

        getBBCLogger().debug("relay AM {} to {} ",
                HexUtil.encodeHexStr(rawMessage), this.bbcContext.getAuthMessageContract().getContractAddress());

        return acbEthClient.relayMsgToAuthMsg(this.bbcContext.getAuthMessageContract().getContractAddress(), rawMessage);
    }

    @Override
    public ConsensusState readConsensusState(BigInteger slot) {
        var ethConsensusData = this.acbEthClient.getEthConsensusStateData(slot, this.bbcContext.getAuthMessageContract().getContractAddress());
        if (ObjectUtil.isNull(ethConsensusData.getBeaconBlockHeader())) {
            getBBCLogger().warn("😠 get missed slot or slot not born for now: {}", slot);
            return new ConsensusState(
                    slot,
                    Bytes32.ZERO.toArray(),
                    Bytes32.ZERO.toArray(),
                    0,
                    ethConsensusData.toJson().getBytes(),
                    "".getBytes(),
                    "".getBytes()
            );
        }

        BeaconBlock beaconBlockWithSyncAggregate = null;
        for (var i = 1; i <= this.config.getMaxTolerateMissedSlots(); i++) {
            var nextSlot = slot.add(BigInteger.valueOf(i));
            beaconBlockWithSyncAggregate = this.acbEthClient.getBeaconBlockBySlot(nextSlot);
            if (ObjectUtil.isNotNull(beaconBlockWithSyncAggregate)) {
                getBBCLogger().info("finding next: 🎉 beacon block at slot {} !", nextSlot);
                break;
            }
            getBBCLogger().warn("finding next: 😓 missed beacon block for slot or not born for now: {}", nextSlot);
        }
        if (ObjectUtil.isNull(beaconBlockWithSyncAggregate)) {
            throw new RuntimeException(
                    StrUtil.format("🤯 try to get next not missed block but get all missed blocks from slot {}, out of max tolerate: {}",
                            slot, this.config.getMaxTolerateMissedSlots()
                    )
            );
        }

        if (beaconBlockWithSyncAggregate.getBody().getOptionalSyncAggregate().isEmpty()) {
            throw new RuntimeException("has no sync aggregate in beacon block by slot " + beaconBlockWithSyncAggregate.getSlot());
        }

        var ethConsensusEndorsements = new EthConsensusEndorsements(
                beaconBlockWithSyncAggregate.getBody().getOptionalSyncAggregate().get(),
                beaconBlockWithSyncAggregate.getSlot()
        );
        this.acbEthClient.populateLightClientUpdateIfRequired(
                ethConsensusData,
                slot,
                beaconBlockWithSyncAggregate.getSlot().bigIntegerValue()
        );

        return new ConsensusState(
                slot,
                ethConsensusData.getBeaconBlockHeader().getRoot().toArray(),
                ethConsensusData.getBeaconBlockHeader().getParentRoot().toArray(),
                ethConsensusData.getExecutionPayloadHeader().getTimestamp().longValue() * 1000,
                ethConsensusData.toJson().getBytes(),
                "".getBytes(),
                ethConsensusEndorsements.toJson().getBytes()
        );
    }

    @Override
    public boolean hasTpBta(CrossChainLane tpbtaLane, int tpBtaVersion) {
        checkPtcContract();
        return acbEthClient.hasTpBtaOnPtcHub(bbcContext.getPtcContract().getContractAddress(), tpbtaLane, tpBtaVersion);
    }

    @Override
    public ThirdPartyBlockchainTrustAnchor getTpBta(CrossChainLane tpbtaLane, int tpBtaVersion) {
        checkPtcContract();
        return acbEthClient.getTpBtaFromPtcHub(this.bbcContext.getPtcContract().getContractAddress(), tpbtaLane, tpBtaVersion);
    }

    @Override
    public Set<PTCTypeEnum> getSupportedPTCType() {
        checkPtcContract();
        return acbEthClient.getSupportedPTCTypesFromPtcHub(this.bbcContext.getPtcContract().getContractAddress());
    }

    @Override
    public PTCTrustRoot getPTCTrustRoot(@NonNull ObjectIdentity ptcOwnerOid) {
        checkPtcContract();
        return acbEthClient.getPTCTrustRootFromPtcHub(this.bbcContext.getPtcContract().getContractAddress(), ptcOwnerOid);
    }

    @Override
    public boolean hasPTCTrustRoot(ObjectIdentity ptcOwnerOid) {
        checkPtcContract();
        return acbEthClient.hasPTCTrustRootFromPtcHub(this.bbcContext.getPtcContract().getContractAddress(), ptcOwnerOid);
    }

    @Override
    public PTCVerifyAnchor getPTCVerifyAnchor(ObjectIdentity ptcOwnerOid, BigInteger version) {
        checkPtcContract();
        return acbEthClient.getPTCVerifyAnchorFromPtcHub(this.bbcContext.getPtcContract().getContractAddress(), ptcOwnerOid, version);
    }

    @Override
    public boolean hasPTCVerifyAnchor(ObjectIdentity ptcOwnerOid, BigInteger version) {
        checkPtcContract();
        return acbEthClient.hasPTCVerifyAnchor(this.bbcContext.getPtcContract().getContractAddress(), ptcOwnerOid, version);
    }

    // in this version, ptc contract needs to be deployed after monitor contract, since monitor verifier is necessary for the init
    @Override
    public void setupPTCContract() {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (this.config.isUpgradableContracts() && StrUtil.isEmpty(this.config.getProxyAdmin())) {
            throw new RuntimeException("empty proxy admin");
        }
        if (ObjectUtil.isNotNull(this.bbcContext.getPtcContract())
            && StrUtil.isNotEmpty(this.bbcContext.getPtcContract().getContractAddress())) {
            // If the contract has been pre-deployed and the contract address is configured in the configuration file,
            // there is no need to redeploy.
            getBBCLogger().info("ptc hub contract already deployed");
            return;
        }

        AbstractCrossChainCertificate bcdnsRootCert = CrossChainCertificateUtil.readCrossChainCertificateFromPem(
                this.config.getBcdnsRootCertPem().getBytes()
        );
        if (bcdnsRootCert.getType() != CrossChainCertificateTypeEnum.BCDNS_TRUST_ROOT_CERTIFICATE) {
            getBBCLogger().error("bcdns root cert in config is incorrect: {}", this.config.getBcdnsRootCertPem());
            throw new RuntimeException("incorrect bcdns root cert");
        }

        // 2. deploy contract
        String ptcHubContractAddr = acbEthClient.deployPtcHubContract(bcdnsRootCert, acbEthClient.deployCommitteeVerifierContract(),
                acbEthClient.getMonitorVerifierFromMonitor(this.bbcContext.getMonitorContract().getContractAddress()));

        PTCContract ptcContract = new PTCContract();
        ptcContract.setContractAddress(ptcHubContractAddr);
        ptcContract.setStatus(ContractStatusEnum.CONTRACT_READY);
        bbcContext.setPtcContract(ptcContract);

        config.setPtcHubContractAddressDeployed(ptcHubContractAddr);

        getBBCLogger().info("setup ptc hub contract successful: {}", ptcHubContractAddr);
    }

    @Override
    public void setPtcContract(String ptcContractAddress) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())) {
            throw new RuntimeException("empty am contract in bbc context");
        }

        acbEthClient.setPtcContractToAuthMsg(this.bbcContext.getAuthMessageContract().getContractAddress(), ptcContractAddress);

        // 4. update am contract status
        try {
            if (!StrUtil.equals(acbEthClient.getProtocolFromAuthMsg(this.bbcContext.getAuthMessageContract().getContractAddress(), BigInteger.ZERO.toString()), Address.DEFAULT.toString())
                    && !StrUtil.equals(acbEthClient.getPtcHubAddrFromAuthMsg(this.bbcContext.getAuthMessageContract().getContractAddress()), Address.DEFAULT.toString())) {
                this.bbcContext.getAuthMessageContract().setStatus(ContractStatusEnum.CONTRACT_READY);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to update am contract status (address: %s)",
                            this.bbcContext.getAuthMessageContract().getContractAddress()
                    ), e);
        }
    }

    @Override
    public void updatePTCTrustRoot(PTCTrustRoot ptcTrustRoot) {
        checkPtcContract();
        acbEthClient.updatePTCTrustRootToPtcHub(this.bbcContext.getPtcContract().getContractAddress(), ptcTrustRoot);
    }

    @Override
    public void addTpBta(ThirdPartyBlockchainTrustAnchor tpbta) {
        checkPtcContract();
        acbEthClient.addTpBtaToPtcHub(this.bbcContext.getPtcContract().getContractAddress(), tpbta);
    }

    @Override
    public BlockState queryValidatedBlockStateByDomain(CrossChainDomain recvDomain) {
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getSdpContract())) {
            throw new RuntimeException("empty sdp contract in bbc context");
        }
        return acbEthClient.queryValidatedBlockStateFromSdp(this.bbcContext.getSdpContract().getContractAddress(), recvDomain);
    }

    @Override
    public CrossChainMessageReceipt recvOffChainException(String exceptionMsgAuthor, byte[] exceptionMsgPkg) {
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getSdpContract())) {
            throw new RuntimeException("empty sdp contract in bbc context");
        }
        return acbEthClient.recvOffChainException(this.bbcContext.getSdpContract().getContractAddress(), exceptionMsgAuthor, exceptionMsgPkg);
    }

    @Override
    public CrossChainMessageReceipt reliableRetry(ReliableCrossChainMessage msg) {
        throw new UnsupportedOperationException("not supported");
    }

    private void checkPtcContract() {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getPtcContract())) {
            throw new RuntimeException("empty am contract in bbc context");
        }
        if (this.bbcContext.getPtcContract().getStatus() != ContractStatusEnum.CONTRACT_READY) {
            throw new RuntimeException("ptc hub is not ready");
        }
    }

    private boolean isByteArrayZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0x00) {
                return false;
            }
        }
        return true;
    }
}
