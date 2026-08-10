// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "../utils/TypesToBytes.sol";
import "../utils/BytesToTypes.sol";
import "../utils/SizeOf.sol";
import "../utils/TLVUtils.sol";
import "../../@openzeppelin/contracts/utils/Strings.sol";

struct MonitorOrder {
    string product;
    string domain;
    uint32 monitorOrderType;
    string senderDomain;
    bytes32 sender;
    string receiverDomain;
    bytes32 receiver;
    string transactionContent;
    string extra;
}

struct MonitorMessage {
    uint32 monitorType;
    string monitorMsg;
    bytes message;
}

library MonitorLib {
    // 监管字段(monitorType值)
    uint32 constant public MONITOR_CLOSE = 1;
    uint32 constant public MONITOR_OPEN = 2;
    uint32 constant public MONITOR_ROLLBACK = 3;

    // 监管指令类型与子类型(32bit 每4bit为间隔 4bit中前1bit表示主类型 后3bit为子类型)
    // 0000 0000 0000 0000 0000 0000 0000 0000: 从左到右共 8 个主类型
    // 第一个主类型-地址相关
    uint8 constant public MAJOR_TYPE_CONTRACT_ADDRESS = 1;
    //  子类型-黑名单
    uint8 constant public MINOR_TYPE_ADD_TO_BLACKLIST = 0;
    uint8 constant public MINOR_TYPE_REMOVE_FROM_BLACKLIST = 1;

    // 第二个主类型-控制相关
    uint8 constant public MAJOR_TYPE_CONTROL = 1;
    //  子类型-是否开启监管
    uint8 constant public MINOR_TYPE_MONITOR_CLOSE = 0;
    uint8 constant public MINOR_TYPE_MONITOR_OPEN = 1;

    // 事前监管失败类型
    uint8 constant public SENDER_IN_BLACKLIST = 0;
    uint8 constant public RECEIVER_IN_BLACKLIST = 1;

    function encodeAddressIntoCrossChainID(address _address) internal pure returns (bytes32) {
        bytes32 id = TypesToBytes.addressToBytes32(_address);
        return id;
    }

    function encodeCrossChainIDIntoAddress(bytes32 id) pure internal returns (address) {
        bytes memory rawId = new bytes(32);
        TypesToBytes.bytes32ToBytes(32, id, rawId);
        return BytesToTypes.bytesToAddress(32, rawId);
    }

    function decode(MonitorOrder memory monitorOrder, bytes memory rawMessage) internal pure {
        uint256 offset = rawMessage.length;

        bytes memory raw_product = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        monitorOrder.product = string(raw_product);
        offset -= 4 + raw_product.length;

        bytes memory raw_domain = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        monitorOrder.domain = string(raw_domain);
        offset -= 4 + raw_domain.length;

        monitorOrder.monitorOrderType = BytesToTypes.bytesToUint32(offset, rawMessage);
        offset -= 4;

        bytes memory raw_send_domain = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        monitorOrder.senderDomain = string(raw_send_domain);
        offset -= 4 + raw_send_domain.length;

        monitorOrder.sender = BytesToTypes.bytesToBytes32(offset, rawMessage);
        offset -= SizeOf.sizeOfBytes32();

        bytes memory raw_recv_domain = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        monitorOrder.receiverDomain = string(raw_recv_domain);
        offset -= 4 + raw_recv_domain.length;

        monitorOrder.receiver = BytesToTypes.bytesToBytes32(offset, rawMessage);
        offset -= SizeOf.sizeOfBytes32();

        bytes memory raw_tran_cont = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        monitorOrder.transactionContent = string(raw_tran_cont);
        offset -= 4 + raw_tran_cont.length;

        bytes memory raw_extra = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        monitorOrder.extra = string(raw_extra);
        offset -= 4 + raw_extra.length;
    }

    function encode(MonitorMessage memory monitorMessage) pure internal returns (bytes memory) {
        require(
            monitorMessage.message.length <= 0xFFFFFFFF,
            "encodeSDPMessage: body length overlimit"
        );
        // 4 + (4 + monitorMsg) + (4 + payload)
        uint total_size = 12 + bytes(monitorMessage.monitorMsg).length + monitorMessage.message.length;
        bytes memory pkg = new bytes(total_size);
        uint offset = total_size;

        TypesToBytes.uint32ToBytes(offset, monitorMessage.monitorType, pkg);
        offset -= SizeOf.sizeOfUint(32);

        bytes memory raw_monitorMsg = bytes(monitorMessage.monitorMsg);
        TypesToBytes.varBytesToBytes(offset, raw_monitorMsg, pkg);
        offset -= 4 + raw_monitorMsg.length;

        TypesToBytes.varBytesToBytes(offset, monitorMessage.message, pkg);
        offset -= 4 + monitorMessage.message.length;

        return pkg;
    }

    function decode(MonitorMessage memory monitorMessage, bytes memory rawMessage) internal pure {
        uint256 offset = rawMessage.length;

        monitorMessage.monitorType = BytesToTypes.bytesToUint32(offset, rawMessage);
        offset -= SizeOf.sizeOfInt(32);

        bytes memory raw_monitorMsg = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        monitorMessage.monitorMsg = string(raw_monitorMsg);
        offset -= 4 + raw_monitorMsg.length;

        monitorMessage.message = BytesToTypes.bytesToVarBytes(offset, rawMessage);
        offset -= 4 + monitorMessage.message.length;
    }
}