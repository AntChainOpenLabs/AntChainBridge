// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./lib/monitor/MonitorProofContext.sol";
pragma experimental ABIEncoderV2;

import "./interfaces/ISDPMessage.sol";
import "./interfaces/IContractUsingSDP.sol";
import "./interfaces/IContractWithAcks.sol";
import "./interfaces/IMonitorVerifier.sol";
import "./lib/utils/Ownable.sol";
import "./lib/utils/SizeOf.sol";
import "./lib/utils/TypesToBytes.sol";

contract Monitor is Ownable {
    uint32 public constant IMPLEMENTATION_VERSION = 9;
    uint32 public constant MONITOR_CLOSE = 1;
    uint32 public constant MONITOR_OPEN = 2;
    uint32 public constant MONITOR_ROLLBACK = 3;
    uint8 private constant MAJOR_TYPE_ENABLED = 1;
    uint8 private constant MINOR_TYPE_ADD_TO_BLACKLIST = 0;
    uint8 private constant MINOR_TYPE_REMOVE_FROM_BLACKLIST = 1;
    uint8 private constant MINOR_TYPE_MONITOR_CLOSE = 0;
    uint8 private constant MINOR_TYPE_MONITOR_OPEN = 1;

    identity public sdpAddress;
    identity public monitorVerifierAddress;
    uint32 public monitorControl;

    mapping(bytes32 => bool) public senderBlacklist;
    mapping(bytes32 => bool) public receiverBlacklist;

    event MonitorOrderApplied(
        uint32 orderType,
        bytes32 sender,
        bytes32 receiver,
        bool senderBlocked,
        bool receiverBlocked,
        uint32 monitorControl
    );
    event MonitorMessageSent(identity indexed sender, string receiverDomain, identity receiverID, uint32 monitorType);
    event MonitorMessageReceived(
        string senderDomain,
        identity indexed sender,
        identity indexed receiver,
        uint32 monitorType,
        bool unordered
    );
    event MonitorNodeProofVerified(
        string senderDomain,
        identity indexed sender,
        identity indexed receiver,
        bool result
    );
    event MonitorRollbackSent(
        identity indexed sender,
        string receiverDomain,
        identity indexed receiver,
        bool unordered
    );

    modifier onlySubProtocol() {
        require(msg.sender == sdpAddress, "Monitor: sender is not sdp");
        _;
    }

    constructor() {
        monitorControl = MONITOR_OPEN;
    }

    function getImplementationVersion() external pure returns (uint32) {
        return IMPLEMENTATION_VERSION;
    }

    function setSdpAddress(identity newSdpAddress) external onlyOwner {
        require(newSdpAddress != identity(0), "Monitor: invalid sdp address");
        sdpAddress = newSdpAddress;
    }

    function setMonitorVerifierAddress(identity newMonitorVerifierAddress) external onlyOwner {
        require(newMonitorVerifierAddress != identity(0), "Monitor: invalid verifier address");
        monitorVerifierAddress = newMonitorVerifierAddress;
    }

    function setMonitorControl(uint32 newMonitorControl) external onlyOwner {
        require(
            newMonitorControl == MONITOR_CLOSE
                || newMonitorControl == MONITOR_OPEN
                || newMonitorControl == MONITOR_ROLLBACK,
            "Monitor: invalid monitor control"
        );
        monitorControl = newMonitorControl;
    }

    function getProtocol() external view returns (identity) {
        return sdpAddress;
    }

    function getMonitorVerifier() external view returns (identity) {
        return monitorVerifierAddress;
    }

    function getMonitorControl() external view returns (uint32) {
        return monitorControl;
    }

    function sendMonitorMessage(
        string calldata receiverDomain,
        identity receiverID,
        bytes calldata message
    ) external {
        if (monitorControl == MONITOR_OPEN) {
            require(_preMonitoring(msg.sender, receiverID), "Monitor: pre monitoring blocked");
        }
        bytes memory rawMonitorMessage = _encodeMonitorMessage(monitorControl, message);
        ISDPMessage(sdpAddress).sendMessageFromMonitor(receiverDomain, receiverID, msg.sender, rawMonitorMessage);
        emit MonitorMessageSent(msg.sender, receiverDomain, receiverID, monitorControl);
    }

    function sendUnorderedMonitorMessage(
        string calldata receiverDomain,
        identity receiverID,
        bytes calldata message
    ) external {
        uint32 control = monitorControl;
        if (control == MONITOR_OPEN) {
            require(_preMonitoring(msg.sender, receiverID), "Monitor: pre monitoring blocked");
        }
        bytes memory rawMonitorMessage = _encodeMonitorMessageCalldata(control, message);
        ISDPMessage(sdpAddress).sendUnorderedMessageFromMonitor(receiverDomain, receiverID, msg.sender, rawMonitorMessage);
    }

    function sendMonitorMessageV2(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message
    ) external onlySubProtocol returns (bytes32) {
        bytes memory rawMessage = _prepareMonitoredMessage(senderID, receiverID, message);
        return ISDPMessage(sdpAddress).sendMessageV2FromMonitor(
            receiverDomain, receiverID, senderID, atomic, rawMessage
        );
    }

    function sendUnorderedMonitorMessageV2(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message
    ) external onlySubProtocol returns (bytes32) {
        bytes memory rawMessage = _prepareMonitoredMessage(senderID, receiverID, message);
        return ISDPMessage(sdpAddress).sendUnorderedMessageV2FromMonitor(
            receiverDomain, receiverID, senderID, atomic, rawMessage
        );
    }

    function sendMonitorMessageV3(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message,
        uint8 timeoutMeasure,
        uint256 timeout
    ) external onlySubProtocol returns (bytes32) {
        bytes memory rawMessage = _prepareMonitoredMessage(senderID, receiverID, message);
        return ISDPMessage(sdpAddress).sendMessageV3FromMonitor(
            receiverDomain, receiverID, senderID, atomic, rawMessage, timeoutMeasure, timeout
        );
    }

    function sendUnorderedMonitorMessageV3(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message,
        uint8 timeoutMeasure,
        uint256 timeout
    ) external onlySubProtocol returns (bytes32) {
        bytes memory rawMessage = _prepareMonitoredMessage(senderID, receiverID, message);
        return ISDPMessage(sdpAddress).sendUnorderedMessageV3FromMonitor(
            receiverDomain, receiverID, senderID, atomic, rawMessage, timeoutMeasure, timeout
        );
    }

    function _prepareMonitoredMessage(
        identity senderID,
        identity receiverID,
        bytes calldata message
    ) internal view returns (bytes memory) {
        if (monitorControl == MONITOR_OPEN) {
            require(_preMonitoring(senderID, receiverID), "Monitor: pre monitoring blocked");
        }
        return _encodeMonitorMessage(monitorControl, message);
    }

    function recvMessageFromSDP(
        string calldata senderDomain,
        identity author,
        identity receiverID,
        bytes calldata rawMessage
    ) external onlySubProtocol {
        (uint32 monitorType, bytes memory message) = _decodeMonitorMessage(rawMessage);
        if (monitorType == MONITOR_OPEN) {
            bool verified = _verifyMonitorNodeProof(senderDomain, author, receiverID, rawMessage);
            emit MonitorNodeProofVerified(senderDomain, author, receiverID, verified);
            if (!verified) {
                bytes memory rollbackMessage = _encodeMonitorMessage(
                    MONITOR_ROLLBACK,
                    bytes("MidMonitoring not pass"),
                    message
                );
                ISDPMessage(sdpAddress).sendMessageFromMonitor(
                    senderDomain,
                    author,
                    receiverID,
                    rollbackMessage
                );
                emit MonitorRollbackSent(receiverID, senderDomain, author, false);
                return;
            }
        }
        IContractUsingSDP(receiverID).recvMessage(senderDomain, author, message);
        emit MonitorMessageReceived(senderDomain, author, receiverID, monitorType, false);
    }

    function recvUnorderedMessageFromSDP(
        string calldata senderDomain,
        identity author,
        identity receiverID,
        bytes calldata rawMessage
    ) external onlySubProtocol {
        (uint32 monitorType, bytes memory message) = _decodeMonitorMessage(rawMessage);
        if (monitorType == MONITOR_OPEN) {
            bool verified = _verifyMonitorNodeProof(senderDomain, author, receiverID, rawMessage);
            emit MonitorNodeProofVerified(senderDomain, author, receiverID, verified);
            if (!verified) {
                bytes memory rollbackMessage = _encodeMonitorMessage(
                    MONITOR_ROLLBACK,
                    bytes("MidMonitoring not pass"),
                    message
                );
                ISDPMessage(sdpAddress).sendUnorderedMessageFromMonitor(
                    senderDomain,
                    author,
                    receiverID,
                    rollbackMessage
                );
                emit MonitorRollbackSent(receiverID, senderDomain, author, true);
                return;
            }
        }
        IContractUsingSDP(receiverID).recvUnorderedMessage(senderDomain, author, message);
        emit MonitorMessageReceived(senderDomain, author, receiverID, monitorType, true);
    }

    function recvUnorderedMessageV2FromSDP(
        string calldata senderDomain,
        identity author,
        identity receiverID,
        bytes calldata rawMessage
    ) external onlySubProtocol {
        _recvVersionedMessage(senderDomain, author, receiverID, rawMessage, true);
    }

    function recvMessageV2FromSDP(
        string calldata senderDomain,
        identity author,
        identity receiverID,
        bytes calldata rawMessage
    ) external onlySubProtocol {
        _recvVersionedMessage(senderDomain, author, receiverID, rawMessage, false);
    }

    function recvUnorderedMessageV3FromSDP(
        string calldata senderDomain,
        identity author,
        identity receiverID,
        bytes calldata rawMessage
    ) external onlySubProtocol {
        _recvVersionedMessage(senderDomain, author, receiverID, rawMessage, true);
    }

    function recvMessageV3FromSDP(
        string calldata senderDomain,
        identity author,
        identity receiverID,
        bytes calldata rawMessage
    ) external onlySubProtocol {
        _recvVersionedMessage(senderDomain, author, receiverID, rawMessage, false);
    }

    function _recvVersionedMessage(
        string memory senderDomain,
        identity author,
        identity receiverID,
        bytes memory rawMessage,
        bool unordered
    ) internal {
        (uint32 monitorType, bytes memory message) = _decodeMonitorMessage(rawMessage);
        if (monitorType == MONITOR_OPEN) {
            bool verified = _verifyMonitorNodeProof(senderDomain, author, receiverID, rawMessage);
            emit MonitorNodeProofVerified(senderDomain, author, receiverID, verified);
            require(verified, "Monitor: mid monitoring not pass");
        }
        if (unordered) {
            IContractUsingSDP(receiverID).recvUnorderedMessage(senderDomain, author, message);
        } else {
            IContractUsingSDP(receiverID).recvMessage(senderDomain, author, message);
        }
        emit MonitorMessageReceived(senderDomain, author, receiverID, monitorType, unordered);
    }

    function ackOnSuccessFromSDP(
        identity receiverID,
        bytes32 messageId,
        string memory receiverDomain,
        identity receiver,
        uint32 sequence,
        uint64 nonce,
        bytes memory rawMessage
    ) external onlySubProtocol {
        (, bytes memory message) = _decodeMonitorMessage(rawMessage);
        IContractWithAcks(receiverID).ackOnSuccess(
            messageId, receiverDomain, receiver, sequence, nonce, message
        );
    }

    function ackOnErrorFromSDP(
        identity receiverID,
        bytes32 messageId,
        string memory receiverDomain,
        identity receiver,
        uint32 sequence,
        uint64 nonce,
        bytes memory rawMessage,
        string memory errorMsg
    ) external onlySubProtocol {
        (, bytes memory message) = _decodeMonitorMessage(rawMessage);
        IContractWithAcks(receiverID).ackOnError(
            messageId, receiverDomain, receiver, sequence, nonce, message, errorMsg
        );
    }

    /** V1 source envelope with an empty monitorMsg, byte-exact with the legacy memory encoder. */
    function _encodeMonitorMessageCalldata(uint32 control, bytes calldata message) internal pure returns (bytes memory pkg) {
        uint256 length = message.length;
        uint256 padded = ((length + 31) / 32) * 32;
        pkg = new bytes(68 + padded);
        assembly {
            let data := add(pkg, 32)
            // Reversed payload words precede the payload length and empty reason length.
            mstore(add(data, padded), length)
            mstore(add(data, add(padded, 32)), 0)
            mstore(add(data, add(padded, 36)), and(control, 0xffffffff))
            for { let pos := 0 } lt(pos, length) { pos := add(pos, 32) } {
                let word := calldataload(add(message.offset, pos))
                let remaining := sub(length, pos)
                if lt(remaining, 32) {
                    let shift := mul(sub(32, remaining), 8)
                    word := shl(shift, shr(shift, word))
                }
                mstore(add(data, sub(sub(padded, 32), pos)), word)
            }
        }
    }

    function _encodeMonitorMessage(uint32 monitorType, bytes memory message) internal pure returns (bytes memory) {
        return _encodeMonitorMessage(monitorType, new bytes(0), message);
    }

    function _encodeMonitorMessage(
        uint32 monitorType,
        bytes memory monitorMsg,
        bytes memory message
    ) internal pure returns (bytes memory) {
        uint256 monitorMsgSize = SizeOf.sizeOfBytes(monitorMsg);
        uint256 messageSize = SizeOf.sizeOfBytes(message);
        bytes memory rawMessage = new bytes(4 + monitorMsgSize + messageSize);
        uint256 offset = rawMessage.length;

        TypesToBytes.uint32ToBytes(offset, monitorType, rawMessage);
        offset -= SizeOf.sizeOfUint(32);

        TypesToBytes.stringToBytes(offset, monitorMsg, rawMessage);
        offset -= monitorMsgSize;

        TypesToBytes.stringToBytes(offset, message, rawMessage);
        return rawMessage;
    }

    function _verifyMonitorNodeProof(string memory senderDomain, identity author, identity receiverID, bytes memory rawMessage) internal returns (bool) {
        require(monitorVerifierAddress != identity(0), "Monitor: verifier not set");
        return IMonitorVerifier(monitorVerifierAddress).verifyMonitorNodeProofMessage(MonitorProofContext.key(senderDomain, bytes32(author), bytes32(receiverID), rawMessage));
    }

    function _decodeMonitorMessage(bytes memory rawMessage)
        internal
        pure
        returns (uint32 monitorType, bytes memory message)
    {
        uint256 offset = rawMessage.length;
        require(offset >= 68, "Monitor: malformed monitor message");

        monitorType = _readUint32AtEnd(rawMessage, offset);
        require(
            monitorType == MONITOR_CLOSE || monitorType == MONITOR_OPEN || monitorType == MONITOR_ROLLBACK,
            "Monitor: invalid monitor type"
        );
        offset -= SizeOf.sizeOfUint(32);

        bytes memory monitorMsg;
        (monitorMsg, offset) = _decodeReversePaddedBytes(rawMessage, offset);
        (message, offset) = _decodeReversePaddedBytes(rawMessage, offset);
        require(offset == 0, "Monitor: trailing monitor message data");
    }

    /**
     * Monitor envelopes use the legacy ACB EVM codec: a 32-byte length word
     * follows reverse-ordered 32-byte payload chunks. The assembly helper used
     * by the original Mychain runtime copied non-word-aligned payloads over the
     * bytes length slot, so a 36-byte body arrived as its last four bytes plus
     * zero padding. Copy complete words at explicit payload offsets to preserve the exact
     * business bytes across Ethereum/FISCO/Mychain compilers.
     */
    function _decodeReversePaddedBytes(bytes memory rawMessage, uint256 endOffset)
        internal
        pure
        returns (bytes memory value, uint256 nextOffset)
    {
        require(endOffset >= 32, "Monitor: malformed variable bytes");
        uint256 length = uint256(_readUint32AtEnd(rawMessage, endOffset));
        uint256 wordCount = (length + 31) / 32;
        uint256 paddedLength = wordCount * 32;
        require(endOffset >= 32 + paddedLength, "Monitor: variable bytes out of bounds");

        nextOffset = endOffset - 32 - paddedLength;
        value = new bytes(length);
        for (uint256 logicalWord = 0; logicalWord < wordCount; logicalWord++) {
            uint256 sourceOffset = nextOffset + (wordCount - 1 - logicalWord) * 32;
            uint256 targetOffset = logicalWord * 32;
            // new bytes(length) reserves complete words, including the final padded word.
            // Address payload bytes after their length slots; do not overwrite the length.
            assembly {
                mstore(add(add(value, 32), targetOffset), mload(add(add(rawMessage, 32), sourceOffset)))
            }
        }
    }

    function _readUint32AtEnd(bytes memory rawMessage, uint256 endOffset)
        internal
        pure
        returns (uint32)
    {
        require(endOffset >= 4 && endOffset <= rawMessage.length, "Monitor: uint32 out of bounds");
        return (uint32(uint8(rawMessage[endOffset - 4])) << 24)
            | (uint32(uint8(rawMessage[endOffset - 3])) << 16)
            | (uint32(uint8(rawMessage[endOffset - 2])) << 8)
            | uint32(uint8(rawMessage[endOffset - 1]));
    }

    function preMonitoring(identity receiverID) external view returns (bool) {
        return monitorControl != MONITOR_OPEN || _preMonitoring(msg.sender, receiverID);
    }

    function recvMonitorOrder(
        string calldata committeeId,
        string calldata signAlgo,
        bytes calldata rawProof,
        bytes calldata rawMonitorOrder
    ) external {
        require(monitorVerifierAddress != identity(0), "Monitor: verifier not set");
        require(
            IMonitorVerifier(monitorVerifierAddress).verifyMonitorOrder(committeeId, signAlgo, rawProof, rawMonitorOrder),
            "Monitor: invalid monitor order signature"
        );
        _applyMonitorOrder(rawMonitorOrder);
    }

    function updateSenderBlacklist(bytes32 sender, bool blocked) external onlyOwner {
        senderBlacklist[sender] = blocked;
    }

    function updateReceiverBlacklist(bytes32 receiver, bool blocked) external onlyOwner {
        receiverBlacklist[receiver] = blocked;
    }

    function _preMonitoring(identity senderID, identity receiverID) internal view returns (bool) {
        if (senderBlacklist[bytes32(senderID)]) {
            return false;
        }
        if (receiverBlacklist[bytes32(receiverID)]) {
            return false;
        }
        return true;
    }

    function _applyMonitorOrder(bytes memory rawMonitorOrder) internal {
        (uint32 orderType, bytes32 sender, bytes32 receiver) = _decodeMonitorOrder(rawMonitorOrder);
        bool applied = false;

        uint8 addressChunk = uint8((orderType >> 28) & 0xF);
        if (((addressChunk >> 3) & 0x1) == MAJOR_TYPE_ENABLED) {
            uint8 addressAction = addressChunk & 0x7;
            require(
                addressAction == MINOR_TYPE_ADD_TO_BLACKLIST
                    || addressAction == MINOR_TYPE_REMOVE_FROM_BLACKLIST,
                "Monitor: unsupported blacklist action"
            );
            bool blocked = addressAction == MINOR_TYPE_ADD_TO_BLACKLIST;
            senderBlacklist[sender] = blocked;
            receiverBlacklist[receiver] = blocked;
            applied = true;
        }

        uint8 controlChunk = uint8((orderType >> 24) & 0xF);
        if (((controlChunk >> 3) & 0x1) == MAJOR_TYPE_ENABLED) {
            uint8 controlAction = controlChunk & 0x7;
            if (controlAction == MINOR_TYPE_MONITOR_CLOSE) {
                monitorControl = MONITOR_CLOSE;
            } else if (controlAction == MINOR_TYPE_MONITOR_OPEN) {
                monitorControl = MONITOR_OPEN;
            } else {
                revert("Monitor: unsupported control action");
            }
            applied = true;
        }

        require(applied, "Monitor: unsupported order type");
        emit MonitorOrderApplied(
            orderType,
            sender,
            receiver,
            senderBlacklist[sender],
            receiverBlacklist[receiver],
            monitorControl
        );
    }

    function _decodeMonitorOrder(bytes memory rawMonitorOrder)
        internal
        pure
        returns (uint32 orderType, bytes32 sender, bytes32 receiver)
    {
        uint256 offset = rawMonitorOrder.length;
        offset = _skipVarBytes(rawMonitorOrder, offset);
        offset = _skipVarBytes(rawMonitorOrder, offset);

        require(offset >= 4, "Monitor: missing order type");
        orderType = _readUint32(rawMonitorOrder, offset - 4);
        offset -= 4;

        offset = _skipVarBytes(rawMonitorOrder, offset);
        require(offset >= 32, "Monitor: missing sender");
        offset -= 32;
        sender = _readBytes32(rawMonitorOrder, offset);

        offset = _skipVarBytes(rawMonitorOrder, offset);
        require(offset >= 32, "Monitor: missing receiver");
        offset -= 32;
        receiver = _readBytes32(rawMonitorOrder, offset);
    }

    function _skipVarBytes(bytes memory data, uint256 offset) internal pure returns (uint256) {
        require(offset >= 4, "Monitor: malformed variable field");
        uint32 length = _readUint32(data, offset - 4);
        require(offset >= uint256(length) + 4, "Monitor: truncated variable field");
        return offset - uint256(length) - 4;
    }

    function _readUint32(bytes memory data, uint256 offset) internal pure returns (uint32 value) {
        require(offset + 4 <= data.length, "Monitor: uint32 out of bounds");
        value = (uint32(uint8(data[offset])) << 24)
            | (uint32(uint8(data[offset + 1])) << 16)
            | (uint32(uint8(data[offset + 2])) << 8)
            | uint32(uint8(data[offset + 3]));
    }

    function _readBytes32(bytes memory data, uint256 offset) internal pure returns (bytes32 value) {
        require(offset + 32 <= data.length, "Monitor: bytes32 out of bounds");
        assembly {
            value := mload(add(add(data, 0x20), offset))
        }
    }
}
