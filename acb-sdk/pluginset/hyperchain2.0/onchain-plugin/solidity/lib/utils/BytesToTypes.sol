pragma solidity ^0.4.22;

/**
 * @title BytesToTypes
 * @dev The BytesToTypes contract converts the memory byte arrays to the standard solidity types
 * @author pouladzade@gmail.com
 */

library BytesToTypes {


    function bytesToAddress(uint _offst, bytes memory _input) internal pure returns (address _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToBool(uint _offst, bytes memory _input) internal pure returns (bool _output) {

        uint8 x;
        assembly {
            x := mload(add(_input, _offst))
        }
        x==0 ? _output = false : _output = true;
    }

    function getStringSize(uint _offst, bytes memory _input) internal pure returns(uint size){

        assembly{

            size := mload(add(_input,_offst))
            let chunk_count := add(div(size,32),1) // chunk_count = size/32 + 1

            if gt(mod(size,32),0) {// if size%32 > 0
                chunk_count := add(chunk_count,1)
            }

            size := mul(chunk_count,32)// first 32 bytes reseves for size in strings
        }
    }

    function bytesToString(uint _offst, bytes memory _input, bytes memory _output) internal  {

        uint size = 32;
        assembly {
            let loop_index:= 0

            let chunk_count

            size := mload(add(_input,_offst))
            chunk_count := add(div(size,32),1) // chunk_count = size/32 + 1

            if gt(mod(size,32),0) {
                chunk_count := add(chunk_count,1)  // chunk_count++
            }


            loop:
            mstore(add(_output,mul(loop_index,32)),mload(add(_input,_offst)))
            _offst := sub(_offst,32)           // _offst -= 32
            loop_index := add(loop_index,1)

            jumpi(loop , lt(loop_index , chunk_count))

        }
    }

    function bytesToVarBytes(uint _offst, bytes memory _input) internal pure returns (bytes memory) {

        uint len = bytesToUint32(_offst, _input);
        _offst -= 4;
        require(
            len <= _offst,
            "bytesToVarBytes: offset less than length of body"
        );

        _offst -= len;

        bytes memory buff;
        assembly {
            switch iszero(len)
            case 0 {
                buff := mload(0x40)
                let lengthmod := and(len, 31)
                let buff_cnt := add(add(buff, lengthmod), mul(0x20, iszero(lengthmod)))
                let end := add(buff_cnt, len)

                for {
                    let input_cnt := add(add(add(_input, lengthmod), mul(0x20, iszero(lengthmod))), _offst)
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

    function bytesToBytes32(uint _offst, bytes memory  _input) internal pure returns (bytes32 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        // mstore(_output , add(_input, _offst))
        // mstore(add(_output,32) , add(add(_input, _offst),32))
        }
    }

    function bytesToInt8(uint _offst, bytes memory  _input) internal pure returns (int8 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt16(uint _offst, bytes memory _input) internal pure returns (int16 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt24(uint _offst, bytes memory _input) internal pure returns (int24 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt32(uint _offst, bytes memory _input) internal pure returns (int32 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt40(uint _offst, bytes memory _input) internal pure returns (int40 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt48(uint _offst, bytes memory _input) internal pure returns (int48 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt56(uint _offst, bytes memory _input) internal pure returns (int56 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt64(uint _offst, bytes memory _input) internal pure returns (int64 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt72(uint _offst, bytes memory _input) internal pure returns (int72 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt80(uint _offst, bytes memory _input) internal pure returns (int80 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt88(uint _offst, bytes memory _input) internal pure returns (int88 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt96(uint _offst, bytes memory _input) internal pure returns (int96 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt104(uint _offst, bytes memory _input) internal pure returns (int104 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt112(uint _offst, bytes memory _input) internal pure returns (int112 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt120(uint _offst, bytes memory _input) internal pure returns (int120 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt128(uint _offst, bytes memory _input) internal pure returns (int128 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt136(uint _offst, bytes memory _input) internal pure returns (int136 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt144(uint _offst, bytes memory _input) internal pure returns (int144 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt152(uint _offst, bytes memory _input) internal pure returns (int152 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt160(uint _offst, bytes memory _input) internal pure returns (int160 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt168(uint _offst, bytes memory _input) internal pure returns (int168 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt176(uint _offst, bytes memory _input) internal pure returns (int176 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt184(uint _offst, bytes memory _input) internal pure returns (int184 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt192(uint _offst, bytes memory _input) internal pure returns (int192 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt200(uint _offst, bytes memory _input) internal pure returns (int200 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt208(uint _offst, bytes memory _input) internal pure returns (int208 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt216(uint _offst, bytes memory _input) internal pure returns (int216 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt224(uint _offst, bytes memory _input) internal pure returns (int224 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt232(uint _offst, bytes memory _input) internal pure returns (int232 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt240(uint _offst, bytes memory _input) internal pure returns (int240 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt248(uint _offst, bytes memory _input) internal pure returns (int248 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToInt256(uint _offst, bytes memory _input) internal pure returns (int256 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint8(uint _offst, bytes memory _input) internal pure returns (uint8 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint16(uint _offst, bytes memory _input) internal pure returns (uint16 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint24(uint _offst, bytes memory _input) internal pure returns (uint24 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint32(uint _offst, bytes memory _input) internal pure returns (uint32 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint40(uint _offst, bytes memory _input) internal pure returns (uint40 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint48(uint _offst, bytes memory _input) internal pure returns (uint48 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint56(uint _offst, bytes memory _input) internal pure returns (uint56 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint64(uint _offst, bytes memory _input) internal pure returns (uint64 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint72(uint _offst, bytes memory _input) internal pure returns (uint72 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint80(uint _offst, bytes memory _input) internal pure returns (uint80 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint88(uint _offst, bytes memory _input) internal pure returns (uint88 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint96(uint _offst, bytes memory _input) internal pure returns (uint96 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint104(uint _offst, bytes memory _input) internal pure returns (uint104 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint112(uint _offst, bytes memory _input) internal pure returns (uint112 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint120(uint _offst, bytes memory _input) internal pure returns (uint120 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint128(uint _offst, bytes memory _input) internal pure returns (uint128 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint136(uint _offst, bytes memory _input) internal pure returns (uint136 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint144(uint _offst, bytes memory _input) internal pure returns (uint144 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint152(uint _offst, bytes memory _input) internal pure returns (uint152 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint160(uint _offst, bytes memory _input) internal pure returns (uint160 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint168(uint _offst, bytes memory _input) internal pure returns (uint168 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint176(uint _offst, bytes memory _input) internal pure returns (uint176 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint184(uint _offst, bytes memory _input) internal pure returns (uint184 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint192(uint _offst, bytes memory _input) internal pure returns (uint192 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint200(uint _offst, bytes memory _input) internal pure returns (uint200 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint208(uint _offst, bytes memory _input) internal pure returns (uint208 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint216(uint _offst, bytes memory _input) internal pure returns (uint216 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint224(uint _offst, bytes memory _input) internal pure returns (uint224 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint232(uint _offst, bytes memory _input) internal pure returns (uint232 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint240(uint _offst, bytes memory _input) internal pure returns (uint240 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint248(uint _offst, bytes memory _input) internal pure returns (uint248 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

    function bytesToUint256(uint _offst, bytes memory _input) internal pure returns (uint256 _output) {

        assembly {
            _output := mload(add(_input, _offst))
        }
    }

}