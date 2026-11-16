pragma solidity ^0.4.22;

import "./utils/SizeOf.sol";
import "./utils/TypesToBytes.sol";
import "./utils/BytesToTypes.sol";
import "./interface/ProtocolInterface.sol";
import "./interface/AuthMsgClientInterface.sol";

contract P2PMsg is ProtocolInterface {

    mapping(identity => bool) contract_admins;

    identity am_contract;

    bytes32 expected_domain_hash_id;

    enum recvMsgStatus{BIZ_SUCCESS, BIZ_REVERTED, ACL_REJECTED}

    struct SendContext {
        uint32 sequence;
    }

    struct RecvContext {
        uint32 sequence;
    }

    mapping(bytes32 => SendContext) send_info;
    mapping(bytes32 => RecvContext) recv_info;

    constructor() public {
        contract_admins[msg.sender] = true;
    }

    event debugLogger(string log);
    event debugLoggerString(string log, string log2);
    event sentMessage(identity msg_sender, string dest_domain, identity msg_receiver, uint32 send_sequence);
    event receiveMessage(string domain_name, identity msg_sender, identity msg_receiver, uint32 recv_sequence, uint32 recv_status);


    uint32 constant UNORDERED_SEQUENCE = 0xffffffff;
    string constant RECV_UNORDERED_MSG_METHOD_SIGN = "recvUnorderedMessage(string,identity,bytes)";
    string constant RECV_ORDERED_MSG_METHOD_SIGN = "recvMessage(string,identity,bytes)";

    modifier onlyAdmins() {
        require(contract_admins[msg.sender], "PERMISSION_ERROR: admin only!");
        _;
    }

    modifier onlyAmClient() {
        require(msg.sender == am_contract, "PERMISSION_ERROR: amclient only!");
        _;
    }

    function SetAmContract(identity _am_contract) onlyAdmins public {
        am_contract = _am_contract;
    }

    function SetAmContractAndDomain(identity _am_contract, string expected_domain_name) onlyAdmins public {
        am_contract = _am_contract;
        expected_domain_hash_id = keccak256(abi.encodePacked(expected_domain_name));
    }

    // 从AM 合约接收到消息
    /*
     * 消息格式：
     * destination domain hash_id
     * destination identity
     * uint32 sequence
     * bytes  message
     */
    function recvMessage(string _domain_name, identity _author, bytes _package) onlyAmClient external {

        bytes32 _domain_hash_id = keccak256(abi.encodePacked(_domain_name));

        identity receiver;
        bytes memory message;
        uint32 recvSeq;
        (receiver, message, recvSeq) = parseMessage(_package);

        string memory cbMethod;
        if (recvSeq == UNORDERED_SEQUENCE) {
            emit debugLogger("receive unordered message");
            cbMethod = RECV_UNORDERED_MSG_METHOD_SIGN;
        } else {
            emit debugLogger("receive ordered message");
            // 检查接收消息序号
            bytes32 ctx_hash_id = keccak256(abi.encodePacked(_domain_hash_id, _author, receiver));
            if (recvSeq != recv_info[ctx_hash_id].sequence) {
                revert("SEQ_ERROR: sequence invalid!");
            }
            recv_info[ctx_hash_id].sequence++;
            cbMethod = RECV_ORDERED_MSG_METHOD_SIGN;
        }

        // 回调用户合约
        bool result = receiver.call(abi.encodeWithSignature(cbMethod, _domain_name, _author, message));

        // result => Enum state
        uint32 recv_msg_status;
        if (result) {
            recv_msg_status = uint32(recvMsgStatus.BIZ_SUCCESS);
        } else {
            recv_msg_status = uint32(recvMsgStatus.BIZ_REVERTED);
        }

        emit receiveMessage(_domain_name, _author, receiver, recvSeq, recv_msg_status);
    }


    /*
     * OracleService提交被拒绝消息，增大sequence
     */
    function rejectMessage(string _domain_name, identity _author, identity _receiver, uint32 _recv_seq) onlyAdmins public {

        bytes32 _domain_hash_id = keccak256(abi.encodePacked(_domain_name));

        bytes32 ctx_hash_id = keccak256(abi.encodePacked(_domain_hash_id, _author, _receiver));
        if (_recv_seq != recv_info[ctx_hash_id].sequence) {
            revert("SEQ_ERROR: sequence invalid!");
        }

        recv_info[ctx_hash_id].sequence++;

        emit receiveMessage(_domain_name, _author, _receiver, _recv_seq, uint32(recvMsgStatus.ACL_REJECTED));
    }

    function setMsgSeq(string _domain_name, identity _author, identity _receiver, uint32 _new_seq) onlyAdmins public {
        bytes32 _domain_hash_id = keccak256(abi.encodePacked(_domain_name));
        bytes32 ctx_hash_id = keccak256(abi.encodePacked(_domain_hash_id, _author, _receiver));

        recv_info[ctx_hash_id].sequence = _new_seq;
    }

    function parseMessage(bytes _package) internal returns (identity, bytes, uint32){
        uint256 offset = _package.length;

        uint32 dest_domain_len = BytesToTypes.bytesToUint32(offset, _package) + 32;
        bytes memory dest_domain = new bytes(dest_domain_len);
        BytesToTypes.bytesToString(offset, _package, dest_domain);
        offset -= SizeOf.sizeOfBytes(dest_domain);

        bytes32 dest_domain_hash_id = keccak256(abi.encodePacked(dest_domain));
        if (expected_domain_hash_id != dest_domain_hash_id) {
            revert("P2P_MSG_ERROR: invalid destination domain!");
        }

        identity receiver = BytesToTypes.bytesToIdentity(offset, _package);
        offset -= SizeOf.sizeOfIdentity();

        uint32 sequence = BytesToTypes.bytesToUint32(offset, _package);
        offset -= SizeOf.sizeOfInt(32);

        uint32 message_len = BytesToTypes.bytesToUint32(offset, _package) + 32;
        bytes memory message = new bytes(message_len);
        BytesToTypes.bytesToString(offset, _package, message);
        offset -= SizeOf.sizeOfBytes(message);

        return (receiver, message, sequence);
    }

    // 发送消息给AM合约
    /*
     * 消息格式：
     * destination domain           (32 + N bytes)
     * destination identity         (32 bytes)
     * uint32 sequence              (4  bytes)
     * bytes  message               (32 + N)
     */
    function sendMessage(string _destination_domain, identity _receiver, bytes _message) external {

        emit debugLoggerString("send message to", _destination_domain);

        bytes memory package;
        uint32 sendSeq;
        (package, sendSeq) = buildOrderedMessage(_destination_domain, _receiver, _message);
        emit debugLogger("build p2p message success");

        emit debugLogger("begin to call am client");
        // 调用AM client
        AuthMsgClientInterface am = AuthMsgClientInterface(am_contract);
        am.recvFromProtocol(msg.sender, package);
        emit debugLogger("called am client success");

        // 通知链下
        emit sentMessage(msg.sender, _destination_domain, _receiver, sendSeq);
    }

    // 发送无序消息
    function sendUnorderedMessage(string _destination_domain, identity _receiver, bytes _message) external {

        emit debugLoggerString("send message to", _destination_domain);

        bytes memory package;
        uint32 sendSeq;
        (package, sendSeq) = buildUnorderedMessage(_destination_domain, _receiver, _message);
        emit debugLogger("build p2p message success");

        emit debugLogger("begin to call am client");
        // 调用AM client
        AuthMsgClientInterface am = AuthMsgClientInterface(am_contract);
        am.recvFromProtocol(msg.sender, package);
        emit debugLogger("called am client success");

        // 通知链下
        emit sentMessage(msg.sender, _destination_domain, _receiver, sendSeq);
    }

    // 构造有序消息
    function buildOrderedMessage(string _destination_domain, identity _receiver, bytes _message) internal returns (bytes, uint32) {

        uint256 len = SizeOf.sizeOfBytes(_message) + 4 + 32 + SizeOf.sizeOfString(_destination_domain);

        bytes memory package = new bytes(len);
        uint offset = len;

        // 填充接受者的domain
        bytes32 domain_hash_id = keccak256(abi.encodePacked(_destination_domain));
        TypesToBytes.stringToBytes(offset, bytes(_destination_domain), package);
        offset -= SizeOf.sizeOfString(_destination_domain);

        // 填充接受者identity
        TypesToBytes.identityToBytes(offset, _receiver, package);
        offset -= SizeOf.sizeOfIdentity();

        // 填充sequence
        bytes32 ctx_hash_id = keccak256(abi.encodePacked(msg.sender, domain_hash_id, _receiver));
        TypesToBytes.uintToBytes(offset, send_info[ctx_hash_id].sequence, package);
        offset -= SizeOf.sizeOfUint(32);

        // 序号加一
        send_info[ctx_hash_id].sequence++;

        // 填充消息
        TypesToBytes.stringToBytes(offset, _message, package);
        offset -= SizeOf.sizeOfBytes(_message);

        return (package, send_info[ctx_hash_id].sequence);
    }

    // 构造无序消息
    function buildUnorderedMessage(string _destination_domain, identity _receiver, bytes _message) internal returns (bytes, uint32) {

        uint256 len = SizeOf.sizeOfBytes(_message) + 4 + 32 + SizeOf.sizeOfString(_destination_domain);

        bytes memory package = new bytes(len);
        uint offset = len;

        // 填充接受者的domain
        bytes32 domain_hash_id = keccak256(abi.encodePacked(_destination_domain));
        TypesToBytes.stringToBytes(offset, bytes(_destination_domain), package);
        offset -= SizeOf.sizeOfString(_destination_domain);

        // 填充接受者identity
        TypesToBytes.identityToBytes(offset, _receiver, package);
        offset -= SizeOf.sizeOfIdentity();

        // 填充unordered sequence
        TypesToBytes.uintToBytes(offset, UNORDERED_SEQUENCE, package);
        offset -= SizeOf.sizeOfUint(32);

        // 填充消息
        TypesToBytes.stringToBytes(offset, _message, package);
        offset -= SizeOf.sizeOfBytes(_message);

        return (package, UNORDERED_SEQUENCE);
    }


    function queryP2PMsgSeqOnChain(bytes _domain_name, identity _from, bytes _dest_domain_name, identity _to) onlyAdmins public view returns (uint32) {
        bytes32 dest_domain_hash_id = keccak256(abi.encodePacked(_dest_domain_name));
        if (expected_domain_hash_id != dest_domain_hash_id) {
            revert("P2P_MSG_ERROR: invalid destination domain!");
        }

        bytes32 _domain_hash_id = keccak256(abi.encodePacked(_domain_name));
        bytes32 ctx_hash_id = keccak256(abi.encodePacked(_domain_hash_id, _from, _to));
        return recv_info[ctx_hash_id].sequence;
    }

    function getLocalDomainHash() public view returns (bytes32) {
        return expected_domain_hash_id;
    }
}
