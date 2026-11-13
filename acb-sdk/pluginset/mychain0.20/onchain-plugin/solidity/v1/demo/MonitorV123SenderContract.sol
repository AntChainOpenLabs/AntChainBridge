// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

/** Test application that exercises the production Monitor/SDP V1/V2/V3 entry points. */
contract MonitorV123SenderContract {
    identity public sdpAddress;
    identity public monitorAddress;

    function setContracts(identity newSdpAddress, identity newMonitorAddress) external {
        require(newSdpAddress != identity(0), "MonitorV123Sender: invalid SDP");
        require(newMonitorAddress != identity(0), "MonitorV123Sender: invalid Monitor");
        sdpAddress = newSdpAddress;
        monitorAddress = newMonitorAddress;
    }

    function sendUnordered(identity receiver, string calldata domain, bytes calldata message) external {
        IMonitorV1Sender(monitorAddress).sendUnorderedMonitorMessage(domain, receiver, message);
    }

    function sendUnorderedV2(
        identity receiver,
        string calldata domain,
        bool atomic,
        bytes calldata message
    ) external returns (bytes32) {
        return ISDPV23Sender(sdpAddress).sendUnorderedMessageV2(domain, receiver, atomic, message);
    }

    function sendUnorderedV3(
        identity receiver,
        string calldata domain,
        bool atomic,
        bytes calldata message,
        uint8 timeoutMeasure,
        uint256 timeout
    ) external returns (bytes32) {
        return ISDPV23Sender(sdpAddress).sendUnorderedMessageV3(
            domain, receiver, atomic, message, timeoutMeasure, timeout
        );
    }
}

interface IMonitorV1Sender {
    function sendUnorderedMonitorMessage(
        string calldata receiverDomain,
        identity receiverID,
        bytes calldata message
    ) external;
}

interface ISDPV23Sender {
    function sendUnorderedMessageV2(
        string calldata receiverDomain,
        identity receiverID,
        bool atomic,
        bytes calldata message
    ) external returns (bytes32);

    function sendUnorderedMessageV3(
        string calldata receiverDomain,
        identity receiverID,
        bool atomic,
        bytes calldata message,
        uint8 timeoutMeasure,
        uint256 timeout
    ) external returns (bytes32);
}
