// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

interface IContractWithAcks {

    /**
     * @dev SDP contract would call this function to deliver the ack response from receiver 
     * contract. The original message being acked now is processed successfully on receiving 
     * blockchain. And the original sdp packet is sent as unordered message.
     *
     * @param messageId the message id for sdp packet used to send
     * @param receiverDomain the domain name of the receiving blockchain for original message and where the ack response from.
     * @param receiver the id of the receiver for original message.
     * @param sequence sequence number of original message from sender to receiver.
     * @param nonce unique number of original message from sender to receiver.
     * @param message the message sent to receiver.
     */
    function ackOnSuccess(bytes32 messageId, string memory receiverDomain, bytes32 receiver, uint32 sequence, uint64 nonce, bytes memory message) external;

    /**
     * @dev SDP contract would call this function to deliver the ack response from receiver 
     * contract. The original message being acked now is processed with errors on receiving 
     * blockchain. And the original sdp packet is sent as unordered message.
     *
     * @param messageId the message id for sdp packet used to send
     * @param receiverDomain the domain name of the receiving blockchain for original message and where the ack response from.
     * @param receiver the id of the receiver for original message.
     * @param sequence sequence number of original message from sender to receiver.
     * @param nonce unique number of original message from sender to receiver.
     * @param message the message sent to receiver.
     * @param errorMsg the error msg from receiver.
     */
    function ackOnError(bytes32 messageId, string memory receiverDomain, bytes32 receiver, uint32 sequence, uint64 nonce, bytes memory message, string memory errorMsg) external;
}
