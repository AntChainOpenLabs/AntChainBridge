// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.12;

import "./@openzeppelin/contracts/token/ERC1155/ERC1155.sol";
import "./@openzeppelin/contracts/utils/Strings.sol";

contract ERC1155CrossChainMapping is ERC1155 {
    using Address for address;

    address public immutable tbAddr;

    string public name;

    string public symbol;

    modifier onlyTokenBridge() {
        require(msg.sender == tbAddr, "only token bridge");
        _;
    }

    constructor(address _tbAddr, string memory _name, string memory _symbol, string memory _uri) ERC1155(_uri) {
        require(_tbAddr.isContract(), "token bridge address must be contract.");
        tbAddr = _tbAddr;
        name = _name;
        symbol = _symbol;
    }

    /**
     * @dev TokenBridge contract would call this method to build the cross-chain mapping asset
     */
    function mintBatchByTB(
        address to,
        uint256[] memory ids,
        uint256[] memory amounts
    ) public onlyTokenBridge {
        _mintBatch(to, ids, amounts, "");
    }

    function uri(uint256 id) public view override returns (string memory) {
        return string.concat(super.uri(id), Strings.toString(id));
    }
}