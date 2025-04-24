// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "./interfaces/IPtcVerifier.sol";
import "./lib/ptc/CommitteeLib.sol";

contract CommitteePtcVerifier is IPtcVerifier {

    function verifyTpBta(PTCVerifyAnchor calldata va, TpBta calldata tpBta) external override returns (bool) {
        return CommitteeLib.verifyTpBta(va, tpBta);
    }

    function verifyTpProof(TpBta memory tpBta, ThirdPartyProof memory tpProof) external override returns (bool) {
        return CommitteeLib.verifyTpProof(tpBta, tpProof);
    }

    function myPtcType() external pure override returns (PTCTypeEnum) {
        return PTCTypeEnum.COMMITTEE;
    }
}