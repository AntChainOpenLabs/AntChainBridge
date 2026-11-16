pragma solidity ^0.4.22;
pragma experimental ABIEncoderV2;

import "./utils.sol";

library TLVUtils {
    struct TLVItem {
        uint16 tag;
        uint32 len;
        bytes value;
    }

    struct LVItem {
        uint32 len;
        bytes value;
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
        item.tag = utils.reverseUint16(utils.readUint16(rawData, offset));
        offset += 2;
        item.len = utils.reverseUint32(utils.readUint32(rawData, offset));
        offset += 4;
        item.value = utils.substring(rawData, offset, item.len);
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
        item.len = utils.reverseUint32(utils.readUint32(rawData, offset));
        offset += 4;
        item.value = utils.substring(rawData, offset, item.len);
        offset += item.len;

        return (item, offset);
    }
}