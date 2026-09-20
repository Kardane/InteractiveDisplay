#!/usr/bin/env python3
"""Summarize a Fabric validation JSONL packet trace without external dependencies."""

from __future__ import annotations

import argparse
import collections
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable


def load(path: Path) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    records: list[dict[str, Any]] = []
    errors: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8", errors="replace") as fh:
        for line_no, raw in enumerate(fh, 1):
            line = raw.strip()
            if not line:
                continue
            try:
                value = json.loads(line)
                if isinstance(value, dict):
                    value.setdefault("_line", line_no)
                    records.append(value)
                else:
                    errors.append({"line": line_no, "error": "not an object"})
            except json.JSONDecodeError as exc:
                errors.append({"line": line_no, "error": str(exc)})
    return records, errors


def keep(r: dict[str, Any], args: argparse.Namespace) -> bool:
    if args.test and r.get("test") != args.test:
        return False
    if args.connection and r.get("connection") != args.connection:
        return False
    if args.direction and r.get("direction") != args.direction:
        return False
    if args.phase and r.get("phase") != args.phase:
        return False
    if args.packet:
        name = str(r.get("packet", ""))
        payload = str(r.get("payload", ""))
        if not re.search(args.packet, name) and not re.search(args.packet, payload):
            return False
    tick = r.get("server_tick")
    if args.from_tick is not None and isinstance(tick, (int, float)) and tick < args.from_tick:
        return False
    if args.to_tick is not None and isinstance(tick, (int, float)) and tick > args.to_tick:
        return False
    return True


def summarize(records: Iterable[dict[str, Any]], sample: int) -> dict[str, Any]:
    rows = list(records)
    packet_rows = [r for r in rows if r.get("kind", "packet") == "packet"]
    landmarks = [r for r in rows if r.get("kind") == "landmark"]

    by_packet = collections.Counter(str(r.get("packet", "<unknown>")) for r in packet_rows)
    by_direction = collections.Counter(str(r.get("direction", "<unknown>")) for r in packet_rows)
    by_phase = collections.Counter(str(r.get("phase", "<unknown>")) for r in packet_rows)
    by_connection = collections.Counter(str(r.get("connection", "<unknown>")) for r in packet_rows)

    seqs = [r.get("seq") for r in rows if isinstance(r.get("seq"), int)]
    ticks = [r.get("server_tick") for r in rows if isinstance(r.get("server_tick"), (int, float))]

    def compact(r: dict[str, Any]) -> dict[str, Any]:
        keys = ["kind", "test", "seq", "server_tick", "client_tick", "direction", "phase", "connection", "packet", "payload", "name", "fields"]
        return {k: r[k] for k in keys if k in r}

    return {
        "records": len(rows),
        "packet_records": len(packet_rows),
        "landmarks": [compact(r) for r in landmarks[:50]],
        "sequence_range": [min(seqs), max(seqs)] if seqs else None,
        "server_tick_range": [min(ticks), max(ticks)] if ticks else None,
        "counts": {
            "direction": dict(by_direction),
            "phase": dict(by_phase),
            "connection": dict(by_connection),
            "packet": dict(by_packet.most_common()),
        },
        "sample_first": [compact(r) for r in rows[:sample]],
        "sample_last": [compact(r) for r in rows[-sample:]] if rows else [],
    }


def print_text(summary: dict[str, Any], malformed: list[dict[str, Any]]) -> None:
    print(f"records={summary['records']} packets={summary['packet_records']} malformed={len(malformed)}")
    if summary["sequence_range"]:
        print(f"seq={summary['sequence_range'][0]}..{summary['sequence_range'][1]}")
    if summary["server_tick_range"]:
        print(f"server_tick={summary['server_tick_range'][0]}..{summary['server_tick_range'][1]}")
    for label in ["direction", "phase", "connection"]:
        values = summary["counts"][label]
        if values:
            print(f"{label}: " + ", ".join(f"{k}={v}" for k, v in values.items()))
    print("packet counts:")
    for name, count in summary["counts"]["packet"].items():
        print(f"  {count:>5}  {name}")
    if summary["landmarks"]:
        print("landmarks:")
        for r in summary["landmarks"]:
            print("  " + json.dumps(r, ensure_ascii=False, sort_keys=True))
    if summary["sample_first"]:
        print("sample first:")
        for r in summary["sample_first"]:
            print("  " + json.dumps(r, ensure_ascii=False, sort_keys=True))
        print("sample last:")
        for r in summary["sample_last"]:
            print("  " + json.dumps(r, ensure_ascii=False, sort_keys=True))
    if malformed:
        print("malformed lines:", file=sys.stderr)
        for err in malformed[:20]:
            print("  " + json.dumps(err), file=sys.stderr)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("trace")
    ap.add_argument("--test")
    ap.add_argument("--connection")
    ap.add_argument("--direction", choices=["C2S", "S2C"])
    ap.add_argument("--phase")
    ap.add_argument("--packet", help="Regex matched against packet or payload")
    ap.add_argument("--from-tick", type=float)
    ap.add_argument("--to-tick", type=float)
    ap.add_argument("--around-seq", type=int, help="Keep records within --seq-radius of this sequence")
    ap.add_argument("--seq-radius", type=int, default=20)
    ap.add_argument("--around-landmark", help="Regex for a landmark name; scopes by its server tick")
    ap.add_argument("--ticks-before", type=float, default=2)
    ap.add_argument("--ticks-after", type=float, default=5)
    ap.add_argument("--sample", type=int, default=3)
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    path = Path(args.trace)
    if not path.exists():
        print(f"trace not found: {path}", file=sys.stderr)
        return 2
    records, malformed = load(path)

    if args.around_landmark:
        landmark = next((
            r for r in records
            if r.get("kind") == "landmark" and re.search(args.around_landmark, str(r.get("name", "")))
        ), None)
        if landmark is None:
            print(f"landmark not found: {args.around_landmark}", file=sys.stderr)
            return 2
        landmark_tick = landmark.get("server_tick")
        if not isinstance(landmark_tick, (int, float)):
            print("matched landmark has no numeric server_tick", file=sys.stderr)
            return 2
        args.from_tick = landmark_tick - args.ticks_before if args.from_tick is None else max(args.from_tick, landmark_tick - args.ticks_before)
        args.to_tick = landmark_tick + args.ticks_after if args.to_tick is None else min(args.to_tick, landmark_tick + args.ticks_after)

    filtered = [r for r in records if keep(r, args)]
    if args.around_seq is not None:
        radius = max(args.seq_radius, 0)
        filtered = [r for r in filtered if isinstance(r.get("seq"), int) and abs(r["seq"] - args.around_seq) <= radius]

    result = summarize(filtered, max(args.sample, 0))
    result["filters"] = {
        "test": args.test, "connection": args.connection, "direction": args.direction, "phase": args.phase,
        "packet": args.packet, "from_tick": args.from_tick, "to_tick": args.to_tick,
        "around_seq": args.around_seq, "seq_radius": args.seq_radius if args.around_seq is not None else None,
        "around_landmark": args.around_landmark,
    }
    result["malformed"] = malformed
    if args.json:
        print(json.dumps(result, indent=2, ensure_ascii=False))
    else:
        print_text(result, malformed)
    return 0 if not malformed else 1


if __name__ == "__main__":
    raise SystemExit(main())
