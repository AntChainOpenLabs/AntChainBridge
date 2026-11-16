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

package com.alipay.antchain.bridge.relayer.core.manager.blockchain;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.bcdns.service.IBlockChainDomainNameService;
import com.alipay.antchain.bridge.bcdns.types.req.QueryThirdPartyBlockchainTrustAnchorRequest;
import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.bta.AbstractBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.bta.IBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.*;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageV3;
import com.alipay.antchain.bridge.commons.core.sdp.TimeoutMeasureEnum;
import com.alipay.antchain.bridge.ptc.service.IPTCService;
import com.alipay.antchain.bridge.relayer.commons.constant.*;
import com.alipay.antchain.bridge.relayer.commons.exception.AMProcessException;
import com.alipay.antchain.bridge.relayer.commons.exception.AntChainBridgeRelayerException;
import com.alipay.antchain.bridge.relayer.commons.exception.BsValidationException;
import com.alipay.antchain.bridge.relayer.commons.exception.RelayerErrorCodeEnum;
import com.alipay.antchain.bridge.relayer.commons.model.*;
import com.alipay.antchain.bridge.relayer.core.manager.bcdns.IBCDNSManager;
import com.alipay.antchain.bridge.relayer.core.manager.network.IRelayerCredentialManager;
import com.alipay.antchain.bridge.relayer.core.manager.ptc.PtcManager;
import com.alipay.antchain.bridge.relayer.core.service.anchor.tasks.BlockTaskTypeEnum;
import com.alipay.antchain.bridge.relayer.core.service.anchor.tasks.NotifyTaskTypeEnum;
import com.alipay.antchain.bridge.relayer.core.types.blockchain.*;
import com.alipay.antchain.bridge.relayer.core.types.exception.BbcInterfaceNotSupportException;
import com.alipay.antchain.bridge.relayer.dal.repository.IBlockchainRepository;
import com.alipay.antchain.bridge.relayer.dal.repository.ICrossChainMessageRepository;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class BlockchainManager implements IBlockchainManager {

    @Resource
    private IBlockchainRepository blockchainRepository;

    @Resource
    private BlockchainClientPool blockchainClientPool;

    @Resource
    private ICrossChainMessageRepository crossChainMessageRepository;

    @Resource
    private IBCDNSManager bcdnsManager;

    @Resource
    private PtcManager ptcManager;

    @Resource
    private IRelayerCredentialManager relayerCredentialManager;

    private final Map<String, Boolean> ifTpBtaReadyOnChainMap = new HashMap<>();

    private final Map<String, BigInteger> latestVerifyAnchorVersionOnchain = new ConcurrentHashMap<>();

    private static final int MONITOR_CLOSE = 1;

    private static final int MONITOR_OPEN = 2;

    @Override
    public void addBlockchain(
            String product,
            String blockchainId,
            String pluginServerId,
            String alias,
            String desc,
            Map<String, String> clientConfig
    ) {
        try {
            BlockchainMeta.BlockchainProperties blockchainProperties = BlockchainMeta.BlockchainProperties.decode(
                    JSON.toJSONBytes(clientConfig)
            );
            if (ObjectUtil.isNull(blockchainProperties)) {
                throw new RuntimeException(
                        StrUtil.format("null blockchain properties from client config : {}", JSON.toJSONString(clientConfig))
                );
            }
            if (ObjectUtil.isNotNull(blockchainProperties.getAnchorRuntimeStatus())) {
                log.warn(
                        "add blockChain information (id : {}) contains anchor runtime status : {} and it will be removed",
                        blockchainId, blockchainProperties.getAnchorRuntimeStatus().getCode()
                );
            }
            blockchainProperties.setAnchorRuntimeStatus(BlockchainStateEnum.INIT);
            blockchainProperties.setPluginServerId(pluginServerId);

            addBlockchain(new BlockchainMeta(product, blockchainId, alias, desc, blockchainProperties));
        } catch (AntChainBridgeRelayerException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    e,
                    "failed to add new blockchain {} - {} with plugin server {}",
                    product, blockchainId, pluginServerId
            );
        }
    }

    @Override
    public void addBlockchain(BlockchainMeta blockchainMeta) {
        log.info(
                "add blockchain {} - {} with plugin server {}",
                blockchainMeta.getProduct(), blockchainMeta.getBlockchainId(), blockchainMeta.getProperties().getPluginServerId()
        );

        try {
            if (isBlockchainExists(blockchainMeta.getProduct(), blockchainMeta.getBlockchainId())) {
                throw new AntChainBridgeRelayerException(
                        RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                        "blockchain {}-{} already exists",
                        blockchainMeta.getProduct(), blockchainMeta.getBlockchainId()
                );
            }

            // 检查客户端配置是否正确
            AbstractBlockchainClient client = blockchainClientPool.createClient(blockchainMeta);
            /* 记录当前区块链高度为初始锚定高度 */
            blockchainMeta.getProperties().setInitBlockHeight(
                    client.getLastBlockHeight()
            );

            blockchainRepository.saveBlockchainMeta(blockchainMeta);

            log.info("[BlockchainManager] add blockchain {} success", blockchainMeta.getMetaKey());

        } catch (AntChainBridgeRelayerException e) {
            blockchainClientPool.deleteClient(blockchainMeta.getProduct(), blockchainMeta.getBlockchainId());
            throw e;
        } catch (Exception e) {
            blockchainClientPool.deleteClient(blockchainMeta.getProduct(), blockchainMeta.getBlockchainId());
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    e,
                    "failed to add new blockchain {} with plugin server {}",
                    blockchainMeta.getMetaKey(), blockchainMeta.getProperties().getPluginServerId()
            );
        }
    }

    @Override
    public void updateBlockchain(String product, String blockchainId, String pluginServerId, String alias, String desc, Map<String, String> clientConfig) {
        try {
            BlockchainMeta blockchainMeta = getBlockchainMeta(product, blockchainId);
            if (ObjectUtil.isNull(blockchainMeta)) {
                throw new RuntimeException(StrUtil.format("none blockchain found for {}-{}", product, blockchainId));
            }

            if (blockchainMeta.isRunning()) {
                AbstractBlockchainClient client = blockchainClientPool.createClient(blockchainMeta);
                if (ObjectUtil.isNull(client)) {
                    throw new AntChainBridgeRelayerException(
                            RelayerErrorCodeEnum.CORE_BLOCKCHAIN_CLIENT_INIT_ERROR,
                            "null blockchain client for {}-{}", product, blockchainId
                    );
                }
            }

            BlockchainMeta.BlockchainProperties blockchainProperties = BlockchainMeta.BlockchainProperties.decode(
                    JSON.toJSONBytes(clientConfig)
            );
            if (ObjectUtil.isNull(blockchainProperties)) {
                throw new RuntimeException(StrUtil.format("none blockchain properties built for {}-{}", product, blockchainId));
            }

            blockchainMeta.setAlias(alias);
            blockchainMeta.setDesc(desc);
            blockchainMeta.updateProperties(blockchainProperties);
            if (!updateBlockchainMeta(blockchainMeta)) {
                throw new RuntimeException(
                        StrUtil.format(
                                "failed to update meta for blockchain {} - {} into DB",
                                product, blockchainId
                        )
                );
            }


        } catch (AntChainBridgeRelayerException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    e,
                    "failed to update blockchain {} - {}",
                    product, blockchainId
            );
        }
    }

    @Override
    public void updateBlockchainProperty(String product, String blockchainId, String confKey, String confValue) {
        log.info("update property (key: {}, val: {}) for blockchain {} - {}", confKey, confValue, product, blockchainId);

        try {
            BlockchainMeta blockchainMeta = getBlockchainMeta(product, blockchainId);
            if (ObjectUtil.isNull(blockchainMeta)) {
                throw new AntChainBridgeRelayerException(
                        RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                        "none blockchain found for {}-{}", product, blockchainId
                );
            }

            blockchainMeta.updateProperty(confKey, confValue);
            if (!updateBlockchainMeta(blockchainMeta)) {
                throw new AntChainBridgeRelayerException(
                        RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                        "failed to update property (key: {}, val: {}) for blockchain {} - {} into DB",
                        confKey, confValue, product, blockchainId
                );
            }
        } catch (AntChainBridgeRelayerException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.UNKNOWN_INTERNAL_ERROR,
                    e,
                    "failed to update property (key: {}, val: {}) for blockchain {} - {} with unknown exception",
                    confKey, confValue, product, blockchainId
            );
        }
        log.info("successful to update property (key: {}, val: {}) for blockchain {} - {}", confKey, confValue, product, blockchainId);
    }

    @Override
    public boolean hasBlockchain(String domain) {
        return blockchainRepository.hasBlockchain(domain);
    }

    @Override
    public DomainCertWrapper getDomainCert(String domain) {
        return blockchainRepository.getDomainCert(domain);
    }

    @Override
    public void deployAMClientContract(String product, String blockchainId) {
        try {
            BlockchainMeta blockchainMeta = this.getBlockchainMeta(product, blockchainId);
            if (ObjectUtil.isNull(blockchainMeta)) {
                throw new RuntimeException(StrUtil.format("none blockchain found for {}-{}", product, blockchainId));
            }
            deployHeteroBlockchainAMContract(blockchainMeta);
        } catch (AntChainBridgeRelayerException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    e,
                    "failed to deploy am contract for blockchain {} - {}",
                    product, blockchainId
            );
        }
    }

    @Override
    @Transactional(rollbackFor = AntChainBridgeRelayerException.class)
    public void deployBBCContractAsync(String product, String blockchainId) {
        try {
            BlockchainMeta blockchainMeta = getBlockchainMeta(product, blockchainId);
            if (ObjectUtil.isNull(blockchainMeta)) {
                throw new RuntimeException(StrUtil.format("blockchain not found : {}-{}", product, blockchainId));
            }

            if (ObjectUtil.isNull(blockchainMeta.getProperties().getAmServiceStatus())) {
                blockchainMeta.getProperties().setAmServiceStatus(OnChainServiceStatusEnum.INIT);
                updateBlockchainMeta(blockchainMeta);
            }
        } catch (AntChainBridgeRelayerException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    e,
                    "failed to mark blockchain {} - {} to deploy BBC contracts",
                    product, blockchainId
            );
        }
    }

    @Override
    public void startBlockchainAnchor(String product, String blockchainId) {
        log.info("start blockchain anchor {} - {}", product, blockchainId);

        try {
            BlockchainMeta blockchainMeta = this.getBlockchainMeta(product, blockchainId);
            if (ObjectUtil.isNull(blockchainMeta)) {
                throw new RuntimeException(StrUtil.format("none blockchain found for {}-{}", product, blockchainId));
            }

            // 已启动的不重复启动
            if (blockchainMeta.isRunning()) {
                return;
            }

            if (!blockchainClientPool.hasClient(product, blockchainId)) {
                blockchainClientPool.createClient(blockchainMeta);
            }

            blockchainMeta.getProperties().setAnchorRuntimeStatus(BlockchainStateEnum.RUNNING);
            if (ObjectUtil.isNull(blockchainMeta.getProperties().getAmServiceStatus())) {
                blockchainMeta.getProperties().setAmServiceStatus(OnChainServiceStatusEnum.INIT);
            }
            if (!updateBlockchainMeta(blockchainMeta)) {
                throw new RuntimeException(
                        StrUtil.format(
                                "failed to update meta for blockchain {} - {} into DB",
                                product, blockchainId
                        )
                );
            }
        } catch (AntChainBridgeRelayerException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    e,
                    "failed to start blockchain {} - {}",
                    product, blockchainId
            );
        }
    }

    @Override
    public void stopBlockchainAnchor(String product, String blockchainId) {
        log.info("stop blockchain anchor {} - {}", product, blockchainId);

        try {
            BlockchainMeta blockchainMeta = this.getBlockchainMeta(product, blockchainId);
            if (ObjectUtil.isNull(blockchainMeta)) {
                throw new RuntimeException(StrUtil.format("none blockchain found for {}-{}", product, blockchainId));
            }

            // 已启动的不重复启动
            if (BlockchainStateEnum.STOPPED == blockchainMeta.getProperties().getAnchorRuntimeStatus()) {
                return;
            }

            blockchainMeta.getProperties().setAnchorRuntimeStatus(BlockchainStateEnum.STOPPED);
            if (!updateBlockchainMeta(blockchainMeta)) {
                throw new RuntimeException(
                        StrUtil.format(
                                "failed to update meta for blockchain {} - {} into DB",
                                product, blockchainId
                        )
                );
            }
        } catch (AntChainBridgeRelayerException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    e,
                    "failed to stop blockchain {} - {}",
                    product, blockchainId
            );
        }
    }

    @Override
    public BlockchainMeta getBlockchainMeta(String product, String blockchainId) {
        return blockchainRepository.getBlockchainMeta(product, blockchainId);
    }

    @Override
    public BlockchainMeta getBlockchainMetaByDomain(String domain) {
        return blockchainRepository.getBlockchainMetaByDomain(domain);
    }

    @Override
    public String getBlockchainDomain(String product, String blockchainId) {
        return blockchainRepository.getBlockchainDomain(product, blockchainId);
    }

    @Override
    public boolean updateBlockchainMeta(BlockchainMeta blockchainMeta) {
        return blockchainRepository.updateBlockchainMeta(blockchainMeta);
    }

    @Override
    public List<BlockchainMeta> getAllBlockchainMeta() {
        return blockchainRepository.getAllBlockchainMeta();
    }

    @Override
    public BlockchainAnchorProcess getBlockchainAnchorProcess(String product, String blockchainId) {
        AnchorProcessHeights heights = blockchainRepository.getAnchorProcessHeights(product, blockchainId);
        if (ObjectUtil.isNull(heights)) {
            log.error("null heights for blockchain {}-{}", product, blockchainId);
            return null;
        }
        return BlockchainAnchorProcess.convertFrom(heights);
    }

    @Override
    public List<BlockchainMeta> getAllServingBlockchains() {
        try {
            return blockchainRepository.getBlockchainMetaByState(BlockchainStateEnum.RUNNING);
        } catch (AntChainBridgeRelayerException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    e,
                    "failed to query all serving blockchains"
            );
        }
    }

    @Override
    public List<BlockchainMeta> getAllStoppedBlockchains() {
        try {
            return blockchainRepository.getBlockchainMetaByState(BlockchainStateEnum.STOPPED);
        } catch (AntChainBridgeRelayerException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    e,
                    "failed to query all stopped blockchains"
            );
        }
    }

    @Override
    public boolean checkIfDomainPrepared(String domain) {
        try {
            BlockchainMeta blockchainMeta = blockchainRepository.getBlockchainMetaByDomain(domain);
            if (ObjectUtil.isNull(blockchainMeta)) {
                return false;
            }
        } catch (Exception e) {
            log.error("failed to query blockchain by domain {}", domain, e);
            return false;
        }
        return true;
    }

    @Override
    public boolean checkIfDomainRunning(String domain) {
        try {
            BlockchainMeta blockchainMeta = blockchainRepository.getBlockchainMetaByDomain(domain);
            if (ObjectUtil.isNull(blockchainMeta)) {
                return false;
            }
            return blockchainMeta.isRunning();
        } catch (Exception e) {
            log.error("failed to query blockchain by domain {}", domain, e);
            return false;
        }
    }

    @Override
    public boolean checkIfDomainAMDeployed(String domain) {
        try {
            BlockchainMeta blockchainMeta = blockchainRepository.getBlockchainMetaByDomain(domain);
            if (ObjectUtil.isNull(blockchainMeta)) {
                return false;
            }
            return OnChainServiceStatusEnum.DEPLOY_FINISHED == blockchainMeta.getProperties().getAmServiceStatus();
        } catch (Exception e) {
            log.error("failed to query blockchain by domain {}", domain, e);
            return false;
        }
    }

    @Override
    public List<String> getBlockchainsByPluginServerId(String pluginServerId) {
        try {
            return blockchainRepository.getBlockchainMetaByPluginServerId(pluginServerId).stream()
                    .map(BlockchainMeta::getBlockchainId)
                    .collect(Collectors.toList());
        } catch (AntChainBridgeRelayerException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    e,
                    "failed to query blockchains by plugin server id {}"
            );
        }
    }

    @Override
    public void updateSDPMsgSeq(String receiverProduct, String receiverBlockchainId, String senderDomain, String from, String to, long newSeq) {
        throw new AntChainBridgeRelayerException(
                RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                "update SDP msg seq not supported for now"
        );
    }

    @Override
    public long querySDPMsgSeq(String receiverProduct, String receiverBlockchainId, String senderDomain, String from, String to) {
        try {
            AbstractBlockchainClient blockchainClient = blockchainClientPool.getClient(receiverProduct, receiverBlockchainId);
            if (ObjectUtil.isNull(blockchainClient)) {
                throw new AntChainBridgeRelayerException(
                        RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                        "failed to get blockchain client for blockchain ( product: {}, bcId: {})",
                        receiverProduct, receiverBlockchainId
                );
            }

            return blockchainClient.getSDPMsgClientContract().querySDPMsgSeqOnChain(
                    senderDomain,
                    CrossChainMsgACLItem.getIdentityHex(from),
                    blockchainClient.getDomain(),
                    CrossChainMsgACLItem.getIdentityHex(to)
            );
        } catch (AntChainBridgeRelayerException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    e,
                    "failed to query blockchains by plugin server id {}"
            );
        }
    }

    @Override
    public void checkTpBtaReadyOnReceivingChain(SDPMsgWrapper sdpMsgWrapper) {
        ThirdPartyProof tpProof = crossChainMessageRepository.getTpProof(sdpMsgWrapper.getAuthMsgWrapper().getUcpId());
        if (ObjectUtil.isNull(tpProof)) {
            log.info("process sdp msg from non-trust sending blockchain now, skip ptc trust uploading check");
            return;
        }

        // The third-party proof and its TpBTA describe the sending blockchain.
        // Checking the target domain here skips TpBTA upload whenever only the
        // source blockchain is trust-enabled, and the subsequent target-chain
        // submission then fails with "tpbta not found".
        if (blockchainRepository.hasBta(new CrossChainDomain(sdpMsgWrapper.getSenderBlockchainDomain()))) {
            AbstractBlockchainClient receivingClient = blockchainClientPool.getClient(
                    sdpMsgWrapper.getReceiverBlockchainProduct(),
                    sdpMsgWrapper.getReceiverBlockchainId()
            );
            if (ObjectUtil.isNotNull(receivingClient)
                    && !hasConfiguredPtcContract(receivingClient.queryBBCContext())) {
                // Some V0 BBC implementations (currently Dioxide) intentionally
                // verify the relayed AM without an on-chain PTC Hub. Calling the
                // V1 hasTpBta API for those targets fails before relayAuthMessage
                // can run. Trust synchronization is only applicable when the
                // receiving chain actually exposes a configured PTC contract.
                log.info(
                        "receiving blockchain {}-{} has no configured ptc contract, skip tpbta upload check",
                        sdpMsgWrapper.getReceiverBlockchainProduct(),
                        sdpMsgWrapper.getReceiverBlockchainId()
                );
                return;
            }
            checkTpBtaReadyOnReceivingChain(sdpMsgWrapper, tpProof);
        }
    }

    static boolean hasConfiguredPtcContract(AbstractBBCContext bbcContext) {
        return ObjectUtil.isNotNull(bbcContext)
                && ObjectUtil.isNotNull(bbcContext.getPtcContract())
                && StrUtil.isNotBlank(bbcContext.getPtcContract().getContractAddress())
                && !"empty".equalsIgnoreCase(bbcContext.getPtcContract().getContractAddress());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initTpBta(@NonNull String ptcServiceId, @NonNull IBlockchainTrustAnchor bta) {
        if (!ptcManager.isPtcServiceWork(ptcServiceId)) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "ptc service {} is not working, cannot setup bta", ptcServiceId
            );
        }
        if (!this.hasBlockchain(bta.getDomain().getDomain())) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "no blockchain found for domain {}", bta.getDomain().getDomain()
            );
        }

        if (ObjectUtil.isNull(bta.getBcOwnerSig())
                && ArrayUtil.equals(relayerCredentialManager.getLocalRelayerCredentialSubject().getSubjectPublicKey().getEncoded(), bta.getBcOwnerPublicKey())
        ) {
            ((AbstractBlockchainTrustAnchor) bta).setBcOwnerSigAlgo(relayerCredentialManager.getLocalNodeSigAlgo());
            bta.sign(relayerCredentialManager.getLocalRelayerPrivateKey());
            log.info("bta {} owned by relayer has no sig, so sign with relayer private key with sig algo {}",
                    bta.getDomain(), relayerCredentialManager.getLocalNodeSigAlgo().name());
        }

        // 1. verify bta and get tpbta
        IPTCService ptcService = ptcManager.getPtcService(ptcServiceId);
        ThirdPartyBlockchainTrustAnchor tpBta = ptcService.verifyBlockchainTrustAnchor(
                blockchainRepository.getDomainCert(bta.getDomain().getDomain()).getCrossChainCertificate(),
                bta
        );
        if (ObjectUtil.isNull(tpBta)) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "null tpbta after ptc service {} verifying the BTA from domain {} ", ptcServiceId, bta.getDomain().getDomain()
            );
        }
        TpBtaDO tpBtaDOMatched = ptcManager.getMatchedTpBta(tpBta.getCrossChainLane());
        if (ObjectUtil.isNotNull(tpBtaDOMatched)) {
            if ((tpBtaDOMatched.getCrossChainLane().equals(tpBta.getCrossChainLane()) && tpBtaDOMatched.getTpBtaVersion() >= tpBta.getTpbtaVersion())
                    || tpBtaDOMatched.getTpbta().type().ordinal() <= tpBta.type().ordinal()) {
                throw new AntChainBridgeRelayerException(
                        RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                        "tpbta {}-{} of blockchain {} from ptc {} conflicts with existing tpbta {}-{}",
                        tpBta.getCrossChainLane().getLaneKey(),
                        tpBta.getTpbtaVersion(),
                        bta.getDomain().getDomain(),
                        ptcServiceId,
                        tpBtaDOMatched.getCrossChainLane().getLaneKey(),
                        tpBtaDOMatched.getTpBtaVersion()
                );
            }
        }

        // 2. sync anchor consensus state to ptc
        BlockchainMeta blockchainMeta = getBlockchainMetaByDomain(bta.getDomain().getDomain());
        AbstractBlockchainClient blockchainClient = blockchainClientPool.getClient(blockchainMeta.getProduct(), blockchainMeta.getBlockchainId());
        if (ObjectUtil.isNull(blockchainClient)) {
            blockchainClient = blockchainClientPool.createClient(blockchainMeta);
        }
        ConsensusState anchorState = blockchainClient.getConsensusState(bta.getInitHeight());
        if (ObjectUtil.isNull(anchorState)) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "failed to query consensus state from blockchain {} - {}",
                    blockchainMeta.getProduct(), blockchainMeta.getBlockchainId()
            );
        }

        ValidatedConsensusState anchorVcs = ptcService.commitAnchorState(bta, tpBta, anchorState);
        if (ObjectUtil.isNull(anchorVcs)) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "invalid anchor validated consensus state {} of blockchain {} returned from ptc {}",
                    anchorState.getHeight().toString(), bta.getDomain().getDomain(), ptcServiceId
            );
        }

        if (!blockchainRepository.hasBta(bta.getDomain(), bta.getSubjectVersion())) {
            log.info("save bta {}-{} for blockchain {} - {}",
                    bta.getDomain().getDomain(), bta.getSubjectVersion(), blockchainMeta.getProduct(), blockchainMeta.getBlockchainId());
            blockchainRepository.saveBta(
                    BtaDO.builder()
                            .blockchainProduct(blockchainMeta.getProduct())
                            .blockchainId(blockchainMeta.getBlockchainId())
                            .bta(bta)
                            .build()
            );
        }

        log.info("save tpbta {}-{} for blockchain {} - {}",
                tpBta.getCrossChainLane().getLaneKey(), tpBta.getTpbtaVersion(), blockchainMeta.getProduct(), blockchainMeta.getBlockchainId());
        ptcManager.saveTpBta(blockchainMeta.getProduct(), blockchainMeta.getBlockchainId(), ptcServiceId, tpBta);
        if (!ptcService.getPtcFeatureDescriptor().isStorageEnabled()) {
            blockchainRepository.setValidatedConsensusState(
                    ValidatedConsensusStateDO.builder()
                            .blockchainProduct(blockchainMeta.getProduct())
                            .blockchainId(blockchainMeta.getBlockchainId())
                            .ptcServiceId(ptcServiceId)
                            .tpbtaLane(tpBta.getCrossChainLane())
                            .validatedConsensusState(anchorVcs)
                            .build()
            );
        }
    }

    @Override
    public List<TpBtaDesc> getMatchedTpBtaDescList(CrossChainLane crossChainLane) {
        List<TpBtaDO> tpBtaDOList = ptcManager.getAllValidTpBtaForDomain(crossChainLane.getSenderDomain());
        if (ObjectUtil.isEmpty(tpBtaDOList)) {
            return ListUtil.empty();
        }

        return tpBtaDOList.stream().map(x -> new TpBtaDesc(x.getCrossChainLane(), x.getTpBtaVersion())).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upgradeTpBta(String ptcServiceId, CrossChainLane tpbtaLane, IBlockchainTrustAnchor newBta) {

        log.info("upgradeTpBta, ptcServiceId: {}, tpbtaLane: {}, with newBta: {}", ptcServiceId, tpbtaLane.getLaneKey(), ObjectUtil.isNull(newBta));
        if (!ptcManager.isPtcServiceWork(ptcServiceId)) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "ptc service {} is not working, cannot setup bta", ptcServiceId
            );
        }
        if (!this.hasBlockchain(tpbtaLane.getSenderDomain().getDomain())) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "no blockchain found for domain {}", tpbtaLane.getSenderDomain().getDomain()
            );
        }

        TpBtaDO latestTpBta = ptcManager.getLatestExactTpBta(tpbtaLane);
        if (ObjectUtil.isNull(latestTpBta)) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "no tpbta {} found in storage, please initialize first", tpbtaLane.getLaneKey()
            );
        }

        BtaDO btaDO = null;
        if (ObjectUtil.isNull(newBta)) {
            log.info("upgradeTpBta, no new bta provided, use the latest bta from blockchain {}", tpbtaLane.getSenderDomain().getDomain());
            btaDO = blockchainRepository.getBta(tpbtaLane.getSenderDomain());
            if (ObjectUtil.isNull(btaDO)) {
                throw new AntChainBridgeRelayerException(
                        RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                        "no bta found for domain {}", tpbtaLane.getSenderDomain().getDomain()
                );
            }
            newBta = btaDO.getBta();
        } else {
            if (!newBta.getDomain().equals(tpbtaLane.getSenderDomain())) {
                throw new AntChainBridgeRelayerException(
                        RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                        "invalid new bta {} provided, the domain does not match the tpbta lane {}",
                        newBta.getDomain().getDomain(), tpbtaLane.getLaneKey()
                );
            }
            if (!blockchainRepository.hasBta(newBta.getDomain(), newBta.getSubjectVersion())) {
                BlockchainMeta blockchainMeta = blockchainRepository.getBlockchainMetaByDomain(tpbtaLane.getSenderDomain().getDomain());
                log.info("save new bta {}-{} for blockchain {} - {}",
                        newBta.getDomain().getDomain(), newBta.getSubjectVersion(), blockchainMeta.getProduct(), blockchainMeta.getBlockchainId());
                btaDO = BtaDO.builder()
                        .blockchainProduct(blockchainMeta.getProduct())
                        .blockchainId(blockchainMeta.getBlockchainId())
                        .bta(newBta)
                        .build();
                blockchainRepository.saveBta(btaDO);
            }
        }

        IPTCService ptcService = ptcManager.getPtcService(ptcServiceId);
        // maybe we need an interface to apply and upgrade the TpBta ?
        ThirdPartyBlockchainTrustAnchor tpBta = ptcService.verifyBlockchainTrustAnchor(
                blockchainRepository.getDomainCert(newBta.getDomain().getDomain()).getCrossChainCertificate(),
                newBta
        );
        if (ObjectUtil.isNull(tpBta)) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "null tpbta after ptc service {} verifying the BTA from domain {} ", ptcServiceId, newBta.getDomain().getDomain()
            );
        }

        log.info("save new TpBta {}-{} of blockchain {} from ptc {}",
                tpBta.getCrossChainLane().getLaneKey(), tpBta.getTpbtaVersion(), btaDO.getDomain().getDomain(), ptcServiceId);
        ptcManager.saveTpBta(btaDO.getBlockchainProduct(), btaDO.getBlockchainId(), ptcServiceId, tpBta);
    }

    @Override
    public BtaDO queryLatestVersionBta(CrossChainDomain domain) {
        return blockchainRepository.getBta(domain);
    }

    private void checkTpBtaReadyOnReceivingChain(SDPMsgWrapper sdpMsgWrapper, ThirdPartyProof tpProof) {
        AbstractBlockchainClient blockchainClient = blockchainClientPool.getClient(
                sdpMsgWrapper.getReceiverBlockchainProduct(),
                sdpMsgWrapper.getReceiverBlockchainId()
        );
        if (ObjectUtil.isNull(blockchainClient)) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "failed to get blockchain client for blockchain ( product: {}, bcId: {})",
                    sdpMsgWrapper.getReceiverBlockchainProduct(),
                    sdpMsgWrapper.getReceiverBlockchainId()
            );
        }
        if (!checkIfTpBtaOnChain(blockchainClient, tpProof.getTpbtaCrossChainLane(), tpProof.getTpbtaVersion())) {
            ThirdPartyBlockchainTrustAnchor tpBta;
            if (!ptcManager.hasExactTpBta(tpProof.getTpbtaCrossChainLane(), tpProof.getTpbtaVersion())) {
                log.info("no tpbta {}-{} in storage yet, try to query from bcdns", tpProof.getTpbtaCrossChainLane().getLaneKey(), tpProof.getTpbtaVersion());

                IBlockChainDomainNameService bcdnsService = bcdnsManager.getBCDNSService(CrossChainDomain.ROOT_DOMAIN_SPACE);
                tpBta = bcdnsService.queryThirdPartyBlockchainTrustAnchor(
                        QueryThirdPartyBlockchainTrustAnchorRequest.builder()
                                .tpBtaCrossChainLane(tpProof.getTpbtaCrossChainLane())
                                .tpbtaVersion(tpProof.getTpbtaVersion())
                                .build()
                );
                if (ObjectUtil.isNull(tpBta)) {
                    throw new AMProcessException("no tpbta from bcdns: (tpbta lane: {}, tpbta version: {})",
                            tpProof.getTpbtaCrossChainLane().getLaneKey(), tpProof.getTpbtaVersion());
                }

                log.info("save tpbta {}-{} to storage", tpProof.getTpbtaCrossChainLane().getLaneKey(), tpProof.getTpbtaVersion());
                ptcManager.saveTpBta(sdpMsgWrapper.getSenderBlockchainProduct(), sdpMsgWrapper.getSenderBlockchainId(), "", tpBta);
            } else {
                log.debug("tpbta {}-{} already in storage, read it from storage", tpProof.getTpbtaCrossChainLane().getLaneKey(), tpProof.getTpbtaVersion());
                tpBta = ptcManager.getExactTpBta(tpProof.getTpbtaCrossChainLane(), tpProof.getTpbtaVersion()).getTpbta();
            }

            checkPtcTrustRootReadyOnReceivingChain(
                    sdpMsgWrapper,
                    tpBta.getSignerPtcCredentialSubject().getApplicant(),
                    tpBta.getPtcVerifyAnchorVersion()
            );

            log.info("tpbta {}-{} not on chain, add it to chain now", tpBta.getCrossChainLane().getLaneKey(), tpBta.getTpbtaVersion());
            blockchainClient.getPtcContract().addTpBtaOnChain(tpBta);

            ifTpBtaReadyOnChainMap.put(
                    tpbtaOnChainOrNotKey(blockchainClient.getDomain(), tpProof.getTpbtaCrossChainLane(), tpProof.getTpbtaVersion()),
                    true
            );
        }
    }

    private boolean checkIfTpBtaOnChain(AbstractBlockchainClient blockchainClient, CrossChainLane tpbtaLane, int tpbtaVersion) {
        String key = tpbtaOnChainOrNotKey(blockchainClient.getDomain(), tpbtaLane, tpbtaVersion);
        if (ifTpBtaReadyOnChainMap.getOrDefault(key, false)) {
            return true;
        }
        ifTpBtaReadyOnChainMap.put(key, blockchainClient.getPtcContract().checkIfTpBtaOnChain(tpbtaLane, tpbtaVersion));
        return ifTpBtaReadyOnChainMap.get(key);
    }

    private void checkPtcTrustRootReadyOnReceivingChain(SDPMsgWrapper sdpMsgWrapper, ObjectIdentity ptcOwnerOid, BigInteger currVerifyAnchorVersion) {
        AbstractBlockchainClient blockchainClient = blockchainClientPool.getClient(
                sdpMsgWrapper.getReceiverBlockchainProduct(),
                sdpMsgWrapper.getReceiverBlockchainId()
        );
        if (ObjectUtil.isNull(blockchainClient)) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "failed to get blockchain client for blockchain ( product: {}, bcId: {})",
                    sdpMsgWrapper.getReceiverBlockchainProduct(),
                    sdpMsgWrapper.getReceiverBlockchainId()
            );
        }
        if (checkIfNeedToFetchVerifyAnchor(blockchainClient, ptcOwnerOid, currVerifyAnchorVersion)) {
            PtcTrustRootDO ptcTrustRoot = queryPtcTrustRootFromBcdns(ptcOwnerOid);
            if (!blockchainClient.getPtcContract().checkIfPtcTypeSupportOnChain(ptcTrustRoot.getPtcCredentialSubject().getType())) {
                throw new AMProcessException("ptc type {} not supported on chain", ptcTrustRoot.getPtcCredentialSubject().getType());
            }

            String ptcOwnerOidHex = ptcOwnerOid.toHex();
            BigInteger latestVersion = ptcTrustRoot.getLatestVerifyAnchor().getVersion();
            if (latestVersion.compareTo(currVerifyAnchorVersion) < 0) {
                throw new AMProcessException("ptc trust root for {} not on chain, but latest verify anchor version {} is less than the version {} in tpbta",
                        ptcOwnerOidHex, latestVersion.toString(), currVerifyAnchorVersion.toString());
            }

            log.info("ptc trust root for {} not on chain, add it to chain now", ptcOwnerOidHex);
            blockchainClient.getPtcContract().updatePtcTrustRoot(PtcTrustRootDO.builder().ptcTrustRoot(ptcTrustRoot.getPtcTrustRoot()).build());

            log.info("mark that latest verify anchor version {} from ptc owned by {} uploaded on the blockchain {}",
                    latestVersion, ptcOwnerOidHex, sdpMsgWrapper.getReceiverBlockchainDomain());
            latestVerifyAnchorVersionOnchain.put(
                    ptcTrustRootOnChainKey(sdpMsgWrapper.getReceiverBlockchainDomain(), ptcOwnerOid),
                    latestVersion
            );
        }
    }

    private PtcTrustRootDO queryPtcTrustRootFromBcdns(ObjectIdentity ptcOwnerOid) {
        log.info("try to query ptc trust root from bcdns for ptc oid: {}", ptcOwnerOid.toHex());

        IBlockChainDomainNameService bbcService = bcdnsManager.getBCDNSService(CrossChainDomain.ROOT_DOMAIN_SPACE);
        if (ObjectUtil.isNull(bbcService)) {
            throw new AMProcessException("no bcdns service client for domain space: {}", CrossChainDomain.ROOT_DOMAIN_SPACE);
        }
        PTCTrustRoot ptcTrustRoot = bbcService.queryPTCTrustRoot(ptcOwnerOid);
        if (ObjectUtil.isNull(ptcTrustRoot)) {
            throw new AMProcessException("no ptc trust root from bcdns: (ptc owner: {})", ptcOwnerOid.toHex());
        }
        if (ObjectUtil.isEmpty(ptcTrustRoot.getVerifyAnchorMap())) {
            throw new AMProcessException("no verify anchor in ptc trust root from bcdns: (ptc owner: {})", ptcOwnerOid.toHex());
        }
        return ptcManager.savePtcTrustRoot("", ptcTrustRoot);
    }

    private boolean checkIfNeedToFetchVerifyAnchor(AbstractBlockchainClient blockchainClient, ObjectIdentity ptcOwnerOid, BigInteger tpbtaVAVersion) {
        String key = ptcVerifyAnchorOnChainOrNotKey(blockchainClient.getDomain(), ptcOwnerOid);
        if (latestVerifyAnchorVersionOnchain.containsKey(key) && latestVerifyAnchorVersionOnchain.get(key).compareTo(tpbtaVAVersion) >= 0) {
            return false;
        }
        boolean result = blockchainClient.getPtcContract().checkIfVerifyAnchorOnChain(ptcOwnerOid, tpbtaVAVersion);
        if (result) {
            latestVerifyAnchorVersionOnchain.put(key, tpbtaVAVersion);
        }
        return !result;
    }

    private boolean isBlockchainExists(String product, String blockchainId) {
        return blockchainRepository.hasBlockchain(product, blockchainId);
    }

    private void deployHeteroBlockchainAMContract(BlockchainMeta blockchainMeta) {
        AbstractBlockchainClient blockchainClient = blockchainClientPool.getClient(
                blockchainMeta.getProduct(),
                blockchainMeta.getBlockchainId()
        );

        if (ObjectUtil.isNull(blockchainClient)) {
            blockchainClient = blockchainClientPool.createClient(blockchainMeta);
        }

        if (!(blockchainClient instanceof HeteroBlockchainClient)) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "wrong type of client for blockchain ( product: {}, bcId: {}, pluginServer: {})",
                    blockchainMeta.getProduct(), blockchainMeta.getBlockchainId(), blockchainMeta.getPluginServerId()
            );
        }

        AbstractBBCContext bbcContext = blockchainClient.queryBBCContext();

        if (
                ObjectUtil.isNull(bbcContext.getAuthMessageContract())
                        || ContractStatusEnum.INIT == bbcContext.getAuthMessageContract().getStatus()
        ) {
            blockchainClient.getAMClientContract().deployContract();
        }

        // setup-bbccontracts is also the explicit reconciliation entry point
        // for versioned system-contract upgrades. BBC implementations must
        // keep setup idempotent when an existing deployment is current.
        blockchainClient.getSDPMsgClientContract().deployContract();

        // if monitor is supported, it needs to be deployed before ptc
        boolean isMonitorSupport = true;
        try {
            blockchainClient.getMonitorClientContract().deployContract();
        } catch (BbcInterfaceNotSupportException e) {
            log.info("setup monitor contract not support for blockchain product: {}", blockchainMeta.getProduct());
            isMonitorSupport = false;
        }

        boolean isPtcSupport = true;
        try {
            // setup-bbccontracts is an explicit reconciliation request. The
            // BBC implementation must be allowed to probe and replace a
            // legacy PTC Hub even when the persisted context says READY.
            blockchainClient.getPtcContract().deployContract();
        } catch (BbcInterfaceNotSupportException e) {
            log.info("setup ptc contract not support for blockchain product: {}", blockchainMeta.getProduct());
            isPtcSupport = false;
        }

        bbcContext = blockchainClient.queryBBCContext();
        if (ObjectUtil.isNull(bbcContext.getAuthMessageContract()) || StrUtil.isEmpty(bbcContext.getAuthMessageContract().getContractAddress())) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "am contract is empty for blockchain ( product: {}, bcId: {}, pluginServer: {})",
                    blockchainMeta.getProduct(), blockchainMeta.getBlockchainId(), blockchainMeta.getPluginServerId()
            );
        }
        if (ObjectUtil.isNull(bbcContext.getSdpContract()) || StrUtil.isEmpty(bbcContext.getSdpContract().getContractAddress())) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "sdp contract is empty for blockchain ( product: {}, bcId: {}, pluginServer: {})",
                    blockchainMeta.getProduct(), blockchainMeta.getBlockchainId(), blockchainMeta.getPluginServerId()
            );
        }
        if (isMonitorSupport && (ObjectUtil.isNull(bbcContext.getMonitorContract()) || StrUtil.isEmpty(bbcContext.getMonitorContract().getContractAddress()))) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "monitor contract is empty for blockchain ( product: {}, bcId: {}, pluginServer: {})",
                    blockchainMeta.getProduct(), blockchainMeta.getBlockchainId(), blockchainMeta.getPluginServerId()
            );
        }
        if (isPtcSupport && (ObjectUtil.isNull(bbcContext.getPtcContract()) || StrUtil.isEmpty(bbcContext.getPtcContract().getContractAddress()))) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                    "ptc contract is empty for blockchain ( product: {}, bcId: {}, pluginServer: {})",
                    blockchainMeta.getProduct(), blockchainMeta.getBlockchainId(), blockchainMeta.getPluginServerId()
            );
        }

        blockchainClient.getAMClientContract()
                .setProtocol(
                        bbcContext.getSdpContract().getContractAddress(),
                        Integer.toString(UpperProtocolTypeBeyondAMEnum.SDP.getCode())
                );
        if (isPtcSupport) {
            blockchainClient.getAMClientContract().setPtcContract(bbcContext.getPtcContract().getContractAddress());
        }

        blockchainClient.getSDPMsgClientContract()
                .setAmContract(bbcContext.getAuthMessageContract().getContractAddress());

        if (isMonitorSupport) {
            blockchainClient.getSDPMsgClientContract()
                    .setMonitorContract(bbcContext.getMonitorContract().getContractAddress());

            blockchainClient.getMonitorClientContract()
                    .setMonitorControl(MONITOR_OPEN);

            blockchainClient.getMonitorClientContract()
                    .setProtocolInMonitor(bbcContext.getSdpContract().getContractAddress());

            if (isPtcSupport) {
                blockchainClient.getMonitorClientContract()
                    .setPtcHubInMonitorVerifier(bbcContext.getPtcContract().getContractAddress());
            }
        }

        bbcContext = blockchainClient.queryBBCContext();

        blockchainMeta.getProperties().setAmClientContractAddress(bbcContext.getAuthMessageContract().getContractAddress());
        blockchainMeta.getProperties().setSdpMsgContractAddress(bbcContext.getSdpContract().getContractAddress());
        if (isMonitorSupport) {
            blockchainMeta.getProperties().setMonitorContractAddress(bbcContext.getMonitorContract().getContractAddress());
        }
        if (isPtcSupport) {
            blockchainMeta.getProperties().setPtcContractAddress(bbcContext.getPtcContract().getContractAddress());
        }
        blockchainMeta.getProperties().setBbcContext((DefaultBBCContext) bbcContext);

        blockchainClient.setBlockchainMeta(blockchainMeta);
        updateBlockchainMeta(blockchainMeta);
    }

    private String tpbtaOnChainOrNotKey(String domain, CrossChainLane lane, int tpbtaVersion) {
        return StrUtil.format("{}:{}:{}", domain, lane.getLaneKey(), tpbtaVersion);
    }

    private String ptcTrustRootOnChainKey(String domain, ObjectIdentity ptcOwnerOid) {
        return StrUtil.format("{}:{}", domain, ptcOwnerOid.toHex());
    }

    private String ptcVerifyAnchorOnChainOrNotKey(String domain, ObjectIdentity ptcOwnerOid) {
        return StrUtil.format("{}:{}", domain, ptcOwnerOid.toHex());
    }

    /**
     * 判断消息是否超时并处理
     * - 为超时跨链消息请求ptc验证背书
     * - 构造跨链超时消息存储到数据库
     *
     * @param sdpMsgWrapper
     * @return 消息超时返回true，未超时返回false
     */
    @Override
    public boolean checkAndProcessMessageTimeouts(SDPMsgWrapper sdpMsgWrapper) {
        try {
            // 1. 请求异常链（接收链）的TpBta
            CrossChainLane receiver2senderCCL = new CrossChainLane(
                    new CrossChainDomain(sdpMsgWrapper.getReceiverBlockchainDomain()),
                    new CrossChainDomain(sdpMsgWrapper.getSenderBlockchainDomain())
            );
            TpBtaDO receiverTpBtaDO = ptcManager.getMatchedTpBta(receiver2senderCCL);
            if (ObjectUtil.isNull(receiverTpBtaDO)) {
                throw new AntChainBridgeRelayerException(
                        RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                        "can not find the matched tpBta for {}", receiver2senderCCL.getLaneKey()
                );
            }

            // 2. 获取中继记录的异常链的当前区块信息
            // todo: 此处应为直接从数据库获取BlockState
            Long recvHeight = blockchainRepository.getAnchorProcessHeightWithoutFlush(
                    sdpMsgWrapper.getReceiverBlockchainProduct(),
                    sdpMsgWrapper.getReceiverBlockchainId(),
                    BlockTaskTypeEnum.NOTIFY.toNotifyWorkerHeightType(NotifyTaskTypeEnum.SYSTEM_WORKER.getCode()),
                    receiverTpBtaDO.getCrossChainLane()
            );
            BlockState recvBlockState = new BlockState();
            recvBlockState.setHeight(BigInteger.valueOf(recvHeight));
            if (sdpMsgWrapper.getSdpMessage().getTimeoutMeasure() != TimeoutMeasureEnum.NO_TIMEOUT
                    && sdpMsgWrapper.getSdpMessage().getTimeoutMeasure() != TimeoutMeasureEnum.RECEIVER_HEIGHT) {
                throw new AntChainBridgeRelayerException(RelayerErrorCodeEnum.CORE_BLOCKCHAIN_ERROR,
                        "type of timeout measure {} is not supported now", sdpMsgWrapper.getSdpMessage().getTimeoutMeasure());
            }

            // 3. 判断消息是否超时
            if (((SDPMessageV3) sdpMsgWrapper.getSdpMessage()).isTimeout(recvBlockState)) {
                log.info("the sdp msg {} is timeout in the cross-chain flow from {} to {}, " +
                                "generate a time out ucp_msg and am_msg",
                        sdpMsgWrapper.getId(),
                        sdpMsgWrapper.getSenderBlockchainDomain(),
                        sdpMsgWrapper.getReceiverBlockchainDomain());

                // 4. 交易超时，构造超时区块信息
                IPTCService ptcService = ptcManager.getPtcService(receiverTpBtaDO.getPtcServiceId());
                ValidatedConsensusState validatedConsensusState;
                if (ptcService.getPtcFeatureDescriptor().isStorageEnabled()) {
                    validatedConsensusState = new ValidatedConsensusStateV1();
                    validatedConsensusState.setHeight(BigInteger.valueOf(recvHeight));
                    validatedConsensusState.setDomain(sdpMsgWrapper.getSdpMessage().getTargetDomain());
                } else {
                    ValidatedConsensusStateDO validatedConsensusStateDO = blockchainRepository.getValidatedConsensusState(
                            receiverTpBtaDO.getPtcServiceId(),
                            sdpMsgWrapper.getReceiverBlockchainDomain(),
                            receiverTpBtaDO.getCrossChainLane(),
                            BigInteger.valueOf(recvHeight)
                    );
                    if (ObjectUtil.isNull(validatedConsensusStateDO)) {
                        throw new BsValidationException(
                                sdpMsgWrapper.getReceiverBlockchainDomain(),
                                recvBlockState.getHash(),
                                recvBlockState.getHeight(),
                                recvBlockState.getTimestamp(),
                                receiverTpBtaDO.getCrossChainLane().getLaneKey(),
                                StrUtil.format("none validated consensus state for chain {} found",
                                        sdpMsgWrapper.getReceiverBlockchainDomain())
                        );
                    }

                    validatedConsensusState = validatedConsensusStateDO.getValidatedConsensusState();
                }

                // 5. 向ptc请求超时区块的背书
                log.info("calling ptc {} to endorse block state {}-{}-{}-{} with TpBTA {}:{}",
                        receiverTpBtaDO.getPtcServiceId(),
                        validatedConsensusState.getDomain(),
                        validatedConsensusState.getHash(),
                        validatedConsensusState.getHeight(),
                        validatedConsensusState.getStateTimestamp(),
                        receiverTpBtaDO.getCrossChainLane().getLaneKey(),
                        receiverTpBtaDO.getTpBtaVersion()
                );
                ThirdPartyProof thirdPartyProof = ptcService.endorseBlockState(receiverTpBtaDO.getTpbta(),
                        new CrossChainDomain(sdpMsgWrapper.getSenderBlockchainDomain()),
                        validatedConsensusState);
                if (ObjectUtil.isNull(thirdPartyProof)) {
                    throw new BsValidationException(
                            validatedConsensusState.getDomain().getDomain(),
                            validatedConsensusState.getHash(),
                            validatedConsensusState.getHeight(),
                            validatedConsensusState.getStateTimestamp(),
                            receiverTpBtaDO.getCrossChainLane().getLaneKey(),
                            "none tp proof found");
                }
                log.info("successful to endorse block state {}-{}-{}-{} with TpBTA {}",
                        validatedConsensusState.getDomain().getDomain(),
                        validatedConsensusState.getHash(),
                        validatedConsensusState.getHeight(),
                        validatedConsensusState.getStateTimestamp(),
                        receiverTpBtaDO.getCrossChainLane().getLaneKey());

                // 6. 构造特殊UCP消息
                UniformCrosschainPacketContext ucpContext = buildProvedUCP(
                        thirdPartyProof,
                        receiverTpBtaDO,
                        sdpMsgWrapper.getReceiverBlockchainProduct(),
                        sdpMsgWrapper.getReceiverBlockchainId());
                crossChainMessageRepository.putUniformCrosschainPacket(ucpContext);

                // 7. 构造特殊AM消息
                AuthMsgWrapper authMsgWrapper = AuthMsgWrapper.buildFrom(
                        sdpMsgWrapper.getReceiverBlockchainProduct(),
                        sdpMsgWrapper.getReceiverBlockchainId(),
                        sdpMsgWrapper.getReceiverBlockchainDomain(),
                        ucpContext.getUcpId(),
                        AuthMessageFactory.createAuthMessage(thirdPartyProof.getResp().getBody())
                );
                authMsgWrapper.setProcessState(AuthMsgProcessStateEnum.PROVED);
                crossChainMessageRepository.putAuthMessageWithIdReturned(authMsgWrapper);

                return true;
            } else {
                return false;
            }
        } catch (AntChainBridgeRelayerException e) {
            log.warn("handle timeout sdp msg {} exception but can be tolerated", sdpMsgWrapper.getId(), e);
            return false;
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.SERVICE_HANDLE_TIMEOUT_MESSAGE_EXCEPTION,
                    StrUtil.format("handle timeout sdp msg {} exception", sdpMsgWrapper.getId()), e
            );
        }
    }

    private @NonNull UniformCrosschainPacketContext buildProvedUCP(ThirdPartyProof thirdPartyProof, TpBtaDO tpBtaDO, String product, String blockchainId) {
        BlockState blockStateValidated = BlockState.decode(
                SDPMessageFactory.createSDPMessage(
                        AuthMessageFactory.createAuthMessage(thirdPartyProof.getResp().getBody()).getPayload()
                ).getPayload()
        );
        CrossChainMessage ccm = CrossChainMessage.createCrossChainMessage(
                CrossChainMessage.CrossChainMessageType.AUTH_MSG,
                blockStateValidated.getHeight(),
                blockStateValidated.getTimestamp(),
                blockStateValidated.getHash(),
                thirdPartyProof.getResp().getBody(),
                new byte[0],
                new byte[0],
                new byte[0]
        );

        UniformCrosschainPacket ucp = new UniformCrosschainPacket(
                blockStateValidated.getDomain(),
                ccm,
                tpBtaDO.getTpbta().getSignerPtcCredentialSubject().getApplicant()
        );
        ucp.setTpProof(thirdPartyProof);

        UniformCrosschainPacketContext ucpContext = new UniformCrosschainPacketContext();
        ucpContext.setUcp(ucp);
        ucpContext.setFromNetwork(false);
        ucpContext.setProduct(product);
        ucpContext.setBlockchainId(blockchainId);
        ucpContext.setProcessState(UniformCrosschainPacketStateEnum.PROVED);

        ucpContext.setTpbtaVersion(thirdPartyProof.getTpbtaVersion());
        ucpContext.setTpbtaLaneKey(thirdPartyProof.getTpbtaCrossChainLane().getLaneKey());
        ucpContext.setRelayerId(relayerCredentialManager.getLocalNodeId());

        return ucpContext;
    }
}
