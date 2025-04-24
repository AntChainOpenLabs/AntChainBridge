pragma solidity ^0.8.0;
// pragma contract_version 1;

// IContractUsingSDP
interface InterContractMessageInterface {
 
    /**
     * function： sendUnorderedMessage
     * usage: 用户合约call 跨链消息收发合约发送跨链消息
     * parameters：
     *         _destination_domain   ：目标区块链域名
     *         _receiver                      ：接收消息的合约账号，根据合约名称或者链码名计算sha256获得
     *         _message                    ：消息内容
     * return value                         ：无
     */
    function sendUnorderedMessage(string calldata _destination_domain, bytes32 _receiver, bytes calldata _message) external;
 
    /**
     * function: recvUnorderedMessage
     * usage: 用户合约需要实现的接口，供跨链消息收发合约合约调用，接收跨链消息
     * parameters:
     *         _from_domain     ：消息来源区块链
     *         _sender               ：消息来源的合约账号名称/链码名的sha256哈希值
     *         _message           ：消息内容
     * return value                ：无
     */
    function recvUnorderedMessage(string calldata _from_domain, bytes32 _sender, bytes calldata _message) external ;
}
