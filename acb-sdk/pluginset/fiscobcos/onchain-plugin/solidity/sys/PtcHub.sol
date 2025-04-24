// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "./interfaces/IPtcHub.sol";
import "./interfaces/IPtcVerifier.sol";
import "./lib/ptc/PtcLib.sol";
import "./@openzeppelin/contracts/access/Ownable.sol";
import "./@openzeppelin/contracts/proxy/utils/Initializable.sol";

contract PtcHub is IPtcHub, Ownable, Initializable {
    string constant ROOT_DOMAIN_SPACE_ALIAS = "root";

    using AcbCommons for CrossChainCertificate;
    using AcbCommons for ObjectIdentity;
    using AcbCommons for CrossChainLane;

    using PtcLib for PtcTrustRoot;
    using PtcLib for ThirdPartyResp;

    using TLVUtils for TLVItemMapValueStream;

    using Strings for string;

    struct PtcTrustRootStorage {
        bytes rawPtcTrustRoot;
        mapping(uint256 => bytes) verifyAnchorMap;
    }

    struct TpBtaStorage {
        uint256 latestVersion;
        mapping(uint256 => bytes) mapByVersion;
    }

    mapping(string => bytes) public bcdnsCertMap;
    mapping(bytes32 => string) public ownerOidToBcdnsDomainSpaceMap;

    mapping(bytes32 => PtcTrustRootStorage) public ptcTrustRootMap;

    mapping(bytes32 => TpBtaStorage) public tpBtaMap;

    mapping(PTCTypeEnum => address) public verifierMap;

    PTCTypeEnum[] public ptcTypeSupported;

    constructor(bytes memory rawRootBcdnsCert) {
        _initBcdns(rawRootBcdnsCert);
        _disableInitializers();
    }

    function init(bytes memory rawRootBcdnsCert) external initializer {
        _initBcdns(rawRootBcdnsCert);
        _transferOwnership(_msgSender());
    }

    function _initBcdns(bytes memory rawRootBcdnsCert) internal {
        CrossChainCertificate memory rootBcdnsCert = AcbCommons
            .decodeCrossChainCertificateFrom(rawRootBcdnsCert);
        require(
            rootBcdnsCert.certType ==
                CrossChainCertificateTypeEnum.BCDNS_TRUST_ROOT_CERTIFICATE,
            "wrong type for bcdns root cert"
        );

        bytes32 k = keccak256(rootBcdnsCert.getCertOwnerOid().encode());
        bcdnsCertMap[ROOT_DOMAIN_SPACE_ALIAS] = rawRootBcdnsCert;
        ownerOidToBcdnsDomainSpaceMap[k] = ROOT_DOMAIN_SPACE_ALIAS;

        emit NewBcdnsCert(k);
    }

    function updatePTCTrustRoot(bytes calldata rawPtcTrustRoot)
        external
        override
        onlyOwner()
    {
        PtcTrustRoot memory ptr = PtcLib.decodePtcTrustRootFrom(
            rawPtcTrustRoot
        );

        _verifyPtcTrustRoot(ptr);

        bytes32 ptrK = ptr.getKey();
        PtcTrustRootStorage storage ptrStorage = ptcTrustRootMap[ptrK];
        if (ptrStorage.rawPtcTrustRoot.length > 0) {
            ptrStorage.rawPtcTrustRoot = rawPtcTrustRoot;
            while (ptr.verifyAnchorMap.hasNext()) {
                uint256 verNum = ptr.verifyAnchorMap.getNextBigInteger();
                if (ptrStorage.verifyAnchorMap[verNum].length == 0) {
                    ptrStorage.verifyAnchorMap[verNum] = ptr
                        .verifyAnchorMap
                        .getNextVarBytes();
                    emit NewVerifyAnchor(ptrK, verNum);
                } else {
                    ptr.verifyAnchorMap.getNextVarBytes();
                }
            }
        } else {
            ptrStorage.rawPtcTrustRoot = rawPtcTrustRoot;
            while (ptr.verifyAnchorMap.hasNext()) {
                uint256 verNum = ptr.verifyAnchorMap.getNextBigInteger();
                ptrStorage.verifyAnchorMap[verNum] = ptr
                    .verifyAnchorMap
                    .getNextVarBytes();
                emit NewVerifyAnchor(ptrK, verNum);
            }
            emit NewPtrTrustRoot(ptrK);
        }
    }

    function _verifyPtcTrustRoot(PtcTrustRoot memory ptr) internal view {
        bytes32 issuerKey = keccak256(ptr.ptcCrossChainCert.issuer.encode());
        require(
            bytes(ownerOidToBcdnsDomainSpaceMap[issuerKey]).length > 0,
            "issuer not found"
        );

        CrossChainCertificate memory issuerCert = AcbCommons.decodeCrossChainCertificateFrom(
            bcdnsCertMap[ownerOidToBcdnsDomainSpaceMap[issuerKey]]
        );
        if (issuerCert.proof.sigAlgo.equal(KECCAK256_WITH_SECP256K1)) {
            (bool ret, string memory errorMsg) = ptr.ptcCrossChainCert.validate(issuerCert);
            require(ret, errorMsg);
        }

        require(ptr.verifySig(), "ptc trust root sig invalid");
    }

    function getPTCTrustRoot(bytes calldata ptcOwnerOid)
        external
        view
        override
        returns (bytes memory)
    {
        return ptcTrustRootMap[keccak256(ptcOwnerOid)].rawPtcTrustRoot;
    }

    function hasPTCTrustRoot(bytes calldata ptcOwnerOid)
        external
        view
        override
        returns (bool)
    {
        return ptcTrustRootMap[keccak256(ptcOwnerOid)].rawPtcTrustRoot.length > 0;
    }

    function getPTCVerifyAnchor(bytes calldata ptcOwnerOid, uint256 versionNum)
        external
        view
        override
        returns (bytes memory)
    {
        return ptcTrustRootMap[keccak256(ptcOwnerOid)].verifyAnchorMap[versionNum];
    }

    function hasPTCVerifyAnchor(bytes calldata ptcOwnerOid, uint256 versionNum)
        external
        view
        override
        returns (bool)
    {
        return ptcTrustRootMap[keccak256(ptcOwnerOid)].verifyAnchorMap[versionNum].length > 0;
    }

    function addTpBta(bytes calldata rawTpBta) external override onlyOwner() {
        TpBta memory newTpBta = PtcLib.decodeTpBtaFrom(rawTpBta);

        require(_hasPTCTrustRoot(newTpBta.signerPtcCredentialSubject.applicant), "no ptc trust root found");

        PtcTrustRoot memory ptr = _getPTCTrustRoot(newTpBta.signerPtcCredentialSubject.applicant);

        uint256 vaVer = TLVUtils.getUint256FromBytes(newTpBta.ptcVerifyAnchorVersion);
        require(_hasPTCVerifyAnchor(newTpBta.signerPtcCredentialSubject.applicant, vaVer), "no ptc verify anchor found");

        address verifier = verifierMap[AcbCommons.decodePTCCredentialSubjectFrom(ptr.ptcCrossChainCert.credentialSubject).ptcType];
        require(verifier != address(0x0), "no ptc veifier set");

        require(
            IPtcVerifier(verifier).verifyTpBta(
                _getPTCVerifyAnchor(newTpBta.signerPtcCredentialSubject.applicant, vaVer),
                newTpBta
            ), "failed to verify tpbta"
        );

        bytes32 k = keccak256(newTpBta.crossChainLane.encode());
        TpBtaStorage storage s = tpBtaMap[k];
        if (s.latestVersion < newTpBta.tpbtaVersion) {
            s.latestVersion = newTpBta.tpbtaVersion;
            emit LatestTpBta(k, newTpBta.tpbtaVersion);
        }
        s.mapByVersion[newTpBta.tpbtaVersion] = rawTpBta;
        emit SaveTpBta(k, newTpBta.tpbtaVersion);
    }

    function getTpBta(bytes calldata tpbtaLane, uint32 tpBtaVersion) external override view returns (bytes memory) {
        return tpBtaMap[keccak256(tpbtaLane)].mapByVersion[tpBtaVersion];
    }

    function getLatestTpBta(bytes calldata tpbtaLane) external override view returns (bytes memory) {
        TpBtaStorage storage s = tpBtaMap[keccak256(tpbtaLane)];
        return s.mapByVersion[s.latestVersion];
    }

    function hasTpBta(bytes calldata tpbtaLane, uint32 tpBtaVersion)
        external
        view
        override
        returns (bool)
    {
        return tpBtaMap[keccak256(tpbtaLane)].mapByVersion[tpBtaVersion].length > 0;
    }

    function verifyProof(bytes calldata rawTpProof)
        external
        override
    {
        ThirdPartyProof memory tpProof = PtcLib.decodeThirdPartyProofFrom(rawTpProof);

        bytes memory rawTpBtaLane = tpProof.tpbtaCrossChainLane.encode();
        require(tpBtaMap[keccak256(rawTpBtaLane)].mapByVersion[tpProof.tpbtaVersion].length > 0, "'tpbta' not found");

        TpBta memory tpbta = _getTpBta(rawTpBtaLane, tpProof.tpbtaVersion);

        require(_hasPTCTrustRoot(tpbta.signerPtcCredentialSubject.applicant), "no ptc trust root found");

        PtcTrustRoot memory ptr = _getPTCTrustRoot(tpbta.signerPtcCredentialSubject.applicant);

        uint256 vaVer = TLVUtils.getUint256FromBytes(tpbta.ptcVerifyAnchorVersion);
        require(_hasPTCVerifyAnchor(tpbta.signerPtcCredentialSubject.applicant, vaVer), "no ptc verify anchor found");

        address verifier = verifierMap[AcbCommons.decodePTCCredentialSubjectFrom(ptr.ptcCrossChainCert.credentialSubject).ptcType];
        require(verifier != address(0x0), "no ptc veifier set");

        bool result = IPtcVerifier(verifier).verifyTpProof(tpbta, tpProof);
        emit VerifyProof(tpbta.crossChainLane, result);
        require(result, "verify not pass");
    }

    function getSupportedPTCType() external override view returns (PTCTypeEnum[] memory) {
        return ptcTypeSupported;
    }

    function addPtcVerifier(address verifierContract) external override onlyOwner() {
        PTCTypeEnum ptcType = IPtcVerifier(verifierContract).myPtcType();

        require(verifierMap[ptcType] == address(0x0), "ptc verifier already exists");

        ptcTypeSupported.push(ptcType);
        verifierMap[ptcType] = verifierContract;

        emit AddPtcVerifier(ptcType, verifierContract);
    }

    function removePtcVerifier(PTCTypeEnum ptcType) external override onlyOwner() {
        require(verifierMap[ptcType] != address(0x0), "ptc verifier not exists");
        for (uint i = 1; i < ptcTypeSupported.length; i++) {
            if (ptcTypeSupported[i] == ptcType) {
                ptcTypeSupported[i] = ptcTypeSupported[ptcTypeSupported.length - 1];
                ptcTypeSupported.pop();
                break;
            }
        }
        delete verifierMap[ptcType];

        emit RemovePtcVerifier(ptcType);
    }

    function _getTpBta(bytes memory crossChainLane, uint256 versionNum) internal view returns (TpBta memory) {
        return PtcLib.decodeTpBtaFrom(tpBtaMap[keccak256(crossChainLane)].mapByVersion[versionNum]);
    }

    function _getTpBta(bytes memory crossChainLane) internal view returns (TpBta memory) {
        TpBtaStorage storage s = tpBtaMap[keccak256(crossChainLane)];
        require(s.mapByVersion[s.latestVersion].length > 0, "none tpbta found");
        return PtcLib.decodeTpBtaFrom(s.mapByVersion[s.latestVersion]);
    }

    function _hasPTCTrustRoot(ObjectIdentity memory ptcOwnerOid)
        internal
        view
        returns (bool)
    {
        return ptcTrustRootMap[keccak256(ptcOwnerOid.encode())].rawPtcTrustRoot.length > 0;
    }

    function _getPTCTrustRoot(ObjectIdentity memory ptcOwnerOid)
        internal
        view
        returns (PtcTrustRoot memory)
    {
        return PtcLib.decodePtcTrustRootFrom(ptcTrustRootMap[keccak256(ptcOwnerOid.encode())].rawPtcTrustRoot);
    }

    function _hasPTCVerifyAnchor(ObjectIdentity memory ptcOwnerOid, uint256 versionNum)
        internal
        view
        returns (bool)
    {
        return ptcTrustRootMap[keccak256(ptcOwnerOid.encode())].verifyAnchorMap[versionNum].length > 0;
    }

    function _getPTCVerifyAnchor(ObjectIdentity memory ptcOwnerOid, uint256 versionNum)
        internal
        view
        returns (PTCVerifyAnchor memory)
    {
        return PtcLib.decodePTCVerifyAnchorFrom(ptcTrustRootMap[keccak256(ptcOwnerOid.encode())].verifyAnchorMap[versionNum]);
    }

    /**
     * @dev This empty reserved space is put in place to allow future versions to add new
     * variables without shifting down storage in the inheritance chain.
     * See https://docs.openzeppelin.com/contracts/4.x/upgradeable#storage_gaps
     */
    uint256[50] private __gap;
}
