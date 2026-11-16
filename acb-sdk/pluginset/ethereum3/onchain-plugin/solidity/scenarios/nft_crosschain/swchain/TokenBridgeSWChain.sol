// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.12;

import "../token_bridge/TokenBridgeWithIndirectReq.sol";
import "./token/ERC1155Str/presets/ERC1155StrPresetMinterPauser.sol";
import "./Refactor_Tb.sol";

contract TokenBridgeSWChain is TokenBridgeWithIndirectReq, Refactor_Tb {
    using SafeMath for uint256;

    // `asset_lock_record_str` records the lock status of assets on the current chain
    // contract address(assets are locked in the contract) -> asset id -> asset amount
    // with string format token id
    mapping(address => mapping(string => uint256)) public asset_lock_record_str;

    struct TokenSet {
        string[] tokenIds;
    }

    struct OriginToken {
        uint256 tokenId;
        uint256 amount;
    }

    struct SrcChainInfo {
        string from_domain;
        bytes32 src_contract;
        bytes32 src_from;
    }

    struct MintOrUnlockParam {
        SrcChainInfo info;
        address holder;
        string[] mint_ids;
        uint256[] mint_amounts;
        string[] transfer_ids;
        uint256[] transfer_amounts;
    }

    constructor(address _sdp_msg_address)
        public
        TokenBridgeWithIndirectReq(_sdp_msg_address)
    {}

    function onERC1155Received(
        address,
        address _from,
        string memory _id,
        uint256 _amount,
        bytes memory _data
    ) public virtual lock returns (bytes4) {
        uint32 req_type = abi.decode(_data, (uint32));

        string[] memory ids = new string[](1);
        uint256[] memory amounts = new uint256[](1);
        ids[0] = _id;
        amounts[0] = _amount;

        if (req_type == REQ_TYPE_INDIRECT) {
            handleIndirectReq(_from, ids, amounts, _data);
        } else if (req_type == REQ_TYPE_DIRECT) {
            handleDirectReq(_from, ids, amounts, _data);
        } else {
            revert("req type not support");
        }

        return IERC1155StrReceiver.onERC1155Received.selector;
    }

    function onERC1155BatchReceived(
        address,
        address _from,
        string[] memory _str_ids,
        uint256[] memory _may_amounts,
        bytes memory _data
    ) public virtual lock returns (bytes4) {
        if (_data.length == 0) {
            return IERC1155StrReceiver.onERC1155BatchReceived.selector;
        }

        uint32 req_type = abi.decode(_data, (uint32));

        if (req_type == REQ_TYPE_INDIRECT) {
            handleIndirectReq(_from, _str_ids, _may_amounts, _data);
        } else if (req_type == REQ_TYPE_DIRECT) {
            handleDirectReq(_from, _str_ids, _may_amounts, _data);
        } else {
            revert("req type not support");
        }

        return IERC1155StrReceiver.onERC1155BatchReceived.selector;
    }

    function handleIndirectReq(
        address _from,
        string[] memory _ids_str,
        uint256[] memory _may_amounts,
        bytes memory _data
    ) internal {
        uint32 req_type;
        CrossChainMsgForIndirectReq memory req;
        (
            req_type,
            req.src_domain,
            req.dest_domain,
            req.src_asset,
            req.dest_asset,
            req.src_from,
            req.dest_holder
        ) = abi.decode(
            _data,
            (uint32, string, string, bytes32, bytes32, bytes32, bytes32)
        );
        bytes32 local_domain_hash = getLocalDomainHash();
        if (local_domain_hash == keccak256(bytes(req.src_domain))) {
            req.src_from = TypesToBytes.addressToBytes32(_from);
            req.src_asset = TypesToBytes.addressToBytes32(msg.sender);
            req.dest_asset = route_table[msg.sender][req.dest_domain];
        }

        require(
            token_bridges[req.dest_domain] != bytes32(0),
            "UNKNOW_TOKEN_BRIDGE"
        );

        for (uint256 i = 0; i < _ids_str.length; ++i) {
            asset_lock_record_str[msg.sender][
                _ids_str[i]
            ] = asset_lock_record_str[msg.sender][_ids_str[i]].add(
                _may_amounts[i]
            );
        }

        (
            uint256[] memory _ids,
            uint256[] memory _amounts
        ) = ERC1155StrPresetMinterPauser(msg.sender).getOriginTokenFromList(
                _ids_str,
                _may_amounts
            );

        (
            bool if_exist,
            ForwardInfoRecord memory f_record
        ) = checkIfForwardExist(req.src_domain, req.src_asset, req.dest_domain);

        require(if_exist, "forward not exist");

        forwardCrossChainData(
            f_record,
            encodeForwardRequestPayload(
                _ids,
                _amounts,
                f_record.forward_domain,
                req
            )
        );

        if (local_domain_hash == keccak256(bytes(req.src_domain))) {
            emit CrossChain(
                req.dest_domain,
                req.src_asset,
                req.dest_asset,
                _ids,
                _amounts,
                req.dest_holder,
                uint8(CrossChainStatus.START)
            );
        }

        emit ForwardRequest(
            f_record.forward_domain,
            token_bridges[f_record.forward_domain],
            route_table[msg.sender][f_record.forward_domain],
            req.src_domain,
            req.src_asset,
            req.dest_domain,
            req.dest_asset,
            _ids,
            _amounts,
            req.dest_holder
        );
    }

    function handleDirectReq(
        address _from,
        string[] memory _ids_str,
        uint256[] memory _may_amounts,
        bytes memory _data
    ) internal {
        (uint32 req_type, string memory dest_domain, bytes32 holder) = abi
            .decode(_data, (uint32, string, bytes32));
        require(
            token_bridges[dest_domain] != bytes32(0),
            "UNKNOW_TOKEN_BRIDGE"
        );

        require(
            route_table[msg.sender][dest_domain] != bytes32(0),
            "ROUTER_IS_NOT_EXISTED"
        );

        for (uint256 i = 0; i < _ids_str.length; ++i) {
            asset_lock_record_str[msg.sender][
                _ids_str[i]
            ] = asset_lock_record_str[msg.sender][_ids_str[i]].add(
                _may_amounts[i]
            );
        }

        (
            uint256[] memory _ids,
            uint256[] memory _amounts
        ) = ERC1155StrPresetMinterPauser(msg.sender).getOriginTokenFromList(
                _ids_str,
                _may_amounts
            );

        CrossChainAssetInfo memory asset_info = CrossChainAssetInfo({
            from: TypesToBytes.addressToBytes32(_from),
            ids: _ids,
            amounts: _amounts,
            holder: holder
        });
        CrossChainMsgForDirectReq memory cc_msg = CrossChainMsgForDirectReq({
            src_contract: TypesToBytes.addressToBytes32(msg.sender),
            dest_contract: route_table[msg.sender][dest_domain],
            asset_info: asset_info,
            status: uint8(CrossChainStatus.START),
            err_msg: "",
            payload: ""
        });

        InterContractMessageInterface(sdp_msg_address).sendUnorderedMessage(
            dest_domain,
            token_bridges[dest_domain],
            encodeCrossChainMsgForDirectReq(cc_msg)
        );

        emit CrossChain(
            dest_domain,
            TypesToBytes.addressToBytes32(msg.sender),
            route_table[msg.sender][dest_domain],
            _ids,
            _amounts,
            holder,
            uint8(CrossChainStatus.START)
        );
    }

    function handleCrossChainMsgForDirectReq(
        string memory _from_domain,
        bytes32 _sender,
        CrossChainMsgForDirectReq memory cc_msg
    ) internal override {
        bool res = false;
        string memory err_msg;

        if (token_bridges[_from_domain] != _sender) {
            err_msg = "tb not found";
            backwardTheFailForDirectReq(_from_domain, _sender, err_msg, cc_msg);
            return;
        }

        if (
            route_table[getAddressFromBytes32(cc_msg.dest_contract)][
                _from_domain
            ] != cc_msg.src_contract
        ) {
            err_msg = "router not found";
            backwardTheFailForDirectReq(_from_domain, _sender, err_msg, cc_msg);
            return;
        }

        (res, err_msg) = _mintOrUnlockWithTryCatch(
            SrcChainInfo({
                from_domain: _from_domain,
                src_contract: cc_msg.src_contract,
                src_from: cc_msg.asset_info.from
            }),
            cc_msg.asset_info.ids,
            cc_msg.asset_info.amounts,
            getAddressFromBytes32(cc_msg.dest_contract),
            getAddressFromBytes32(cc_msg.asset_info.holder)
        );

        // notify origin domain
        if (res) {
            InterContractMessageInterface(sdp_msg_address).sendUnorderedMessage(
                _from_domain,
                token_bridges[_from_domain],
                encodeCrossChainMsgForDirectReq(cc_msg)
            );
        } else {
            backwardTheFailForDirectReq(_from_domain, _sender, err_msg, cc_msg);
        }
    }

    function handleCrossChainMsgForIndirectReq(
        string memory _from_domain,
        bytes32 _sender,
        CrossChainMsgForDirectReq memory base_msg,
        bytes memory _raw_i_msg
    ) internal override {
        CrossChainMsgForIndirectReq
            memory i_msg = decodeCrossChainMsgForIndirectReq(_raw_i_msg);

        bytes32 local_domain_hash = getLocalDomainHash();
        bool res = false;
        string memory err_msg;
        if (local_domain_hash == keccak256(bytes(i_msg.dest_domain))) {
            (res, err_msg) = processIfLocalIndirectMsg(
                _from_domain,
                _sender,
                base_msg,
                i_msg
            );
            if (res) {
                backwardTheSuccess(_from_domain, base_msg, i_msg);
                return;
            }
        } else {
            (res, err_msg) = processIfRelayIndirectMsg(
                _from_domain,
                _sender,
                base_msg,
                i_msg
            );
        }

        if (!res) {
            backwardTheFail(_from_domain, _sender, base_msg, i_msg, err_msg);
        }
    }

    function processIfLocalIndirectMsg(
        string memory _from_domain,
        bytes32 _sender,
        CrossChainMsgForDirectReq memory base_msg,
        CrossChainMsgForIndirectReq memory i_msg
    ) internal override returns (bool res, string memory err_msg) {
        if (token_bridges[_from_domain] != _sender) {
            return (false, "wrong tb");
        }

        address dest_asset = getAddressFromBytes32(i_msg.dest_asset);
        if (route_table[dest_asset][i_msg.src_domain] != i_msg.src_asset) {
            return (false, "wrong route info");
        }

        address dest_holder = getAddressFromBytes32(i_msg.dest_holder);
        (res, err_msg) = _mintOrUnlockWithTryCatch(
            SrcChainInfo({
                from_domain: i_msg.src_domain,
                src_contract: i_msg.src_asset,
                src_from: i_msg.src_from
            }),
            base_msg.asset_info.ids,
            base_msg.asset_info.amounts,
            dest_asset,
            dest_holder
        );

        if (res) {
            emit CrossChainAssetReceived(
                i_msg.src_domain,
                i_msg.src_asset,
                dest_asset,
                base_msg.asset_info.ids,
                base_msg.asset_info.amounts,
                i_msg.src_from,
                dest_holder
            );
        }

        return (res, err_msg);
    }

    function processIfRelayIndirectMsg(
        string memory _from_domain,
        bytes32 _sender,
        CrossChainMsgForDirectReq memory base_msg,
        CrossChainMsgForIndirectReq memory i_msg
    ) internal override returns (bool res, string memory err_msg) {
        if (token_bridges[_from_domain] != _sender) {
            return (false, "wrong tb");
        }

        if (token_bridges[i_msg.dest_domain] != bytes32(0)) {
            (res, err_msg) = _mintOrUnlockWithTryCatch(
                SrcChainInfo({
                    from_domain: i_msg.src_domain,
                    src_contract: i_msg.src_asset,
                    src_from: i_msg.src_from
                }),
                base_msg.asset_info.ids,
                base_msg.asset_info.amounts,
                getAddressFromBytes32(base_msg.dest_contract),
                address(this)
            );
            if (res) {
                (res, err_msg) = _transferWithTryCatch(base_msg, i_msg);
            }
        } else {
            return (false, "no tb for dest domain");
        }

        return (res, err_msg);
    }

    function _revertByUnlock(
        address _asset,
        address _to,
        uint256[] memory _ids,
        uint256[] memory _amounts
    ) internal override {
        (
            string[] memory str_ids,
            uint256[] memory amounts
        ) = ERC1155StrPresetMinterPauser(_asset).alignTokenIdAndAmount(
                _ids,
                _amounts
            );

        for (uint256 i = 0; i < str_ids.length; i++) {
            asset_lock_record_str[_asset][str_ids[i]] = asset_lock_record_str[
                _asset
            ][str_ids[i]].sub(amounts[i]);
        }

        ERC1155StrPresetMinterPauser(_asset).safeBatchTransferFrom(
            address(this),
            _to,
            str_ids,
            amounts,
            ""
        );
    }

    function _revertByBurn(
        CrossChainMsgForIndirectReq memory i_msg,
        CrossChainMsgForDirectReq memory base_msg
    ) internal override {
        address asset_to_burn = getAddressFromBytes32(base_msg.src_contract);

        (
            string[] memory str_ids,
            uint256[] memory amounts
        ) = ERC1155StrPresetMinterPauser(asset_to_burn).alignTokenIdAndAmount(
                base_msg.asset_info.ids,
                base_msg.asset_info.amounts
            );

        for (uint256 i = 0; i < str_ids.length; i++) {
            asset_lock_record_str[asset_to_burn][
                str_ids[i]
            ] = asset_lock_record_str[asset_to_burn][str_ids[i]].sub(
                amounts[i]
            );
        }

        bool res = false;
        try
            ERC1155StrPresetMinterPauser(asset_to_burn).burnBatch(
                address(this),
                str_ids,
                amounts
            )
        {
            res = true;
        } catch Error(
            string memory /*reason*/
        ) {} catch (
            bytes memory /*lowLevelData*/
        ) {}

        if (!res) {
            emit BurnToRevertFail(
                i_msg.src_domain,
                i_msg.src_asset,
                i_msg.dest_domain,
                base_msg.src_contract,
                base_msg.asset_info.ids,
                base_msg.asset_info.amounts
            );
        }
    }

    function _mintOrUnlockWithTryCatch(
        SrcChainInfo memory _info,
        uint256[] memory _ids,
        uint256[] memory _may_amounts,
        address dest_contract,
        address holder
    ) internal returns (bool, string memory) {
        (
            string[] memory str_ids,
            uint256[] memory amounts
        ) = ERC1155StrPresetMinterPauser(dest_contract).alignTokenIdAndAmount(
                _ids,
                _may_amounts
            );

        // mint or unlock asset
        string[] memory mint_ids = new string[](str_ids.length);
        uint256[] memory mint_amounts = new uint256[](str_ids.length);
        string[] memory transfer_ids = new string[](str_ids.length);
        uint256[] memory transfer_amounts = new uint256[](str_ids.length);

        bool mint = false;
        bool transfer = false;
        for (uint256 i = 0; i < str_ids.length; i++) {
            mint_ids[i] = str_ids[i];
            transfer_ids[i] = str_ids[i];
            if (
                asset_lock_record_str[dest_contract][str_ids[i]] >= amounts[i]
            ) {
                transfer_amounts[i] = amounts[i];
                asset_lock_record_str[dest_contract][str_ids[i]] -= amounts[i];
                transfer = true;
            } else {
                // mint assets if the locked assets are insufficient， only in cross chain
                transfer_amounts[i] = asset_lock_record_str[dest_contract][
                    str_ids[i]
                ];
                mint_amounts[i] =
                    amounts[i] -
                    asset_lock_record_str[dest_contract][str_ids[i]];
                asset_lock_record_str[dest_contract][str_ids[i]] = 0;
                mint = true;
                if (transfer_amounts[i] > 0) {
                    transfer = true;
                }
            }
        }

        MintOrUnlockParam memory param;
        param.info = _info;
        param.holder = holder;
        param.mint_ids = mint_ids;
        param.mint_amounts = mint_amounts;
        param.transfer_ids = transfer_ids;
        param.transfer_amounts = transfer_amounts;

        if (
            mint &&
            !super.checkTokenRegisteredExists(
                param.info.from_domain,
                param.info.src_contract,
                param.mint_ids,
                param.info.src_from,
                TypesToBytes.addressToBytes32(param.holder)
            )
        ) {
            return (false, "Token registered status check failed");
        }

        try
            TokenBridgeSWChain(address(this)).mintOrUnlockInternal(
                dest_contract,
                param,
                mint,
                transfer
            )
        {} catch Error(string memory reason) {
            return (false, reason);
        } catch (
            bytes memory /*lowLevelData*/
        ) {
            return (false, "unknown");
        }

        return (true, "");
    }

    function mintOrUnlockInternal(
        address dest_contract,
        MintOrUnlockParam memory param,
        bool mintOrNot,
        bool transferOrNot
    ) external onlySelf {
        if (mintOrNot) {
            ERC1155StrPresetMinterPauser(dest_contract).mintBatchByTB(
                param.holder,
                param.mint_ids,
                param.mint_amounts
            );
        }

        if (transferOrNot) {
            ERC1155StrPresetMinterPauser(dest_contract).safeBatchTransferFrom(
                msg.sender,
                param.holder,
                param.transfer_ids,
                param.transfer_amounts,
                ""
            );
        }
    }

    function _transferWithTryCatch(
        CrossChainMsgForDirectReq memory base_msg,
        CrossChainMsgForIndirectReq memory i_msg
    ) internal override returns (bool, string memory) {
        address asset = getAddressFromBytes32(base_msg.dest_contract);

        (
            string[] memory str_ids,
            uint256[] memory amounts
        ) = ERC1155StrPresetMinterPauser(asset).alignTokenIdAndAmount(
                base_msg.asset_info.ids,
                base_msg.asset_info.amounts
            );

        try
            ERC1155StrPresetMinterPauser(asset).safeBatchTransferFrom(
                address(this),
                address(this),
                str_ids,
                amounts,
                encodeIndirectReq(i_msg)
            )
        {} catch Error(string memory reason) {
            return (false, reason);
        } catch (
            bytes memory /*lowLevelData*/
        ) {
            return (false, "unknown");
        }

        return (true, "");
    }

    function removeAssetAndTokenId(string memory _chain_domain, bytes32 _asset_address, string[] memory _token_ids) public onlyRole(ADMIN_ROLE) {
        super._removeAssetAndTokenId(_chain_domain, _asset_address, _token_ids);
    }

    function addRegisteredAsset(string memory _chain_domain, bytes32 _asset_address, string[] memory _token_ids, bytes32 _src_from, bytes32 _dest_holder) public onlyRole(ADMIN_ROLE) {
        super._addRegisteredAsset(_chain_domain, _asset_address, _token_ids, _src_from, _dest_holder);
    }
        
}
