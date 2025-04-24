// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./interfaces/ISDPMessage.sol";
import "./interfaces/IAuthMessage.sol";
import "./interfaces/IContractUsingSDP.sol";
import "./interfaces/IContractWithAcks.sol";
import "./lib/sdp/SDPLib.sol";
import "./lib/utils/Ownable.sol";
import "./@openzeppelin/contracts/proxy/utils/Initializable.sol";


contract SDPMsg is ISDPMessage, Ownable, Initializable {

    using SDPLib for SDPMessage;
    using SDPLib for SDPMessageV2;
    using SDPLib for SDPMessageV3;
    using SDPLib for BlockState;

    address public amAddress;

    bytes32 public localDomainHash;

    mapping(bytes32 => uint32) sendSeq;
    mapping(bytes32 => uint32) recvSeq;

    mapping(bytes32 => uint32) sendNonce;
    mapping(bytes32 => bool) recvNonce;

    /**
    * 接收链已验证高度：接收链域名哈希 -> 已验证区块信息
    */
    mapping(bytes32 => BlockState) recvValidatedBlockState;

    /**
    * 记录已发送原子性消息的哈希
    */
    mapping(bytes32 => bool) sendSDPV3Msgs;

    modifier onlyAM() {
        require(
            amAddress == msg.sender,
            "SDPMsg: not valid am contract"
        );
        _;
    }

    constructor() {
        _disableInitializers();
    }

    function init() external initializer() {
        _transferOwnership(_msgSender());
    }

    function setAmContract(address newAmContract) override external onlyOwner {
        require(newAmContract != address(0), "SDPMsg: invalid am contract");
        amAddress = newAmContract;
    }

    function getAmAddress() external view returns (address) {
        return amAddress;
    }

    function setLocalDomain(string memory domain) external override onlyOwner {
        localDomainHash = keccak256(abi.encodePacked(domain));
    }

    function getLocalDomain() external view returns (bytes32) {
        return localDomainHash;
    }

    function sendMessage(string calldata receiverDomain, bytes32 receiverID, bytes calldata message) override external {
        _beforeSend(receiverDomain, receiverID, message);

        SDPMessage memory sdpMessage = SDPMessage(
            {
                receiveDomain: receiverDomain,
                receiver: receiverID,
                message: message,
                sequence: _getAndUpdateSendSeq(receiverDomain, msg.sender, receiverID)
            }
        );

        bytes memory rawMsg = sdpMessage.encode();

        IAuthMessage(amAddress).recvFromProtocol(msg.sender, rawMsg);

        _afterSend();
    }

    function sendUnorderedMessage(string calldata receiverDomain, bytes32 receiverID, bytes calldata message) override external {
        _beforeSendUnordered(receiverDomain, receiverID, message);

        SDPMessage memory sdpMessage = SDPMessage(
            {
                receiveDomain: receiverDomain,
                receiver: receiverID,
                message: message,
                sequence: SDPLib.UNORDERED_SEQUENCE
            }
        );

        IAuthMessage(amAddress).recvFromProtocol(msg.sender, sdpMessage.encode());

        _afterSendUnordered();
    }

    function sendMessageV2(string calldata receiverDomain, bytes32 receiverID, bool atomic, bytes calldata message) override external returns (bytes32) {
         _beforeSend(receiverDomain, receiverID, message);

        SDPMessageV2 memory sdpMessage = SDPMessageV2(
            {
                version: 2,
                messageId: bytes32(0),
                receiveDomain: receiverDomain,
                receiver: receiverID,
                atomicFlag: atomic ? SDPLib.SDP_V2_ATOMIC_FLAG_ATOMIC_REQUEST : SDPLib.SDP_V2_ATOMIC_FLAG_NONE_ATOMIC,
                nonce: SDPLib.MAX_NONCE,
                sequence: _getAndUpdateSendSeq(receiverDomain, msg.sender, receiverID),
                message: message,
                errorMsg: ""
            }
        );
        sdpMessage.calcMessageId(localDomainHash);

        IAuthMessage(amAddress).recvFromProtocol(msg.sender, sdpMessage.encode());

        _afterSend();

        return sdpMessage.messageId;
    }

    function sendUnorderedMessageV2(string calldata receiverDomain, bytes32 receiverID, bool atomic, bytes calldata message) override external returns (bytes32) {
        _beforeSendUnordered(receiverDomain, receiverID, message);

        SDPMessageV2 memory sdpMessage = SDPMessageV2(
            {
                version: 2,
                messageId: bytes32(0),
                receiveDomain: receiverDomain,
                receiver: receiverID,
                atomicFlag: atomic ? SDPLib.SDP_V2_ATOMIC_FLAG_ATOMIC_REQUEST : SDPLib.SDP_V2_ATOMIC_FLAG_NONE_ATOMIC,
                nonce: _getAndUpdateSendNonce(receiverDomain, msg.sender, receiverID),
                sequence: SDPLib.UNORDERED_SEQUENCE,
                message: message,
                errorMsg: ""
            }
        );
        sdpMessage.calcMessageId(localDomainHash);

        IAuthMessage(amAddress).recvFromProtocol(msg.sender, sdpMessage.encode());

        _afterSendUnordered();

        return sdpMessage.messageId;
    }

    // 发送有序消息 SDPv3
    function sendMessageV3(string calldata receiverDomain, bytes32 receiverID, bool atomic, bytes calldata message,
        uint8 _timeoutMeasure, uint256 _timeout) public returns (bytes32) {

        require(
            _timeoutMeasure >= SDPLib.SDP_V3_TIMEOUT_MEASUREMENT_NO_TIMEOUT && _timeoutMeasure <= SDPLib.SDP_V3_TIMEOUT_MEASUREMENT_RECEIVER_TIMESTAMP,
            "SDP: invalid timeout measure"
        );

        _beforeSendUnordered(receiverDomain, receiverID, message);

        bytes32 receiver = bytes32(receiverID);
        SDPMessageV3 memory sdpMessage = SDPMessageV3(
            {
                version: 3,
                messageId: bytes32(0),
                receiveDomain: receiverDomain,
                receiver: receiver,
                atomicFlag: atomic ? SDPLib.SDP_V3_ATOMIC_FLAG_ATOMIC_REQUEST : SDPLib.SDP_V3_ATOMIC_FLAG_NONE_ATOMIC,
                nonce: SDPLib.MAX_NONCE,
                sequence: _getAndUpdateSendSeq(receiverDomain, msg.sender, receiver),
                message: message,
                errorMsg: "",
                timeoutMeasure: _timeoutMeasure,
                timeout: _timeout
            }
        );
        sdpMessage.calcMessageId(localDomainHash);

        // v3消息才有的记录，用于超时回滚前的验证
        sendSDPV3Msgs[sdpMessage.messageId] = true;

        IAuthMessage(amAddress).recvFromProtocol(msg.sender, sdpMessage.encode());

        _afterSend();

        return sdpMessage.messageId;
    }

    // 发送无序消息 SDPv3
    function sendUnorderedMessageV3(string calldata receiverDomain, bytes32 receiverID, bool atomic, bytes calldata message,
        uint8 _timeoutMeasure, uint256 _timeout) public returns (bytes32) {

        require(
            _timeoutMeasure >= SDPLib.SDP_V3_TIMEOUT_MEASUREMENT_NO_TIMEOUT && _timeoutMeasure <= SDPLib.SDP_V3_TIMEOUT_MEASUREMENT_RECEIVER_TIMESTAMP,
            "SDP: invalid timeout measure"
        );

        _beforeSendUnordered(receiverDomain, receiverID, message);

        bytes32 receiver = bytes32(receiverID);
        SDPMessageV3 memory sdpMessage = SDPMessageV3(
            {
                version: 3,
                messageId: bytes32(0),
                receiveDomain: receiverDomain,
                receiver: receiver,
                atomicFlag: atomic ? SDPLib.SDP_V3_ATOMIC_FLAG_ATOMIC_REQUEST : SDPLib.SDP_V3_ATOMIC_FLAG_NONE_ATOMIC,
                nonce: _getAndUpdateSendNonce(receiverDomain, msg.sender, receiver),
                sequence: SDPLib.UNORDERED_SEQUENCE,
                message: message,
                errorMsg: "",
                timeoutMeasure: _timeoutMeasure,
                timeout: _timeout
            }
        );
        sdpMessage.calcMessageId(localDomainHash);

        sendSDPV3Msgs[sdpMessage.messageId] = true;

        IAuthMessage(amAddress).recvFromProtocol(msg.sender, sdpMessage.encode());

        _afterSend();

        return sdpMessage.messageId;
    }

    function recvMessage(string calldata senderDomain, bytes32 senderID, bytes calldata pkg) override external onlyAM {
        _beforeRecv(senderDomain, senderID, pkg);

        uint32 version = SDPLib.getSDPVersionFrom(pkg);
        if (version == 1) {
            _processSDPv1(senderDomain, senderID, pkg);
        } else if (version == 2) {
            _processSDPv2(senderDomain, senderID, pkg);
        } else if (version == 3) {
            _processSDPv3(senderDomain, senderID, pkg);
        } else {
            revert("unsupported sdp version");
        }

        _afterRecv();
    }

    function querySDPMessageSeq(string calldata senderDomain, bytes32 senderID, string calldata receiverDomain, bytes32 receiverID) override external view returns (uint32) {
        require(
            keccak256(abi.encodePacked(receiverDomain)) == localDomainHash,
            "SDPMsg: wrong receiving domain"
        );
        bytes32 seqKey = SDPLib.getReceivingSeqID(senderDomain, senderID, receiverID);
        uint32 seq = recvSeq[seqKey];
        return seq;
    }

    function _processSDPv1(string calldata senderDomain, bytes32 senderID, bytes memory pkg) internal {
        SDPMessage memory sdpMessage;
        sdpMessage.decode(pkg);

        require(
            keccak256(abi.encodePacked(sdpMessage.receiveDomain)) == localDomainHash,
            "SDPMsg: wrong receiving domain"
        );

        if (sdpMessage.sequence == SDPLib.UNORDERED_SEQUENCE) {
            _routeUnorderedMessage(senderDomain, senderID, sdpMessage);
        } else {
            _routeOrderedMessage(senderDomain, senderID, sdpMessage);
        }
    }

    function _routeOrderedMessage(string calldata senderDomain, bytes32 senderID, SDPMessage memory sdpMessage) internal {
        uint32 seqExpected = _getAndUpdateRecvSeq(senderDomain, senderID, sdpMessage.receiver);
        require(
            sdpMessage.sequence == seqExpected,
            "SDPMsg: sequence not equal"
        );

        bool res = false;
        string memory errMsg;
        address receiver = sdpMessage.getReceiverAddress();
        if (receiver.code.length == 0) {
            res = false;
            errMsg = "receiver has no code";
        } else {
            try
                IContractUsingSDP(receiver).recvMessage(senderDomain, senderID, sdpMessage.message)
            {
                res = true;
            } catch Error(
                string memory reason
            ) {
                errMsg = reason;
            } catch (
                bytes memory /*lowLevelData*/
            ) {}
        }

        emit receiveMessage(senderDomain, senderID, receiver, seqExpected, res, errMsg);
    }

    function _routeUnorderedMessage(string calldata senderDomain, bytes32 senderID, SDPMessage memory sdpMessage) internal {
        IContractUsingSDP(sdpMessage.getReceiverAddress())
                .recvUnorderedMessage(senderDomain, senderID, sdpMessage.message);
    }

    function _processSDPv2(string calldata senderDomain, bytes32 senderID, bytes memory pkg) internal {
        SDPMessageV2 memory sdpMessage;
        sdpMessage.decode(pkg);

        require(
            keccak256(abi.encodePacked(sdpMessage.receiveDomain)) == localDomainHash,
            "SDPMsg: wrong receiving domain"
        );

        if (
            sdpMessage.atomicFlag == SDPLib.SDP_V2_ATOMIC_FLAG_NONE_ATOMIC 
                || sdpMessage.atomicFlag == SDPLib.SDP_V2_ATOMIC_FLAG_ATOMIC_REQUEST
        ) {
            _processSDPv2Request(senderDomain, senderID, sdpMessage);
        } else if (sdpMessage.atomicFlag == SDPLib.SDP_V2_ATOMIC_FLAG_ACK_SUCCESS) {
            _processSDPv2AckSuccess(senderDomain, senderID, sdpMessage);
        } else if (sdpMessage.atomicFlag == SDPLib.SDP_V2_ATOMIC_FLAG_ACK_ERROR) {
           _processSDPv2AckError(senderDomain, senderID, sdpMessage);
        } else {
            revert("unexpected atomic flag");
        }
    }

    function _processSDPv2Request(string calldata senderDomain, bytes32 senderID, SDPMessageV2 memory sdpMessage) internal {
        bool res;
        string memory errMsg;
        if (sdpMessage.sequence == SDPLib.UNORDERED_SEQUENCE) {
            bytes32 nonceKey = SDPLib.getReceivingNonceID(senderDomain, senderID, sdpMessage.receiver, sdpMessage.nonce);
            require(!recvNonce[nonceKey], "SDPMsg: nonce has been processed");

            (res, errMsg) = _routeUnorderedMessageV2(senderDomain, senderID, sdpMessage);
            if (sdpMessage.atomicFlag == SDPLib.SDP_V2_ATOMIC_FLAG_NONE_ATOMIC) {
                require(res, errMsg);
            }

            recvNonce[nonceKey] = true;
        } else {
            (res, errMsg) = _routeOrderedMessageV2(senderDomain, senderID, sdpMessage);
        }

        emit ReceiveMessageV2(
            sdpMessage.messageId, 
            senderDomain,
            senderID, 
            sdpMessage.receiveDomain,
            sdpMessage.getReceiverAddress(),
            sdpMessage.sequence,
            sdpMessage.nonce, 
            sdpMessage.atomicFlag,
            res, 
            errMsg
        );

        if (sdpMessage.atomicFlag == SDPLib.SDP_V2_ATOMIC_FLAG_ATOMIC_REQUEST) {
            _ackSDPv2Request(sdpMessage, senderDomain, senderID, res, errMsg);
        }
    }

    function _routeOrderedMessageV2(string calldata senderDomain, bytes32 senderID, SDPMessageV2 memory sdpMessage) internal returns (bool, string memory) {
        uint32 seqExpected = _getAndUpdateRecvSeq(senderDomain, senderID, sdpMessage.receiver);
        require(sdpMessage.sequence == seqExpected, "SDPMsg: sequence not equal");
        
        bool res = false;
        string memory errMsg;
        address receiver = sdpMessage.getReceiverAddress();
        if (receiver.code.length == 0) {
            res = false;
            errMsg = "receiver has no code";
        } else {
            try
                IContractUsingSDP(receiver).recvMessage(senderDomain, senderID, sdpMessage.message)
            {
                res = true;
            } catch Error(
                string memory reason
            ) {
                errMsg = reason;
            } catch (
                bytes memory /*lowLevelData*/
            ) {}
        }

        return (res, errMsg);
    }

    function _routeUnorderedMessageV2(string calldata senderDomain, bytes32 senderID, SDPMessageV2 memory sdpMessage) internal returns (bool, string memory) {
        bool res = false;
        string memory errMsg;
        if (sdpMessage.getReceiverAddress().code.length == 0) {
            res = false;
            errMsg = "receiver has no code";
        } else {
            try
                IContractUsingSDP(sdpMessage.getReceiverAddress()).recvUnorderedMessage(senderDomain, senderID, sdpMessage.message)
            {
                res = true;
            } catch Error(
                string memory reason
            ) {
                errMsg = reason;
            } catch (
                bytes memory /*lowLevelData*/
            ) {}
        }
        
        return (res, errMsg);
    }

    function _ackSDPv2Request(SDPMessageV2 memory sdpMessage, string calldata senderDomain, bytes32 senderID, bool res, string memory errMsg) internal {
        address receiverAddr = sdpMessage.getReceiverAddress();

        sdpMessage.receiveDomain = senderDomain;
        sdpMessage.receiver = senderID;
        sdpMessage.atomicFlag = res ? SDPLib.SDP_V2_ATOMIC_FLAG_ACK_SUCCESS : SDPLib.SDP_V2_ATOMIC_FLAG_ACK_ERROR;
        sdpMessage.errorMsg = res ? "" : errMsg;

        IAuthMessage(amAddress).recvFromProtocol(receiverAddr, sdpMessage.encode());
    }

    function _processSDPv2AckSuccess(string calldata senderDomain, bytes32 senderID, SDPMessageV2 memory sdpMessage) internal {
        IContractWithAcks(sdpMessage.getReceiverAddress()).ackOnSuccess(
            sdpMessage.messageId,
            senderDomain,
            senderID,
            sdpMessage.sequence, 
            sdpMessage.nonce, 
            sdpMessage.message
        );
        emit ReceiveMessageV2(
            sdpMessage.messageId, 
            senderDomain,
            senderID, 
            sdpMessage.receiveDomain,
            sdpMessage.getReceiverAddress(),
            sdpMessage.sequence,
            sdpMessage.nonce, 
            sdpMessage.atomicFlag,
            true, 
            "success"
        );
    }

    function _processSDPv2AckError(string calldata senderDomain, bytes32 senderID, SDPMessageV2 memory sdpMessage) internal {
        IContractWithAcks(sdpMessage.getReceiverAddress()).ackOnError(
            sdpMessage.messageId,
            senderDomain,
            senderID,
            sdpMessage.sequence, 
            sdpMessage.nonce, 
            sdpMessage.message,
            sdpMessage.errorMsg
        );
        emit ReceiveMessageV2(
            sdpMessage.messageId, 
            senderDomain,
            senderID, 
            sdpMessage.receiveDomain,
            sdpMessage.getReceiverAddress(),
            sdpMessage.sequence,
            sdpMessage.nonce, 
            sdpMessage.atomicFlag,
            true, 
            sdpMessage.errorMsg
        );
    }

    function _processSDPv3(string calldata senderDomain, bytes32 senderID, bytes memory pkg) internal {
        SDPMessageV3 memory sdpMessage;
        sdpMessage.decode(pkg);

        require(
            keccak256(abi.encodePacked(sdpMessage.receiveDomain)) == localDomainHash,
            "SDP_Msg: wrong receiving domain"
        );

        if (
            sdpMessage.atomicFlag == SDPLib.SDP_V3_ATOMIC_FLAG_NONE_ATOMIC
            || sdpMessage.atomicFlag == SDPLib.SDP_V3_ATOMIC_FLAG_ATOMIC_REQUEST
        ) {
            _processSDPv3Request(senderDomain, senderID, sdpMessage);
        } else if (sdpMessage.atomicFlag == SDPLib.SDP_V3_ATOMIC_FLAG_ACK_SUCCESS) {
            _processSDPv3AckSuccess(senderDomain, senderID, sdpMessage);
        } else if (sdpMessage.atomicFlag == SDPLib.SDP_V3_ATOMIC_FLAG_ACK_ERROR) {
            _processSDPv3AckError(senderDomain, senderID, sdpMessage);
        } else if (sdpMessage.atomicFlag == SDPLib.SDP_V3_ATOMIC_FLAG_ACK_OFF_CHAIN_EXCEPTION) {
            _processSDPv3AckOffChainException(senderDomain, senderID, sdpMessage);
        } else {
            revert("SDP_MSG_ERROR: unexpected atomic flag");
        }
    }

    function _processSDPv3Request(string calldata senderDomain, bytes32 senderID, SDPMessageV3 memory sdpMessage) internal {

        bool res;
        string memory errMsg;
        require(
            sdpMessage.timeoutMeasure == 0 || sdpMessage.timeoutMeasure == 2 || sdpMessage.timeoutMeasure == 4,
            "only support timeout measure 0, 2 and 4 for now"
        );
        if (sdpMessage.timeoutMeasure == 2) {
            require(sdpMessage.timeout > block.number, "msg is timeout");
        } else if (sdpMessage.timeoutMeasure == 4) {
            require(sdpMessage.timeout > block.timestamp, "msg is timeout");
        }

        if (sdpMessage.sequence == SDPLib.UNORDERED_SEQUENCE) {
            (res, errMsg) = _routeUnorderedMessageV3(senderDomain, senderID, sdpMessage);
            if (sdpMessage.atomicFlag == SDPLib.SDP_V3_ATOMIC_FLAG_NONE_ATOMIC) {
                require(res, errMsg);
            }
        } else {
            (res, errMsg) = _routeOrderedMessageV3(senderDomain, senderID, sdpMessage);
        }

        emit ReceiveMessageV3(
            sdpMessage.messageId,
            senderDomain,
            senderID,
            sdpMessage.receiveDomain,
            sdpMessage.getReceiverAddress(),
            sdpMessage.sequence,
            sdpMessage.nonce,
            sdpMessage.atomicFlag,
            res,
            errMsg
        );

        emit MessageV3TimeoutInfo(
            sdpMessage.messageId,
            sdpMessage.timeoutMeasure,
            sdpMessage.timeout
        );

        if (sdpMessage.atomicFlag == SDPLib.SDP_V3_ATOMIC_FLAG_ATOMIC_REQUEST) {
            _ackSDPv3Request(sdpMessage, senderDomain, senderID, res, errMsg);
        }
    }

    function _routeUnorderedMessageV3(string calldata senderDomain, bytes32 senderID, SDPMessageV3 memory sdpMessage) internal returns (bool, string memory) {
        bool res = false;
        string memory errMsg;
        if (sdpMessage.getReceiverAddress().code.length == 0) {
            res = false;
            errMsg = "receiver has no code";
        } else {
            try
                IContractUsingSDP(sdpMessage.getReceiverAddress()).recvUnorderedMessage(senderDomain, senderID, sdpMessage.message)
            {
                res = true;
            } catch Error(
                string memory reason
            ) {
                errMsg = reason;
            } catch (
                bytes memory /*lowLevelData*/
            ) {}
        }

        return (res, errMsg);
    }

    function _routeOrderedMessageV3(string calldata senderDomain, bytes32 senderID, SDPMessageV3 memory sdpMessage) internal returns (bool, string memory) {
        uint32 seqExpected = _getAndUpdateRecvSeq(senderDomain, senderID, sdpMessage.receiver);
        require(sdpMessage.sequence == seqExpected, "SDP_MSG_ERROR: sequence not equal");

        bool res = false;
        string memory errMsg;
        address receiver = sdpMessage.getReceiverAddress();
        if (receiver.code.length == 0) {
            res = false;
            errMsg = "receiver has no code";
        } else {
            try
                IContractUsingSDP(receiver).recvMessage(senderDomain, senderID, sdpMessage.message)
            {
                res = true;
            } catch Error(
                string memory reason
            ) {
                errMsg = reason;
            } catch (
                bytes memory /*lowLevelData*/
            ) {}
        }

        return (res, errMsg);
    }

    function _ackSDPv3Request(SDPMessageV3 memory sdpMessage, string calldata senderDomain, bytes32 senderID, bool res, string memory errMsg) internal {
        address receiverAddr = sdpMessage.getReceiverAddress();

        sdpMessage.receiveDomain = senderDomain;
        sdpMessage.receiver = senderID;
        sdpMessage.atomicFlag = res ? SDPLib.SDP_V3_ATOMIC_FLAG_ACK_SUCCESS : SDPLib.SDP_V3_ATOMIC_FLAG_ACK_ERROR;
        sdpMessage.errorMsg = res ? "" : errMsg;

        IAuthMessage(amAddress).recvFromProtocol(receiverAddr, sdpMessage.encode());
    }

    function _processSDPv3AckSuccess(string calldata senderDomain, bytes32 senderID, SDPMessageV3 memory sdpMessage) internal {
        IContractWithAcks(sdpMessage.getReceiverAddress()).ackOnSuccess(
            sdpMessage.messageId,
            senderDomain,
            senderID,
            sdpMessage.sequence,
            sdpMessage.nonce,
            sdpMessage.message
        );

        emit ReceiveMessageV3(
            sdpMessage.messageId,
            senderDomain,
            senderID,
            sdpMessage.receiveDomain,
            sdpMessage.getReceiverAddress(),
            sdpMessage.sequence,
            sdpMessage.nonce,
            sdpMessage.atomicFlag,
            true,
            "success"
        );

        emit MessageV3TimeoutInfo(
            sdpMessage.messageId,
            sdpMessage.timeoutMeasure,
            sdpMessage.timeout
        );
    }

    function _processSDPv3AckError(string calldata senderDomain, bytes32 senderID, SDPMessageV3 memory sdpMessage) internal {
        IContractWithAcks(sdpMessage.getReceiverAddress()).ackOnError(
            sdpMessage.messageId,
            senderDomain,
            senderID,
            sdpMessage.sequence,
            sdpMessage.nonce,
            sdpMessage.message,
            sdpMessage.errorMsg
        );

        emit ReceiveMessageV3(
            sdpMessage.messageId,
            senderDomain,
            senderID,
            sdpMessage.receiveDomain,
            sdpMessage.getReceiverAddress(),
            sdpMessage.sequence,
            sdpMessage.nonce,
            sdpMessage.atomicFlag,
            true,
            sdpMessage.errorMsg
        );
        emit MessageV3TimeoutInfo(
            sdpMessage.messageId,
            sdpMessage.timeoutMeasure,
            sdpMessage.timeout
        );

    }

    /**
     * - senderDomain 异常链域名
     * - senderID 异常合约地址
     * - sdpMessage 待更新的异常链已验证区块信息
     */
    function _processSDPv3AckOffChainException(
        string calldata senderDomain,
        bytes32 senderID,
        SDPMessageV3 memory sdpMessage
    ) internal {

        BlockState memory blockState = SDPLib.decodeBlockStateFrom(sdpMessage.message);

        recvValidatedBlockState[keccak256(abi.encodePacked(senderDomain))].blockHash = blockState.blockHash;
        recvValidatedBlockState[keccak256(abi.encodePacked(senderDomain))].blockTimestamp = blockState.blockTimestamp;
        recvValidatedBlockState[keccak256(abi.encodePacked(senderDomain))].blockHeight = blockState.blockHeight;

        emit ReceiveMessageV3(
            sdpMessage.messageId,
            senderDomain,
            senderID,
            sdpMessage.receiveDomain,
            sdpMessage.getReceiverAddress(),
            sdpMessage.sequence,
            sdpMessage.nonce,
            sdpMessage.atomicFlag,
            true,
            sdpMessage.errorMsg
        );
        emit MessageV3TimeoutInfo(
            sdpMessage.messageId,
            sdpMessage.timeoutMeasure,
            sdpMessage.timeout
        );
    }

    /*
     * 中继调用，触发异常（超时）回调， SDPv3接口
    */
    function recvOffChainException(bytes32 exceptionMsgAuthor, bytes calldata exceptionMsgPkg) external onlyOwner {
        SDPMessageV3 memory sdpMessage;
        sdpMessage.decode(exceptionMsgPkg);

        if (0 == sdpMessage.timeoutMeasure) {
            // 无超时限制，不应该触发链下异常
            revert("SDP_MSG_ERROR: the message timeoutMeasure is 0");
        } else if (2 == sdpMessage.timeoutMeasure) {
            // 接收链高度超时限制
            if (recvValidatedBlockState[keccak256(abi.encodePacked(sdpMessage.receiveDomain))].blockHeight > sdpMessage.timeout) {
                // 已验证高度已经过了超时高度，说明消息确实已超时
                // 验证消息原文存在
                if (!sendSDPV3Msgs[sdpMessage.messageId]) {
                    revert("SDP_MSG_ERROR: exception message hash does not exist");
                }

                // 调用onError接口
                IContractWithAcks(SDPLib.encodeCrossChainIDIntoAddress(exceptionMsgAuthor)).ackOnError(
                    sdpMessage.messageId,
                    sdpMessage.receiveDomain,
                    sdpMessage.receiver,
                    sdpMessage.sequence,
                    sdpMessage.nonce,
                    sdpMessage.message,
                    sdpMessage.errorMsg
                );
            } else {
                revert("SDP_MSG_ERROR: the message is not timeout with timeoutMeasure 2");
            }
        } else {
            revert("SDP_MSG_ERROR: unsupported timeout measure");
        }
    }

    function queryValidatedBlockStateByDomain(string calldata recvDomain) external view returns (BlockState memory) {
        return recvValidatedBlockState[keccak256(abi.encodePacked(recvDomain))];
    }

    function _getAndUpdateSendSeq(string memory receiveDomain, address sender, bytes32 receiver) internal returns (uint32) {
        bytes32 seqKey = SDPLib.getSendingSeqID(receiveDomain, sender, receiver);
        uint32 seq = sendSeq[seqKey];
        sendSeq[seqKey]++;
        return seq;
    }

    function _getAndUpdateRecvSeq(string memory sendDomain, bytes32 sender, bytes32 receiver) internal returns (uint32) {
        bytes32 seqKey = SDPLib.getReceivingSeqID(sendDomain, sender, receiver);
        uint32 seq = recvSeq[seqKey];
        recvSeq[seqKey]++;
        return seq;
    }

    function _getAndUpdateSendNonce(string memory receiveDomain, address sender, bytes32 receiver) internal returns (uint32) {
        bytes32 nonceKey = SDPLib.getSendingSeqID(receiveDomain, sender, receiver);
        uint32 nonce = sendNonce[nonceKey];
        sendNonce[nonceKey]++;
        return nonce;
    }

    function _beforeSend(string calldata receiverDomain, bytes32 receiverID, bytes calldata message) internal {}

    function _afterSend() internal {}

    function _beforeSendUnordered(string calldata receiverDomain, bytes32 receiverID, bytes calldata message) internal {}

    function _afterSendUnordered() internal {}

    function _beforeRecv(string calldata senderDomain, bytes32 senderID, bytes calldata pkg) internal {}

    function _afterRecv() internal {}

    /**
     * @dev This empty reserved space is put in place to allow future versions to add new
     * variables without shifting down storage in the inheritance chain.
     * See https://docs.openzeppelin.com/contracts/4.x/upgradeable#storage_gaps
     */
    uint256[50] private __gap;
}
