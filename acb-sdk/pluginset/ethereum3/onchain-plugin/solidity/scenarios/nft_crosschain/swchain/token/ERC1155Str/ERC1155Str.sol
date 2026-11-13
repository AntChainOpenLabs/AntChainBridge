// SPDX-License-Identifier: MIT
// OpenZeppelin Contracts (last updated v4.8.0) (token/ERC1155/ERC1155.sol)

pragma solidity ^0.8.0;

import "./IERC1155Str.sol";
import "./IERC1155StrReceiver.sol";
import "./extensions/IERC1155StrMetadataURI.sol";
import "../../../erc1155/@openzeppelin/contracts/utils/Address.sol";
import "../../../erc1155/@openzeppelin/contracts/utils/Context.sol";
import "../../../erc1155/@openzeppelin/contracts/utils/Strings.sol";

import "../../../erc1155/@openzeppelin/contracts/utils/introspection/ERC165.sol";

/**
 * @dev Implementation of the basic standard multi-token.
 * See https://eips.ethereum.org/EIPS/eip-1155
 * Originally based on code by Enjin: https://github.com/enjin/erc-1155
 *
 * _Available since v3.1._
 */
contract ERC1155Str is Context, IERC1155Str, IERC1155StrMetadataURI, ERC165 {
  using Address for address;
  using Strings for uint256;
  // amount is amount or amount is id
  bool public isNFTMode;

  // Mapping from token ID to account balances
  mapping(string => mapping(address => uint256)) private _balances;

  // Mapping from account to operator approvals
  mapping(address => mapping(address => bool)) private _operatorApprovals;

  // Used as the URI for all token types by relying on ID substitution, e.g. https://token-cdn-domain/{id}.json
  string private _uri;

  struct OriginToken {
    uint256 tokenId;
    uint256 amount;
  }
  mapping(string => OriginToken) private _mapStrTokenToToken;
  mapping(string => uint256) private _mintTokenCount;

  /**
   * @dev See {_setURI}.
   */
  constructor(string memory uri_, bool modeFlag) {
    _setURI(uri_);
    isNFTMode = modeFlag;
  }

  /**
   * @dev See {IERC165-supportsInterface}.
   */
  function supportsInterface(bytes4 interfaceId) public view virtual override(ERC165, IERC165) returns (bool) {
    return interfaceId == type(IERC1155Str).interfaceId || interfaceId == type(IERC1155StrMetadataURI).interfaceId || super.supportsInterface(interfaceId);
  }

  /**
   * @dev See {IERC1155MetadataURI-uri}.
   *
   * This implementation returns the same URI for *all* token types. It relies
   * on the token type ID substitution mechanism
   * https://eips.ethereum.org/EIPS/eip-1155#metadata[defined in the EIP].
   *
   * Clients calling this function must replace the `\{id\}` substring with the
   * actual token type ID.
   */
  function uri(string memory) public view virtual override returns (string memory) {
    return _uri;
  }

  /**
   * @dev See {IERC1155-balanceOf}.
   *
   * Requirements:
   *
   * - `account` cannot be the zero address.
   */
  function balanceOf(address account, uint256 id) public view virtual override returns (uint256) {
    return balanceOf(account, id.toString());
  }

  function balanceOf(address account, string memory id) public view virtual override returns (uint256) {
    require(account != address(0), "ERC1155: address zero is not a valid owner");
    return _balances[id][account];
  }

  /**
   * @dev See {IERC1155-balanceOfBatch}.
   *
   * Requirements:
   *
   * - `accounts` and `ids` must have the same length.
   */
  function balanceOfBatch(address[] memory accounts, uint256[] memory ids) public view virtual returns (uint256[] memory) {
    require(accounts.length == ids.length, "ERC1155: accounts and ids length mismatch");

    uint256[] memory batchBalances = new uint256[](accounts.length);

    for (uint256 i = 0; i < accounts.length; ++i) {
      batchBalances[i] = balanceOf(accounts[i], ids[i]);
    }

    return batchBalances;
  }

  function balanceOfBatch(address[] memory accounts, string[] memory ids) public view virtual override returns (uint256[] memory) {
    require(accounts.length == ids.length, "ERC1155: accounts and ids length mismatch");

    uint256[] memory batchBalances = new uint256[](accounts.length);

    for (uint256 i = 0; i < accounts.length; ++i) {
      batchBalances[i] = balanceOf(accounts[i], ids[i]);
    }

    return batchBalances;
  }

  /**
   * @dev See {IERC1155-setApprovalForAll}.
   */
  function setApprovalForAll(address operator, bool approved) public virtual override {
    _setApprovalForAll(_msgSender(), operator, approved);
  }

  /**
   * @dev See {IERC1155-isApprovedForAll}.
   */
  function isApprovedForAll(address account, address operator) public view virtual override returns (bool) {
    return _operatorApprovals[account][operator];
  }

  /**
   * @dev See {IERC1155-safeTransferFrom}.
   */
  function safeTransferFrom(address from, address to, uint256 id, uint256 amount, bytes memory data) public virtual {
    safeTransferFrom(from, to, id.toString(), amount, data);
  }

  function safeTransferFrom(address from, address to, string memory id, uint256 amount, bytes memory data) public virtual override {
    require(from == _msgSender() || isApprovedForAll(from, _msgSender()), "ERC1155: caller is not token owner or approved");
    _safeTransferFrom(from, to, id, amount, data);
  }

  /**
   * @dev See {IERC1155-safeBatchTransferFrom}.
   */
  function safeBatchTransferFrom(address from, address to, uint256[] memory ids, uint256[] memory amounts, bytes memory data) public virtual override {
    require(from == _msgSender() || isApprovedForAll(from, _msgSender()), "ERC1155: caller is not token owner or approved");
    string[] memory tokenIds = new string[](ids.length);
    for(uint256 i = 0; i < ids.length; ++i) {
      tokenIds[i] = ids[i].toString();
    }
    _safeBatchTransferFrom(from, to, tokenIds, amounts, data);
  }

  function safeBatchTransferFrom(address from, address to, string[] memory ids, uint256[] memory amounts, bytes memory data) public virtual override {
    require(from == _msgSender() || isApprovedForAll(from, _msgSender()), "ERC1155: caller is not token owner or approved");
    _safeBatchTransferFrom(from, to, ids, amounts, data);
  }

  /**
   * @dev Transfers `amount` tokens of token type `id` from `from` to `to`.
   *
   * Emits a {TransferSingle} event.
   *
   * Requirements:
   *
   * - `to` cannot be the zero address.
   * - `from` must have a balance of tokens of type `id` of at least `amount`.
   * - If `to` refers to a smart contract, it must implement {IERC1155Receiver-onERC1155Received} and return the
   * acceptance magic value.
   */
  function _safeTransferFrom(address from, address to, string memory id, uint256 amount, bytes memory data) internal virtual {
    require(to != address(0), "ERC1155: transfer to the zero address");

    address operator = _msgSender();
    string[] memory ids = _asSingletonStringArray(id);
    uint256[] memory amounts = _asSingletonArray(amount);

    _beforeTokenTransfer(operator, from, to, ids, amounts, data);

    uint256 fromBalance = _balances[id][from];
    require(fromBalance >= amount, "ERC1155: insufficient balance for transfer");
    unchecked {
      _balances[id][from] = fromBalance - amount;
    }
    _balances[id][to] += amount;

    emit TransferSingle(operator, from, to, id, amount);

    _afterTokenTransfer(operator, from, to, ids, amounts, data);

    _doSafeTransferAcceptanceCheck(operator, from, to, id, amount, data);
  }

  /**
   * @dev xref:ROOT:erc1155.adoc#batch-operations[Batched] version of {_safeTransferFrom}.
   *
   * Emits a {TransferBatch} event.
   *
   * Requirements:
   *
   * - If `to` refers to a smart contract, it must implement {IERC1155Receiver-onERC1155BatchReceived} and return the
   * acceptance magic value.
   */
  function _safeBatchTransferFrom(address from, address to, string[] memory ids, uint256[] memory amounts, bytes memory data) internal virtual {
    require(ids.length == amounts.length, "ERC1155: ids and amounts length mismatch");
    require(to != address(0), "ERC1155: transfer to the zero address");

    address operator = _msgSender();

    _beforeTokenTransfer(operator, from, to, ids, amounts, data);

    for (uint256 i = 0; i < ids.length; ++i) {
      string memory id = ids[i];
      uint256 amount = amounts[i];

      uint256 fromBalance = _balances[id][from];
      require(fromBalance >= amount, "ERC1155: insufficient balance for transfer");
      unchecked {
        _balances[id][from] = fromBalance - amount;
      }
      _balances[id][to] += amount;
    }

    emit TransferBatch(operator, from, to, ids, amounts);

    _afterTokenTransfer(operator, from, to, ids, amounts, data);

    _doSafeBatchTransferAcceptanceCheck(operator, from, to, ids, amounts, data);
  }

  /**
   * @dev Sets a new URI for all token types, by relying on the token type ID
   * substitution mechanism
   * https://eips.ethereum.org/EIPS/eip-1155#metadata[defined in the EIP].
   *
   * By this mechanism, any occurrence of the `\{id\}` substring in either the
   * URI or any of the amounts in the JSON file at said URI will be replaced by
   * clients with the token type ID.
   *
   * For example, the `https://token-cdn-domain/\{id\}.json` URI would be
   * interpreted by clients as
   * `https://token-cdn-domain/000000000000000000000000000000000000000000000000000000000004cce0.json`
   * for token type ID 0x4cce0.
   *
   * See {uri}.
   *
   * Because these URIs cannot be meaningfully represented by the {URI} event,
   * this function emits no events.
   */
  function _setURI(string memory newuri) internal virtual {
    _uri = newuri;
  }

  /**
   * @dev Creates `amount` tokens of token type `id`, and assigns them to `to`.
   *
   * Emits a {TransferSingle} event.
   *
   * Requirements:
   *
   * - `to` cannot be the zero address.
   * - If `to` refers to a smart contract, it must implement {IERC1155Receiver-onERC1155Received} and return the
   * acceptance magic value.
   * ! 调用此方法前需要完成token的格式转换
   */
  function _mint(address to, string memory id, uint256 amount, bytes memory data) internal virtual {
    require(to != address(0), "ERC1155: mint to the zero address");
    if (isNFTMode) {
      // 判断是否已经铸造过
      require(_mintTokenCount[id] == 0, "token already minted");
    }

    address operator = _msgSender();
    string[] memory ids = _asSingletonStringArray(id);
    uint256[] memory amounts = _asSingletonArray(amount);

    _beforeTokenTransfer(operator, address(0), to, ids, amounts, data);

    _balances[id][to] += amount;
    _mintTokenCount[id] += amount;
    emit TransferSingle(operator, address(0), to, id, amount);

    _afterTokenTransfer(operator, address(0), to, ids, amounts, data);

    _doSafeTransferAcceptanceCheck(operator, address(0), to, id, amount, data);
  }

  /**
   * @dev xref:ROOT:erc1155.adoc#batch-operations[Batched] version of {_mint}.
   *
   * Emits a {TransferBatch} event.
   *
   * Requirements:
   *
   * - `ids` and `amounts` must have the same length.
   * - If `to` refers to a smart contract, it must implement {IERC1155Receiver-onERC1155BatchReceived} and return the
   * acceptance magic value.
   * ! 调用此方法前需要完成token的格式转换
   */
  function _mintBatch(address to, string[] memory ids, uint256[] memory amounts, bytes memory data) internal virtual {
    require(to != address(0), "ERC1155: mint to the zero address");
    require(ids.length == amounts.length, "ERC1155: ids and amounts length mismatch");

    address operator = _msgSender();

    _beforeTokenTransfer(operator, address(0), to, ids, amounts, data);

    for (uint256 i = 0; i < ids.length; i++) {
      if (isNFTMode) {
        // 判断是否已经铸造过
        require(_mintTokenCount[ids[i]] == 0, "token already minted");
      }
      _balances[ids[i]][to] += amounts[i];
      _mintTokenCount[ids[i]] += amounts[i];
    }

    emit TransferBatch(operator, address(0), to, ids, amounts);

    _afterTokenTransfer(operator, address(0), to, ids, amounts, data);

    _doSafeBatchTransferAcceptanceCheck(operator, address(0), to, ids, amounts, data);
  }

  /**
   * @dev Destroys `amount` tokens of token type `id` from `from`
   *
   * Emits a {TransferSingle} event.
   *
   * Requirements:
   *
   * - `from` cannot be the zero address.
   * - `from` must have at least `amount` tokens of token type `id`.
   */
  function _burn(address from, string memory id, uint256 amount) internal virtual {
    require(from != address(0), "ERC1155: burn from the zero address");

    address operator = _msgSender();
    string[] memory ids = _asSingletonStringArray(id);
    uint256[] memory amounts = _asSingletonArray(amount);

    _beforeTokenTransfer(operator, from, address(0), ids, amounts, "");

    uint256 fromBalance = _balances[id][from];
    require(fromBalance >= amount, "ERC1155: burn amount exceeds balance");
    unchecked {
      _balances[id][from] = fromBalance - amount;
      _mintTokenCount[id] -= amount;
    }

    emit TransferSingle(operator, from, address(0), id, amount);

    _afterTokenTransfer(operator, from, address(0), ids, amounts, "");
  }

  /**
   * @dev xref:ROOT:erc1155.adoc#batch-operations[Batched] version of {_burn}.
   *
   * Emits a {TransferBatch} event.
   *
   * Requirements:
   *
   * - `ids` and `amounts` must have the same length.
   */
  function _burnBatch(address from, string[] memory ids, uint256[] memory amounts) internal virtual {
    require(from != address(0), "ERC1155: burn from the zero address");
    require(ids.length == amounts.length, "ERC1155: ids and amounts length mismatch");

    address operator = _msgSender();

    _beforeTokenTransfer(operator, from, address(0), ids, amounts, "");

    for (uint256 i = 0; i < ids.length; i++) {
      string memory id = ids[i];
      uint256 amount = amounts[i];

      uint256 fromBalance = _balances[id][from];
      require(fromBalance >= amount, "ERC1155: burn amount exceeds balance");
      unchecked {
        _balances[id][from] = fromBalance - amount;
        _mintTokenCount[id] -= amount;
      }
    }

    emit TransferBatch(operator, from, address(0), ids, amounts);

    _afterTokenTransfer(operator, from, address(0), ids, amounts, "");
  }

  /**
   * @dev Approve `operator` to operate on all of `owner` tokens
   *
   * Emits an {ApprovalForAll} event.
   */
  function _setApprovalForAll(address owner, address operator, bool approved) internal virtual {
    require(owner != operator, "ERC1155: setting approval status for self");
    _operatorApprovals[owner][operator] = approved;
    emit ApprovalForAll(owner, operator, approved);
  }

  /**
   * @dev Hook that is called before any token transfer. This includes minting
   * and burning, as well as batched variants.
   *
   * The same hook is called on both single and batched variants. For single
   * transfers, the length of the `ids` and `amounts` arrays will be 1.
   *
   * Calling conditions (for each `id` and `amount` pair):
   *
   * - When `from` and `to` are both non-zero, `amount` of ``from``'s tokens
   * of token type `id` will be  transferred to `to`.
   * - When `from` is zero, `amount` tokens of token type `id` will be minted
   * for `to`.
   * - when `to` is zero, `amount` of ``from``'s tokens of token type `id`
   * will be burned.
   * - `from` and `to` are never both zero.
   * - `ids` and `amounts` have the same, non-zero length.
   *
   * To learn more about hooks, head to xref:ROOT:extending-contracts.adoc#using-hooks[Using Hooks].
   */
  function _beforeTokenTransfer(address operator, address from, address to, string[] memory ids, uint256[] memory amounts, bytes memory data) internal virtual {}

  /**
   * @dev Hook that is called after any token transfer. This includes minting
   * and burning, as well as batched variants.
   *
   * The same hook is called on both single and batched variants. For single
   * transfers, the length of the `id` and `amount` arrays will be 1.
   *
   * Calling conditions (for each `id` and `amount` pair):
   *
   * - When `from` and `to` are both non-zero, `amount` of ``from``'s tokens
   * of token type `id` will be  transferred to `to`.
   * - When `from` is zero, `amount` tokens of token type `id` will be minted
   * for `to`.
   * - when `to` is zero, `amount` of ``from``'s tokens of token type `id`
   * will be burned.
   * - `from` and `to` are never both zero.
   * - `ids` and `amounts` have the same, non-zero length.
   *
   * To learn more about hooks, head to xref:ROOT:extending-contracts.adoc#using-hooks[Using Hooks].
   */
  function _afterTokenTransfer(address operator, address from, address to, string[] memory ids, uint256[] memory amounts, bytes memory data) internal virtual {}

  function _doSafeTransferAcceptanceCheck(address operator, address from, address to, string memory id, uint256 amount, bytes memory data) private {
    if (to.isContract()) {
      try IERC1155StrReceiver(to).onERC1155Received(operator, from, id, amount, data) returns (bytes4 response) {
        if (response != IERC1155StrReceiver.onERC1155Received.selector) {
          revert("ERC1155: ERC1155Receiver rejected tokens");
        }
      } catch Error(string memory reason) {
        revert(reason);
      } catch {
        revert("ERC1155: transfer to non-ERC1155Receiver implementer");
      }
    }
  }

  function _doSafeBatchTransferAcceptanceCheck(address operator, address from, address to, string[] memory ids, uint256[] memory amounts, bytes memory data) private {
    if (to.isContract()) {
      try IERC1155StrReceiver(to).onERC1155BatchReceived(operator, from, ids, amounts, data) returns (bytes4 response) {
        if (response != IERC1155StrReceiver.onERC1155BatchReceived.selector) {
          revert("ERC1155: ERC1155Receiver rejected tokens");
        }
      } catch Error(string memory reason) {
        revert(reason);
      } catch {
        revert("ERC1155: transfer to non-ERC1155Receiver implementer");
      }
    }
  }

  function _asSingletonArray(uint256 element) private pure returns (uint256[] memory) {
    uint256[] memory array = new uint256[](1);
    array[0] = element;

    return array;
  }

  function _asSingletonStringArray(string memory element) private pure returns (string[] memory) {
    string[] memory array = new string[](1);
    array[0] = element;

    return array;
  }

  /**
   * 对齐格式，确定当前资产合约的amount是id还是count
   */
  function _alignTokenIdAndAmount(uint256 prefixToken, uint256 amountOrShareId) internal returns (string memory strTokenId, uint256 amount) {
    if (isNFTMode) {
      strTokenId = string(abi.encodePacked(prefixToken.toString(), ",", amountOrShareId.toString()));
      amount = 1;
    } else {
      strTokenId = prefixToken.toString();
      amount = amountOrShareId;
    }
    _mapStrTokenToToken[strTokenId] = OriginToken(prefixToken, amountOrShareId);
  }

  /**
   * 对齐格式，确定当前资产合约的amount是id还是count
   */
  function alignTokenIdAndAmount(uint256[] memory prefixTokens, uint256[] memory amountOrShareIds) public returns (string[] memory, uint256[] memory) {
    require(prefixTokens.length == amountOrShareIds.length, "ERC1155 Str: miss length");
    string[] memory strTokenIds = new string[](prefixTokens.length);
    uint256[] memory amounts = new uint256[](prefixTokens.length);
    for (uint256 i = 0; i < prefixTokens.length; ++i) {
      (strTokenIds[i], amounts[i]) = _alignTokenIdAndAmount(prefixTokens[i], amountOrShareIds[i]);
    }
    return (strTokenIds, amounts);
  }

  // (str tokenId, amount?) => (uint tokenId, amount)
  function getOriginToken(string memory strToken, uint256 maybeAmount) public view returns (uint256 tokenId, uint256 amount) {
    OriginToken memory o = _mapStrTokenToToken[strToken];
    tokenId = o.tokenId;
    amount = isNFTMode ? o.amount : maybeAmount;
  }

  function getOriginTokenFromList(string[] memory strTokenIds, uint256[] memory maybeAmounts) public view returns (uint256[] memory, uint256[] memory) {
    require(strTokenIds.length == maybeAmounts.length, "ERC1155 Str: miss match length");
    require(strTokenIds.length != 0, "ERC1155 Str: can not empty");
    uint256[] memory tokenIds = new uint256[](strTokenIds.length);
    uint256[] memory amounts = new uint256[](maybeAmounts.length);
    for (uint i = 0; i < strTokenIds.length; i++) {
      (tokenIds[i], amounts[i]) = getOriginToken(strTokenIds[i], maybeAmounts[i]);
    }
    return (tokenIds, amounts);
  }
}
