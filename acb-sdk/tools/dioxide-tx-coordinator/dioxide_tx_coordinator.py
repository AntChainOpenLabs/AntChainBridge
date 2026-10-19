"""Dioxide coordination protocol shared with JdbcTransactionCoordinator (no private keys stored)."""
from __future__ import annotations

import base64
import hashlib
import json
import os
import time
import uuid
from contextlib import contextmanager
from pathlib import Path
from urllib.parse import urlsplit

import pymysql

MAX_ISN = 0xFFFFFFFF


def config_file(filename=None):
    filename = filename or os.environ.get("DIOXIDE_TX_COORDINATOR_CONFIG") or "/etc/antchain-bridge/dioxide-tx.properties"
    if not filename:
        raise RuntimeError("DIOXIDE_TX_COORDINATOR_CONFIG is required; unsafe allocation is disabled")
    result = {}
    for line in Path(filename).read_text().splitlines():
        if line.strip() and not line.lstrip().startswith(("#", "!")):
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


class Coordinator:
    def __init__(self, config, connect=None):
        self.config = config
        self.network = config["networkId"]
        self.checkpoint = config["checkpointHash"]
        self._validate(self.network, 96)
        self._validate(self.checkpoint, 128)
        if connect is None:
            uri = urlsplit(config["jdbcUrl"].removeprefix("jdbc:"))
            password = Path(config["passwordFile"]).read_text().strip()
            connect = lambda: pymysql.connect(
                host=uri.hostname, port=uri.port or 3306, user=config["user"], password=password,
                database=uri.path.lstrip("/"), charset="utf8mb4", autocommit=True,
                connect_timeout=5, read_timeout=30, write_timeout=15,
            )
        self.connect = connect

    @staticmethod
    def _validate(value, maximum):
        if not isinstance(value, str) or not value or len(value) > maximum or any(not 33 <= ord(c) <= 126 for c in value):
            raise ValueError("invalid transaction coordinator identifier")

    @contextmanager
    def lock(self, kind, account):
        name = hashlib.sha256(f"{kind}|{self.network}|{account}".encode()).hexdigest()
        connection = self.connect()
        try:
            with connection.cursor() as cursor:
                cursor.execute("SELECT GET_LOCK(%s,20)", (name,))
                if cursor.fetchone()[0] != 1:
                    raise TimeoutError("coordinator lock timeout")
            yield connection
        finally:
            try:
                with connection.cursor() as cursor:
                    cursor.execute("SELECT RELEASE_LOCK(%s)", (name,))
            finally:
                connection.close()

    def submit(self, operation_id, account, payload, transport):
        if account.lower().endswith(":ed25519"):
            account = account[:-8].lower()
        self._validate(operation_id, 191)
        self._validate(account, 160)
        fingerprint = hashlib.sha256(payload).hexdigest()
        with self.lock("submission", account) as connection:
            if transport.checkpoint() != self.checkpoint:
                raise RuntimeError("Dioxide network checkpoint changed; submission disabled")
            connection.begin()
            try:
                with connection.cursor() as cursor:
                    cursor.execute(
                        "INSERT INTO bridge_tx_account(network_id,account,checkpoint_hash,next_isn) VALUES(%s,%s,%s,0) "
                        "ON DUPLICATE KEY UPDATE account=VALUES(account)", (self.network, account, self.checkpoint))
                    cursor.execute("SELECT checkpoint_hash,next_isn,observed_isn FROM bridge_tx_account WHERE network_id=%s AND account=%s FOR UPDATE",
                                   (self.network, account))
                    checkpoint, persisted, observed = cursor.fetchone()
                    if checkpoint != self.checkpoint:
                        raise RuntimeError("coordinator network mismatch")
                    cursor.execute(
                        "SELECT account,payload_hash,signed_tx,tx_hash FROM bridge_tx_submission WHERE network_id=%s AND operation_id=%s FOR UPDATE",
                        (self.network, operation_id))
                    previous = cursor.fetchone()
                    if previous:
                        if previous[0] != account or previous[1] != fingerprint:
                            raise RuntimeError("submission identity reused with different account or payload")
                        signed, tx_hash = previous[2:]
                    else:
                        node_isn = transport.current_isn(account)
                        if node_isn < observed:
                            raise RuntimeError("node ISN regressed; reconcile network state before new submission")
                        if not 0 <= node_isn <= MAX_ISN or not 0 <= persisted <= MAX_ISN:
                            raise RuntimeError("ISN exhausted or invalid; refusing wraparound")
                        isn = max(node_isn, persisted)
                        signed = transport.compose_and_sign(isn)
                        if not signed:
                            raise RuntimeError("empty signed transaction")
                        cursor.execute(
                            "INSERT INTO bridge_tx_submission(network_id,operation_id,account,isn,payload_hash,signed_tx,state) "
                            "VALUES(%s,%s,%s,%s,%s,%s,'SIGNED')", (self.network, operation_id, account, isn, fingerprint, signed))
                        cursor.execute("UPDATE bridge_tx_account SET next_isn=%s,observed_isn=%s WHERE network_id=%s AND account=%s",
                                       (isn + 1, node_isn, self.network, account))
                        tx_hash = None
                connection.commit()
            except BaseException:
                connection.rollback()
                raise
            if tx_hash:
                return tx_hash
            try:
                tx_hash = transport.broadcast(signed)
                if not tx_hash:
                    raise RuntimeError("broadcast returned no hash")
                with connection.cursor() as cursor:
                    cursor.execute(
                        "UPDATE bridge_tx_submission SET tx_hash=%s,state='BROADCAST',last_error=NULL WHERE network_id=%s AND operation_id=%s",
                        (tx_hash, self.network, operation_id))
                return tx_hash
            except Exception as error:
                try:
                    with connection.cursor() as cursor:
                        cursor.execute(
                            "UPDATE bridge_tx_submission SET state='UNKNOWN',last_error=%s WHERE network_id=%s AND operation_id=%s AND tx_hash IS NULL",
                            (type(error).__name__, self.network, operation_id))
                except Exception:
                    pass  # SIGNED also preserves the exact recovery bytes.
                raise

    def record_outcome(self, tx_hash, success):
        connection = self.connect()
        try:
            with connection.cursor() as cursor:
                cursor.execute("UPDATE bridge_tx_submission SET state=%s WHERE network_id=%s AND tx_hash=%s",
                               ("FINALIZED" if success else "FAILED", self.network, tx_hash))
        finally:
            connection.close()


class CoordinatedDioxClient:
    """Wrap existing SDK reads while routing all supported writes through the shared journal."""
    def __init__(self, client, config=None):
        self.client = client
        self.coordinator = Coordinator(config or config_file())

    def __getattr__(self, name):
        # Do not silently forward a write that bypasses allocation.
        if name.startswith(("send_", "deploy_", "mint_", "transfer", "create_")):
            raise AttributeError(f"Use an explicitly coordinated operation instead of {name}")
        return getattr(self.client, name)

    def send_transaction(self, user, function, args, tokens=None, isn=None, is_delegatee=False,
                         gas_price=None, gas_limit=None, is_sync=False, timeout=120000,
                         operation_id=None, delegatee=None, ttl=None):
        if isn is not None:
            raise ValueError("ISN is allocated by the shared coordinator, not the caller")
        params = {"function": function, "args": args}
        sender = str(delegatee if delegatee is not None else user.address)
        if delegatee is not None or is_delegatee:
            if ":" not in sender:
                sender += ":dapp"
            params["delegatee"] = sender
        else:
            params["sender"] = sender
        for key, value in [("tokens", tokens), ("gasprice", gas_price), ("gaslimit", gas_limit), ("ttl", ttl)]:
            if value is not None:
                params[key] = value
        operation_id = operation_id or "operation:" + str(uuid.uuid4())
        payload = json.dumps(params, separators=(",", ":"), ensure_ascii=False).encode()
        client, coordinator = self.client, self.coordinator

        class Transport:
            def checkpoint(self):
                return client.make_request("dx.consensus_header", {
                    "query_type": 0, "height": int(coordinator.config["checkpointHeight"])})["Hash"]

            def current_isn(self, account):
                return int(client.make_request("dx.isn", {"address": account})["ISN"])

            def compose_and_sign(self, allocated):
                unsigned = client.make_request("tx.compose", dict(params, isn=allocated))
                raw = base64.b64decode(unsigned["TxData"], validate=True)
                if len(raw) < 12 or int.from_bytes(raw[8:12], "little") != allocated:
                    raise RuntimeError("node did not compose the reserved ISN")
                return user.sign_diox_transaction(raw)

            def broadcast(self, signed):
                return client.make_request("tx.send", {"txdata": base64.b64encode(signed).decode()})["Hash"]

        tx_hash = coordinator.submit(operation_id, sender, payload, Transport())
        if is_sync:
            if not self.wait_for_transaction_confirmed(tx_hash, timeout):
                raise TimeoutError(f"Dioxide synchronous submission timed out: {tx_hash}")
        return tx_hash

    def wait_for_transaction_confirmed(self, tx_hash, timeout=120000):
        deadline = time.monotonic() + timeout / 1000
        while time.monotonic() < deadline:
            queue, seen, pending, resolved = [tx_hash], set(), False, {}
            while queue:
                current = queue.pop()
                if current in seen:
                    continue
                seen.add(current)
                base_hash = current.split(":", 1)[0]
                if base_hash not in resolved:
                    resolved[base_hash] = self.client.make_request("dx.transaction", {"hash": base_hash})
                tx = resolved[base_hash]
                if not isinstance(tx, dict):
                    pending = True
                    continue
                if ":" in current:
                    suffix = current.split(":", 1)[1]
                    members = tx.get("Relays") or []
                    if not suffix.isdecimal() or int(suffix) >= len(members):
                        pending = True
                        continue
                    tx = dict(tx, Invocation=None, Relays=[members[int(suffix)]])
                if tx.get("ConfirmState") in {"TXN_ABORTED", "TXN_EXPIRED", "TXN_RELAY_INVALIDED"} or tx.get("State") in {"DUS_INVALID", "DUS_FORKED", "DUS_ARCHIVED_UNCLE"}:
                    self.coordinator.record_outcome(tx_hash, False)
                    raise RuntimeError(f"Dioxide transaction failed: {current} ({tx.get('ConfirmState')})")
                if tx.get("ConfirmState") not in {"TXN_FINALIZED", "TXN_ARCHIVED"} and tx.get("State") not in {"DUS_FINALIZED", "DUS_ARCHIVED"}:
                    pending = True
                def inspect(value):
                    invocation = value.get("Invocation") or {}
                    status = invocation.get("Status")
                    if status and status != "IVKRET_SUCCESS":
                        self.coordinator.record_outcome(tx_hash, False)
                        raise RuntimeError(f"Dioxide invocation failed: {current} ({status})")
                    queue.extend(invocation.get("Relays") or [])
                    for child in value.get("Relays") or []:
                        if isinstance(child, dict):
                            inspect(child)
                inspect(tx)
            if not pending:
                self.coordinator.record_outcome(tx_hash, True)
                return True
            time.sleep(1)
        return False

    def mint_dio(self, user, amount, sync=True, timeout=120000, operation_id=None):
        return self.send_transaction(user, "core.coin.mint", {"Amount": str(amount)},
                                     is_sync=sync, timeout=timeout, operation_id=operation_id)

    def create_dapp(self, user, dapp_name, deposit_amount, sync=True, timeout=120000, operation_id=None):
        tx_hash = self.send_transaction(user, "core.delegation.create",
                                       {"Type": 10, "Name": str(dapp_name), "Deposit": str(deposit_amount)},
                                       is_sync=sync, timeout=timeout, operation_id=operation_id)
        if sync and not self.client.wait_for_dapp_deployed(tx_hash, timeout):
            raise TimeoutError(f"Dioxide dapp deployment did not complete: {tx_hash}")
        return tx_hash, True if sync else None

    def deploy_contracts(self, dapp_name, delegator, contracts, compile_time=None, operation_id=None):
        args = {"code": [], "cargs": []}
        for filename, constructor in contracts.items():
            args["code"].append(Path(filename).read_text())
            args["cargs"].append(json.dumps(constructor))
        if compile_time is not None:
            args["time"] = compile_time
        tx_hash = self.send_transaction(delegator, "core.delegation.deploy_contracts", args,
                                        delegatee=dapp_name, is_sync=True, operation_id=operation_id)
        self.client.wait_for_deploy(tx_hash)
        return tx_hash

    def deploy_contract(self, dapp_name, delegator, file_path=None, source_code=None, construct_args=None,
                        compile_time=None, operation_id=None):
        code = Path(file_path).read_text() if file_path else source_code
        if code is None:
            raise ValueError("contract source is required")
        args = {"code": [code], "cargs": [json.dumps(construct_args)]}
        if compile_time is not None:
            args["time"] = compile_time
        tx_hash = self.send_transaction(delegator, "core.delegation.deploy_contracts", args,
                                        delegatee=dapp_name, is_sync=True, operation_id=operation_id)
        self.client.wait_for_deploy(tx_hash)
        return tx_hash
