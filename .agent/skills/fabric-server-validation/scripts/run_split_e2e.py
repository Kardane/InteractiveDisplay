#!/usr/bin/env python3
"""Coordinate a local split-process Minecraft validation without using a shell.

Commands are JSON arrays so argument boundaries are explicit. The runner substitutes
{port}, {repo}, {output_dir}, and {scenario} in each argument and also exports
FABRIC_VALIDATION_* environment variables. It never accepts the EULA or changes repo files.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import signal
import socket
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


SENSITIVE_FLAG = re.compile(r"(?i)(access.?token|auth(?:orization)?|password|passwd|secret|session.?token|api.?key)")


def sanitize_command(cmd: list[str]) -> list[str]:
    out: list[str] = []
    redact_next = False
    for arg in cmd:
        if redact_next:
            out.append("<redacted>")
            redact_next = False
            continue
        if SENSITIVE_FLAG.search(arg):
            if "=" in arg:
                key = arg.split("=", 1)[0]
                out.append(key + "=<redacted>")
            else:
                out.append(arg)
                redact_next = True
        else:
            out.append(arg)
    return out


def sanitize_log_line(line: str) -> str:
    line = re.sub(r"(?i)(authorization\s*[:=]\s*bearer\s+)[^\s]+", r"\1<redacted>", line)
    line = re.sub(r"(?i)((?:access.?token|password|secret|session.?token|api.?key)\s*[:=]\s*)[^\s,;]+", r"\1<redacted>", line)
    return line


def parse_command(raw: str, label: str) -> list[str]:
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise ValueError(f"{label} must be a JSON array: {exc}") from exc
    if not isinstance(value, list) or not value or not all(isinstance(x, str) and x for x in value):
        raise ValueError(f"{label} must be a non-empty JSON array of non-empty strings")
    return value


def reserve_port(port: int) -> int:
    if port:
        return port
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def expand_value(value: str, replacements: dict[str, str]) -> str:
    # Replace only documented placeholders; leave unrelated braces untouched.
    for key, replacement in replacements.items():
        value = value.replace("{" + key + "}", replacement)
    return value


def expand(cmd: list[str], values: dict[str, str]) -> list[str]:
    return [expand_value(arg, values) for arg in cmd]


def read_tail(path: Path, max_bytes: int = 200_000) -> str:
    try:
        with path.open("rb") as fh:
            fh.seek(0, os.SEEK_END)
            size = fh.tell()
            fh.seek(max(0, size - max_bytes))
            return fh.read().decode("utf-8", errors="replace")
    except OSError:
        return ""


def wait_for_server(
    proc: subprocess.Popen[str],
    log_path: Path,
    ready_re: re.Pattern[str],
    loaded_re: re.Pattern[str] | None,
    timeout: float,
) -> tuple[bool, bool, str]:
    deadline = time.monotonic() + timeout
    ready = False
    loaded = loaded_re is None
    while time.monotonic() < deadline:
        text = read_tail(log_path)
        ready = ready or ready_re.search(text) is not None
        loaded = loaded or (loaded_re is not None and loaded_re.search(text) is not None)
        if ready and loaded:
            return True, True, "ready"
        rc = proc.poll()
        if rc is not None:
            return ready, loaded, f"server exited before readiness with code {rc}"
        time.sleep(0.1)
    return ready, loaded, "startup timeout"


def terminate_process(proc: subprocess.Popen[str], shutdown_timeout: float) -> tuple[int | None, bool]:
    """Try console stop, then terminate only this process/group. Returns (code, forced)."""
    if proc.poll() is not None:
        return proc.returncode, False

    try:
        if proc.stdin:
            proc.stdin.write("stop\n")
            proc.stdin.flush()
        proc.wait(timeout=shutdown_timeout)
        return proc.returncode, False
    except (BrokenPipeError, OSError, subprocess.TimeoutExpired):
        pass

    forced = True
    try:
        if os.name == "nt":
            subprocess.run(
                ["taskkill", "/PID", str(proc.pid), "/T", "/F"],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False, timeout=5,
            )
        else:
            os.killpg(proc.pid, signal.SIGTERM)
        proc.wait(timeout=max(1.0, min(shutdown_timeout, 5.0)))
        return proc.returncode, forced
    except (OSError, subprocess.TimeoutExpired):
        try:
            if os.name == "nt":
                proc.kill()
            else:
                os.killpg(proc.pid, signal.SIGKILL)
            proc.wait(timeout=3)
        except (OSError, subprocess.TimeoutExpired):
            pass
        return proc.poll(), forced


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=".")
    ap.add_argument("--scenario", default="split-e2e")
    ap.add_argument(
        "--client-fidelity",
        choices=["fabric-probe", "protocol-bot", "vanilla-black-box", "other-instrumented"],
        default="other-instrumented",
        help="Label the client that actually runs; this is evidence metadata, not an automatic compatibility claim.",
    )
    ap.add_argument("--server-cmd-json", required=True, help='JSON argv, e.g. ["./gradlew","runServer"]')
    ap.add_argument("--client-cmd-json", required=True, help="JSON argv for the separate client/probe process")
    ap.add_argument("--server-ready-regex", required=True, help="Semantic server-ready log marker")
    ap.add_argument("--server-loaded-regex", help="Optional log marker proving the production mod loaded")
    ap.add_argument("--port", type=int, default=0, help="Loopback port; 0 selects a currently free local port")
    ap.add_argument("--output-dir", default="build/validation/e2e")
    ap.add_argument("--startup-timeout", type=float, default=120.0)
    ap.add_argument("--client-timeout", type=float, default=180.0)
    ap.add_argument("--shutdown-timeout", type=float, default=15.0)
    ap.add_argument(
        "--require-pass-json", action="append", default=[],
        help="General result JSON path template; each must contain verdict=PASS or pass=true. Repeat as needed.",
    )
    ap.add_argument(
        "--client-proof-json", action="append", default=[],
        help="Client-fidelity proof JSON path template. Required for vanilla-black-box PASS; each must contain verdict=PASS or pass=true.",
    )
    args = ap.parse_args()

    if not (0 <= args.port <= 65535):
        print("--port must be 0..65535", file=sys.stderr)
        return 2
    if min(args.startup_timeout, args.client_timeout, args.shutdown_timeout) <= 0:
        print("timeouts must be > 0", file=sys.stderr)
        return 2

    try:
        server_raw = parse_command(args.server_cmd_json, "--server-cmd-json")
        client_raw = parse_command(args.client_cmd_json, "--client-cmd-json")
        ready_re = re.compile(args.server_ready_regex)
        loaded_re = re.compile(args.server_loaded_regex) if args.server_loaded_regex else None
    except (ValueError, re.error) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2

    root = Path(args.repo).resolve()
    if not root.is_dir():
        print(f"repo not found: {root}", file=sys.stderr)
        return 2

    port = reserve_port(args.port)
    out_root = Path(args.output_dir)
    if not out_root.is_absolute():
        out_root = root / out_root
    scenario_dir = out_root / re.sub(r"[^A-Za-z0-9_.-]+", "_", args.scenario).strip("_")
    scenario_dir.mkdir(parents=True, exist_ok=True)

    values = {
        "port": str(port),
        "repo": str(root),
        "output_dir": str(scenario_dir),
        "scenario": args.scenario,
    }
    try:
        server_cmd = expand(server_raw, values)
        client_cmd = expand(client_raw, values)
    except (KeyError, ValueError) as exc:
        print(f"command placeholder error: {exc}", file=sys.stderr)
        return 2

    env = os.environ.copy()
    env.update({
        "FABRIC_VALIDATION_HOST": "127.0.0.1",
        "FABRIC_VALIDATION_PORT": str(port),
        "FABRIC_VALIDATION_SCENARIO": args.scenario,
        "FABRIC_VALIDATION_OUTPUT_DIR": str(scenario_dir),
    })

    server_log = scenario_dir / "server.log"
    client_log = scenario_dir / "client.log"
    started = datetime.now(timezone.utc).isoformat()
    server_proc: subprocess.Popen[str] | None = None
    result: dict[str, Any] = {
        "schema": 1,
        "scenario": args.scenario,
        "client_fidelity": args.client_fidelity,
        "host": "127.0.0.1",
        "port": port,
        "started_at": started,
        "server_command": sanitize_command(server_cmd),
        "client_command": sanitize_command(client_cmd),
        "client_fidelity_note": (
            "unmodified vanilla must be proven by the external black-box harness; a raw client process exit code alone is not a vanilla compatibility assertion"
            if args.client_fidelity == "vanilla-black-box"
            else "instrumented/protocol client evidence must not be relabeled as unmodified vanilla"
        ),
        "server_log": str(server_log),
        "client_log": str(client_log),
    }

    try:
        with server_log.open("w", encoding="utf-8") as slog:
            popen_kwargs: dict[str, Any] = {
                "cwd": str(root),
                "env": env,
                "stdin": subprocess.PIPE,
                "stdout": slog,
                "stderr": subprocess.STDOUT,
                "text": True,
            }
            if os.name == "nt":
                popen_kwargs["creationflags"] = subprocess.CREATE_NEW_PROCESS_GROUP
            else:
                popen_kwargs["start_new_session"] = True
            server_proc = subprocess.Popen(server_cmd, **popen_kwargs)

            ready, loaded, startup_detail = wait_for_server(
                server_proc, server_log, ready_re, loaded_re, args.startup_timeout
            )
            result.update({"server_ready": ready, "production_mod_loaded_marker": loaded, "startup_detail": startup_detail})

            if not ready or not loaded:
                result["verdict"] = "BLOCKED"
            else:
                with client_log.open("w", encoding="utf-8") as clog:
                    client_kwargs: dict[str, Any] = {
                        "cwd": str(root),
                        "env": env,
                        "stdin": subprocess.DEVNULL,
                        "stdout": clog,
                        "stderr": subprocess.STDOUT,
                        "text": True,
                    }
                    if os.name == "nt":
                        client_kwargs["creationflags"] = subprocess.CREATE_NEW_PROCESS_GROUP
                    else:
                        client_kwargs["start_new_session"] = True
                    try:
                        client_proc = subprocess.Popen(client_cmd, **client_kwargs)
                        client_proc.wait(timeout=args.client_timeout)
                        result["client_returncode"] = client_proc.returncode
                        result["client_timed_out"] = False
                    except subprocess.TimeoutExpired:
                        terminate_process(client_proc, max(1.0, min(args.shutdown_timeout, 5.0)))
                        result["client_returncode"] = 124
                        result["client_timed_out"] = True
                        clog.write(f"\n[split-e2e] client timeout after {args.client_timeout}s\n")
                result["verdict"] = "PASS" if result["client_returncode"] == 0 else "FAIL"

                required_results: list[dict[str, Any]] = []
                for raw_path in args.require_pass_json:
                    expanded_path = expand_value(raw_path, values)
                    result_path = Path(expanded_path)
                    if not result_path.is_absolute():
                        result_path = root / result_path
                    entry: dict[str, Any] = {"path": str(result_path), "pass": False}
                    try:
                        payload = json.loads(result_path.read_text(encoding="utf-8"))
                        if not isinstance(payload, dict):
                            raise ValueError("expected JSON object")
                        entry["pass"] = payload.get("verdict") == "PASS" or payload.get("pass") is True
                        entry["reported_verdict"] = payload.get("verdict", payload.get("pass"))
                    except (OSError, json.JSONDecodeError, ValueError) as exc:
                        entry["error"] = str(exc)
                    required_results.append(entry)
                if required_results:
                    result["required_results"] = required_results
                    if not all(entry["pass"] for entry in required_results):
                        result["verdict"] = "FAIL"

                client_proofs: list[dict[str, Any]] = []
                for raw_path in args.client_proof_json:
                    expanded_path = expand_value(raw_path, values)
                    proof_path = Path(expanded_path)
                    if not proof_path.is_absolute():
                        proof_path = root / proof_path
                    entry: dict[str, Any] = {"path": str(proof_path), "pass": False}
                    try:
                        payload = json.loads(proof_path.read_text(encoding="utf-8"))
                        if not isinstance(payload, dict):
                            raise ValueError("expected JSON object")
                        entry["pass"] = payload.get("verdict") == "PASS" or payload.get("pass") is True
                        entry["reported_verdict"] = payload.get("verdict", payload.get("pass"))
                    except (OSError, json.JSONDecodeError, ValueError) as exc:
                        entry["error"] = str(exc)
                    client_proofs.append(entry)

                if client_proofs:
                    result["client_proofs"] = client_proofs
                    if not all(entry["pass"] for entry in client_proofs):
                        explicit_fail = any(entry.get("reported_verdict") in {"FAIL", False} for entry in client_proofs)
                        result["verdict"] = "FAIL" if explicit_fail else "BLOCKED"

                if args.client_fidelity == "vanilla-black-box" and not client_proofs:
                    result["vanilla_black_box_proof"] = "missing external black-box client proof; raw client process exit is not sufficient"
                    if result.get("verdict") == "PASS":
                        result["verdict"] = "BLOCKED"
    except (OSError, KeyError, ValueError) as exc:
        result["verdict"] = "BLOCKED"
        result["orchestration_error"] = str(exc)
    finally:
        if server_proc is not None:
            code, forced = terminate_process(server_proc, args.shutdown_timeout)
            result["server_returncode_after_cleanup"] = code
            result["cleanup_forced"] = forced
        result["finished_at"] = datetime.now(timezone.utc).isoformat()
        if result.get("verdict") != "PASS":
            result["server_log_tail"] = [sanitize_log_line(x) for x in read_tail(server_log, 20_000).splitlines()[-40:]]
            result["client_log_tail"] = [sanitize_log_line(x) for x in read_tail(client_log, 20_000).splitlines()[-40:]]
        result_path = scenario_dir / "orchestrator-result.json"
        result["result_file"] = str(result_path)
        result_path.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0 if result.get("verdict") == "PASS" else (1 if result.get("verdict") == "FAIL" else 2)


if __name__ == "__main__":
    raise SystemExit(main())
