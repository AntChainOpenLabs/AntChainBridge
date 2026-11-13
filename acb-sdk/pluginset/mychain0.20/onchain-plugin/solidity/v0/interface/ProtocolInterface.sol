pragma solidity ^0.4.22;

interface ProtocolInterface {

    // 客户合约call P2P合约发送消息
    function sendMessage(string _destination_domain, identity _receiver, bytes _message) external;

    function sendUnorderedMessage(string _destination_domain, identity _receiver, bytes _message) external;

    // 客户合约需要实现接收消息接口，P2P合约回调
    function recvMessage(string domain_name, identity author, bytes message) external;
}
