// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "../commons/AcbCommons.sol";
import "../../@openzeppelin/contracts/utils/Strings.sol";

// tags for PtcTrustRoot
uint16 constant PTC_TRUST_ROOT_ISSUER_BCDNS_DOMAIN_SPACE = 0;
uint16 constant PTC_TRUST_ROOT_PTC_CROSSCHAIN_CERT = 1;
uint16 constant PTC_TRUST_ROOT_NETWORK_INFO = 2;
uint16 constant PTC_TRUST_ROOT_VA_MAP = 3;
uint16 constant PTC_TRUST_ROOT_SIG_ALGO = 4;
uint16 constant PTC_TRUST_ROOT_SIG = 5;

struct PtcTrustRoot {
    string issuerBcdnsDomainSpace;
    CrossChainCertificate ptcCrossChainCert;
    bytes networkInfo;
    TLVItemMapValueStream verifyAnchorMap;
    string signAlgo;
    bytes sig;
}
// tags for PTCVerifyAnchor
uint16 constant PTC_VA_VERSION = 0;
uint16 constant PTC_VA_ANCHOR = 1;

struct PTCVerifyAnchor {
    bytes version;
    bytes anchor;
}

// tags for TpBta
uint16 constant TPBTA_STRUCT_VERSION = 0;
uint16 constant TPBTA_VERSION = 1;
uint16 constant TPBTA_PTC_VA_VERSION = 2;
uint16 constant TPBTA_SIGNER_PTC_CREDENTIAL_SUBJECT = 3;
uint16 constant TPBTA_CROSSCHAIN_LANE = 4;
uint16 constant TPBTA_BTA_ENDORSED_SUBJECT_VERSION = 5;
uint16 constant TPBTA_UCP_MSG_DIGEST_HASH_ALGO = 6;
uint16 constant TPBTA_ENDORSE_ROOT = 7;
uint16 constant TPBTA_ENDORSE_PROOF = 0xff;

struct TpBta {
    uint32 version;
    uint32 tpbtaVersion;
    bytes ptcVerifyAnchorVersion;
    PTCCredentialSubject signerPtcCredentialSubject;
    CrossChainLane crossChainLane;
    uint32 btaSubjectVersion;
    string ucpMessageHashAlgo;
    bytes endorseRoot;
    bytes endorseProof;
}

// tags for ThirdPartyResp
uint16 constant PTC_RESP_FIELD_BODY = 0;
uint16 constant PTC_RESP_FIELD_AMEXT_CALL_SUCCESS = 1;
uint16 constant PTC_RESP_FIELD_AMEXT_EXEC_SUCCESS = 2;
uint16 constant PTC_RESP_FIELD_AMEXT_EXEC_OUTPUT = 3;

struct ThirdPartyResp {
    bytes body;
}

// tags for ThirdPartyProof
uint16 constant TP_PROOF_TPBTA_VERSION = 0x0100;
uint16 constant TLV_PROOF_TPBTA_CROSSCHAIN_LANE = 0x0101;
// - tags from ODATS
uint16 constant TLV_ORACLE_PUBKEY_HASH = 0;
uint16 constant TLV_ORACLE_REQUEST_ID = 1;
uint16 constant TLV_ORACLE_REQUEST_BODY = 2;
uint16 constant TLV_ORACLE_SIGNATURE_TYPE = 3;
uint16 constant TLV_ORACLE_REQUEST = 4;
uint16 constant TLV_ORACLE_RESPONSE_BODY = 5;
uint16 constant TLV_ORACLE_RESPONSE_SIGNATURE = 6;
uint16 constant TLV_ORACLE_ERROR_CODE = 7;
uint16 constant TLV_ORACLE_ERROR_MSG = 8;
uint16 constant TLV_PROOF_SENDER_DOMAIN = 9;
uint16 constant TP_PROOF_RAW_PROOF = 0x01ff;

struct ThirdPartyProof {
    ThirdPartyResp resp;
    CrossChainLane tpbtaCrossChainLane;
    uint32 tpbtaVersion;
    bytes rawProof;
}

library PtcLib {
    using AcbCommons for CrossChainCertificate;
    using AcbCommons for PTCCredentialSubject;
    using AcbCommons for CrossChainLane;
    using AcbCommons for ObjectIdentity;

    using TLVUtils for TLVPacket;
    using TLVUtils for TLVItem;

    using Strings for string;

    using PtcLib for PtcTrustRoot;
    using PtcLib for ThirdPartyResp;

    function encode(PTCVerifyAnchor memory self)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items = new TLVItem[](2);
        items[0] = TLVUtils.fromBigIntegerToTLVItem(PTC_VA_VERSION, self.version);
        items[1] = TLVUtils.fromBytesToTLVItem(PTC_VA_ANCHOR, self.anchor);

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodePTCVerifyAnchorFrom(bytes memory rawData)
        internal
        pure
        returns (PTCVerifyAnchor memory)
    {
        PTCVerifyAnchor memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == PTC_VA_VERSION) {
                result.version = currItem.toBigInteger();
            } else if (currItem.tag == PTC_VA_ANCHOR) {
                result.anchor = currItem.toBytes();
            }
        }
        return result;
    }

    function encode(ThirdPartyResp memory resp)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items = new TLVItem[](1);
        items[0] = TLVUtils.fromBytesToTLVItem(PTC_RESP_FIELD_BODY, resp.body);

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeThirdPartyRespFrom(bytes memory rawData)
        internal
        pure
        returns (ThirdPartyResp memory)
    {
        ThirdPartyResp memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == PTC_RESP_FIELD_BODY) {
                result.body = currItem.toBytes();
            }
        }
        return result;
    }

    function encode(ThirdPartyProof memory proof)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items = new TLVItem[](4);
        items[0] = TLVUtils.fromUint32ToTLVItem(TP_PROOF_TPBTA_VERSION, proof.tpbtaVersion);
        items[1] = TLVUtils.fromBytesToTLVItem(TLV_ORACLE_RESPONSE_BODY, proof.resp.encode());
        items[3] = TLVUtils.fromBytesToTLVItem(TLV_PROOF_TPBTA_CROSSCHAIN_LANE, proof.tpbtaCrossChainLane.encode());
        items[2] = TLVUtils.fromBytesToTLVItem(TP_PROOF_RAW_PROOF, proof.rawProof);

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function getEncodedToSign(ThirdPartyProof memory proof)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items = new TLVItem[](3);
        items[0] = TLVUtils.fromUint32ToTLVItem(TP_PROOF_TPBTA_VERSION, proof.tpbtaVersion);
        items[1] = TLVUtils.fromBytesToTLVItem(TLV_ORACLE_RESPONSE_BODY, proof.resp.encode());
        items[2] = TLVUtils.fromBytesToTLVItem(TLV_PROOF_TPBTA_CROSSCHAIN_LANE, proof.tpbtaCrossChainLane.encode());

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeThirdPartyProofFrom(bytes memory rawData)
        internal
        pure
        returns (ThirdPartyProof memory)
    {
        ThirdPartyProof memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TLV_ORACLE_RESPONSE_BODY) {
                result.resp = decodeThirdPartyRespFrom(currItem.toBytes());
            } else if (currItem.tag == TLV_PROOF_TPBTA_CROSSCHAIN_LANE) {
                result.tpbtaCrossChainLane = AcbCommons.decodeCrossChainLaneFrom(currItem.toBytes());
            } else if (currItem.tag == TP_PROOF_TPBTA_VERSION) {
                result.tpbtaVersion = currItem.toUint32();
            } else if (currItem.tag == TP_PROOF_RAW_PROOF) {
                result.rawProof = currItem.toBytes();
            }
        }
        return result;
    }

    function encode(TpBta memory tpbta) internal pure returns (bytes memory) {
        TLVItem[] memory items = new TLVItem[](9);
        items[0] = TLVUtils.fromUint32ToTLVItem(
            TPBTA_STRUCT_VERSION,
            tpbta.version
        );
        items[1] = TLVUtils.fromUint32ToTLVItem(
            TPBTA_VERSION,
            tpbta.tpbtaVersion
        );
        items[2] = TLVUtils.fromBytesToTLVItem(
            TPBTA_PTC_VA_VERSION,
            tpbta.ptcVerifyAnchorVersion
        );
        items[3] = TLVUtils.fromBytesToTLVItem(
            TPBTA_SIGNER_PTC_CREDENTIAL_SUBJECT,
            tpbta.signerPtcCredentialSubject.encode()
        );
        items[4] = TLVUtils.fromBytesToTLVItem(
            TPBTA_CROSSCHAIN_LANE,
            tpbta.crossChainLane.encode()
        );
        items[5] = TLVUtils.fromUint32ToTLVItem(
            TPBTA_BTA_ENDORSED_SUBJECT_VERSION,
            tpbta.btaSubjectVersion
        );
        items[6] = TLVUtils.fromStringToTLVItem(
            TPBTA_UCP_MSG_DIGEST_HASH_ALGO,
            tpbta.ucpMessageHashAlgo
        );
        items[7] = TLVUtils.fromBytesToTLVItem(
            TPBTA_ENDORSE_ROOT,
            tpbta.endorseRoot
        );
        items[8] = TLVUtils.fromBytesToTLVItem(
            TPBTA_ENDORSE_PROOF,
            tpbta.endorseProof
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function getEncodedToSign(TpBta memory tpbta)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items = new TLVItem[](8);
        items[0] = TLVUtils.fromUint32ToTLVItem(
            TPBTA_STRUCT_VERSION,
            tpbta.version
        );
        items[1] = TLVUtils.fromUint32ToTLVItem(
            TPBTA_VERSION,
            tpbta.tpbtaVersion
        );
        items[2] = TLVUtils.fromBigIntegerToTLVItem(
            TPBTA_PTC_VA_VERSION,
            tpbta.ptcVerifyAnchorVersion
        );
        items[3] = TLVUtils.fromBytesToTLVItem(
            TPBTA_SIGNER_PTC_CREDENTIAL_SUBJECT,
            tpbta.signerPtcCredentialSubject.encode()
        );
        items[4] = TLVUtils.fromBytesToTLVItem(
            TPBTA_CROSSCHAIN_LANE,
            tpbta.crossChainLane.encode()
        );
        items[5] = TLVUtils.fromUint32ToTLVItem(
            TPBTA_BTA_ENDORSED_SUBJECT_VERSION,
            tpbta.btaSubjectVersion
        );
        items[6] = TLVUtils.fromStringToTLVItem(
            TPBTA_UCP_MSG_DIGEST_HASH_ALGO,
            tpbta.ucpMessageHashAlgo
        );
        items[7] = TLVUtils.fromBytesToTLVItem(
            TPBTA_ENDORSE_ROOT,
            tpbta.endorseRoot
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeTpBtaFrom(bytes memory rawData)
        internal
        pure
        returns (TpBta memory)
    {
        TpBta memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TPBTA_STRUCT_VERSION) {
                result.version = currItem.toUint32();
            } else if (currItem.tag == TPBTA_VERSION) {
                result.tpbtaVersion = currItem.toUint32();
            } else if (currItem.tag == TPBTA_PTC_VA_VERSION) {
                result.ptcVerifyAnchorVersion = currItem.toBigInteger();
            } else if (currItem.tag == TPBTA_SIGNER_PTC_CREDENTIAL_SUBJECT) {
                result.signerPtcCredentialSubject = AcbCommons
                    .decodePTCCredentialSubjectFrom(currItem.toBytes());
            } else if (currItem.tag == TPBTA_CROSSCHAIN_LANE) {
                result.crossChainLane = AcbCommons.decodeCrossChainLaneFrom(
                    currItem.toBytes()
                );
            } else if (currItem.tag == TPBTA_BTA_ENDORSED_SUBJECT_VERSION) {
                result.btaSubjectVersion = currItem.toUint32();
            } else if (currItem.tag == TPBTA_UCP_MSG_DIGEST_HASH_ALGO) {
                result.ucpMessageHashAlgo = currItem.toString();
            } else if (currItem.tag == TPBTA_ENDORSE_ROOT) {
                result.endorseRoot = currItem.toBytes();
            } else if (currItem.tag == TPBTA_ENDORSE_PROOF) {
                result.endorseProof = currItem.toBytes();
            }
        }
        return result;
    }

    function encode(PtcTrustRoot memory ptr)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items = new TLVItem[](6);
        items[0] = TLVUtils.fromStringToTLVItem(
            PTC_TRUST_ROOT_ISSUER_BCDNS_DOMAIN_SPACE,
            ptr.issuerBcdnsDomainSpace
        );
        items[1] = TLVUtils.fromBytesToTLVItem(
            PTC_TRUST_ROOT_PTC_CROSSCHAIN_CERT,
            ptr.ptcCrossChainCert.encode()
        );
        items[2] = TLVUtils.fromBytesToTLVItem(
            PTC_TRUST_ROOT_NETWORK_INFO,
            ptr.networkInfo
        );
        items[3] = TLVUtils.fromBytesToTLVItem(
            PTC_TRUST_ROOT_VA_MAP,
            ptr.verifyAnchorMap.value
        );
        items[4] = TLVUtils.fromStringToTLVItem(
            PTC_TRUST_ROOT_SIG_ALGO,
            ptr.signAlgo
        );
        items[5] = TLVUtils.fromBytesToTLVItem(PTC_TRUST_ROOT_SIG, ptr.sig);

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function getEncodedToSign(PtcTrustRoot memory ptr)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items = new TLVItem[](5);
        items[0] = TLVUtils.fromStringToTLVItem(
            PTC_TRUST_ROOT_ISSUER_BCDNS_DOMAIN_SPACE,
            ptr.issuerBcdnsDomainSpace
        );
        items[1] = TLVUtils.fromBytesToTLVItem(
            PTC_TRUST_ROOT_PTC_CROSSCHAIN_CERT,
            ptr.ptcCrossChainCert.encode()
        );
        items[2] = TLVUtils.fromBytesToTLVItem(
            PTC_TRUST_ROOT_NETWORK_INFO,
            ptr.networkInfo
        );
        items[3] = TLVUtils.fromBytesToTLVItem(
            PTC_TRUST_ROOT_VA_MAP,
            ptr.verifyAnchorMap.value
        );
        items[4] = TLVUtils.fromStringToTLVItem(
            PTC_TRUST_ROOT_SIG_ALGO,
            ptr.signAlgo
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function getKey(PtcTrustRoot memory ptr) internal pure returns (bytes32) {
        return keccak256(ptr.ptcCrossChainCert.getCertOwnerOid().encode());
    }

    function decodePtcTrustRootFrom(bytes memory rawData)
        internal
        pure
        returns (PtcTrustRoot memory)
    {
        PtcTrustRoot memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == PTC_TRUST_ROOT_ISSUER_BCDNS_DOMAIN_SPACE) {
                result.issuerBcdnsDomainSpace = currItem.toString();
            } else if (currItem.tag == TLV_TYPE_OID_RAW_ID) {
                result.ptcCrossChainCert = AcbCommons
                    .decodeCrossChainCertificateFrom(currItem.toBytes());
            } else if (currItem.tag == PTC_TRUST_ROOT_NETWORK_INFO) {
                result.networkInfo = currItem.toBytes();
            } else if (currItem.tag == PTC_TRUST_ROOT_VA_MAP) {
                result.verifyAnchorMap = currItem.toMapValueStream();
            } else if (currItem.tag == PTC_TRUST_ROOT_SIG_ALGO) {
                result.signAlgo = currItem.toString();
            } else if (currItem.tag == PTC_TRUST_ROOT_SIG) {
                result.sig = currItem.toBytes();
            }
        }
        return result;
    }

    function verifySig(
        PtcTrustRoot memory ptr
    ) internal view returns (bool) {
        return
            AcbCommons.verifySig(
                ptr.signAlgo,
                ptr.ptcCrossChainCert.getRawPublicKey(),
                ptr.getEncodedToSign(),
                ptr.sig
            );
    }
}
