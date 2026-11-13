pragma solidity ^0.4.22;

import "./utils/utils.sol";
import "./utils/strings.sol";
import "./utils/TypesToBytes.sol";
import "./utils/BytesToTypes.sol";
import "./utils/SizeOf.sol";
import "./utils/AMLib.sol";
import "./interface/ProtocolInterface.sol";
import "./interface/AuthMsgClientInterface.sol";

contract AuthMsgClient is AuthMsgClientInterface {

    // admin
    mapping(identity => bool) contract_admins;

    struct RelayerInfo {
        bool if_exists;
    }

    // relayer
    mapping(identity => RelayerInfo) allowed_relayers;
    identity[] relayer_list;

    // upper protocols
    struct ProtocolInfo {
        uint32 protocol_type;
        bool if_exists;
    }

    mapping(identity => ProtocolInfo) allowed_protocols;

    // protocol type to protocol mapping
    struct RouteInfo {
        identity protocol;
        bool if_exists;
    }

    mapping(uint32 => RouteInfo) protocol_routes;

    // event
    event SendAuthMessage(bytes package);
    event RecvAuthMessage(identity txhash);
    event ParseAuthMessage(bytes message);
    event UpdateProtocol(identity account, uint32 protocol_type);

    event debugLogger(string log);
    event debugLoggerBytes32(bytes32 log);


    modifier onlyAdmins() {
        require(contract_admins[msg.sender], "PERMISSION_ERROR: admin only");
        _;
    }

    modifier onlyRelayers() {
        require(allowed_relayers[msg.sender].if_exists, "PERMISSION_ERROR: relayer only");
        _;
    }

    modifier onlyProtocols() {
        require(allowed_protocols[msg.sender].if_exists, "PERMISSION_ERROR: protocol only");
        _;
    }

    constructor() public {
        contract_admins[msg.sender] = true;
    }

    function addRelayers(identity _relayer) onlyAdmins public {
        if (!allowed_relayers[_relayer].if_exists) {
            allowed_relayers[_relayer].if_exists = true;
            relayer_list.push(_relayer);
        }
    }

    /**
     * @dev query the number of whole allowed relayers
   */
    function queryRelayersNum() onlyAdmins public view returns (uint256){
        return relayer_list.length;
    }

    /**
     * @dev query the whole allowed relayers
   */
    function queryRelayers() onlyAdmins public view returns (identity[]){
        return relayer_list;
    }

    /**
     * @dev  set protocol
   */
    function setProtocol(identity _protocol, uint32 _protocol_type) onlyAdmins public {
        allowed_protocols[_protocol].if_exists = true;
        allowed_protocols[_protocol].protocol_type = _protocol_type;

        protocol_routes[_protocol_type].protocol = _protocol;
        protocol_routes[_protocol_type].if_exists = true;

        emit UpdateProtocol(_protocol, _protocol_type);
    }

    // 从protocol合约收到消息
    /*
     *  报文格式:
     *  version          (4 byte)
     *  identity         (32byte)
     *  protocol type    (4 byte)
     *  message          (variable) 32 + N
     */
    // add
    function recvFromProtocol(identity author, bytes message) onlyProtocols external {

        emit debugLogger("recv message from protocol");

        uint256 len = SizeOf.sizeOfBytes(message) + 4 + 32 + 4;
        bytes memory package = new bytes(len);
        uint offset = len;

        uint32 version = 1;
        TypesToBytes.uintToBytes(offset, version, package);
        offset -= SizeOf.sizeOfInt(32);

        TypesToBytes.identityToBytes(offset, author, package);
        offset -= SizeOf.sizeOfIdentity();

        uint32 protocol_type = allowed_protocols[msg.sender].protocol_type;
        TypesToBytes.uintToBytes(offset, protocol_type, package);
        offset -= SizeOf.sizeOfInt(32);

        TypesToBytes.stringToBytes(offset, message, package);
        offset -= SizeOf.sizeOfBytes(message);

        emit debugLogger("build am message success");

        emit SendAuthMessage(package);

        emit debugLogger("write am message success");
    }


    // 解析AM package
    /*
     *  报文格式:
     *  version          (4 byte)
     *  identity         (32byte)
     *  protocol type    (4 byte)
     *  message          (variable) 32 + N
     */
    function ParseAMPackage(bytes _package) internal returns (identity author, uint32 protocol_type, bytes message) {

        // emit ParseAuthMessage(_package);

        uint256 offset = _package.length;

        uint32 version = BytesToTypes.bytesToUint32(offset, _package);
        offset -= SizeOf.sizeOfInt(32);
        if (version != 1) {
            revert("PARSE_ERROR: non supported AM package version!");
        }

        author = BytesToTypes.bytesToIdentity(offset, _package);
        offset -= SizeOf.sizeOfIdentity();

        protocol_type = BytesToTypes.bytesToUint32(offset, _package);
        offset -= SizeOf.sizeOfInt(32);

        message = new bytes(BytesToTypes.getStringSize(offset, _package));
        BytesToTypes.bytesToString(offset, _package, message);
        offset -= SizeOf.sizeOfBytes(message);

        return (author, protocol_type, message);
    }

    /*
    * recv proof_data from relayer
    */
    function recvProofData(bytes _data, bytes _hints, bool _verify) internal {
        string memory domain_name;
        bytes memory package;

        // Adapt the design of accessing heterogeneous chain
        (domain_name, package) = AMLib.decodeProof(_data);

        recvAmPkg(domain_name, package);
    }


    /*
     * recv proof_data from relayer
     */
    function recvAmPkg(string _domain_name, bytes _package) internal {
        // 拆解AM报文，提取identity, protocol type
        identity author;
        uint32 protocol_type;
        bytes    memory message;

        emit debugLogger("begin ParseAMPackage");
        (author, protocol_type, message) = ParseAMPackage(bytes(_package));
        emit debugLogger("finish ParseAMPackage");

        // 根据protocol type路由选择上层protocol合约
        if (!protocol_routes[protocol_type].if_exists) {
            revert("ROUTE_ERROR: non-recoganized protocol type!");
        }

        emit debugLogger("begin callback protocal ");
        ProtocolInterface protocol_contract = ProtocolInterface(protocol_routes[protocol_type].protocol);

        // 调用上层protocol合约，传入protocol
        protocol_contract.recvMessage(_domain_name, author, message);
        emit debugLogger("end callback protocal ");
        // 结束
    }

    /*
     * recv proof_data pkg from relayer
     */
    function recvPkgFromRelayer(bytes amPkg) onlyRelayers external {
        emit RecvAuthMessage(tx.txhash);
        emit debugLogger("begin recvPkgFromRelayer");
        uint256 _len = amPkg.length;
        uint256 _offset = 0;

        while (_offset < _len) {
            bytes memory hints_len_bytes = utils.bytesCopy(_offset, amPkg, 4);
            // 4 bytes for hints length
            _offset += 4;
            uint32 hints_len = BytesToTypes.bytesToUint32(4, hints_len_bytes);
            // hints length
            bytes memory hints = utils.bytesCopy(_offset, amPkg, hints_len);
            _offset += hints_len;

            bytes memory proof_len_bytes = utils.bytesCopy(_offset, amPkg, 4);
            // 4 bytes for proof length
            _offset += 4;
            uint32 proof_len = BytesToTypes.bytesToUint32(4, proof_len_bytes);
            // proof length
            bytes memory proof = utils.bytesCopy(_offset, amPkg, proof_len);
            _offset += proof_len;

            emit debugLogger("begin recvProofData with proof and hints");
            // call recvProofData to transfer message
            recvProofData(proof, hints, true);
            emit debugLogger("finish recvProofData ");
        }
        emit debugLogger("finish recvPkgFromRelayer");
    }

    /*
      * recv proof_data pkg from relayer
      * 上链数据可信程度高，跳过TEE proof 验证阶段，只留存证
      */
    function recvPkgFromRelayerTrusted(bytes amPkg) onlyRelayers external {
        emit RecvAuthMessage(tx.txhash);
        emit debugLogger("begin recvPkgFromRelayer");
        uint256 _len = amPkg.length;
        uint256 _offset = 0;

        while (_offset < _len) {
            bytes memory hints_len_bytes = utils.bytesCopy(_offset, amPkg, 4);
            // 4 bytes for hints length
            _offset += 4;
            uint32 hints_len = BytesToTypes.bytesToUint32(4, hints_len_bytes);
            // hints length
            bytes memory hints = utils.bytesCopy(_offset, amPkg, hints_len);
            _offset += hints_len;

            bytes memory proof_len_bytes = utils.bytesCopy(_offset, amPkg, 4);
            // 4 bytes for proof length
            _offset += 4;
            uint32 proof_len = BytesToTypes.bytesToUint32(4, proof_len_bytes);
            // proof length
            bytes memory proof = utils.bytesCopy(_offset, amPkg, proof_len);
            _offset += proof_len;

            emit debugLogger("begin recvProofData with proof and hints");
            // call recvProofData to transfer message
            recvProofData(proof, hints, false);
            emit debugLogger("finish recvProofData");
        }
        emit debugLogger("finish recvPkgFromRelayer");
    }

    /*
      * recv proof_data pkg from relayer
      * 上链数据可信程度高，直接传 am + proof data，存证使用
      */
    function recvNotaryPkgFromRelayer(string _domain_name, bytes _am_pkg) onlyRelayers external {
        emit RecvAuthMessage(tx.txhash);
        emit debugLogger("begin recvNotaryPkgFromRelayer");
        uint256 _len = _am_pkg.length;
        uint256 _offset = 0;

        while (_offset < _len) {
            bytes memory hints_len_bytes = utils.bytesCopy(_offset, _am_pkg, 4);
            // 4 bytes for hints length
            _offset += 4;
            uint32 hints_len = BytesToTypes.bytesToUint32(4, hints_len_bytes);
            // hints length
            _offset += hints_len;
            // 跳过hints, proof data只做存证

            bytes memory proof_len_bytes = utils.bytesCopy(_offset, _am_pkg, 4);
            // 4 bytes for proof length
            _offset += 4;
            uint32 proof_len = BytesToTypes.bytesToUint32(4, proof_len_bytes);
            // proof length
            _offset += proof_len;
            // 跳过proof data， proof data只做存证

            bytes memory am_pkg_len_bytes = utils.bytesCopy(_offset, _am_pkg, 4);
            // 4 bytes for am_pkg length
            _offset += 4;
            uint32 am_pkg_len = BytesToTypes.bytesToUint32(4, am_pkg_len_bytes);
            // am_pkg length
            bytes memory am_pkg = utils.bytesCopy(_offset, _am_pkg, am_pkg_len);
            _offset += am_pkg_len;


            emit debugLogger("begin recvAmPkg with proof and hints");
            // call recvFromRelayer to transfer message
            recvAmPkg(_domain_name, am_pkg);
            emit debugLogger("finish recvAmPkg ");
        }
        emit debugLogger("finish recvPkgFromRelayer");
    }
}

