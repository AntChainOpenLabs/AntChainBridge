// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "./Utils.sol";
import "./TypesToBytes.sol";
import "./BytesToTypes.sol";

struct TLVItem {
    uint16 tag;
    uint32 len;
    bytes value;
}

struct LVItem {
    uint32 len;
    bytes value;
}

struct TLVPacket {
    uint16 version;
    TLVItem[] items;
}

struct TLVItemMapValueStream {
    uint offset;
    bytes value;
}

struct BytesArrayStream {
    uint offset;
    bytes value;
}

library TLVUtils {

    using TLVUtils for TLVPacket;
    using TLVUtils for TLVItem;
    using TLVUtils for TLVItemMapValueStream;
    using TLVUtils for BytesArrayStream;

    function itemsLength(TLVPacket memory packet) internal pure returns (uint256) {
        return packet.items.length;
    }

    function bodySize(TLVPacket memory packet) internal pure returns (uint32) {
        uint32 size = 0;
        for (uint i = 0; i < packet.itemsLength(); i++) {
            TLVItem memory item = packet.items[i];
            size = uint32(size + 6 + item.value.length);
        }
        return size;
    }

    function decodePacket(bytes memory rawData) internal pure returns (TLVPacket memory) {
        require(rawData.length >= 6, "illegal raw data length");

        uint offset = 0;
        TLVPacket memory packet;
        packet.version = Utils.reverseUint16(Utils.readUint16(rawData, offset));
        offset += 6;

        uint arrLen = 0;
        uint tempOffset = offset;
        while (tempOffset < rawData.length) {
            tempOffset += (6 + Utils.reverseUint32(Utils.readUint32(rawData, tempOffset + 2)));
            arrLen++;
        }

        TLVItem[] memory arr = new TLVItem[](arrLen);
        arrLen = 0;
        while (offset < rawData.length) {
            TLVItem memory item;
            (item, offset) = parseTLVItem(rawData, offset);
            arr[arrLen++] = item;
        }
        packet.items = arr;

        return packet;
    }

    function encode(TLVPacket memory packet) internal pure returns (bytes memory) {
        uint32 bodySizeNum = packet.bodySize();
        bytes memory rawData = new bytes(bodySizeNum + 6);
        TypesToBytes.uint16ToBytes(2, Utils.reverseUint16(packet.version), rawData);
        TypesToBytes.uint32ToBytes(6, Utils.reverseUint32(bodySizeNum), rawData);

        uint offset = 6;
        for (uint i = 0; i < packet.itemsLength(); i++) {
            bytes memory currRawItem = packet.items[i].encode();
            TypesToBytes.varBytesToBytesBigEndian(offset, currRawItem, rawData);
            offset = offset + currRawItem.length;
        }

        return rawData;
    }

    function encode(TLVItem memory item) internal pure returns (bytes memory) {
        bytes memory rawData = new bytes(item.len + 6);
        TypesToBytes.uint16ToBytes(2, Utils.reverseUint16(item.tag), rawData);
        TypesToBytes.uint32ToBytes(6, Utils.reverseUint32(item.len), rawData);
        TypesToBytes.varBytesToBytesBigEndian(6, item.value, rawData);
        return rawData;
    }

    function fromUint8ToTLVItem(uint16 tag, uint8 val) internal pure returns (TLVItem memory) {
        bytes memory raw = new bytes(1);
        TypesToBytes.byteToBytes(1, val, raw);
        return TLVItem({
            tag: tag,
            len: 1,
            value: raw
        });
    }

    function toUint8(TLVItem memory item) internal pure returns (uint8) {
        return BytesToTypes.bytesToUint8(1, item.value);
    }

    function fromUint16ToTLVItem(uint16 tag, uint16 val) internal pure returns (TLVItem memory) {
        bytes memory raw = new bytes(2);
        TypesToBytes.uint16ToBytes(2, Utils.reverseUint16(val), raw);
        return TLVItem({
            tag: tag,
            len: 2,
            value: raw
        });
    }

    function toUint16(TLVItem memory item) internal pure returns (uint16) {
        return Utils.reverseUint16(BytesToTypes.bytesToUint16(2, item.value));
    }

    function fromUint32ToTLVItem(uint16 tag, uint32 val) internal pure returns (TLVItem memory) {
        bytes memory raw = new bytes(4);
        TypesToBytes.uint32ToBytes(4, Utils.reverseUint32(val), raw);
        return TLVItem({
            tag: tag,
            len: 4,
            value: raw
        });
    }

    function toUint32(TLVItem memory item) internal pure returns (uint32) {
        return Utils.reverseUint32(BytesToTypes.bytesToUint32(4, item.value));
    }

    function fromUint64ToTLVItem(uint16 tag, uint64 val) internal pure returns (TLVItem memory) {
        bytes memory raw = new bytes(8);
        TypesToBytes.uint64ToBytes(8, Utils.reverseUint64(val), raw);
        return TLVItem({
            tag: tag,
            len: 8,
            value: raw
        });
    }

    function toUint64(TLVItem memory item) internal pure returns (uint64) {
        return Utils.reverseUint64(BytesToTypes.bytesToUint64(8, item.value));
    }

    function fromStringToTLVItem(uint16 tag, string memory val) internal pure returns (TLVItem memory) {
        bytes memory raw = bytes(val);
        return TLVItem({
            tag: tag,
            len: uint32(raw.length),
            value: raw
        });
    }

    function toString(TLVItem memory item) internal pure returns (string memory) {
        return string(item.value);
    }

    function fromBytesToTLVItem(uint16 tag, bytes memory val) internal pure returns (TLVItem memory) {
        return TLVItem({
            tag: tag,
            len: uint32(val.length),
            value: val
        });
    }

    function toBytes(TLVItem memory item) internal pure returns (bytes memory) {
        return item.value;
    }

    // for now we use bytes to represent the BigInteger type
    function fromBigIntegerToTLVItem(uint16 tag, bytes memory val) internal pure returns (TLVItem memory) {
        return TLVItem({
            tag: tag,
            len: uint32(val.length),
            value: val
        });
    }

    // for now we use bytes to represent the BigInteger type
    function toBigInteger(TLVItem memory item) internal pure returns (bytes memory) {
        return item.value;
    }

    // to get variable length number
    function getRawBigIntegerFrom(uint256 val) internal pure returns (bytes memory) {
        return TypesToBytes.uint256ToVarBytes(val);
    }

    function fromBytesArrayToTLVItem(uint16 tag, uint totalSize, bytes[] memory val) internal pure returns (TLVItem memory) {
        bytes memory raw = new bytes(totalSize + 4 * val.length);
        uint offset = 0;
        for (uint i = 0; i < val.length; i++)
        {
            TypesToBytes.uint32ToBytes(offset + 4, Utils.reverseUint32(uint32(val[i].length)), raw);
            offset += 4;
            TypesToBytes.varBytesToBytesBigEndian(offset, val[i], raw);
            offset += val[i].length;
        }
        return TLVItem({
            tag: tag,
            len: uint32(raw.length),
            value: raw
        });
    }

    function toBytesArrayStream(TLVItem memory item) internal pure returns (BytesArrayStream memory) {
        return BytesArrayStream({
            offset: 0,
            value: item.value
        });
    }

    function hasNext(BytesArrayStream memory stream) internal pure returns (bool) {
        return stream.offset < stream.value.length;
    }

    function getNextVarBytes(BytesArrayStream memory stream) internal pure returns (bytes memory) {
        require(stream.offset < stream.value.length, "BytesArrayStream: offest out of buffer");
        bytes memory raw = BytesToTypes.bytesToVarBytesBigEndianWithReverseLen(stream.offset, stream.value);
        stream.offset = stream.offset + 4 + raw.length;
        return raw;
    }

    function calcLenValueSize(BytesArrayStream memory stream) internal pure returns (uint) {
        uint currOffsetVal = stream.offset;
        uint count = 0;
        while (stream.hasNext()) {
            stream.getNextVarBytes();
            count++;
        }
        stream.offset = currOffsetVal;
        return count;
    }

    function getUint256FromRawBigInteger(uint offset, bytes memory raw) internal pure returns (uint256) {
        return getUint256FromBytes(BytesToTypes.bytesToVarBytesBigEndianWithReverseLen(offset, raw));
    }

    function getUint256FromBytes(bytes memory numVal) internal pure returns (uint256) {
        uint l = numVal.length;
        require(l <= 32, "32bytes big integer at most for now");
        bytes memory temp = new bytes(l);
        
        uint256 r = 0;
        assembly {
            mstore(temp, mload(numVal))
            let minuend := mload(add(numVal, l))
            let subtrahend := mload(add(temp, l))
            
            r := sub(minuend, subtrahend)
        }

        return r;
    }

    function fromMapValueStreamToTLVItem(uint16 tag, TLVItemMapValueStream memory stream) internal pure returns (TLVItem memory) {
        return TLVItem({
            tag: tag,
            len: uint32(stream.value.length),
            value: stream.value
        });
    }

    function toMapValueStream(TLVItem memory item) internal pure returns (TLVItemMapValueStream memory) {
        return TLVItemMapValueStream({
            offset: 0,
            value: item.value
        });
    }

    function getNextVarBytes(TLVItemMapValueStream memory stream) internal pure returns (bytes memory) {
        require(stream.offset < stream.value.length, "TLVItemMapValueStream: offest out of buffer");
        bytes memory raw = BytesToTypes.bytesToVarBytesBigEndianWithReverseLen(stream.offset, stream.value);
        stream.offset = stream.offset + 4 + raw.length;
        return raw;
    }

    function getNextBigInteger(TLVItemMapValueStream memory stream) internal pure returns (uint256) {
        return getUint256FromBytes(stream.getNextVarBytes());
    }

    function hasNext(TLVItemMapValueStream memory stream) internal pure returns (bool) {
        return stream.offset < stream.value.length;
    }

    function parseTLVItem(bytes memory rawData, uint offset) internal pure returns (TLVItem memory, uint) {
        require(
            rawData.length > offset,
            "length of raw data less than offset"
        );
        require(
            offset >= 6,
            "illegal offset"
        );

        TLVItem memory item;
        item.tag = Utils.reverseUint16(Utils.readUint16(rawData, offset));
        offset += 2;
        item.len = Utils.reverseUint32(Utils.readUint32(rawData, offset));
        offset += 4;
        item.value = Utils.substring(rawData, offset, item.len);
        offset += item.len;

        return (item, offset);
    }

    function parseLVItem(bytes memory rawData, uint offset) internal pure returns (LVItem memory, uint) {
        require(
            rawData.length > offset,
            "length of raw data less than offset"
        );
        require(
            offset >= 6,
            "illegal offset"
        );

        LVItem memory item;
        item.len = Utils.reverseUint32(Utils.readUint32(rawData, offset));
        offset += 4;
        item.value = Utils.substring(rawData, offset, item.len);
        offset += item.len;

        return (item, offset);
    }
}