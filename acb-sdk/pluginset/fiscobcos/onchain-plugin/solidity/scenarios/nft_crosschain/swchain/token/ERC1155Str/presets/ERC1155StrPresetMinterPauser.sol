// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.0;

// import "@openzeppelin/contracts/token/ERC1155/ERC1155.sol";
import "../ERC1155Str.sol";
import "../extensions/ERC1155StrBurnable.sol";
import "../extensions/ERC1155StrPausable.sol";
import "../extensions/ERC1155StrURIStorage.sol";
import "../../../access/AccessControlEnumerable.sol";

/**
 * @dev {ERC1155} token, including:
 *
 *  - ability for holders to burn (destroy) their tokens
 *  - a minter role that allows for token minting (creation)
 *  - a pauser role that allows to stop all token transfers
 *
 * This contract uses {AccessControl} to lock permissioned functions using the
 * different roles - head to its documentation for details.
 *
 * The account that deploys the contract will be granted the minter and pauser
 * roles, as well as the default admin role, which will let it grant both minter
 * and pauser roles to other accounts.
 *
 * _Deprecated in favor of https://wizard.openzeppelin.com/[Contracts Wizard]._
 */
contract ERC1155StrPresetMinterPauser is Context, AccessControlEnumerable, ERC1155StrBurnable, ERC1155StrPausable, ERC1155StrURIStorage {
  using Strings for uint256;

  bytes32 public constant MINTER_ROLE = keccak256("MINTER_ROLE");
  bytes32 public constant PAUSER_ROLE = keccak256("PAUSER_ROLE");

  mapping(string => string) _tokenMetadatas;

  address public immutable tbAddr;

  string public name;

  string public symbol;

  /**
   * @dev Grants `DEFAULT_ADMIN_ROLE`, `MINTER_ROLE`, and `PAUSER_ROLE` to the account that
   * deploys the contract.
   */
  constructor(address _tbAddr, string memory _name, string memory _symbol, bool enableNFTMode) ERC1155Str("", enableNFTMode) {
    tbAddr = _tbAddr;
    if (_tbAddr == address(0x0)) {
      _setupRole(DEFAULT_ADMIN_ROLE, _msgSender());

      _setupRole(MINTER_ROLE, _msgSender());
      _setupRole(PAUSER_ROLE, _msgSender());
    }

    name = _name;
    symbol = _symbol;
  }

  // 赋予某个用户拥有铸造的角色
  function approveMintRole(address to) public {
    // 判断是否有铸造的权限
    require(hasRole(MINTER_ROLE, _msgSender()), "ERC1155Market: must have minter role to mint");
    _setupRole(MINTER_ROLE, to);
  }

  /********** MINT api **********/
  /**
   * @dev TokenBridge contract would call this method to build the cross-chain mapping asset
   * * 从TB过来的铸造约定是已经处理过拼接的token
   */
  function mintBatchByTB(address to, string[] memory ids, uint256[] memory amounts) public {
    require(msg.sender == tbAddr, "only token bridge");
    _mintBatch(to, ids, amounts, "");
  }

  function safeMint(address to, uint256 id, uint256 amount, string memory _tokenURI, string memory metadata) public {
    (string memory strId, uint256 fixedAmount) = _alignTokenIdAndAmount(id, amount);
    _safeMint(to, strId, fixedAmount, _tokenURI, metadata);
  }

  // 铸造NFT
  function _safeMint(address to, string memory id, uint256 amount, string memory _tokenURI, string memory metadata) internal {
    // 判断是否有铸造的权限
    require(hasRole(MINTER_ROLE, _msgSender()), "ERC1155PresetMinterPauser: must have minter role to mint");
    if (bytes(_tokenMetadatas[id]).length < 1) {
      setTokenMetadata(id, metadata);
    }
    if (bytes(uri(id)).length < 1) {
      setTokenURI(id, _tokenURI);
    }
    if (bytes(metadata).length > 0) {
      _mint(to, id, amount, bytes(metadata));
    } else {
      _mint(to, id, amount, "");
    }
  }

  function safeMintBatch(address to, uint256[] memory ids, uint256[] memory amounts, bytes memory data) public {
    (string[] memory strIds, uint256[] memory _amounts) = alignTokenIdAndAmount(ids, amounts);
    mintBatch(to, strIds, _amounts, data);
  }

  /**
   * @dev xref:ROOT:erc1155.adoc#batch-operations[Batched] variant of {mint}.
   */
  function mintBatch(address to, string[] memory ids, uint256[] memory amounts, bytes memory data) internal virtual {
    require(hasRole(MINTER_ROLE, _msgSender()), "ERC1155PresetMinterPauser: must have minter role to mint");
    _mintBatch(to, ids, amounts, data);
  }

  /**
   * @dev Pauses all token transfers.
   *
   * See {ERC1155Pausable} and {Pausable-_pause}.
   *
   * Requirements:
   *
   * - the caller must have the `PAUSER_ROLE`.
   */
  function pause() public virtual {
    require(hasRole(PAUSER_ROLE, _msgSender()), "ERC1155PresetMinterPauser: must have pauser role to pause");
    _pause();
  }

  /**
   * @dev Unpauses all token transfers.
   *
   * See {ERC1155Pausable} and {Pausable-_unpause}.
   *
   * Requirements:
   *
   * - the caller must have the `PAUSER_ROLE`.
   */
  function unpause() public virtual {
    require(hasRole(PAUSER_ROLE, _msgSender()), "ERC1155PresetMinterPauser: must have pauser role to unpause");
    _unpause();
  }

  /**
   * @dev See {IERC165-supportsInterface}.
   */
  function supportsInterface(bytes4 interfaceId) public view virtual override(AccessControlEnumerable, ERC1155Str) returns (bool) {
    return super.supportsInterface(interfaceId);
  }

  function _beforeTokenTransfer(
    address operator,
    address from,
    address to,
    string[] memory ids,
    uint256[] memory amounts,
    bytes memory data
  ) internal virtual override(ERC1155Str, ERC1155StrPausable) {
    super._beforeTokenTransfer(operator, from, to, ids, amounts, data);
  }

  /********** uri-storage and metadata api **********/
  function uri(string memory tokenId) public view virtual override(ERC1155Str, ERC1155StrURIStorage) returns (string memory) {
    return super.uri(tokenId);
  }

  function setTokenMetadata(string memory id, string memory metadata) public {
    require(hasRole(MINTER_ROLE, _msgSender()), "ERC1155PresetMinterPauser: must have minter role to mint");
    _tokenMetadatas[id] = metadata;
  }

  function tokenMetadata(uint256 id) public view returns (string memory) {
    return tokenMetadata(id.toString());
  }

  function tokenMetadata(string memory id) public view returns (string memory) {
    return _tokenMetadatas[id];
  }

  function tokenURI(uint256 tokenId) public view returns (string memory) {
    return tokenURI(tokenId.toString());
  }

  function tokenURI(string memory tokenId) public view returns (string memory) {
    return uri(tokenId);
  }

  function setTokenURI(uint256 tokenId, string memory _uri) public {
    setTokenURI(tokenId.toString(), _uri);
  }

  function setTokenURI(string memory tokenId, string memory _uri) public {
    require(hasRole(MINTER_ROLE, _msgSender()), "ERC1155PresetMinterPauser: must have minter role to mint");
    _setURI(tokenId, _uri);
  }
}
