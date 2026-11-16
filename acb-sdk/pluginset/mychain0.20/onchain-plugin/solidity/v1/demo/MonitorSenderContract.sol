// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./IMonitorMessage.sol";

contract MonitorSenderContract {
    identity public monitorAddress;
    string public lastReceivedDomain;
    identity public lastReceivedAuthor;
    bytes public lastReceivedMessage;
    uint256 public receiveCount;

    event UnorderedMessageSent(string receiverDomain, identity receiver, bytes message);
    event UnorderedMessageReceived(string senderDomain, identity author, bytes message, uint256 receiveCount);

    function setMonitorContract(identity newMonitorAddress) external {
        require(newMonitorAddress != identity(0), "MonitorSender: invalid monitor");
        monitorAddress = newMonitorAddress;
    }

    function sendUnordered(identity receiver, string calldata receiverDomain, bytes calldata message) external {
        require(monitorAddress != identity(0), "MonitorSender: monitor not set");
        IMonitorMessage(monitorAddress).sendUnorderedMonitorMessage(receiverDomain, receiver, message);
        emit UnorderedMessageSent(receiverDomain, receiver, message);
    }

    function recvUnorderedMessage(
        string memory senderDomain,
        identity author,
        bytes memory message
    ) external {
        require(msg.sender == monitorAddress, "MonitorSender: sender is not monitor");
        lastReceivedDomain = senderDomain;
        lastReceivedAuthor = author;
        lastReceivedMessage = message;
        receiveCount += 1;
        emit UnorderedMessageReceived(senderDomain, author, message, receiveCount);
    }
}
