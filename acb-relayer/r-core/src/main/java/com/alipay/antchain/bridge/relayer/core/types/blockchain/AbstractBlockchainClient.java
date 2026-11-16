package com.alipay.antchain.bridge.relayer.core.types.blockchain;

import java.math.BigInteger;

import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.core.base.ConsensusState;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;
import com.alipay.antchain.bridge.commons.core.rcc.ReliableCrossChainMessage;
import com.alipay.antchain.bridge.relayer.commons.model.BlockchainMeta;
import com.alipay.antchain.bridge.relayer.core.manager.bbc.IAMClientContract;
import com.alipay.antchain.bridge.relayer.core.manager.bbc.IMonitorClientContract;
import com.alipay.antchain.bridge.relayer.core.manager.bbc.IPtcContract;
import com.alipay.antchain.bridge.relayer.core.manager.bbc.ISDPMsgClientContract;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public abstract class AbstractBlockchainClient {

    private BlockchainMeta blockchainMeta;

    private String domain;

    public abstract boolean start();

    public abstract boolean shutdown();

    public abstract boolean ifHasDeployedAMClientContract();

    public abstract boolean ifSupportReliableCrossChain();

    public abstract long getLastBlockHeight();

    public abstract AbstractBlock getEssentialBlockByHeight(long height);

    public abstract ConsensusState getConsensusState(BigInteger height);

    public abstract IAMClientContract getAMClientContract();

    public abstract ISDPMsgClientContract getSDPMsgClientContract();

    public abstract IMonitorClientContract getMonitorClientContract();

    public abstract IPtcContract getPtcContract();

    public abstract CrossChainMessageReceipt queryCommittedTxReceipt(String txhash);

    public abstract AbstractBBCContext queryBBCContext();

    public abstract CrossChainMessageReceipt reliableRetry(ReliableCrossChainMessage msg);
}