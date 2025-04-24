// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "../lib/ptc/PtcLib.sol";

interface IPtcHub {
    
    event NewPtrTrustRoot(bytes32 ptcOwnerOid);

    event NewVerifyAnchor(bytes32 ptcOwnerOid, uint256 newVersionNum);

    event NewBcdnsCert(bytes32 ownerOid);

    event SaveTpBta(bytes32 laneHash, uint256 version);

    event LatestTpBta(bytes32 laneHash, uint256 version);

    event AddPtcVerifier(PTCTypeEnum ptcType, address verifierAddress);

    event RemovePtcVerifier(PTCTypeEnum ptcType);

    event VerifyProof(CrossChainLane tpbtaLane, bool result);

    function updatePTCTrustRoot(bytes calldata rawPtcTrustRoot) external;

    function getPTCTrustRoot(bytes calldata ptcOwnerOid) external view returns (bytes memory);

    function hasPTCTrustRoot(bytes calldata ptcOwnerOid) external view returns (bool);

    function getPTCVerifyAnchor(bytes calldata ptcOwnerOid, uint256 versionNum) external view returns (bytes memory);

    function hasPTCVerifyAnchor(bytes calldata ptcOwnerOid, uint256 versionNum) external view returns (bool);

    function addTpBta(bytes calldata rawTpBta) external;

    function getTpBta(bytes calldata tpbtaLane, uint32 tpBtaVersion) external view returns (bytes memory);

    function getLatestTpBta(bytes calldata tpbtaLane) external view returns (bytes memory);

    function hasTpBta(bytes calldata tpbtaLane, uint32 tpBtaVersion) external view returns (bool);

    function verifyProof(bytes calldata rawTpProof) external;

    function getSupportedPTCType() external view returns (PTCTypeEnum[] memory);

    function addPtcVerifier(address verifierContract) external;

    function removePtcVerifier(PTCTypeEnum ptcType) external;
}