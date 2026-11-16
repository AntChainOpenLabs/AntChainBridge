"""Synthetic evidence only; these fixtures are NOT real experiment data."""

import base64
import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
import aggregate_latency as a


def fixture(n=150):
    m = dict(
        schemaVersion=2,
        runId="synthetic-run",
        experimentId="synthetic-exp",
        scheme="Xun",
        route="ETH-MYC",
        round=1,
        roundsPerScheme=5,
        samples=n,
        formal=n == 150,
        protocol="unordered-sdp-v1",
        ratePerSecond=5 if n == 150 else 1,
        durationSeconds=30 if n == 150 else n,
        intervalMillis=200 if n == 150 else 1000,
        codeRevision="a" * 40,
        pluginChecksum="b" * 64,
        sourceProduct="ethereum3",
        sourceBlockchainId="eth.id",
        sourceDomain="eth.example",
        sourceAppIdentity="a" * 64,
        targetProduct="mychain2",
        targetBlockchainId="my.id",
        targetDomain="my.example",
        targetAppIdentity="b" * 64,
        eventProfile="mychain-legacy",
        targetStartHeight=1,
        targetStartHash="start",
        expectedSamples=[],
    )
    sender = []
    metrics = []
    events = []
    for i in range(n):
        sid = f"synthetic-exp-Xun-ETH-MYC-r1-s{i}"
        payload = f"ACBEXP1|{sid}|".encode().ljust(512, b"0")
        planned = dict(
            sampleId=sid,
            sampleIndex=i,
            attemptId=f"attempt-{i}",
            payloadBase64=base64.b64encode(payload).decode(),
            payloadSha256=hashlib.sha256(payload).hexdigest(),
        )
        m["expectedSamples"].append(planned)
        row = dict(
            planned,
            **{
                k: m[k]
                for k in (
                    "schemaVersion",
                    "runId",
                    "experimentId",
                    "scheme",
                    "route",
                    "round",
                    "sourceProduct",
                    "sourceBlockchainId",
                )
            },
            t0EpochMs=1000 + i * 200,
            scheduleLatenessMs=0,
            sourceTxHashKey=f"{i + 1:064x}",
            sourceTxHashNative=f"0x{i + 1:064x}",
            sendStatus="SENT",
        )
        sender.append(row)
        u = f"{i + 10000:064x}"
        target = f"{i + 1000:064x}"
        common = dict(
            ucpId=u,
            sourceProduct=m["sourceProduct"],
            sourceBlockchainId=m["sourceBlockchainId"],
            targetProduct=m["targetProduct"],
            targetBlockchainId=m["targetBlockchainId"],
        )
        metrics.extend(
            [
                dict(
                    common,
                    event="SOURCE_ACCEPTED",
                    eventTimeEpochMs=row["t0EpochMs"] + 20,
                    sourceTxHashKey=row["sourceTxHashKey"],
                ),
                dict(
                    common,
                    event="TARGET_SUBMITTING",
                    eventTimeEpochMs=row["t0EpochMs"] + 60,
                    targetTxHash=target,
                    success=True,
                ),
                dict(
                    common,
                    event="TARGET_CONFIRMED",
                    eventTimeEpochMs=row["t0EpochMs"] + 100,
                    targetTxHash=target,
                    success=True,
                ),
            ]
        )
        events.append(
            dict(
                schemaVersion=2,
                runId=m["runId"],
                targetProduct=m["targetProduct"],
                targetBlockchainId=m["targetBlockchainId"],
                appIdentity=m["targetAppIdentity"],
                sourceDomain=m["sourceDomain"],
                author=m["sourceAppIdentity"],
                payloadBase64=planned["payloadBase64"],
                payloadSha256=planned["payloadSha256"],
                eventProfile=m["eventProfile"],
                blockHeight=i + 2,
                blockHash=f"block{i + 2}",
                targetTxHash=target,
                logIndex=0,
                receiptSuccessful=True,
                finalized=True,
            )
        )
    coverage = dict(
        schemaVersion=2,
        runId=m["runId"],
        targetProduct=m["targetProduct"],
        targetBlockchainId=m["targetBlockchainId"],
        targetAppIdentity=m["targetAppIdentity"],
        targetStartHeight=1,
        targetStartHash="start",
        endHeight=n + 2,
        endHash="end",
        scannedBlocks=n + 2,
        observationSeconds=60,
        complete=True,
        terminalReached=True,
    )
    return (
        [m],
        sender,
        metrics,
        events,
        [coverage],
        [dict(schemaVersion=2, runId=m["runId"], samples=n)],
    )


class EvidenceVerificationTest(unittest.TestCase):
    def checked(self, data):
        rows, issues = a.analyze(*data)
        return rows, issues, a.summaries(rows, data[0][0]["samples"])

    def invalid(self, data, code=None):
        rows, issues, summary = self.checked(data)
        self.assertFalse(summary and all(s["validRun"] for s in summary))
        if code:
            self.assertTrue(any(d["code"] == code for d in issues), issues)
        return rows, issues

    def test_150_correct_samples(self):
        rows, issues, s = self.checked(fixture())
        self.assertEqual([], issues)
        self.assertTrue(s[0]["validRun"])
        self.assertEqual(150, s[0]["applicationVerifiedSamples"])
        self.assertEqual([20, 40, 40, 100], [rows[0][k] for k in a.LATENCY_FIELDS])

    def test_duplicate_missing_extra_and_malformed_plan(self):
        d = list(fixture())
        d[1] = [copy.deepcopy(d[1][0]) for _ in range(150)]
        self.invalid(d, "DUPLICATE_SAMPLE")
        d = fixture()
        d[1].pop()
        rows, _ = self.invalid(d, "MISSING_SENDER")
        self.assertEqual(150, len(rows))
        d = fixture()
        d[1].append(dict(d[1][0], sampleId="extra"))
        rows, _ = self.invalid(d, "EXTRA_SAMPLE")
        self.assertEqual(151, len(rows))
        d = fixture()
        d[0][0]["expectedSamples"][-1]["sampleIndex"] = 0
        self.invalid(d, "INVALID_MANIFEST")

    def test_shared_source_or_ambiguous_ucp(self):
        d = fixture()
        d[1][1].update(
            sourceTxHashKey=d[1][0]["sourceTxHashKey"],
            sourceTxHashNative=d[1][0]["sourceTxHashNative"],
        )
        self.invalid(d, "SHARED_TRANSACTION_OR_UCP")
        d = fixture()
        d[2].append(dict(d[2][0], ucpId="another"))
        self.invalid(d, "SOURCE_UCP_CONFLICT")
        d = fixture()
        d[2].append(dict(d[2][0], sourceBlockchainId="wrong"))
        self.invalid(d, "UCP_SOURCE_CONFLICT")

    def test_out_of_order_duplicate_logs_are_idempotent(self):
        d = fixture()
        d[2].extend(copy.deepcopy(d[2]))
        d[2].reverse()
        d[3].extend(copy.deepcopy(d[3]))
        self.assertTrue(self.checked(d)[2][0]["validRun"])

    def test_real_duplicate_receives_remain_visible(self):
        for change in (dict(targetTxHash="c" * 64), dict(logIndex=1)):
            d = fixture()
            d[3].append(dict(d[3][0], **change))
            rows, _ = self.invalid(d)
            self.assertEqual("APP_DUPLICATE", rows[0]["applicationStatus"])
        d = fixture()
        d[3].append(dict(d[3][0], author="d" * 64))
        self.invalid(d, "EVENT_POSITION_CONFLICT")

    def test_batch_target_receipt(self):
        d = fixture()
        for i, e in enumerate(d[3]):
            e.update(targetTxHash="c" * 64, blockHeight=2, blockHash="b2", logIndex=i)
        for e in d[2]:
            if "targetTxHash" in e:
                e["targetTxHash"] = "c" * 64
        self.assertTrue(self.checked(d)[2][0]["validRun"])

    def test_missing_or_corrupt_payload(self):
        d = fixture()
        d[3].pop()
        rows, _ = self.invalid(d)
        self.assertEqual("OK", rows[-1]["protocolStatus"])
        self.assertEqual("APP_MISSING", rows[-1]["applicationStatus"])
        d = fixture()
        b = bytearray(base64.b64decode(d[3][0]["payloadBase64"]))
        b[-1] ^= 1
        d[3][0].update(
            payloadBase64=base64.b64encode(b).decode(),
            payloadSha256=hashlib.sha256(b).hexdigest(),
        )
        rows, _ = self.invalid(d)
        self.assertEqual("APP_PAYLOAD_MISMATCH", rows[0]["applicationStatus"])

    def test_wrong_source_receiver_chain_or_receipt(self):
        for field, value in (
            ("author", "d" * 64),
            ("sourceDomain", "wrong"),
            ("appIdentity", "d" * 64),
            ("targetBlockchainId", "wrong"),
            ("receiptSuccessful", False),
            ("finalized", False),
            ("isOrdered", True),
        ):
            with self.subTest(field=field):
                d = fixture()
                d[3][0][field] = value
                self.invalid(d)

    def test_incomplete_observation(self):
        for field, value in (
            ("complete", False),
            ("observationSeconds", 59),
            ("scannedBlocks", 1),
            ("terminalReached", False),
            ("targetStartHash", "changed"),
        ):
            with self.subTest(field=field):
                d = fixture()
                d[4][0][field] = value
                self.invalid(d)
        d = list(fixture())
        d[5] = []
        self.invalid(d, "RUN_INTERRUPTED")

    def test_sender_cannot_diverge_from_plan(self):
        for field, value in (
            ("sourceTxHashNative", "2" * 64),
            ("scheme", "Base"),
            ("payloadSha256", "0" * 64),
            ("attemptId", "wrong"),
        ):
            with self.subTest(field=field):
                d = fixture()
                d[1][0][field] = value
                self.invalid(d)

    def test_latency_unknown_and_confirmation_conflicts(self):
        d = fixture()
        d[2][0]["eventTimeEpochMs"] = 0
        rows, _ = self.invalid(d)
        self.assertEqual("NEGATIVE_OR_INCONSISTENT_LATENCY", rows[0]["protocolStatus"])
        d = fixture()
        d[1][0]["scheduleLatenessMs"] = 201
        self.invalid(d)
        d = fixture()
        d[1][0]["sendStatus"] = "UNKNOWN"
        rows, _ = self.invalid(d)
        self.assertEqual("UNKNOWN", rows[0]["protocolStatus"])
        d = fixture()
        d[2].append(dict(d[2][2], targetTxHash="e" * 64))
        self.invalid(d, "TARGET_CONFIRMATION_CONFLICT")

    def test_nonformal_data_is_never_formal_paper_acceptance(self):
        d = fixture()
        d[0][0]["formal"] = False
        rows, issues, s = self.checked(d)
        self.assertTrue(s[0]["validRun"])
        self.assertFalse(s[0]["formalValidRun"])
        group = a.pooled_summaries(
            rows, [dict(s[0], round=str(i)) for i in range(1, 6)]
        )[0]
        self.assertFalse(group["completeGroup"])

    def test_legacy_cannot_pass_new_acceptance(self):
        d = fixture()
        rows, issues = a.analyze([], d[1], d[2], [], [], [], legacy=True)
        s = a.summaries(rows, 150)[0]
        self.assertTrue(s["protocolValidRun"])
        self.assertFalse(s["validRun"])

    def test_five_rounds(self):
        rows, _, s = self.checked(fixture())
        rounds = [dict(s[0], round=str(i), runId=f"r{i}") for i in range(1, 6)]
        group = a.pooled_summaries(rows, rounds)[0]
        self.assertTrue(group["completeGroup"])
        self.assertEqual(5, group["expectedRounds"])

    def test_complete_60_round_9000_sample_campaign(self):
        combined = [[] for _ in range(6)]
        products = {
            "Base": {"ETH": "ethereum2", "MYC": "mychain", "FISCO": "fiscobcos"},
            "Xun": {"ETH": "ethereum3", "MYC": "mychain2", "FISCO": "fiscobcos3"},
        }
        offset = 0
        for scheme in ("Base", "Xun"):
            for route in (
                "ETH-MYC",
                "ETH-FISCO",
                "MYC-ETH",
                "MYC-FISCO",
                "FISCO-ETH",
                "FISCO-MYC",
            ):
                source, target = route.split("-")
                for number in range(1, 6):
                    d = fixture()
                    m = d[0][0]
                    run = f"synthetic-{scheme}-{route}-{number}"
                    m.update(
                        runId=run,
                        scheme=scheme,
                        route=route,
                        round=number,
                        sourceProduct=products[scheme][source],
                        targetProduct=products[scheme][target],
                        sourceBlockchainId=source + ".id",
                        targetBlockchainId=target + ".id",
                        eventProfile="mychain-legacy" if target == "MYC" else "evm-app",
                    )
                    for i, (planned, sender, event) in enumerate(
                        zip(m["expectedSamples"], d[1], d[3])
                    ):
                        sid = f"synthetic-exp-{scheme}-{route}-r{number}-s{i}"
                        payload = f"ACBEXP1|{sid}|".encode().ljust(512, b"0")
                        planned.update(
                            sampleId=sid,
                            attemptId=f"{run}-{i}",
                            payloadBase64=base64.b64encode(payload).decode(),
                            payloadSha256=hashlib.sha256(payload).hexdigest(),
                        )
                        sender.update(planned)
                        sender.update(
                            {
                                k: m[k]
                                for k in (
                                    "runId",
                                    "scheme",
                                    "route",
                                    "round",
                                    "sourceProduct",
                                    "sourceBlockchainId",
                                )
                            }
                        )
                        sender.update(
                            sourceTxHashKey=f"{offset + i + 1:064x}",
                            sourceTxHashNative=f"0x{offset + i + 1:064x}",
                        )
                        for metric in d[2][i * 3 : i * 3 + 3]:
                            metric.update(
                                ucpId=f"{offset + i + 20000:064x}",
                                sourceProduct=m["sourceProduct"],
                                sourceBlockchainId=m["sourceBlockchainId"],
                                targetProduct=m["targetProduct"],
                                targetBlockchainId=m["targetBlockchainId"],
                            )
                            if metric["event"] == "SOURCE_ACCEPTED":
                                metric["sourceTxHashKey"] = sender["sourceTxHashKey"]
                            else:
                                metric["targetTxHash"] = f"{offset + i + 10000:064x}"
                        event.update(
                            runId=run,
                            targetProduct=m["targetProduct"],
                            targetBlockchainId=m["targetBlockchainId"],
                            payloadBase64=planned["payloadBase64"],
                            payloadSha256=planned["payloadSha256"],
                            targetTxHash=f"{offset + i + 10000:064x}",
                            eventProfile=m["eventProfile"],
                        )
                        if m["eventProfile"] == "evm-app":
                            event["isOrdered"] = False
                    d[4][0].update(
                        runId=run,
                        targetProduct=m["targetProduct"],
                        targetBlockchainId=m["targetBlockchainId"],
                    )
                    d[5][0]["runId"] = run
                    for dest, part in zip(combined, d):
                        dest.extend(part)
                    offset += 150
        rows, issues = a.analyze(*combined)
        summaries = a.summaries(rows, 150)
        pooled = a.pooled_summaries(rows, summaries)
        self.assertEqual([], issues)
        self.assertEqual(9000, len(rows))
        self.assertEqual(60, len(summaries))
        self.assertEqual(12, len(pooled))
        self.assertTrue(all(r["validRun"] for r in summaries))
        self.assertTrue(all(g["completeGroup"] for g in pooled))

    def test_cli_input_and_output_protection(self):
        root = Path(tempfile.mkdtemp(prefix="paper-aggregate-v2-"))
        paths = []
        for name, records in zip(
            ("manifest", "sender", "relayer", "receptions", "coverage", "completion"),
            fixture(),
        ):
            p = root / name
            p.write_text(
                json.dumps(records[0]) + "\n"
                if name in {"manifest", "coverage", "completion"}
                else "".join(
                    (a.METRIC_PREFIX if name == "relayer" else "")
                    + json.dumps(r)
                    + "\n"
                    for r in records
                )
            )
            paths.extend(["--" + name, str(p)])
        args = paths + ["--output-dir", str(root / "success")]
        self.assertEqual(0, a.main(args))
        with self.assertRaises(FileExistsError):
            a.main(args)
        bad = paths.copy()
        bad.insert(bad.index("--sender") + 2, str(root / "sender"))
        self.assertEqual(2, a.main(bad + ["--output-dir", str(root / "duplicate")]))
        self.assertIn(
            "DUPLICATE_INPUT_FILE", (root / "duplicate/diagnostics.csv").read_text()
        )

    def test_completion_and_metric_schema_conflicts(self):
        d = fixture()
        d[5][0]["samples"] = 1
        self.invalid(d, "INVALID_COMPLETION")
        d = fixture()
        d[2][2]["targetProduct"] = "wrong"
        self.invalid(d, "METRIC_CHAIN_CONFLICT")
        d = fixture()
        d[2][0].pop("eventTimeEpochMs")
        self.invalid(d, "INVALID_METRIC")
        d = fixture()
        d[1][0].pop("t0EpochMs")
        self.invalid(d, "INVALID_SENDER_TIME")

    def test_platform_history_is_not_timing_evidence(self):
        # Sanitized 2026-10-16 platform-report acknowledgements: these are NOT experiment metrics.
        lines = [
            "2026-10-16 13:08:55.935 platform report succeeded: ucpId=a98294e9bc53c40c28187591a5723c1c818383805b91422d0af3c3af18806b10, httpStatus=200",
            "2026-10-16 13:09:47.640 platform report succeeded: ucpId=a98294e9bc53c40c28187591a5723c1c818383805b91422d0af3c3af18806b10, httpStatus=200",
        ]
        root = Path(tempfile.mkdtemp(prefix="paper-historical-"))
        p = root / "platform.log"
        p.write_text("\n".join(lines) + "\n")
        self.assertEqual([], a.read_jsonl(p, True))
        d = fixture()
        rows, issues = a.analyze(
            [], d[1], a.read_jsonl(p, True), [], [], [], legacy=True
        )
        self.assertTrue(all(r["protocolStatus"] == "MISSING_T1" for r in rows))
        self.assertFalse(a.summaries(rows, 150)[0]["validRun"])

    def test_log_reader_and_torn_tail(self):
        root = Path(tempfile.mkdtemp(prefix="paper-torn-"))
        p = root / "sender"
        p.write_text('{"sampleId":"x"}\n{"broken":')
        with self.assertRaises(ValueError):
            a.read_jsonl(p)
        p = root / "metrics"
        p.write_text(
            "normal log\n"
            + a.METRIC_PREFIX
            + json.dumps(dict(event="TARGET_FAILED", ucpId="u"))
            + "\n"
        )
        self.assertEqual(1, len(a.read_jsonl(p, True)))


if __name__ == "__main__":
    unittest.main()
