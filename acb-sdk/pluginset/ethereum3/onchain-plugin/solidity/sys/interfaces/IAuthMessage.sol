// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

/**
 * @dev {IAuthMessage} is the basic protocol of the cross-chain communication protocol stack,
 * and AM is a verifiable cross-chain message which makes the upper layer protocol not need
 * to care about the legitimacy of the cross-chain message.
 *
 * When the {IAuthMessage} contract receives the cross-chain message submitted by the relayer,
 * it will decode cross-chain message. Then, the IAuthMessage contract will parse the cross-chain
 * message and forward it to the specified upper layer protocol contract.
 */
interface IAuthMessage {

    /**
     * @dev Event {SendAuthMessage} marks that the cross-chain message has been sent on this blockchain.
     * @param pkg the serialized auth-message.
     */
    event SendAuthMessage(bytes pkg);

    /**
     * @dev Set the SDP contract address for verifying the proof come from outside the blockchain.
     * @param protocolAddress upper protocol contract address
     * @param protocolType type number for upper protocol. for example sdp protocol is zero.
     */
    function setProtocol(address protocolAddress, uint32 protocolType) external;

    /**
     * @dev The upper protocol call this method to send cross-chain message.
     *
     * The upper layer protocol passes the message constructed by itself to the AM contract.
     * The AM contract will generate event SendAuthMessage containing serialized AuthMessage.
     *
     * @param senderID who send the cross-chain message to upper protocol.
     * @param message message from upper protocol.
     */
    function recvFromProtocol(address senderID, bytes memory message) external;

    /**
     * @dev The relayer call this method to submit raw cross-chain message.
     *
     * The relayer submits the cross-chain messages in the network to the AM contract.
     * The AM contract decodes the message.
     * Then, the upper-layer protocol message is parsed and the
     * message is passed to the upper-layer protocol through inter-contract calls.
     *
     * @param pkg raw cross-chain message submitted by relayer.
     */
    function recvPkgFromRelayer(bytes memory pkg) external;
}
