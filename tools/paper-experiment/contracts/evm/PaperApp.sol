// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.13;

interface IPaperSDP {
    function sendUnorderedMessage(string calldata domain, bytes32 receiver, bytes calldata message) external;
}
interface IPaperMonitor {
    function sendUnorderedMonitorMessage(string calldata domain, bytes32 receiver, bytes calldata message) external;
}

/// Experiment application: no message storage, send event, counter or ACK path.
abstract contract PaperReceiver {
    address public router;
    event recvCrosschainMsg(string senderDomain, bytes32 author, bytes message, bool isOrdered);

    constructor(address initialRouter) {
        require(initialRouter != address(0), "PaperApp: zero router");
        router = initialRouter;
    }

    function recvUnorderedMessage(string calldata domain, bytes32 author, bytes calldata message) external {
        require(msg.sender == router, "PaperApp: caller is not router");
        emit recvCrosschainMsg(domain, author, message, false);
    }
}

contract BasePaperApp is PaperReceiver {
    constructor(address initialRouter) PaperReceiver(initialRouter) {}
    function sendUnorderedMessage(string calldata domain, bytes32 receiver, bytes calldata message) external {
        IPaperSDP(router).sendUnorderedMessage(domain, receiver, message);
    }
}

contract XunPaperApp is PaperReceiver {
    constructor(address initialRouter) PaperReceiver(initialRouter) {}
    function sendUnorderedMessage(string calldata domain, bytes32 receiver, bytes calldata message) external {
        IPaperMonitor(router).sendUnorderedMonitorMessage(domain, receiver, message);
    }
}
