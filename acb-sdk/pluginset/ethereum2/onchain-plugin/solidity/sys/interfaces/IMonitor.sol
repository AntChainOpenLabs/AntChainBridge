// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "../lib/monitor/MonitorLib.sol";

interface IMonitor {
    // 发送方-监管通过 监管不通过
    event MonitorApproval(
        bytes32 senderID,
        string receiverDomain,
        bytes32 receiverID
    );

    event MonitorDisapproval(
        bytes32 senderID,
        string receiverDomain,
        bytes32 receiverID,
        uint8 monitorMainType,
        uint8 monitorSubType
    );

    // 收到监管指令
    event receiveMonitorOrder(
        uint32 monitorType,
        string senderDomain,
        bytes32 sender,
        string receiverDomain,
        bytes32 receiver,
        string transactionContent,
        string extra
    );

    // 收到监管回滚消息
    event receiveMonitorRollbackMessage(
        uint32 monitorType,
        string senderDomain,
        bytes32 sender,
        bytes32 receiver,
        string monitorMsg
    );

    // 监管不通过 构造监管回滚消息
    event sendMonitorRollbakMessage(
        uint32 monitorType,
        bytes32 sender,
        string receiverDomain,
        bytes32 receiver,
        string monitorMsg
    );

    // 验证监管节点签名
    event VerifyMonitorNodeProofMessage(
        string senderDomain,
        bytes32 author,
        address receiverID,
        bool result
    );

    event VerifyMonitorOrder(
        string committeeId,
        bool result
    );

    // send接口:供dapp合约调用
    function sendMonitorMessage(string calldata receiverDomain, bytes32 receiverID, bytes calldata message) external;

    function sendUnorderedMonitorMessage(string calldata receiverDomain, bytes32 receiverID, bytes calldata message) external;

    function recvMonitorOrder(string calldata committeeId, string calldata signAlgo, bytes memory proof, bytes memory rawMonitorOrder) external;

    // 其他
    function setProtocol(address protocolAddress) external;

    function setMonitorVerifier(address newMonitorVerifierAddress) external;

    function setMonitorControl(uint32 monitorType) external;
}