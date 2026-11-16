#!/usr/bin/env python3
"""Redeploy and bind Dioxide system contracts without embedding credentials.

The script is intended to run on the Relayer host. It reads the existing
root-only BBC JSON configuration, deploys the repository's current GCL sources
into the existing dapp, rebinds AM/SDP/Monitor, and emits a non-secret JSON
receipt. It deliberately leaves Relayer metadata and anchor cursors untouched;
those are updated only after this receipt has been verified.
"""

from __future__ import annotations

import argparse
import json
import os
import time
from collections import OrderedDict
from pathlib import Path
from typing import Any, Callable

from dioxide_python_sdk.client.account import (
    DioxAccount,
    DioxAddress,
    DioxAddressType,
)
from dioxide_python_sdk.client.contract import Scope
from dioxide_python_sdk.client.dioxclient import DioxClient
from dioxide_tx_coordinator import CoordinatedDioxClient


def as_dict(value: Any) -> dict[str, Any]:
    if value is None:
        return {}
    if isinstance(value, dict):
        return value
    if hasattr(value, "to_dict"):
        return value.to_dict()
    return dict(value)


def contract_info(client: DioxClient, dapp: str, name: str) -> dict[str, Any]:
    return as_dict(client.get_contract_info(dapp, name))


def contract_state(client: DioxClient, dapp: str, name: str) -> dict[str, Any]:
    raw = as_dict(client.get_contract_state(dapp, name, Scope.Global, None))
    return as_dict(raw.get("State", raw))


def wait_for(label: str, predicate: Callable[[], bool], timeout: int = 180) -> None:
    deadline = time.monotonic() + timeout
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            if predicate():
                return
        except Exception as error:  # state relays can be temporarily unavailable
            last_error = error
        time.sleep(2)
    suffix = f": {last_error}" if last_error else ""
    raise TimeoutError(f"timed out waiting for {label}{suffix}")


def cid_address(cid: int) -> str:
    return f"0x{cid:016X}:contract"


def require_file(root: Path, relative: str) -> Path:
    path = root / relative
    if not path.is_file():
        raise FileNotFoundError(path)
    return path


def build_contracts(
    root: Path, account_address: str, monitored: bool, deploy_app: bool
) -> OrderedDict[str, dict[str, Any] | None]:
    relative_paths = [
        "interfaces/IAuthMessage.gcl",
        "interfaces/IContractUsingSDP.gcl",
        "interfaces/ISDPMessage.gcl",
        "interfaces/ISubProtocol.gcl",
        "lib/am/AMLib.gcl",
        "lib/sdp/SDPLib.gcl",
        "lib/utils/BytesToTypes.gcl",
        "lib/utils/SizeOf.gcl",
        "lib/utils/TLVUtils.gcl",
        "lib/utils/TypesToBytes.gcl",
        "lib/utils/Utils.gcl",
    ]
    if monitored:
        relative_paths[1:1] = [
            "interfaces/IContractUsingMonitor.gcl",
            "interfaces/IMonitor.gcl",
        ]
        relative_paths.insert(6, "lib/monitor/MonitorLib.gcl")

    contracts: OrderedDict[str, dict[str, Any] | None] = OrderedDict()
    for relative in relative_paths:
        contracts[str(require_file(root, relative))] = None
    contracts[str(require_file(root, "AuthMsg.gcl"))] = {
        "_owner": account_address,
        "_relayer": account_address,
    }
    contracts[str(require_file(root, "SDPMsg.gcl"))] = {
        "_owner": account_address
    }
    if monitored:
        contracts[str(require_file(root, "Monitor.gcl"))] = {
            "_owner": account_address
        }
    if deploy_app:
        contracts[str(require_file(root, "AppContract.gcl"))] = {
            "_owner": account_address
        }
    return contracts


def submit(
    client: DioxClient,
    account: DioxAccount,
    function: str,
    args: dict[str, Any],
) -> str:
    tx_hash = client.send_transaction(
        account, function, args, is_sync=False,
        operation_id=client.operation_prefix + ":bind:" + function,
    )
    if not tx_hash:
        raise RuntimeError(f"empty transaction hash for {function}")
    return str(tx_hash)


def transaction_snapshot(client: DioxClient, tx_hash: str) -> dict[str, Any]:
    return as_dict(client.get_transaction(tx_hash))


def transaction_finalized(transaction: dict[str, Any]) -> bool:
    return transaction.get("ConfirmState") in {"TXN_FINALIZED", "TXN_ARCHIVED"} or (
        transaction.get("State") in {"DUS_FINALIZED", "DUS_ARCHIVED"}
    )


def transaction_failed(transaction: dict[str, Any]) -> bool:
    invocation = as_dict(transaction.get("Invocation"))
    return invocation.get("Status") in {
        "IVKRET_FALSE",
        "IVKRET_EXCEPTION_THROWN",
        "IVKRET_CONTRACT_UNAVAILABLE",
    } or transaction.get("ConfirmState") in {
        "TXN_RELAY_INVALIDED",
        "TXN_ABORTED",
        "TXN_EXPIRED",
    } or transaction.get("State") in {
        "DUS_INVALID",
        "DUS_FORKED",
        "DUS_ARCHIVED_UNCLE",
    }


def wait_for_transaction(
    client: DioxClient, tx_hash: str, timeout: int = 300
) -> None:
    if not client.wait_for_transaction_confirmed(tx_hash, timeout * 1000):
        raise TimeoutError(f"transaction/relay tree {tx_hash} did not finalize")
    deadline = time.monotonic() + timeout
    last: dict[str, Any] = {}
    while time.monotonic() < deadline:
        last = transaction_snapshot(client, tx_hash)
        if transaction_failed(last):
            raise RuntimeError(
                f"transaction {tx_hash} failed: "
                f"state={last.get('State')} confirm={last.get('ConfirmState')}"
            )
        if transaction_finalized(last):
            return
        time.sleep(2)
    raise TimeoutError(
        f"transaction {tx_hash} did not finalize: "
        f"state={last.get('State')} confirm={last.get('ConfirmState')}"
    )


def deploy_contracts_async(
    client: DioxClient,
    account: DioxAccount,
    dapp: str,
    contracts: OrderedDict[str, dict[str, Any] | None],
    compile_time: int,
) -> str:
    codes: list[str] = []
    constructor_args: list[str] = []
    for path, constructor in contracts.items():
        codes.append(Path(path).read_text(encoding="utf-8"))
        constructor_args.append(
            "" if constructor is None else json.dumps(constructor)
        )
    dapp_address = DioxAddress(None, DioxAddressType.DAPP)
    if not dapp_address.set_delegatee_from_string(dapp):
        raise ValueError(f"invalid Dioxide dapp name: {dapp}")
    tx_hash = client.send_transaction(
        account, "core.delegation.deploy_contracts", {
            "code": codes,
            "cargs": constructor_args,
            "time": compile_time,
        },
        delegatee=dapp_address.address, is_sync=False,
        operation_id=client.operation_prefix + ":deploy:" + dapp,
    )
    if not tx_hash:
        raise RuntimeError("Dioxide returned an empty deployment transaction hash")
    return str(tx_hash)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--operation-id", required=True, help="Stable deployment operation ID; reuse when resuming.")
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--domain", required=True)
    parser.add_argument("--monitored", action="store_true")
    parser.add_argument(
        "--redeploy-app",
        action="store_true",
        help="deploy the current AppContract and bind it to the new SDP/Monitor",
    )
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--compile-time", type=int, default=90)
    parser.add_argument(
        "--resume-deploy-tx",
        help="resume a previously submitted deployment transaction without resubmitting",
    )
    args = parser.parse_args()

    config = json.loads(args.config.read_text(encoding="utf-8"))
    private_key = config.get("privateKey")
    rpc_url = config.get("rpcUrl")
    ws_rpc = config.get("wsRpc")
    dapp = config.get("dappName")
    if not all((private_key, rpc_url, dapp)):
        raise ValueError("BBC config is missing privateKey, rpcUrl, or dappName")

    account = DioxAccount.from_key(private_key)
    if account is None:
        raise ValueError("invalid Dioxide private key")
    client = CoordinatedDioxClient(DioxClient(rpc_url, ws_rpc) if ws_rpc else DioxClient(rpc_url))
    client.operation_prefix = args.operation_id
    overview = as_dict(client.get_overview())
    before_height = int(overview.get("HeadHeight", 0))

    contract_names = ["AuthMsg", "SDPMsg"]
    if args.monitored:
        contract_names.append("Monitor")
    if args.redeploy_app:
        contract_names.append("AppContract")
    before = {
        name: contract_info(client, dapp, name).get("ContractVersionID")
        for name in contract_names
    }

    contracts = build_contracts(
        args.source_dir, account.address, args.monitored, args.redeploy_app
    )
    deploy_tx_hash = args.resume_deploy_tx or deploy_contracts_async(
        client, account, dapp, contracts, args.compile_time
    )
    print(f"deployment transaction: {deploy_tx_hash}", flush=True)
    wait_for_transaction(client, str(deploy_tx_hash))

    wait_for(
        "new contract versions",
        lambda: all(
            str(contract_info(client, dapp, name).get("ContractVersionID"))
            != str(before.get(name))
            for name in contract_names
        ),
        timeout=300,
    )

    after: dict[str, int] = {}
    for name in contract_names:
        info = contract_info(client, dapp, name)
        cid = int(info.get("ContractVersionID", 0))
        if cid <= 0:
            raise RuntimeError(f"missing ContractVersionID for {dapp}.{name}")
        after[name] = cid

    am_cid = after["AuthMsg"]
    sdp_cid = after["SDPMsg"]
    tx_hashes: dict[str, str] = {"deploy": str(deploy_tx_hash)}

    tx_hashes["sdp_set_am"] = submit(
        client,
        account,
        f"{dapp}.SDPMsg.setAmContract",
        {"_amContractId": am_cid, "_amAddress": cid_address(am_cid)},
    )
    wait_for(
        "SDP AM binding",
        lambda: int(contract_state(client, dapp, "SDPMsg").get("amContractId", 0))
        == am_cid,
    )

    tx_hashes["sdp_set_domain"] = submit(
        client,
        account,
        f"{dapp}.SDPMsg.setLocalDomain",
        {"domain": list(args.domain.encode("utf-8"))},
    )
    wait_for(
        "SDP local domain",
        lambda: contract_state(client, dapp, "SDPMsg").get("localDomain")
        == list(args.domain.encode("utf-8")),
    )

    if args.monitored:
        monitor_cid = after["Monitor"]
        tx_hashes["monitor_set_sdp"] = submit(
            client,
            account,
            f"{dapp}.Monitor.setProtocol",
            {
                "_sdpContractId": sdp_cid,
                "_sdpAddress": cid_address(sdp_cid),
            },
        )
        wait_for(
            "Monitor SDP binding",
            lambda: int(
                contract_state(client, dapp, "Monitor").get("sdpContractId", 0)
            )
            == sdp_cid,
        )
        tx_hashes["sdp_set_monitor"] = submit(
            client,
            account,
            f"{dapp}.SDPMsg.setMonitorContract",
            {
                "_monitorContractId": monitor_cid,
                "_monitorAddress": cid_address(monitor_cid),
            },
        )
        wait_for(
            "SDP Monitor binding",
            lambda: int(
                contract_state(client, dapp, "SDPMsg").get(
                    "monitorContractId", 0
                )
            )
            == monitor_cid,
        )

    tx_hashes["am_set_sdp"] = submit(
        client,
        account,
        f"{dapp}.AuthMsg.setProtocol",
        {
            "protocolID": sdp_cid,
            "protocolAddress": cid_address(sdp_cid),
            "protocolType": 0,
        },
    )
    wait_for(
        "AM SDP binding",
        lambda: str(sdp_cid)
        in json.dumps(contract_state(client, dapp, "AuthMsg"), sort_keys=True),
    )

    app_cid = (
        after["AppContract"]
        if args.redeploy_app
        else int(
            contract_info(client, dapp, "AppContract").get(
                "ContractVersionID", 0
            )
        )
    )
    if app_cid:
        if args.monitored:
            monitor_cid = after["Monitor"]
            tx_hashes["app_set_monitor"] = submit(
                client,
                account,
                f"{dapp}.AppContract.setMonitor",
                {
                    "_monitorContractId": monitor_cid,
                    "_monitorAddress": cid_address(monitor_cid),
                },
            )
            wait_for(
                "App Monitor binding",
                lambda: int(
                    contract_state(client, dapp, "AppContract").get(
                        "monitorContractId", 0
                    )
                )
                == monitor_cid,
            )
        else:
            tx_hashes["app_set_sdp"] = submit(
                client,
                account,
                f"{dapp}.AppContract.setProtocol",
                {
                    "_protocolContractId": sdp_cid,
                    "_protocolAddress": cid_address(sdp_cid),
                },
            )

    after_height = int(as_dict(client.get_overview()).get("HeadHeight", 0))
    receipt = {
        "domain": args.domain,
        "dapp": dapp,
        "monitored": args.monitored,
        "headHeightBefore": before_height,
        "headHeightAfter": after_height,
        "contractsBefore": before,
        "contractsAfter": after,
        "appContract": app_cid or None,
        "transactions": tx_hashes,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    os.chmod(args.output, 0o600)
    print(json.dumps(receipt, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
