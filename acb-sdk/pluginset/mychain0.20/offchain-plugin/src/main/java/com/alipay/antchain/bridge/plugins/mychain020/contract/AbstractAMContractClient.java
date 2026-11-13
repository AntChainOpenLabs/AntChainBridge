package com.alipay.antchain.bridge.plugins.mychain020.contract;

import java.util.List;

import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.mychain.sdk.domain.block.Block;
import com.alipay.mychain.sdk.message.transaction.TransactionReceiptResponse;

/**
 * 抽象的AM服务合约，定义了Mychain的AM合约模型。
 * <p>
 * 不同合约语言有不同实现
 */
public interface AbstractAMContractClient {

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    //  合约接口，支持合约本身接口的调用
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * 给AM合约添加Relayer
     *
     * @param relayer
     * @return
     */
    boolean addRelayers(String relayer);

    /**
     * 添加AM上层协议
     *
     * @param protocolContract 协议合约地址
     * @param protocolType     协议类型
     * @return
     */
    boolean setProtocol(String protocolContract, String protocolType);

    SendResponseResult recvPkgFromRelayer(byte[] pkg);

    void setPtcHub(String ptcHubContractName);

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // 合约管理接口，部署、升级合约及合约数据解析
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * 部署合约
     *
     * @return
     */
    boolean deployContract();

    /**
     * 解析区块事件构造 AM 合约相关的跨链消息
     *
     * @param block
     * @return
     */
    public List<CrossChainMessage> parseCrossChainMessage(Block block);

}
