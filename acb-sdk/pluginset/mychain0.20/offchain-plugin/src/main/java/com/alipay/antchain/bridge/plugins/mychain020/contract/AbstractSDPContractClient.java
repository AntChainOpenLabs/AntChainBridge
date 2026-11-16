package com.alipay.antchain.bridge.plugins.mychain020.contract;

import com.alipay.antchain.bridge.commons.core.base.BlockState;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;

import java.math.BigInteger;

/**
 * 抽象的P2PMsgClient服务合约，定义了MYOracle的P2PMsgClient合约模型。
 * <p>
 * 不同的链有不同的实现。
 */
public interface AbstractSDPContractClient {

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    //  合约接口
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * 设置AM协议合约和本地域名，本地域名已经记录在sdp合约中了
     *
     * @param amContractName
     * @return
     */
    boolean setAmContractAndDomain(String amContractName);

    /**
     * 向链上提交异常消息
     * @param exceptionMsgAuthor
     * @param exceptionMsgPkg
     * @return
     */
    SendResponseResult recvOffChainException(String exceptionMsgAuthor, byte[] exceptionMsgPkg);

    /**
     * 查询接收链关于指定超时限制类型的已验证信息
     *
     * @param receiverDomain
     * @return
     */
    BlockState queryValidatedBlockStateByDomain(String receiverDomain);

    /**
     * 查询指定跨链元组的P2P消息序号，to合约为当前mychain上的合约
     *
     * @param senderDomain
     * @param from
     * @param receiverDomain
     * @param to
     * @return
     */
    long queryP2PMsgSeqOnChain(String senderDomain, String from, String receiverDomain, String to);


    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // 合约管理接口，部署、升级合约及合约数据解析
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * 部署合约
     *
     * @return
     */
    boolean deployContract();

}