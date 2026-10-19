import base64
import unittest
from unittest.mock import Mock
from dioxide_tx_coordinator import CoordinatedDioxClient


class WrapperTest(unittest.TestCase):
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
