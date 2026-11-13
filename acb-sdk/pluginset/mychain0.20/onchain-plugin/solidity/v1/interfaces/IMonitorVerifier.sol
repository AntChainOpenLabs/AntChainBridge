// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "../lib/ptc/PtcLib.sol";

struct MonitorCommitteeNodeProof {
    string nodeId;
    string signAlgo;
    bytes signature;
}

struct MonitorNodeProofMessage {
    CrossChainLane crossChainLane;
    string committeeId;
    MonitorCommitteeNodeProof monitorNodeProof;
    bytes encodedToSign;
}

interface IMonitorVerifier {

    function updateMonitorNodeEndorseInfo(bytes calldata rawEndorseRoot) external;

    function receiveMonitorNodeProofMessage(
        CrossChainLane calldata newCrossChainLane,
        string calldata newCommitteeId,
        MonitorCommitteeNodeProof calldata newMonitorNodeProof,
        bytes calldata newEncodedToSign,
        bytes32 messageContext
    ) external;

    function verifyMonitorNodeProofMessage(bytes32 messageContext) external returns (bool);

    function verifyMonitorOrder(
        string calldata committeeId,
        string calldata signAlgo,
        bytes calldata rawProof,
        bytes calldata rawMonitorOrder
    ) external returns (bool);
}
