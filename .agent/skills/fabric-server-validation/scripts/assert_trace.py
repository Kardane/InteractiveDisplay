#!/usr/bin/env python3
"""Evaluate focused declarative assertions against a JSONL validation trace."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

MISSING = object()


def get_path(record: dict[str, Any], path: str) -> Any:
    current: Any = record
    for part in path.split("."):
        if not isinstance(current, dict) or part not in current:
            return MISSING
        current = current[part]
    return current


def compare(actual: Any, expected: Any) -> bool:
    if isinstance(expected, dict) and len(expected) == 1:
        op, value = next(iter(expected.items()))
        if op == "exists":
            return (actual is not MISSING) is bool(value)
        if actual is MISSING:
            return False
        if op == "regex":
            return re.search(str(value), str(actual)) is not None
        if op == "in":
            return actual in value
        try:
            if op == "gte":
                return actual >= value
            if op == "lte":
                return actual <= value
            if op == "gt":
                return actual > value
            if op == "lt":
                return actual < value
        except TypeError:
            return False
    if actual is MISSING:
        return False
    return actual == expected


def matches(record: dict[str, Any], match: dict[str, Any]) -> bool:
    return all(compare(get_path(record, key), expected) for key, expected in match.items())


def count_ok(count: int, rule: dict[str, Any] | None) -> tuple[bool, str]:
    if rule is None:
        return count >= 1, ">=1"
    if "eq" in rule:
        expected = int(rule["eq"])
        return count == expected, f"=={expected}"
    minimum = int(rule.get("min", 0))
    maximum = rule.get("max")
    ok = count >= minimum and (maximum is None or count <= int(maximum))
    desc = f">={minimum}" + (f" and <={int(maximum)}" if maximum is not None else "")
    return ok, desc


def load_trace(path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8", errors="replace") as fh:
        for line_no, raw in enumerate(fh, 1):
            if not raw.strip():
                continue
            try:
                value = json.loads(raw)
            except json.JSONDecodeError as exc:
                raise ValueError(f"trace line {line_no}: {exc}") from exc
            if not isinstance(value, dict):
                raise ValueError(f"trace line {line_no}: expected JSON object")
            value.setdefault("_line", line_no)
            records.append(value)
    return records


def evaluate(records: list[dict[str, Any]], spec: dict[str, Any]) -> dict[str, Any]:
    results: list[dict[str, Any]] = []
    global_scope = spec.get("scope", {})
    if not isinstance(global_scope, dict):
        raise ValueError("scope must be an object")
    scoped_records = [r for r in records if matches(r, global_scope)]

    for kind in ("expect", "forbid"):
        entries = spec.get(kind, [])
        if not isinstance(entries, list):
            raise ValueError(f"{kind} must be a list")
        for index, entry in enumerate(entries):
            if not isinstance(entry, dict):
                raise ValueError(f"{kind}[{index}] must be an object")
            name = entry.get("name") or f"{kind}[{index}]"
            match = entry.get("match", {})
            if not isinstance(match, dict):
                raise ValueError(f"{name}: match must be an object")
            local_scope = entry.get("scope", {})
            if not isinstance(local_scope, dict):
                raise ValueError(f"{name}: scope must be an object")
            candidates = [r for r in scoped_records if matches(r, local_scope)]
            found = [r for r in candidates if matches(r, match)]
            if kind == "forbid":
                ok = len(found) == 0
                expectation = "==0"
            else:
                rule = entry.get("count")
                if rule is not None and not isinstance(rule, dict):
                    raise ValueError(f"{name}: count must be an object")
                ok, expectation = count_ok(len(found), rule)
            results.append({
                "name": name,
                "kind": kind,
                "pass": ok,
                "matched": len(found),
                "expected_count": expectation,
                "matched_seq": [r.get("seq") for r in found[:25]],
                "matched_lines": [r.get("_line") for r in found[:25]],
            })

    ordered = spec.get("ordered", [])
    if not isinstance(ordered, list):
        raise ValueError("ordered must be a list")
    for index, entry in enumerate(ordered):
        if not isinstance(entry, dict):
            raise ValueError(f"ordered[{index}] must be an object")
        name = entry.get("name") or f"ordered[{index}]"
        steps = entry.get("steps")
        if not isinstance(steps, list) or len(steps) < 2 or not all(isinstance(step, dict) for step in steps):
            raise ValueError(f"{name}: steps must be a list of at least two match objects")
        local_scope = entry.get("scope", {})
        if not isinstance(local_scope, dict):
            raise ValueError(f"{name}: scope must be an object")
        candidates = [r for r in scoped_records if matches(r, local_scope)]
        cursor = 0
        selected: list[dict[str, Any]] = []
        for step in steps:
            found_index = None
            for idx in range(cursor, len(candidates)):
                if matches(candidates[idx], step):
                    found_index = idx
                    selected.append(candidates[idx])
                    cursor = idx + 1
                    break
            if found_index is None:
                break
        ok = len(selected) == len(steps)
        max_ticks = entry.get("max_server_ticks")
        tick_span = None
        if ok and max_ticks is not None:
            first_tick = selected[0].get("server_tick")
            last_tick = selected[-1].get("server_tick")
            if isinstance(first_tick, (int, float)) and isinstance(last_tick, (int, float)):
                tick_span = last_tick - first_tick
                ok = tick_span <= max_ticks
            else:
                ok = False
        results.append({
            "name": name,
            "kind": "ordered",
            "pass": ok,
            "matched_steps": len(selected),
            "expected_steps": len(steps),
            "matched_seq": [r.get("seq") for r in selected],
            "matched_lines": [r.get("_line") for r in selected],
            "server_tick_span": tick_span,
            "max_server_ticks": max_ticks,
        })

    passed = all(r["pass"] for r in results)
    return {
        "verdict": "PASS" if passed else "FAIL",
        "assertions": results,
        "records": len(records),
        "scoped_records": len(scoped_records),
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("trace")
    ap.add_argument("spec")
    ap.add_argument("--output", help="Optional JSON report path")
    args = ap.parse_args()

    try:
        records = load_trace(Path(args.trace))
        spec = json.loads(Path(args.spec).read_text(encoding="utf-8"))
        if not isinstance(spec, dict):
            raise ValueError("assertion spec must be a JSON object")
        result = evaluate(records, spec)
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2

    text = json.dumps(result, indent=2, ensure_ascii=False)
    print(text)
    if args.output:
        out = Path(args.output)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text + "\n", encoding="utf-8")
    return 0 if result["verdict"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
