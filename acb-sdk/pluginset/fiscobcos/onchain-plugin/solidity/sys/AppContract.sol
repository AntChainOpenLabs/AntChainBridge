// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./interfaces/IContractUsingSDP.sol";
import "./interfaces/ISDPMessage.sol";
import "./lib/utils/Ownable.sol";

contract AppContract is IContractUsingSDP, Ownable {

    event AckOnSuccess(bytes32 messageId, string receiverDomain, bytes32 receiver, uint32 sequence, uint64 nonce, bytes message);

    event AckOnError(bytes32 messageId, string receiverDomain, bytes32 receiver, uint32 sequence, uint64 nonce, bytes message, string errorMsg);

    mapping(bytes32 => bytes[]) public recvMsg;

    mapping(bytes32 => bytes[]) public sendMsg;

    address public sdpAddress;

    bytes32 public latest_msg_id_sent_order;
    bytes32 public latest_msg_id_sent_unorder;
    bytes32 public latest_msg_id_ack_success;
    bytes32 public latest_msg_id_ack_error;
    string public latest_msg_error;

    bytes public last_msg;
    bytes public last_uo_msg;

    event recvCrosschainMsg(string senderDomain, bytes32 author, bytes message, bool isOrdered);

    event sendCrosschainMsg(string receiverDomain, bytes32 receiver, bytes  message, bool isOrdered);

    modifier onlySdpMsg() {
        require(msg.sender == sdpAddress, "INVALID_PERMISSION");
        _;
    }

    function setProtocol(address protocolAddress) external onlyOwner {
        sdpAddress = protocolAddress;
    }

    function recvUnorderedMessage(string memory senderDomain, bytes32 author, bytes memory message) external override onlySdpMsg {
        recvMsg[author].push(message);
        last_uo_msg = message;
        emit recvCrosschainMsg(senderDomain, author, message, false);
    }

    function recvMessage(string memory senderDomain, bytes32 author, bytes memory message) external override onlySdpMsg {
        recvMsg[author].push(message);
        last_msg = message;
        emit recvCrosschainMsg(senderDomain, author, message, true);
    }

    function sendUnorderedMessage(string memory receiverDomain, bytes32 receiver, bytes memory message) external {
        ISDPMessage(sdpAddress).sendUnorderedMessage(receiverDomain, receiver, message);

        sendMsg[receiver].push(message);
        emit sendCrosschainMsg(receiverDomain, receiver, message, false);
    }

    function sendMessage(string memory receiverDomain, bytes32 receiver, bytes memory message) external{

        ISDPMessage(sdpAddress).sendMessage(receiverDomain, receiver, message);

        sendMsg[receiver].push(message);
        emit sendCrosschainMsg(receiverDomain, receiver, message, true);
    }

    function sendV2(bytes32 receiver, string memory domain, bool atomic, bytes memory _msg) public {

        ISDPMessage sdp = ISDPMessage(sdpAddress);
        latest_msg_id_sent_order = sdp.sendMessageV2(domain, receiver, atomic, _msg);
    }

    function sendUnorderedV2(bytes32 receiver, string memory domain, bool atomic, bytes memory _msg) public {

        ISDPMessage sdp = ISDPMessage(sdpAddress);
        latest_msg_id_sent_unorder = sdp.sendUnorderedMessageV2(domain, receiver, atomic, _msg);
    }

    function ackOnSuccess(bytes32 messageId, string memory receiverDomain, bytes32 receiver, uint32 sequence, uint64 nonce, bytes memory message) public {
        emit AckOnSuccess(messageId, receiverDomain, receiver, sequence, nonce, message);
        latest_msg_id_ack_success = messageId;
    }

    function ackOnError(bytes32 messageId, string memory receiverDomain, bytes32 receiver, uint32 sequence, uint64 nonce, bytes memory message, string memory errorMsg) public {
        emit AckOnError(messageId, receiverDomain, receiver, sequence, nonce, message, errorMsg);
        latest_msg_id_ack_error = messageId;
        latest_msg_error = errorMsg;
    }

    function getLastUnorderedMsg() public view returns (bytes memory) {
        return last_uo_msg;
    }

    function getLastMsg() public view returns (bytes memory) {
        return last_msg;
    }

    /**
     * @dev This empty reserved space is put in place to allow future versions to add new
     * variables without shifting down storage in the inheritance chain.
     * See https://docs.openzeppelin.com/contracts/4.x/upgradeable#storage_gaps
     */
    uint256[50] private __gap;
}