// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./interfaces/IContractUsingSDP.sol";
import "./interfaces/IContractUsingMonitor.sol";
import "./interfaces/ISDPMessage.sol";
import "./interfaces/IMonitor.sol";
import "./interfaces/IMonitorVerifier.sol";
import "./lib/monitor/MonitorLib.sol";
import "./lib/utils/Ownable.sol";
import "./@openzeppelin/contracts/proxy/utils/Initializable.sol";

// 监管节点部署该合约
// 接受监管指令; 只在发送方进行事前监管
contract Monitor is IMonitor, IContractUsingMonitor, Ownable, Initializable {
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

    function setMonitorVerifier(address newMonitorVerifierAddress) override external {
        require(newMonitorVerifierAddress != address(0), "MonitorMsg: invalid MonitorVerifier contract");
        monitorVerifierAddress = newMonitorVerifierAddress;
    }

    function setMonitorControl(uint32 monitorType) override external onlyOwner {
        monitorControl = monitorType == 0 ? MonitorLib.MONITOR_CLOSE : MonitorLib.MONITOR_OPEN;
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
            monitorResult = PreMonitoring(receiverDomain, receiverID, message);
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

        // 执行事前监管的检查
        if (monitorControl == MonitorLib.MONITOR_OPEN) {
            bool monitorResult = false;
            monitorResult = PreMonitoring(receiverDomain, receiverID, message);
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

        ISDPMessage(sdpAddress).sendUnorderedMessage(receiverDomain, receiverID, msg.sender, rawMsg);

        _afterSendUnordered();
    }

    // IContractUsingMonitor.sol里面的接口
    function recvUnorderedMessageFromSDP(string memory senderDomain, bytes32 author, address receiverID, bytes memory message) external override onlySubProtocols {
        // 去掉监管字段 再向上传给app合约
        MonitorMessage memory monitorMessage;
        monitorMessage.decode(message);
        
        if (monitorMessage.monitorType == MonitorLib.MONITOR_OPEN) {
            // 验证监管节点的签名
            bool res = IMonitorVerifier(monitorVerifierAddress).verifyMonitorNodeProofMessage();
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
            bool res = IMonitorVerifier(monitorVerifierAddress).verifyMonitorNodeProofMessage();
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
            IContractUsingSDP(receiverID).recvMessage(senderDomain, author, monitorMessage.message);
            // successfulCallInMonitorCLOSE += 1;
        } else {
            revert("Monitor_Msg: wrong monitor type");
        }
    }

    function PreMonitoring(string calldata receiverDomain, bytes32 receiverID, bytes calldata message) internal returns (bool) {
        bool result = false;

        // 事前监管逻辑
        bytes32 senderID = MonitorLib.encodeAddressIntoCrossChainID(msg.sender);
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
                emit MonitorApproval(senderID,
                    receiverDomain,
                    receiverID);
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