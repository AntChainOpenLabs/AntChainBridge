// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
pragma experimental ABIEncoderV2;

import "./interfaces/IAuthMessage.sol";
import "./interfaces/ISubProtocol.sol";
import "./lib/am/AMLib.sol";
import "./lib/utils/Ownable.sol";
import "./@openzeppelin/contracts/proxy/utils/Initializable.sol";
import "./lib/ptc/PtcLib.sol";
import "./interfaces/IPtcHub.sol";

contract AuthMsg is IAuthMessage, Ownable, Initializable {

    struct SubProtocol {
        uint32 protocolType;
        bool exist;
    }

    // only relayer can call `recvPkgFromRelayer`
    address public relayer;

    // It is recommended that users read the `exist` field first when accessing this variable.
    // If the `exist` field is false, the `protocolType` field is invalid (pay attention to avoid
    //   the ambiguity of SDPType when type is not set).
    // addtess -> type/exist
    mapping(address => SubProtocol) public subProtocols;

    // type -> address
    mapping(uint32 => address) public protocolRoutes;

    address public ptcHubAddr;

    event SubProtocolUpdate(uint32 indexed protocolType, address protocolAddress);

    event recvAuthMessage(string sendDomain, bytes rawMsg);

    modifier onlySubProtocols {
        require(
            subProtocols[msg.sender].exist,
            "AuthMsg: sender not valid sub-protocol"
        );
        _;
    }

    modifier onlyRelayer {
        require(
            relayer == msg.sender,
            "AuthMsg: sender not valid relayer"
        );
        _;
    }

    constructor() {
        // The AM contract is deployed by relayer by default.
        // If the admin of heterogeneous chain deployed the AM contract in advance, the admin
        //   can invoke the interface `setRelayer` to set the real relayer address.
        relayer = msg.sender;
        _disableInitializers();
    }

    function init() external initializer() {
        relayer = msg.sender;
        _transferOwnership(_msgSender());
    }

    function setRelayer(address relayerAddress) external onlyOwner {
        relayer = relayerAddress;
    }

    function setProtocol(address protocolAddress, uint32 protocolType) override external onlyOwner {
        SubProtocol storage p = subProtocols[protocolAddress];
        require(!p.exist, "AuthMsg: protocol exists");
        p.exist = true;
        p.protocolType = protocolType;

        protocolRoutes[protocolType] = protocolAddress;

        emit SubProtocolUpdate(protocolType, protocolAddress);
    }

    function getProtocol(uint32 protocolType) external view returns(address) {
        return protocolRoutes[protocolType];
    }

    function setPtcHub(address _ptcHubAddr) external onlyOwner() {
        ptcHubAddr = _ptcHubAddr;
    }

    function recvFromProtocol(address senderID, bytes memory message) override external onlySubProtocols {
        _beforeSend(senderID, message);

        // use version 1 for now
        AuthMessage memory amV1 = AuthMessage(
            {
                version: 1,
                author: AMLib.encodeAddressIntoCrossChainID(senderID),
                protocolType: subProtocols[msg.sender].protocolType,
                body: message
            }
        );

        emit SendAuthMessage(AMLib.encodeAuthMessage(amV1));

        _afterSend(amV1);
    }

    // Security risks may exist here！！！
    //
    // In the current example contract, ordered cross-chain messages can be assured of uniqueness
    //   by sequence number（checked in function `_routeOrderedMessage` of function `recvMessage`
    //   of SDP contract ）, but no uniqueness check is done for unordered cross-chain messages!!!
    // By default, the relayer's replay control mechanism is trusted, that is, we trust the relay
    //   not to replay the message.
    function recvPkgFromRelayer(bytes memory pkg) override external onlyRelayer {
        _beforeReceive(pkg);

        MessageFromRelayer memory msgFromRelayer = AMLib.decodeMessageFromRelayer(pkg);
        ThirdPartyProof memory tpProof = PtcLib.decodeThirdPartyProofFrom(msgFromRelayer.proofData);
        string memory domain;
        bytes memory rawResp;
        if (tpProof.rawProof.length > 0) {
            require(ptcHubAddr != address(0x0), "ptc hub not set yet");
            IPtcHub(ptcHubAddr).verifyProof(msgFromRelayer.proofData);
            domain = tpProof.tpbtaCrossChainLane.channel.senderDomain;
            rawResp = tpProof.resp.body;
        } else {
            (domain, rawResp) = AMLib._decodeProof(msgFromRelayer.proofData);
        }

        emit recvAuthMessage(domain, rawResp);

        _afterReceive(routeAuthMessage(domain, rawResp));
    }

    function routeAuthMessage(string memory domain, bytes memory rawResp) internal returns (AuthMessage memory) {
        AuthMessage memory message = AMLib.decodeAuthMessage(rawResp);

        require(
            protocolRoutes[message.protocolType] != address(0x0),
            "AuthMsg: no protocol exist"
        );

        // Messages received from the relayer are checked to see if the received chain is the current chain
        //   in the recvMessage method of the SDP contract (that is, if the domains match).
        ISubProtocol(protocolRoutes[message.protocolType]).recvMessage(domain, message.author, message.body);

        return message;
    }

    function _beforeReceive(bytes memory pkg) internal virtual {}

    function _afterReceive(AuthMessage memory message) internal virtual {}

    function _beforeSend(address senderID, bytes memory message) internal virtual {}

    function _afterSend(AuthMessage memory message) internal virtual {}

    /**
     * @dev This empty reserved space is put in place to allow future versions to add new
     * variables without shifting down storage in the inheritance chain.
     * See https://docs.openzeppelin.com/contracts/4.x/upgradeable#storage_gaps
     */
    uint256[49] private __gap;
}
