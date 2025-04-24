// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "../utils/BytesToTypes.sol";
import "../utils/TypesToBytes.sol";
import "../utils/SizeOf.sol";
import "../utils/Utils.sol";
import "../utils/TLVUtils.sol";

struct MessageFromRelayer {
    bytes hints;
    bytes proofData;
}

struct Proof {
    Request req;
    bytes rawRespBody;
    uint32 errorCode;
    string errorMsg;
    string senderDomain;
    uint16 version;
}

struct Request {
    string reqID;
    bytes rawReqBody;
}

struct AuthMessage {
    uint32 version;
    bytes32 author;
    uint32 protocolType;
    bytes body;
}

library AMLib {

    uint16 constant TLV_PROOF_PUBKEY_HASH = 0;
    uint16 constant TLV_PROOF_REQUEST_ID = 1;
    uint16 constant TLV_PROOF_REQUEST_BODY = 2;
    uint16 constant TLV_PROOF_SIGNATURE_TYPE = 3;
    uint16 constant TLV_PROOF_REQUEST = 4;
    uint16 constant TLV_PROOF_RESPONSE_BODY = 5;
    uint16 constant TLV_PROOF_RESPONSE_SIGNATURE = 6;
    uint16 constant TLV_PROOF_ERROR_CODE = 7;
    uint16 constant TLV_PROOF_ERROR_MSG = 8;
    uint16 constant TLV_PROOF_SENDER_DOMAIN = 9;
    uint16 constant TLV_PROOF_VERSION = 10;

    uint16 constant VERSION_SIMPLE_PROOF = 1;

    function decodeMessageFromRelayer(bytes memory rawMessage) internal pure returns (MessageFromRelayer memory msgFromRelayer) {
        uint offset = 0;

        bytes memory hintsLenBytes = Utils.bytesCopy(offset, rawMessage, 4); // 4 bytes for hints length
        offset += 4;
        uint32 hintsLen = BytesToTypes.bytesToUint32(4, hintsLenBytes); // hints length
        bytes memory hints = Utils.bytesCopy(offset, rawMessage, hintsLen);
        offset += hintsLen;

        bytes memory proofLenBytes = Utils.bytesCopy(offset, rawMessage, 4); // 4 bytes for proof length
        offset += 4;
        uint32 proofLen = BytesToTypes.bytesToUint32(4, proofLenBytes); // proof length
        bytes memory proof = Utils.bytesCopy(offset, rawMessage, proofLen);
        offset += proofLen;

        return MessageFromRelayer({hints: hints, proofData: proof});
    }

    function _decodeProof(bytes memory rawProof) internal pure returns (string memory, bytes memory) {
        Proof memory proof = _decodeProofFromBytes(rawProof);

        bytes memory msgBody = _decodeMsgBodyFromUDAGResp(proof.rawRespBody);

        return (proof.senderDomain, msgBody);
    }

    function _decodeProofFromBytes(bytes memory rawData) internal pure returns (Proof memory) {
        Proof memory proof;
        uint offset = 6;
        while (offset < rawData.length)
        {
            TLVItem memory item;
            (item, offset) = TLVUtils.parseTLVItem(rawData, offset);

            if (item.tag == TLV_PROOF_REQUEST) {
                proof.req = _decodeRequestFromBytes(item.value);
            } else if (item.tag == TLV_PROOF_RESPONSE_BODY) {
                proof.rawRespBody = item.value;
            } else if (item.tag == TLV_PROOF_ERROR_CODE) {
                proof.errorCode = Utils.reverseUint32(Utils.readUint32(item.value, 0));
            } else if (item.tag == TLV_PROOF_ERROR_MSG) {
                proof.errorMsg = string(item.value);
            } else if (item.tag == TLV_PROOF_SENDER_DOMAIN) {
                proof.senderDomain = string(item.value);
            } else if (item.tag == TLV_PROOF_VERSION) {
                proof.version = Utils.reverseUint16(Utils.readUint16(item.value, 0));
            }
        }
        return proof;
    }

    function _decodeRequestFromBytes(bytes memory rawData) internal pure returns (Request memory) {
        Request memory req;
        uint offset = 6;
        while (offset < rawData.length)
        {
            TLVItem memory item;
            (item, offset) = TLVUtils.parseTLVItem(rawData, offset);

            if (item.tag == TLV_PROOF_REQUEST_ID) {
                req.reqID = string(item.value);
            } else if (item.tag == TLV_PROOF_REQUEST_BODY) {
                req.rawReqBody = item.value;
            }
        }
        return req;
    }

    function _decodeMsgBodyFromUDAGResp(bytes memory rawData) internal pure returns (bytes memory) {
        require(
            rawData.length > 12,
            "illegal length of udag resp"
        );
        uint32 l = Utils.reverseUint32(Utils.readUint32(rawData, 8));

        require(
            rawData.length >= 12 + l,
            "length of udag resp less than the length of msg body"
        );

        return Utils.sliceBytes(12, rawData, l);
    }

    function decodeAuthMessage(bytes memory rawMessage) internal pure returns (AuthMessage memory) {
        uint32 version = decodeAuthMessageVersion(rawMessage);
        if (version == 1) {
            return decodeAuthMessageV1(rawMessage);
        } else if (version == 2) {
            return decodeAuthMessageV2(rawMessage);
        }

        revert("decodeAuthMessage: am version not support");
    }

    function decodeAuthMessageVersion(bytes memory rawMessage) internal pure returns (uint32) {
        return BytesToTypes.bytesToUint32(rawMessage.length, rawMessage);
    }

    function decodeAuthMessageV1(bytes memory rawMessage) internal pure returns (AuthMessage memory) {
        uint offset = rawMessage.length - SizeOf.sizeOfInt(32);

        bytes32 author = BytesToTypes.bytesToBytes32(offset, rawMessage);
        offset -= 32;

        uint32 protocolType = BytesToTypes.bytesToUint32(offset, rawMessage);
        offset -= SizeOf.sizeOfInt(32);

        bytes memory body = new bytes(BytesToTypes.getStringSize(offset, rawMessage));
        BytesToTypes.bytesToString(offset, rawMessage, body);
        offset -= SizeOf.sizeOfBytes(body);

        return AuthMessage({version: 1, author: author, protocolType: protocolType, body: body});
    }

    function decodeAuthMessageV2(bytes memory rawMessage) internal pure returns (AuthMessage memory) {
        uint offset = rawMessage.length - SizeOf.sizeOfInt(32);

        bytes32 author = BytesToTypes.bytesToBytes32(offset, rawMessage);
        offset -= 32;

        uint32 protocolType = BytesToTypes.bytesToUint32(offset, rawMessage);
        offset -= SizeOf.sizeOfInt(32);

        bytes memory body = BytesToTypes.bytesToVarBytes(offset, rawMessage);

        return AuthMessage({version: 2, author: author, protocolType: protocolType, body: body});
    }

    function encodeAuthMessage(AuthMessage memory message) internal pure returns (bytes memory) {
        if (message.version == 1) {
            return encodeAuthMessageV1(message);
        } else if (message.version == 2) {
            return encodeAuthMessageV2(message);
        }

        revert("encodeAuthMessage: am version not support");
    }

    function encodeAuthMessageV1(AuthMessage memory message) internal pure returns (bytes memory) {
        require(
            message.version == 1, 
            "encodeAuthMessageV1: wrong version"
        );
        
        uint256 len = SizeOf.sizeOfBytes(message.body) + 4 + 32 + 4;

        bytes memory pkg = new bytes(len);
        uint offset = len;

        TypesToBytes.uint32ToBytes(offset, message.version, pkg);
        offset -= SizeOf.sizeOfInt(32);

        TypesToBytes.bytes32ToBytes(offset, message.author, pkg);
        offset -= SizeOf.sizeOfBytes32();

        TypesToBytes.uint32ToBytes(offset, message.protocolType, pkg);
        offset -= SizeOf.sizeOfInt(32);

        TypesToBytes.stringToBytes(offset, message.body, pkg);
        offset -= SizeOf.sizeOfBytes(message.body);

        return pkg;
    }

    function encodeAuthMessageV2(AuthMessage memory message) internal pure returns (bytes memory) {
        require(
            message.version == 2, 
            "encodeAuthMessageV2: wrong version"
        );
        
        require(
            message.body.length <= 0xFFFFFFFF,
            "encodeAuthMessageV2: body length overlimit"
        );

        uint256 len = message.body.length + 4 + 4 + 32 + 4;

        bytes memory pkg = new bytes(len);
        uint offset = len;

        TypesToBytes.uint32ToBytes(offset, message.version, pkg);
        offset -= SizeOf.sizeOfInt(32);

        TypesToBytes.bytes32ToBytes(offset, message.author, pkg);
        offset -= SizeOf.sizeOfBytes32();

        TypesToBytes.uint32ToBytes(offset, message.protocolType, pkg);
        offset -= SizeOf.sizeOfInt(32);

        TypesToBytes.varBytesToBytes(offset, message.body, pkg);

        return pkg;
    }

    function encodeAddressIntoCrossChainID(address _address) internal pure returns (bytes32) {
        bytes32 id = TypesToBytes.addressToBytes32(_address);
        return id;
    }
}
