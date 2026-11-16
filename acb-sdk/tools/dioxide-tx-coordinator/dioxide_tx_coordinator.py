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
SCHEMA_VERSION = 2
COMPONENT = "dioxide-coordinator"
RETRYABLE = {"REJECTED", "FAILED", "EXPIRED", "FORKED", "ABANDONED"}
UNCERTAIN = {"SIGNED", "UNKNOWN"}


class NodeRejectedError(RuntimeError):
    """A valid tx.send response explicitly rejected the signed transaction."""

    def __init__(self, code, message):
        super().__init__(message)
        self.code = code


class ReconciliationRequiredError(RuntimeError):
    pass


def dioxide_transaction_hash(signed):
    digest = hashlib.sha256(signed).digest()
    alphabet = "0123456789abcdefghjkmnpqrstvwxyz"
    output = []
    for group in range((len(digest) * 8 + 4) // 5):
        value = 0
        for offset in range(5):
            bit = group * 5 + offset
            value <<= 1
            if bit < len(digest) * 8:
                value |= (digest[bit // 8] >> (7 - bit % 8)) & 1
        output.append(alphabet[value])
    return "".join(output)


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
        self.acceptance_wait = int(config.get("acceptanceWaitMillis", "20000")) / 1000
        self.acceptance_poll = int(config.get("acceptancePollMillis", "500")) / 1000
        if self.acceptance_wait < 0 or self.acceptance_poll <= 0:
            raise ValueError("invalid coordinator acceptance polling settings")
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
                cursor.execute("SELECT schema_version FROM bridge_tx_schema_version WHERE component=%s", (COMPONENT,))
                row = cursor.fetchone()
                if not row or row[0] != SCHEMA_VERSION:
                    raise RuntimeError(f"Dioxide coordinator schema v{SCHEMA_VERSION} is required")
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

    @staticmethod
    def _checked_isn(value):
        value = int(value)
        if not 0 <= value <= MAX_ISN + 1:
            raise RuntimeError("ISN exhausted or invalid; refusing wraparound")
        return value

    def _reconcile(self, cursor, account, row, node_isn):
        observed, state, active_operation, active_attempt, active_isn = row
        if state == "RECONCILE_REQUIRED":
            raise ReconciliationRequiredError("Dioxide account requires reconciliation before submission")
        if active_operation is None:
            if node_isn < observed:
                cursor.execute(
                    "UPDATE bridge_tx_account SET allocation_state='RECONCILE_REQUIRED',last_error=%s "
                    "WHERE network_id=%s AND account=%s",
                    (f"node ISN regressed from {observed} to {node_isn}", self.network, account))
                raise ReconciliationRequiredError("node ISN regressed; reconcile network state before new submission")
            cursor.execute("UPDATE bridge_tx_account SET next_isn=%s,observed_isn=%s WHERE network_id=%s AND account=%s",
                           (node_isn, node_isn, self.network, account))
            return
        if active_attempt is None or active_isn is None:
            cursor.execute(
                "UPDATE bridge_tx_account SET allocation_state='RECONCILE_REQUIRED',last_error=%s "
                "WHERE network_id=%s AND account=%s",
                ("incomplete active reservation", self.network, account))
            raise ReconciliationRequiredError("incomplete Dioxide account reservation")
        if node_isn < active_isn:
            cursor.execute(
                "UPDATE bridge_tx_account SET allocation_state='RECONCILE_REQUIRED',last_error=%s "
                "WHERE network_id=%s AND account=%s",
                ("node ISN regressed below active reservation", self.network, account))
            raise ReconciliationRequiredError("node ISN regressed below active Dioxide reservation")
        if node_isn == active_isn:
            cursor.execute("UPDATE bridge_tx_account SET next_isn=%s,observed_isn=%s WHERE network_id=%s AND account=%s",
                           (node_isn, node_isn, self.network, account))
            return
        cursor.execute(
            "UPDATE bridge_tx_attempt SET state=IF(state IN ('SIGNED','BROADCAST','UNKNOWN'),'ACCEPTED',state) "
            "WHERE network_id=%s AND operation_id=%s AND attempt_no=%s",
            (self.network, active_operation, active_attempt))
        cursor.execute(
            "UPDATE bridge_tx_submission SET state=IF(state IN ('SIGNED','BROADCAST','UNKNOWN'),'ACCEPTED',state) "
            "WHERE network_id=%s AND operation_id=%s", (self.network, active_operation))
        cursor.execute(
            "UPDATE bridge_tx_account SET next_isn=%s,observed_isn=%s,allocation_state='READY',"
            "active_operation_id=NULL,active_attempt_no=NULL,active_isn=NULL,last_error=NULL "
            "WHERE network_id=%s AND account=%s", (node_isn, node_isn, self.network, account))

    def _reconcile_observation(self, connection, account, node_isn):
        connection.begin()
        try:
            with connection.cursor() as cursor:
                cursor.execute(
                    "SELECT observed_isn,allocation_state,active_operation_id,active_attempt_no,active_isn "
                    "FROM bridge_tx_account WHERE network_id=%s AND account=%s FOR UPDATE",
                    (self.network, account))
                self._reconcile(cursor, account, cursor.fetchone(), node_isn)
            connection.commit()
        except ReconciliationRequiredError:
            connection.commit()
            raise
        except BaseException:
            connection.rollback()
            raise

    def _broadcast(self, connection, operation_id, account, attempt, transport):
        attempt_no, isn, signed, expected_hash, _state = attempt
        try:
            returned_hash = transport.broadcast(signed)
            if not returned_hash:
                raise RuntimeError("broadcast returned no hash")
            if returned_hash != expected_hash:
                raise RuntimeError("node returned a transaction hash different from the signed bytes")
            with connection.cursor() as cursor:
                cursor.execute(
                    "UPDATE bridge_tx_attempt SET tx_hash=%s,state='BROADCAST',node_error_code=NULL,last_error=NULL "
                    "WHERE network_id=%s AND operation_id=%s AND attempt_no=%s",
                    (returned_hash, self.network, operation_id, attempt_no))
                cursor.execute(
                    "UPDATE bridge_tx_submission SET tx_hash=%s,state='BROADCAST',last_error=NULL "
                    "WHERE network_id=%s AND operation_id=%s",
                    (returned_hash, self.network, operation_id))
        except NodeRejectedError as error:
            node_isn = self._checked_isn(transport.current_isn(account))
            if node_isn > isn:
                self._reconcile_observation(connection, account, node_isn)
                return expected_hash
            with connection.cursor() as cursor:
                cursor.execute(
                    "UPDATE bridge_tx_attempt SET state='REJECTED',node_error_code=%s,last_error=%s "
                    "WHERE network_id=%s AND operation_id=%s AND attempt_no=%s",
                    (error.code, str(error)[:512], self.network, operation_id, attempt_no))
                cursor.execute(
                    "UPDATE bridge_tx_submission SET state='REJECTED',last_error=%s "
                    "WHERE network_id=%s AND operation_id=%s",
                    (str(error)[:512], self.network, operation_id))
            raise
        except Exception as error:
            try:
                with connection.cursor() as cursor:
                    cursor.execute(
                        "UPDATE bridge_tx_attempt SET state='UNKNOWN',last_error=%s "
                        "WHERE network_id=%s AND operation_id=%s AND attempt_no=%s",
                        (type(error).__name__, self.network, operation_id, attempt_no))
                    cursor.execute(
                        "UPDATE bridge_tx_submission SET state='UNKNOWN',last_error=%s "
                        "WHERE network_id=%s AND operation_id=%s",
                        (type(error).__name__, self.network, operation_id))
            except Exception:
                pass
            raise

        deadline = time.monotonic() + self.acceptance_wait
        while True:
            try:
                node_isn = self._checked_isn(transport.current_isn(account))
                self._reconcile_observation(connection, account, node_isn)
                if node_isn > isn:
                    break
            except Exception as error:
                try:
                    with connection.cursor() as cursor:
                        cursor.execute("UPDATE bridge_tx_account SET last_error=%s WHERE network_id=%s AND account=%s",
                                       (("acceptance check: " + type(error).__name__)[:512], self.network, account))
                except Exception:
                    pass
                break
            if self.acceptance_wait == 0 or time.monotonic() >= deadline:
                break
            time.sleep(min(self.acceptance_poll, max(0.001, deadline - time.monotonic())))
        return returned_hash

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
                    cursor.execute("SELECT checkpoint_hash,observed_isn,allocation_state,active_operation_id,active_attempt_no,active_isn "
                                   "FROM bridge_tx_account WHERE network_id=%s AND account=%s FOR UPDATE",
                                   (self.network, account))
                    checkpoint, observed, allocation_state, active_operation, active_attempt, active_isn = cursor.fetchone()
                    if checkpoint != self.checkpoint:
                        raise RuntimeError("coordinator network mismatch")
                    cursor.execute(
                        "SELECT account,payload_hash,active_attempt_no FROM bridge_tx_submission "
                        "WHERE network_id=%s AND operation_id=%s FOR UPDATE",
                        (self.network, operation_id))
                    previous = cursor.fetchone()
                    if previous and (previous[0] != account or previous[1] != fingerprint):
                        raise RuntimeError("submission identity reused with different account or payload")

                    node_isn = self._checked_isn(transport.current_isn(account))
                    self._reconcile(cursor, account,
                                    (observed, allocation_state, active_operation, active_attempt, active_isn), node_isn)
                    cursor.execute(
                        "SELECT active_operation_id,active_attempt_no,active_isn FROM bridge_tx_account "
                        "WHERE network_id=%s AND account=%s", (self.network, account))
                    active_operation, active_attempt, active_isn = cursor.fetchone()
                    if active_operation is not None and active_operation != operation_id:
                        raise RuntimeError(f"Dioxide account ISN {active_isn} is owned by operation {active_operation}")

                    if previous:
                        cursor.execute(
                            "SELECT attempt_no,isn,signed_tx,tx_hash,state FROM bridge_tx_attempt "
                            "WHERE network_id=%s AND operation_id=%s AND attempt_no=%s FOR UPDATE",
                            (self.network, operation_id, previous[2]))
                        attempt = cursor.fetchone()
                        if not attempt:
                            raise RuntimeError("coordinator submission has no attempt journal")
                        if active_operation is not None and attempt[4] in UNCERTAIN:
                            connection.commit()
                            return self._broadcast(connection, operation_id, account, attempt, transport)
                        if attempt[4] not in RETRYABLE:
                            if not attempt[3]:
                                raise RuntimeError("accepted Dioxide submission has no transaction hash")
                            connection.commit()
                            return attempt[3]

                    if node_isn > MAX_ISN:
                        raise RuntimeError("ISN exhausted or invalid; refusing wraparound")
                    attempt_no = 1 if previous is None else previous[2] + 1
                    signed = transport.compose_and_sign(node_isn)
                    if not signed:
                        raise RuntimeError("empty signed transaction")
                    hash_method = getattr(transport, "transaction_hash", dioxide_transaction_hash)
                    tx_hash = hash_method(signed)
                    cursor.execute(
                        "INSERT INTO bridge_tx_attempt(network_id,operation_id,attempt_no,account,isn,payload_hash,signed_tx,tx_hash,state) "
                        "VALUES(%s,%s,%s,%s,%s,%s,%s,%s,'SIGNED')",
                        (self.network, operation_id, attempt_no, account, node_isn, fingerprint, signed, tx_hash))
                    cursor.execute(
                        "INSERT INTO bridge_tx_submission(network_id,operation_id,account,isn,payload_hash,signed_tx,tx_hash,state,active_attempt_no) "
                        "VALUES(%s,%s,%s,%s,%s,%s,%s,'SIGNED',%s) ON DUPLICATE KEY UPDATE isn=VALUES(isn),"
                        "signed_tx=VALUES(signed_tx),tx_hash=VALUES(tx_hash),state='SIGNED',"
                        "active_attempt_no=VALUES(active_attempt_no),last_error=NULL",
                        (self.network, operation_id, account, node_isn, fingerprint, signed, tx_hash, attempt_no))
                    cursor.execute(
                        "UPDATE bridge_tx_account SET next_isn=%s,observed_isn=%s,allocation_state='BLOCKED',"
                        "active_operation_id=%s,active_attempt_no=%s,active_isn=%s,last_error=NULL "
                        "WHERE network_id=%s AND account=%s",
                        (node_isn, node_isn, operation_id, attempt_no, node_isn, self.network, account))
                    attempt = (attempt_no, node_isn, signed, tx_hash, "SIGNED")
                connection.commit()
            except ReconciliationRequiredError:
                connection.commit()
                raise
            except BaseException:
                connection.rollback()
                raise
            return self._broadcast(connection, operation_id, account, attempt, transport)

    def record_outcome(self, tx_hash, success):
        connection = self.connect()
        try:
            with connection.cursor() as cursor:
                cursor.execute("UPDATE bridge_tx_attempt SET state=%s WHERE network_id=%s AND tx_hash=%s",
                               ("FINALIZED" if success else "FAILED", self.network, tx_hash))
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
            # Match DioxClient: an absent constructor is the empty string,
            # whereas an explicitly supplied empty object must stay "{}".
            args["cargs"].append("" if constructor is None else json.dumps(constructor))
        if compile_time is not None:
            args["time"] = compile_time
        deploy_timeout_seconds = max(60, (compile_time or 0) * 6)
        tx_hash = self.send_transaction(delegator, "core.delegation.deploy_contracts", args,
                                        delegatee=dapp_name, is_sync=True,
                                        timeout=deploy_timeout_seconds * 1000, operation_id=operation_id)
        self.client.wait_for_deploy(tx_hash, deploy_timeout_seconds)
        return tx_hash

    def deploy_contract(self, dapp_name, delegator, file_path=None, source_code=None, construct_args=None,
                        compile_time=None, operation_id=None):
        code = Path(file_path).read_text() if file_path else source_code
        if code is None:
            raise ValueError("contract source is required")
        args = {"code": [code], "cargs": ["" if construct_args is None else json.dumps(construct_args)]}
        if compile_time is not None:
            args["time"] = compile_time
        deploy_timeout_seconds = max(60, (compile_time or 0) * 6)
        tx_hash = self.send_transaction(delegator, "core.delegation.deploy_contracts", args,
                                        delegatee=dapp_name, is_sync=True,
                                        timeout=deploy_timeout_seconds * 1000, operation_id=operation_id)
        self.client.wait_for_deploy(tx_hash, deploy_timeout_seconds)
        return tx_hash
