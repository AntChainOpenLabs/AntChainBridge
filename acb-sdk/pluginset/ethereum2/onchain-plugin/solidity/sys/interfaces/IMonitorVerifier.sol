// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "../lib/ptc/CommitteeLib.sol";
import "../lib/commons/AcbCommons.sol";

interface IMonitorVerifier {
    
    struct MonitorNodeProofMessage {
        CrossChainLane crossChainLane;
        string committeeId;
        CommitteeNodeProof monitorNodeProof;
        bytes encodedToSign;
    }

    event UpdateMonitorNodeEndorseInfo(string committeeId, string nodeId, bytes rawPublicKey);

    function setPtcHubAddress(address newPtcHubAddress) external;

    function updateMonitorNodeEndorseInfo(bytes memory rawEndorseRoot) external;

    function receiveMonitorNodeProofMessage(
        CrossChainLane calldata newCrossChainLane,
        string calldata newCommitteeId,
        CommitteeNodeProof calldata newMonitorNodeProof,
        bytes calldata encodedToSign) external;

    function verifyMonitorNodeProofMessage() external returns (bool);

    function verifyMonitorOrder(string calldata committeeId, string calldata signAlgo, bytes calldata rawProof, bytes calldata rawMonitorOrder) external returns (bool);
}