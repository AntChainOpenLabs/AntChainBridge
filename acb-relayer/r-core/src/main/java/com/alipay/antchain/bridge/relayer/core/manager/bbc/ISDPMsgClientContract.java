package com.alipay.antchain.bridge.relayer.core.manager.bbc;

import com.alipay.antchain.bridge.commons.core.base.BlockState;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;

/**
 * 抽象的SDPMsgClient服务合约。
 * <p>
 * 不同的链有不同的实现。
 */
public interface ISDPMsgClientContract {

    /**
     * 设置AM协议合约
     *
     * @param amContract am协议合约地址
     * @return
     */
    void setAmContract(String amContract);

    void setMonitorContract(String monitorContract);

    /**
     * Query the sequence number of the cross-chain direction
     *
     * @param senderDomain
     * @param from
     * @param receiverDomain
     * @param to
     * @return long
     */
    long querySDPMsgSeqOnChain(String senderDomain, String from, String receiverDomain, String to);

    /**
     * 部署合约
     */
    void deployContract();

    /**
     * 向SDP合约提交链下异常消息
     * : 一般跨链消息由AM提交到SDP，但链下异常消息由中继直接发起没有实际的发送链，故绕过AM合约直接提交到SDP合约
     * @param exceptionMsgAuthor 异常发送方合约
     * @param exceptionPkg          异常消息内容
     * @return
     */
    CrossChainMessageReceipt recvOffChainException(String exceptionMsgAuthor, byte[] exceptionPkg);

    BlockState queryValidatedBlockStateByDomain(String domain);
}