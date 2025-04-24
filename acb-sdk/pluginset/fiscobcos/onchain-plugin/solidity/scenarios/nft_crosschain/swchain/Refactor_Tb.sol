// SPDX-License-Identifier: UNLICENSED

pragma solidity ^0.8.12;

contract Refactor_Tb {  
  
  // 'registered_tokens' record asset registered information
  // ${domain} -> (${assetAddress} -> (keccak256(bytes(${tokenId})) -> (${from}、{to})))
  mapping(string => mapping(bytes32 => mapping(bytes32 => RegisteredTokenDetails))) private registered_tokens;
  
  struct RegisteredTokenDetails {
    bytes32 from;
    bytes32 to;
  }

  // 定义一个事件，用于通知备案失败
  event AssetRegisteredFailure(string _chain_domain, bytes32 _asset_address, string[] _token_ids);

  // 定义一个事件，用于记录已经存在的tokenId
  event TokenAlreadyExists(string _chain_domain, bytes32 _asset_address, string[] _token_ids);
  
  // 定义事件：toekn删除失败的记录
  event TokenRemovalFailed(string indexed _chain_domain, bytes32 indexed _asset_address, string[] _token_ids);

  function _addRegisteredAsset(string memory _chain_domain, bytes32 _asset_address, string[] memory _token_ids, bytes32 _src_from, bytes32 _dest_holder) internal {
    string[] memory existing_token_ids = new string[](_token_ids.length);
    uint existing_token_count = 0;
    
    for (uint i = 0; i < _token_ids.length; i++) {
        string memory token_id = _token_ids[i];
        bytes32 token_id_hash = keccak256(bytes(token_id));
        if (registered_tokens[_chain_domain][_asset_address][token_id_hash].from != 0x0) {
            existing_token_ids[existing_token_count] = token_id;
            existing_token_count++;
        }
        registered_tokens[_chain_domain][_asset_address][token_id_hash] = RegisteredTokenDetails(_src_from, _dest_holder);
    }

    if (existing_token_count > 0) {
        assembly {
          mstore(existing_token_ids, existing_token_count)
        }
        emit TokenAlreadyExists(_chain_domain, _asset_address, existing_token_ids);
    }
  }

  function checkTokenRegisteredExists(string memory _chain_domain, bytes32 _asset_address, string[] memory _mint_ids, bytes32 _src_from, bytes32 _dest_holder) internal returns (bool) {
    string[] memory failed_token_ids = new string[](_mint_ids.length);
    uint failed_token_count = 0;

    for (uint i = 0; i < _mint_ids.length; i++) {
        string memory token_id = _mint_ids[i];
        bytes32 token_id_hash = keccak256(bytes(token_id));

        if ((keccak256(abi.encodePacked(registered_tokens[_chain_domain][_asset_address][token_id_hash].from)) != keccak256(abi.encodePacked(_src_from)))
          || (keccak256(abi.encodePacked(registered_tokens[_chain_domain][_asset_address][token_id_hash].to)) != keccak256(abi.encodePacked(_dest_holder)))) {
            failed_token_ids[failed_token_count] = token_id;
            failed_token_count++;
        }
    }
    
    if (failed_token_count > 0) {
        assembly {
          mstore(failed_token_ids, failed_token_count)
        }
        emit AssetRegisteredFailure(_chain_domain, _asset_address, failed_token_ids);
        return false;
    }
    return true;
  }

  function _removeAssetAndTokenId(string memory _chain_domain, bytes32 _asset_address, string[] memory _token_ids) internal {
    string[] memory not_found_token_ids = new string[](_token_ids.length);
    uint not_found_token_count = 0;
    
    for (uint i = 0; i < _token_ids.length; i++) {
        string memory token_id = _token_ids[i];

        bytes32 token_id_hash = keccak256(bytes(token_id));

        if (registered_tokens[_chain_domain][_asset_address][token_id_hash].from != 0x0) {
            delete registered_tokens[_chain_domain][_asset_address][token_id_hash];
        } else {
            not_found_token_ids[not_found_token_count] = token_id;
            not_found_token_count++;
        }
    }

    if (not_found_token_count > 0) {
        assembly {
          mstore(not_found_token_ids, not_found_token_count)
        }
        emit TokenRemovalFailed(_chain_domain, _asset_address, not_found_token_ids);
    }
  }
}