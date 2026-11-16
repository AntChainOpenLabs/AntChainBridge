pragma solidity ^0.8.12;

import "../erc1155/@openzeppelin/contracts/token/ERC1155/IERC1155Receiver.sol";
import "../erc1155/@openzeppelin/contracts/token/ERC1155/IERC1155.sol";
import "../erc1155/ERC1155CrossChainMapping.sol";
import "../erc1155/@openzeppelin/contracts/utils/introspection/ERC165.sol";
import "./utils/SafeMath.sol";
import "./utils/InterContractMessageInterface.sol";
import "../../../sys/lib/utils/BytesToTypes.sol";
import "../../../sys/lib/utils/TypesToBytes.sol";

contract TokenBridge is IERC1155Receiver, ERC165 {
    using SafeMath for uint256;

    enum CrossChainStatus {
        START,
        SUCCESS,
        ERROR
    }

    // prevent reentrancy attack
    uint256 private unlocked = 1;

    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");
    bytes32 public constant DEFAULT_ROLE = 0x00;

    // cross-chain bridge contracts on the current chain
    // sdp message contract
    address public sdp_msg_address;

    mapping(address => bytes32) private _roles;

    // only ADMIN and contract operators can lock and unlock
    mapping(address => address) private _operators;

    // `token_bridges` records the mapping relationship from domain to token_bridge
    // domain of the other chain -> tb contract address of the chain
    mapping(string => bytes32) public token_bridges;

    // `route_table` records the anchor contract address of the domain
    // asset contract address of the current chain -> domain of the other chain -> asset contract address of the other chain
    mapping(address => mapping(string => bytes32)) public route_table;

    // `asset_lock_record` records the lock status of assets on the current chain
    // contract address(assets are locked in the contract) -> asset id -> asset amount
    mapping(address => mapping(uint256 => uint256)) public asset_lock_record;

    error TokenBridgeError(string message);

    /**
    `CrossChain` is used to record asset cross-chain transactions status.
    The `_domain` argument MUST be be a registered blockchain domain.
    The `_src_contract` argument represents the contract address of the asset source.
    The `_dest_contract` argument represents the contract address of the asset anchored in the `_domain`.
    The `_id` argument represents the cross-chain asset id.
    The `_amounts` argument represents the cross-chain asset amounts.
    The `_holder` argument represents the owner of the asset after cross-chain.
    The `_status`  argument represents the status of cross-chain(START, SUCCESS, ERROR)
*/
    event CrossChain(
        string indexed _domain,
        bytes32 indexed _src_contract,
        bytes32 indexed _dest_contract,
        uint256[] _ids,
        uint256[] _amounts,
        bytes32 _holder,
        uint8 _status
    );

    modifier lock() {
        require(unlocked == 1, "TokenBridge: LOCKED");
        unlocked = 0;
        _;
        unlocked = 1;
    }

    // permission check
    modifier onlyRole(bytes32 _role) {
        require(_roles[msg.sender] == _role, "TokenBridge: INVALID_PERMISSION");
        _;
    }

    modifier onlyRoleOrOperator(bytes32 _role, address _contract) {
        require(_roles[msg.sender] == _role || _operators[_contract] == msg.sender, "TokenBridge: INVALID_PERMISSION");
        _;
    }

    modifier onlySdpMsg() {
        require(msg.sender == sdp_msg_address, "TokenBridge: INVALID_PERMISSION");
        _;
    }

    constructor(address _sdp_msg_address) {
        _init(_sdp_msg_address);
    }

    function _init(address _sdp_msg_address) internal {
        require(
            _sdp_msg_address != address(0),
            "TokenBridge: INVALID_SDP_MSG_ADDRESS"
        );
        require(
            sdp_msg_address == address(0),
            "only call once"
        );
        _roles[msg.sender] = ADMIN_ROLE;
        sdp_msg_address = _sdp_msg_address;
    }

    function init(address _sdp_msg_address) external virtual {
        _init(_sdp_msg_address);
    }

    function setSdpMsgAddress(
        address _sdp_msg_address
    ) external onlyRole(ADMIN_ROLE) {
        require(_sdp_msg_address != address(0), "TokenBridge: INVALID_SDP_MSG_ADDRESS");
        sdp_msg_address = _sdp_msg_address;
    }

    function setDomainTokenBridgeAddress(
        string memory _domain,
        bytes32 _token_bridge_address
    ) external onlyRole(ADMIN_ROLE) {
        require(_token_bridge_address != bytes32(0), "TokenBridge: INVALID_TOKEN_BRIDGE_ADDRESS");
        token_bridges[_domain] = _token_bridge_address;
    }

    function setContractOperator (
        address _contract,
        address _operator
    ) external onlyRole(ADMIN_ROLE) {
        require(_operator != address(0), "TokenBridge: INVALID_OPERATOR");
        _operators[_contract] = _operator;
    }

    function grantRole(
        bytes32 _role,
        address _account
    ) external onlyRole(ADMIN_ROLE) {
        require(_account != address(0), "TokenBridge: INVALID_ACCOUNT");
        _roles[_account] = _role;
    }

    function revokeRole(
        address _account
    ) external onlyRole(ADMIN_ROLE) {
        require(_account != address(0), "TokenBridge: INVALID_ACCOUNT");
        _roles[_account] = DEFAULT_ROLE;
    }

    function registerRouter(
        address _src_contract,
        string memory _domain,
        bytes32 _dest_contract
    ) external onlyRole(ADMIN_ROLE) {
        require(
            route_table[_src_contract][_domain] == bytes32(0),
            "TokenBridge: ROUTER_EXISTS"
        );

        require(
            _dest_contract != bytes32(0),
            "TokenBridge: INVALID_DEST_CONTRACT"
        );

        route_table[_src_contract][_domain] = _dest_contract;
    }

    function deregisterRouter(
        address _src_contract,
        string memory _domain
    ) external onlyRole(ADMIN_ROLE) {
        require(
            route_table[_src_contract][_domain] != bytes32(0),
            "TokenBridge: ROUTER_IS_NOT_EXISTED"
        );
        route_table[_src_contract][_domain] = bytes32(0);
    }

    function getAddressFromBytes32(
        bytes32 raw
    ) internal pure returns (address) {
        bytes memory rawId = new bytes(32);
        TypesToBytes.bytes32ToBytes(32, raw, rawId);
        return BytesToTypes.bytesToAddress(32, rawId);
    }

    // When tb contract receives the cross-chain message,
    // if it is the cross-chain message receipt, it will directly throw out the corresponding cross-chain message status event;
    // if it is the cross-chain message request, it will mint or unlock the corresponding asset and call the `sendUnorderedMessage`
    //   function of SDP contract to send the cross-chain message receipt.
    function recvUnorderedMessage(
        string memory _from_domain,
        bytes32 _sender,
        bytes memory _message
    ) external virtual lock onlySdpMsg {
        // 1. Decode cross-chain message.
        (
            uint256[] memory ids,
            uint256[] memory amounts,
            bytes32 src_contract,
            bytes32 dest_contract,
            bytes32 holder,
            uint8 status
        ) = abi.decode(
                _message,
                (uint256[], uint256[], bytes32, bytes32, bytes32, uint8)
            );

        require(ids.length == amounts.length, "TokenBridge: LENGTH_MISMATCH");

        address dest_contract_addr = getAddressFromBytes32(dest_contract);
        address holder_addr = getAddressFromBytes32(holder);

        // 2.1 The message is a cross-chain message receipt.
        // notify origin domain
        if (status == uint8(CrossChainStatus.SUCCESS)) {
            emit CrossChain(
                _from_domain,
                src_contract,
                dest_contract,
                ids,
                amounts,
                holder,
                uint8(CrossChainStatus.SUCCESS)
            );
            return;
        }

        // 2.2 The message is a cross-chain message request.

        // 2.2.1 Verify that the source chain and the source TB contract of the current cross-chain message are consistent.
        require(token_bridges[_from_domain] == _sender, "TokenBridge: UNKNOWN_TOKEN_BRIDGE");

        // 2.2.2 Verify that the source asset contract for the cross-chain message is the same as in `route_table`.
        require(
            route_table[dest_contract_addr][_from_domain] == src_contract,
            "TokenBridge: ROUTER_IS_NOT_EXISTED"
        );

        // 2.2.3 Mint or unlock asset.
        _mintOrUnlock(ids, amounts, dest_contract_addr, holder_addr);

        // 2.2.4 Send cross-chain message receipt.
        // notify origin domain
        InterContractMessageInterface(sdp_msg_address).sendUnorderedMessage(
            _from_domain,
            token_bridges[_from_domain],
            abi.encode(
                ids,
                amounts,
                src_contract,
                dest_contract,
                holder,
                uint8(CrossChainStatus.SUCCESS)
            )
        );
    }

    // admin interface
    function batchUnlock(
        address _contract,
        address _to,
        uint256[] memory _ids,
        uint256[] memory _amounts
    ) external lock onlyRoleOrOperator(ADMIN_ROLE, _contract) {
        require(_ids.length == _amounts.length, "TokenBridge: LENGTH_MISMATCH");

        for (uint256 i = 0; i < _ids.length; ++i) {
            require(asset_lock_record[_contract][_ids[i]] >= _amounts[i], "TokenBridge: INSUFFICIENT_BALANCE");
            asset_lock_record[_contract][_ids[i]] -= _amounts[i];
        }
        
        IERC1155(_contract).safeBatchTransferFrom(
            address(this),
            _to,
            _ids,
            _amounts,
            ""
        );
    }

    function onERC1155Received(
        address,
        address,
        uint256 _id,
        uint256 _amount,
        bytes memory _data
    ) external virtual override lock returns (bytes4) {
        // 1. Decode to get dest chain and dest account.
        (string memory dest_domain, bytes32 holder) = abi.decode(
            _data,
            (string, bytes32)
        );

        // 2. The tb contract address of the dest chain cannot be 0.
        require(
            token_bridges[dest_domain] != bytes32(0),
            "TokenBridge: UNKNOWN_TOKEN_BRIDGE"
        );

        // 3. The asset contract address of the dest chain cannot be 0.
        require(
            route_table[msg.sender][dest_domain] != bytes32(0),
            "TokenBridge: ROUTER_IS_NOT_EXISTED"
        );

        // 4. Lock the sender's assets.
        asset_lock_record[msg.sender][_id] = asset_lock_record[msg.sender][_id]
            .add(_amount);

        // 5. Send cross-chain message request.
        uint256[] memory ids = new uint256[](1);
        ids[0] = _id;
        uint256[] memory amounts = new uint256[](1);
        amounts[0] = _amount;
        InterContractMessageInterface(sdp_msg_address).sendUnorderedMessage(
            dest_domain,
            token_bridges[dest_domain],
            abi.encode(
                ids,
                amounts,
                TypesToBytes.addressToBytes32(msg.sender),
                route_table[msg.sender][dest_domain],
                holder,
                uint8(CrossChainStatus.START)
            )
        );

        emit CrossChain(
            dest_domain,
            TypesToBytes.addressToBytes32(msg.sender),
            route_table[msg.sender][dest_domain],
            ids,
            amounts,
            holder,
            uint8(CrossChainStatus.START)
        );

        return this.onERC1155Received.selector;
    }

    function onERC1155BatchReceived(
        address,
        address,
        uint256[] memory _ids,
        uint256[] memory _amounts,
        bytes memory _data
    ) external virtual override lock returns (bytes4) {
        (string memory dest_domain, address holder) = abi.decode(
            _data,
            (string, address)
        );

        require(_ids.length == _amounts.length, "TokenBridge: LENGTH_MISMATCH");

        require(
            token_bridges[dest_domain] != bytes32(0),
            "TokenBridge: UNKNOWN_TOKEN_BRIDGE"
        );
        require(
            route_table[msg.sender][dest_domain] != bytes32(0),
            "TokenBridge: ROUTER_IS_NOT_EXISTED"
        );

        for (uint256 i = 0; i < _ids.length; ++i) {
            asset_lock_record[msg.sender][_ids[i]] = asset_lock_record[
                msg.sender
            ][_ids[i]].add(_amounts[i]);
        }

        bytes32 holderB32 = TypesToBytes.addressToBytes32(holder);

        InterContractMessageInterface(sdp_msg_address).sendUnorderedMessage(
            dest_domain,
            token_bridges[dest_domain],
            abi.encode(
                _ids,
                _amounts,
                TypesToBytes.addressToBytes32(msg.sender),
                route_table[msg.sender][dest_domain],
                holderB32,
                uint8(CrossChainStatus.START)
            )
        );

        emit CrossChain(
            dest_domain,
            TypesToBytes.addressToBytes32(msg.sender),
            route_table[msg.sender][dest_domain],
            _ids,
            _amounts,
            holderB32,
            uint8(CrossChainStatus.START)
        );

        return this.onERC1155BatchReceived.selector;
    }

    function _mintOrUnlock(
        uint256[] memory ids,
        uint256[] memory amounts,
        address dest_contract,
        address holder
    ) private {
        require(ids.length == amounts.length, "TokenBridge: LENGTH_MISMATCH");

        // mint or unlock asset
        uint256[] memory mint_ids = new uint256[](ids.length);
        uint256[] memory mint_amounts = new uint256[](ids.length);
        uint256[] memory transfer_ids = new uint256[](ids.length);
        uint256[] memory transfer_amounts = new uint256[](ids.length);

        bool mint = false;
        bool transfer = false;
        for (uint256 i = 0; i < ids.length; ++i) {
            mint_ids[i] = ids[i];
            transfer_ids[i] = ids[i];
            if (asset_lock_record[dest_contract][ids[i]] >= amounts[i]) {
                transfer_amounts[i] = amounts[i];
                asset_lock_record[dest_contract][ids[i]] -= amounts[i];
                transfer = true;
            } else {
                // mint assets if the locked assets are insufficient， only in cross chain
                transfer_amounts[i] = asset_lock_record[dest_contract][ids[i]];
                mint_amounts[i] =
                    amounts[i] -
                    asset_lock_record[dest_contract][ids[i]];
                asset_lock_record[dest_contract][ids[i]] = 0;
                mint = true;
                if (transfer_amounts[i] > 0) {
                    transfer = true;
                }
            }
        }
        if (mint) {
            ERC1155CrossChainMapping(dest_contract).mintBatchByTB(
                holder,
                mint_ids,
                mint_amounts
            );
        }

        if (transfer) {
            IERC1155(dest_contract).safeBatchTransferFrom(
                address(this),
                holder,
                transfer_ids,
                transfer_amounts,
                ""
            );
        }
    }

    function supportsInterface(bytes4 interfaceId) public view virtual override(ERC165, IERC165) returns (bool) {
       return interfaceId == type(IERC1155Receiver).interfaceId || super.supportsInterface(interfaceId);
    }
}
