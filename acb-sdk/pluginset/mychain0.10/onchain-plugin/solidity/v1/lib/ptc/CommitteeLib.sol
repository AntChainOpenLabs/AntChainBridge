// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "./PtcLib.sol";
import "../../@openzeppelin/contracts/utils/Strings.sol";

// tags for CommitteeEndorseRoot
uint16 constant TAG_COMMITTEE_ID = 0x00;
uint16 constant TAG_POLICY = 0x01;
uint16 constant TAG_ENDORSERS = 0x02;

struct CommitteeEndorseRoot {
    string committeeId;
    OptionalEndorsePolicy policy;
    NodeEndorseInfo[] endorsers;
}

// tags for NodeEndorseInfo
uint16 constant TAG_NODE_ID = 0x00;
uint16 constant TAG_REQUIRED = 0x01;
uint16 constant TAG_PUBLIC_KEY = 0x02;

struct NodeEndorseInfo {
    string nodeId;
    bool required;
    NodePublicKeyEntry publicKey;
}

// tags for NodePublicKeyEntry
uint16 constant TAG_KEY_ID = 0x00;
uint16 constant TAG_RAW_PUBKEY = 0x01;

struct NodePublicKeyEntry {
    string keyId;
    bytes rawPublicKey;
}

// tags for OptionalEndorsePolicy
uint16 constant TAG_THRESHOLD = 0x00;

struct OptionalEndorsePolicy {
    Threshold threshold;
}

// tags for Threshold
uint16 constant TAG_OPERATOR = 0x00;
uint16 constant TAG_THRESHOLD_NUM = 0x01;

struct Threshold {
    string operator;
    uint32 threshold;
}

// tags for CommitteeVerifyAnchor
uint16 constant TAG_CVA_COMMITTEE_ID = 0x00;
uint16 constant TAG_CVA_ANCHORS = 0x01;

struct CommitteeVerifyAnchor {
    string committeeId;
    NodeAnchorInfo[] anchors;
}

// tags for NodeAnchorInfo
uint16 constant TAG_NAI_NODE_ID = 0x00;
uint16 constant TAG_NAI_NODE_PUBKEYS = 0x01;

struct NodeAnchorInfo {
    string nodeId;
    NodePublicKeyEntry[] nodePublicKeys;
}

// tags for CommitteeEndorseProof
uint16 constant TAG_CEP_COMMITTEE_ID = 0x00;
uint16 constant TAG_CEP_SIGS = 0x01;

struct CommitteeEndorseProof {
    string committeeId;
    CommitteeNodeProof[] sigs;
}

// tags for CommitteeNodeProof
uint16 constant TAG_CNP_NODE_ID = 0x00;
uint16 constant TAG_CNP_SIGN_ALGO = 0x01;
uint16 constant TAG_CNP_SIG_HEX = 0x02;

struct CommitteeNodeProof {
    string nodeId;
    string signAlgo;
    bytes signature;
}

library CommitteeLib {

    event DoVeriyTpBta(CrossChainLane laneKey, string committeeId, string nodeId, bool result);
    event DoVeriyTpProof(CrossChainLane laneKey, string committeeId, string nodeId, bool result);

    using TLVUtils for TLVPacket;
    using TLVUtils for TLVItem;
    using TLVUtils for BytesArrayStream;

    using CommitteeLib for Threshold;
    using CommitteeLib for NodePublicKeyEntry;
    using CommitteeLib for NodeEndorseInfo;
    using CommitteeLib for OptionalEndorsePolicy;
    using CommitteeLib for NodeAnchorInfo;
    using CommitteeLib for CommitteeVerifyAnchor;
    using CommitteeLib for CommitteeNodeProof;

    using PtcLib for PtcTrustRoot;
    using PtcLib for ThirdPartyResp;
    using PtcLib for TpBta;
    using PtcLib for ThirdPartyProof;

    using Strings for string;

    function verifyTpBta(PTCVerifyAnchor memory va, TpBta memory tpBta) internal returns (bool) {
        require(
            TLVUtils.getUint256FromBytes(va.version) == TLVUtils.getUint256FromBytes(tpBta.ptcVerifyAnchorVersion), 
            "verify anchor version not equal"
        );

        CommitteeVerifyAnchor memory cva = decodeCommitteeVerifyAnchorFrom(va.anchor);
        CommitteeEndorseProof memory ceProof = decodeCommitteeEndorseProofFrom(tpBta.endorseProof);
                
        return cva.verifyCommitteeEndorseProof(ceProof, tpBta);
    }

    function verifyCommitteeEndorseProof(CommitteeVerifyAnchor memory cva, CommitteeEndorseProof memory ceProof, TpBta memory tpBta) internal returns (bool) {
        require(cva.committeeId.equal(ceProof.committeeId), "committee id in proof not equal with the one in verify anchor");

        bytes memory encodedToSign = tpBta.getEncodedToSign();
        uint correct = 0;
        for (uint i = 0; i < ceProof.sigs.length; i++) 
        {
            CommitteeNodeProof memory proof = ceProof.sigs[i];
            require(proof.signAlgo.equal(KECCAK256_WITH_SECP256K1), "only support KECCAK256_WITH_SECP256K1 sig");
            for (uint j = 0; j < cva.anchors.length; j++) {
                if (cva.anchors[j].nodeId.equal(proof.nodeId)) {
                    bool res = false;
                    for (uint k = 0; k < cva.anchors[j].nodePublicKeys.length; k++) 
                    {
                        res = AcbCommons.verifySig(
                            proof.signAlgo,
                            cva.anchors[j].nodePublicKeys[k].getRawPublicKey(),
                            encodedToSign,
                            proof.signature
                        );
                        if (res) {
                            break;
                        }
                    }
                    emit DoVeriyTpBta(tpBta.crossChainLane, cva.committeeId, proof.nodeId, res);
                    if (res) {
                        correct++;
                        break;
                    }
                }
            }
        }

        return 3 * correct > 2 * cva.anchors.length;
    }

   function verifyTpProof(TpBta memory tpBta, ThirdPartyProof memory tpProof) internal returns (bool) {
        CommitteeEndorseRoot memory cer = decodeCommitteeEndorseRootFrom(tpBta.endorseRoot);
        CommitteeEndorseProof memory ceProof = decodeCommitteeEndorseProofFrom(tpProof.rawProof);

        require(cer.committeeId.equal(ceProof.committeeId), "committee id in proof not equal with the one in endorse root");

        bytes memory encodedToSign = tpProof.getEncodedToSign();
        uint32 optionalCorrect = 0;

        // Preprocessing: create a fixed-size boolean array to mark the signature status to avoid duplicate lookups
        bool[] memory verifiedNodes = new bool[](cer.endorsers.length);
        uint[] memory proofIndices = new uint[](cer.endorsers.length);

        // build the mapping from node ID to signature index
        for (uint i = 0; i < cer.endorsers.length; i++) {
            proofIndices[i] = type(uint).max; // Initialized to the maximum value to indicate not found
            for (uint j = 0; j < ceProof.sigs.length; j++) {
                if (cer.endorsers[i].nodeId.equal(ceProof.sigs[j].nodeId)) {
                    proofIndices[i] = j;
                    break;
                }
            }
        }

        // verifying signatures
        for (uint i = 0; i < cer.endorsers.length; i++) {
            NodeEndorseInfo memory info = cer.endorsers[i];
            bool res = false;

            if (proofIndices[i] != type(uint).max) {
                CommitteeNodeProof memory proof = ceProof.sigs[proofIndices[i]];
                res = AcbCommons.verifySig(
                    proof.signAlgo,
                    info.publicKey.getRawPublicKey(),
                    encodedToSign,
                    proof.signature
                );
                if (res) {
                    optionalCorrect++;
                    verifiedNodes[i] = true;
                }
            }

            emit DoVeriyTpProof(tpBta.crossChainLane, cer.committeeId, info.nodeId, res);
            if (!res && info.required) {
                return false;
            }
        }

        return cer.policy.threshold.check(optionalCorrect);
    }

    function getRawPublicKey(
        NodePublicKeyEntry memory entry
    ) internal pure returns (bytes memory) {
        require(entry.rawPublicKey.length >= 64, "public key length not enouth");

        bytes memory derPubkey = entry.rawPublicKey;
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

    function encode(CommitteeEndorseRoot memory self) internal pure returns (bytes memory) {
        TLVItem[] memory items = new TLVItem[](3);
        items[0] = TLVUtils.fromStringToTLVItem(
            TAG_COMMITTEE_ID,
            self.committeeId
        );
        items[1] = TLVUtils.fromBytesToTLVItem(
            TAG_POLICY,
            self.policy.encode()
        );

        uint totalSize = 0;
        bytes[] memory rawEndorsers = new bytes[](self.endorsers.length);
        for (uint i = 0; i < self.endorsers.length; i++) 
        {
            rawEndorsers[i] = self.endorsers[i].encode();
            totalSize += rawEndorsers[i].length;
        }
        items[2] = TLVUtils.fromBytesArrayToTLVItem(
            TAG_ENDORSERS,
            totalSize,
            rawEndorsers
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeCommitteeEndorseRootFrom(bytes memory rawData)
        internal
        pure
        returns (CommitteeEndorseRoot memory)
    {
        CommitteeEndorseRoot memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TAG_COMMITTEE_ID) {
                result.committeeId = currItem.toString();
            } else if (currItem.tag == TAG_POLICY) {
                result.policy = decodeOptionalEndorsePolicyFrom(currItem.toBytes());
            } else if (currItem.tag == TAG_ENDORSERS) {
                BytesArrayStream memory s = currItem.toBytesArrayStream();
                NodeEndorseInfo[] memory endorsers = new NodeEndorseInfo[](s.calcLenValueSize());
                uint j = 0;
                while (s.hasNext()) {
                    endorsers[j++] = decodeNodeEndorseInfoFrom(s.getNextVarBytes());
                }
                result.endorsers = endorsers;
            }
        }
        return result;
    }

    function encode(NodeEndorseInfo memory self) internal pure returns (bytes memory) {
        TLVItem[] memory items = new TLVItem[](3);
        items[0] = TLVUtils.fromStringToTLVItem(
            TAG_NODE_ID,
            self.nodeId
        );
        items[1] = TLVUtils.fromUint8ToTLVItem(
            TAG_REQUIRED,
            self.required ? 1 : 0
        );
        items[2] = TLVUtils.fromBytesToTLVItem(
            TAG_PUBLIC_KEY,
            self.publicKey.encode()
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeNodeEndorseInfoFrom(bytes memory rawData)
        internal
        pure
        returns (NodeEndorseInfo memory)
    {
        NodeEndorseInfo memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TAG_NODE_ID) {
                result.nodeId = currItem.toString();
            } else if (currItem.tag == TAG_REQUIRED) {
                result.required = currItem.toUint8() > 0 ? true : false;
            } else if (currItem.tag == TAG_PUBLIC_KEY) {
                result.publicKey = decodeNodePublicKeyEntryFrom(currItem.toBytes());
            }
        }
        return result;
    }

    function encode(NodePublicKeyEntry memory self) internal pure returns (bytes memory) {
        TLVItem[] memory items = new TLVItem[](2);
        items[0] = TLVUtils.fromStringToTLVItem(
            TAG_KEY_ID,
            self.keyId
        );
        items[1] = TLVUtils.fromBytesToTLVItem(
            TAG_RAW_PUBKEY,
            self.rawPublicKey
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeNodePublicKeyEntryFrom(bytes memory rawData)
        internal
        pure
        returns (NodePublicKeyEntry memory)
    {
        NodePublicKeyEntry memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TAG_KEY_ID) {
                result.keyId = currItem.toString();
            } else if (currItem.tag == TAG_RAW_PUBKEY) {
                result.rawPublicKey = currItem.toBytes();
            }
        }
        return result;
    }

    function encode(OptionalEndorsePolicy memory self) internal pure returns (bytes memory) {
        TLVItem[] memory items = new TLVItem[](1);
        items[0] = TLVUtils.fromBytesToTLVItem(
            TAG_THRESHOLD,
            self.threshold.encode()
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeOptionalEndorsePolicyFrom(bytes memory rawData)
        internal
        pure
        returns (OptionalEndorsePolicy memory)
    {
        OptionalEndorsePolicy memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TAG_THRESHOLD) {
                result.threshold = decodeThresholdFrom(currItem.toBytes());
            }
        }
        return result;
    }

    function check(Threshold memory self, uint32 n) internal pure returns (bool) {
        if (self.operator.equal("<")) {
            return n < self.threshold;
        } else if (self.operator.equal(">")) {
            return n > self.threshold;
        } else if (self.operator.equal("==")) {
            return n == self.threshold;
        } else if (self.operator.equal(">=")) {
            return n >= self.threshold;
        } else if (self.operator.equal("<=")) {
            return n <= self.threshold;
        } else if (self.operator.equal("!=")) {
            return n != self.threshold;
        } else { 
            revert("no operator matched");
        }
    }

    function encode(Threshold memory self) internal pure returns (bytes memory) {
        TLVItem[] memory items = new TLVItem[](2);
        items[0] = TLVUtils.fromStringToTLVItem(
            TAG_OPERATOR,
            self.operator
        );
        items[1] = TLVUtils.fromUint32ToTLVItem(TAG_THRESHOLD_NUM, self.threshold);

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeThresholdFrom(bytes memory rawData)
        internal
        pure
        returns (Threshold memory)
    {
        Threshold memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TAG_OPERATOR) {
                result.operator = currItem.toString();
            } else if (currItem.tag == TLV_TYPE_ISSUE_PROOF_CERT_HASH) {
                result.threshold = currItem.toUint32();
            }
        }
        return result;
    }

    function encode(NodeAnchorInfo memory self) internal pure returns (bytes memory) {
        TLVItem[] memory items = new TLVItem[](2);
        items[0] = TLVUtils.fromStringToTLVItem(
            TAG_NAI_NODE_ID,
            self.nodeId
        );

        uint totalSize = 0;
        bytes[] memory rawNodePublicKeys = new bytes[](self.nodePublicKeys.length);
        for (uint i = 0; i < self.nodePublicKeys.length; i++) 
        {
            rawNodePublicKeys[i] = self.nodePublicKeys[i].encode();
            totalSize += rawNodePublicKeys[i].length;
        }
        items[1] = TLVUtils.fromBytesArrayToTLVItem(
            TAG_NAI_NODE_PUBKEYS,
            totalSize,
            rawNodePublicKeys
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeNodeAnchorInfoFrom(bytes memory rawData)
        internal
        pure
        returns (NodeAnchorInfo memory)
    {
        NodeAnchorInfo memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TAG_NAI_NODE_ID) {
                result.nodeId = currItem.toString();
            } else if (currItem.tag == TAG_NAI_NODE_PUBKEYS) {
                BytesArrayStream memory s = currItem.toBytesArrayStream();
                NodePublicKeyEntry[] memory entries = new NodePublicKeyEntry[](s.calcLenValueSize());
                uint j = 0;
                while (s.hasNext()) {
                    entries[j++] = decodeNodePublicKeyEntryFrom(s.getNextVarBytes());
                }
                result.nodePublicKeys = entries;
            }
        }
        return result;
    }

    function encode(CommitteeVerifyAnchor memory self) internal pure returns (bytes memory) {
        TLVItem[] memory items = new TLVItem[](2);
        items[0] = TLVUtils.fromStringToTLVItem(
            TAG_CVA_COMMITTEE_ID,
            self.committeeId
        );

        uint totalSize = 0;
        bytes[] memory rawAnchors = new bytes[](self.anchors.length);
        for (uint i = 0; i < self.anchors.length; i++) 
        {
            rawAnchors[i] = self.anchors[i].encode();
            totalSize += rawAnchors[i].length;
        }
        items[1] = TLVUtils.fromBytesArrayToTLVItem(
            TAG_CVA_ANCHORS,
            totalSize,
            rawAnchors
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeCommitteeVerifyAnchorFrom(bytes memory rawData)
        internal
        pure
        returns (CommitteeVerifyAnchor memory)
    {
        CommitteeVerifyAnchor memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TAG_CVA_COMMITTEE_ID) {
                result.committeeId = currItem.toString();
            } else if (currItem.tag == TAG_CVA_ANCHORS) {
                BytesArrayStream memory s = currItem.toBytesArrayStream();
                NodeAnchorInfo[] memory infos = new NodeAnchorInfo[](s.calcLenValueSize());
                uint j = 0;
                while (s.hasNext()) {
                    infos[j++] = decodeNodeAnchorInfoFrom(s.getNextVarBytes());
                }
                result.anchors = infos;
            }
        }
        return result;
    }

    function encode(CommitteeNodeProof memory self) internal pure returns (bytes memory) {
        TLVItem[] memory items = new TLVItem[](3);
        items[0] = TLVUtils.fromStringToTLVItem(
            TAG_CNP_NODE_ID,
            self.nodeId
        );
        items[1] = TLVUtils.fromStringToTLVItem(
            TAG_CNP_SIGN_ALGO,
            self.signAlgo
        );
        items[2] = TLVUtils.fromBytesToTLVItem(
            TAG_CNP_SIG_HEX,
            self.signature
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeCommitteeNodeProofFrom(bytes memory rawData)
        internal
        pure
        returns (CommitteeNodeProof memory)
    {
        CommitteeNodeProof memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TAG_CNP_NODE_ID) {
                result.nodeId = currItem.toString();
            } else if (currItem.tag == TAG_CNP_SIGN_ALGO) {
                result.signAlgo = currItem.toString();
            } else if (currItem.tag == TAG_CNP_SIG_HEX) {
                result.signature = currItem.toBytes();
            }
        }
        return result;
    }

    function encode(CommitteeEndorseProof memory self) internal pure returns (bytes memory) {
        TLVItem[] memory items = new TLVItem[](2);
        items[0] = TLVUtils.fromStringToTLVItem(
            TAG_CEP_COMMITTEE_ID,
            self.committeeId
        );
        uint totalSize = 0;
        bytes[] memory rawSigs = new bytes[](self.sigs.length);
        for (uint i = 0; i < self.sigs.length; i++) 
        {
            rawSigs[i] = self.sigs[i].encode();
            totalSize += rawSigs[i].length;
        }
        items[1] = TLVUtils.fromBytesArrayToTLVItem(
            TAG_CEP_SIGS,
            totalSize,
            rawSigs
        );

        TLVPacket memory packet;
        packet.items = items;

        return packet.encode();
    }

    function decodeCommitteeEndorseProofFrom(bytes memory rawData)
        internal
        pure
        returns (CommitteeEndorseProof memory)
    {
        CommitteeEndorseProof memory result;
        TLVPacket memory packet = TLVUtils.decodePacket(rawData);
        for (uint256 i = 0; i < packet.items.length; i++) {
            TLVItem memory currItem = packet.items[i];
            if (currItem.tag == TAG_CEP_COMMITTEE_ID) {
                result.committeeId = currItem.toString();
            } else if (currItem.tag == TAG_CEP_SIGS) {
                BytesArrayStream memory s = currItem.toBytesArrayStream();
                CommitteeNodeProof[] memory proofs = new CommitteeNodeProof[](s.calcLenValueSize());
                uint j = 0;
                while (s.hasNext()) {
                    proofs[j++] = decodeCommitteeNodeProofFrom(s.getNextVarBytes());
                }
                result.sigs = proofs;
            }
        }
        return result;
    }
}