#!/usr/bin/env python3
"""Strict v2 experiment evidence verification; historical protocol-only import is explicit."""

from __future__ import annotations
import argparse
import base64
import csv
import hashlib
import json
import math
import statistics
import sys
import time
from collections import defaultdict
from pathlib import Path

METRIC_PREFIX = "EXPERIMENT_METRIC "
LATENCY_FIELDS = ("Ts", "To", "Tt", "Ttotal")
EVENTS = {"SOURCE_ACCEPTED", "TARGET_SUBMITTING", "TARGET_CONFIRMED", "TARGET_FAILED"}


def norm(value):
    return str(value or "").lower().removeprefix("0x")


def identity(value):
    h = norm(value)
    if len(h) not in (40, 64) or any(c not in "0123456789abcdef" for c in h):
        raise ValueError("invalid application identity")
    return h.rjust(64, "0")


def source_key(record):
    return (
        record.get("sourceProduct", ""),
        record.get("sourceBlockchainId", ""),
        norm(record.get("sourceTxHashKey")),
    )


def diagnostic(code, run="", sample="", detail=""):
    return dict(code=code, runId=run, sampleId=sample, detail=detail)


def read_jsonl(path, relayer=False):
    text = Path(path).read_text(encoding="utf-8")
    result = []
    for line in text.splitlines(keepends=True):
        if not line.strip():
            continue
        if relayer:
            marker = line.find(METRIC_PREFIX)
            if marker < 0:
                continue
            line = line[marker + len(METRIC_PREFIX) :]
        # A partial final line is not silently accepted as complete sender evidence.
        if not line.endswith("\n"):
            raise ValueError(f"unterminated evidence record: {path}")
        record = json.loads(line)
        if not isinstance(record, dict):
            raise ValueError(f"evidence record must be an object: {path}")
        if not relayer or record.get("event") in EVENTS:
            result.append(record)
    return result


def protocol_rows(senders, metrics, diagnostics):
    accepted = defaultdict(dict)
    by_ucp = defaultdict(list)
    ucp_sources = defaultdict(set)
    for e in metrics:
        u = e.get("ucpId", "")
        if not u:
            continue
        by_ucp[u].append(e)
        if e.get("event") == "SOURCE_ACCEPTED":
            k = source_key(e)
            ucp_sources[u].add(k)
            prior = accepted[k].get(u)
            if prior is None or int(e["eventTimeEpochMs"]) < int(
                prior["eventTimeEpochMs"]
            ):
                accepted[k][u] = e
    rows = []
    for sender in senders:
        row = dict(sender)
        row.update(ucpId="", targetTxHash="", protocolStatus="", reason="")
        row.update(
            {
                field: ""
                for field in ("t1EpochMs", "t2EpochMs", "t3EpochMs", *LATENCY_FIELDS)
            }
        )

        def error(code):
            row["protocolStatus"] = code
            diagnostics.append(
                diagnostic(code, row.get("runId", ""), row.get("sampleId", ""))
            )

        status = sender.get("sendStatus")
        if status == "SENT" and (
            not isinstance(sender.get("t0EpochMs"), int)
            or not isinstance(sender.get("scheduleLatenessMs"), int)
        ):
            error("INVALID_SENDER_TIME")
            rows.append(row)
            continue
        if status != "SENT":
            row["protocolStatus"] = (
                status
                if status in {"SEND_FAILED", "UNKNOWN", "NOT_SENT"}
                else "MISSING_SENDER"
            )
        else:
            matches = accepted[source_key(sender)]
            if not matches:
                row["protocolStatus"] = "MISSING_T1"
            elif len(matches) != 1:
                error("SOURCE_UCP_CONFLICT")
            else:
                u, a = next(iter(matches.items()))
                row["ucpId"] = u
                if len(ucp_sources[u]) != 1:
                    error("UCP_SOURCE_CONFLICT")
                else:
                    events = by_ucp[u]
                    pick = lambda kind: min(
                        (e for e in events if e.get("event") == kind),
                        key=lambda e: int(e["eventTimeEpochMs"]),
                        default=None,
                    )
                    t2, t3, failed = (
                        pick(k)
                        for k in (
                            "TARGET_SUBMITTING",
                            "TARGET_CONFIRMED",
                            "TARGET_FAILED",
                        )
                    )
                    row["t1EpochMs"] = int(a["eventTimeEpochMs"])
                    if t2 is None:
                        row["protocolStatus"] = "MISSING_T2"
                    else:
                        row["t2EpochMs"] = int(t2["eventTimeEpochMs"])
                        if t3 is None:
                            row["protocolStatus"] = (
                                "TARGET_FAILED" if failed else "MISSING_T3"
                            )
                            row["reason"] = failed.get("reason", "") if failed else ""
                            row["targetTxHash"] = (
                                failed.get("targetTxHash", "") if failed else ""
                            )
                        elif not t3.get("success", False):
                            error("INVALID_CONFIRMATION")
                        else:
                            hashes = {
                                norm(e.get("targetTxHash"))
                                for e in events
                                if e.get("event") == "TARGET_CONFIRMED"
                            }
                            if len(hashes) != 1 or not next(iter(hashes)):
                                error("TARGET_CONFIRMATION_CONFLICT")
                            else:
                                row["t3EpochMs"] = int(t3["eventTimeEpochMs"])
                                row["targetTxHash"] = t3["targetTxHash"]
                                t0, t1, t2v, t3v = (
                                    int(row[k])
                                    for k in (
                                        "t0EpochMs",
                                        "t1EpochMs",
                                        "t2EpochMs",
                                        "t3EpochMs",
                                    )
                                )
                                row.update(
                                    Ts=t1 - t0,
                                    To=t2v - t1,
                                    Tt=t3v - t2v,
                                    Ttotal=t3v - t0,
                                )
                                row["protocolStatus"] = (
                                    "OK"
                                    if all(row[k] >= 0 for k in LATENCY_FIELDS)
                                    else "NEGATIVE_OR_INCONSISTENT_LATENCY"
                                )
        rows.append(row)
    return rows


def payload_bytes(record):
    b = base64.b64decode(record["payloadBase64"], validate=True)
    if len(b) != 512 or hashlib.sha256(b).hexdigest() != record["payloadSha256"]:
        raise ValueError("payload length/digest mismatch")
    return b


def validate_manifest(m):
    if m.get("schemaVersion") != 2 or not m.get("runId"):
        raise ValueError("schemaVersion=2/runId required")
    for k in (
        "experimentId",
        "scheme",
        "route",
        "round",
        "sourceProduct",
        "sourceBlockchainId",
        "sourceDomain",
        "sourceAppIdentity",
        "targetProduct",
        "targetBlockchainId",
        "targetDomain",
        "targetAppIdentity",
        "eventProfile",
        "targetStartHeight",
        "targetStartHash",
        "formal",
        "ratePerSecond",
        "durationSeconds",
        "intervalMillis",
        "codeRevision",
    ):
        if k not in m or m[k] == "":
            raise ValueError(f"manifest missing {k}")
    if type(m.get("formal")) is not bool or not isinstance(m.get("samples"), int):
        raise ValueError("formal boolean and sample count required")
    if m["scheme"] not in {"Base", "Xun"} or m["route"] not in {
        "ETH-MYC",
        "ETH-FISCO",
        "MYC-ETH",
        "MYC-FISCO",
        "FISCO-ETH",
        "FISCO-MYC",
    }:
        raise ValueError("invalid scheme/route")
    products = {
        "Base": {"ETH": "ethereum2", "MYC": "mychain", "FISCO": "fiscobcos"},
        "Xun": {"ETH": "ethereum3", "MYC": "mychain2", "FISCO": "fiscobcos3"},
    }[m["scheme"]]
    left, right = m["route"].split("-")
    if m["sourceProduct"] != products[left] or m["targetProduct"] != products[right]:
        raise ValueError("scheme/route/product conflict")
    if (
        int(m["ratePerSecond"]) <= 0
        or int(m["durationSeconds"]) <= 0
        or m["samples"] != m["ratePerSecond"] * m["durationSeconds"]
        or m["intervalMillis"] * m["ratePerSecond"] != 1000
    ):
        raise ValueError("inconsistent workload metadata")
    if m.get("protocol") != "unordered-sdp-v1" or m.get("roundsPerScheme") != 5:
        raise ValueError("unordered-sdp-v1 and five rounds required")
    if m.get("eventProfile") not in {"evm-app", "mychain-legacy", "mychain-monitor"}:
        raise ValueError("unsupported event profile")
    expected = m["expectedSamples"]
    n = int(m["samples"])
    if (
        n <= 0
        or len(expected) != n
        or [s["sampleIndex"] for s in expected] != list(range(n))
    ):
        raise ValueError("manifest sample coverage invalid")
    if m.get("formal") and (
        len(m["codeRevision"]) != 40
        or any(c not in "0123456789abcdefABCDEF" for c in m["codeRevision"])
        or len(m.get("pluginChecksum", "")) != 64
    ):
        raise ValueError("formal revision/checksum required")
    if m.get("formal") and (
        n != 150
        or m.get("ratePerSecond") != 5
        or m.get("durationSeconds") != 30
        or m["round"] not in range(1, 6)
    ):
        raise ValueError(
            "formal run requires 150 samples, 5 TPS, 30 seconds, round 1..5"
        )
    ids, attempts = set(), set()
    for s in expected:
        wanted = f"{m['experimentId']}-{m['scheme']}-{m['route']}-r{m['round']}-s{s['sampleIndex']}"
        b = payload_bytes(s)
        if s["sampleId"] != wanted or not b.startswith(f"ACBEXP1|{wanted}|".encode()):
            raise ValueError("sample identity/payload conflict")
        if s["sampleId"] in ids or not s.get("attemptId") or s["attemptId"] in attempts:
            raise ValueError("duplicate sample/attempt in plan")
        ids.add(s["sampleId"])
        attempts.add(s["attemptId"])
    identity(m["sourceAppIdentity"])
    identity(m["targetAppIdentity"])


def reception_status(row, m, events, coverage):
    if (
        not coverage
        or coverage.get("complete") is not True
        or coverage.get("terminalReached") is not True
    ):
        return "EVIDENCE_INCOMPLETE"
    for k in (
        "runId",
        "targetProduct",
        "targetBlockchainId",
        "targetAppIdentity",
        "targetStartHeight",
        "targetStartHash",
    ):
        if coverage.get(k) != m.get(k):
            return "EVIDENCE_CONFLICT"
    if (
        int(coverage.get("observationSeconds", 0)) < 60
        or int(coverage.get("scannedBlocks", 0))
        != int(coverage.get("endHeight", -1)) - int(m["targetStartHeight"]) + 1
        or not coverage.get("endHash")
    ):
        return "EVIDENCE_INCOMPLETE"
    wanted = payload_bytes(row)
    prefix = f"ACBEXP1|{row['sampleId']}|".encode()
    candidates = []
    for e in events:
        try:
            raw = base64.b64decode(e["payloadBase64"], validate=True)
        except (KeyError, ValueError):
            return "EVIDENCE_CONFLICT"
        # A related target transaction also exposes corruption of the sample header itself.
        if (
            raw == wanted
            or raw.startswith(prefix)
            or (
                row.get("targetTxHash")
                and norm(e.get("targetTxHash")) == norm(row["targetTxHash"])
            )
        ):
            candidates.append(e)
    # Multiple legitimate samples can share a target transaction; prefer the sample payload/header.
    exact = [
        e
        for e in candidates
        if base64.b64decode(e["payloadBase64"]) == wanted
        or base64.b64decode(e["payloadBase64"]).startswith(prefix)
    ]
    if exact:
        candidates = exact
    if not candidates:
        return "APP_MISSING"
    if len(candidates) != 1:
        return "APP_DUPLICATE"
    e = candidates[0]
    raw = base64.b64decode(e["payloadBase64"])
    if raw != wanted or hashlib.sha256(raw).hexdigest() != e.get("payloadSha256"):
        return "APP_PAYLOAD_MISMATCH"
    if e.get("sourceDomain") != m["sourceDomain"] or identity(
        e.get("author", "")
    ) != identity(m["sourceAppIdentity"]):
        return "APP_WRONG_SOURCE"
    if (
        identity(e.get("appIdentity", "")) != identity(m["targetAppIdentity"])
        or e.get("eventProfile") != m["eventProfile"]
    ):
        return "APP_WRONG_RECEIVER"
    if (
        e.get("targetProduct") != m["targetProduct"]
        or e.get("targetBlockchainId") != m["targetBlockchainId"]
    ):
        return "APP_WRONG_CHAIN"
    if e.get("isOrdered") is True or (
        m["eventProfile"] == "evm-app" and e.get("isOrdered") is not False
    ):
        return "APP_WRONG_ORDER"
    if (
        e.get("receiptSuccessful") is not True
        or e.get("finalized") is not True
        or not e.get("blockHash")
        or type(e.get("logIndex")) is not int
        or e["logIndex"] < 0
    ):
        return "EVIDENCE_INCOMPLETE"
    if (
        not int(m["targetStartHeight"])
        <= int(e["blockHeight"])
        <= int(coverage["endHeight"])
    ):
        return "EVIDENCE_CONFLICT"
    if row.get("targetTxHash") and norm(e["targetTxHash"]) != norm(row["targetTxHash"]):
        return "APP_TX_CONFLICT"
    return "OK"


def analyze(
    manifests, senders, metrics, receptions, coverages, completed=(), legacy=False
):
    diagnostics = []
    if legacy:
        rows = protocol_rows(senders, metrics, diagnostics)
        for r in rows:
            r.update(
                formal=False,
                legacyIntegrity=True,
                applicationStatus="LEGACY_UNVERIFIED",
                status="LEGACY_UNVERIFIED",
                dataValid=False,
            )
        for selector in (
            lambda r: r.get("sampleId"),
            lambda r: source_key(r) if r.get("sourceTxHashKey") else None,
            lambda r: r.get("ucpId"),
        ):
            groups = defaultdict(list)
            for r in rows:
                key = selector(r)
                if key:
                    groups[key].append(r)
            for group in groups.values():
                if len(group) > 1:
                    for r in group:
                        r["legacyIntegrity"] = False
                    diagnostics.append(diagnostic("LEGACY_DUPLICATE"))
        diagnostics.append(diagnostic("LEGACY_UNVERIFIED"))
        return rows, diagnostics
    plans, rounds = {}, set()
    for m in manifests:
        try:
            validate_manifest(m)
        except (ValueError, KeyError, TypeError) as e:
            diagnostics.append(
                diagnostic("INVALID_MANIFEST", m.get("runId", ""), detail=str(e))
            )
            continue
        logical = (m["experimentId"], m["scheme"], m["route"], m["round"])
        if m["runId"] in plans or logical in rounds:
            diagnostics.append(diagnostic("DUPLICATE_MANIFEST", m["runId"]))
        plans[m["runId"]] = m
        rounds.add(logical)
    by_sample = defaultdict(list)
    for sender in senders:
        by_sample[(sender.get("runId"), sender.get("sampleId"))].append(sender)
    prepared = []
    for run, m in plans.items():
        for expected in m["expectedSamples"]:
            group = by_sample.pop((run, expected["sampleId"]), [])
            row = dict(expected)
            row.update(
                {
                    k: m[k]
                    for k in (
                        "runId",
                        "experimentId",
                        "scheme",
                        "route",
                        "round",
                        "sourceProduct",
                        "sourceBlockchainId",
                    )
                }
            )
            row["dataValid"] = True
            if not group:
                row["sendStatus"] = "NOT_SENT"
                diagnostics.append(
                    diagnostic("MISSING_SENDER", run, expected["sampleId"])
                )
                row["dataValid"] = False
            else:
                row.update(group[0])
                if len(group) != 1:
                    diagnostics.append(
                        diagnostic("DUPLICATE_SAMPLE", run, expected["sampleId"])
                    )
                    row["dataValid"] = False
                for k, v in {
                    **expected,
                    **{
                        k: m[k]
                        for k in (
                            "runId",
                            "experimentId",
                            "scheme",
                            "route",
                            "round",
                            "sourceProduct",
                            "sourceBlockchainId",
                        )
                    },
                    "schemaVersion": 2,
                }.items():
                    if row.get(k) != v:
                        diagnostics.append(
                            diagnostic(
                                "SENDER_PLAN_CONFLICT", run, expected["sampleId"], k
                            )
                        )
                        row["dataValid"] = False
                try:
                    payload_bytes(row)
                    if row.get("sendStatus") == "SENT" and (
                        len(norm(row.get("sourceTxHashKey"))) != 64
                        or any(
                            c not in "0123456789abcdef"
                            for c in norm(row.get("sourceTxHashKey"))
                        )
                    ):
                        raise ValueError("invalid source hash")
                    if norm(row.get("sourceTxHashNative")) != norm(
                        row.get("sourceTxHashKey")
                    ):
                        raise ValueError("source hash representations conflict")
                except (ValueError, KeyError) as e:
                    diagnostics.append(
                        diagnostic("INVALID_SENDER", run, expected["sampleId"], str(e))
                    )
                    row["dataValid"] = False
            row["formal"] = m["formal"]
            prepared.append(row)
    for (run, sample), group in by_sample.items():
        diagnostics.append(diagnostic("EXTRA_SAMPLE", run or "", sample or ""))
        for sender in group:
            r = dict(sender)
            r["dataValid"] = False
            prepared.append(r)
    checked_metrics = []
    for e in metrics:
        if e.get("event") not in EVENTS:
            continue
        if not isinstance(e.get("eventTimeEpochMs"), int) or not e.get("ucpId"):
            diagnostics.append(diagnostic("INVALID_METRIC"))
        else:
            checked_metrics.append(e)
    rows = protocol_rows(prepared, checked_metrics, diagnostics)
    checked_by_ucp = defaultdict(list)
    for e in checked_metrics:
        checked_by_ucp[e["ucpId"]].append(e)
    for r in rows:
        m = plans.get(r.get("runId"))
        if not m or not r.get("ucpId"):
            continue
        for e in checked_by_ucp[r["ucpId"]]:
            fields = (
                ("sourceProduct", "sourceBlockchainId")
                if e["event"] == "SOURCE_ACCEPTED"
                else (
                    "sourceProduct",
                    "sourceBlockchainId",
                    "targetProduct",
                    "targetBlockchainId",
                )
            )
            if any(e.get(k) != m[k] for k in fields) or (
                e.get("sourceTxHashKey")
                and norm(e["sourceTxHashKey"]) != norm(r.get("sourceTxHashKey"))
            ):
                diagnostics.append(
                    diagnostic(
                        "METRIC_CHAIN_CONFLICT",
                        r.get("runId", ""),
                        r.get("sampleId", ""),
                    )
                )
                r["dataValid"] = False
    for key in (
        lambda r: source_key(r) if r.get("sourceTxHashKey") else None,
        lambda r: r.get("ucpId") or None,
    ):
        groups = defaultdict(list)
        for r in rows:
            k = key(r)
            if k:
                groups[k].append(r)
        for group in groups.values():
            if len(group) > 1:
                for r in group:
                    r["dataValid"] = False
                    diagnostics.append(
                        diagnostic(
                            "SHARED_TRANSACTION_OR_UCP",
                            r.get("runId", ""),
                            r.get("sampleId", ""),
                        )
                    )
    events_by_run = defaultdict(dict)
    for e in receptions:
        try:
            k = (
                e["targetProduct"],
                e["targetBlockchainId"],
                e["blockHash"],
                norm(e["targetTxHash"]),
                e["logIndex"],
            )
            previous = events_by_run[e["runId"]].get(k)
            if previous is not None and previous != e:
                diagnostics.append(diagnostic("EVENT_POSITION_CONFLICT", e["runId"]))
            else:
                events_by_run[e["runId"]][k] = e
        except KeyError:
            diagnostics.append(diagnostic("INVALID_RECEPTION", e.get("runId", "")))
    coverage_by_run = {}
    for c in coverages:
        run = c.get("runId")
        if run in coverage_by_run:
            diagnostics.append(diagnostic("DUPLICATE_COVERAGE", run))
        coverage_by_run[run] = c
    complete_ids = set()
    for c in completed:
        m = plans.get(c.get("runId"))
        if (
            not m
            or c.get("schemaVersion") != 2
            or c.get("samples") != m["samples"]
            or c.get("runId") in complete_ids
        ):
            diagnostics.append(diagnostic("INVALID_COMPLETION", c.get("runId", "")))
        else:
            complete_ids.add(c["runId"])
    for r in rows:
        run = r.get("runId")
        m = plans.get(run)
        if m is None:
            r["applicationStatus"] = "EXTRA_SAMPLE"
        else:
            if run not in complete_ids:
                diagnostics.append(
                    diagnostic("RUN_INTERRUPTED", run, r.get("sampleId", ""))
                )
                r["dataValid"] = False
            try:
                r["applicationStatus"] = reception_status(
                    r, m, list(events_by_run[run].values()), coverage_by_run.get(run)
                )
            except (ValueError, KeyError, TypeError):
                r["applicationStatus"] = "EVIDENCE_CONFLICT"
        r["status"] = (
            "OK"
            if r.get("dataValid")
            and r.get("protocolStatus") == "OK"
            and r["applicationStatus"] == "OK"
            else (
                "DATA_INVALID"
                if not r.get("dataValid")
                else r["protocolStatus"]
                if r["protocolStatus"] != "OK"
                else r["applicationStatus"]
            )
        )
    bad_runs = {d["runId"] for d in diagnostics}
    for r in rows:
        if "" in bad_runs or r.get("runId", "") in bad_runs:
            r["dataValid"] = False
            if r["status"] == "OK":
                r["status"] = "DATA_INVALID"
    for r in rows:
        if r.get("status") != "OK":
            diagnostics.append(
                diagnostic(
                    r.get("status", "INVALID_SAMPLE"),
                    r.get("runId", ""),
                    r.get("sampleId", ""),
                )
            )
    return rows, diagnostics


def percentile(values, fraction):
    if not values:
        return ""
    ordered = sorted(values)
    p = (len(ordered) - 1) * fraction
    lo, hi = math.floor(p), math.ceil(p)
    return ordered[lo] + (ordered[hi] - ordered[lo]) * (p - lo)


def summaries(rows, expected_samples=150):
    groups = defaultdict(list)
    for r in rows:
        groups[
            tuple(
                str(r.get(k, ""))
                for k in ("experimentId", "scheme", "route", "round", "runId")
            )
        ].append(r)
    result = []
    for key, group in sorted(groups.items()):
        protocol = [r for r in group if r.get("protocolStatus") == "OK"]
        ok = [r for r in group if r.get("status") == "OK"]
        s = dict(zip(("experimentId", "scheme", "route", "round", "runId"), key))
        s.update(
            expectedSamples=expected_samples,
            observedSamples=len(group),
            protocolSuccessfulSamples=len(protocol),
            applicationVerifiedSamples=sum(
                r.get("applicationStatus") == "OK" for r in group
            ),
            successfulSamples=len(ok),
            failedSamples=sum(
                r.get("protocolStatus") in {"SEND_FAILED", "TARGET_FAILED"}
                for r in group
            ),
            missingSamples=sum(
                str(r.get("protocolStatus", "")).startswith("MISSING")
                or r.get("protocolStatus") in {"NOT_SENT", "UNKNOWN"}
                for r in group
            ),
            invalidLatencySamples=sum(
                r.get("protocolStatus") == "NEGATIVE_OR_INCONSISTENT_LATENCY"
                for r in group
            ),
            lateSamples=sum(int(r.get("scheduleLatenessMs", 0)) > 200 for r in group),
        )
        s["protocolValidRun"] = (
            len(group) == len(protocol) == expected_samples
            and s["lateSamples"] == 0
            and all(
                r.get("dataValid")
                or (
                    r.get("applicationStatus") == "LEGACY_UNVERIFIED"
                    and r.get("legacyIntegrity", False)
                )
                for r in group
            )
        )
        s["validRun"] = (
            len(group) == len(ok) == expected_samples and s["lateSamples"] == 0
        )
        s["formalRun"] = (
            all(r.get("formal") is True for r in group) and expected_samples == 150
        )
        s["formalValidRun"] = s["validRun"] and s["formalRun"]
        for field in LATENCY_FIELDS:
            # Main-table statistics use fully verified samples. Protocol-only values remain explicit.
            for prefix, items in (("", ok), ("Protocol", protocol)):
                values = [float(r[field]) for r in items]
                s[f"{field}{prefix}MeanMs"] = statistics.mean(values) if values else ""
                s[f"{field}{prefix}StddevMs"] = (
                    statistics.stdev(values) if len(values) > 1 else 0 if values else ""
                )
                s[f"{field}{prefix}P50Ms"] = percentile(values, 0.5)
                s[f"{field}{prefix}P95Ms"] = percentile(values, 0.95)
        result.append(s)
    return result


def pooled_summaries(rows, rounds):
    result = []
    groups = defaultdict(list)
    for r in rounds:
        groups[tuple(r[k] for k in ("experimentId", "scheme", "route"))].append(r)
    for key, group in sorted(groups.items()):
        s = dict(zip(("experimentId", "scheme", "route"), key))
        valid = [r for r in group if r.get("formalValidRun", False)]
        s.update(
            expectedRounds=5,
            rounds=len(group),
            validRounds=len(valid),
            observedSamples=sum(r["observedSamples"] for r in group),
            successfulSamples=sum(r["successfulSamples"] for r in group),
            completeGroup=len(group) == 5
            and len(valid) == 5
            and {int(r["round"]) for r in valid} == set(range(1, 6)),
        )
        for field in LATENCY_FIELDS:
            means = [r[f"{field}MeanMs"] for r in valid]
            s[f"{field}MeanOfRoundMeansMs"] = statistics.mean(means) if means else ""
            values = [
                float(r[field])
                for r in rows
                if tuple(str(r.get(k, "")) for k in ("experimentId", "scheme", "route"))
                == key
                and r.get("status") == "OK"
            ]
            s[f"{field}PooledMeanMs"] = statistics.mean(values) if values else ""
            s[f"{field}PooledStddevMs"] = (
                statistics.stdev(values) if len(values) > 1 else 0 if values else ""
            )
            s[f"{field}PooledP50Ms"] = percentile(values, 0.50)
            s[f"{field}PooledP95Ms"] = percentile(values, 0.95)
        result.append(s)
    return result


def write_csv(path, rows):
    keys = list(dict.fromkeys(k for r in rows for k in r)) or ["code"]
    with path.open("x", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=keys)
        w.writeheader()
        w.writerows(rows)


def write_jsonl(path, rows):
    with path.open("x", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False, separators=(",", ":")) + "\n")


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    for name in (
        "sender",
        "relayer",
        "manifest",
        "receptions",
        "coverage",
        "completion",
    ):
        p.add_argument(
            "--" + name, type=Path, nargs="+", required=name in {"sender", "relayer"}
        )
    p.add_argument("--output-dir", type=Path, required=True)
    p.add_argument("--expected-samples", type=int, default=150)
    p.add_argument("--wait-seconds", type=float, default=0)
    p.add_argument("--poll-seconds", type=float, default=5)
    p.add_argument("--allow-legacy", action="store_true")
    args = p.parse_args(argv)
    if args.wait_seconds < 0 or args.poll_seconds <= 0:
        p.error("invalid wait/poll interval")
    if not args.allow_legacy and not all(
        (args.manifest, args.receptions, args.coverage, args.completion)
    ):
        p.error(
            "v2 requires --manifest --receptions --coverage --completion; historical import requires --allow-legacy"
        )
    args.output_dir.mkdir(mode=0o700, parents=False, exist_ok=False)
    input_errors = []
    deadline = time.monotonic() + args.wait_seconds
    while True:
        loaded = {}
        for kind in (
            "sender",
            "relayer",
            "manifest",
            "receptions",
            "coverage",
            "completion",
        ):
            loaded[kind] = []
            seen = set()
            for path in getattr(args, kind) or []:
                real = path.resolve()
                if real in seen:
                    input_errors.append(
                        diagnostic("DUPLICATE_INPUT_FILE", detail=f"{kind}: {path}")
                    )
                    continue
                seen.add(real)
                try:
                    loaded[kind].extend(
                        [json.loads(path.read_text())]
                        if kind in {"manifest", "coverage", "completion"}
                        else read_jsonl(path, kind == "relayer")
                    )
                except (ValueError, OSError) as e:
                    input_errors.append(
                        diagnostic(
                            "INVALID_INPUT_FILE",
                            detail=f"{kind}: {path}: {type(e).__name__}",
                        )
                    )
        rows, diags = analyze(
            loaded["manifest"],
            loaded["sender"],
            loaded["relayer"],
            loaded["receptions"],
            loaded["coverage"],
            loaded["completion"],
            args.allow_legacy,
        )
        if (
            input_errors
            or not rows
            or not any(
                str(r.get("protocolStatus", "")).startswith("MISSING_T") for r in rows
            )
            or time.monotonic() >= deadline
        ):
            break
        time.sleep(min(args.poll_seconds, max(0, deadline - time.monotonic())))
    diags = input_errors + diags
    if input_errors:
        for r in rows:
            r.update(status="DATA_INVALID", dataValid=False)
    rounds = summaries(rows, args.expected_samples)
    pooled = pooled_summaries(rows, rounds)
    for name in (
        "sender",
        "relayer",
        "manifest",
        "receptions",
        "coverage",
        "completion",
    ):
        write_jsonl(args.output_dir / (name + ".jsonl"), loaded[name])
    write_csv(args.output_dir / "transactions.csv", rows)
    write_csv(args.output_dir / "summary.csv", rounds)
    write_csv(args.output_dir / "pooled-summary.csv", pooled)
    write_csv(args.output_dir / "diagnostics.csv", diags)
    with (args.output_dir / "validation.json").open("x") as f:
        json.dump(
            dict(
                schemaVersion=2,
                valid=bool(rounds) and all(r["validRun"] for r in rounds),
                formalValid=bool(rounds)
                and all(r.get("formalValidRun", False) for r in rounds),
                errors=diags,
                expectedRoundsPerGroup=5,
                expectedTotalRounds=60,
                expectedTotalSamples=9000,
                observedTotalRounds=len(rounds),
                validTotalRounds=sum(r["validRun"] for r in rounds),
                completeExperiment=len(rounds) == 60
                and len({r["experimentId"] for r in rounds}) == 1
                and len(pooled) == 12
                and all(g["completeGroup"] for g in pooled),
            ),
            f,
            ensure_ascii=False,
            indent=2,
        )
    passed = bool(rounds) and all(r["validRun"] for r in rounds) and not diags
    if not passed:
        print(
            f"Validation incomplete/failed; inspect {args.output_dir / 'diagnostics.csv'}",
            file=sys.stderr,
        )
    else:
        print(
            f"Validated {len(rounds)} run(s), {len(rows)} sample(s); formalValid={all(r['formalValidRun'] for r in rounds)}"
        )
    return 0 if passed else 2


if __name__ == "__main__":
    sys.exit(main())
