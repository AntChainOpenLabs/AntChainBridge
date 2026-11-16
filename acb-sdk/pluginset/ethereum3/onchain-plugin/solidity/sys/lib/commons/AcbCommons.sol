// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "../utils/TLVUtils.sol";
import "../../@openzeppelin/contracts/utils/Strings.sol";
import "../../@openzeppelin/contracts/utils/cryptography/SignatureChecker.sol";

enum CrossChainCertificateTypeEnum {
    BCDNS_TRUST_ROOT_CERTIFICATE,
    DOMAIN_NAME_CERTIFICATE,
    PROOF_TRANSFORMATION_COMPONENT_CERTIFICATE,
    RELAYER_CERTIFICATE
}

// tags for IssueProof
uint16 constant TLV_TYPE_CERT_VERSION = 0x00;
uint16 constant TLV_TYPE_CERT_ID = 0x01;
uint16 constant TLV_TYPE_CERT_TYPE = 0x02;
uint16 constant TLV_TYPE_CERT_ISSUER = 0x03;
uint16 constant TLV_TYPE_CERT_ISSUANCE_DATE = 0x04;
uint16 constant TLV_TYPE_CERT_EXPIRATION_DATE = 0x05;
uint16 constant TLV_TYPE_CERT_CREDENTIAL_SUBJECT = 0x06;
uint16 constant TLV_TYPE_CERT_PROOF = 0x07;

struct CrossChainCertificate {
    string version;
    string id;
    CrossChainCertificateTypeEnum certType;
    ObjectIdentity issuer;
    uint64 issuanceDate;
    uint64 expirationDate;
    bytes credentialSubject;
    CrossChainCertIssueProof proof;
}

// hash algo contants
string constant SHA2_256 = "SHA2-256";
string constant SHA3_256 = "SHA3-256";
string constant SM3 = "SM3";
string constant KECCAK_256 = "KECCAK-256";

// sign algo contants
string constant SHA256_WITH_RSA = "SHA256withRSA";
string constant SHA256_WITH_ECDSA = "SHA256withECDSA";
string constant SM3_WITH_SM2 = "SM3withSM2";
string constant ED25519 = "ED25519";
string constant KECCAK256_WITH_SECP256K1 = "Keccak256WithSecp256k1";

// tags for IssueProof
uint16 constant TLV_TYPE_ISSUE_PROOF_HASH_ALGO = 0x00;
uint16 constant TLV_TYPE_ISSUE_PROOF_CERT_HASH = 0x01;
uint16 constant TLV_TYPE_ISSUE_PROOF_SIG_ALGO = 0x02;
uint16 constant TLV_TYPE_ISSUE_PROOF_RAW_PROOF = 0x03;

struct CrossChainCertIssueProof {
    string hashAlgo;
    bytes certHash;
    string sigAlgo;
    bytes rawProof;
}

enum ObjectIdentityType {
    X509_PUBLIC_KEY_INFO,
    BID
}

// tags for ObjectIdentity
uint16 constant TLV_TYPE_OID_TYPE = 0x00;
uint16 constant TLV_TYPE_OID_RAW_ID = 0x01;

struct ObjectIdentity {
    ObjectIdentityType oidType;
    bytes rawId;
}

enum PTCTypeEnum {
    EXTERNAL_VERIFIER,
    COMMITTEE,
    RELAY_CHAIN
}

// tags for PTCCredentialSubject
uint16 constant TLV_TYPE_PTC_CREDENTIAL_SUBJECT_VERSION = 0x0000;
uint16 constant TLV_TYPE_PTC_CREDENTIAL_SUBJECT_NAME = 0x0001;
uint16 constant TLV_TYPE_PTC_CREDENTIAL_SUBJECT_TYPE = 0x0002;
uint16 constant TLV_TYPE_PTC_CREDENTIAL_SUBJECT_APPLICANT = 0x0003;
uint16 constant TLV_TYPE_PTC_CREDENTIAL_SUBJECT_SUBJECT_INFO = 0x0004;

struct PTCCredentialSubject {
    string version;
    string name;
    PTCTypeEnum ptcType;
    ObjectIdentity applicant;
    bytes subjectInfo;
}

// tags for BCDNSTrustRootCredentialSubject
uint16 constant TLV_TYPE_NAME = 0x0000;
uint16 constant TLV_TYPE_BCDNS_ROOT_OWNER = 0x0001;
uint16 constant TLV_TYPE_SUBJECT_INFO = 0x0002;

struct BCDNSTrustRootCredentialSubject {
    string name;
    ObjectIdentity bcdnsRootOwner;
    bytes bcdnsRootSubjectInfo;
}

enum DomainNameTypeEnum {
    DOMAIN_NAME,
    DOMAIN_NAME_SPACE
}

// tags for DomainNameCredentialSubject
uint16 constant TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_VERSION = 0x0000;
uint16 constant TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_DOMAIN_NAME_TYPE = 0x0001;
uint16 constant TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_PARENT_DOMAIN_SPACE = 0x0002;
uint16 constant TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_DOMAIN_NAME = 0x0003;
uint16 constant TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_APPLICANT = 0x0004;
uint16 constant TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_SUBJECT = 0x0005;

struct DomainNameCredentialSubject {
    string version;
    DomainNameTypeEnum domainNameType;
    string parentDomainSpace;
    string domainName;
    ObjectIdentity applicant;
    bytes subject;
}

// tags for RelayerCredentialSubject
uint16 constant TLV_TYPE_RELAYER_CREDENTIAL_SUBJECT_VERSION = 0x0000;
uint16 constant TLV_TYPE_RELAYER_CREDENTIAL_SUBJECT_NAME = 0x0001;
uint16 constant TLV_TYPE_RELAYER_CREDENTIAL_SUBJECT_TYPE = 0x0002;
uint16 constant TLV_TYPE_RELAYER_CREDENTIAL_SUBJECT_APPLICANT = 0x0003;
uint16 constant TLV_TYPE_RELAYER_CREDENTIAL_SUBJECT_SUBJECT_INFO = 0x0004;

struct RelayerCredentialSubject {
    string version;
    string name;
    ObjectIdentity applicant;
    bytes subjectInfo;
}

// tags for CrossChainLane
uint16 constant TAG_CROSS_CHAIN_CHANNEL = 0;
uint16 constant TAG_SENDER_ID = 1;
uint16 constant TAG_RECEIVER_ID = 2;

struct CrossChainLane {
    CrossChainChannel channel;
    bytes32 senderId;
    bytes32 receiverId;
}

// tags for CrossChainChannel
uint16 constant TAG_SENDER_DOMAIN = 0;
uint16 constant TAG_RECEIVER_DOMAIN = 1;

struct CrossChainChannel {
    string senderDomain;
    string receiverDomain;
}

library AcbCommons {
    using AcbCommons for ObjectIdentity;
    using AcbCommons for ObjectIdentityType;
    using AcbCommons for CrossChainCertificateTypeEnum;
    using AcbCommons for CrossChainCertIssueProof;
    using AcbCommons for CrossChainCertificate;
    using AcbCommons for PTCTypeEnum;
    using AcbCommons for DomainNameTypeEnum;
    using AcbCommons for BCDNSTrustRootCredentialSubject;
    using AcbCommons for RelayerCredentialSubject;
    using AcbCommons for PTCCredentialSubject;
    using AcbCommons for DomainNameCredentialSubject;
    using AcbCommons for CrossChainChannel;
    using AcbCommons for CrossChainLane;

    using TLVUtils for TLVPacket;
    using TLVUtils for TLVItem;

    using Strings for string;

    function encode(CrossChainChannel memory ccChannel)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items;
        if (bytes(ccChannel.receiverDomain).length > 0) {
            items = new TLVItem[](2);
            items[0] = TLVUtils.fromStringToTLVItem(
                TAG_SENDER_DOMAIN,
                ccChannel.senderDomain
            );
            items[1] = TLVUtils.fromStringToTLVItem(
                TAG_RECEIVER_DOMAIN,
                ccChannel.receiverDomain
            );
        } else {
            items = new TLVItem[](1);
            items[0] = TLVUtils.fromStringToTLVItem(
                TAG_SENDER_DOMAIN,
                ccChannel.senderDomain
            );
        }

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeCrossChainChannelFrom(bytes memory rawData)
        internal
        pure
        returns (CrossChainChannel memory)
    {
        CrossChainChannel memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TAG_SENDER_DOMAIN) {
                result.senderDomain = currItem.toString();
            } else if (currItem.tag == TAG_RECEIVER_DOMAIN) {
                result.receiverDomain = currItem.toString();
            }
        }
        return result;
    }

    function encode(CrossChainLane memory lane)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items;
        if (lane.senderId == bytes32(0) && lane.receiverId == bytes32(0)) {
            items = new TLVItem[](1);
            items[0] = TLVUtils.fromBytesToTLVItem(
                TAG_CROSS_CHAIN_CHANNEL,
                lane.channel.encode()
            );
        } else if (lane.senderId != bytes32(0) && lane.receiverId != bytes32(0)) {
            items = new TLVItem[](3);
            items[0] = TLVUtils.fromBytesToTLVItem(
                TAG_CROSS_CHAIN_CHANNEL,
                lane.channel.encode()
            );
    
            bytes memory rawSenderId = new bytes(32);
            TypesToBytes.bytes32ToBytes(0x20, lane.senderId, rawSenderId);
            items[1] = TLVUtils.fromBytesToTLVItem(TAG_SENDER_ID, rawSenderId);

            bytes memory rawReceiverId = new bytes(32);
            TypesToBytes.bytes32ToBytes(0x20, lane.receiverId, rawReceiverId);
            items[2] = TLVUtils.fromBytesToTLVItem(TAG_RECEIVER_ID, rawReceiverId);
        } else {
            revert("invalid crosschain lane");
        }

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeCrossChainLaneFrom(bytes memory rawData)
        internal
        pure
        returns (CrossChainLane memory)
    {
        CrossChainLane memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TAG_CROSS_CHAIN_CHANNEL) {
                result.channel = decodeCrossChainChannelFrom(
                    currItem.toBytes()
                );
            } else if (currItem.tag == TAG_SENDER_ID) {
                bytes memory raw = currItem.toBytes();
                result.senderId = BytesToTypes.bytesToBytes32(raw.length, raw);
            } else if (currItem.tag == TAG_RECEIVER_ID) {
                bytes memory raw = currItem.toBytes();
                result.receiverId = BytesToTypes.bytesToBytes32(
                    raw.length,
                    raw
                );
            }
        }
        return result;
    }

    function encode(ObjectIdentity memory oid)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items = new TLVItem[](2);
        items[0] = TLVUtils.fromUint8ToTLVItem(
            TLV_TYPE_OID_TYPE,
            oid.oidType.value()
        );
        items[1] = TLVUtils.fromBytesToTLVItem(TLV_TYPE_OID_RAW_ID, oid.rawId);

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeObjectIdentityFrom(bytes memory rawData)
        internal
        pure
        returns (ObjectIdentity memory)
    {
        ObjectIdentity memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TLV_TYPE_OID_TYPE) {
                result.oidType = ObjectIdentityType(currItem.toUint8());
            } else if (currItem.tag == TLV_TYPE_OID_RAW_ID) {
                result.rawId = currItem.toBytes();
            }
        }
        return result;
    }

    function encode(CrossChainCertIssueProof memory proof)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items = new TLVItem[](4);
        items[0] = TLVUtils.fromStringToTLVItem(
            TLV_TYPE_ISSUE_PROOF_HASH_ALGO,
            proof.hashAlgo
        );
        items[1] = TLVUtils.fromBytesToTLVItem(
            TLV_TYPE_ISSUE_PROOF_CERT_HASH,
            proof.certHash
        );
        items[2] = TLVUtils.fromStringToTLVItem(
            TLV_TYPE_ISSUE_PROOF_SIG_ALGO,
            proof.sigAlgo
        );
        items[3] = TLVUtils.fromBytesToTLVItem(
            TLV_TYPE_ISSUE_PROOF_RAW_PROOF,
            proof.rawProof
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeCrossChainCertIssueProofFrom(bytes memory rawData)
        internal
        pure
        returns (CrossChainCertIssueProof memory)
    {
        CrossChainCertIssueProof memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TLV_TYPE_ISSUE_PROOF_HASH_ALGO) {
                result.hashAlgo = currItem.toString();
            } else if (currItem.tag == TLV_TYPE_ISSUE_PROOF_CERT_HASH) {
                result.certHash = currItem.toBytes();
            } else if (currItem.tag == TLV_TYPE_ISSUE_PROOF_SIG_ALGO) {
                result.sigAlgo = currItem.toString();
            } else if (currItem.tag == TLV_TYPE_ISSUE_PROOF_RAW_PROOF) {
                result.rawProof = currItem.toBytes();
            }
        }
        return result;
    }

    function getEncodedToSign(CrossChainCertificate memory cert)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items = new TLVItem[](7);
        items[0] = TLVUtils.fromStringToTLVItem(
            TLV_TYPE_CERT_VERSION,
            cert.version
        );
        items[1] = TLVUtils.fromStringToTLVItem(TLV_TYPE_CERT_ID, cert.id);
        items[2] = TLVUtils.fromUint8ToTLVItem(
            TLV_TYPE_CERT_TYPE,
            cert.certType.value()
        );
        items[3] = TLVUtils.fromBytesToTLVItem(
            TLV_TYPE_CERT_ISSUER,
            cert.issuer.encode()
        );
        items[4] = TLVUtils.fromUint64ToTLVItem(
            TLV_TYPE_CERT_ISSUANCE_DATE,
            cert.issuanceDate
        );
        items[5] = TLVUtils.fromUint64ToTLVItem(
            TLV_TYPE_CERT_EXPIRATION_DATE,
            cert.expirationDate
        );
        items[6] = TLVUtils.fromBytesToTLVItem(
            TLV_TYPE_CERT_CREDENTIAL_SUBJECT,
            cert.credentialSubject
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function encode(CrossChainCertificate memory cert)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items = new TLVItem[](8);
        items[0] = TLVUtils.fromStringToTLVItem(
            TLV_TYPE_CERT_VERSION,
            cert.version
        );
        items[1] = TLVUtils.fromStringToTLVItem(TLV_TYPE_CERT_ID, cert.id);
        items[2] = TLVUtils.fromUint8ToTLVItem(
            TLV_TYPE_CERT_TYPE,
            cert.certType.value()
        );
        items[3] = TLVUtils.fromBytesToTLVItem(
            TLV_TYPE_CERT_ISSUER,
            cert.issuer.encode()
        );
        items[4] = TLVUtils.fromUint64ToTLVItem(
            TLV_TYPE_CERT_ISSUANCE_DATE,
            cert.issuanceDate
        );
        items[5] = TLVUtils.fromUint64ToTLVItem(
            TLV_TYPE_CERT_EXPIRATION_DATE,
            cert.expirationDate
        );
        items[6] = TLVUtils.fromBytesToTLVItem(
            TLV_TYPE_CERT_CREDENTIAL_SUBJECT,
            cert.credentialSubject
        );
        items[7] = TLVUtils.fromBytesToTLVItem(
            TLV_TYPE_CERT_PROOF,
            cert.proof.encode()
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeCrossChainCertificateFrom(bytes memory rawData)
        internal
        pure
        returns (CrossChainCertificate memory)
    {
        CrossChainCertificate memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TLV_TYPE_CERT_VERSION) {
                result.version = currItem.toString();
            } else if (currItem.tag == TLV_TYPE_CERT_ID) {
                result.id = currItem.toString();
            } else if (currItem.tag == TLV_TYPE_CERT_TYPE) {
                result.certType = CrossChainCertificateTypeEnum(
                    currItem.toUint8()
                );
            } else if (currItem.tag == TLV_TYPE_CERT_ISSUER) {
                result.issuer = decodeObjectIdentityFrom(currItem.toBytes());
            } else if (currItem.tag == TLV_TYPE_CERT_ISSUANCE_DATE) {
                result.issuanceDate = currItem.toUint64();
            } else if (currItem.tag == TLV_TYPE_CERT_EXPIRATION_DATE) {
                result.expirationDate = currItem.toUint64();
            } else if (currItem.tag == TLV_TYPE_CERT_CREDENTIAL_SUBJECT) {
                result.credentialSubject = currItem.toBytes();
            } else if (currItem.tag == TLV_TYPE_CERT_PROOF) {
                result.proof = decodeCrossChainCertIssueProofFrom(
                    currItem.toBytes()
                );
            }
        }
        return result;
    }

    function encode(PTCCredentialSubject memory subject)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items = new TLVItem[](5);
        items[0] = TLVUtils.fromStringToTLVItem(
            TLV_TYPE_PTC_CREDENTIAL_SUBJECT_VERSION,
            subject.version
        );
        items[1] = TLVUtils.fromStringToTLVItem(
            TLV_TYPE_PTC_CREDENTIAL_SUBJECT_NAME,
            subject.name
        );
        items[2] = TLVUtils.fromUint8ToTLVItem(
            TLV_TYPE_PTC_CREDENTIAL_SUBJECT_TYPE,
            subject.ptcType.value()
        );
        items[3] = TLVUtils.fromBytesToTLVItem(
            TLV_TYPE_PTC_CREDENTIAL_SUBJECT_APPLICANT,
            subject.applicant.encode()
        );
        items[4] = TLVUtils.fromBytesToTLVItem(
            TLV_TYPE_PTC_CREDENTIAL_SUBJECT_SUBJECT_INFO,
            subject.subjectInfo
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodePTCCredentialSubjectFrom(bytes memory rawData)
        internal
        pure
        returns (PTCCredentialSubject memory)
    {
        PTCCredentialSubject memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TLV_TYPE_PTC_CREDENTIAL_SUBJECT_VERSION) {
                result.version = currItem.toString();
            } else if (currItem.tag == TLV_TYPE_PTC_CREDENTIAL_SUBJECT_NAME) {
                result.name = currItem.toString();
            } else if (currItem.tag == TLV_TYPE_PTC_CREDENTIAL_SUBJECT_TYPE) {
                result.ptcType = PTCTypeEnum(currItem.toUint8());
            } else if (
                currItem.tag == TLV_TYPE_PTC_CREDENTIAL_SUBJECT_APPLICANT
            ) {
                result.applicant = decodeObjectIdentityFrom(currItem.toBytes());
            } else if (
                currItem.tag == TLV_TYPE_PTC_CREDENTIAL_SUBJECT_SUBJECT_INFO
            ) {
                result.subjectInfo = currItem.toBytes();
            }
        }
        return result;
    }

    function encode(BCDNSTrustRootCredentialSubject memory subject)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items = new TLVItem[](3);
        items[0] = TLVUtils.fromStringToTLVItem(TLV_TYPE_NAME, subject.name);
        items[1] = TLVUtils.fromBytesToTLVItem(
            TLV_TYPE_BCDNS_ROOT_OWNER,
            subject.bcdnsRootOwner.encode()
        );
        items[2] = TLVUtils.fromBytesToTLVItem(
            TLV_TYPE_SUBJECT_INFO,
            subject.bcdnsRootSubjectInfo
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeBCDNSTrustRootCredentialSubjectFrom(bytes memory rawData)
        internal
        pure
        returns (BCDNSTrustRootCredentialSubject memory)
    {
        BCDNSTrustRootCredentialSubject memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TLV_TYPE_NAME) {
                result.name = currItem.toString();
            } else if (currItem.tag == TLV_TYPE_BCDNS_ROOT_OWNER) {
                result.bcdnsRootOwner = decodeObjectIdentityFrom(
                    currItem.toBytes()
                );
            } else if (currItem.tag == TLV_TYPE_SUBJECT_INFO) {
                result.bcdnsRootSubjectInfo = currItem.toBytes();
            }
        }
        return result;
    }

    function encode(DomainNameCredentialSubject memory subject)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items = new TLVItem[](6);
        items[0] = TLVUtils.fromStringToTLVItem(
            TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_VERSION,
            subject.version
        );
        items[1] = TLVUtils.fromUint8ToTLVItem(
            TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_DOMAIN_NAME_TYPE,
            subject.domainNameType.value()
        );
        items[2] = TLVUtils.fromStringToTLVItem(
            TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_PARENT_DOMAIN_SPACE,
            subject.parentDomainSpace
        );
        items[3] = TLVUtils.fromStringToTLVItem(
            TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_DOMAIN_NAME,
            subject.domainName
        );
        items[4] = TLVUtils.fromBytesToTLVItem(
            TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_APPLICANT,
            subject.applicant.encode()
        );
        items[5] = TLVUtils.fromBytesToTLVItem(
            TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_SUBJECT,
            subject.subject
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeDomainNameCredentialSubjectFrom(bytes memory rawData)
        internal
        pure
        returns (DomainNameCredentialSubject memory)
    {
        DomainNameCredentialSubject memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (
                currItem.tag == TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_VERSION
            ) {
                result.version = currItem.toString();
            } else if (
                currItem.tag ==
                TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_DOMAIN_NAME_TYPE
            ) {
                result.domainNameType = DomainNameTypeEnum(currItem.toUint8());
            } else if (
                currItem.tag ==
                TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_PARENT_DOMAIN_SPACE
            ) {
                result.parentDomainSpace = currItem.toString();
            } else if (
                currItem.tag ==
                TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_DOMAIN_NAME
            ) {
                result.domainName = currItem.toString();
            } else if (
                currItem.tag ==
                TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_APPLICANT
            ) {
                result.applicant = decodeObjectIdentityFrom(currItem.toBytes());
            } else if (
                currItem.tag == TLV_TYPE_DOMAIN_NAME_CREDENTIAL_SUBJECT_SUBJECT
            ) {
                result.subject = currItem.toBytes();
            }
        }
        return result;
    }

    function encode(RelayerCredentialSubject memory subject)
        internal
        pure
        returns (bytes memory)
    {
        TLVItem[] memory items = new TLVItem[](4);
        items[0] = TLVUtils.fromStringToTLVItem(
            TLV_TYPE_RELAYER_CREDENTIAL_SUBJECT_VERSION,
            subject.version
        );
        items[1] = TLVUtils.fromStringToTLVItem(
            TLV_TYPE_RELAYER_CREDENTIAL_SUBJECT_NAME,
            subject.name
        );
        items[2] = TLVUtils.fromBytesToTLVItem(
            TLV_TYPE_RELAYER_CREDENTIAL_SUBJECT_APPLICANT,
            subject.applicant.encode()
        );
        items[3] = TLVUtils.fromBytesToTLVItem(
            TLV_TYPE_RELAYER_CREDENTIAL_SUBJECT_SUBJECT_INFO,
            subject.subjectInfo
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeRelayerCredentialSubjectFrom(bytes memory rawData)
        internal
        pure
        returns (RelayerCredentialSubject memory)
    {
        RelayerCredentialSubject memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TLV_TYPE_RELAYER_CREDENTIAL_SUBJECT_VERSION) {
                result.version = currItem.toString();
            } else if (
                currItem.tag == TLV_TYPE_RELAYER_CREDENTIAL_SUBJECT_NAME
            ) {
                result.name = currItem.toString();
            } else if (
                currItem.tag == TLV_TYPE_RELAYER_CREDENTIAL_SUBJECT_APPLICANT
            ) {
                result.applicant = decodeObjectIdentityFrom(currItem.toBytes());
            } else if (
                currItem.tag == TLV_TYPE_RELAYER_CREDENTIAL_SUBJECT_SUBJECT_INFO
            ) {
                result.subjectInfo = currItem.toBytes();
            }
        }
        return result;
    }

    // For now, only SECP256k1 key supported
    function getRawPublicKey(BCDNSTrustRootCredentialSubject memory subject)
        internal
        pure
        returns (bytes memory)
    {
        return
            subject.bcdnsRootOwner.getRawPublicKey(
                subject.bcdnsRootSubjectInfo
            );
    }

    // For now, only SECP256k1 key supported
    function getRawPublicKey(RelayerCredentialSubject memory subject)
        internal
        pure
        returns (bytes memory)
    {
        return subject.applicant.getRawPublicKey(subject.subjectInfo);
    }

    // For now, only SECP256k1 key supported
    function getRawPublicKey(DomainNameCredentialSubject memory subject)
        internal
        pure
        returns (bytes memory)
    {
        return subject.applicant.getRawPublicKey(subject.subject);
    }

    // For now, only SECP256k1 key supported
    function getRawPublicKey(PTCCredentialSubject memory subject)
        internal
        pure
        returns (bytes memory)
    {
        return subject.applicant.getRawPublicKey(subject.subjectInfo);
    }

    function getRawPublicKey(
        ObjectIdentity memory oid,
        bytes memory subjectInfo
    ) internal pure returns (bytes memory) {
        require(
            oid.oidType == ObjectIdentityType.X509_PUBLIC_KEY_INFO,
            "only support X509_PUBLIC_KEY_INFO now"
        );
        require(oid.rawId.length >= 64, "public key length not enouth");

        bytes memory derPubkey = oid.rawId;
        uint256 offset = derPubkey.length - 64;
        bytes memory rawPubkey = new bytes(64);
        assembly {
            mstore(
                add(rawPubkey, 0x20),
                mload(add(add(derPubkey, 0x20), offset))
            )
            mstore(
                add(rawPubkey, 0x40),
                mload(add(add(derPubkey, 0x40), offset))
            )
        }
        return rawPubkey;
    }

    function getAddressFrom(bytes memory pubkey)
        internal
        pure
        returns (address)
    {
        return address(uint160(uint256(keccak256(pubkey))));
    }

    function getRawPublicKey(CrossChainCertificate memory cert)
        internal
        pure
        returns (bytes memory)
    {
        if (
            cert.certType ==
            CrossChainCertificateTypeEnum.BCDNS_TRUST_ROOT_CERTIFICATE
        ) {
            return
                decodeBCDNSTrustRootCredentialSubjectFrom(
                    cert.credentialSubject
                ).getRawPublicKey();
        } else if (
            cert.certType ==
            CrossChainCertificateTypeEnum.DOMAIN_NAME_CERTIFICATE
        ) {
            return
                decodeDomainNameCredentialSubjectFrom(cert.credentialSubject)
                    .getRawPublicKey();
        } else if (
            cert.certType ==
            CrossChainCertificateTypeEnum
                .PROOF_TRANSFORMATION_COMPONENT_CERTIFICATE
        ) {
            return
                decodePTCCredentialSubjectFrom(cert.credentialSubject)
                    .getRawPublicKey();
        } else if (
            cert.certType == CrossChainCertificateTypeEnum.RELAYER_CERTIFICATE
        ) {
            return
                decodeRelayerCredentialSubjectFrom(cert.credentialSubject)
                    .getRawPublicKey();
        }

        revert("getRawPublicKey: cert type not support");
    }

    function verifySig(
        string memory signAlgo,
        bytes memory pubkey,
        bytes memory data,
        bytes memory sig
    ) internal view returns (bool) {
        require(
            signAlgo.equal(KECCAK256_WITH_SECP256K1),
            "only support KECCAK256_WITH_SECP256K1 signature now"
        );
        require(sig.length >= 65, "wrong secp256k1 sig length");
        sig[64] = bytes1(uint8(sig[64]) + uint8(27));
        bool result = 
            SignatureChecker.isValidSignatureNow(
                getAddressFrom(pubkey),
                keccak256(data),
                sig
            );
        sig[64] = bytes1(uint8(sig[64]) - uint8(27));
        return result;
    }

    function equals(ObjectIdentity memory self, ObjectIdentity memory otherOid)
        internal
        pure
        returns (bool)
    {
        return keccak256(self.encode()) == keccak256(otherOid.encode());
    }

    function validate(
        CrossChainCertificate memory self,
        CrossChainCertificate memory signerCert
    ) internal view returns (bool, string memory) {
        if (
            signerCert.certType ==
            CrossChainCertificateTypeEnum.BCDNS_TRUST_ROOT_CERTIFICATE
        ) {
            BCDNSTrustRootCredentialSubject
                memory signerSubject = decodeBCDNSTrustRootCredentialSubjectFrom(
                    signerCert.credentialSubject
                );
            if (!self.issuer.equals(signerSubject.bcdnsRootOwner)) {
                return (false, "crosschain cert validation: wrong signer");
            }
        } else if (
            signerCert.certType ==
            CrossChainCertificateTypeEnum.DOMAIN_NAME_CERTIFICATE
        ) {
            DomainNameCredentialSubject
                memory signerSubject = decodeDomainNameCredentialSubjectFrom(
                    signerCert.credentialSubject
                );
            if (
                signerSubject.domainNameType !=
                DomainNameTypeEnum.DOMAIN_NAME_SPACE
            ) {
                return (
                    false,
                    "crosschain cert validation: require bcdns root or domain space owner cert as signer"
                );
            }
            if (!self.issuer.equals(signerSubject.applicant)) {
                return (false, "crosschain cert validation: wrong signer");
            }
        } else {
            return (false, "crosschain cert validation: type of signer cert must be bcdns");
        }
        if (
            verifySig(
                self.proof.sigAlgo,
                signerCert.getRawPublicKey(),
                self.getEncodedToSign(),
                self.proof.rawProof
            )
        ) {
            return (true, "");
        }
        return (false, "crosschain cert validation: invalid sig");
    }

    function getCertOwnerOid(CrossChainCertificate memory self)
        internal
        pure
        returns (ObjectIdentity memory)
    {
        if (
            self.certType ==
            CrossChainCertificateTypeEnum.BCDNS_TRUST_ROOT_CERTIFICATE
        ) {
            return
                decodeBCDNSTrustRootCredentialSubjectFrom(
                    self.credentialSubject
                ).bcdnsRootOwner;
        } else if (
            self.certType ==
            CrossChainCertificateTypeEnum.DOMAIN_NAME_CERTIFICATE
        ) {
            return
                decodeDomainNameCredentialSubjectFrom(self.credentialSubject)
                    .applicant;
        } else if (
            self.certType ==
            CrossChainCertificateTypeEnum.PROOF_TRANSFORMATION_COMPONENT_CERTIFICATE
        ) {
            return
                decodePTCCredentialSubjectFrom(self.credentialSubject)
                    .applicant;
        } else if (
            self.certType == CrossChainCertificateTypeEnum.RELAYER_CERTIFICATE
        ) {
            return
                decodeRelayerCredentialSubjectFrom(self.credentialSubject)
                    .applicant;
        }

        revert("getCertOwnerOid: cert type not support");
    }

    function value(ObjectIdentityType _type) internal pure returns (uint8) {
        return uint8(_type);
    }

    function value(CrossChainCertificateTypeEnum _type)
        internal
        pure
        returns (uint8)
    {
        return uint8(_type);
    }

    function value(PTCTypeEnum _type) internal pure returns (uint8) {
        return uint8(_type);
    }

    function value(DomainNameTypeEnum _type) internal pure returns (uint8) {
        return uint8(_type);
    }
}
