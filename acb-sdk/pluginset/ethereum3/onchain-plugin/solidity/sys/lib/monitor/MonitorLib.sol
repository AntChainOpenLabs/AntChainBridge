// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "../utils/TypesToBytes.sol";
import "../utils/BytesToTypes.sol";
import "../utils/SizeOf.sol";
import "../utils/TLVUtils.sol";

struct MonitorOrder {
    string product;
    string domain;
    uint32 monitorOrderType;
    string senderDomain;
    bytes32 sender;
    string receiverDomain;
    bytes32 receiver;
    string transactionContent;
    string extra;
}

struct MonitorMessage {
    uint32 monitorType;
    string monitorMsg;
    bytes message;
}

library MonitorLib {
    // 监管字段(monitorType值)
    uint32 constant public MONITOR_CLOSE = 1;
    uint32 constant public MONITOR_OPEN = 2;
    uint32 constant public MONITOR_ROLLBACK = 3;

    // 监管指令类型与子类型(32bit 每4bit为间隔 4bit中前1bit表示主类型 后3bit为子类型)
    // 0000 0000 0000 0000 0000 0000 0000 0000: 从左到右共 8 个主类型
    // 第一个主类型-地址相关
    uint8 constant public MAJOR_TYPE_CONTRACT_ADDRESS = 1;
    //  子类型-黑名单
    uint8 constant public MINOR_TYPE_ADD_TO_BLACKLIST = 0;
    uint8 constant public MINOR_TYPE_REMOVE_FROM_BLACKLIST = 1;

    // 第二个主类型-控制相关
    uint8 constant public MAJOR_TYPE_CONTROL = 1;
    //  子类型-是否开启监管
    uint8 constant public MINOR_TYPE_MONITOR_CLOSE = 0;
    uint8 constant public MINOR_TYPE_MONITOR_OPEN = 1;

    // 事前监管失败类型
    uint8 constant public SENDER_IN_BLACKLIST = 0;
    uint8 constant public RECEIVER_IN_BLACKLIST = 1;

    function encodeAddressIntoCrossChainID(address _address) internal pure returns (bytes32) {
        bytes32 id = TypesToBytes.addressToBytes32(_address);
        return id;
    }

    function encodeCrossChainIDIntoAddress(bytes32 id) pure internal returns (address) {
        bytes memory rawId = new bytes(32);
        TypesToBytes.bytes32ToBytes(32, id, rawId);
        return BytesToTypes.bytesToAddress(32, rawId);
    }

    function decode(MonitorOrder memory monitorOrder, bytes memory rawMessage) internal pure {
        uint256 offset = rawMessage.length;

        bytes memory raw_product = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        monitorOrder.product = string(raw_product);
        offset -= 4 + raw_product.length;

        bytes memory raw_domain = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        monitorOrder.domain = string(raw_domain);
        offset -= 4 + raw_domain.length;

        monitorOrder.monitorOrderType = BytesToTypes.bytesToUint32(offset, rawMessage);
        offset -= 4;

        bytes memory raw_send_domain = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        monitorOrder.senderDomain = string(raw_send_domain);
        offset -= 4 + raw_send_domain.length;

        monitorOrder.sender = BytesToTypes.bytesToBytes32(offset, rawMessage);
        offset -= SizeOf.sizeOfBytes32();

        bytes memory raw_recv_domain = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        monitorOrder.receiverDomain = string(raw_recv_domain);
        offset -= 4 + raw_recv_domain.length;

        monitorOrder.receiver = BytesToTypes.bytesToBytes32(offset, rawMessage);
        offset -= SizeOf.sizeOfBytes32();

        bytes memory raw_tran_cont = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        monitorOrder.transactionContent = string(raw_tran_cont);
        offset -= 4 + raw_tran_cont.length;

        bytes memory raw_extra = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        monitorOrder.extra = string(raw_extra);
        offset -= 4 + raw_extra.length;
    }

    // function encode(MonitorMessage memory monitorMessage) pure internal returns (bytes memory) {
    //     require(
    //         monitorMessage.message.length <= 0xFFFFFFFF,
    //         "encodeSDPMessage: body length overlimit"
    //     );
    //     // 4 + (4 + monitorMsg) + (4 + payload)
    //     uint total_size = 12 + bytes(monitorMessage.monitorMsg).length + monitorMessage.message.length;
    //     bytes memory pkg = new bytes(total_size);
    //     uint offset = total_size;

    //     TypesToBytes.uint32ToBytes(offset, monitorMessage.monitorType, pkg);
    //     offset -= SizeOf.sizeOfUint(32);

    //     bytes memory raw_monitorMsg = bytes(monitorMessage.monitorMsg);
    //     TypesToBytes.varBytesToBytes(offset, raw_monitorMsg, pkg);
    //     offset -= 4 + raw_monitorMsg.length;

    //     TypesToBytes.varBytesToBytes(offset, monitorMessage.message, pkg);
    //     offset -= 4 + monitorMessage.message.length;

    //     return pkg;
    // }

    // function decode(MonitorMessage memory monitorMessage, bytes memory rawMessage) internal pure {
    //     uint256 offset = rawMessage.length;

    //     monitorMessage.monitorType = BytesToTypes.bytesToUint32(offset, rawMessage);
    //     offset -= SizeOf.sizeOfInt(32);

    //     bytes memory raw_monitorMsg = BytesToTypes.bytesToVarBytes(offset, rawMessage);
    //     monitorMessage.monitorMsg = string(raw_monitorMsg);
    //     offset -= 4 + raw_monitorMsg.length;

    //     monitorMessage.message = BytesToTypes.bytesToVarBytes(offset, rawMessage);
    //     offset -= 4 + monitorMessage.message.length;
    // }

    /** V1 source envelope with an empty monitorMsg, byte-exact with the legacy memory encoder. */
    function encodeCalldata(uint32 control, bytes calldata message) internal pure returns (bytes memory pkg) {
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

    function encode(MonitorMessage memory monitorMessage) pure internal returns (bytes memory) {

        uint total_size = 4 + SizeOf.sizeOfString(monitorMessage.monitorMsg) + SizeOf.sizeOfBytes(monitorMessage.message);
        bytes memory pkg = new bytes(total_size);
        uint offset = total_size;

        TypesToBytes.uint32ToBytes(offset, monitorMessage.monitorType, pkg);
        offset -= SizeOf.sizeOfUint(32);

        TypesToBytes.stringToBytes(offset, bytes(monitorMessage.monitorMsg), pkg);
        offset -= SizeOf.sizeOfString(monitorMessage.monitorMsg);

        TypesToBytes.stringToBytes(offset, monitorMessage.message, pkg);
        offset -= SizeOf.sizeOfBytes(monitorMessage.message);

        return pkg;
    }

    function decode(MonitorMessage memory monitorMessage, bytes memory rawMessage) internal pure {
        uint256 offset = rawMessage.length;
        require(offset >= 68, "MonitorLib: malformed monitor message");

        monitorMessage.monitorType = _readUint32AtEnd(rawMessage, offset);
        require(
            monitorMessage.monitorType == MONITOR_CLOSE
                || monitorMessage.monitorType == MONITOR_OPEN
                || monitorMessage.monitorType == MONITOR_ROLLBACK,
            "MonitorLib: invalid monitor type"
        );
        offset -= SizeOf.sizeOfInt(32);

        bytes memory monitor_msg;
        bytes memory message;
        (monitor_msg, offset) = _decodeReversePaddedBytes(rawMessage, offset);
        (message, offset) = _decodeReversePaddedBytes(rawMessage, offset);
        require(offset == 0, "MonitorLib: trailing monitor message data");

        monitorMessage.monitorMsg = string(monitor_msg);
        monitorMessage.message = message;
    }

    /**
     * Decode the legacy ACB EVM variable-bytes representation explicitly.
     * Payload words are stored in reverse order and followed by a 32-byte
     * length slot. Avoid the assembly decoder here because contracts compiled
     * by MySolidity and standard solc disagree on non-word-aligned memory
     * copies, which can otherwise turn a successful relay into truncated data.
     */
    function _decodeReversePaddedBytes(bytes memory rawMessage, uint256 endOffset)
        private
        pure
        returns (bytes memory value, uint256 nextOffset)
    {
        require(endOffset >= 32, "MonitorLib: malformed variable bytes");
        uint256 length = uint256(_readUint32AtEnd(rawMessage, endOffset));
        uint256 wordCount = (length + 31) / 32;
        uint256 paddedLength = wordCount * 32;
        require(endOffset >= 32 + paddedLength, "MonitorLib: variable bytes out of bounds");

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
        private
        pure
        returns (uint32)
    {
        require(endOffset >= 4 && endOffset <= rawMessage.length, "MonitorLib: uint32 out of bounds");
        return (uint32(uint8(rawMessage[endOffset - 4])) << 24)
            | (uint32(uint8(rawMessage[endOffset - 3])) << 16)
            | (uint32(uint8(rawMessage[endOffset - 2])) << 8)
            | uint32(uint8(rawMessage[endOffset - 1]));
    }
}
