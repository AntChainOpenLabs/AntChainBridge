package token_bridge

import (
	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-protos-go/peer"
)

type ERC1155Receiver interface {
	/**
	 * @dev Handles the receipt of a single ERC1155 token type. This function is
	 * called at the end of a `safeTransferFrom` after the balance has been updated.
	 *
	 * NOTE: To accept the transfer, this must return
	 * `bytes4(keccak256("onERC1155Received(address,address,uint256,uint256,bytes)"))`
	 * (i.e. 0xf23a6e61, or its own function selector).
	 *
	 * @param operator The address which initiated the transfer (i.e. msg.sender)
	 * @param from The address which previously owned the token
	 * @param id The ID of the token being transferred
	 * @param value The amount of tokens being transferred
	 * @param data Additional data with no specified format
	 * @return `bytes4(keccak256("onERC1155Received(address,address,uint256,uint256,bytes)"))` if transfer is allowed
	 */
	onERC1155Received(stub shim.ChaincodeStubInterface, args [][]byte) peer.Response

	/**
	 * @dev Handles the receipt of a multiple ERC1155 token types. This function
	 * is called at the end of a `safeBatchTransferFrom` after the balances have
	 * been updated.
	 *
	 * NOTE: To accept the transfer(s), this must return
	 * `bytes4(keccak256("onERC1155BatchReceived(address,address,uint256[],uint256[],bytes)"))`
	 * (i.e. 0xbc197c81, or its own function selector).
	 *
	 * @param operator The address which initiated the batch transfer (i.e. msg.sender)
	 * @param from The address which previously owned the token
	 * @param ids An array containing ids of each token being transferred (order and length must match values array)
	 * @param values An array containing amounts of each token being transferred (order and length must match ids array)
	 * @param data Additional data with no specified format
	 * @return `bytes4(keccak256("onERC1155BatchReceived(address,address,uint256[],uint256[],bytes)"))` if transfer is allowed
	 */
	onERC1155BatchReceived(stub shim.ChaincodeStubInterface, args [][]byte) peer.Response
}
