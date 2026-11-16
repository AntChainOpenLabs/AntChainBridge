// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.12;

import "./TokenBridge.sol";
import "../erc1155/@openzeppelin/contracts/token/ERC1155/extensions/IERC1155Burnable.sol";

contract TokenBridgeWithIndirectReq is TokenBridge {
    using SafeMath for uint256;

    uint32 public constant REQ_TYPE_DIRECT = 0;
    uint32 public constant REQ_TYPE_INDIRECT = 1;

    struct ForwardInfoRecord {
        string forward_domain;
        bool exist;
    }

    struct BackwardInfoRecord {
        string backward_domain;
        bool exist;
    }

    struct CrossChainMsgForDirectReq {
        bytes32 src_contract;
        bytes32 dest_contract;
        CrossChainAssetInfo asset_info;
        uint8 status;
        string err_msg;
        bytes payload;
    }

    struct CrossChainAssetInfo {
        bytes32 from;
        uint256[] ids;
        uint256[] amounts;
        bytes32 holder;
    }

    struct CrossChainMsgForIndirectReq {
        string src_domain;
        string dest_domain;
        bytes32 src_asset;
        bytes32 dest_asset;
        bytes32 src_from;
        bytes32 dest_holder;
    }

    event ForwardRequest(
        string indexed _forward_domain,
        bytes32 indexed _forward_tb,
        bytes32 indexed _forward_asset,
        string _src_domain,
        bytes32 _src_asset,
        string _dest_domain,
        bytes32 _dest_asset,
        uint256[] _ids,
        uint256[] _amounts,
        bytes32 _holder
    );

    event BackwardRequestWithSuccess(
        string indexed _backward_domain,
        bytes32 indexed _backward_tb,
        string _src_domain,
        bytes32 _src_tb,
        bytes32 _src_asset,
        string _dest_domain,
        bytes32 _dest_tb,
        bytes32 _dest_asset,
        uint256[] _ids,
        uint256[] _amounts,
        bytes32 _holder
    );

    event BackwardRequestWithError(
        string indexed _backward_domain,
        bytes32 indexed _backward_tb,
        string _last_sending_domain, // the last domain send the req successfully
        string _src_domain,
        bytes32 _src_asset,
        string _dest_domain,
        bytes32 _dest_asset,
        uint256[] _ids,
        uint256[] _amounts,
        bytes32 _holder
    );

    event CrossChainLastSendingDomain(string _last_sending_domain);

    event CrossChainErrMsg(string _err_msg);

    event BurnToRevertFail(
        string indexed _src_domain,
        bytes32 indexed _src_asset,
        string indexed _dest_domain,
        bytes32 _mapping_asset,
        uint256[] _ids,
        uint256[] _amounts
    );

    event CrossChainAssetReceived(
        string _src_domain, // 发送跨链请求的链域名
        bytes32 _src_asset, // 原生资产合约地址
        address _dest_asset, // 目标链资产合约地址
        uint256[] _ids, // 资产token id
        uint256[] _amounts, // 资产数额
        bytes32 _src_from, // 发送资产的账户地址（发送链）
        address _dest_holder // 接收资产的账户地址
    );

    modifier onlySelf() {
        require(
            msg.sender == address(this), 
            "only self"
        );
        _;
    }

    // `indirect_forward_route_table` records the forwarding router of the (src_domain, src_asset, dest_domain)
    mapping(string => mapping(bytes32 => mapping(string => ForwardInfoRecord)))
        public indirect_forward_route_table;

    // `indirect_backward_route_table` records the backwarding router of the (src_domain, src_asset, dest_domain)
    mapping(string => mapping(bytes32 => mapping(string => BackwardInfoRecord)))
        public indirect_backward_route_table;

    constructor(address _sdp_msg_address)
        public
        TokenBridge(_sdp_msg_address)
    {}

    function registerIndirectRouter(
        string memory src_domain,
        bytes32 src_asset,
        string memory dest_domain,
        string memory forward_domain,
        string memory backward_domain
    ) public onlyRole(ADMIN_ROLE) {
        if (bytes(forward_domain).length > 0) {
            ForwardInfoRecord storage f_record = indirect_forward_route_table[
                src_domain
            ][src_asset][dest_domain];
            f_record.forward_domain = forward_domain;
            f_record.exist = true;
        }

        if (bytes(backward_domain).length > 0) {
            BackwardInfoRecord storage b_record = indirect_backward_route_table[
                src_domain
            ][src_asset][dest_domain];
            b_record.backward_domain = backward_domain;
            b_record.exist = true;
        }
    }

    function onERC1155Received(
        address,
        address _from,
        uint256 _id,
        uint256 _amount,
        bytes memory _data
    ) public virtual override lock returns (bytes4) {
        uint32 req_type = abi.decode(_data, (uint32));

        uint256[] memory ids = new uint256[](1);
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

        return this.onERC1155Received.selector;
    }

    function onERC1155BatchReceived(
        address,
        address _from,
        uint256[] memory _ids,
        uint256[] memory _amounts,
        bytes memory _data
    ) public virtual override lock returns (bytes4) {
        if (_data.length == 0) {
            return this.onERC1155BatchReceived.selector;
        }

        uint32 req_type = abi.decode(_data, (uint32));

        if (req_type == REQ_TYPE_INDIRECT) {
            handleIndirectReq(_from, _ids, _amounts, _data);
        } else if (req_type == REQ_TYPE_DIRECT) {
            handleDirectReq(_from, _ids, _amounts, _data);
        } else {
            revert("req type not support");
        }

        return this.onERC1155BatchReceived.selector;
    }

    // cross chain contract callback
    function recvUnorderedMessage(
        string memory _from_domain,
        bytes32 _sender,
        bytes memory _message
    ) public virtual override onlySdpMsg {
        CrossChainMsgForDirectReq
            memory cc_msg = decodeCrossChainMsgForDirectReq(_message);

        if (cc_msg.status == uint8(CrossChainStatus.SUCCESS)) {
            if (cc_msg.asset_info.holder == bytes32(0)) {
                handleSuccessCrossChainReceiptForIndirectReq(
                    _from_domain,
                    _sender,
                    cc_msg,
                    cc_msg.payload,
                    _message
                );
            } else {
                emit CrossChain(
                    _from_domain,
                    cc_msg.src_contract,
                    cc_msg.dest_contract,
                    cc_msg.asset_info.ids,
                    cc_msg.asset_info.amounts,
                    cc_msg.asset_info.holder,
                    uint8(CrossChainStatus.SUCCESS)
                );
            }
        } else if (cc_msg.status == uint8(CrossChainStatus.ERROR)) {
            if (cc_msg.asset_info.holder == bytes32(0)) {
                string memory last_sending_domain;
                (
                    cc_msg,
                    last_sending_domain
                ) = decodeCrossChainMsgForDirectReqWithExt(_message);
                handleErrorCrossChainReceiptForIndirectReq(
                    _from_domain,
                    _sender,
                    cc_msg,
                    cc_msg.payload,
                    last_sending_domain
                );
            } else {
                _revertByUnlock(
                    getAddressFromBytes32(cc_msg.src_contract),
                    getAddressFromBytes32(cc_msg.asset_info.from),
                    cc_msg.asset_info.ids,
                    cc_msg.asset_info.amounts
                );

                emit CrossChain(
                    _from_domain,
                    cc_msg.src_contract,
                    cc_msg.dest_contract,
                    cc_msg.asset_info.ids,
                    cc_msg.asset_info.amounts,
                    cc_msg.asset_info.holder,
                    uint8(CrossChainStatus.ERROR)
                );
                emit CrossChainErrMsg(cc_msg.err_msg);
            }
        } else {
            if (cc_msg.asset_info.holder == bytes32(0)) {
                handleCrossChainMsgForIndirectReq(
                    _from_domain,
                    _sender,
                    cc_msg,
                    cc_msg.payload
                );
            } else {
                handleCrossChainMsgForDirectReq(_from_domain, _sender, cc_msg);
            }
        }
    }

    function handleIndirectReq(
        address _from,
        uint256[] memory _ids,
        uint256[] memory _amounts,
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

        for (uint256 i = 0; i < _ids.length; ++i) {
            asset_lock_record[msg.sender][_ids[i]] = asset_lock_record[
                msg.sender
            ][_ids[i]].add(_amounts[i]);
        }

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
        uint256[] memory _ids,
        uint256[] memory _amounts,
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

        for (uint256 i = 0; i < _ids.length; ++i) {
            asset_lock_record[msg.sender][_ids[i]] = asset_lock_record[
                msg.sender
            ][_ids[i]].add(_amounts[i]);
        }

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

    function checkIfForwardExist(
        string memory src_domain,
        bytes32 src_asset,
        string memory dest_domain
    ) internal view returns (bool, ForwardInfoRecord memory) {
        ForwardInfoRecord memory f_record = indirect_forward_route_table[
            src_domain
        ][src_asset][dest_domain];
        return (f_record.exist, f_record);
    }

    function forwardCrossChainData(
        ForwardInfoRecord memory f_record,
        bytes memory _msg
    ) internal {
        require(
            bytes(f_record.forward_domain).length > 0,
            "empty forward domain"
        );
        require(
            route_table[msg.sender][f_record.forward_domain] != bytes32(0),
            "empty forward asset"
        );
        require(
            token_bridges[f_record.forward_domain] != bytes32(0),
            "empty forward token bridge"
        );

        InterContractMessageInterface(sdp_msg_address).sendUnorderedMessage(
            f_record.forward_domain,
            token_bridges[f_record.forward_domain],
            _msg
        );
    }

    function encodeForwardRequestPayload(
        uint256[] memory ids,
        uint256[] memory amounts,
        string memory forward_domain,
        CrossChainMsgForIndirectReq memory req
    ) internal view returns (bytes memory) {
        CrossChainMsgForIndirectReq memory i_msg = CrossChainMsgForIndirectReq({
            src_domain: req.src_domain,
            dest_domain: req.dest_domain,
            src_asset: req.src_asset,
            dest_asset: req.dest_asset,
            src_from: req.src_from,
            dest_holder: req.dest_holder
        });

        CrossChainAssetInfo memory asset_info = CrossChainAssetInfo({
            from: bytes32(0),
            ids: ids,
            amounts: amounts,
            holder: bytes32(0)
        });
        CrossChainMsgForDirectReq memory cc_msg = CrossChainMsgForDirectReq({
            src_contract: TypesToBytes.addressToBytes32(msg.sender),
            dest_contract: route_table[msg.sender][forward_domain],
            asset_info: asset_info,
            status: uint8(CrossChainStatus.START),
            err_msg: "",
            payload: encodeCrossChainMsgForIndirectReq(i_msg)
        });

        return encodeCrossChainMsgForDirectReq(cc_msg);
    }

    function handleCrossChainMsgForIndirectReq(
        string memory _from_domain,
        bytes32 _sender,
        CrossChainMsgForDirectReq memory base_msg,
        bytes memory _raw_i_msg
    ) internal virtual {
        CrossChainMsgForIndirectReq
            memory i_msg = decodeCrossChainMsgForIndirectReq(_raw_i_msg);

        bytes32 local_domain_hash = getLocalDomainHash();
        bool res = false;
        string memory err_msg;

        if (local_domain_hash == keccak256(bytes(i_msg.dest_domain))) {
            (res, err_msg) = processIfLocalIndirectMsg(_from_domain, _sender, base_msg, i_msg);
            if (res) {
                backwardTheSuccess(_from_domain, base_msg, i_msg);
                return;
            }

        } else {
            (res, err_msg) = processIfRelayIndirectMsg(_from_domain, _sender, base_msg, i_msg);
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
    ) internal virtual returns(bool res, string memory err_msg) {
        if (token_bridges[_from_domain] != _sender) {
            return (false, "wrong tb");
        }

        address dest_asset = getAddressFromBytes32(i_msg.dest_asset);
        if (route_table[dest_asset][i_msg.src_domain] != i_msg.src_asset) {
            return (false, "wrong route info");
        }

        address dest_holder = getAddressFromBytes32(i_msg.dest_holder);
        (res, err_msg) = _mintOrUnlockWithTryCatch(
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
    ) internal virtual returns(bool res, string memory err_msg) {
        if (token_bridges[_from_domain] != _sender) {
            return (false, "wrong tb");
        }

        if (token_bridges[i_msg.dest_domain] != bytes32(0)) {
            (res, err_msg) = _mintOrUnlockWithTryCatch(
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

    function handleSuccessCrossChainReceiptForIndirectReq(
        string memory _from_domain,
        bytes32 _sender,
        CrossChainMsgForDirectReq memory base_msg,
        bytes memory _raw_i_msg,
        bytes memory _message
    ) internal {
        require(token_bridges[_from_domain] == _sender, "UNKNOW_TOKEN_BRIDGE");

        CrossChainMsgForIndirectReq
            memory i_msg = decodeCrossChainMsgForIndirectReq(_raw_i_msg);

        if (getLocalDomainHash() == keccak256(bytes(i_msg.src_domain))) {
            emit CrossChain(
                i_msg.dest_domain,
                i_msg.src_asset,
                i_msg.dest_asset,
                base_msg.asset_info.ids,
                base_msg.asset_info.amounts,
                i_msg.dest_holder,
                uint8(CrossChainStatus.SUCCESS)
            );
        } else {
            (
                bool if_exist,
                BackwardInfoRecord memory b_record
            ) = checkIfBackwardExist(
                    i_msg.src_domain,
                    i_msg.src_asset,
                    i_msg.dest_domain
                );
            require(if_exist, "no backward record exist");
            require(
                token_bridges[b_record.backward_domain] != bytes32(0),
                "no tb for backward"
            );

            InterContractMessageInterface(sdp_msg_address).sendUnorderedMessage(
                b_record.backward_domain,
                token_bridges[b_record.backward_domain],
                _message
            );

            emit BackwardRequestWithSuccess(
                b_record.backward_domain,
                token_bridges[b_record.backward_domain],
                i_msg.src_domain,
                token_bridges[i_msg.src_domain],
                i_msg.src_asset,
                i_msg.dest_domain,
                token_bridges[i_msg.dest_domain],
                i_msg.dest_asset,
                base_msg.asset_info.ids,
                base_msg.asset_info.amounts,
                i_msg.dest_holder
            );
        }
    }

    function handleErrorCrossChainReceiptForIndirectReq(
        string memory _from_domain,
        bytes32 _sender,
        CrossChainMsgForDirectReq memory base_msg,
        bytes memory _raw_i_msg,
        string memory _last_sending_domain
    ) internal {
        require(token_bridges[_from_domain] == _sender, "UNKNOWN_TOKEN_BRIDGE");

        CrossChainMsgForIndirectReq
            memory i_msg = decodeCrossChainMsgForIndirectReq(_raw_i_msg);

        if (getLocalDomainHash() == keccak256(bytes(i_msg.src_domain))) {
            _revertByUnlock(
                getAddressFromBytes32(i_msg.src_asset),
                getAddressFromBytes32(i_msg.src_from),
                base_msg.asset_info.ids,
                base_msg.asset_info.amounts
            );

            emit CrossChain(
                i_msg.dest_domain,
                i_msg.src_asset,
                i_msg.dest_asset,
                base_msg.asset_info.ids,
                base_msg.asset_info.amounts,
                i_msg.dest_holder,
                uint8(CrossChainStatus.ERROR)
            );
            emit CrossChainLastSendingDomain(_last_sending_domain);
            emit CrossChainErrMsg(base_msg.err_msg);
        } else {
            _revertByBurn(i_msg, base_msg);

            (
                bool if_exist,
                BackwardInfoRecord memory b_record
            ) = checkIfBackwardExist(
                    i_msg.src_domain,
                    i_msg.src_asset,
                    i_msg.dest_domain
                );
            require(if_exist, "no backward record exist");
            require(
                token_bridges[b_record.backward_domain] != bytes32(0),
                "no tb for backward"
            );
            require(
                route_table[getAddressFromBytes32(base_msg.src_contract)][
                    b_record.backward_domain
                ] != bytes32(0),
                "no backward asset route"
            );

            base_msg.dest_contract = base_msg.src_contract;
            base_msg.src_contract = route_table[
                getAddressFromBytes32(base_msg.src_contract)
            ][b_record.backward_domain];
            base_msg.payload = encodeCrossChainMsgForIndirectReq(i_msg);
            InterContractMessageInterface(sdp_msg_address).sendUnorderedMessage(
                b_record.backward_domain,
                token_bridges[b_record.backward_domain],
                encodeCrossChainMsgForDirectReqWithExt(
                    base_msg,
                    _last_sending_domain
                )
            );

            emit BackwardRequestWithError(
                b_record.backward_domain,
                token_bridges[b_record.backward_domain],
                _last_sending_domain,
                i_msg.src_domain,
                i_msg.src_asset,
                i_msg.dest_domain,
                i_msg.dest_asset,
                base_msg.asset_info.ids,
                base_msg.asset_info.amounts,
                i_msg.dest_holder
            );
        }
    }

    function _revertByUnlock(
        address _asset,
        address _to,
        uint256[] memory _ids,
        uint256[] memory _amounts
    ) internal virtual {
        for (uint256 i = 0; i < _ids.length; i++) {
            require(asset_lock_record[_asset][_ids[i]] >= _amounts[i]);
            asset_lock_record[_asset][_ids[i]] -= _amounts[i];
        }

        IERC1155(_asset).safeBatchTransferFrom(
            address(this),
            _to,
            _ids,
            _amounts,
            ""
        );
    }

    function _revertByBurn(
        CrossChainMsgForIndirectReq memory i_msg,
        CrossChainMsgForDirectReq memory base_msg
    ) internal virtual {
        address asset_to_burn = getAddressFromBytes32(base_msg.src_contract);

        for (uint256 i = 0; i < base_msg.asset_info.ids.length; i++) {
            require(
                asset_lock_record[asset_to_burn][base_msg.asset_info.ids[i]] >=
                    base_msg.asset_info.amounts[i]
            );
            asset_lock_record[asset_to_burn][
                base_msg.asset_info.ids[i]
            ] -= base_msg.asset_info.amounts[i];
        }

        bool res = false;
        try
            IERC1155Burnable(asset_to_burn).burnBatch(
                address(this),
                base_msg.asset_info.ids,
                base_msg.asset_info.amounts
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

    function checkIfBackwardExist(
        string memory src_domain,
        bytes32 src_asset,
        string memory dest_domain
    ) internal view returns (bool, BackwardInfoRecord memory) {
        BackwardInfoRecord memory b_record = indirect_backward_route_table[
            src_domain
        ][src_asset][dest_domain];
        return (b_record.exist, b_record);
    }

    function handleCrossChainMsgForDirectReq(
        string memory _from_domain,
        bytes32 _sender,
        CrossChainMsgForDirectReq memory cc_msg
    ) internal virtual {
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

    function backwardTheFailForDirectReq(
        string memory _from_domain,
        bytes32 _sender,
        string memory err_msg,
        CrossChainMsgForDirectReq memory cc_msg
    ) internal {
        cc_msg.status = uint8(CrossChainStatus.ERROR);
        cc_msg.err_msg = err_msg;
        InterContractMessageInterface(sdp_msg_address).sendUnorderedMessage(
            _from_domain,
            _sender,
            encodeCrossChainMsgForDirectReq(cc_msg)
        );
    }

    function backwardTheSuccess(
        string memory _from_domain,
        CrossChainMsgForDirectReq memory base_msg,
        CrossChainMsgForIndirectReq memory i_msg
    ) internal {
        base_msg.status = uint8(CrossChainStatus.SUCCESS);
        base_msg.payload = encodeCrossChainMsgForIndirectReq(i_msg);
        InterContractMessageInterface(sdp_msg_address).sendUnorderedMessage(
            _from_domain,
            token_bridges[_from_domain],
            encodeCrossChainMsgForDirectReq(base_msg)
        );
    }

    function _transferWithTryCatch(
        CrossChainMsgForDirectReq memory base_msg,
        CrossChainMsgForIndirectReq memory i_msg
    ) internal virtual returns (bool, string memory) {
        try
            IERC1155(getAddressFromBytes32(base_msg.dest_contract))
                .safeBatchTransferFrom(
                    address(this),
                    address(this),
                    base_msg.asset_info.ids,
                    base_msg.asset_info.amounts,
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

    function _mintOrUnlockWithTryCatch(
        uint256[] memory ids,
        uint256[] memory amounts,
        address dest_contract,
        address holder
    ) internal virtual returns (bool, string memory) {
        // mint or unlock asset
        uint256[] memory mint_ids = new uint256[](ids.length);
        uint256[] memory mint_amounts = new uint256[](ids.length);
        uint256[] memory transfer_ids = new uint256[](ids.length);
        uint256[] memory transfer_amounts = new uint256[](ids.length);

        bool mint = false;
        bool transfer = false;
        for (uint256 i = 0; i < ids.length; i++) {
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

        try
            TokenBridgeWithIndirectReq(address(this)).mintOrUnlockInternal(
                dest_contract,
                holder,
                mint_ids,
                mint_amounts,
                transfer_ids,
                transfer_amounts,
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
        address holder, 
        uint256[] memory mint_ids,
        uint256[] memory mint_amounts,
        uint256[] memory transfer_ids,
        uint256[] memory transfer_amounts,
        bool mintOrNot,
        bool transferOrNot
    ) external onlySelf {

        if (mintOrNot) {
            ERC1155CrossChainMapping(dest_contract).mintBatchByTB(
                holder,
                mint_ids,
                mint_amounts
            );
        }

        if (transferOrNot) {
            IERC1155(dest_contract).safeBatchTransferFrom(
                msg.sender,
                holder,
                transfer_ids,
                transfer_amounts,
                ""
            );
        }
    }

    function backwardTheFail(
        string memory _from_domain,
        bytes32 _sender,
        CrossChainMsgForDirectReq memory base_msg,
        CrossChainMsgForIndirectReq memory i_msg,
        string memory err_msg
    ) internal {
        base_msg.status = uint8(CrossChainStatus.ERROR);
        base_msg.err_msg = err_msg;
        base_msg.payload = encodeCrossChainMsgForIndirectReq(i_msg);
        InterContractMessageInterface(sdp_msg_address).sendUnorderedMessage(
            _from_domain,
            _sender,
            encodeCrossChainMsgForDirectReqWithExt(base_msg, _from_domain)
        );
    }

    function getLocalDomainHash() internal returns (bytes32) {
        bytes memory returnData;
        bool success;
        (success, returnData) = sdp_msg_address.call(
            abi.encodePacked(
                bytes4(keccak256(abi.encodePacked("getLocalDomain", "()"))),
                abi.encode()
            )
        );
        require(success, "failed to get local domain hash");

        bytes32 res;
        assembly {
            res := mload(add(returnData, 32))
        }

        return res;
    }

    // serialization and deserialization

    function encodeIndirectReq(CrossChainMsgForIndirectReq memory i_msg)
        internal
        pure
        returns (bytes memory)
    {
        return
            abi.encode(
                REQ_TYPE_INDIRECT,
                i_msg.src_domain,
                i_msg.dest_domain,
                i_msg.src_asset,
                i_msg.dest_asset,
                i_msg.src_from,
                i_msg.dest_holder
            );
    }

    function encodeCrossChainMsgForIndirectReq(
        CrossChainMsgForIndirectReq memory i_msg
    ) internal pure returns (bytes memory) {
        return
            abi.encode(
                i_msg.src_domain,
                i_msg.dest_domain,
                i_msg.src_asset,
                i_msg.dest_asset,
                i_msg.src_from,
                i_msg.dest_holder
            );
    }

    function decodeCrossChainMsgForIndirectReq(bytes memory raw_i_msg)
        internal
        pure
        returns (CrossChainMsgForIndirectReq memory)
    {
        CrossChainMsgForIndirectReq memory i_msg;

        (
            i_msg.src_domain,
            i_msg.dest_domain,
            i_msg.src_asset,
            i_msg.dest_asset,
            i_msg.src_from,
            i_msg.dest_holder
        ) = abi.decode(
            raw_i_msg,
            (string, string, bytes32, bytes32, bytes32, bytes32)
        );

        return i_msg;
    }

    function encodeCrossChainMsgForDirectReq(
        CrossChainMsgForDirectReq memory base_msg
    ) internal pure returns (bytes memory) {
        return
            abi.encode(
                base_msg.src_contract,
                base_msg.dest_contract,
                abi.encode(
                    base_msg.asset_info.from,
                    base_msg.asset_info.ids,
                    base_msg.asset_info.amounts,
                    base_msg.asset_info.holder
                ),
                base_msg.status,
                base_msg.err_msg,
                base_msg.payload
            );
    }

    function decodeCrossChainMsgForDirectReq(bytes memory _message)
        internal
        pure
        returns (CrossChainMsgForDirectReq memory)
    {
        CrossChainMsgForDirectReq memory base_msg;
        bytes memory raw_assert_info;

        (
            base_msg.src_contract,
            base_msg.dest_contract,
            raw_assert_info,
            base_msg.status,
            base_msg.err_msg,
            base_msg.payload
        ) = abi.decode(
            _message,
            (bytes32, bytes32, bytes, uint8, string, bytes)
        );

        CrossChainAssetInfo memory asset_info;
        (
            asset_info.from,
            asset_info.ids,
            asset_info.amounts,
            asset_info.holder
        ) = abi.decode(
            raw_assert_info,
            (bytes32, uint256[], uint256[], bytes32)
        );

        base_msg.asset_info = asset_info;

        return base_msg;
    }

    function encodeCrossChainMsgForDirectReqWithExt(
        CrossChainMsgForDirectReq memory base_msg,
        string memory ext
    ) internal pure returns (bytes memory) {
        return
            abi.encode(
                base_msg.src_contract,
                base_msg.dest_contract,
                abi.encode(
                    base_msg.asset_info.from,
                    base_msg.asset_info.ids,
                    base_msg.asset_info.amounts,
                    base_msg.asset_info.holder
                ),
                base_msg.status,
                base_msg.err_msg,
                base_msg.payload,
                ext
            );
    }

    function decodeCrossChainMsgForDirectReqWithExt(bytes memory _message)
        internal
        pure
        returns (CrossChainMsgForDirectReq memory, string memory)
    {
        CrossChainMsgForDirectReq memory base_msg;
        bytes memory raw_assert_info;

        string memory ext;

        (
            base_msg.src_contract,
            base_msg.dest_contract,
            raw_assert_info,
            base_msg.status,
            base_msg.err_msg,
            base_msg.payload,
            ext
        ) = abi.decode(
            _message,
            (bytes32, bytes32, bytes, uint8, string, bytes, string)
        );

        CrossChainAssetInfo memory asset_info;
        (
            asset_info.from,
            asset_info.ids,
            asset_info.amounts,
            asset_info.holder
        ) = abi.decode(
            raw_assert_info,
            (bytes32, uint256[], uint256[], bytes32)
        );

        base_msg.asset_info = asset_info;

        return (base_msg, ext);
    }
}
