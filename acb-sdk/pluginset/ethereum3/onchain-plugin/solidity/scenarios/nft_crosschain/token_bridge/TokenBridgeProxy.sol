// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.12;

import "../erc1155/@openzeppelin/contracts/proxy/ERC1967/ERC1967Proxy.sol";
import "../erc1155/@openzeppelin/contracts/access/AccessControl.sol";

contract TokenBridgeProxy is ERC1967Proxy, AccessControl {
    constructor(address _logic, bytes memory _data) payable ERC1967Proxy(_logic, _data) {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
    }

    function upgradeTo(address newImplementation) external onlyRole(DEFAULT_ADMIN_ROLE) {
        _upgradeTo(newImplementation);
    }

    function implementation() external view returns (address impl) {
        return _implementation();
    }
}