pragma solidity ^0.4.22;
pragma experimental ABIEncoderV2;

import "./utils.sol";
import "./TLVUtils.sol";

library AMLib {
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

    struct AuthMessage {
        uint32 version;
        bytes32 author;
        uint32 protocolType;
        bytes body;
    }

    function decodeProof(bytes rawProof) internal pure returns (string memory, bytes memory) {
        Proof memory proof = _decodeProofFromBytes(rawProof);

        bytes memory msgBody = _decodeMsgBodyFromUDAGResp(proof.rawRespBody);

        return (proof.senderDomain, msgBody);
    }

    function _decodeProofFromBytes(bytes memory rawData) internal pure returns (Proof memory) {
        Proof memory proof;
        uint offset = 6;
        while (offset < rawData.length)
        {
            TLVUtils.TLVItem memory item;
            (item, offset) = TLVUtils.parseTLVItem(rawData, offset);

            if (item.tag == TLV_PROOF_REQUEST) {
                proof.req = _decodeRequestFromBytes(item.value);
            } else if (item.tag == TLV_PROOF_RESPONSE_BODY) {
                proof.rawRespBody = item.value; ////
            } else if (item.tag == TLV_PROOF_ERROR_CODE) {
                proof.errorCode = utils.reverseUint32(utils.readUint32(item.value, 0));
            } else if (item.tag == TLV_PROOF_ERROR_MSG) {
                proof.errorMsg = string(item.value);
            } else if (item.tag == TLV_PROOF_SENDER_DOMAIN) {
                proof.senderDomain = string(item.value);  ////
            } else if (item.tag == TLV_PROOF_VERSION) {
                proof.version = utils.reverseUint16(utils.readUint16(item.value, 0));
            }
        }
        return proof;
    }

    function _decodeRequestFromBytes(bytes memory rawData) internal pure returns (Request memory) {
        Request memory req;
        uint offset = 6;
        while (offset < rawData.length)
        {
            TLVUtils.TLVItem memory item;
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
        uint32 l = utils.reverseUint32(utils.readUint32(rawData, 8));

        require(
            rawData.length >= 12 + l,
            "length of udag resp less than the length of msg body"
        );

        return utils.sliceBytes(12, rawData, l);
    }
}


