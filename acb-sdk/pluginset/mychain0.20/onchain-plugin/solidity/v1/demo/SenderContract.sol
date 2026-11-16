pragma solidity ^0.6.4;

    struct BlockState {
        uint16 version;
        string domain;
        bytes32 blockHash;
        uint256 blockHeight;
        uint64 blockTimestamp;
    }

interface ProtocolInterface {

    // 客户合约call SDP合约发送消息
    function sendMessage(string calldata _destination_domain, identity _receiver, bytes calldata _message) external;

    function sendUnorderedMessage(string calldata _destination_domain, identity _receiver, bytes calldata _message) external;

    function sendMessageV2(string calldata receiverDomain, identity receiverID, bool atomic, bytes calldata message) external returns (bytes32);

    function sendUnorderedMessageV2(string calldata receiverDomain, identity receiverID, bool atomic, bytes calldata message) external returns (bytes32);

    function sendMessageV3(string calldata receiverDomain, identity receiverID, bool atomic, bytes calldata message, uint8 _timeoutMeasure, uint256 _timeout) external returns (bytes32);

    function sendUnorderedMessageV3(string calldata receiverDomain, identity receiverID, bool atomic, bytes calldata message, uint8 _timeoutMeasure, uint256 _timeout) external returns (bytes32);
}

interface IContractWithAcks {

    /**
     * @dev SDP contract would call this function to deliver the ack response from receiver 
     * contract. The original message being acked now is processed successfully on receiving 
     * blockchain. And the original sdp packet is sent as unordered message.
     *
     * @param messageId the message id for sdp packet used to send
     * @param receiverDomain the domain name of the receiving blockchain for original message and where the ack response from.
     * @param receiver the id of the receiver for original message.
     * @param sequence sequence number of original message from sender to receiver.
     * @param nonce unique number of original message from sender to receiver.
     * @param message the message sent to receiver.
     */
    function ackOnSuccess(bytes32 messageId, string calldata receiverDomain, identity receiver, uint32 sequence, uint64 nonce, bytes calldata message) external;

    /**
     * @dev SDP contract would call this function to deliver the ack response from receiver 
     * contract. The original message being acked now is processed with errors on receiving 
     * blockchain. And the original sdp packet is sent as unordered message.
     *
     * @param messageId the message id for sdp packet used to send
     * @param receiverDomain the domain name of the receiving blockchain for original message and where the ack response from.
     * @param receiver the id of the receiver for original message.
     * @param sequence sequence number of original message from sender to receiver.
     * @param nonce unique number of original message from sender to receiver.
     * @param message the message sent to receiver.
     * @param errorMsg the error _msg from receiver.
     */
    function ackOnError(bytes32 messageId, string calldata receiverDomain, identity receiver, uint32 sequence, uint64 nonce, bytes calldata message, string calldata errorMsg) external;
}

contract SenderContract is IContractWithAcks {

    identity public sdp_address;
    bytes32 public latest_msg_id_sent_order;
    bytes32 public latest_msg_id_sent_unorder;
    bytes32 public latest_msg_id_ack_success;
    bytes32 public latest_msg_id_ack_error;
    string public latest_msg_error;

    event AckOnSuccess(bytes32 messageId, string receiverDomain, identity receiver, uint32 sequence, uint64 nonce, bytes message);

    event AckOnError(bytes32 messageId, string receiverDomain, identity receiver, uint32 sequence, uint64 nonce, bytes message, string errorMsg);

    constructor() public {}

    function setSDPMSGAddress(identity _sdp_address) public {
        sdp_address = _sdp_address;
    }

    function send(identity receiver, string memory domain, bytes memory _msg) public {

        ProtocolInterface sdp = ProtocolInterface(sdp_address);
        sdp.sendMessage(domain, receiver, _msg);
    }

    function sendUnordered(identity receiver, string memory domain, bytes memory _msg) public {

        ProtocolInterface sdp = ProtocolInterface(sdp_address);
        sdp.sendUnorderedMessage(domain, receiver, _msg);
    }

    function sendV2(identity receiver, string memory domain, bool atomic, bytes memory _msg) public {

        ProtocolInterface sdp = ProtocolInterface(sdp_address);
        latest_msg_id_sent_order = sdp.sendMessageV2(domain, receiver, atomic, _msg);
    }

    function sendUnorderedV2(identity receiver, string memory domain, bool atomic, bytes memory _msg) public {

        ProtocolInterface sdp = ProtocolInterface(sdp_address);
        latest_msg_id_sent_unorder = sdp.sendUnorderedMessageV2(domain, receiver, atomic, _msg);
    }


    function sendV3(identity receiver, string memory domain, bool atomic, bytes memory _msg, uint8 _timeoutMeasure, uint256 _timeout) public {

        ProtocolInterface sdp = ProtocolInterface(sdp_address);
        latest_msg_id_sent_order = sdp.sendMessageV3(domain, receiver, atomic, _msg, _timeoutMeasure, _timeout);
    }

    function sendUnorderedV3(identity receiver, string memory domain, bool atomic, bytes memory _msg, uint8 _timeoutMeasure, uint256 _timeout) public {

        ProtocolInterface sdp = ProtocolInterface(sdp_address);
        latest_msg_id_sent_unorder = sdp.sendUnorderedMessageV3(domain, receiver, atomic, _msg, _timeoutMeasure, _timeout);
    }

    function ackOnSuccess(bytes32 messageId, string memory receiverDomain, identity receiver, uint32 sequence, uint64 nonce, bytes memory message) override public {
        emit AckOnSuccess(messageId, receiverDomain, receiver, sequence, nonce, message);
        latest_msg_id_ack_success = messageId;
    }

    function ackOnError(bytes32 messageId, string memory receiverDomain, identity receiver, uint32 sequence, uint64 nonce, bytes memory message, string memory errorMsg) override public {
        emit AckOnError(messageId, receiverDomain, receiver, sequence, nonce, message, errorMsg);
        latest_msg_id_ack_error = messageId;
        latest_msg_error = errorMsg;
    }
}
