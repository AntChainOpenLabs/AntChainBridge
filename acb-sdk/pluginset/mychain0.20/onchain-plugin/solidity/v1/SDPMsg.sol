// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./interfaces/ISDPMessage.sol";
import "./interfaces/IAuthMessage.sol";
import "./interfaces/IContractUsingSDP.sol";
import "./interfaces/IContractUsingMonitor.sol";
import "./interfaces/IContractWithAcks.sol";
import "./interfaces/IMonitor.sol";
import "./lib/sdp/SDPLib.sol";
import "./lib/utils/Ownable.sol";


contract SDPMsg is ISDPMessage, Ownable {

    // Version 4 fixes the ordered receive dispatch: SDP V1 must call the V1
    // Monitor entry point and SDP V2 must call the V2 entry point.
    uint32 private constant MONITOR_ROUTING_VERSION = 4;

    using SDPLib for SDPMessage;
    using SDPLib for SDPMessageV2;
    using SDPLib for SDPMessageV3;
    using SDPLib for BlockState;

    identity public amAddress;

    bytes32 public localDomainHash;

    mapping(bytes32 => uint32) sendSeq;
    mapping(bytes32 => uint32) recvSeq;

    mapping(bytes32 => uint32) sendNonce;

    /**
    * 接收链已验证高度：接收链域名哈希 -> 已验证区块信息
    */
    mapping(bytes32 => BlockState) recvValidatedBlockState;

    /**
    * 记录已发送原子性消息的哈希
    */
    mapping(bytes32 => bool) sendSDPV3Msgs;

    // only relayer can call `recvOffChainException`
    mapping(identity => bool) public relayerAuthMap;

    // Keep new regulatory state after every legacy SDP storage field so an
    // update-contract migration does not shift existing sequences or state.
    identity public monitorAddress;

    modifier onlyAM() {
        require(
            amAddress == msg.sender,
            "SDPMsg: not valid am contract"
        );
        _;
    }

    modifier onlyMonitorOrLegacySender() {
        require(
            monitorAddress == identity(0) || monitorAddress == msg.sender,
            "SDPMsg: not valid monitor contract"
        );
        _;
    }

    modifier onlyRelayer {
        require(
            relayerAuthMap[msg.sender],
            "SDPMsg: sender not valid relayer"
        );
        _;
    }

    constructor() {
        // The AM contract is deployed by relayer by default.
        // If the admin of heterogeneous chain deployed the AM contract in advance, the admin
        //   can invoke the interface `setRelayer` to set the real relayer identity.
        relayerAuthMap[msg.sender] = true;
    }

    function setAmContract(identity newAmContract) override external onlyOwner {
        require(newAmContract != identity(0), "SDPMsg: invalid am contract");
        amAddress = newAmContract;
    }

    function setMonitorContract(identity newMonitorContract) external onlyOwner {
        require(newMonitorContract != identity(0), "SDPMsg: invalid monitor contract");
        monitorAddress = newMonitorContract;
    }

    function getMonitorRoutingVersion() external pure returns (uint32) {
        return MONITOR_ROUTING_VERSION;
    }

    function getAmAddress() external view returns (identity) {
        return amAddress;
    }

    function setLocalDomain(string memory domain) external override onlyOwner {
        localDomainHash = keccak256(abi.encodePacked(domain));
    }

    function getLocalDomain() external view returns (bytes32) {
        return localDomainHash;
    }

    function setMsgSeq(string memory _domain_name, identity _author, identity _receiver, uint32 _new_seq) external onlyOwner {
        bytes32 seqKey = SDPLib.getReceivingSeqID(_domain_name, bytes32(_author), bytes32(_receiver));
        recvSeq[seqKey] = _new_seq;
    }

    function SetAmContractAndDomain(identity _am_contract, string memory expected_domain_name) external onlyOwner {
        require(_am_contract != identity(0), "SDPMsg: invalid am contract");
        amAddress = _am_contract;

        localDomainHash = keccak256(abi.encodePacked(expected_domain_name));
    }

    // 发送有序消息 SDPv1
    function sendMessage(string calldata receiverDomain, identity receiverID, bytes calldata message) override external onlyMonitorOrLegacySender {
        _sendMessage(receiverDomain, receiverID, msg.sender, message);
    }

    function sendMessageFromMonitor(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bytes calldata message
    ) override external onlyMonitorOrLegacySender {
        _sendMessage(receiverDomain, receiverID, senderID, message);
    }

    function _sendMessage(string calldata receiverDomain, identity receiverID, identity senderID, bytes calldata message) internal {
        _beforeSend(receiverDomain, receiverID, message);
        bytes32 receiver = bytes32(receiverID);
        SDPMessage memory sdpMessage = SDPMessage(
            {
                receiveDomain: receiverDomain,
                receiver: receiver,
                message: message,
                sequence: _getAndUpdateSendSeq(receiverDomain, senderID, receiver)
            }
        );

        bytes memory rawMsg = sdpMessage.encode();

        IAuthMessage(amAddress).recvFromProtocol(senderID, rawMsg);

        _afterSend();
    }

    // 发送无序消息 SDPv1
    function sendUnorderedMessage(string calldata receiverDomain, identity receiverID, bytes calldata message) override external onlyMonitorOrLegacySender {
        _sendUnorderedMessage(receiverDomain, receiverID, msg.sender, message);
    }

    function sendUnorderedMessageFromMonitor(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bytes calldata message
    ) override external onlyMonitorOrLegacySender {
        _sendUnorderedMessage(receiverDomain, receiverID, senderID, message);
    }

    function _sendUnorderedMessage(string calldata receiverDomain, identity receiverID, identity senderID, bytes calldata message) internal {
        _beforeSendUnordered(receiverDomain, receiverID, message);

        SDPMessage memory sdpMessage = SDPMessage(
            {
                receiveDomain: receiverDomain,
                receiver: bytes32(receiverID),
                message: message,
                sequence: SDPLib.UNORDERED_SEQUENCE
            }
        );

        IAuthMessage(amAddress).recvFromProtocol(senderID, sdpMessage.encode());

        _afterSendUnordered();
    }

    // 发送有序消息 SDPv2
    function sendMessageV2(string calldata receiverDomain, identity receiverID, bool atomic, bytes calldata message) override external returns (bytes32) {
        if (monitorAddress != identity(0)) {
            return IMonitor(monitorAddress).sendMonitorMessageV2(
                receiverDomain, receiverID, msg.sender, atomic, message
            );
        }
        return _sendMessageV2(receiverDomain, receiverID, msg.sender, atomic, message);
    }

    function sendMessageV2FromMonitor(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message
    ) override external onlyMonitorOrLegacySender returns (bytes32) {
        return _sendMessageV2(receiverDomain, receiverID, senderID, atomic, message);
    }

    function _sendMessageV2(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message
    ) internal returns (bytes32) {
        _beforeSend(receiverDomain, receiverID, message);
        bytes32 receiver = bytes32(receiverID);
        SDPMessageV2 memory sdpMessage = SDPMessageV2(
            {
                version: 2,
                messageId: bytes32(0),
                receiveDomain: receiverDomain,
                receiver: receiver,
                atomicFlag: atomic ? SDPLib.SDP_V2_ATOMIC_FLAG_ATOMIC_REQUEST : SDPLib.SDP_V2_ATOMIC_FLAG_NONE_ATOMIC,
                nonce: SDPLib.MAX_NONCE,
                sequence: _getAndUpdateSendSeq(receiverDomain, senderID, receiver),
                message: message,
                errorMsg: ""
            }
        );
        sdpMessage.calcMessageId(localDomainHash);

        IAuthMessage(amAddress).recvFromProtocol(senderID, sdpMessage.encode());

        _afterSend();

        return sdpMessage.messageId;
    }

    // 发送无序消息 SDPv2
    function sendUnorderedMessageV2(string calldata receiverDomain, identity receiverID, bool atomic, bytes calldata message) override external returns (bytes32) {
        if (monitorAddress != identity(0)) {
            return IMonitor(monitorAddress).sendUnorderedMonitorMessageV2(
                receiverDomain, receiverID, msg.sender, atomic, message
            );
        }
        return _sendUnorderedMessageV2(receiverDomain, receiverID, msg.sender, atomic, message);
    }

    function sendUnorderedMessageV2FromMonitor(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message
    ) override external onlyMonitorOrLegacySender returns (bytes32) {
        return _sendUnorderedMessageV2(receiverDomain, receiverID, senderID, atomic, message);
    }

    function _sendUnorderedMessageV2(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message
    ) internal returns (bytes32) {
        _beforeSendUnordered(receiverDomain, receiverID, message);
        bytes32 receiver = bytes32(receiverID);
        SDPMessageV2 memory sdpMessage = SDPMessageV2(
            {
                version: 2,
                messageId: bytes32(0),
                receiveDomain: receiverDomain,
                receiver: receiver,
                atomicFlag: atomic ? SDPLib.SDP_V2_ATOMIC_FLAG_ATOMIC_REQUEST : SDPLib.SDP_V2_ATOMIC_FLAG_NONE_ATOMIC,
                nonce: _getAndUpdateSendNonce(receiverDomain, senderID, receiver),
                sequence: SDPLib.UNORDERED_SEQUENCE,
                message: message,
                errorMsg: ""
            }
        );
        sdpMessage.calcMessageId(localDomainHash);

        IAuthMessage(amAddress).recvFromProtocol(senderID, sdpMessage.encode());

        _afterSendUnordered();

        return sdpMessage.messageId;
    }

    // 发送有序消息 SDPv3
    function sendMessageV3(string calldata receiverDomain, identity receiverID, bool atomic, bytes calldata message,
        uint8 _timeoutMeasure, uint256 _timeout) public returns (bytes32) {

        if (monitorAddress != identity(0)) {
            return IMonitor(monitorAddress).sendMonitorMessageV3(
                receiverDomain,
                receiverID,
                msg.sender,
                atomic,
                message,
                _timeoutMeasure,
                _timeout
            );
        }
        return _sendMessageV3(
            receiverDomain, receiverID, msg.sender, atomic, message, _timeoutMeasure, _timeout
        );
    }

    function sendMessageV3FromMonitor(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message,
        uint8 _timeoutMeasure,
        uint256 _timeout
    ) override external onlyMonitorOrLegacySender returns (bytes32) {
        return _sendMessageV3(
            receiverDomain, receiverID, senderID, atomic, message, _timeoutMeasure, _timeout
        );
    }

    function _sendMessageV3(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message,
        uint8 _timeoutMeasure,
        uint256 _timeout
    ) internal returns (bytes32) {

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
                sequence: _getAndUpdateSendSeq(receiverDomain, senderID, receiver),
                message: message,
                errorMsg: "",
                timeoutMeasure: _timeoutMeasure,
                timeout: _timeout
            }
        );
        sdpMessage.calcMessageId(localDomainHash);

        // v3消息才有的记录，用于超时回滚前的验证
        sendSDPV3Msgs[sdpMessage.messageId] = true;

        IAuthMessage(amAddress).recvFromProtocol(senderID, sdpMessage.encode());

        _afterSend();

        return sdpMessage.messageId;
    }

    // 发送无序消息 SDPv3
    function sendUnorderedMessageV3(string calldata receiverDomain, identity receiverID, bool atomic, bytes calldata message,
        uint8 _timeoutMeasure, uint256 _timeout) public returns (bytes32) {

        if (monitorAddress != identity(0)) {
            return IMonitor(monitorAddress).sendUnorderedMonitorMessageV3(
                receiverDomain,
                receiverID,
                msg.sender,
                atomic,
                message,
                _timeoutMeasure,
                _timeout
            );
        }
        return _sendUnorderedMessageV3(
            receiverDomain, receiverID, msg.sender, atomic, message, _timeoutMeasure, _timeout
        );
    }

    function sendUnorderedMessageV3FromMonitor(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message,
        uint8 _timeoutMeasure,
        uint256 _timeout
    ) override external onlyMonitorOrLegacySender returns (bytes32) {
        return _sendUnorderedMessageV3(
            receiverDomain, receiverID, senderID, atomic, message, _timeoutMeasure, _timeout
        );
    }

    function _sendUnorderedMessageV3(
        string calldata receiverDomain,
        identity receiverID,
        identity senderID,
        bool atomic,
        bytes calldata message,
        uint8 _timeoutMeasure,
        uint256 _timeout
    ) internal returns (bytes32) {

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
                nonce: _getAndUpdateSendNonce(receiverDomain, senderID, receiver),
                sequence: SDPLib.UNORDERED_SEQUENCE,
                message: message,
                errorMsg: "",
                timeoutMeasure: _timeoutMeasure,
                timeout: _timeout
            }
        );
        sdpMessage.calcMessageId(localDomainHash);

        sendSDPV3Msgs[sdpMessage.messageId] = true;

        IAuthMessage(amAddress).recvFromProtocol(senderID, sdpMessage.encode());

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

    function queryP2PMsgSeqOnChain(string calldata senderDomain, identity senderID, string calldata receiverDomain, identity receiverID) external view returns (uint32) {
        require(
            keccak256(abi.encodePacked(receiverDomain)) == localDomainHash,
            "SDPMsg: wrong receiving domain"
        );
        bytes32 seqKey = SDPLib.getReceivingSeqID(senderDomain, bytes32(senderID), bytes32(receiverID));
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
        identity receiver = sdpMessage.getReceiverAddress();
        if (receiver.code.length == 0) {
            res = false;
            errMsg = "receiver has no code";
        } else {
            if (monitorAddress == identity(0)) {
                try IContractUsingSDP(receiver).recvMessage(senderDomain, identity(senderID), sdpMessage.message) {
                    res = true;
                } catch Error(string memory reason) {
                    errMsg = reason;
                } catch (bytes memory /*lowLevelData*/) {}
            } else {
                try IContractUsingMonitor(monitorAddress).recvMessageFromSDP(
                    senderDomain,
                    identity(senderID),
                    receiver,
                    sdpMessage.message
                ) {
                    res = true;
                } catch Error(string memory reason) {
                    errMsg = reason;
                } catch (bytes memory /*lowLevelData*/) {}
            }
        }

        emit receiveMessage(senderDomain, senderID, receiver, seqExpected, res, errMsg);
    }

    function _routeUnorderedMessage(string calldata senderDomain, bytes32 senderID, SDPMessage memory sdpMessage) internal {
        identity receiver = sdpMessage.getReceiverAddress();
        if (monitorAddress == identity(0)) {
            IContractUsingSDP(receiver).recvUnorderedMessage(senderDomain, identity(senderID), sdpMessage.message);
        } else {
            IContractUsingMonitor(monitorAddress).recvUnorderedMessageFromSDP(
                senderDomain,
                identity(senderID),
                receiver,
                sdpMessage.message
            );
        }
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
            (res, errMsg) = _routeUnorderedMessageV2(senderDomain, senderID, sdpMessage);
            if (sdpMessage.atomicFlag == SDPLib.SDP_V2_ATOMIC_FLAG_NONE_ATOMIC) {
                require(res, errMsg);
            }
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
        identity receiver = sdpMessage.getReceiverAddress();
        if (receiver.code.length == 0) {
            res = false;
            errMsg = "receiver has no code";
        } else {
            if (monitorAddress == identity(0)) {
                try IContractUsingSDP(receiver).recvMessage(senderDomain, identity(senderID), sdpMessage.message) {
                    res = true;
                } catch Error(string memory reason) {
                    errMsg = reason;
                } catch (bytes memory /*lowLevelData*/) {}
            } else {
                try IContractUsingMonitor(monitorAddress).recvMessageV2FromSDP(
                    senderDomain,
                    identity(senderID),
                    receiver,
                    sdpMessage.message
                ) {
                    res = true;
                } catch Error(string memory reason) {
                    errMsg = reason;
                } catch (bytes memory /*lowLevelData*/) {}
            }
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
            identity receiver = sdpMessage.getReceiverAddress();
            if (monitorAddress == identity(0)) {
                try IContractUsingSDP(receiver).recvUnorderedMessage(senderDomain, identity(senderID), sdpMessage.message) {
                    res = true;
                } catch Error(string memory reason) {
                    errMsg = reason;
                } catch (bytes memory /*lowLevelData*/) {}
            } else {
                try IContractUsingMonitor(monitorAddress).recvUnorderedMessageV2FromSDP(
                    senderDomain,
                    identity(senderID),
                    receiver,
                    sdpMessage.message
                ) {
                    res = true;
                } catch Error(string memory reason) {
                    errMsg = reason;
                } catch (bytes memory /*lowLevelData*/) {}
            }
        }

        return (res, errMsg);
    }

    function _ackSDPv2Request(SDPMessageV2 memory sdpMessage, string calldata senderDomain, bytes32 senderID, bool res, string memory errMsg) internal {
        identity receiverAddr = sdpMessage.getReceiverAddress();

        sdpMessage.receiveDomain = senderDomain;
        sdpMessage.receiver = senderID;
        sdpMessage.atomicFlag = res ? SDPLib.SDP_V2_ATOMIC_FLAG_ACK_SUCCESS : SDPLib.SDP_V2_ATOMIC_FLAG_ACK_ERROR;
        sdpMessage.errorMsg = res ? "" : errMsg;

        IAuthMessage(amAddress).recvFromProtocol(receiverAddr, sdpMessage.encode());
    }

    function _processSDPv2AckSuccess(string calldata senderDomain, bytes32 senderID, SDPMessageV2 memory sdpMessage) internal {
        if (monitorAddress == identity(0)) {
            IContractWithAcks(sdpMessage.getReceiverAddress()).ackOnSuccess(
                sdpMessage.messageId,
                senderDomain,
                identity(senderID),
                sdpMessage.sequence,
                sdpMessage.nonce,
                sdpMessage.message
            );
        } else {
            IContractUsingMonitor(monitorAddress).ackOnSuccessFromSDP(
                sdpMessage.getReceiverAddress(),
                sdpMessage.messageId,
                senderDomain,
                identity(senderID),
                sdpMessage.sequence,
                sdpMessage.nonce,
                sdpMessage.message
            );
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
            true,
            "success"
        );
    }

    function _processSDPv2AckError(string calldata senderDomain, bytes32 senderID, SDPMessageV2 memory sdpMessage) internal {
        if (monitorAddress == identity(0)) {
            IContractWithAcks(sdpMessage.getReceiverAddress()).ackOnError(
                sdpMessage.messageId,
                senderDomain,
                identity(senderID),
                sdpMessage.sequence,
                sdpMessage.nonce,
                sdpMessage.message,
                sdpMessage.errorMsg
            );
        } else {
            IContractUsingMonitor(monitorAddress).ackOnErrorFromSDP(
                sdpMessage.getReceiverAddress(),
                sdpMessage.messageId,
                senderDomain,
                identity(senderID),
                sdpMessage.sequence,
                sdpMessage.nonce,
                sdpMessage.message,
                sdpMessage.errorMsg
            );
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
            identity receiver = sdpMessage.getReceiverAddress();
            if (monitorAddress == identity(0)) {
                try IContractUsingSDP(receiver).recvUnorderedMessage(senderDomain, identity(senderID), sdpMessage.message) {
                    res = true;
                } catch Error(string memory reason) {
                    errMsg = reason;
                } catch (bytes memory /*lowLevelData*/) {}
            } else {
                try IContractUsingMonitor(monitorAddress).recvUnorderedMessageV3FromSDP(
                    senderDomain,
                    identity(senderID),
                    receiver,
                    sdpMessage.message
                ) {
                    res = true;
                } catch Error(string memory reason) {
                    errMsg = reason;
                } catch (bytes memory /*lowLevelData*/) {}
            }
        }

        return (res, errMsg);
    }

    function _routeOrderedMessageV3(string calldata senderDomain, bytes32 senderID, SDPMessageV3 memory sdpMessage) internal returns (bool, string memory) {
        uint32 seqExpected = _getAndUpdateRecvSeq(senderDomain, senderID, sdpMessage.receiver);
        require(sdpMessage.sequence == seqExpected, "SDP_MSG_ERROR: sequence not equal");

        bool res = false;
        string memory errMsg;
        identity receiver = sdpMessage.getReceiverAddress();
        if (receiver.code.length == 0) {
            res = false;
            errMsg = "receiver has no code";
        } else {
            if (monitorAddress == identity(0)) {
                try IContractUsingSDP(receiver).recvMessage(senderDomain, identity(senderID), sdpMessage.message) {
                    res = true;
                } catch Error(string memory reason) {
                    errMsg = reason;
                } catch (bytes memory /*lowLevelData*/) {}
            } else {
                try IContractUsingMonitor(monitorAddress).recvMessageV3FromSDP(
                    senderDomain,
                    identity(senderID),
                    receiver,
                    sdpMessage.message
                ) {
                    res = true;
                } catch Error(string memory reason) {
                    errMsg = reason;
                } catch (bytes memory /*lowLevelData*/) {}
            }
        }

        return (res, errMsg);
    }

    function _ackSDPv3Request(SDPMessageV3 memory sdpMessage, string calldata senderDomain, bytes32 senderID, bool res, string memory errMsg) internal {
        identity receiverAddr = sdpMessage.getReceiverAddress();

        sdpMessage.receiveDomain = senderDomain;
        sdpMessage.receiver = senderID;
        sdpMessage.atomicFlag = res ? SDPLib.SDP_V3_ATOMIC_FLAG_ACK_SUCCESS : SDPLib.SDP_V3_ATOMIC_FLAG_ACK_ERROR;
        sdpMessage.errorMsg = res ? "" : errMsg;

        IAuthMessage(amAddress).recvFromProtocol(receiverAddr, sdpMessage.encode());
    }

    function _processSDPv3AckSuccess(string calldata senderDomain, bytes32 senderID, SDPMessageV3 memory sdpMessage) internal {
        if (monitorAddress == identity(0)) {
            IContractWithAcks(sdpMessage.getReceiverAddress()).ackOnSuccess(
                sdpMessage.messageId,
                senderDomain,
                identity(senderID),
                sdpMessage.sequence,
                sdpMessage.nonce,
                sdpMessage.message
            );
        } else {
            IContractUsingMonitor(monitorAddress).ackOnSuccessFromSDP(
                sdpMessage.getReceiverAddress(),
                sdpMessage.messageId,
                senderDomain,
                identity(senderID),
                sdpMessage.sequence,
                sdpMessage.nonce,
                sdpMessage.message
            );
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
        if (monitorAddress == identity(0)) {
            IContractWithAcks(sdpMessage.getReceiverAddress()).ackOnError(
                sdpMessage.messageId,
                senderDomain,
                identity(senderID),
                sdpMessage.sequence,
                sdpMessage.nonce,
                sdpMessage.message,
                sdpMessage.errorMsg
            );
        } else {
            IContractUsingMonitor(monitorAddress).ackOnErrorFromSDP(
                sdpMessage.getReceiverAddress(),
                sdpMessage.messageId,
                senderDomain,
                identity(senderID),
                sdpMessage.sequence,
                sdpMessage.nonce,
                sdpMessage.message,
                sdpMessage.errorMsg
            );
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
        SDPMessageV3 memory sdpMessage) internal {

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
    function recvOffChainException(bytes32 exceptionMsgAuthor, bytes calldata exceptionMsgPkg) external onlyRelayer{
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
                identity exceptionReceiver = SDPLib.encodeCrossChainIDIntoAddress(exceptionMsgAuthor);
                if (monitorAddress == identity(0)) {
                    IContractWithAcks(exceptionReceiver).ackOnError(
                        sdpMessage.messageId,
                        sdpMessage.receiveDomain,
                        identity(sdpMessage.receiver),
                        sdpMessage.sequence,
                        sdpMessage.nonce,
                        sdpMessage.message,
                        sdpMessage.errorMsg
                    );
                } else {
                    IContractUsingMonitor(monitorAddress).ackOnErrorFromSDP(
                        exceptionReceiver,
                        sdpMessage.messageId,
                        sdpMessage.receiveDomain,
                        identity(sdpMessage.receiver),
                        sdpMessage.sequence,
                        sdpMessage.nonce,
                        sdpMessage.message,
                        sdpMessage.errorMsg
                    );
                }
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

    function _getAndUpdateSendSeq(string memory receiveDomain, identity sender, bytes32 receiver) internal returns (uint32) {
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

    function _getAndUpdateSendNonce(string memory receiveDomain, identity sender, bytes32 receiver) internal returns (uint32) {
        bytes32 nonceKey = SDPLib.getSendingSeqID(receiveDomain, sender, receiver);
        uint32 nonce = sendNonce[nonceKey];
        sendNonce[nonceKey]++;
        return nonce;
    }

    function _beforeSend(string calldata receiverDomain, identity receiverID, bytes calldata message) internal {}

    function _afterSend() internal {}

    function _beforeSendUnordered(string calldata receiverDomain, identity receiverID, bytes calldata message) internal {}

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
