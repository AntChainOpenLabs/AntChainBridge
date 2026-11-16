pragma solidity ^0.8.0;

import "../IERC1155.sol";

interface IERC1155Burnable is IERC1155 {
    function burnBatch(
        address from,
        uint256[] calldata ids,
        uint256[] calldata amounts
    ) external;
}