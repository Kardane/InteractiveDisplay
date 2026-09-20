#!/usr/bin/env python3
"""Run selected Gradle validation tasks while keeping full logs out of agent context.

The script prints a compact JSON summary and writes one log per invocation. It never
chooses tasks by itself; callers must first inspect the project and provide exact tasks.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def gradle_prefix(root: Path) -> list[str] | None:
    if os.name == "nt":
        wrapper = root / "gradlew.bat"
        return ["cmd", "/c", str(wrapper)] if wrapper.exists() else None
    wrapper = root / "gradlew"
    if not wrapper.exists():
        return None
    if os.access(wrapper, os.X_OK):
        return [str(wrapper)]
    return ["bash", str(wrapper)]


def slug(value: str) -> str:
    value = re.sub(r"[^A-Za-z0-9_.-]+", "_", value).strip("_")
    return value[:100] or "task"


def tail(path: Path, lines: int = 40) -> list[str]:
    try:
        content = path.read_text(encoding="utf-8", errors="replace").splitlines()
        return content[-lines:]
    except OSError:
        return []


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=".")
    ap.add_argument("--task", action="append", required=True, help="Exact Gradle task; repeat for multiple tasks")
    ap.add_argument("--gradle-arg", action="append", default=[], help="Extra Gradle argument; repeat as needed")
    ap.add_argument("--repeat", type=int, default=1, help="Repeat each task, useful for flake checks")
    ap.add_argument("--continue-on-fail", action="store_true")
    ap.add_argument("--output-dir", default="build/validation/runs")
    ap.add_argument("--failure-tail", type=int, default=40)
    ap.add_argument("--timeout", type=int, default=0, help="Per-run timeout seconds; 0 means no timeout")
    args = ap.parse_args()

    if args.repeat < 1:
        print("--repeat must be >= 1", file=sys.stderr)
        return 2

    root = Path(args.repo).resolve()
    prefix = gradle_prefix(root)
    if prefix is None:
        print(f"Gradle wrapper not found under {root}", file=sys.stderr)
        return 2

    out_dir = Path(args.output_dir)
    if not out_dir.is_absolute():
        out_dir = root / out_dir
    out_dir.mkdir(parents=True, exist_ok=True)

    summary: dict[str, Any] = {
        "schema": 1,
        "repo": str(root),
        "started_at": datetime.now(timezone.utc).isoformat(),
        "runs": [],
    }

    stop = False
    for task in args.task:
        if stop:
            break
        for attempt in range(1, args.repeat + 1):
            run_id = f"{slug(task)}-{attempt}"
            log_path = out_dir / f"{run_id}.log"
            cmd = [*prefix, task, "--console=plain", *args.gradle_arg]
            start = time.monotonic()
            timed_out = False
            try:
                with log_path.open("w", encoding="utf-8") as log:
                    proc = subprocess.run(
                        cmd,
                        cwd=str(root),
                        stdout=log,
                        stderr=subprocess.STDOUT,
                        text=True,
                        timeout=(args.timeout or None),
                        check=False,
                    )
                returncode = proc.returncode
            except subprocess.TimeoutExpired:
                timed_out = True
                returncode = 124
                with log_path.open("a", encoding="utf-8") as log:
                    log.write(f"\n[validation-runner] timeout after {args.timeout}s\n")
            except OSError as exc:
                returncode = 127
                with log_path.open("a", encoding="utf-8") as log:
                    log.write(f"\n[validation-runner] failed to start: {exc}\n")

            elapsed = round(time.monotonic() - start, 3)
            passed = returncode == 0
            entry = {
                "task": task,
                "attempt": attempt,
                "returncode": returncode,
                "pass": passed,
                "timed_out": timed_out,
                "duration_seconds": elapsed,
                "log": str(log_path.relative_to(root)) if log_path.is_relative_to(root) else str(log_path),
            }
            if not passed:
                entry["failure_tail"] = tail(log_path, max(args.failure_tail, 0))
            summary["runs"].append(entry)

            if not passed and not args.continue_on_fail:
                stop = True
                break

    summary["finished_at"] = datetime.now(timezone.utc).isoformat()
    summary["verdict"] = "PASS" if summary["runs"] and all(r["pass"] for r in summary["runs"]) else "FAIL"
    summary["passed"] = sum(1 for r in summary["runs"] if r["pass"])
    summary["failed"] = sum(1 for r in summary["runs"] if not r["pass"])

    manifest = out_dir / "summary.json"
    manifest.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2, ensure_ascii=False))
    return 0 if summary["verdict"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
