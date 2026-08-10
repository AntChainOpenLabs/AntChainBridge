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

package com.alipay.antchain.bridge.relayer.server.admin.impl;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.core.base.ConsensusState;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.base.CrossChainLane;
import com.alipay.antchain.bridge.commons.core.bta.BlockchainTrustAnchorFactory;
import com.alipay.antchain.bridge.relayer.commons.constant.Constants;
import com.alipay.antchain.bridge.relayer.commons.constant.OnChainServiceStatusEnum;
import com.alipay.antchain.bridge.relayer.commons.constant.SDPMsgProcessStateEnum;
import com.alipay.antchain.bridge.relayer.commons.model.BlockchainMeta;
import com.alipay.antchain.bridge.relayer.commons.model.BtaDO;
import com.alipay.antchain.bridge.relayer.commons.model.SDPMsgWrapper;
import com.alipay.antchain.bridge.relayer.commons.model.TpBtaDO;
import com.alipay.antchain.bridge.relayer.core.manager.bcdns.IBCDNSManager;
import com.alipay.antchain.bridge.relayer.core.manager.blockchain.IBlockchainManager;
import com.alipay.antchain.bridge.relayer.core.manager.ptc.IPtcManager;
import com.alipay.antchain.bridge.relayer.core.types.blockchain.AbstractBlockchainClient;
import com.alipay.antchain.bridge.relayer.core.types.blockchain.BlockchainAnchorProcess;
import com.alipay.antchain.bridge.relayer.core.types.blockchain.BlockchainClientPool;
import com.alipay.antchain.bridge.relayer.dal.repository.ISystemConfigRepository;
import com.alipay.antchain.bridge.relayer.dal.repository.impl.BlockchainIdleDCache;
import com.alipay.antchain.bridge.relayer.dal.repository.impl.CrossChainMessageRepository;
import com.alipay.antchain.bridge.relayer.facade.admin.types.BlockchainId;
import com.alipay.antchain.bridge.relayer.facade.admin.types.ConsensusStateInfo;
import com.alipay.antchain.bridge.relayer.facade.admin.types.SysContractsInfo;
import com.alipay.antchain.bridge.relayer.facade.admin.types.TpBtaInfo;
import com.alipay.antchain.bridge.relayer.server.admin.AbstractNamespace;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Slf4j
public class BlockchainNamespace extends AbstractNamespace {

    @Resource
    private IBlockchainManager blockchainManager;

    @Resource
    private IBCDNSManager bcdnsManager;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private ISystemConfigRepository systemConfigRepository;

    @Resource
    private BlockchainClientPool blockchainClientPool;

    @Resource
    private IPtcManager ptcManager;

    @Resource
    private CrossChainMessageRepository crossChainMessageRepository;

    @Resource
    private BlockchainIdleDCache blockchainIdleDCache;

    public BlockchainNamespace() {
        addCommand("getBlockchainIdByDomain", this::getBlockchainIdByDomain);
        addCommand("getBlockchain", this::getBlockchain);
        addCommand("getBlockchainContracts", this::getBlockchainContracts);
        addCommand("getBlockchainHeights", this::getBlockchainHeights);
        addCommand("addBlockchainAnchor", this::addBlockchainAnchor);
        addCommand("setupBBCContracts", this::setupBBCContracts);
        addCommand("registerTpBta", this::registerTpBta);
        addCommand("queryValidTpBtaList", this::queryValidTpBtaList);
        addCommand("upgradeTpBta", this::upgradeTpBta);
        addCommand("queryExactTpBta", this::queryExactTpBta);
        addCommand("deployBBCContractsAsync", this::deployBBCContractsAsync);
        addCommand("updateBlockchainAnchor", this::updateBlockchainAnchor);
        addCommand("updateBlockchainProperty", this::updateBlockchainProperty);
        addCommand("startBlockchainAnchor", this::startBlockchainAnchor);
        addCommand("stopBlockchainAnchor", this::stopBlockchainAnchor);
        addCommand("setTxPendingLimit", this::setTxPendingLimit);
        addCommand("querySDPMsgSeq", this::querySDPMsgSeq);
        addCommand("getLatestVersionOfBta", this::getLatestVersionOfBta);
        addCommand("readLatestConsensusInfo", this::readLatestConsensusInfo);
        addCommand("retrySdpMessage", this::retrySdpMessage);
    }

    Object getBlockchainIdByDomain(String... args) {
        if (args.length != 1) {
            return "wrong number of arguments";
        }
        String domain = args[0];
        try {
            BlockchainMeta blockchainMeta = blockchainManager.getBlockchainMetaByDomain(domain);
            if (ObjectUtils.isEmpty(blockchainMeta)) {
                return "none blockchain found";
            }
            BlockchainId blockchainId = new BlockchainId();
            blockchainId.setBlockchainId(
                    blockchainMeta.getBlockchainId()
            );
            blockchainId.setProduct(
                    blockchainMeta.getProduct()
            );
            return JSON.toJSONString(blockchainId);
        } catch (Throwable e) {
            log.error("failed to get blockchain id for domain {}", domain, e);
            return "get blockchain id failed: " + e.getMessage();
        }
    }

    /**
     * 查询区块链
     *
     * @return
     */
    Object getBlockchain(String... args) {
        String product = args[0];
        String blockchainId = args[1];

        BlockchainMeta blockchainMeta = blockchainManager.getBlockchainMeta(product, blockchainId);
        if (blockchainMeta == null) {
            return "blockchain not exist.";
        }

        return JSONObject.toJSONString(blockchainMeta, SerializerFeature.PrettyFormat);
    }

    /**
     * 查询区块链
     *
     * @return
     */
    Object getBlockchainContracts(String... args) {
        String product = args[0];
        String blockchainId = args[1];

        BlockchainMeta blockchainMeta = blockchainManager.getBlockchainMeta(product, blockchainId);
        if (blockchainMeta == null) {
            return "blockchain not exist.";
        }

        SysContractsInfo sysContractsInfo = new SysContractsInfo();
        sysContractsInfo.setAmContract(
                ObjectUtil.defaultIfEmpty(
                        blockchainMeta.getProperties().getAmClientContractAddress(), "empty"
                )
        );
        sysContractsInfo.setSdpContract(
                ObjectUtil.defaultIfEmpty(
                        blockchainMeta.getProperties().getSdpMsgContractAddress(), "empty"
                )
        );
        sysContractsInfo.setPtcContract(
                ObjectUtil.defaultIfNull(
                        blockchainMeta.getProperties().getPtcContractAddress(), "empty"
                )
        );

        sysContractsInfo.setMonitorContract(
                ObjectUtil.defaultIfNull(
                        blockchainMeta.getProperties().getMonitorContractAddress(), "empty"
                )
        );

        OnChainServiceStatusEnum amStatus = blockchainMeta.getProperties().getAmServiceStatus();
        sysContractsInfo.setState(
                ObjectUtil.isNull(amStatus)? "" : amStatus.name()
        );

        return JSON.toJSONString(sysContractsInfo);
    }

    Object getBlockchainHeights(String... args) {
        String product = args[0];
        String blockchainId = args[1];

        BlockchainMeta blockchainMeta = blockchainManager.getBlockchainMeta(product, blockchainId);
        if (blockchainMeta == null) {
            return "blockchain not exist.";
        }

        BlockchainAnchorProcess anchorProcess = blockchainManager.getBlockchainAnchorProcess(product, blockchainId);
        if (ObjectUtil.isNull(anchorProcess)) {
            return "none heights found";
        }

        return JSON.toJSONString(anchorProcess, SerializerFeature.PrettyFormat);
    }

    Object addBlockchainAnchor(String... args) {
        if (args.length != 8 && args.length != 7) {
            return "wrong number of arguments";
        }

        String product = args[0];
        String blockchainId = args[1];
        String domain = args[2];
        String pluginServerId = args[3];
        String alias = args[4];
        String desc = args[5];
        String heteroConf = args[6];
        boolean isReliableRelayOn = false;
        if (args.length == 8) {
            isReliableRelayOn = Boolean.parseBoolean(args[7]);
        }

        try {
            if (blockchainManager.hasBlockchain(domain)) {
                return "blockchain already exist";
            }

            byte[] rawConf;
            Path confPath = Paths.get(heteroConf);
            if (FileUtil.isFile(confPath, false)) {
                rawConf = Files.readAllBytes(confPath);
            } else {
                rawConf = heteroConf.getBytes();
            }

            DefaultBBCContext bbcContext = new DefaultBBCContext();
            bbcContext.setConfForBlockchainClient(rawConf);
            bbcContext.setReliable(isReliableRelayOn);

            Map<String, String> clientConfig = new HashMap<>();
            clientConfig.put(Constants.HETEROGENEOUS_BBC_CONTEXT, JSON.toJSONString(bbcContext));

            transactionTemplate.execute(
                    new TransactionCallbackWithoutResult() {
                        @Override
                        protected void doInTransactionWithoutResult(TransactionStatus status) {
                            bcdnsManager.bindDomainCertWithBlockchain(domain, product, blockchainId);
                            blockchainManager.addBlockchain(
                                    product,
                                    blockchainId,
                                    pluginServerId,
                                    alias,
                                    desc,
                                    clientConfig
                            );
                        }
                    }
            );

            return "success";
        } catch (Exception e) {
            log.error("failed to add blockchain: ", e);
            return "exception happened: " + e.getMessage();
        }
    }

    Object setupBBCContracts(String... args) {
        if (args.length != 2) {
            return "wrong number of arguments";
        }

        String product = args[0];
        String blockchainId = args[1];

        try {
            blockchainManager.deployAMClientContract(product, blockchainId);
            blockchainManager.updateBlockchainProperty(
                    product,
                    blockchainId,
                    Constants.AM_SERVICE_STATUS,
                    OnChainServiceStatusEnum.DEPLOY_FINISHED.name()
            );
            return "success";
        } catch (Exception e) {
            log.error("failed to mark BBC contract to deploy for blockchain {}-{}: ", product, blockchainId, e);
            return "exception happened: " + ObjectUtil.defaultIfNull(e.getCause(), e).getMessage();
        }
    }

    Object registerTpBta(String... args) {
        if (args.length != 2) {
            return "wrong number of arguments";
        }

        String ptcServiceId = args[0];
        String rawBtaBase64 = args[1];

        try {
            blockchainManager.initTpBta(ptcServiceId, BlockchainTrustAnchorFactory.createBTA(Base64.decode(rawBtaBase64)));
            return "success";
        } catch (Exception e) {
            log.error("failed to init tpbta: ", e);
            return "exception happened: " + e.getMessage();
        }
    }

    Object queryValidTpBtaList(String... args) {
        if (args.length != 1) {
            return "wrong number of arguments";
        }

        String senderDomain = args[0];

        try {
            return JSON.toJSONString(blockchainManager.getMatchedTpBtaDescList(new CrossChainLane(new CrossChainDomain(senderDomain))), SerializerFeature.PrettyFormat);
        } catch (Exception e) {
            log.error("failed to get matched valid tpbta: ", e);
            return "exception happened: " + e.getMessage();
        }
    }

    Object getLatestVersionOfBta(String... args) {
        if (args.length != 1) {
            return "wrong number of arguments";
        }

        String domain = args[0];

        try {
            BtaDO btaDO = blockchainManager.queryLatestVersionBta(new CrossChainDomain(domain));
            if (ObjectUtil.isNull(btaDO)) {
                return "-1";
            }
            return Integer.toString(btaDO.getSubjectVersion());
        } catch (Exception e) {
            log.error("failed to init tpbta: ", e);
            return "exception happened: " + e.getMessage();
        }
    }

    Object upgradeTpBta(String... args) {
        if (args.length != 3 && args.length != 2) {
            return "wrong number of arguments";
        }

        String ptcServiceId = args[0];
        String tpBtaCrossChainLaneKey = args[1];
        String rawBtaBase64 = null;
        if (args.length == 3) {
            rawBtaBase64 = args[2];
        }

        try {
            blockchainManager.upgradeTpBta(
                    ptcServiceId,
                    CrossChainLane.fromLaneKey(tpBtaCrossChainLaneKey),
                    StrUtil.isEmpty(rawBtaBase64) ? null : BlockchainTrustAnchorFactory.createBTA(Base64.decode(rawBtaBase64))
            );
            return "success";
        } catch (Exception e) {
            log.error("failed to add blockchain: ", e);
            return "exception happened: " + e.getMessage();
        }
    }

    Object queryExactTpBta(String... args) {
        if (args.length != 2 && args.length != 1) {
            return "wrong number of arguments";
        }

        try {
            String tpbtaLaneKey = args[0];
            int tpbtaVersion = -1;
            if (args.length == 2) {
                tpbtaVersion = Integer.parseInt(args[1]);
            }

            TpBtaDO tpBtaDO = ptcManager.getExactTpBta(CrossChainLane.fromLaneKey(tpbtaLaneKey), tpbtaVersion);
            if (ObjectUtil.isNull(tpBtaDO)) {
                return "not found";
            }

            return JSON.toJSONString(
                    new TpBtaInfo(
                            tpBtaDO.getPtcServiceId(),
                            tpBtaDO.getCrossChainLane().getLaneKey(),
                            tpBtaDO.getTpBtaVersion(),
                            Base64.encode(tpBtaDO.getTpbta().encode())
                    )
            );
        } catch (Exception e) {
            log.error("failed to query tpbta: ", e);
            return "exception happened: " + e.getMessage();
        }
    }

    Object deployBBCContractsAsync(String... args) {
        if (args.length != 2) {
            return "wrong number of arguments";
        }

        String product = args[0];
        String blockchainId = args[1];

        try {
            blockchainManager.deployBBCContractAsync(product, blockchainId);
            return "success";
        } catch (Exception e) {
            log.error("failed to mark BBC contract to deploy for blockchain {}-{}: ", product, blockchainId, e);
            return "exception happened: " + ObjectUtil.defaultIfNull(e.getCause(), e).getMessage();
        }
    }

    /**
     * 更新区块链
     *
     * @return
     */
    Object updateBlockchainAnchor(String... args) {
        String product = args[0];
        String blockchainId = args[1];
        String alias = args[2];
        String desc = args[3];
        String clientConfig = args[4];

        try {
            if (!JSONUtil.isTypeJSON(clientConfig.trim())) {
                clientConfig = new String(Files.readAllBytes(Paths.get(clientConfig)));
            }
            blockchainManager.updateBlockchain(
                    product,
                    blockchainId,
                    "",
                    alias,
                    desc,
                    JSONObject.parseObject(clientConfig)
                            .entrySet().stream().collect(
                                    Collectors.toMap(
                                            Map.Entry::getKey,
                                            entry -> entry.getValue().toString()
                                    )
                            )
            );
        } catch (Throwable e) {
            log.error("failed to update chain properties: ( product: {}, blockchainId: {} )", product, blockchainId, e);
            return "update failed: " + e.getMessage();
        }
        return "success";
    }

    /**
     * 更新区块链单个属性
     *
     * @return
     */
    Object updateBlockchainProperty(String... args) {
        String product = args[0];
        String blockchainId = args[1];
        String confKey = args[2];
        String confValue = args[3];

        try {
            blockchainManager.updateBlockchainProperty(product, blockchainId, confKey, confValue);
        } catch (Throwable e) {
            log.error("failed to update chain properties: ( key: {}, val: {} )", confKey, confValue, e);
            return "update failed: " + e.getMessage();
        }
        return "success";
    }

    /**
     * 停止区块链
     *
     * @return
     */
    Object stopBlockchainAnchor(String... args) {
        String product = args[0];
        String blockchainId = args[1];
        try {
            blockchainManager.stopBlockchainAnchor(product, blockchainId);
        } catch (Throwable e) {
            log.error("failed to stop blockchain: ( product: {}, blockchain_id: {} )", product, blockchainId, e);
            return "stop blockchain failed: " + e.getMessage();
        }
        return "success";
    }

    /**
     * 开启区块链
     *
     * @return
     */
    Object startBlockchainAnchor(String... args) {
        String product = args[0];
        String blockchainId = args[1];
        try {
            blockchainManager.startBlockchainAnchor(product, blockchainId);
        } catch (Throwable e) {
            log.error("failed to start blockchain: ( product: {}, blockchain_id: {} )", product, blockchainId, e);
            return "start blockchain failed: " + e.getMessage();
        }
        return "success";
    }

    Object setTxPendingLimit(String... args) {
        if (args.length != 3) {
            return "wrong args size. ";
        }

        if (!StrUtil.isNumeric(args[2])) {
            return "the third arg supposed to be numeric";
        }

        BlockchainMeta meta = this.blockchainManager.getBlockchainMeta(args[0], args[1]);
        if (ObjectUtils.isEmpty(meta)) {
            return String.format("none blockchain found for id %s", args[1]);
        }

        int txPendingLimit = Integer.parseInt(args[2]);
        if (txPendingLimit <= 0) {
            return "limit of tx pending should be greater than zero!";
        }

        try {
            systemConfigRepository.setSystemConfig(
                    String.format("%s-%s-%s", Constants.PENDING_LIMIT, args[0], args[1]),
                    args[2]
            );
        } catch (Throwable e) {
            log.error("failed to set tx pending limit to blockchain ( product: {}, blockchain_id: {} )", args[0], args[1], e);
            return "set tx pending limit failed: " + e.getMessage();
        }

        return "success";
    }

    Object querySDPMsgSeq(String... args) {
        if (args.length != 5) {
            return "wrong args size. ";
        }

        String receiverProduct = args[0];
        String receiverBlockchainId = args[1];
        String senderDomain = args[2];
        String from = args[3];
        String to = args[4];

        try {
            return "result is " + this.blockchainManager.querySDPMsgSeq(
                    receiverProduct,
                    receiverBlockchainId,
                    senderDomain,
                    from,
                    to
            );
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    Object readLatestConsensusInfo(String... args) {
        if (args.length != 1) {
            return "wrong args size.";
        }

        String domain = args[0];

        try {
            BlockchainMeta blockchainMeta = blockchainManager.getBlockchainMetaByDomain(domain);
            if (ObjectUtil.isNull(blockchainMeta)) {
                return "no blockchain found";
            }

            AbstractBlockchainClient client = blockchainClientPool.createClient(blockchainMeta);
            ConsensusState state = client.getConsensusState(BigInteger.valueOf(client.getLastBlockHeight()));

            ConsensusStateInfo consensusStateInfo = new ConsensusStateInfo();
            consensusStateInfo.setHash(state.getHashHex());
            consensusStateInfo.setHeight(state.getHeight().toString());

            return JSON.toJSONString(consensusStateInfo);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    Object retrySdpMessage(String... args) {
        if (args.length != 1) {
            return "wrong args size.";
        }

        try {
            long amId = Long.parseLong(args[0]);
            SDPMsgWrapper wrapper = crossChainMessageRepository.getSDPMessage(amId);
            if (ObjectUtil.isNull(wrapper)) {
                return "no message found";
            }

            blockchainIdleDCache.setLastAMProcessTime(wrapper.getReceiverBlockchainProduct(), wrapper.getReceiverBlockchainId());
            crossChainMessageRepository.updateSDPMessageState(amId, SDPMsgProcessStateEnum.PENDING);
            return "success";
        } catch (Throwable t) {
            log.error("failed to retry sdp message", t);
            return "unexpected error";
        }
    }
}
