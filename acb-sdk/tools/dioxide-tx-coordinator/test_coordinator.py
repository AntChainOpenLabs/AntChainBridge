import concurrent.futures
import os
import subprocess
import unittest
import uuid
import time

import pymysql
from dioxide_tx_coordinator import Coordinator, dioxide_transaction_hash


def connection():
    return pymysql.connect(host="127.0.0.1", port=int(os.environ.get("ISN_TEST_MYSQL_PORT", "18236")),
                           user="root", password="", database=os.environ.get("ISN_TEST_MYSQL_DB", "isn_test"),
                           autocommit=True)


def coordinator(network):
    return Coordinator({"networkId": network, "checkpointHash": "fixture",
                        "acceptanceWaitMillis": "0", "acceptancePollMillis": "1"}, connection)


class Transport:
    def __init__(self, network):
        self.network = network

    def checkpoint(self):
        return "fixture"

    def current_isn(self, account):
        with connection() as c, c.cursor() as q:
            q.execute("SELECT isn FROM coordinator_test_node WHERE network_id=%s", (self.network,))
            return q.fetchone()[0]

    def compose_and_sign(self, isn):
        return isn.to_bytes(8, "big")

    def transaction_hash(self, signed):
        return "tx-" + str(int.from_bytes(signed, "big"))

    def broadcast(self, signed):
        isn = int.from_bytes(signed, "big")
        with connection() as c, c.cursor() as q:
            c.begin()
            q.execute("SELECT isn FROM coordinator_test_node WHERE network_id=%s FOR UPDATE", (self.network,))
            current = q.fetchone()[0]
            if isn > current:
                c.rollback()
                raise RuntimeError("future ISN")
            if isn == current:
                q.execute("UPDATE coordinator_test_node SET isn=isn+1 WHERE network_id=%s", (self.network,))
            c.commit()
        return self.transaction_hash(signed)


def python_process(network, prefix):
    c = coordinator(network)
    return [c.submit(f"{prefix}-{i}", "account:ed25519", bytes([1, 2, 3]), Transport(network)) for i in range(32)]


def python_query_process(network, prefix):
    values = []
    for i in range(8):
        own = f"{prefix}-{i}"
        with coordinator(network).lock("query", "sdp|account") as c, c.cursor() as q:
            q.execute("UPDATE coordinator_test_mailbox SET value=%s WHERE network_id=%s", (own, network))
            time.sleep(0.01)
            q.execute("SELECT value FROM coordinator_test_mailbox WHERE network_id=%s", (network,))
            if q.fetchone()[0] != own:
                raise AssertionError("mailbox overwritten")
            values.append(own)
    return values


@unittest.skipUnless(os.environ.get("ISN_TEST_MYSQL") == "1", "requires disposable MySQL")
class CoordinatorTest(unittest.TestCase):
    def setUp(self):
        self.network = "python-" + str(uuid.uuid4())
        with connection() as c, c.cursor() as q:
            q.execute("CREATE TABLE IF NOT EXISTS coordinator_test_node (network_id VARCHAR(96) PRIMARY KEY, isn BIGINT NOT NULL)")
            q.execute("INSERT INTO coordinator_test_node VALUES (%s,181)", (self.network,))

    def test_dioxide_hash_vector(self):
        signed = bytes.fromhex(
            "6300A3C188B3A0010300000077000B000C80640000000000000000000109000140420F0000000000"
            "03E2BC6A2FE6104B24B41B4B6864F1B9C2DC533C12DEBE0316B0D6EBA471EF9911A3B7A2BA0134AB5F"
            "C1119EA07447830B5DF3D031005FCB8AA0A8FCE23E20DCDECDCC9EA09AF368542F10E998F6EEF869AC4"
            "11ABC678513FE83ED05B1BDC0E4088C0E00007C2D000036340000")
        self.assertEqual("9apsx7wjpxbvg0b99tjpjgfacet3zhx2f4nsbcs1fwcghdexgz00",
                         dioxide_transaction_hash(signed))

    def test_independent_intents_and_response_loss(self):
        class Lost(Transport):
            def broadcast(self, signed):
                super().broadcast(signed)
                raise TimeoutError()
        c = coordinator(self.network)
        with self.assertRaises(TimeoutError):
            c.submit("same", "account", b"payload", Lost(self.network))
        self.assertEqual("tx-181", coordinator(self.network).submit("same", "account", b"payload", Transport(self.network)))
        self.assertEqual("tx-182", c.submit("other", "account", b"payload", Transport(self.network)))
        with self.assertRaisesRegex(RuntimeError, "identity"):
            c.submit("same", "account", b"changed", Transport(self.network))

    def test_two_python_processes(self):
        with concurrent.futures.ProcessPoolExecutor(max_workers=2) as pool:
            futures = [pool.submit(python_process, self.network, prefix) for prefix in ["a", "b"]]
            values = [h for f in futures for h in f.result(timeout=60)]
        self.assertEqual(64, len(set(values)))

    @unittest.skipUnless(os.environ.get("JAVA_PROBE_CLASSPATH"), "requires compiled Java fixture")
    def test_two_java_and_two_python_processes_and_crash_recovery(self):
        args = [os.environ["JAVA_PROBE_JAVA"], "-cp", os.environ["JAVA_PROBE_CLASSPATH"],
                "com.alipay.antchain.bridge.plugins.lib.transactions.CoordinatorProcessProbe",
                f"jdbc:mysql://127.0.0.1:{os.environ.get('ISN_TEST_MYSQL_PORT', '18236')}/"
                f"{os.environ.get('ISN_TEST_MYSQL_DB', 'isn_test')}?allowPublicKeyRetrieval=true&useSSL=false",
                self.network]
        processes = [subprocess.Popen(args + [prefix, "32"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                     for prefix in ["j1", "j2"]]
        with concurrent.futures.ProcessPoolExecutor(max_workers=2) as pool:
            futures = [pool.submit(python_process, self.network, prefix) for prefix in ["p1", "p2"]]
            hashes = [h for f in futures for h in f.result(timeout=60)]
        for process in processes:
            stdout, stderr = process.communicate(timeout=60)
            self.assertEqual(0, process.returncode, stderr)
            hashes.extend(stdout.splitlines())
        self.assertEqual(128, len(set(hashes)))
        crashed = subprocess.run(args + ["crash", "1", "crash"], capture_output=True, timeout=30)
        self.assertEqual(17, crashed.returncode)
        # The Python implementation resumes Java's committed signed bytes, not a new ISN.
        recovered = coordinator(self.network).submit("crash-0", "account", bytes([1, 2, 3]), Transport(self.network))
        self.assertEqual("tx-309", recovered)
        crashed_before_sign = subprocess.run(args + ["pre-crash", "1", "crash-before-sign"], capture_output=True, timeout=30)
        self.assertEqual(18, crashed_before_sign.returncode)
        self.assertEqual("tx-310", coordinator(self.network).submit("pre-crash-0", "account", bytes([1, 2, 3]), Transport(self.network)))
        with connection() as c, c.cursor() as cursor:
            cursor.execute("SELECT COUNT(*),COUNT(DISTINCT isn) FROM bridge_tx_submission WHERE network_id=%s", (self.network,))
            self.assertEqual((130, 130), cursor.fetchone())

    @unittest.skipUnless(os.environ.get("JAVA_PROBE_CLASSPATH"), "requires compiled Java fixture")
    def test_query_mailbox_across_two_java_and_two_python_processes(self):
        with connection() as c, c.cursor() as q:
            q.execute("CREATE TABLE IF NOT EXISTS coordinator_test_mailbox (network_id VARCHAR(96) PRIMARY KEY, value VARCHAR(96))")
            q.execute("INSERT INTO coordinator_test_mailbox VALUES (%s,'')", (self.network,))
        args = [os.environ["JAVA_PROBE_JAVA"], "-cp", os.environ["JAVA_PROBE_CLASSPATH"],
                "com.alipay.antchain.bridge.plugins.lib.transactions.CoordinatorProcessProbe",
                f"jdbc:mysql://127.0.0.1:{os.environ.get('ISN_TEST_MYSQL_PORT', '18236')}/"
                f"{os.environ.get('ISN_TEST_MYSQL_DB', 'isn_test')}?allowPublicKeyRetrieval=true&useSSL=false", self.network]
        processes = [subprocess.Popen(args + [prefix, "8", "query"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                     for prefix in ["j1", "j2"]]
        with concurrent.futures.ProcessPoolExecutor(max_workers=2) as pool:
            futures = [pool.submit(python_query_process, self.network, prefix) for prefix in ["p1", "p2"]]
            values = [v for f in futures for v in f.result(timeout=60)]
        for process in processes:
            stdout, stderr = process.communicate(timeout=60)
            self.assertEqual(0, process.returncode, stderr)
            values.extend(stdout.splitlines())
        self.assertEqual(32, len(set(values)))


if __name__ == "__main__":
    unittest.main()
