// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

interface IContractUsingSDP {

    /**
     * @dev SDP contract would call this function to deliver the message from sender contract
     * on sender blockchain. This message sent with no order and in parallel.
     *
     * @param senderDomain the domain name of the sending blockchain.
     * @param author the id of the sender.
     * @param message the raw message from sender contract.
     */
    function recvUnorderedMessage(string memory senderDomain, bytes32 author, bytes memory message) external;

    /**
     * @dev SDP contract would call this function to deliver the message from sender contract
     * on sender blockchain. This message sent with order.
     *
     * @param senderDomain the domain name of the sending blockchain.
     * @param author the id of the sender.
     * @param message the raw message from sender contract.
     */
    function recvMessage(string memory senderDomain, bytes32 author, bytes memory message) external;
}
