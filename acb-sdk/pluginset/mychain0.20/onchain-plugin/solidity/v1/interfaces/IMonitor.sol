// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

interface IMonitor {
    function sendMonitorMessageV2(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message
    ) external returns (bytes32);

    function sendUnorderedMonitorMessageV2(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message
    ) external returns (bytes32);

    function sendMonitorMessageV3(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message,
        uint8 timeoutMeasure,
        uint256 timeout
    ) external returns (bytes32);

    function sendUnorderedMonitorMessageV3(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message,
        uint8 timeoutMeasure,
        uint256 timeout
    ) external returns (bytes32);
}
