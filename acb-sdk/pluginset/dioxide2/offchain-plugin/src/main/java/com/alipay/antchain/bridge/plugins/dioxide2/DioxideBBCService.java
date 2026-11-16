package com.alipay.antchain.bridge.plugins.dioxide2;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.AuthMessageContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.bbc.syscontract.MonitorContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.PTCContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.SDPContract;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTypeEnum;
import com.alipay.antchain.bridge.commons.core.ptc.PTCVerifyAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.rcc.ReliableCrossChainMessage;
import com.alipay.antchain.bridge.plugins.dioxide2.conf.DioxideConfig;
import com.alipay.antchain.bridge.plugins.dioxide2.conf.DioxideTypes;
import com.alipay.antchain.bridge.plugins.dioxide2.core.DioxideClient;
import com.alipay.antchain.bridge.plugins.dioxide2.core.DioxideTransaction;
import com.alipay.antchain.bridge.plugins.dioxide2.core.DioxideTransactionBlock;
import com.alipay.antchain.bridge.plugins.lib.BBCService;
import com.alipay.antchain.bridge.plugins.spi.bbc.AbstractBBCService;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@BBCService(products = "dioxide2", pluginId = "plugin-dioxide2")
@Getter
public class DioxideBBCService extends AbstractBBCService {

    private DioxideConfig config;

    private AbstractBBCContext bbcContext;

    @Setter
    private DioxideClient dioxideClient;

    @Override
    public void startup(AbstractBBCContext abstractBBCContext) {

        getBBCLogger().info("Dioxide BBCService startup with context: {}",
                new String(abstractBBCContext.getConfForBlockchainClient()));

        if (ObjectUtil.isNull(abstractBBCContext)) {
            throw new RuntimeException("null bbc context");
        }
        if (ObjectUtil.isEmpty(abstractBBCContext.getConfForBlockchainClient())) {
            throw new RuntimeException("empty blockchain client conf");
        }

        // 1. Obtain the configuration information
        try
        {
            config = DioxideConfig.fromJsonString(new String(abstractBBCContext.getConfForBlockchainClient()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (ObjectUtil.isEmpty(config.getPrivateKey())) {
            throw new RuntimeException("private key is empty");
        }

        if (StrUtil.isEmpty(config.getRpcUrl()) || StrUtil.isEmpty(config.getWsRpc())) {
            throw new RuntimeException("dioxide url is empty");
        }

        // 6. set context
        this.bbcContext = abstractBBCContext;

        this.dioxideClient = new DioxideClient(config, getBBCLogger());

        // 7. set the pre-deployed contracts into context
        if (ObjectUtil.isNull(abstractBBCContext.getAuthMessageContract())
                && StrUtil.isNotEmpty(this.config.getAmContractAddressDeployed())) {
            AuthMessageContract authMessageContract = new AuthMessageContract();
            authMessageContract.setContractAddress(this.config.getAmContractAddressDeployed());
            authMessageContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            this.bbcContext.setAuthMessageContract(authMessageContract);
        }

        if (ObjectUtil.isNull(abstractBBCContext.getSdpContract())
                && StrUtil.isNotEmpty(this.config.getSdpContractAddressDeployed())) {
            SDPContract sdpContract = new SDPContract();
            sdpContract.setContractAddress(this.config.getSdpContractAddressDeployed());
            sdpContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            this.bbcContext.setSdpContract(sdpContract);
        }

        if (ObjectUtil.isNull(abstractBBCContext.getMonitorContract())
                && StrUtil.isNotEmpty(this.config.getMonitorContractAddressDeployed())) {
            MonitorContract monitorContract = new MonitorContract();
            monitorContract.setContractAddress(this.config.getMonitorContractAddressDeployed());
            monitorContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            this.bbcContext.setMonitorContract(monitorContract);
        }

        if (ObjectUtil.isNull(abstractBBCContext.getPtcContract())
                && StrUtil.isNotEmpty(this.config.getPtcHubContractAddressDeployed())) {
            PTCContract ptcContract = new PTCContract();
            ptcContract.setContractAddress(this.config.getPtcHubContractAddressDeployed());
            ptcContract.setStatus(ContractStatusEnum.CONTRACT_READY);
            this.bbcContext.setPtcContract(ptcContract);
        }

    }

    @Override
    public void shutdown() {
        getBBCLogger().info("shut down DIOXIDE BBCService!");
        dioxideClient.shutdown();
    }

    @Override
    public AbstractBBCContext getContext() {
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }

        getBBCLogger().debug("Dioxide BBCService context (amAddr: {}, amStatus: {}, sdpAddr: {}, sdpStatus: {})",
                this.bbcContext.getAuthMessageContract() != null ? this.bbcContext.getAuthMessageContract().getContractAddress() : "",
                this.bbcContext.getAuthMessageContract() != null ? this.bbcContext.getAuthMessageContract().getStatus() : "",
                this.bbcContext.getSdpContract() != null ? this.bbcContext.getSdpContract().getContractAddress() : "",
                this.bbcContext.getSdpContract() != null ? this.bbcContext.getSdpContract().getStatus() : ""
        );

        // Deployment updates the contract context first. Persist those CIDs in
        // the client config too, so a restarted service uses the same contracts.
        if (this.bbcContext.getAuthMessageContract() != null) {
            this.config.setAmContractAddressDeployed(this.bbcContext.getAuthMessageContract().getContractAddress());
        }
        if (this.bbcContext.getSdpContract() != null) {
            this.config.setSdpContractAddressDeployed(this.bbcContext.getSdpContract().getContractAddress());
        }
        this.bbcContext.setConfForBlockchainClient(this.config.toJsonString().getBytes(StandardCharsets.UTF_8));
        return this.bbcContext;
    }

    // Relayer polls this interface after relayAuthMessage returns an unconfirmed receipt.
    @Override
    public CrossChainMessageReceipt readCrossChainMessageReceipt(String txHash) {
        DioxideTransaction dioxideTransaction = dioxideClient.getTransactionByHash(txHash);

        getBBCLogger().info("[readCrossChainMessageReceipt] tx:\n{}", JSON.toJSONString(dioxideTransaction, SerializerFeature.PrettyFormat));

        // Construct cross-chain message receipt
        CrossChainMessageReceipt crossChainMessageReceipt = dioxideClient.getCrossChainMessageReceipt(dioxideTransaction);
        getBBCLogger().info("cross chain message receipt for txhash {} : {}", txHash, JSON.toJSONString(crossChainMessageReceipt));

        return crossChainMessageReceipt;
    }

    @Override
    public List<CrossChainMessage> readCrossChainMessagesByHeight(long height) {
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())) {
            throw new RuntimeException("empty am contract in bbc context");
        }

        try {
            return dioxideClient.readAuthMessagesFromBlock(height);
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to readCrossChainMessagesByHeight (height: %d, contractAddr: %s, topic: %s)",
                            height,
                            "AuthMsg",
                            "SENDAUTHMESSAGE_EVENT"
                    ), e
            );
        }
    }

    @Override
    public Long queryLatestHeight() {
        return dioxideClient.queryLatestHeight();
    }

    @Override
    public void setupAuthMessageContract() {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (checkAmContractIsDeployed()) {
            // If the contract has been pre-deployed and the contract address is configured in the configuration file,
            // there is no need to redeploy.
            return;
        }

        // 2. deploy contract
        if (checkSdpContractIsDeployed() || checkMonitorContractIsDeployed()) {
            dioxideClient.getConfig().setIsPreContractDeployed(true);
        }
        long amContractCid = dioxideClient.deployAuthMsgContract();
        AuthMessageContract authMessageContract = new AuthMessageContract();
        authMessageContract.setContractAddress(String.valueOf(amContractCid));
        authMessageContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
        bbcContext.setAuthMessageContract(authMessageContract);

        getBBCLogger().info("setup am contract successful: contract_name:{}.{}",
                config.getDappName(), config.getAmContractName());
    }

    @Override
    public void setupSDPMessageContract() {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (checkSdpContractIsDeployed()) {
            // If the contract has been pre-deployed and the contract address is configured in the configuration file,
            // there is no need to redeploy.
            return;
        }

        // 2. deploy contract
        if (checkAmContractIsDeployed() || checkMonitorContractIsDeployed()) {
            dioxideClient.getConfig().setIsPreContractDeployed(true);
        }
        long sdpContractCid = dioxideClient.deploySdpContract();

        SDPContract sdpContract = new SDPContract();
        sdpContract.setContractAddress(String.valueOf(sdpContractCid));
        sdpContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
        bbcContext.setSdpContract(sdpContract);
        getBBCLogger().info("setup sdp contract successful: contract_name:{}.{}",
                config.getDappName(), config.getSdpContractName());
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

        return dioxideClient.querySdpSeq(senderDomain, senderID, receiverDomain, receiverID);
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

        // arg protocolAddress: contract cid
        dioxideClient.setProtocolToAuthMsg(protocolAddress, protocolType);

        updateAuthMessageContractStatus();
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

        // arg contractAddress: contract cid
        dioxideClient.setAmContractToSdp(contractAddress);

        // 4. update sdp contract status
        try {
            JSONObject valState = dioxideClient.getContractState(this.config.getDappName(), config.getSdpContractName(), DioxideTypes.Scope.Global, "")
                    .getJSONObject("State");
            if (!"0".equals(valState.getString("amContractId"))
                    && CollUtil.isNotEmpty(valState.getJSONArray("localDomain"))
                    && !"0".equals(valState.getString("monitorContractId"))) {
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

        dioxideClient.setLocalDomainToSdp(domain);

        // 4. update sdp contract status
        try {
            JSONObject valState = dioxideClient.getContractState(this.config.getDappName(), config.getSdpContractName(), DioxideTypes.Scope.Global, "")
                    .getJSONObject("State");
            
            if (!"0".equals(valState.getString("amContractId"))
                    && CollUtil.isNotEmpty(valState.getJSONArray("localDomain"))
                    && !"0".equals(valState.getString("monitorContractId"))) {
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
    public CrossChainMessageReceipt relayAuthMessage(byte[] rawMessage) {
        return relayAuthMessage(rawMessage, "");
    }

    @Override
    public CrossChainMessageReceipt relayAuthMessage(byte[] rawMessage, String submissionId) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())) {
            throw new RuntimeException("empty am contract in bbc context");
        }

        return dioxideClient.relayMsgToAuthMsg(rawMessage, submissionId);

    }

    @Override
    public void setupMonitorContract() {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (checkMonitorContractIsDeployed()) {
            // If the contract has been pre-deployed and the contract address is configured in the configuration file,
            // there is no need to redeploy.
            return;
        }

        // 2. deploy contract
        if (checkAmContractIsDeployed() || checkSdpContractIsDeployed()) {
            dioxideClient.getConfig().setIsPreContractDeployed(true);
        }

        long monitorVerifierCid = dioxideClient.deployMonitorVerifierContract();
        config.setMonitorVerifierContractAddressDeployed(String.valueOf(monitorVerifierCid));

        long monitorContractCid = dioxideClient.deployMonitorContract();
        config.setMonitorContractAddressDeployed(String.valueOf(monitorContractCid));
        dioxideClient.setMonitorVerifierToMonitor(String.valueOf(monitorVerifierCid));
        dioxideClient.setMonitorToMonitorVerifier(String.valueOf(monitorContractCid));
        MonitorContract monitorContract = new MonitorContract();
        monitorContract.setContractAddress(String.valueOf(monitorContractCid));
        monitorContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
        bbcContext.setMonitorContract(monitorContract);

        getBBCLogger().info("setup monitor contract successful: contract_name:{}.{}",
                config.getDappName(), config.getMonitorContractName());
    }

    @Override
    public void setMonitorContract(String contractAddress) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (StrUtil.isEmpty(this.bbcContext.getSdpContract().getContractAddress())) {
            throw new RuntimeException("none sdp contract address");
        }

        dioxideClient.setMonitorContractToSdp(contractAddress);

        // 4. update sdp contract status
        try {
            JSONObject valState = dioxideClient.getContractState(this.config.getDappName(), config.getSdpContractName(), DioxideTypes.Scope.Global, "")
                    .getJSONObject("State");

            System.out.println("amContractId: " + valState.getString("amContractId"));
            System.out.println("amContractId(bool): " + !"0".equals(valState.getString("amContractId")));
            System.out.println("localDomain: " + valState.getJSONArray("localDomain"));
            System.out.println("localDomain(bool): " + CollUtil.isNotEmpty(valState.getJSONArray("localDomain")));
            System.out.println("monitorContractId: " + valState.getString("monitorContractId"));
            System.out.println("monitorContractId(bool): " + !"0".equals(valState.getString("monitorContractId")));
            
            if (!"0".equals(valState.getString("amContractId"))
                    && CollUtil.isNotEmpty(valState.getJSONArray("localDomain"))
                    && !"0".equals(valState.getString("monitorContractId"))) {
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
    public void setProtocolInMonitor(String contractAddress) {
        // 1. check context
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (StrUtil.isEmpty(this.bbcContext.getMonitorContract().getContractAddress())) {
            throw new RuntimeException("none monitor contract address");
        }

        dioxideClient.setSdpContractToMonitor(contractAddress);

        updateMonitorContractStatus();
    }

    @Override
    public void setMonitorControl(int monitorType) {
        requireMonitorContract();
        if (monitorType != 1 && monitorType != 2 && monitorType != 3) {
            throw new IllegalArgumentException("unsupported monitor control: " + monitorType);
        }
        dioxideClient.setMonitorControl(monitorType);
        updateMonitorContractStatus();
    }

    @Override
    public void setPtcHubInMonitorVerifier(String contractAddress) {
        requireMonitorContract();
        requirePtcContract();
        if (StrUtil.isEmpty(config.getMonitorVerifierContractAddressDeployed())) {
            throw new RuntimeException("empty monitor verifier contract address");
        }
        dioxideClient.setPtcHubToMonitorVerifier(contractAddress);
        updateMonitorContractStatus();
    }

    @Override
    public CrossChainMessageReceipt relayMonitorOrder(String committeeId, String signAlgo, byte[] rawProof, byte[] rawMonitorOrder) {
        return null;
    }

    @Override
    public ConsensusState readConsensusState(BigInteger height) {
        if (height == null || height.signum() < 0) {
            throw new IllegalArgumentException("height must be non-negative");
        }

        DioxideTransactionBlock block = dioxideClient.getTransactionBlockInGlobalShardByHeight(height.longValueExact());
        if (block == null) {
            throw new RuntimeException("empty Dioxide transaction block at height " + height);
        }
        if (StrUtil.isEmpty(block.getHash())) {
            throw new RuntimeException("empty Dioxide block hash at height " + height);
        }

        JSONObject consensusHeader = dioxideClient.getConsensusHeaderByHeight(height.longValueExact());
        long timestamp = block.getTimestamp() == null
                ? (consensusHeader == null ? 0L : consensusHeader.getLongValue("Timestamp"))
                : block.getTimestamp();
        byte[] stateData = consensusHeader == null
                ? new byte[0]
                : consensusHeader.toJSONString().getBytes(StandardCharsets.UTF_8);

        return new ConsensusState(
                BigInteger.valueOf(block.getHeight() == null ? height.longValueExact() : block.getHeight()),
                block.getHash().getBytes(StandardCharsets.UTF_8),
                StrUtil.nullToEmpty(block.getPrev()).getBytes(StandardCharsets.UTF_8),
                timestamp,
                stateData,
                new byte[0],
                new byte[0]
        );
    }

    @Override
    public boolean hasTpBta(CrossChainLane tpbtaLane, int tpBtaVersion) {
        requirePtcContract();
        return dioxideClient.hasTpBta(tpbtaLane.encode(), versionBytes(tpBtaVersion));
    }

    @Override
    public ThirdPartyBlockchainTrustAnchor getTpBta(CrossChainLane tpbtaLane, int tpBtaVersion) {
        requirePtcContract();
        byte[] raw = dioxideClient.queryTpBta(tpbtaLane.encode(), versionBytes(tpBtaVersion));
        return ObjectUtil.isEmpty(raw) ? null : ThirdPartyBlockchainTrustAnchor.decode(raw);
    }

    @Override
    public Set<PTCTypeEnum> getSupportedPTCType() {
        requirePtcContract();
        return Set.of(PTCTypeEnum.COMMITTEE);
    }

    @Override
    public PTCTrustRoot getPTCTrustRoot(@NonNull ObjectIdentity ptcOwnerOid) {
        requirePtcContract();
        byte[] raw = dioxideClient.queryPtcTrustRoot(ptcOwnerOid.encode());
        return ObjectUtil.isEmpty(raw) ? null : PTCTrustRoot.decode(raw);
    }

    @Override
    public boolean hasPTCTrustRoot(ObjectIdentity ptcOwnerOid) {
        requirePtcContract();
        return dioxideClient.hasPtcTrustRoot(ptcOwnerOid.encode());
    }

    @Override
    public PTCVerifyAnchor getPTCVerifyAnchor(ObjectIdentity ptcOwnerOid, BigInteger version) {
        requirePtcContract();
        byte[] raw = dioxideClient.queryPtcVerifyAnchor(ptcOwnerOid.encode(), versionBytes(version));
        return ObjectUtil.isEmpty(raw) ? null : PTCVerifyAnchor.decode(raw);
    }

    @Override
    public boolean hasPTCVerifyAnchor(ObjectIdentity ptcOwnerOid, BigInteger version) {
        requirePtcContract();
        return dioxideClient.hasPtcVerifyAnchor(ptcOwnerOid.encode(), versionBytes(version));
    }

    @Override
    public void setupPTCContract() {
        if (ObjectUtil.isNull(this.bbcContext)) {
            throw new RuntimeException("empty bbc context");
        }
        if (checkPtcContractIsDeployed()) {
            return;
        }
        if (ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())) {
            throw new RuntimeException("auth message contract must be deployed before PtcHub");
        }
        if (StrUtil.isEmpty(config.getMonitorVerifierContractAddressDeployed())) {
            throw new RuntimeException("monitor verifier must be deployed before PtcHub");
        }

        long ptcHubCid = dioxideClient.deployPtcHubContract();
        String ptcHubAddress = String.valueOf(ptcHubCid);
        config.setPtcHubContractAddressDeployed(ptcHubAddress);
        dioxideClient.setMonitorVerifierToPtcHub(config.getMonitorVerifierContractAddressDeployed());
        dioxideClient.setAuthMessageToPtcHub(this.bbcContext.getAuthMessageContract().getContractAddress());

        PTCContract ptcContract = new PTCContract();
        ptcContract.setContractAddress(ptcHubAddress);
        ptcContract.setStatus(ContractStatusEnum.CONTRACT_READY);
        this.bbcContext.setPtcContract(ptcContract);
    }

    @Override
    public void setPtcContract(String ptcContractAddress) {
        if (ObjectUtil.isNull(this.bbcContext)
                || ObjectUtil.isNull(this.bbcContext.getAuthMessageContract())) {
            throw new RuntimeException("empty auth message contract in bbc context");
        }
        requirePtcContract();
        dioxideClient.setPtcHubToAuthMessage(ptcContractAddress);
        updateAuthMessageContractStatus();
    }

    @Override
    public void updatePTCTrustRoot(PTCTrustRoot ptcTrustRoot) {
        requirePtcContract();
        byte[] ownerOid = ptcTrustRoot.getPtcCredentialSubject().getApplicant().encode();
        dioxideClient.savePtcTrustRoot(ownerOid, ptcTrustRoot.encode());
        if (ptcTrustRoot.getVerifyAnchorMap() != null) {
            ptcTrustRoot.getVerifyAnchorMap().forEach((version, anchor) ->
                    dioxideClient.savePtcVerifyAnchor(
                            ownerOid,
                            versionBytes(version),
                            anchor.encode()
                    )
            );
        }
    }

    @Override
    public void addTpBta(ThirdPartyBlockchainTrustAnchor tpbta) {
        requirePtcContract();
        dioxideClient.saveTpBta(
                tpbta.getCrossChainLane().encode(),
                versionBytes(tpbta.getTpbtaVersion()),
                tpbta.encode()
        );
    }

    @Override
    public BlockState queryValidatedBlockStateByDomain(CrossChainDomain recvDomain) {
        return null;
    }

    @Override
    public CrossChainMessageReceipt recvOffChainException(String exceptionMsgAuthor, byte[] exceptionMsgPkg) {
        return null;
    }

    @Override
    public CrossChainMessageReceipt reliableRetry(ReliableCrossChainMessage msg) {
        throw new UnsupportedOperationException("not supported");
    }

    private boolean checkAmContractIsDeployed() {
        return ObjectUtil.isNotNull(this.bbcContext.getAuthMessageContract())
        && StrUtil.isNotEmpty(this.bbcContext.getAuthMessageContract().getContractAddress())
        && dioxideClient.isContractVersionCurrent(
                config.getAmContractName(),
                this.bbcContext.getAuthMessageContract().getContractAddress()
        );
    }

    private boolean checkSdpContractIsDeployed() {
        return ObjectUtil.isNotNull(this.bbcContext.getSdpContract())
        && StrUtil.isNotEmpty(this.bbcContext.getSdpContract().getContractAddress())
        && dioxideClient.isContractVersionCurrent(
                config.getSdpContractName(),
                this.bbcContext.getSdpContract().getContractAddress()
        );
    }

    private boolean checkMonitorContractIsDeployed() {
        return ObjectUtil.isNotNull(this.bbcContext.getMonitorContract())
        && StrUtil.isNotEmpty(this.bbcContext.getMonitorContract().getContractAddress())
        && dioxideClient.isContractVersionCurrent(
                config.getMonitorContractName(),
                this.bbcContext.getMonitorContract().getContractAddress()
        );
    }

    private boolean checkPtcContractIsDeployed() {
        return ObjectUtil.isNotNull(this.bbcContext.getPtcContract())
                && StrUtil.isNotEmpty(this.bbcContext.getPtcContract().getContractAddress())
                && dioxideClient.isContractVersionCurrent(
                        config.getPtcHubContractName(),
                        this.bbcContext.getPtcContract().getContractAddress()
                );
    }

    private void requireMonitorContract() {
        if (ObjectUtil.isNull(this.bbcContext)
                || ObjectUtil.isNull(this.bbcContext.getMonitorContract())
                || StrUtil.isEmpty(this.bbcContext.getMonitorContract().getContractAddress())) {
            throw new RuntimeException("empty monitor contract in bbc context");
        }
    }

    private void requirePtcContract() {
        if (ObjectUtil.isNull(this.bbcContext)
                || ObjectUtil.isNull(this.bbcContext.getPtcContract())
                || StrUtil.isEmpty(this.bbcContext.getPtcContract().getContractAddress())) {
            throw new RuntimeException("empty PtcHub contract in bbc context");
        }
    }

    private void updateAuthMessageContractStatus() {
        try {
            JSONObject state = dioxideClient.getContractState(
                    config.getDappName(), config.getAmContractName(), DioxideTypes.Scope.Global, ""
            ).getJSONObject("State");
            JSONObject protocolRoutes = state == null ? null : state.getJSONObject("protocolRoutes");
            if (state != null
                    && CollUtil.isNotEmpty(protocolRoutes)
                    && StrUtil.isNotEmpty(protocolRoutes.getString("0"))
                    && state.getLongValue("ptcHubId") != 0L) {
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

    private void updateMonitorContractStatus() {
        try {
            JSONObject monitorState = dioxideClient.getContractState(
                    config.getDappName(), config.getMonitorContractName(), DioxideTypes.Scope.Global, ""
            ).getJSONObject("State");
            JSONObject verifierState = dioxideClient.getContractState(
                    config.getDappName(), config.getMonitorVerifierContractName(), DioxideTypes.Scope.Global, ""
            ).getJSONObject("State");
            String ptcHubAddress = verifierState == null ? null : verifierState.getString("ptcHubAddress");
            if (monitorState != null
                    && monitorState.getLongValue("sdpContractId") != 0L
                    && monitorState.getLongValue("monitorVerifierId") != 0L
                    && monitorState.getIntValue("monitorControl") != 0
                    && StrUtil.isNotEmpty(ptcHubAddress)
                    && !"<address:null>".equals(ptcHubAddress)) {
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

    private byte[] versionBytes(int version) {
        return versionBytes(BigInteger.valueOf(version));
    }

    private byte[] versionBytes(BigInteger version) {
        if (version == null || version.signum() < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        return version.toByteArray();
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
