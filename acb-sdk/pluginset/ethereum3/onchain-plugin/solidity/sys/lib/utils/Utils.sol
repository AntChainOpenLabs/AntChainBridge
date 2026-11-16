// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./SafeMath.sol";
import "./BytesToTypes.sol";

library Utils {

    function bytesCopy(uint _offset, bytes memory _input, uint _len) internal pure returns (bytes memory) {
        return sliceBytes(_offset, _input, _len);
    }

    function sliceBytes(uint _offset, bytes memory _input, uint len) internal pure returns (bytes memory) {
        bytes memory buff;
        assembly {
            switch iszero(len)
            case 0 {
                buff := mload(0x40)
                let lengthmod := and(len, 31)
                let buff_cnt := add(add(buff, lengthmod), mul(0x20, iszero(lengthmod)))
                let end := add(buff_cnt, len)

                for {
                    let input_cnt := add(add(add(_input, lengthmod), mul(0x20, iszero(lengthmod))), _offset)
                } lt(buff_cnt, end) {
                    buff_cnt := add(buff_cnt, 0x20)
                    input_cnt := add(input_cnt, 0x20)
                } {
                    mstore(buff_cnt, mload(input_cnt))
                }

                mstore(buff, len)
                mstore(0x40, and(add(buff_cnt, 31), not(31)))
            } default {
                buff := mload(0x40)
                mstore(0x40, add(buff, 0x20))
            }
        }

        return buff;
    }

    function abiDecodeBytes(bytes memory _input) internal pure returns (bytes memory) {
        // 提取长度
        uint256 len = BytesToTypes.bytesToUint256(64, _input);
        return bytesCopy(64, _input, len);
    }

    // bytes
    function bytesToBytes32(bytes memory source) internal pure returns (bytes32 result) {
        assembly {
            result := mload(add(source, 32))
        }
    }

    function stringCopyToBytes(uint _offset, bytes memory _input, bytes memory _output) internal pure {
        uint256 stack_size = _input.length / 32;
        if(_input.length % 32 > 0) stack_size++;

        assembly {
            let index := 0

            for
            {stack_size := add(stack_size,1)} //adding because of 32 first bytes memory as the length
            lt(index, stack_size)
            {index := add(index ,1)}
            {
                mstore(add(_output, _offset), mload(add(_input,mul(index,32))))
                _offset := sub(_offset , 32)
            }
        }
    }

    function bytes32ToHexString(bytes32 input) internal pure returns (string memory) {
        bytes memory ret = new bytes(64);

        uint j = 0;
        for (uint i = 0; i < 32; i++) {
            uint8 tmp = uint8(input[i]);
            ret[j++] = bytes1(nibbleToChar(tmp / 0x10));
            ret[j++] = bytes1(nibbleToChar(tmp));
        }

        return string(ret);
    }

    function bytes32ToString(bytes32 x) internal pure returns (string memory) {
        bytes memory bytesString = new bytes(32);
        uint charCount = 0;
        for (uint j = 0; j < 32; j++) {
            // byte char = byte(bytes32(SafeMath.mul(uint256(x), SafeMath.pwr(2, (SafeMath.mul(8, j))))));
            bytes1 char = bytes1(bytes32(uint(x) * 2 ** (8 * j)));
            if (char != 0) {
                bytesString[charCount] = char;
                charCount++;
            }
        }
        bytes memory bytesStringTrimmed = new bytes(charCount);
        for (uint j = 0; j < charCount; j++) {
            bytesStringTrimmed[j] = bytesString[j];
        }

        return string(bytesStringTrimmed);
    }

    function nibbleToChar(uint8 nibble) internal pure returns (uint8) {
        nibble &= 0x0f;
        if (nibble > 9)
            return nibble + 87; // nibble + 'a'- 10
        else
            return nibble + 48; // '0'
    }

    function bytesToHexString(bytes memory bs) internal pure returns(string memory) {
        bytes memory tempBytes = new bytes(bs.length * 2);
        uint len = bs.length;
        for (uint i = 0; i < len; i++) {
            bytes1 b = bs[i];
            bytes1 nb = (b & 0xf0) >> 4;
            tempBytes[2 * i] = nb > 0x09 ? bytes1((uint8(nb) + 0x57)) : (nb | 0x30);
            nb = (b & 0x0f);
            tempBytes[2 * i + 1] = nb > 0x09 ? bytes1((uint8(nb) + 0x57)) : (nb | 0x30);
        }
        return string(tempBytes);
    }

    function bytes32ToBytes(bytes32 data) internal pure returns (bytes memory) {
        bytes memory result = new bytes(32);
        assembly {
            mstore(add(result, 32), data)
        }
        return result;
    }

    function uintToString(uint i) internal pure returns (string memory){
        if (i == 0) return "0";
        uint j = i;
        uint length;
        while (j != 0){
            length++;
            j /= 10;
        }
        bytes memory bstr = new bytes(length);
        uint k = length - 1;
        while (i != 0){
            bstr[k--] = bytes1(uint8(48 + i % 10));
            i /= 10;
        }
        return string(bstr);
    }

    function stringToBytes(string memory source) internal pure returns (bytes memory result) {
        return bytes(source);
    }

    function stringToBytes32(string memory source) internal pure returns (bytes32 result) {
        assembly {
            result := mload(add(source, 32))
        }
    }

    function bytes32ArrayToString(bytes32[] memory data) internal pure returns (string memory) {
        bytes memory bytesString = new bytes(data.length * 32);
        uint urlLength;
        for (uint i = 0; i< data.length; i++) {
            for (uint j = 0; j < 32; j++) {
                // byte char = byte(bytes32(SafeMath.mul(uint256(data[i]), SafeMath.mul(2, SafeMath.mul(8, j)))));
                bytes1 char = bytes1(bytes32(uint(data[i]) * 2 ** (8 * j)));
                if (char != 0) {
                    bytesString[urlLength] = char;
                    urlLength += 1;
                }
            }
        }
        bytes memory bytesStringTrimmed = new bytes(urlLength);
        for (uint i = 0; i < urlLength; i++) {
            bytesStringTrimmed[i] = bytesString[i];
        }
        return string(bytesStringTrimmed);
    }

    function hexstring2byte(bytes1 bp1, bytes1 bp2) internal pure returns (bytes1) {
        bytes1 bp3;
        bytes1 bp4;

        if(bp1 >= 0x30 && bp1 <= 0x39) {
            bp3 = bytes1(uint8(bp1) - 0x30);
        }
        if(bp1 >= 0x41 && bp1 <= 0x46) {
            bp3 = bytes1(uint8(bp1) - 0x41 + 10);
        }
        if(bp1 >= 0x61 && bp1 <= 0x66) {
            bp3 = bytes1(uint8(bp1) - 0x61 + 10);
        }

        if(bp2 >= 0x30 && bp2 <= 0x39) {
            bp4 = bytes1(uint8(bp2) - 0x30);
        }
        if(bp2 >= 0x41 && bp2 <= 0x46) {
            bp4 = bytes1(uint8(bp2) - 0x41 + 10);
        }
        if(bp2 >= 0x61 && bp2 <= 0x66) {
            bp4 = bytes1(uint8(bp2) - 0x61 + 10);
        }

        return ((bp3<<4) | bp4);
    }

    function concat(bytes memory _preBytes, bytes memory _postBytes) internal pure returns (bytes memory) {
        bytes memory tempBytes;

        assembly {

            tempBytes := mload(0x40)
            let length := mload(_preBytes)
            mstore(tempBytes, length)
            let mc := add(tempBytes, 0x20)
            let end := add(mc, length)

            for {

                let cc := add(_preBytes, 0x20)
            } lt(mc, end) {
                mc := add(mc, 0x20)
                cc := add(cc, 0x20)
            } {

                mstore(mc, mload(cc))
            }

            length := mload(_postBytes)
            mstore(tempBytes, add(length, mload(tempBytes)))

            mc := end
            end := add(mc, length)

            for {
                let cc := add(_postBytes, 0x20)
            } lt(mc, end) {
                mc := add(mc, 0x20)
                cc := add(cc, 0x20)
            } {
                mstore(mc, mload(cc))
            }

            mstore(0x40, and(
            add(add(end, iszero(add(length, mload(_preBytes)))), 31),
            not(31) // Round down to the nearest 32 bytes.
            ))
        }

        return tempBytes;
    }

    function uintToBytes(uint _offset, uint _input, bytes memory _output) internal pure {
        assembly {
            mstore(add(_output, _offset), _input)
        }
    }

    function addressToBytes(uint _offset, address _input, bytes memory _output) internal pure {
        assembly {
            mstore(add(_output, _offset), _input)
        }
    }

    function uint8ToBytes(uint8 input) internal pure returns (bytes memory) {
        bytes memory b = new bytes(1);
        bytes1 temp = bytes1(input);
        b[0] = temp;
        return b;
    }

    function readUint8(bytes memory self, uint idx) internal pure returns (uint8 ret) {
        require(idx + 1 <= self.length);
        assembly {
            ret := and(mload(add(add(self, 1), idx)), 0xFF)
        }
    }

    function readUint16(bytes memory self, uint idx) internal pure returns (uint16 ret) {
        require(idx + 2 <= self.length);
        assembly {
            ret := and(mload(add(add(self, 2), idx)), 0xFFFF)
        }
    }

    function readUint32(bytes memory self, uint idx) internal pure returns (uint32 ret) {
        require(idx + 4 <= self.length);
        assembly {
            ret := and(mload(add(add(self, 4), idx)), 0xFFFFFFFF)
        }
    }

    function readUint64(bytes memory self, uint idx) internal pure returns (uint64 ret) {
        require(idx + 8 <= self.length);
        assembly {
            ret := and(mload(add(add(self, 8), idx)), 0xFFFFFFFFFFFFFFFF)
        }
    }

    function readBytes32(bytes memory self, uint idx) internal pure returns (bytes32 ret) {
        require(idx + 32 <= self.length);
        assembly {
            ret := mload(add(add(self, 32), idx))
        }
    }

    function reverseUint64(uint64 self) internal pure returns (uint64) {

        bytes8 a = bytes8(self);
        bytes memory bytesString = new bytes(8);

        bytesString[0] = a[7];
        bytesString[1] = a[6];
        bytesString[2] = a[5];
        bytesString[3] = a[4];
        bytesString[4] = a[3];
        bytesString[5] = a[2];
        bytesString[6] = a[1];
        bytesString[7] = a[0];

        uint64 output = readUint64(bytesString, 0);
        return output;
    }

    function reverseUint32(uint32 self) internal pure returns (uint32) {

        bytes4 a = bytes4(self);
        bytes memory bytesString = new bytes(4);

        bytesString[0] = a[3];
        bytesString[1] = a[2];
        bytesString[2] = a[1];
        bytesString[3] = a[0];

        uint32 output = readUint32(bytesString, 0);
        return output;
    }

    function reverseUint16(uint16 self) internal pure returns (uint16) {

        bytes2 a = bytes2(self);
        bytes memory bytesString = new bytes(2);

        bytesString[0] = a[1];
        bytesString[1] = a[0];

        uint16 output = readUint16(bytesString, 0);
        return output;
    }

    function substring(bytes memory self, uint offset, uint len) internal pure returns(bytes memory) {
        require(offset + len <= self.length);

        bytes memory ret = new bytes(len);
        uint dest;
        uint src;

        assembly {
            dest := add(ret, 32)
            src := add(add(self, 32), offset)
        }
        memcpy(dest, src, len);

        return ret;
    }

    function fromHexChar(uint8 c) internal pure returns (uint8) {
        if (bytes1(c) >= bytes1('0') && bytes1(c) <= bytes1('9')) {
            return c - uint8(bytes1('0'));
        }
        if (bytes1(c) >= bytes1('a') && bytes1(c) <= bytes1('f')) {
            return 10 + c - uint8(bytes1('a'));
        }
        if (bytes1(c) >= bytes1('A') && bytes1(c) <= bytes1('F')) {
            return 10 + c - uint8(bytes1('A'));
        }
        revert("fromHexChar failed");
    }

    function hexStringToBytes(string memory s) internal pure returns (bytes memory) {
        bytes memory ss = bytes(s);
        require(ss.length%2 == 0); // length must be even
        bytes memory r = new bytes(ss.length/2);
        for (uint i=0; i<ss.length/2; ++i) {
            r[i] = bytes1(fromHexChar(uint8(ss[2*i])) * 16 +
                fromHexChar(uint8(ss[2*i+1])));
        }
        return r;
    }

    function hexStringToBytes32(string memory s) internal pure returns (bytes32 result) {
        bytes memory ss = bytes(s);
        require(ss.length == 64); // length must be even
        bytes memory r = new bytes(32);
        for (uint i=0; i<ss.length/2; ++i) {
            r[i] = bytes1(fromHexChar(uint8(ss[2*i])) * 16 +
                fromHexChar(uint8(ss[2*i+1])));
        }
        assembly {
            result := mload(add(r, 32))
        }
    }

    function memcpy(uint dest, uint src, uint len) private pure {
        for (; len >= 32; len -= 32) {
            assembly {
                mstore(dest, mload(src))
            }
            dest += 32;
            src += 32;
        }

        if (len == 0) {
            return;
        }
        
        // uint mask = SafeMath.pwr(256, (32 - len)) - 1;
        uint mask = 256 ** (32 - len) - 1;
        assembly {
            let srcpart := and(mload(src), not(mask))
            let destpart := and(mload(dest), mask)
            mstore(dest, or(destpart, srcpart))
        }
    }

    function strJoin(string memory _a, string memory _b, string memory _c, string memory _d, string memory _e) internal pure returns (string memory) {
        bytes memory _ba = bytes(_a);
        bytes memory _bb = bytes(_b);
        bytes memory _bc = bytes(_c);
        bytes memory _bd = bytes(_d);
        bytes memory _be = bytes(_e);
        string memory abcde = new string(_ba.length + _bb.length + _bc.length + _bd.length + _be.length);
        bytes memory babcde = bytes(abcde);
        uint k = 0;
        for (uint i = 0; i < _ba.length; i++) babcde[k++] = _ba[i];
        for (uint i = 0; i < _bb.length; i++) babcde[k++] = _bb[i];
        for (uint i = 0; i < _bc.length; i++) babcde[k++] = _bc[i];
        for (uint i = 0; i < _bd.length; i++) babcde[k++] = _bd[i];
        for (uint i = 0; i < _be.length; i++) babcde[k++] = _be[i];
        return string(babcde);
    }

    function strJoin(string memory _a, string memory _b, string memory _c, string memory _d) internal pure returns (string memory) {
        return strJoin(_a, _b, _c, _d, "");
    }

    function strJoin(string memory _a, string memory _b, string memory _c) internal pure returns (string memory) {
        return strJoin(_a, _b, _c, "", "");
    }

    function strJoin(string memory _a, string memory _b) internal pure returns (string memory) {
        return strJoin(_a, _b, "", "", "");
    }

    function uint2str(uint i) internal pure returns (string memory){
        if (i == 0) return "0";
        uint j = i;
        uint len;
        while (j != 0){
            len++;
            j /= 10;
        }
        bytes memory bstr = new bytes(len);
        uint k = len - 1;
        while (i != 0){
            bstr[k--] = bytes1(uint8(48 + i % 10));
            i /= 10;
        }
        return string(bstr);
    }

    function isContract(address account) internal view returns (bool) {
        uint256 size;
        assembly { size := extcodesize(account) }
        return size > 0;
    }

}
