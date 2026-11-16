pragma solidity ^0.6.4;

contract ReceiverContract {

    bytes last_msg;
    bytes last_uo_msg;

    event amNotify(string key, identity value, string enterprise);

    constructor() public {
    }

    function recvMessage(string memory domain_name, identity author, bytes memory message) public {
        require(message.length != 32, "32bytes received !");
        last_msg = message;
        emit amNotify(domain_name, author, string(message));
    }

    function getLastMsg() public view returns (bytes memory) {
        return last_msg;
    }

    function recvUnorderedMessage(string memory domain_name, identity author, bytes memory message) public {
        require(message.length != 32, "32bytes received !");
        last_uo_msg = message;
        emit amNotify(domain_name, author, string(message));
    }

    function getLastUnorderedMsg() public view returns (bytes memory) {
        return last_uo_msg;
    }

}
