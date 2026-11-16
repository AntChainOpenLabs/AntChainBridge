// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

interface IContractUsingMonitor {

    function recvUnorderedMessageFromSDP(string memory senderDomain, bytes32 author, address receiverID, bytes memory message) external;

    function recvMessageFromSDP(string memory senderDomain, bytes32 author, address receiverID, bytes memory message) external;

    function recvUnorderedMessageV2FromSDP(string memory senderDomain, bytes32 author, address receiverID, bytes memory message) external;

    function recvMessageV2FromSDP(string memory senderDomain, bytes32 author, address receiverID, bytes memory message) external;

    function recvUnorderedMessageV3FromSDP(string memory senderDomain, bytes32 author, address receiverID, bytes memory message) external;

    function recvMessageV3FromSDP(string memory senderDomain, bytes32 author, address receiverID, bytes memory message) external;

    function ackOnSuccessFromSDP(
        address receiverID,
        bytes32 messageId,
        string memory receiverDomain,
        bytes32 receiver,
        uint32 sequence,
        uint64 nonce,
        bytes memory message
    ) external;

    function ackOnErrorFromSDP(
        address receiverID,
        bytes32 messageId,
        string memory receiverDomain,
        bytes32 receiver,
        uint32 sequence,
        uint64 nonce,
        bytes memory message,
        string memory errorMsg
    ) external;
}
