// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./lib/monitor/MonitorProofContext.sol";

import "./interfaces/IContractUsingSDP.sol";
import "./interfaces/IContractUsingMonitor.sol";
import "./interfaces/IContractWithAcks.sol";
import "./interfaces/ISDPMessage.sol";
import "./interfaces/IMonitor.sol";
import "./interfaces/IMonitorVerifier.sol";
import "./lib/monitor/MonitorLib.sol";
import "./lib/utils/Ownable.sol";
import "./@openzeppelin/contracts/proxy/utils/Initializable.sol";

// 接受监管指令; 只在发送方进行事前监管
contract Monitor is IMonitor, IContractUsingMonitor, Ownable, Initializable {
    // Version 4 fixes monitor-control handling: CLOSE/OPEN/ROLLBACK are stored
    // as the protocol enum instead of treating every non-zero value as OPEN.
    // Version 5 preserves non-word-aligned payloads emitted by MySolidity chains.
    uint32 public constant IMPLEMENTATION_VERSION = 7;

    using MonitorLib for MonitorOrder;
    using MonitorLib for MonitorMessage;

    // SDP合约地址
    address public sdpAddress;

    // MonitorVerifier合约地址
    address public monitorVerifierAddress;

    // 控制监管的字段
    uint32 public monitorControl;

    // 黑名单
    mapping(bytes32 => bool) public senderBlacklist;
    mapping(bytes32 => bool) public receiverBlacklist;

    // uint32 public successfulCallInMonitorOPEN = 0;
    // uint32 public successfulCallInMonitorCLOSE = 0;
    // uint32 public successfulCallInMonitorROLLBACK = 0;

    modifier onlySubProtocols {
        require(
            msg.sender == sdpAddress,
            "MonitorMsg: sender not valid sub-protocol"
        );
        _;
    }

    constructor() {
        _disableInitializers();
    }

    function init() external initializer() {
        _transferOwnership(_msgSender());
    }

    function setProtocol(address protocolAddress) override external onlyOwner {
        require(protocolAddress != address(0), "MonitorMsg: invalid sdp contract");
        sdpAddress = protocolAddress;
    }

    function setMonitorVerifier(address newMonitorVerifierAddress) override external onlyOwner {
        require(newMonitorVerifierAddress != address(0), "MonitorMsg: invalid MonitorVerifier contract");
        monitorVerifierAddress = newMonitorVerifierAddress;
    }

    function setMonitorControl(uint32 monitorType) override external onlyOwner {
        require(
            monitorType == MonitorLib.MONITOR_CLOSE
                || monitorType == MonitorLib.MONITOR_OPEN
                || monitorType == MonitorLib.MONITOR_ROLLBACK,
            "MonitorMsg: invalid monitor control"
        );
        monitorControl = monitorType;
    }

    function getProtocol() external view returns (address) {
        return sdpAddress;
    }

    function getMonitorControl() external view returns (uint32) {
        return monitorControl;
    }

    function getMonitorVerifier() external view returns (address) {
        return monitorVerifierAddress;
    }

    function getImplementationVersion() external pure returns (uint32) {
        return IMPLEMENTATION_VERSION;
    }

    // function getSuccessfulCallInMonitorOPEN() external view returns (uint32) {
    //     return successfulCallInMonitorOPEN;
    // }

    // function getSuccessfulCallInMonitorCLOSE() external view returns (uint32) {
    //     return successfulCallInMonitorCLOSE;
    // }

    // function getSuccessfulCallInMonitorROLLBACK() external view returns (uint32) {
    //     return successfulCallInMonitorROLLBACK;
    // }

    function sendMonitorMessage(string calldata receiverDomain, bytes32 receiverID, bytes calldata message) override external {
        _beforeSend(receiverDomain, receiverID, message);

        // 执行事前监管的检查
        if (monitorControl == MonitorLib.MONITOR_OPEN) {
            bool monitorResult = false;
            monitorResult = PreMonitoring(msg.sender, receiverDomain, receiverID, message);
            if(monitorResult == false) {
                revert("beforehand Monitor disapproval");
            }
        }

        MonitorMessage memory monitorMessage = MonitorMessage(
            {
                monitorType: monitorControl,
                monitorMsg: "",
                message: message
            }
        );

        bytes memory rawMsg = monitorMessage.encode();

        ISDPMessage(sdpAddress).sendMessage(receiverDomain, receiverID, msg.sender, rawMsg);

        _afterSend();
    }

    function sendUnorderedMonitorMessage(string calldata receiverDomain, bytes32 receiverID, bytes calldata message) external {
        _beforeSendUnordered(receiverDomain, receiverID, message);

        uint32 control = monitorControl;

        // 执行事前监管的检查
        if (control == MonitorLib.MONITOR_OPEN) {
            bool monitorResult = false;
            monitorResult = _preMonitoring(msg.sender, receiverDomain, receiverID, false);
            if(monitorResult == false) {
                revert("beforehand Monitor disapproval");
            }
        }

        bytes memory rawMsg = MonitorLib.encodeCalldata(control, message);

        ISDPMessage(sdpAddress).sendUnorderedMessage(receiverDomain, receiverID, msg.sender, rawMsg);

        _afterSendUnordered();
    }

    function sendMonitorMessageV2(
        string calldata receiverDomain,
        bytes32 receiverID,
        address senderID,
        bool atomic,
        bytes calldata message
    ) external override onlySubProtocols returns (bytes32) {
        bytes memory rawMsg = _prepareMonitoredMessage(senderID, receiverDomain, receiverID, message);
        return ISDPMessage(sdpAddress).sendMessageV2FromMonitor(
            receiverDomain, receiverID, senderID, atomic, rawMsg
        );
    }

    function sendUnorderedMonitorMessageV2(
        string calldata receiverDomain,
        bytes32 receiverID,
        address senderID,
        bool atomic,
        bytes calldata message
    ) external override onlySubProtocols returns (bytes32) {
        bytes memory rawMsg = _prepareMonitoredMessage(senderID, receiverDomain, receiverID, message);
        return ISDPMessage(sdpAddress).sendUnorderedMessageV2FromMonitor(
            receiverDomain, receiverID, senderID, atomic, rawMsg
        );
    }

    function sendMonitorMessageV3(
        string calldata receiverDomain,
        bytes32 receiverID,
        address senderID,
        bool atomic,
        bytes calldata message,
        uint8 timeoutMeasure,
        uint256 timeout
    ) external override onlySubProtocols returns (bytes32) {
        bytes memory rawMsg = _prepareMonitoredMessage(senderID, receiverDomain, receiverID, message);
        return ISDPMessage(sdpAddress).sendMessageV3FromMonitor(
            receiverDomain, receiverID, senderID, atomic, rawMsg, timeoutMeasure, timeout
        );
    }

    function sendUnorderedMonitorMessageV3(
        string calldata receiverDomain,
        bytes32 receiverID,
        address senderID,
        bool atomic,
        bytes calldata message,
        uint8 timeoutMeasure,
        uint256 timeout
    ) external override onlySubProtocols returns (bytes32) {
        bytes memory rawMsg = _prepareMonitoredMessage(senderID, receiverDomain, receiverID, message);
        return ISDPMessage(sdpAddress).sendUnorderedMessageV3FromMonitor(
            receiverDomain, receiverID, senderID, atomic, rawMsg, timeoutMeasure, timeout
        );
    }

    function _prepareMonitoredMessage(
        address senderID,
        string calldata receiverDomain,
        bytes32 receiverID,
        bytes calldata message
    ) internal returns (bytes memory) {
        if (monitorControl == MonitorLib.MONITOR_OPEN) {
            require(
                PreMonitoring(senderID, receiverDomain, receiverID, message),
                "beforehand Monitor disapproval"
            );
        }
        MonitorMessage memory monitorMessage = MonitorMessage({
            monitorType: monitorControl,
            monitorMsg: "",
            message: message
        });
        return monitorMessage.encode();
    }

    // IContractUsingMonitor.sol里面的接口
    function recvUnorderedMessageFromSDP(string memory senderDomain, bytes32 author, address receiverID, bytes memory message) external override onlySubProtocols {
        // 去掉监管字段 再向上传给app合约
        MonitorMessage memory monitorMessage;
        monitorMessage.decode(message);
        
        if (monitorMessage.monitorType == MonitorLib.MONITOR_OPEN) {
            // 验证监管节点的签名
            bool res = IMonitorVerifier(monitorVerifierAddress).verifyMonitorNodeProofMessage(MonitorProofContext.key(senderDomain, author, bytes32(uint256(uint160(receiverID))), message));
            emit VerifyMonitorNodeProofMessage(senderDomain, author, receiverID, res);
            // 监管不通过 构造回滚消息(仅修改监管字段 对原文不修改)
            if (res == false) {
                monitorMessage.monitorType = MonitorLib.MONITOR_ROLLBACK;
                monitorMessage.monitorMsg = "MidMonitoring not pass";
                ISDPMessage(sdpAddress).sendUnorderedMessage(senderDomain, author, receiverID, monitorMessage.encode());
                emit sendMonitorRollbakMessage(monitorMessage.monitorType, MonitorLib.encodeAddressIntoCrossChainID(receiverID), senderDomain, author, monitorMessage.monitorMsg);
            } else {
                IContractUsingSDP(receiverID).recvUnorderedMessage(senderDomain, author, monitorMessage.message);
            }
        } else if (monitorMessage.monitorType == MonitorLib.MONITOR_ROLLBACK) {
            IContractUsingSDP(receiverID).recvUnorderedMessage(senderDomain, author, monitorMessage.message);
            emit receiveMonitorRollbackMessage(monitorMessage.monitorType, senderDomain, author, MonitorLib.encodeAddressIntoCrossChainID(receiverID), monitorMessage.monitorMsg);
        } else if (monitorMessage.monitorType == MonitorLib.MONITOR_CLOSE) {
            IContractUsingSDP(receiverID).recvUnorderedMessage(senderDomain, author, monitorMessage.message);
        } else {
            revert("Monitor_Msg: wrong monitor type");
        }
    }

    // IContractUsingMonitor.sol里面的接口
    function recvMessageFromSDP(string memory senderDomain, bytes32 author, address receiverID, bytes memory message) external override onlySubProtocols {
        // 去掉监管字段 再向上传给app合约
        MonitorMessage memory monitorMessage;
        monitorMessage.decode(message);
        
        if (monitorMessage.monitorType == MonitorLib.MONITOR_OPEN) {
            // 验证监管节点的签名
            bool res = IMonitorVerifier(monitorVerifierAddress).verifyMonitorNodeProofMessage(MonitorProofContext.key(senderDomain, author, bytes32(uint256(uint160(receiverID))), message));
            emit VerifyMonitorNodeProofMessage(senderDomain, author, receiverID, res);
            // 监管不通过 构造回滚消息(仅修改监管字段 对原文不修改)
            if (res == false) {
                // revert("Monitor_Msg: MidMonitoring not pass");
                monitorMessage.monitorType = MonitorLib.MONITOR_ROLLBACK;
                monitorMessage.monitorMsg = "MidMonitoring not pass";
                ISDPMessage(sdpAddress).sendMessage(senderDomain, author, receiverID, monitorMessage.encode());
                emit sendMonitorRollbakMessage(monitorMessage.monitorType, MonitorLib.encodeAddressIntoCrossChainID(receiverID), senderDomain, author, monitorMessage.monitorMsg);
            } else {
                IContractUsingSDP(receiverID).recvMessage(senderDomain, author, monitorMessage.message);
                // successfulCallInMonitorOPEN += 1;
            }
        } else if (monitorMessage.monitorType == MonitorLib.MONITOR_ROLLBACK) {
            IContractUsingSDP(receiverID).recvMessage(senderDomain, author, monitorMessage.message);
            // successfulCallInMonitorROLLBACK += 1;
            emit receiveMonitorRollbackMessage(monitorMessage.monitorType, senderDomain, author, MonitorLib.encodeAddressIntoCrossChainID(receiverID), monitorMessage.monitorMsg);
        } else if (monitorMessage.monitorType == MonitorLib.MONITOR_CLOSE) {
            // revert("Monitor_Msg: here is Monitor.recvMessageFromSDP and monitor type is MONITOR_CLOSE");
            IContractUsingSDP(receiverID).recvMessage(senderDomain, author, monitorMessage.message);
            // successfulCallInMonitorCLOSE += 1;
        } else {
            revert("Monitor_Msg: wrong monitor type");
        }
    }

    function recvUnorderedMessageV2FromSDP(
        string memory senderDomain,
        bytes32 author,
        address receiverID,
        bytes memory message
    ) external override onlySubProtocols {
        _recvVersionedMessage(senderDomain, author, receiverID, message, true);
    }

    function recvMessageV2FromSDP(
        string memory senderDomain,
        bytes32 author,
        address receiverID,
        bytes memory message
    ) external override onlySubProtocols {
        _recvVersionedMessage(senderDomain, author, receiverID, message, false);
    }

    function recvUnorderedMessageV3FromSDP(
        string memory senderDomain,
        bytes32 author,
        address receiverID,
        bytes memory message
    ) external override onlySubProtocols {
        _recvVersionedMessage(senderDomain, author, receiverID, message, true);
    }

    function recvMessageV3FromSDP(
        string memory senderDomain,
        bytes32 author,
        address receiverID,
        bytes memory message
    ) external override onlySubProtocols {
        _recvVersionedMessage(senderDomain, author, receiverID, message, false);
    }

    function _recvVersionedMessage(
        string memory senderDomain,
        bytes32 author,
        address receiverID,
        bytes memory rawMessage,
        bool unordered
    ) internal {
        MonitorMessage memory monitorMessage;
        monitorMessage.decode(rawMessage);
        if (monitorMessage.monitorType == MonitorLib.MONITOR_OPEN) {
            bool verified = IMonitorVerifier(monitorVerifierAddress).verifyMonitorNodeProofMessage(MonitorProofContext.key(senderDomain, author, bytes32(uint256(uint160(receiverID))), rawMessage));
            emit VerifyMonitorNodeProofMessage(senderDomain, author, receiverID, verified);
            require(verified, "Monitor_Msg: MidMonitoring not pass");
        } else {
            require(
                monitorMessage.monitorType == MonitorLib.MONITOR_CLOSE
                    || monitorMessage.monitorType == MonitorLib.MONITOR_ROLLBACK,
                "Monitor_Msg: wrong monitor type"
            );
        }

        if (unordered) {
            IContractUsingSDP(receiverID).recvUnorderedMessage(
                senderDomain, author, monitorMessage.message
            );
        } else {
            IContractUsingSDP(receiverID).recvMessage(
                senderDomain, author, monitorMessage.message
            );
        }
    }

    function ackOnSuccessFromSDP(
        address receiverID,
        bytes32 messageId,
        string memory receiverDomain,
        bytes32 receiver,
        uint32 sequence,
        uint64 nonce,
        bytes memory message
    ) external override onlySubProtocols {
        MonitorMessage memory monitorMessage;
        monitorMessage.decode(message);
        IContractWithAcks(receiverID).ackOnSuccess(
            messageId,
            receiverDomain,
            receiver,
            sequence,
            nonce,
            monitorMessage.message
        );
    }

    function ackOnErrorFromSDP(
        address receiverID,
        bytes32 messageId,
        string memory receiverDomain,
        bytes32 receiver,
        uint32 sequence,
        uint64 nonce,
        bytes memory message,
        string memory errorMsg
    ) external override onlySubProtocols {
        MonitorMessage memory monitorMessage;
        monitorMessage.decode(message);
        IContractWithAcks(receiverID).ackOnError(
            messageId,
            receiverDomain,
            receiver,
            sequence,
            nonce,
            monitorMessage.message,
            errorMsg
        );
    }

    function PreMonitoring(address senderAddress, string calldata receiverDomain, bytes32 receiverID, bytes calldata message) internal returns (bool) {
        return _preMonitoring(senderAddress, receiverDomain, receiverID, true);
    }

    // Only V1 unordered source sends omit the auxiliary success event.
    function _preMonitoring(address senderAddress, string calldata receiverDomain, bytes32 receiverID, bool emitApproval) internal returns (bool) {
        bool result = false;

        // 事前监管逻辑
        bytes32 senderID = MonitorLib.encodeAddressIntoCrossChainID(senderAddress);
        if(senderBlacklist[senderID]) {
            emit MonitorDisapproval(senderID,
                                    receiverDomain,
                                    receiverID,
                                    MonitorLib.MAJOR_TYPE_CONTRACT_ADDRESS,
                                    MonitorLib.SENDER_IN_BLACKLIST);
        } else {
            if (receiverBlacklist[receiverID]) {
                emit MonitorDisapproval(senderID,
                    receiverDomain,
                    receiverID,
                    MonitorLib.MAJOR_TYPE_CONTRACT_ADDRESS,
                    MonitorLib.RECEIVER_IN_BLACKLIST);
            } else {
                if (emitApproval) {
                    emit MonitorApproval(senderID, receiverDomain, receiverID);
                }
                return true;
            }
        }

        return result;
    }


    function recvMonitorOrder(string calldata committeeId, string calldata signAlgo, bytes memory rawProof, bytes memory rawMonitorOrder) external override {
        // 对监管节点发送的监管指令 验签
        bool res = IMonitorVerifier(monitorVerifierAddress).verifyMonitorOrder(committeeId, signAlgo, rawProof, rawMonitorOrder);
        emit VerifyMonitorOrder(committeeId, res);
        require(res == true, "Monitor_Msg: verify monitor node proof message failed");

        MonitorOrder memory monitorOrder;
        monitorOrder.decode(rawMonitorOrder);

        uint8[8] memory flags;
        uint8[8] memory values;
        for (uint8 i = 0; i < 8; i++) {
            uint8 chunk = uint8((monitorOrder.monitorOrderType >> (28 - i * 4)) & 0xF); // 取出 4-bit
            flags[i]  = (chunk >> 3) & 0x1; // 最高位为主类型
            values[i] = chunk & 0x7;        // 后3位为子类型
        }

        // 存储监管指令 目前只支持 加入和移除黑名单 控制监管开关 (合约部署时默认监管开启)
        if(flags[0] == MonitorLib.MAJOR_TYPE_CONTRACT_ADDRESS) {
            if(values[0] == MonitorLib.MINOR_TYPE_ADD_TO_BLACKLIST) {
                senderBlacklist[monitorOrder.sender] = true;
                receiverBlacklist[monitorOrder.receiver] = true;
            } else if(values[0] == MonitorLib.MINOR_TYPE_REMOVE_FROM_BLACKLIST) {
                senderBlacklist[monitorOrder.sender] = false;
                receiverBlacklist[monitorOrder.receiver] = false;
            } else {
                revert("Monitor_Msg: not support monitor order containing this MINOR TYPE in MAJOR_TYPE_CONTRACT_ADDRESS yet");
            }
        }
        if(flags[1] == MonitorLib.MAJOR_TYPE_CONTROL) {
            if(values[1] == MonitorLib.MINOR_TYPE_MONITOR_CLOSE) {
                monitorControl = MonitorLib.MONITOR_CLOSE;
            } else if(values[1] == MonitorLib.MINOR_TYPE_MONITOR_OPEN) {
                monitorControl = MonitorLib.MONITOR_OPEN;
            } else {
                revert("Monitor_Msg: not support monitor order containing this MINOR TYPE in MAJOR_TYPE_CONTROL yet");
            }
        }
        if(flags[0] != MonitorLib.MAJOR_TYPE_CONTRACT_ADDRESS) {
            if(flags[1] != MonitorLib.MAJOR_TYPE_CONTROL) {
                revert("Monitor_Msg: not support monitor order containing this MAJOR TYPE yet");
            }
        }
        emit receiveMonitorOrder(monitorOrder.monitorOrderType, monitorOrder.senderDomain, monitorOrder.sender,
                                    monitorOrder.receiverDomain, monitorOrder.receiver,
                                    monitorOrder.transactionContent, monitorOrder.extra);
    }

    function _beforeSend(string calldata receiverDomain, bytes32 receiverID, bytes calldata message) internal {}

    function _afterSend() internal {}

    function _beforeSendUnordered(string calldata receiverDomain, bytes32 receiverID, bytes calldata message) internal {}

    function _afterSendUnordered() internal {}

    /**
     * @dev This empty reserved space is put in place to allow future versions to add new
     * variables without shifting down storage in the inheritance chain.
     * See https://docs.openzeppelin.com/contracts/4.x/upgradeable#storage_gaps
     */
    uint256[50] private __gap;
}
