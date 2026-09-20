#!/usr/bin/env python3
"""Behavioral smoke tests for the bundled helper scripts."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path


def run(cmd: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(cmd, text=True, capture_output=True, check=False)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    scripts = Path(__file__).resolve().parent
    with tempfile.TemporaryDirectory(prefix="fabric-validation-skill-") as td:
        root = Path(td)
        (root / "src/main/resources").mkdir(parents=True)
        (root / "src/gametest/resources").mkdir(parents=True)
        (root / "src/gametest/java/example").mkdir(parents=True)
        (root / "serverentry/src/main/resources").mkdir(parents=True)
        (root / "gradle.properties").write_text(
            "minecraft_version=1.21.1\n"
            "loader_version=0.16.10\n"
            "fabric_version=0.115.0+1.21.1\n"
            "loom_version=1.9\n",
            encoding="utf-8",
        )
        (root / "build.gradle").write_text(
            "plugins { id 'fabric-loom' version loom_version }\n"
            "java { toolchain.languageVersion = JavaLanguageVersion.of(21) }\n"
            "dependencies { testImplementation \"net.fabricmc:fabric-loader-junit:${project.loader_version}\" }\n"
            "fabricApi { configureTests { createSourceSet = true; modId = 'fixture-tests' } }\n"
            "tasks.register('prodServerFixture', net.fabricmc.loom.task.prod.ServerProductionRunTask) { }\n"
            "tasks.register('prodClientGameTestFixture', net.fabricmc.loom.task.prod.ClientProductionRunTask) { jvmArgs.add('-Dfabric.client.gametest') }\n"
            "dependencies { productionRuntimeMods \"net.fabricmc.fabric-api:fabric-api:${project.fabric_version}\" }\n",
            encoding="utf-8",
        )
        (root / "src/main/resources/fabric.mod.json").write_text(
            json.dumps({"schemaVersion": 1, "id": "fixture", "version": "1", "environment": "server"}),
            encoding="utf-8",
        )
        # A server-only test mod must not be mistaken for the production deployment contract.
        (root / "src/gametest/resources/fabric.mod.json").write_text(
            json.dumps({"schemaVersion": 1, "id": "fixture-tests", "version": "1", "environment": "server"}),
            encoding="utf-8",
        )
        (root / "serverentry/src/main/resources/fabric.mod.json").write_text(
            json.dumps({
                "schemaVersion": 1, "id": "serverentry", "version": "1", "environment": "*",
                "entrypoints": {"server": ["example.ServerInit"]}
            }),
            encoding="utf-8",
        )
        (root / "src/gametest/java/example/ClientProbe.java").write_text(
            "class ClientProbe implements FabricClientGameTest {}\n", encoding="utf-8"
        )

        inspect = run([sys.executable, str(scripts / "inspect_fabric_project.py"), "--repo", str(root)])
        require(inspect.returncode == 0, inspect.stderr)
        env = json.loads(inspect.stdout)
        require(env["versions"]["minecraft"]["value"] == "1.21.1", "minecraft version detection")
        require(env["test_setup"]["fabric_loader_junit"] is True, "Fabric Loader JUnit detection")
        require(env["test_setup"]["configure_tests"] is True, "configureTests detection")
        require(env["test_setup"]["client_gametest_markers"] is True, "Client GameTest source marker detection")
        require(env["test_setup"]["server_production_run_task_markers"] is True, "ServerProductionRunTask detection")
        require(env["test_setup"]["client_production_run_task_markers"] is True, "ClientProductionRunTask detection")
        require(env["test_setup"]["production_client_gametest_markers"] is True, "production Client GameTest detection")
        require(env["test_setup"]["production_runtime_mods_markers"] is True, "productionRuntimeMods detection")
        require(env["derived"]["server_only_mod_ids"] == ["fixture"], "production server-only mod detection")
        physical = {m["id"]: m["reasons"] for m in env["derived"]["physical_server_sensitive_mods"]}
        require("mod_environment_server" in physical.get("fixture", []), "environment=server physical-side reason")
        require("dedicated_server_entrypoint" in physical.get("serverentry", []), "server entrypoint physical-side reason")
        require(env["derived"]["physical_server_verification_hint"] == "loom_production_server_run_first", "official production-server route selection")
        require(env["derived"]["network_e2e_topology_hint"] == "production_server_then_choose_required_client_fidelity_gate", "physical-side topology selection")
        require(env["derived"]["client_fidelity_contract_hint"] == "requirement_driven_not_inferred_from_repository", "client fidelity is requirement-driven")
        require("not_unmodified_vanilla_proof" in env["derived"]["vanilla_compatibility_hint"], "vanilla compatibility distinction")
        kinds = {m.get("id"): m.get("source_kind") for m in env["fabric_mods"]}
        require(kinds.get("fixture") == "main" and kinds.get("fixture-tests") == "gametest", "source-set classification")
        require("21" in env["derived"]["java_toolchain_hints"], "Java toolchain detection")

        trace = root / "trace.jsonl"
        trace.write_text(
            '\n'.join([
                json.dumps({"schema": 1, "kind": "landmark", "test": "sync", "seq": 1, "server_tick": 10, "name": "action"}),
                json.dumps({"schema": 1, "test": "sync", "seq": 2, "server_tick": 10, "direction": "S2C", "phase": "PLAY", "connection": "player", "packet": "TeleportPacket", "fields": {"x": 3}}),
                json.dumps({"schema": 1, "test": "sync", "seq": 3, "server_tick": 11, "direction": "C2S", "phase": "PLAY", "connection": "player", "packet": "MovePacket", "fields": {"x": 3}}),
            ]) + '\n',
            encoding="utf-8",
        )
        spec = root / "assertions.json"
        spec.write_text(json.dumps({
            "scope": {"test": "sync", "phase": "PLAY"},
            "expect": [{"name": "teleport", "match": {"direction": "S2C", "packet": {"regex": "Teleport"}, "fields.x": 3}, "count": {"eq": 1}}],
            "forbid": [{"name": "no observer", "match": {"connection": "observer"}}],
            "ordered": [{"name": "teleport before move", "steps": [{"packet": "TeleportPacket"}, {"packet": "MovePacket"}], "max_server_ticks": 1}],
        }), encoding="utf-8")

        summary = run([sys.executable, str(scripts / "summarize_trace.py"), str(trace), "--json"])
        require(summary.returncode == 0, summary.stderr)
        summary_json = json.loads(summary.stdout)
        require(summary_json["packet_records"] == 2, "trace packet count")
        landmark_summary = run([sys.executable, str(scripts / "summarize_trace.py"), str(trace), "--around-landmark", "action", "--ticks-before", "0", "--ticks-after", "0", "--json"])
        require(landmark_summary.returncode == 0, landmark_summary.stderr)
        require(json.loads(landmark_summary.stdout)["records"] == 2, "landmark window includes action tick records")

        assertion = run([sys.executable, str(scripts / "assert_trace.py"), str(trace), str(spec)])
        require(assertion.returncode == 0, assertion.stdout + assertion.stderr)
        require(json.loads(assertion.stdout)["verdict"] == "PASS", "trace assertion pass")

        failing_spec = root / "failing.json"
        failing_spec.write_text(json.dumps({"expect": [{"match": {"packet": "MissingPacket"}}]}), encoding="utf-8")
        failure = run([sys.executable, str(scripts / "assert_trace.py"), str(trace), str(failing_spec)])
        require(failure.returncode == 1, "trace assertion failure exit code")
        require(json.loads(failure.stdout)["verdict"] == "FAIL", "trace assertion fail verdict")

        fake_server = root / "fake_server.py"
        fake_server.write_text(
            "import os, pathlib, sys\n"
            "out=pathlib.Path(os.environ['FABRIC_VALIDATION_OUTPUT_DIR'])/'server-result.json'\n"
            "out.write_text('{\"verdict\":\"PASS\"}')\n"
            "print('SERVER READY', flush=True)\n"
            "print('MOD fixture loaded', flush=True)\n"
            "for line in sys.stdin:\n"
            "    if line.strip() == 'stop': raise SystemExit(0)\n",
            encoding="utf-8",
        )
        fake_client = root / "fake_client.py"
        fake_client.write_text(
            "import os, pathlib, json\n"
            "out=pathlib.Path(os.environ['FABRIC_VALIDATION_OUTPUT_DIR'])/'client-result.json'\n"
            "out.write_text(json.dumps({'pass': True}))\n",
            encoding="utf-8",
        )
        split = run([
            sys.executable, str(scripts / "run_split_e2e.py"),
            "--repo", str(root), "--scenario", "smoke",
            "--client-fidelity", "fabric-probe",
            "--server-cmd-json", json.dumps([sys.executable, str(fake_server)]),
            "--client-cmd-json", json.dumps([sys.executable, str(fake_client)]),
            "--server-ready-regex", "SERVER READY",
            "--server-loaded-regex", "MOD fixture loaded",
            "--require-pass-json", "{output_dir}/server-result.json",
            "--require-pass-json", "{output_dir}/client-result.json",
            "--startup-timeout", "5", "--client-timeout", "5", "--shutdown-timeout", "2",
        ])
        require(split.returncode == 0, split.stdout + split.stderr)
        split_json = json.loads(split.stdout)
        require(split_json["verdict"] == "PASS", "split-process E2E runner verdict")
        require(split_json["production_mod_loaded_marker"] is True, "split-process loaded marker")
        require(split_json["client_fidelity"] == "fabric-probe", "split-process client fidelity label")

        vanilla_missing = run([
            sys.executable, str(scripts / "run_split_e2e.py"),
            "--repo", str(root), "--scenario", "smoke-vanilla-missing",
            "--client-fidelity", "vanilla-black-box",
            "--server-cmd-json", json.dumps([sys.executable, str(fake_server)]),
            "--client-cmd-json", json.dumps([sys.executable, str(fake_client)]),
            "--server-ready-regex", "SERVER READY",
            "--server-loaded-regex", "MOD fixture loaded",
            "--require-pass-json", "{output_dir}/server-result.json",
            "--startup-timeout", "5", "--client-timeout", "5", "--shutdown-timeout", "2",
        ])
        require(vanilla_missing.returncode == 2, "vanilla black-box without proof must be blocked")
        vanilla_missing_json = json.loads(vanilla_missing.stdout)
        require(vanilla_missing_json["verdict"] == "BLOCKED", "vanilla missing proof verdict")

        fake_vanilla_harness = root / "fake_vanilla_harness.py"
        fake_vanilla_harness.write_text(
            "import os, pathlib, json\n"
            "out=pathlib.Path(os.environ['FABRIC_VALIDATION_OUTPUT_DIR'])/'vanilla-proof.json'\n"
            "out.write_text(json.dumps({'verdict': 'PASS', 'observation': 'synthetic-black-box-smoke'}))\n",
            encoding="utf-8",
        )
        vanilla_proved = run([
            sys.executable, str(scripts / "run_split_e2e.py"),
            "--repo", str(root), "--scenario", "smoke-vanilla-proof",
            "--client-fidelity", "vanilla-black-box",
            "--server-cmd-json", json.dumps([sys.executable, str(fake_server)]),
            "--client-cmd-json", json.dumps([sys.executable, str(fake_vanilla_harness)]),
            "--server-ready-regex", "SERVER READY",
            "--server-loaded-regex", "MOD fixture loaded",
            "--require-pass-json", "{output_dir}/server-result.json",
            "--client-proof-json", "{output_dir}/vanilla-proof.json",
            "--startup-timeout", "5", "--client-timeout", "5", "--shutdown-timeout", "2",
        ])
        require(vanilla_proved.returncode == 0, vanilla_proved.stdout + vanilla_proved.stderr)
        vanilla_proved_json = json.loads(vanilla_proved.stdout)
        require(vanilla_proved_json["verdict"] == "PASS", "vanilla black-box proof artifact pass")
        require(vanilla_proved_json["client_fidelity"] == "vanilla-black-box", "vanilla client fidelity label")

        if os.name != "nt":
            wrapper = root / "gradlew"
            wrapper.write_text(
                "#!/bin/sh\n"
                "echo fake-gradle task=$1\n"
                "if [ \"$1\" = \"failingTask\" ]; then echo BOOM; exit 7; fi\n"
                "exit 0\n",
                encoding="utf-8",
            )
            wrapper.chmod(0o755)
            runner = run([
                sys.executable, str(scripts / "run_gradle_validation.py"),
                "--repo", str(root), "--task", "passingTask", "--repeat", "2"
            ])
            require(runner.returncode == 0, runner.stdout + runner.stderr)
            runner_json = json.loads(runner.stdout)
            require(runner_json["verdict"] == "PASS" and len(runner_json["runs"]) == 2, "Gradle runner pass/repeat")

            runner_fail = run([
                sys.executable, str(scripts / "run_gradle_validation.py"),
                "--repo", str(root), "--task", "failingTask"
            ])
            require(runner_fail.returncode == 1, "Gradle runner failure exit code")
            runner_fail_json = json.loads(runner_fail.stdout)
            require(runner_fail_json["runs"][0]["returncode"] == 7, "Gradle runner preserves task exit code")
            require(any("BOOM" in line for line in runner_fail_json["runs"][0]["failure_tail"]), "Gradle runner failure tail")

    print("PASS: helper script smoke tests")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
