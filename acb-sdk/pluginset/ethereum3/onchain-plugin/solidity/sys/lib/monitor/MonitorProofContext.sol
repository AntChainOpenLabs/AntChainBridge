// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "../am/AMLib.sol";
import "../sdp/SDPLib.sol";

/** Canonical binding for the authenticated message handed from PTC to Monitor. */
library MonitorProofContext {
    function fromResponse(string memory senderDomain, bytes memory response) internal pure returns (bytes32) {
        AuthMessage memory am = AMLib.decodeAuthMessage(response);
        if (am.protocolType != 0) {
            return keccak256(abi.encode(senderDomain, am.author, am.protocolType, keccak256(am.body)));
        }
        uint32 version = SDPLib.getSDPVersionFrom(am.body);
        if (version == 1) {
            SDPMessage memory message;
            SDPLib.decode(message, am.body);
            return key(senderDomain, am.author, message.receiver, message.message);
        }
        if (version == 2) {
            SDPMessageV2 memory message;
            SDPLib.decode(message, am.body);
            return key(senderDomain, am.author, message.receiver, message.message);
        }
        if (version == 3) {
            SDPMessageV3 memory message;
            SDPLib.decode(message, am.body);
            return key(senderDomain, am.author, message.receiver, message.message);
        }
        revert("MonitorProof: unsupported SDP");
    }

    function key(string memory senderDomain, bytes32 author, bytes32 receiver, bytes memory envelope)
        internal pure returns (bytes32)
    {
        return keccak256(abi.encode(senderDomain, author, receiver, keccak256(envelope)));
    }
}
