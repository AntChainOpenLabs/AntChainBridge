// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "./interfaces/IMonitorVerifier.sol";
import "./lib/ptc/CommitteeLib.sol";
import "./lib/monitor/MonitorLib.sol";
import "./@openzeppelin/contracts/access/Ownable.sol";
import "./@openzeppelin/contracts/proxy/utils/Initializable.sol";

interface IMonitorProofPtcHub {
    function verifierMap(uint8 ptcType) external view returns (address);
}

contract MonitorVerifier is Ownable, Initializable, IMonitorVerifier {

    using CommitteeLib for NodeEndorseInfo; 
    using CommitteeLib for CommitteeNodeProof;
    using CommitteeLib for CrossChainLane;

    MonitorNodeProofMessage public monitorNodeProofMessage;

    address public ptcHubAddress;

    // key: committeeId, value: NodeEndorseInfo
    mapping(string => NodeEndorseInfo) public monitorNodeEndorseInfoMap;

    address public monitorAddress;
    uint8 private currentProofState; // 0 = absent/consumed, 1 = passed, 2 = failed
    bytes32 private currentMessageContext;

    modifier onlyPtcHub() {
        require(_msgSender() == ptcHubAddress, "MonitorVerifierMsg: caller is not the ptcHub");
        _;
    }

    constructor() {
        _disableInitializers();
    }

    function init() external initializer {
        _transferOwnership(_msgSender());
    }

    function setPtcHubAddress(address newPtcHubAddress) external override onlyOwner {
        ptcHubAddress = newPtcHubAddress;
    }

    function getPtcHubAddress() external view returns (address) {
        return ptcHubAddress;
    }

    function updateMonitorNodeEndorseInfo(bytes memory rawEndorseRoot) external override onlyPtcHub {
        CommitteeEndorseRoot memory cer = CommitteeLib.decodeCommitteeEndorseRootFrom(rawEndorseRoot);
        for (uint i = 0; i < cer.endorsers.length; i++) {
            NodeEndorseInfo memory info = cer.endorsers[i];
            if (CommitteeLib.checkMonitorNode(info.nodeId)) {
                monitorNodeEndorseInfoMap[cer.committeeId] = info;
                emit UpdateMonitorNodeEndorseInfo(cer.committeeId, info.nodeId, info.publicKey.rawPublicKey);
                break;
            }
        }
    }

    // The complete proof is verified in this call and is never persisted.
    // Only a message-bound, single-consumption decision crosses the AM/SDP call frames.
    function receiveMonitorNodeProofMessage(
        CrossChainLane calldata newCrossChainLane,
        string calldata newCommitteeId,
        CommitteeNodeProof calldata newMonitorNodeProof,
        bytes calldata newEncodedToSign,
        bytes32 messageContext
    ) external override {
        require(ptcHubAddress != address(0), "MonitorVerifier: PtcHub not set");
        require(msg.sender == IMonitorProofPtcHub(ptcHubAddress).verifierMap(1),
            "MonitorVerifier: caller is not committee verifier");
        require(messageContext != bytes32(0), "MonitorVerifier: empty context");
        NodeEndorseInfo memory monitorNode = monitorNodeEndorseInfoMap[newCommitteeId];
        require(bytes(monitorNode.nodeId).length > 0, "MonitorVerifier: monitor node not found");

        bool verified = CommitteeLib.verifyTpProofFromMonitorNode(
            newCrossChainLane, newCommitteeId, monitorNode, newMonitorNodeProof, newEncodedToSign);
        currentMessageContext = messageContext;
        currentProofState = verified ? 1 : 2;
    }

    function verifyMonitorNodeProofMessage(bytes32 messageContext) external override returns (bool) {
        require(msg.sender == monitorAddress, "MonitorVerifier: caller is not Monitor");
        require(currentProofState != 0 && currentMessageContext == messageContext,
            "MonitorVerifier: no proof for current message");
        bool verified = currentProofState == 1;
        // Consume before the application can make any external calls or reenter.
        delete currentProofState;
        delete currentMessageContext;
        return verified;
    }

    function setMonitorAddress(address monitor) external onlyOwner {
        require(monitor != address(0), "MonitorVerifier: zero Monitor");
        monitorAddress = monitor;
    }

    function getPendingProofContext() external view returns (bytes32, uint8) {
        return (currentMessageContext, currentProofState);
    }

    function verifyMonitorOrder(string calldata committeeId, string calldata signAlgo, bytes calldata rawProof, bytes calldata rawMonitorOrder) external override returns (bool) {
        require(_hasMonitorNodeEndorseInfo(committeeId), "MonitorVerifierMsg: no monitor node endorse info");
        return CommitteeLib.verifyMonitorOrder(
            committeeId,
            monitorNodeEndorseInfoMap[committeeId],
            signAlgo,
            rawProof,
            rawMonitorOrder);
    }

    function _hasMonitorNodeEndorseInfo(string memory committeeId) internal view returns (bool) {
        return bytes(monitorNodeEndorseInfoMap[committeeId].nodeId).length > 0;
    }

}
