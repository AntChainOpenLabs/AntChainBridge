#!/usr/bin/env python3
"""Send a Dioxide AppContract message using a root-only BBC config.

No key material is accepted on the command line or written to the receipt.
The finality check supports both Dioxide state families used by the node.
"""

from __future__ import annotations

import argparse
import json
import time
from pathlib import Path
from typing import Any

from dioxide_python_sdk.client.account import DioxAccount
from dioxide_python_sdk.client.dioxclient import DioxClient
from dioxide_tx_coordinator import CoordinatedDioxClient


def as_dict(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    if hasattr(value, "to_dict"):
        return value.to_dict()
    return dict(value)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--operation-id", required=True, help="Stable ID for this send; reuse only when resuming it.")
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--dapp", required=True)
    parser.add_argument("--app-contract", default="AppContract")
    parser.add_argument("--target-domain", required=True)
    parser.add_argument("--target-identity", required=True)
    parser.add_argument("--message", required=True)
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args()

    identity = args.target_identity.removeprefix("0x")
    if len(identity) != 64:
        raise ValueError("target identity must contain exactly 32 bytes of hex")
    receiver = list(bytes.fromhex(identity))

    config = json.loads(args.config.read_text(encoding="utf-8"))
    account = DioxAccount.from_key(config["privateKey"])
    if account is None:
        raise ValueError("invalid Dioxide key in BBC config")
    client = CoordinatedDioxClient(DioxClient(config["rpcUrl"], config.get("wsRpc")))
    tx_hash = client.send_transaction(
        account,
        f"{args.dapp}.{args.app_contract}.sendUnorderedMessage",
        {
            "receiverDomain": list(args.target_domain.encode("utf-8")),
            "receiver": receiver,
            "message": list(args.message.encode("utf-8")),
        },
        is_sync=False,
        operation_id=args.operation_id,
    )
    if not tx_hash:
        raise RuntimeError("Dioxide returned an empty transaction hash")
    print(json.dumps({"txHash": str(tx_hash), "submitted": True}), flush=True)
    if not client.wait_for_transaction_confirmed(str(tx_hash), args.timeout * 1000):
        raise TimeoutError("Dioxide transaction/relay tree did not finalize")

    deadline = time.monotonic() + args.timeout
    last: dict[str, Any] = {}
    while time.monotonic() < deadline:
        last = as_dict(client.get_transaction(str(tx_hash)))
        state = last.get("State")
        confirm = last.get("ConfirmState")
        if state in {"DUS_INVALID", "DUS_FORKED", "DUS_ARCHIVED_UNCLE"} or confirm in {
            "TXN_RELAY_INVALIDED",
            "TXN_ABORTED",
            "TXN_EXPIRED",
        }:
            raise RuntimeError(
                f"Dioxide transaction failed: state={state}, confirm={confirm}"
            )
        if state in {"DUS_FINALIZED", "DUS_ARCHIVED"} or confirm in {
            "TXN_FINALIZED",
            "TXN_ARCHIVED",
        }:
            print(
                json.dumps(
                    {
                        "txHash": str(tx_hash),
                        "state": state,
                        "confirmState": confirm,
                        "height": last.get("Height"),
                    }
                )
            )
            return
        time.sleep(2)
    raise TimeoutError(
        f"Dioxide transaction did not finalize: "
        f"state={last.get('State')}, confirm={last.get('ConfirmState')}"
    )


if __name__ == "__main__":
    main()
