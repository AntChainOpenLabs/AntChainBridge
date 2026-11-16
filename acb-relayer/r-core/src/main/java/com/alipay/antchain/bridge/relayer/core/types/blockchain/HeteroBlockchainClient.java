package com.alipay.antchain.bridge.relayer.core.types.blockchain;

import java.math.BigInteger;
import java.util.List;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.rcc.ReliableCrossChainMessage;
import com.alipay.antchain.bridge.relayer.commons.model.BlockchainMeta;
import com.alipay.antchain.bridge.relayer.core.manager.bbc.*;
import com.alipay.antchain.bridge.relayer.core.types.pluginserver.IBBCServiceClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HeteroBlockchainClient extends AbstractBlockchainClient {

    @Getter
    private final IBBCServiceClient bbcClient;

    private final IAMClientContract amClientContract;

    private final ISDPMsgClientContract sdpMsgClient;

    private final IMonitorClientContract monitorClient;

    private final IPtcContract ptcContract;

    public HeteroBlockchainClient(IBBCServiceClient bbcClient, BlockchainMeta blockchainMeta) {
        super(blockchainMeta, bbcClient.getDomain());
        this.bbcClient = bbcClient;
        this.amClientContract = new AMClientContractHeteroBlockchainImpl(bbcClient);
        this.sdpMsgClient = new SDPMsgClientHeteroBlockchainImpl(bbcClient);
        this.monitorClient = new MonitorClientContractHeteroBlockchainImpl(bbcClient);
        this.ptcContract = new PtcContractHeteroBlockchainImpl(bbcClient);
    }

    @Override
    public boolean start() {

        DefaultBBCContext bbcContext = getBlockchainMeta().getProperties().getBbcContext();
        if (ObjectUtil.isNull(bbcContext)) {
            log.error(
                    "bbcContext is null for ( plugin_server: {}, product: {}, domain: {} )",
                    getBlockchainMeta().getProperties().getPluginServerId(),
                    bbcClient.getProduct(),
                    bbcClient.getDomain()
            );
            return false;
        }
        try {
            this.bbcClient.startup(bbcContext);
        } catch (Exception e) {
            log.error(
                    "failed to start heterogeneous blockchain client ( plugin_server: {}, product: {}, domain: {} )",
                    getBlockchainMeta().getProperties().getPluginServerId(),
                    bbcClient.getProduct(),
                    bbcClient.getDomain(),
                    e
            );
            return false;
        }

        return true;
    }

    @Override
    public boolean shutdown() {
        try {
            this.bbcClient.shutdown();
        } catch (Exception e) {
            log.error(
                    "failed to shutdown heterogeneous blockchain client ( plugin_server: {}, product: {}, domain: {} )",
                    getBlockchainMeta().getProperties().getPluginServerId(),
                    this.bbcClient.getProduct(),
                    this.bbcClient.getDomain(),
                    e
            );
            return false;
        }

        return true;
    }

    @Override
    public boolean ifHasDeployedAMClientContract() {
        if (checkIfHasAMDeployedLocally()) {
            return true;
        }
        return checkIfHasAMDeployedRemotely();
    }

    @Override
    public boolean ifSupportReliableCrossChain() {
        return getBlockchainMeta().getProperties().getBbcContext().isReliable();
    }

    private boolean checkContractsStatus(AbstractBBCContext bbcContext) {
        if (ObjectUtil.isNull(bbcContext.getAuthMessageContract()) || ObjectUtil.isNull(bbcContext.getSdpContract())) {
            log.info(
                    "local bbc context for ( product: {}, domain: {} ) is incomplete",
                    getBlockchainMeta().getProduct(), getDomain()
            );
            return false;
        }

        boolean ifAMPrepared = ContractStatusEnum.CONTRACT_READY == bbcContext.getAuthMessageContract().getStatus();
        if (!ifAMPrepared) {
            log.info(
                    "AM contract of heterogeneous blockchain client ( product: {} , domain: {} ) is {} instead of ready",
                    getBlockchainMeta().getProduct(), getDomain(), bbcContext.getAuthMessageContract().getStatus()
            );
            return false;
        }
        boolean ifSDPPrepared = ContractStatusEnum.CONTRACT_READY == bbcContext.getSdpContract().getStatus();
        if (!ifSDPPrepared) {
            log.info(
                    "SDP contract of heterogeneous blockchain client ( product: {} , domain: {} ) is not ready",
                    getBlockchainMeta().getProduct(), getDomain()
            );
            return false;
        }
        return true;
    }

    private boolean checkIfHasAMDeployedLocally() {
        return checkContractsStatus(
                getBlockchainMeta().getProperties().getBbcContext()
        );
    }

    private boolean checkIfHasAMDeployedRemotely() {
        AbstractBBCContext bbcContext = this.bbcClient.getContext();
        if (ObjectUtil.isNull(bbcContext)) {
            log.error("get null bbc context for {}-{}", getBlockchainMeta().getProduct(), getDomain());
            return false;
        }
        getBlockchainMeta().getProperties().setBbcContext((DefaultBBCContext) bbcContext);
        return checkContractsStatus(bbcContext);
    }

    @Override
    public long getLastBlockHeight() {
        return this.bbcClient.queryLatestHeight();
    }

    @Override
    public AbstractBlock getEssentialBlockByHeight(long height) {
        List<CrossChainMessage> messages = this.bbcClient.readCrossChainMessagesByHeight(height);

        ConsensusState currConsensusState = null;
        if (StrUtil.isNotEmpty(getBlockchainMeta().getProperties().getPtcContractAddress())) {
            currConsensusState = this.bbcClient.readConsensusState(BigInteger.valueOf(height));
            currConsensusState.setDomain(new CrossChainDomain(getDomain()));
        }

        return new HeterogeneousBlock(
                getBlockchainMeta().getProduct(),
                getDomain(),
                getBlockchainMeta().getBlockchainId(),
                height,
                messages,
                currConsensusState
        );
    }

    @Override
    public ConsensusState getConsensusState(BigInteger height) {
        ConsensusState consensusState = this.bbcClient.readConsensusState(height);
        consensusState.setDomain(new CrossChainDomain(this.getDomain()));
        return consensusState;
    }

    @Override
    public IAMClientContract getAMClientContract() {
        return this.amClientContract;
    }

    @Override
    public ISDPMsgClientContract getSDPMsgClientContract() {
        return this.sdpMsgClient;
    }

    @Override
    public IMonitorClientContract getMonitorClientContract() {
        return this.monitorClient;
    }

    @Override
    public IPtcContract getPtcContract() {
        return this.ptcContract;
    }

    @Override
    public CrossChainMessageReceipt queryCommittedTxReceipt(String txhash) {
        return this.bbcClient.readCrossChainMessageReceipt(txhash);
    }

    @Override
    public AbstractBBCContext queryBBCContext() {
        return this.bbcClient.getContext();
    }

    @Override
    public CrossChainMessageReceipt reliableRetry(ReliableCrossChainMessage msg) {
        if (!ifSupportReliableCrossChain()) {
            // 不支持可靠上链特性直接抛出异常
            throw new RuntimeException("not support reliable cross-chain");
        }

        return this.bbcClient.reliableRetry(msg);
    }
}
