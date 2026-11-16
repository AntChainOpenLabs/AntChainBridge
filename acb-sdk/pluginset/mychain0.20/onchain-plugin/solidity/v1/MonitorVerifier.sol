// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "./interfaces/IMonitorVerifier.sol";
import "./lib/utils/Ownable.sol";
import "./lib/ptc/CommitteeLib.sol";
import "./lib/commons/AcbCommons.sol";
import "./@openzeppelin/contracts/utils/Strings.sol";

interface IMonitorProofPtcHub {
    function verifierMap(uint8 ptcType) external view returns (identity);
}

contract MonitorVerifier is IMonitorVerifier, Ownable {
    using Strings for string;
    using CommitteeLib for NodePublicKeyEntry;

    mapping(bytes32 => NodeEndorseInfo) private monitorNodeEndorseInfoMap;
    identity public ptcHubAddress;

    // Appended after the legacy storage fields for update-contract compatibility.
    MonitorNodeProofMessage private monitorNodeProofMessage;

    identity public monitorAddress;
    uint8 private currentProofState;
    bytes32 private currentMessageContext;

    uint32 public constant IMPLEMENTATION_VERSION = 3;

    event MonitorNodeEndorseInfoUpdated(string committeeId, string nodeId, bytes rawPublicKey);

    modifier onlyPtcHub() {
        require(msg.sender == ptcHubAddress, "MonitorVerifier: caller is not PtcHub");
        _;
    }

    function getImplementationVersion() external pure returns (uint32) {
        return IMPLEMENTATION_VERSION;
    }

    function setPtcHubAddress(identity newPtcHubAddress) external onlyOwner {
        require(newPtcHubAddress != identity(0), "MonitorVerifier: invalid PtcHub address");
        ptcHubAddress = newPtcHubAddress;
    }

    function getPtcHubAddress() external view returns (identity) {
        return ptcHubAddress;
    }

    function updateMonitorNodeEndorseInfo(bytes calldata rawEndorseRoot) external override onlyPtcHub {
        CommitteeEndorseRoot memory endorseRoot = CommitteeLib.decodeCommitteeEndorseRootFrom(rawEndorseRoot);
        require(bytes(endorseRoot.committeeId).length > 0, "MonitorVerifier: empty committee id");

        for (uint256 index = 0; index < endorseRoot.endorsers.length; index++) {
            NodeEndorseInfo memory info = endorseRoot.endorsers[index];
            if (_isMonitorNode(info.nodeId)) {
                monitorNodeEndorseInfoMap[keccak256(abi.encodePacked(endorseRoot.committeeId))] = info;
                emit MonitorNodeEndorseInfoUpdated(
                    endorseRoot.committeeId,
                    info.nodeId,
                    info.publicKey.rawPublicKey
                );
                return;
            }
        }
        revert("MonitorVerifier: monitor node not found");
    }

    function verifyMonitorOrder(
        string calldata committeeId,
        string calldata signAlgo,
        bytes calldata rawProof,
        bytes calldata rawMonitorOrder
    ) external override returns (bool) {
        NodeEndorseInfo storage monitorNode =
            monitorNodeEndorseInfoMap[keccak256(abi.encodePacked(committeeId))];
        require(bytes(monitorNode.nodeId).length > 0, "MonitorVerifier: monitor node not found");
        return CommitteeLib.verifyMonitorOrder(
            committeeId,
            monitorNode,
            signAlgo,
            rawProof,
            rawMonitorOrder
        );
    }

    // Verify immediately; never persist the complete signed payload.
    function receiveMonitorNodeProofMessage(
        CrossChainLane calldata newCrossChainLane,
        string calldata newCommitteeId,
        MonitorCommitteeNodeProof calldata newMonitorNodeProof,
        bytes calldata newEncodedToSign,
        bytes32 messageContext
    ) external override {
        require(ptcHubAddress != identity(0), "MonitorVerifier: PtcHub not set");
        require(msg.sender == IMonitorProofPtcHub(ptcHubAddress).verifierMap(1),
            "MonitorVerifier: caller is not committee verifier");
        require(messageContext != bytes32(0), "MonitorVerifier: empty context");
        NodeEndorseInfo memory monitorNode = monitorNodeEndorseInfoMap[keccak256(abi.encodePacked(newCommitteeId))];
        require(bytes(monitorNode.nodeId).length > 0, "MonitorVerifier: monitor node not found");
        CommitteeNodeProof memory proof = CommitteeNodeProof({nodeId: newMonitorNodeProof.nodeId,
            signAlgo: newMonitorNodeProof.signAlgo, signature: newMonitorNodeProof.signature});
        bool verified = CommitteeLib.verifyTpProofFromMonitorNode(
            newCrossChainLane, newCommitteeId, monitorNode, proof, newEncodedToSign);
        currentMessageContext = messageContext;
        currentProofState = verified ? 1 : 2;
    }

    function verifyMonitorNodeProofMessage(bytes32 messageContext) external override returns (bool) {
        require(msg.sender == monitorAddress, "MonitorVerifier: caller is not Monitor");
        require(currentProofState != 0 && currentMessageContext == messageContext,
            "MonitorVerifier: no proof for current message");
        bool verified = currentProofState == 1;
        delete currentProofState;
        delete currentMessageContext;
        return verified;
    }

    function setMonitorAddress(identity monitor) external onlyOwner {
        require(monitor != identity(0), "MonitorVerifier: zero Monitor");
        monitorAddress = monitor;
    }

    function getPendingProofContext() external view returns (bytes32, uint8) {
        return (currentMessageContext, currentProofState);
    }

    function _isMonitorNode(string memory nodeId) internal pure returns (bool) {
        bytes memory prefix = bytes("monitor");
        bytes memory nodeIdBytes = bytes(nodeId);
        if (nodeIdBytes.length < prefix.length) {
            return false;
        }
        for (uint256 index = 0; index < prefix.length; index++) {
            if (nodeIdBytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }
}
