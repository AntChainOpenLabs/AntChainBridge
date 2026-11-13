// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

interface IMonitorMessage {
    function sendUnorderedMonitorMessage(
        string calldata receiverDomain,
        identity receiverID,
        bytes calldata message
    ) external;
}
