// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

// 供dapp合约编写的接口 由监管合约向上传递消息时调用
interface IContractUsingMonitor {

    function recvUnorderedMessageFromSDP(string memory senderDomain, bytes32 author, address receiverID, bytes memory message) external;

    function recvMessageFromSDP(string memory senderDomain, bytes32 author, address receiverID, bytes memory message) external;
}