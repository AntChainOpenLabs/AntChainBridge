package com.alipay.antchain.bridge.ptc.committee.types.network.nodeclient;

import java.math.BigInteger;

import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.bta.IBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ValidatedConsensusState;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.EndorseBlockStateResp;

public interface INodeClient {

    /**
     * 节点间心跳连接
     * @return
     */
    HeartbeatResp heartbeat();

    /**
     * 根据跨链通道查询链的ptc背书
     * - receiverDomain为空，tpbta用于该链发出的所有消息的背书——目前仅考虑这一种情况
     * - receiverDomain非空，sender & receiver非空，tpbta用于sender到receiver的背书
     * - receiverDomain & sender 非空，receiver空，tpbta用于sender到receiverDomain的背书
     * @param lane
     * @return
     */
    ThirdPartyBlockchainTrustAnchor queryTpBta(CrossChainLane lane);

    /**
     * 验证指定域名链的bta
     * @param domainCert
     * @param bta
     * @return
     */
    ThirdPartyBlockchainTrustAnchor verifyBta(AbstractCrossChainCertificate domainCert, IBlockchainTrustAnchor bta);

    /**
     * 验证初始共识状态
     * @param crossChainLane
     * @param consensusState
     * @return
     */
    ValidatedConsensusState commitAnchorState(CrossChainLane crossChainLane, ConsensusState consensusState);

    /**
     * 验证非初始的共识状态
     * @param crossChainLane
     * @param consensusState
     * @return
     */
    ValidatedConsensusState commitConsensusState(CrossChainLane crossChainLane, ConsensusState consensusState);

    /**
     * 验证跨链消息
     * @param crossChainLane
     * @param packet
     * @return
     */
    CommitteeNodeProof verifyCrossChainMessage(CrossChainLane crossChainLane, UniformCrosschainPacket packet);

    default NodeVerifyCrossChainMessageResult verifyCrossChainMessageWithResult(
            CrossChainLane crossChainLane,
            UniformCrosschainPacket packet
    ) {
        return new NodeVerifyCrossChainMessageResult(
                verifyCrossChainMessage(crossChainLane, packet),
                "",
                ""
        );
    }

    default NodeVerifyCrossChainMessageResult verifyCrossChainMessageWithResult(
            CrossChainLane crossChainLane,
            UniformCrosschainPacket packet,
            String ucpId
    ) {
        return verifyCrossChainMessageWithResult(crossChainLane, packet);
    }

    BlockState queryBlockState(CrossChainDomain blockchainDomain);

    EndorseBlockStateResp endorseBlockState(CrossChainLane tpbtaLane, CrossChainDomain receiverDomain, BigInteger height);
}
