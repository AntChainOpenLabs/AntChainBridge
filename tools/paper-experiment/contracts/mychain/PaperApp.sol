// SPDX-License-Identifier: Apache-2.0
// MySolidity's pinned 0.8.14-ci compiler uses a pre-release version string.
pragma solidity ^0.8.0;

interface IPaperSDP {
    function sendUnorderedMessage(string calldata domain, identity receiver, bytes calldata message) external;
}
interface IPaperMonitor {
    function sendUnorderedMonitorMessage(string calldata domain, identity receiver, bytes calldata message) external;
}

abstract contract PaperReceiver {
    identity public router;
    event recvCrosschainMsg(string senderDomain, identity author, bytes message, bool isOrdered);

    constructor(identity initialRouter) {
        require(initialRouter != identity(0), "PaperApp: zero router");
        router = initialRouter;
    }

    function recvUnorderedMessage(string calldata domain, identity author, bytes calldata message) external {
        require(msg.sender == router, "PaperApp: caller is not router");
        emit recvCrosschainMsg(domain, author, message, false);
    }
}

contract BasePaperApp is PaperReceiver {
    constructor(identity initialRouter) PaperReceiver(initialRouter) {}
    function sendUnorderedMessage(string calldata domain, identity receiver, bytes calldata message) external {
        IPaperSDP(router).sendUnorderedMessage(domain, receiver, message);
    }
}

contract XunPaperApp is PaperReceiver {
    constructor(identity initialRouter) PaperReceiver(initialRouter) {}
    function sendUnorderedMessage(string calldata domain, identity receiver, bytes calldata message) external {
        IPaperMonitor(router).sendUnorderedMonitorMessage(domain, receiver, message);
    }
}
