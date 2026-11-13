pragma solidity ^0.4.22;

interface AuthMsgClientInterface {

    function setProtocol(identity protocol_address, uint32 protocol_type) external;

    function addRelayers(identity relayer_address) external;

    function recvFromProtocol(identity author, bytes message) external;

    function recvPkgFromRelayer(bytes pkg) external;
}
