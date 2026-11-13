// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.12;

import "./@openzeppelin/contracts/token/ERC1155/extensions/ERC1155URIStorage.sol";
import "./@openzeppelin/contracts/utils/Strings.sol";

contract ERC1155CrossChainMapping is ERC1155URIStorage {
    using Address for address; 

    address public tbAddr;

    modifier onlyTokenBridge() {
        require(msg.sender == tbAddr, "only token bridge");
        _;
    }
    
    constructor(address _tbAddr, string memory _baseuri) ERC1155(string.concat(_baseuri, "{id}.json")) {
        require(_tbAddr.isContract(), "token bridge address must be contract.");
        tbAddr = _tbAddr;
        _setBaseURI(_baseuri);
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
        for (uint idx = 0; idx < ids.length; ++idx) 
        {
            _setURI(ids[idx], Strings.toString(ids[idx]));
        }
    }
}