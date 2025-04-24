// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "../lib/ptc/PtcLib.sol";

interface IPtcVerifier {

    function verifyTpBta(PTCVerifyAnchor calldata va, TpBta calldata tpBta) external returns (bool);

    function verifyTpProof(TpBta memory tpBta, ThirdPartyProof memory tpProof) external returns (bool);

    function myPtcType() external returns (PTCTypeEnum);
}