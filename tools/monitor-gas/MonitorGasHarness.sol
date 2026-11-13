// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "MonitorVerifier.sol";
import "lib/monitor/MonitorLib.sol";
import "lib/monitor/MonitorProofContext.sol";
import {Monitor} from "Monitor.sol";

// Offline-only fixture. This file is never included in business deployment artifacts.
contract MonitorVerifierFixture is MonitorVerifier {
    function seedNode(string memory committee, bytes memory publicKey) external onlyOwner {
        monitorNodeEndorseInfoMap[committee] = NodeEndorseInfo(
            "monitor-node", true, NodePublicKeyEntry("test-key", publicKey));
    }
}

contract HubFixture {
    address public verifier;
    constructor(address trustedVerifier) { verifier = trustedVerifier; }
    function verifierMap(uint8) external view returns (address) { return verifier; }
}

contract DecoderFixture {
    function encode(bytes memory body) external pure returns (bytes memory) {
        return MonitorLib.encode(MonitorMessage(2, "", body));
    }
    function roundTrip(bytes memory body) external pure returns (bytes memory) {
        MonitorMessage memory message = MonitorMessage(2, "", body);
        bytes memory encoded = MonitorLib.encode(message);
        MonitorMessage memory decoded;
        MonitorLib.decode(decoded, encoded);
        require(decoded.monitorType == 2 && bytes(decoded.monitorMsg).length == 0, "bad metadata");
        return decoded.message;
    }
    function decode(bytes memory encoded) external pure returns (bytes memory) {
        MonitorMessage memory decoded;
        MonitorLib.decode(decoded, encoded);
        return decoded.message;
    }
    function context(string memory domain, bytes32 author, bytes32 receiver, bytes memory body)
        external pure returns (bytes32 direct, bytes32 fromProof)
    {
        SDPMessage memory sdp = SDPMessage("target", receiver, body, 0xffffffff);
        AuthMessage memory am = AuthMessage(1, author, 0, SDPLib.encode(sdp));
        direct = MonitorProofContext.key(domain, author, receiver, body);
        fromProof = MonitorProofContext.fromResponse(domain, AMLib.encodeAuthMessage(am));
    }
}

contract MonitorSourceFixture is Monitor {
    function seedSender(bytes32 sender, bool blocked) external onlyOwner {
        senderBlacklist[sender] = blocked;
    }
    function seedReceiver(bytes32 receiver, bool blocked) external onlyOwner {
        receiverBlacklist[receiver] = blocked;
    }
}

contract RoutingFixture {
    bytes public lastMessage;
    address public lastAuthor;
    uint256 public received;
    function sendUnorderedMessage(string calldata, bytes32, address author, bytes calldata message) external {
        lastAuthor = author;
        lastMessage = message;
    }
    function recvUnorderedMessage(string calldata, bytes32, bytes calldata message) external {
        lastMessage = message;
        received++;
    }
    function deliver(address monitor, string calldata domain, bytes32 author, address receiver, bytes calldata message) external {
        Monitor(monitor).recvUnorderedMessageFromSDP(domain, author, receiver, message);
    }
}
