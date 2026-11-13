#!/usr/bin/env python3
"""Redeploy and bind one Dioxide test AppContract without touching system CIDs."""

from __future__ import annotations

import argparse
import json
import os
from collections import OrderedDict
from pathlib import Path

from dioxide_python_sdk.client.account import DioxAccount
from dioxide_python_sdk.client.dioxclient import DioxClient, DioxError
from dioxide_tx_coordinator import CoordinatedDioxClient

from redeploy_dioxide_system_contracts import (
    cid_address,
    contract_info,
    contract_state,
    deploy_contracts_async,
    submit,
    wait_for,
    wait_for_transaction,
)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--operation-id", required=True, help="Stable deployment operation ID; reuse when resuming.")
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--contract-name", default="AppContract")
    parser.add_argument(
        "--resume-deploy-tx",
        help="resume a previously submitted deployment instead of submitting again",
    )
    parser.add_argument("--monitored", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--compile-time", type=int, default=30)
    args = parser.parse_args()

    config = json.loads(args.config.read_text(encoding="utf-8"))
    account = DioxAccount.from_key(config["privateKey"])
    if account is None:
        raise ValueError("invalid Dioxide private key")
    client = CoordinatedDioxClient(DioxClient(config["rpcUrl"], config.get("wsRpc")))
    client.operation_prefix = args.operation_id
    dapp = str(config["dappName"])
    contract_name = str(args.contract_name)

    try:
        before = int(
            contract_info(client, dapp, contract_name).get(
                "ContractVersionID", 0
            )
        )
    except DioxError as error:
        if error.code != 10007:
            raise
        before = 0
    if args.resume_deploy_tx:
        deploy_tx = str(args.resume_deploy_tx)
        print(
            json.dumps(
                {"deployTransaction": deploy_tx, "resumed": True}
            ),
            flush=True,
        )
    else:
        contracts = OrderedDict(
            [(str(args.source), {"_owner": account.address})]
        )
        deploy_tx = deploy_contracts_async(
            client, account, dapp, contracts, args.compile_time
        )
        print(
            json.dumps(
                {"deployTransaction": deploy_tx, "resumed": False}
            ),
            flush=True,
        )
    wait_for_transaction(client, deploy_tx)
    wait_for(
        "new AppContract version",
        lambda: int(
            contract_info(client, dapp, contract_name).get(
                "ContractVersionID", 0
            )
        )
        != before,
        timeout=300,
    )
    after = int(
        contract_info(client, dapp, contract_name).get("ContractVersionID", 0)
    )
    if after <= 0:
        raise RuntimeError("new AppContract CID is missing")

    if args.monitored:
        system_name = "Monitor"
        cid_field = "monitorContractId"
        function = f"{dapp}.{contract_name}.setMonitor"
        bind_args = {
            "_monitorContractId": int(
                contract_info(client, dapp, system_name)["ContractVersionID"]
            ),
        }
        bind_args["_monitorAddress"] = cid_address(
            bind_args["_monitorContractId"]
        )
    else:
        system_name = "SDPMsg"
        cid_field = "sdpContractId"
        function = f"{dapp}.{contract_name}.setProtocol"
        bind_args = {
            "_protocolContractId": int(
                contract_info(client, dapp, system_name)["ContractVersionID"]
            ),
        }
        bind_args["_protocolAddress"] = cid_address(
            bind_args["_protocolContractId"]
        )

    expected_system_cid = next(
        value for key, value in bind_args.items() if key.endswith("ContractId")
    )
    bind_tx = submit(client, account, function, bind_args)
    wait_for_transaction(client, bind_tx)
    wait_for(
        "AppContract system binding",
        lambda: int(
            contract_state(client, dapp, contract_name).get(cid_field, 0)
        )
        == expected_system_cid,
    )

    receipt = {
        "dapp": dapp,
        "contractName": contract_name,
        "monitored": args.monitored,
        "appContractBefore": before,
        "appContractAfter": after,
        "boundSystem": system_name,
        "boundSystemCid": expected_system_cid,
        "deployTransaction": deploy_tx,
        "bindTransaction": bind_tx,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(receipt, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    os.chmod(args.output, 0o600)
    print(json.dumps(receipt, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
