// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "./interfaces/IMonitorVerifier.sol";
import "./lib/ptc/CommitteeLib.sol";
import "./lib/monitor/MonitorLib.sol";
import "./@openzeppelin/contracts/access/Ownable.sol";
import "./@openzeppelin/contracts/proxy/utils/Initializable.sol";

contract MonitorVerifier is Ownable, Initializable, IMonitorVerifier {

    using CommitteeLib for NodeEndorseInfo; 
    using CommitteeLib for CommitteeNodeProof;
    using CommitteeLib for CrossChainLane;

    MonitorNodeProofMessage public monitorNodeProofMessage;

    address public ptcHubAddress;

    // key: committeeId, value: NodeEndorseInfo
    mapping(string => NodeEndorseInfo) public monitorNodeEndorseInfoMap;

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

    // AM-SDP-Monitor合约这样一条跨合约调用链是一个原子性操作, 属于同一笔交易的执行上下文，在交易结束之前不会切换到下一笔交易
    // 所以此处不需要用modifier修饰符去保证来源的可靠性
    function receiveMonitorNodeProofMessage(
            CrossChainLane calldata newCrossChainLane,
            string calldata newCommitteeId,
            CommitteeNodeProof calldata newMonitorNodeProof,
            bytes calldata newEncodedToSign) external override {
        monitorNodeProofMessage.crossChainLane = newCrossChainLane;
        monitorNodeProofMessage.committeeId = newCommitteeId;        
        monitorNodeProofMessage.monitorNodeProof = newMonitorNodeProof;
        monitorNodeProofMessage.encodedToSign = newEncodedToSign;
    }

    function verifyMonitorNodeProofMessage() external override returns (bool) {
        require(_hasMonitorNodeEndorseInfo(monitorNodeProofMessage.committeeId), "MonitorVerifierMsg: no monitor node endorse info");
        return CommitteeLib.verifyTpProofFromMonitorNode(
            monitorNodeProofMessage.crossChainLane,
            monitorNodeProofMessage.committeeId,
            monitorNodeEndorseInfoMap[monitorNodeProofMessage.committeeId],
            monitorNodeProofMessage.monitorNodeProof,
            monitorNodeProofMessage.encodedToSign);
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
