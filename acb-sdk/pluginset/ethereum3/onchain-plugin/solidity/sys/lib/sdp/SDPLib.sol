// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "../utils/TypesToBytes.sol";
import "../utils/BytesToTypes.sol";
import "../utils/SizeOf.sol";
import "../utils/TLVUtils.sol";

struct SDPMessage {
    string receiveDomain;
    bytes32 receiver;
    bytes message;
    uint32 sequence;
}

struct SDPMessageV2 {
    uint32 version;
    bytes32 messageId;
    string receiveDomain;
    bytes32 receiver;
    uint8 atomicFlag;
    uint64 nonce;
    uint32 sequence;
    bytes message;
    string errorMsg;
}

struct SDPMessageV3 {
    uint32 version;
    bytes32 messageId;
    string receiveDomain;
    bytes32 receiver;
    uint8 atomicFlag;
    uint64 nonce;
    uint32 sequence;
    uint8 timeoutMeasure;
    uint256 timeout;
    bytes message;
    string errorMsg;
}

struct BlockState {
    uint16 version;
    string domain;
    bytes32 blockHash;
    uint256 blockHeight;
    uint64 blockTimestamp;
}

library SDPLib {
    using TLVUtils for TLVPacket;
    using TLVUtils for TLVItem;

    uint8 constant SDP_V2_ATOMIC_FLAG_NONE_ATOMIC = 0;

    uint8 constant SDP_V2_ATOMIC_FLAG_ATOMIC_REQUEST = 1;

    uint8 constant SDP_V2_ATOMIC_FLAG_ACK_SUCCESS = 2;

    uint8 constant SDP_V2_ATOMIC_FLAG_ACK_ERROR = 3;

    uint8 constant SDP_V2_ATOMIC_FLAG_ACK_RECEIVE_TX_FAILED = 4;

    uint8 constant SDP_V2_ATOMIC_FLAG_ACK_UNKNOWN_EXCEPTION = 5;

    // SDP v3

    uint8 constant SDP_V3_ATOMIC_FLAG_NONE_ATOMIC = 0;

    uint8 constant SDP_V3_ATOMIC_FLAG_ATOMIC_REQUEST = 1;

    uint8 constant SDP_V3_ATOMIC_FLAG_ACK_SUCCESS = 2;

    uint8 constant SDP_V3_ATOMIC_FLAG_ACK_ERROR = 3;

    uint8 constant SDP_V3_ATOMIC_FLAG_ACK_OFF_CHAIN_EXCEPTION = 4;

    uint8 constant SDP_V3_ATOMIC_FLAG_ACK_RECEIVE_TX_FAILED = 5;

    uint8 constant SDP_V3_ATOMIC_FLAG_ACK_UNKNOWN_EXCEPTION = 6;

    uint8 constant SDP_V3_TIMEOUT_MEASUREMENT_NO_TIMEOUT = 0;
    uint8 constant SDP_V3_TIMEOUT_MEASUREMENT_SENDER_HEIGHT = 1;
    uint8 constant SDP_V3_TIMEOUT_MEASUREMENT_RECEIVER_HEIGHT = 2;
    uint8 constant SDP_V3_TIMEOUT_MEASUREMENT_SENDER_TIMESTAMP = 3;
    uint8 constant SDP_V3_TIMEOUT_MEASUREMENT_RECEIVER_TIMESTAMP = 4;

    uint32 constant UNORDERED_SEQUENCE = 0xffffffff;

    // @notice only for orderred msg
    uint64 constant MAX_NONCE = 0xffffffffffffffff;

    function getSDPVersionFrom(bytes memory pkg) pure internal returns (uint32) {
        bytes1 firstByte;
        uint l = pkg.length;
        assembly {
            firstByte := mload(add(add(pkg, 32), sub(l, 4)))
        }

        if (firstByte == 0xff) {
            uint32 version;
            bytes memory rawVersion = new bytes(4);
            assembly {
                mstore(add(rawVersion, 33), mload(add(pkg, add(32, sub(l, 3)))))
                version := mload(add(rawVersion, 4))
            }
            return version;
        }

        return 1;
    }

    function encode(SDPMessage memory sdpMessage) pure internal returns (bytes memory) {

        uint256 len = SizeOf.sizeOfBytes(sdpMessage.message) + 4 + 32 + SizeOf.sizeOfString(sdpMessage.receiveDomain);

        bytes memory pkg = new bytes(len);
        uint offset = len;

        // 填充接受者的domain
        TypesToBytes.stringToBytes(offset, bytes(sdpMessage.receiveDomain), pkg);
        offset -= SizeOf.sizeOfString(sdpMessage.receiveDomain);

        // 填充接受者identity
        TypesToBytes.bytes32ToBytes(offset, sdpMessage.receiver, pkg);
        offset -= SizeOf.sizeOfBytes32();

        // 填充sequence
        TypesToBytes.uint32ToBytes(offset, sdpMessage.sequence, pkg);
        offset -= SizeOf.sizeOfUint(32);

        // 填充消息
        TypesToBytes.stringToBytes(offset, sdpMessage.message, pkg);
        offset -= SizeOf.sizeOfBytes(sdpMessage.message);

        return pkg;
    }

    function decode(SDPMessage memory sdpMessage, bytes memory rawMessage) pure internal {
        uint256 offset = rawMessage.length;

        uint32 dest_domain_len = BytesToTypes.bytesToUint32(offset, rawMessage) + 32;
        bytes memory dest_domain = new bytes(dest_domain_len);
        BytesToTypes.bytesToString(offset, rawMessage, dest_domain);
        offset -= SizeOf.sizeOfBytes(dest_domain);

        bytes32 receiver = BytesToTypes.bytesToBytes32(offset, rawMessage);
        offset -= SizeOf.sizeOfBytes32();

        uint32 sequence = BytesToTypes.bytesToUint32(offset, rawMessage);
        offset -= SizeOf.sizeOfInt(32);

        uint32 message_len = BytesToTypes.bytesToUint32(offset, rawMessage) + 32;
        bytes memory message = new bytes(message_len);
        BytesToTypes.bytesToString(offset, rawMessage, message);
        offset -= SizeOf.sizeOfBytes(message);

        sdpMessage.receiveDomain = string(dest_domain);
        sdpMessage.receiver = receiver;
        sdpMessage.sequence = sequence;
        sdpMessage.message = message;
    }

    function encode(SDPMessageV2 memory sdpMessage) pure internal returns (bytes memory) {
        require(
            sdpMessage.version == 2, 
            "encodeSDPMessage: wrong version"
        );
        require(
            sdpMessage.message.length <= 0xFFFFFFFF,
            "encodeSDPMessage: body length overlimit"
        );
        require(
            sdpMessage.messageId != bytes32(0),
            "encodeSDPMessage: zero message id"
        );

        // 4 + 32 + (4 + domain) + 32 + 1 + 8 + 4 + (4 + payload) + [ (4 + errMsg) ]
        uint total_size;
        bool withErrorMsg = sdpMessage.atomicFlag > SDP_V2_ATOMIC_FLAG_ACK_SUCCESS;
        if (withErrorMsg) {
            total_size = 89 + bytes(sdpMessage.receiveDomain).length + sdpMessage.message.length + 4 + bytes(sdpMessage.errorMsg).length;
        } else {
            total_size = 89 + bytes(sdpMessage.receiveDomain).length + sdpMessage.message.length;
        }

        bytes memory pkg = new bytes(total_size);
        uint offset = total_size;

        TypesToBytes.uintToBytes(offset, sdpMessage.version + 0xff000000, pkg);
        offset -= SizeOf.sizeOfInt(32);

        TypesToBytes.bytes32ToBytes(offset, sdpMessage.messageId, pkg);
        offset -= SizeOf.sizeOfBytes32();

        bytes memory raw_recv_domain = bytes(sdpMessage.receiveDomain);
        TypesToBytes.varBytesToBytes(offset, raw_recv_domain, pkg);
        offset -= 4 + raw_recv_domain.length;
        
        TypesToBytes.bytes32ToBytes(offset, sdpMessage.receiver, pkg);
        offset -= SizeOf.sizeOfBytes32();

        TypesToBytes.byteToBytes(offset, sdpMessage.atomicFlag, pkg);
        offset -= 1;

        TypesToBytes.uint64ToBytes(offset, sdpMessage.nonce, pkg);
        offset -= SizeOf.sizeOfInt(64);

        TypesToBytes.uint32ToBytes(offset, sdpMessage.sequence, pkg);
        offset -= SizeOf.sizeOfInt(32);

        TypesToBytes.varBytesToBytes(offset, sdpMessage.message, pkg);
        offset -= 4 + sdpMessage.message.length;

        if (withErrorMsg) {
            TypesToBytes.varBytesToBytes(offset, bytes(sdpMessage.errorMsg), pkg);
        }

        return pkg;
    }

    function decode(SDPMessageV2 memory sdpMessage, bytes memory rawMessage) internal pure {
        uint256 offset = rawMessage.length;

        sdpMessage.version = getSDPVersionFrom(rawMessage);
        offset -= SizeOf.sizeOfUint(32);

        sdpMessage.messageId = BytesToTypes.bytesToBytes32(offset, rawMessage);
        offset -= SizeOf.sizeOfBytes32();

        bytes memory raw_recv_domain = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        sdpMessage.receiveDomain = string(raw_recv_domain);
        offset -= 4 + raw_recv_domain.length;

        sdpMessage.receiver = BytesToTypes.bytesToBytes32(offset, rawMessage);
        offset -= SizeOf.sizeOfBytes32();

        sdpMessage.atomicFlag = BytesToTypes.bytesToUint8(offset, rawMessage);
        offset -= 1;

        sdpMessage.nonce = BytesToTypes.bytesToUint64(offset, rawMessage);
        offset -= 8;

        sdpMessage.sequence = BytesToTypes.bytesToUint32(offset, rawMessage);
        offset -= 4;

        sdpMessage.message = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        offset -= 4 + sdpMessage.message.length;

        if (sdpMessage.atomicFlag > SDP_V2_ATOMIC_FLAG_ACK_SUCCESS) {
            sdpMessage.errorMsg = string(BytesToTypes.bytesToVarBytes(offset, rawMessage));
        }
    }

    function calcMessageId(SDPMessageV2 memory sdpMessage, bytes32 localDomainHash) internal view {
        require(
            sdpMessage.version == 2, 
            "encodeSDPMessage: wrong version"
        );
        require(
            sdpMessage.message.length <= 0xFFFFFFFF,
            "encodeSDPMessage: body length overlimit"
        );

        uint total_size = 121 + bytes(sdpMessage.receiveDomain).length + sdpMessage.message.length;
        bytes memory pkg = new bytes(total_size);
        uint offset = total_size;

        TypesToBytes.uintToBytes(offset, sdpMessage.version + 0xff000000, pkg);
        offset -= SizeOf.sizeOfInt(32);

        bytes memory raw_recv_domain = bytes(sdpMessage.receiveDomain);
        TypesToBytes.varBytesToBytes(offset, raw_recv_domain, pkg);
        offset -= 4 + raw_recv_domain.length;
        
        TypesToBytes.bytes32ToBytes(offset, sdpMessage.receiver, pkg);
        offset -= SizeOf.sizeOfBytes32();

        TypesToBytes.byteToBytes(offset, sdpMessage.atomicFlag, pkg);
        offset -= 1;

        TypesToBytes.uint64ToBytes(offset, sdpMessage.nonce, pkg);
        offset -= SizeOf.sizeOfInt(64);

        TypesToBytes.uint32ToBytes(offset, sdpMessage.sequence, pkg);
        offset -= SizeOf.sizeOfInt(32);

        TypesToBytes.varBytesToBytes(offset, sdpMessage.message, pkg);
        offset -= 4 + sdpMessage.message.length;

        TypesToBytes.addressToBytes(offset, msg.sender, pkg);
        offset -= SizeOf.sizeOfBytes32();

        TypesToBytes.bytes32ToBytes(offset, localDomainHash, pkg);
        offset -= SizeOf.sizeOfBytes32();
        
        sdpMessage.messageId = keccak256(pkg);
    }

    function encode(SDPMessageV3 memory sdpMessage) internal pure returns (bytes memory) {
        require(
            sdpMessage.version == 3,
            "encodeSDPMessage: wrong version"
        );
        require(
            sdpMessage.message.length <= 0xFFFFFFFF,
            "encodeSDPMessage: body length overlimit"
        );
        require(
            sdpMessage.messageId != bytes32(0),
            "encodeSDPMessage: zero message id"
        );

        // 4 + 32 + (4 + domain) + 32 + 1 + 8 + 4 + 1 + (4 + timeout) + (4 + payload) + [ (4 + errMsg) ]
        uint total_size;
        bool withErrorMsg = sdpMessage.atomicFlag > SDP_V3_ATOMIC_FLAG_ACK_SUCCESS;
        bytes memory rawTimeOut = TypesToBytes.uint256ToVarBytes(sdpMessage.timeout);

        if (withErrorMsg) {
            total_size = 94 + bytes(sdpMessage.receiveDomain).length + sdpMessage.message.length + rawTimeOut.length + 4 + bytes(sdpMessage.errorMsg).length;
        } else {
            total_size = 94 + bytes(sdpMessage.receiveDomain).length + sdpMessage.message.length + rawTimeOut.length;
        }

        bytes memory pkg = new bytes(total_size);
        uint offset = total_size;

        TypesToBytes.uint32ToBytes(offset, sdpMessage.version + 0xff000000, pkg);
        offset -= SizeOf.sizeOfInt(32);

        TypesToBytes.bytes32ToBytes(offset, sdpMessage.messageId, pkg);
        offset -= SizeOf.sizeOfBytes32();

        bytes memory raw_recv_domain = bytes(sdpMessage.receiveDomain);
        TypesToBytes.varBytesToBytes(offset, raw_recv_domain, pkg);
        offset -= 4 + raw_recv_domain.length;

        TypesToBytes.bytes32ToBytes(offset, sdpMessage.receiver, pkg);
        offset -= SizeOf.sizeOfBytes32();

        TypesToBytes.byteToBytes(offset, sdpMessage.atomicFlag, pkg);
        offset -= 1;

        TypesToBytes.uint64ToBytes(offset, sdpMessage.nonce, pkg);
        offset -= SizeOf.sizeOfInt(64);

        TypesToBytes.uint32ToBytes(offset, sdpMessage.sequence, pkg);
        offset -= SizeOf.sizeOfInt(32);

        TypesToBytes.byteToBytes(offset, sdpMessage.timeoutMeasure, pkg);
        offset -= 1;

        TypesToBytes.varBytesToBytes(offset, rawTimeOut, pkg);
        offset -= 4 + rawTimeOut.length;

        TypesToBytes.varBytesToBytes(offset, sdpMessage.message, pkg);
        offset -= 4 + sdpMessage.message.length;

        if (withErrorMsg) {
            TypesToBytes.varBytesToBytes(offset, bytes(sdpMessage.errorMsg), pkg);
            offset -= 4 + bytes(sdpMessage.errorMsg).length;
        }

        return pkg;
    }

    function decode(SDPMessageV3 memory sdpMessage, bytes memory rawMessage) internal pure {
        uint256 offset = rawMessage.length;

        sdpMessage.version = getSDPVersionFrom(rawMessage);
        offset -= SizeOf.sizeOfUint(32);

        sdpMessage.messageId = BytesToTypes.bytesToBytes32(offset, rawMessage);
        offset -= SizeOf.sizeOfBytes32();

        bytes memory raw_recv_domain = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        sdpMessage.receiveDomain = string(raw_recv_domain);
        offset -= 4 + raw_recv_domain.length;

        sdpMessage.receiver = BytesToTypes.bytesToBytes32(offset, rawMessage);
        offset -= SizeOf.sizeOfBytes32();

        sdpMessage.atomicFlag = BytesToTypes.bytesToUint8(offset, rawMessage);
        offset -= 1;

        sdpMessage.nonce = BytesToTypes.bytesToUint64(offset, rawMessage);
        offset -= 8;

        sdpMessage.sequence = BytesToTypes.bytesToUint32(offset, rawMessage);
        offset -= 4;

        sdpMessage.timeoutMeasure = BytesToTypes.bytesToUint8(offset, rawMessage);
        offset -= 1;

        bytes memory rawTimeOut = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        sdpMessage.timeout = TLVUtils.getUint256FromBytes(rawTimeOut);
        offset -= 4 + rawTimeOut.length;

        sdpMessage.message = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        offset -= 4 + sdpMessage.message.length;

        if (sdpMessage.atomicFlag > SDP_V3_ATOMIC_FLAG_ACK_SUCCESS) {
            sdpMessage.errorMsg = string(BytesToTypes.bytesToVarBytes(offset, rawMessage));
            offset -= 4 + bytes(sdpMessage.errorMsg).length;
        }
    }

    function calcMessageId(SDPMessageV3 memory sdpMessage, bytes32 localDomainHash) internal view {
        require(
            sdpMessage.version == 3,
            "encodeSDPMessage: wrong version"
        );
        require(
            sdpMessage.message.length <= 0xFFFFFFFF,
            "encodeSDPMessage: body length overlimit"
        );

        bytes memory rawTimeOut = TypesToBytes.uint256ToVarBytes(sdpMessage.timeout);
        // 4 + (4 + domain) + 32 + 1 + 8 + 4 + 1 + (4 + timeout) + (4 + payload) + 32 + 32
        uint total_size = 126 + bytes(sdpMessage.receiveDomain).length + sdpMessage.message.length + rawTimeOut.length;
        bytes memory pkg = new bytes(total_size);
        uint offset = total_size;

        TypesToBytes.uint32ToBytes(offset, sdpMessage.version + 0xff000000, pkg);
        offset -= SizeOf.sizeOfInt(32);

        bytes memory raw_recv_domain = bytes(sdpMessage.receiveDomain);
        TypesToBytes.varBytesToBytes(offset, raw_recv_domain, pkg);
        offset -= 4 + raw_recv_domain.length;
        

        TypesToBytes.bytes32ToBytes(offset, sdpMessage.receiver, pkg);
        offset -= SizeOf.sizeOfBytes32();

        TypesToBytes.byteToBytes(offset, sdpMessage.atomicFlag, pkg);
        offset -= 1;

        TypesToBytes.uint64ToBytes(offset, sdpMessage.nonce, pkg);
        offset -= SizeOf.sizeOfInt(64);

        TypesToBytes.uint32ToBytes(offset, sdpMessage.sequence, pkg);
        offset -= SizeOf.sizeOfInt(32);

        TypesToBytes.byteToBytes(offset, sdpMessage.timeoutMeasure, pkg);
        offset -= 1;

        TypesToBytes.varBytesToBytes(offset, rawTimeOut, pkg);
        offset -= 4 + rawTimeOut.length;

        TypesToBytes.varBytesToBytes(offset, sdpMessage.message, pkg);
        offset -= 4 + sdpMessage.message.length;

        TypesToBytes.addressToBytes(offset, msg.sender, pkg);
        offset -= SizeOf.sizeOfBytes32();

        TypesToBytes.bytes32ToBytes(offset, localDomainHash, pkg);
        
        offset -= SizeOf.sizeOfBytes32();

        sdpMessage.messageId = keccak256(pkg);
    }

    function getSendingSeqID(string memory receiveDomain, address sender, bytes32 receiver) pure internal returns (bytes32) {
        bytes32 sender32 = TypesToBytes.addressToBytes32(sender);
        return keccak256(abi.encodePacked(sender32, keccak256(abi.encodePacked(receiveDomain)), receiver));
    }

    function getReceivingSeqID(string memory sendDomain, bytes32 sender, bytes32 receiver) pure internal returns (bytes32) {
        return keccak256(abi.encodePacked(keccak256(abi.encodePacked(sendDomain)), sender, receiver));
    }

    function getReceivingNonceID(string memory sendDomain, bytes32 sender, bytes32 receiver, uint64 nonce) pure internal returns (bytes32) {
        return keccak256(abi.encodePacked(sendDomain, sender, receiver, nonce));
    }

    function encodeCrossChainIDIntoAddress(bytes32 id) pure internal returns (address) {
        bytes memory rawId = new bytes(32);
        TypesToBytes.bytes32ToBytes(32, id, rawId);
        return BytesToTypes.bytesToAddress(32, rawId);
    }

    function getReceiverAddress(SDPMessageV2 memory sdpMessage) pure internal returns (address) {
        return encodeCrossChainIDIntoAddress(sdpMessage.receiver);
    }

    function getReceiverAddress(SDPMessage memory sdpMessage) pure internal returns (address) {
        return encodeCrossChainIDIntoAddress(sdpMessage.receiver);
    }

    function getReceiverAddress(SDPMessageV3 memory sdpMessage) pure internal returns (address) {
        return encodeCrossChainIDIntoAddress(sdpMessage.receiver);
    }

    function decodeBlockStateFrom(bytes memory rawBs) internal pure returns (BlockState memory) {
        BlockState memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawBs);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == 0) {
                result.version = currItem.toUint16();
            } else if (currItem.tag == 1) {
                result.domain = currItem.toString();
            } else if (currItem.tag == 2) {
                result.blockHash = BytesToTypes.bytesToBytes32(32, currItem.toBytes());
            } else if (currItem.tag == 3) {
                result.blockHeight = TLVUtils.getUint256FromBytes(currItem.toBytes());
            } else if (currItem.tag == 4) {
                result.blockTimestamp = currItem.toUint64();
            }
        }
        return result;
    }
}
