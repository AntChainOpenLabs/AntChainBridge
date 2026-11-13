import base64
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock
from dioxide_tx_coordinator import CoordinatedDioxClient


class WrapperTest(unittest.TestCase):
    def test_batch_deploy_preserves_sdk_constructor_encoding(self):
        wrapper = self.wrapper([])
        wrapper.send_transaction = Mock(return_value="deployment-hash")
        user = Mock(address="public-owner")
        with tempfile.TemporaryDirectory() as directory:
            contracts = {}
            for name, constructor in [("Interface.gcl", None), ("Empty.gcl", {}),
                                      ("App.gcl", {"owner": "public-owner", "label": "中文"})]:
                path = Path(directory) / name
                path.write_text(name, encoding="utf-8")
                contracts[str(path)] = constructor
            self.assertEqual("deployment-hash", wrapper.deploy_contracts(
                "testapp", user, contracts, compile_time=20, operation_id="deploy:batch"))
        wrapper.send_transaction.assert_called_once_with(
            user, "core.delegation.deploy_contracts",
            {"code": ["Interface.gcl", "Empty.gcl", "App.gcl"],
             "cargs": ["", "{}", json.dumps({"owner": "public-owner", "label": "中文"})],
             "time": 20}, delegatee="testapp", is_sync=True, timeout=120000,
            operation_id="deploy:batch")
        wrapper.client.wait_for_deploy.assert_called_once_with("deployment-hash", 120)

    def test_single_deploy_preserves_sdk_constructor_encoding(self):
        for constructor in [None, {}, {"owner": "public-owner"}]:
            with self.subTest(constructor=constructor):
                wrapper = self.wrapper([])
                wrapper.send_transaction = Mock(return_value="deployment-hash")
                user = Mock(address="public-owner")
                self.assertEqual("deployment-hash", wrapper.deploy_contract(
                    "testapp", user, source_code="interface I {}", construct_args=constructor,
                    operation_id="deploy:single"))
                expected = "" if constructor is None else json.dumps(constructor)
                wrapper.send_transaction.assert_called_once_with(
                    user, "core.delegation.deploy_contracts",
                    {"code": ["interface I {}"], "cargs": [expected]},
                    delegatee="testapp", is_sync=True, timeout=60000, operation_id="deploy:single")
                wrapper.client.wait_for_deploy.assert_called_once_with("deployment-hash", 60)

    def test_deployment_wait_limits_match_sdk_in_both_units(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Interface.gcl"
            path.write_text("interface I {}", encoding="utf-8")
            for method in ["deploy_contract", "deploy_contracts"]:
                for compile_time, seconds in [(None, 60), (0, 60), (10, 60), (20, 120), (30, 180)]:
                    with self.subTest(method=method, compile_time=compile_time):
                        wrapper = self.wrapper([])
                        wrapper.send_transaction = Mock(return_value="original-hash")
                        args = {"file_path": str(path)} if method == "deploy_contract" else {
                            "contracts": {str(path): None}}
                        self.assertEqual("original-hash", getattr(wrapper, method)(
                            "testapp", Mock(address="owner"), compile_time=compile_time,
                            operation_id="deploy:timeout-test", **args))
                        wrapper.send_transaction.assert_called_once()
                        self.assertEqual(seconds * 1000,
                                         wrapper.send_transaction.call_args.kwargs["timeout"])
                        self.assertEqual("deploy:timeout-test",
                                         wrapper.send_transaction.call_args.kwargs["operation_id"])
                        wrapper.client.wait_for_deploy.assert_called_once_with("original-hash", seconds)

    def test_deployment_wait_failures_never_resubmit(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Interface.gcl"
            path.write_text("interface I {}", encoding="utf-8")
            for method in ["deploy_contract", "deploy_contracts"]:
                for phase in ["confirmation", "deployment"]:
                    for error in [TimeoutError("wait expired"), RuntimeError("execution failed")]:
                        with self.subTest(method=method, phase=phase, error=type(error)):
                            wrapper = self.wrapper([])
                            wrapper.send_transaction = Mock(return_value="original-hash")
                            if phase == "confirmation":
                                wrapper.send_transaction.side_effect = error
                            else:
                                wrapper.client.wait_for_deploy.side_effect = error
                            args = {"file_path": str(path)} if method == "deploy_contract" else {
                                "contracts": {str(path): None}}
                            with self.assertRaises(type(error)) as caught:
                                getattr(wrapper, method)("testapp", Mock(address="owner"),
                                    compile_time=20, operation_id="deploy:original", **args)
                            self.assertIs(error, caught.exception)
                            wrapper.send_transaction.assert_called_once()
                            if phase == "confirmation":
                                wrapper.client.wait_for_deploy.assert_not_called()
                            else:
                                wrapper.client.wait_for_deploy.assert_called_once_with("original-hash", 120)

    def test_dapp_setup_uses_coordinated_submission_and_checks_wait(self):
        wrapper = self.wrapper([])
        wrapper.send_transaction = Mock(return_value="same-hash")
        user = Mock()
        self.assertEqual("same-hash", wrapper.mint_dio(user, 100, operation_id="setup:fund"))
        wrapper.send_transaction.assert_called_with(user, "core.coin.mint", {"Amount": "100"},
                                                    is_sync=True, timeout=120000, operation_id="setup:fund")
        wrapper.client.wait_for_dapp_deployed.return_value = True
        self.assertEqual(("same-hash", True), wrapper.create_dapp(user, "test", 10, operation_id="setup:dapp"))
        wrapper.send_transaction.assert_called_with(user, "core.delegation.create",
                                                    {"Type": 10, "Name": "test", "Deposit": "10"},
                                                    is_sync=True, timeout=120000, operation_id="setup:dapp")
        wrapper.client.wait_for_dapp_deployed.return_value = False
        with self.assertRaises(TimeoutError):
            wrapper.create_dapp(user, "test", 10, operation_id="setup:dapp")

    def test_indexed_group_ignores_unrelated_failed_business(self):
        wrapper = self.wrapper([
            {"State": "DUS_ARCHIVED", "Invocation": {"Status": "IVKRET_SUCCESS", "Relays": ["group:1"]}},
            {"State": "DUS_ARCHIVED", "Relays": [
                {"Invocation": {"Status": "IVKRET_EXCEPTION_THROWN"}},
                {"Invocation": {"Status": "IVKRET_SUCCESS"}},
            ]},
        ])
        self.assertTrue(wrapper.wait_for_transaction_confirmed("root"))
        wrapper.coordinator.record_outcome.assert_called_once_with("root", True)

    def wrapper(self, responses):
        wrapper = CoordinatedDioxClient.__new__(CoordinatedDioxClient)
        wrapper.client = Mock()
        wrapper.client.make_request.side_effect = responses
        wrapper.coordinator = Mock()
        wrapper.coordinator.config = {"checkpointHeight": "123"}
        return wrapper

    def test_explicit_reserved_isn_and_delegatee(self):
        raw = bytes(8) + (181).to_bytes(4, "little") + b"payload"
        wrapper = self.wrapper([{"Hash": "checkpoint"}, {"ISN": 181},
                                {"TxData": base64.b64encode(raw).decode()}, {"Hash": "tx"}])
        user = Mock()
        user.address = "signer:ed25519"
        user.sign_diox_transaction.return_value = b"signed-fixture"
        def submit(op, account, payload, transport):
            self.assertEqual("deployment-1", op)
            self.assertEqual("testapp:dapp", account)
            self.assertEqual("checkpoint", transport.checkpoint())
            self.assertEqual(181, transport.current_isn(account))
            self.assertEqual(b"signed-fixture", transport.compose_and_sign(181))
            return transport.broadcast(b"signed-fixture")
        wrapper.coordinator.submit.side_effect = submit
        self.assertEqual("tx", wrapper.send_transaction(user, "core.delegation.deploy_contracts", {},
                                                       delegatee="testapp", operation_id="deployment-1"))
        compose = wrapper.client.make_request.call_args_list[2].args[1]
        self.assertEqual(181, compose["isn"])
        self.assertNotIn("sender", compose)
        user.sign_diox_transaction.assert_called_once_with(raw)

    def test_sync_timeout_is_not_success(self):
        wrapper = self.wrapper([])
        wrapper.coordinator.submit.return_value = "tx"
        wrapper.wait_for_transaction_confirmed = Mock(return_value=False)
        user = Mock(address="account")
        with self.assertRaises(TimeoutError):
            wrapper.send_transaction(user, "app.send", {}, is_sync=True, operation_id="one")

    def test_embedded_failure_and_abort_are_terminal(self):
        for tx in [
            {"ConfirmState": "TXN_ABORTED"},
            {"State": "DUS_ARCHIVED", "Relays": [{"Invocation": {"Status": "IVKRET_EXCEPTION_THROWN"}}]},
        ]:
            wrapper = self.wrapper([tx])
            with self.assertRaises(RuntimeError):
                wrapper.wait_for_transaction_confirmed("root")
            wrapper.coordinator.record_outcome.assert_called_once_with("root", False)

    def test_waits_for_referenced_child_before_success(self):
        wrapper = self.wrapper([
            {"State": "DUS_ARCHIVED", "Relays": [{"Invocation": {"Status": "IVKRET_SUCCESS", "Relays": ["child"]}}]},
            {"State": "DUS_ARCHIVED", "Invocation": {"Status": "IVKRET_SUCCESS"}},
        ])
        self.assertTrue(wrapper.wait_for_transaction_confirmed("root"))
        wrapper.coordinator.record_outcome.assert_called_once_with("root", True)
        self.assertEqual({"hash": "child"}, wrapper.client.make_request.call_args.args[1])
