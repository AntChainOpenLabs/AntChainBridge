// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "./interfaces/IPtcVerifier.sol";
import "./lib/ptc/CommitteeLib.sol";

contract CommitteePtcVerifier is IPtcVerifier {

    uint32 public constant IMPLEMENTATION_VERSION = 2;

    function getImplementationVersion() external pure returns (uint32) {
        return IMPLEMENTATION_VERSION;
    }

    function verifyTpBta(PTCVerifyAnchor calldata va, TpBta calldata tpBta) external override returns (bool) {
        return CommitteeLib.verifyTpBta(va, tpBta);
    }

    function verifyTpProof(TpBta memory tpBta, ThirdPartyProof memory tpProof) external override returns (bool) {
        return CommitteeLib.verifyTpProof(tpBta, tpProof);
    }

    function verifyTpProof(
        TpBta memory tpBta,
        ThirdPartyProof memory tpProof,
        identity monitorVerifierAddress
    ) external override returns (bool) {
        return CommitteeLib.verifyTpProof(tpBta, tpProof, monitorVerifierAddress);
    }

    function myPtcType() external pure override returns (PTCTypeEnum) {
        return PTCTypeEnum.COMMITTEE;
    }
}
