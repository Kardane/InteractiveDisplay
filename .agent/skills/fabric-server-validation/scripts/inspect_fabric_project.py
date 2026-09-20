#!/usr/bin/env python3
"""Best-effort, dependency-free inspection of a Fabric mod repository.

This script deliberately reports uncertainty instead of guessing. It does not modify the
repository. With --tasks it invokes the repository's Gradle wrapper only to list tasks.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

try:
    import tomllib  # Python 3.11+
except ModuleNotFoundError:  # pragma: no cover
    tomllib = None


VERSION_KEYS = {
    "minecraft": ["minecraft_version", "minecraft.version", "minecraft"],
    "loader": ["loader_version", "fabric_loader_version", "fabric-loader", "loader"],
    "fabric_api": ["fabric_version", "fabric_api_version", "fabric-api", "fabric_api"],
    "loom": ["loom_version", "fabric_loom_version", "fabric-loom", "loom"],
    "java": ["java_version", "java.version", "jvm_version"],
}


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def parse_properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in read_text(path).splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        if "=" in line:
            key, value = line.split("=", 1)
        elif ":" in line:
            key, value = line.split(":", 1)
        else:
            continue
        result[key.strip()] = value.strip()
    return result


def flatten_dict(value: Any, prefix: str = "") -> dict[str, Any]:
    out: dict[str, Any] = {}
    if isinstance(value, dict):
        for key, child in value.items():
            new_prefix = f"{prefix}.{key}" if prefix else str(key)
            out.update(flatten_dict(child, new_prefix))
    else:
        out[prefix] = value
    return out


def parse_version_catalog(path: Path) -> dict[str, Any]:
    if not path.exists() or tomllib is None:
        return {}
    try:
        with path.open("rb") as fh:
            return flatten_dict(tomllib.load(fh))
    except Exception:
        return {}


def resolve_from_maps(maps: list[tuple[str, dict[str, Any]]], aliases: list[str]) -> dict[str, Any] | None:
    normalized_aliases = {a.lower().replace("-", "_").replace(".", "_") for a in aliases}
    candidates: list[dict[str, Any]] = []
    for source, mapping in maps:
        for key, value in mapping.items():
            norm = key.lower().replace("-", "_").replace(".", "_")
            leaf = norm.split("_")[-1]
            exactish = norm in normalized_aliases or any(norm.endswith("_" + a) for a in normalized_aliases)
            if exactish and isinstance(value, (str, int, float)):
                candidates.append({"value": str(value), "source": source, "key": key, "confidence": "high"})
            elif any(a in norm for a in normalized_aliases) and isinstance(value, (str, int, float)):
                candidates.append({"value": str(value), "source": source, "key": key, "confidence": "medium"})
    if not candidates:
        return None
    candidates.sort(key=lambda c: (0 if c["confidence"] == "high" else 1, len(c["key"])))
    return candidates[0]


def regex_version_hints(files: list[Path]) -> dict[str, list[dict[str, str]]]:
    patterns = {
        "minecraft": [r"minecraft[^\n]{0,80}?[=:,(\s]\s*[\"']([^\"']+)[\"']"],
        "loader": [r"fabric-loader[^\n]{0,80}?[=:,(\s]\s*[\"']([^\"']+)[\"']"],
        "fabric_api": [r"fabric-api[^\n]{0,80}?[=:,(\s]\s*[\"']([^\"']+)[\"']"],
        "loom": [r"fabric-loom[^\n]{0,80}?(?:version\s*)?[=:,(\s]\s*[\"']([^\"']+)[\"']"],
    }
    result: dict[str, list[dict[str, str]]] = {k: [] for k in patterns}
    for path in files:
        text = read_text(path)
        for kind, regexes in patterns.items():
            for regex in regexes:
                for match in re.finditer(regex, text, flags=re.IGNORECASE):
                    value = match.group(1).strip()
                    if value and "$" not in value and "{" not in value:
                        result[kind].append({"value": value, "source": str(path), "confidence": "low"})
    return result


def source_kind(path: Path) -> str:
    parts = list(path.parts)
    try:
        src_index = len(parts) - 1 - parts[::-1].index("src")
    except ValueError:
        return "unknown"
    if src_index + 1 < len(parts):
        return parts[src_index + 1]
    return "unknown"


def parse_fabric_mod(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(read_text(path))
    except Exception as exc:
        return {"path": str(path), "source_kind": source_kind(path), "error": str(exc)}

    entrypoint_keys = sorted((data.get("entrypoints") or {}).keys())
    mixins = data.get("mixins", [])
    physical_server_reasons: list[str] = []
    if data.get("environment") == "server":
        physical_server_reasons.append("mod_environment_server")
    if "server" in entrypoint_keys:
        physical_server_reasons.append("dedicated_server_entrypoint")
    if isinstance(mixins, list) and any(
        isinstance(item, dict) and item.get("environment") == "server" for item in mixins
    ):
        physical_server_reasons.append("server_environment_mixin")

    return {
        "path": str(path),
        "source_kind": source_kind(path),
        "id": data.get("id"),
        "version": data.get("version"),
        "environment": data.get("environment"),
        "entrypoints": entrypoint_keys,
        "mixins": mixins,
        "depends": data.get("depends", {}),
        "physical_server_reasons": physical_server_reasons,
    }


def repo_files(root: Path, names: set[str] | None = None, suffix: str | None = None) -> list[Path]:
    """Find source/config files while ignoring generated/tool directories."""
    ignored = {".git", ".gradle", ".idea", ".vscode", "build", "out", "run", "runs", "node_modules"}
    found: list[Path] = []
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        try:
            rel = path.relative_to(root)
        except ValueError:
            continue
        if any(part in ignored for part in rel.parts[:-1]):
            continue
        if names is not None and path.name not in names:
            continue
        if suffix is not None and not path.name.endswith(suffix):
            continue
        found.append(path)
    return sorted(found)


def detect_mapping_style(build_text: str) -> dict[str, Any]:
    checks = [
        ("yarn", r"yarn|net\.fabricmc:yarn|mappings\s+.*yarn"),
        ("official_mojang", r"officialMojangMappings|mojangMappings|loom\.officialMojangMappings"),
        ("layered", r"layered\s*\{"),
    ]
    found = [name for name, pattern in checks if re.search(pattern, build_text, flags=re.IGNORECASE)]
    return {"detected": found, "confidence": "high" if len(found) == 1 else "mixed_or_unknown"}


def detect_test_setup(all_text: str, root: Path) -> dict[str, Any]:
    ignored = {".git", ".gradle", ".idea", ".vscode", "build", "out", "run", "runs", "node_modules"}
    source_dirs: dict[str, list[str]] = {}
    for source_set in ["test", "gametest", "clientTest", "testmod"]:
        matches: list[str] = []
        for path in root.rglob(source_set):
            if not path.is_dir() or path.parent.name != "src":
                continue
            rel = path.relative_to(root)
            if any(part in ignored for part in rel.parts):
                continue
            matches.append(str(rel))
        source_dirs[source_set] = sorted(matches)

    return {
        "fabric_loader_junit": bool(re.search(r"fabric-loader-junit", all_text, flags=re.IGNORECASE)),
        "configure_tests": bool(re.search(r"configureTests\s*[({]", all_text)),
        "server_gametest_markers": bool(re.search(r"fabric-gametest|@GameTest\b|GameTestHelper", all_text)),
        "client_gametest_markers": bool(re.search(r"fabric-client-gametest|FabricClientGameTest|ClientGameTestContext", all_text)),
        "server_production_run_task_markers": bool(re.search(r"ServerProductionRunTask", all_text)),
        "client_production_run_task_markers": bool(re.search(r"ClientProductionRunTask", all_text)),
        "production_client_gametest_markers": bool(
            re.search(r"ClientProductionRunTask", all_text)
            and re.search(r"fabric\.client\.gametest", all_text)
        ),
        "production_runtime_mods_markers": bool(re.search(r"productionRuntimeMods", all_text)),
        "eula_true_in_build": bool(re.search(r"\beula\s*=\s*true\b", all_text, flags=re.IGNORECASE)),
        "source_dirs": source_dirs,
    }


def run_cmd(cmd: list[str], cwd: Path, timeout: int = 60) -> dict[str, Any]:
    try:
        proc = subprocess.run(cmd, cwd=str(cwd), text=True, capture_output=True, timeout=timeout, check=False)
        return {
            "command": cmd,
            "returncode": proc.returncode,
            "stdout": proc.stdout[-200_000:],
            "stderr": proc.stderr[-50_000:],
        }
    except OSError as exc:
        return {"command": cmd, "error": str(exc)}
    except subprocess.TimeoutExpired as exc:
        return {"command": cmd, "error": f"timeout after {timeout}s", "stdout": (exc.stdout or "")[-20_000:]}



def gradle_command(root: Path, *args: str) -> list[str] | None:
    if os.name == "nt":
        wrapper = root / "gradlew.bat"
        if wrapper.exists():
            return ["cmd", "/c", str(wrapper), *args]
        return None
    wrapper = root / "gradlew"
    if not wrapper.exists():
        return None
    if os.access(wrapper, os.X_OK):
        return [str(wrapper), *args]
    return ["bash", str(wrapper), *args]


def extract_tasks(output: str) -> list[str]:
    tasks: set[str] = set()
    for line in output.splitlines():
        # Gradle task listing: taskName - description
        match = re.match(r"^([A-Za-z][A-Za-z0-9:_-]*)\s+-\s+", line.strip())
        if match:
            tasks.add(match.group(1))
    interesting = [t for t in tasks if re.search(r"test|game|server|client|production|prod|check|build", t, flags=re.IGNORECASE)]
    return sorted(interesting, key=str.lower)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".", help="Repository root (default: current directory)")
    parser.add_argument("--tasks", action="store_true", help="Invoke Gradle wrapper to list tasks")
    parser.add_argument("--probe-runtime", action="store_true", help="Probe java and Gradle versions")
    parser.add_argument("--output", help="Optional JSON output file; stdout is always printed")
    args = parser.parse_args()

    root = Path(args.repo).resolve()
    if not root.exists():
        print(json.dumps({"error": f"repo does not exist: {root}"}, indent=2))
        return 2

    discovered_files = repo_files(root)
    gradle_files = [p for p in discovered_files if p.name in {"build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"}]
    properties_files = [p for p in discovered_files if p.name == "gradle.properties"]
    catalog_files = [p for p in discovered_files if p.name == "libs.versions.toml"]

    maps: list[tuple[str, dict[str, Any]]] = []
    for path in properties_files:
        maps.append((str(path), parse_properties(path)))
    for path in catalog_files:
        maps.append((str(path), parse_version_catalog(path)))

    versions: dict[str, Any] = {}
    hints = regex_version_hints(gradle_files)
    for kind, aliases in VERSION_KEYS.items():
        resolved = resolve_from_maps(maps, aliases)
        if resolved:
            versions[kind] = resolved
        elif hints.get(kind):
            versions[kind] = hints[kind][0]
        else:
            versions[kind] = None

    fabric_mod_paths = [p for p in discovered_files if p.name == "fabric.mod.json" and source_kind(p) != "unknown"]
    mixin_paths = [p for p in discovered_files if p.suffix == ".json" and source_kind(p) != "unknown" and "mixin" in p.name.lower()]

    parsed_mods = [parse_fabric_mod(p) for p in fabric_mod_paths]
    production_mods = [m for m in parsed_mods if m.get("source_kind") == "main"]
    strict_server_mods = [m for m in production_mods if m.get("environment") == "server" and m.get("id")]
    physical_server_sensitive_mods = [m for m in production_mods if m.get("physical_server_reasons") and m.get("id")]

    all_scan_files = gradle_files + properties_files + catalog_files + fabric_mod_paths + mixin_paths
    all_text = "\n".join(read_text(p) for p in all_scan_files)

    # Test API markers live in source code, not build metadata. Scan test-like source sets only
    # to avoid loading the entire production codebase into this lightweight inspector.
    test_source_files = [
        p for p in discovered_files
        if p.suffix in {".java", ".kt"} and source_kind(p) in {"test", "gametest", "clientTest", "testmod"}
    ]
    test_source_text = "\n".join(read_text(p) for p in test_source_files)
    test_detection_text = all_text + "\n" + test_source_text

    dsl_kinds = sorted({"kotlin" if p.suffix == ".kts" else "groovy" for p in gradle_files if p.name.startswith("build.gradle")})
    build_dsl = dsl_kinds[0] if len(dsl_kinds) == 1 else ("mixed" if dsl_kinds else "unknown")

    report: dict[str, Any] = {
        "schema": 1,
        "repo": str(root),
        "build_dsl": build_dsl,
        "versions": versions,
        "mapping_style": detect_mapping_style("\n".join(read_text(p) for p in gradle_files)),
        "fabric_mods": parsed_mods,
        "mixin_configs": [str(p) for p in mixin_paths],
        "test_setup": detect_test_setup(test_detection_text, root),
        "derived": {
            "production_mod_ids": [m.get("id") for m in production_mods if m.get("id")],
            "server_only_mod_ids": [m.get("id") for m in strict_server_mods],
            "physical_server_sensitive_mods": [
                {"id": m.get("id"), "reasons": m.get("physical_server_reasons", [])}
                for m in physical_server_sensitive_mods
            ],
            "physical_server_verification_hint": (
                "loom_production_server_run_first"
                if physical_server_sensitive_mods
                else "not_required_by_detected_loader_metadata"
            ),
            "network_e2e_topology_hint": (
                "production_server_then_choose_required_client_fidelity_gate"
                if physical_server_sensitive_mods
                else "fabric_client_gametest_candidate_but_not_vanilla_proof"
            ),
            "network_e2e_topology_reason": (
                "production metadata contains physical-server-sensitive loading hooks; verify the physical production-server path with Loom production-run facilities first, then choose an unmodified-vanilla black-box gate or an instrumented split-process client according to the actual product contract"
                if physical_server_sensitive_mods
                else "no obvious physical-server-sensitive loader hooks detected; Fabric Client GameTest may provide client-state evidence when supported, but an explicit unmodified-vanilla compatibility contract still requires a separate black-box gate"
            ),
            "client_fidelity_contract_hint": "requirement_driven_not_inferred_from_repository",
            "vanilla_compatibility_hint": "fabric_client_gametest_production_fabric_client_and_protocol_bots_are_not_unmodified_vanilla_proof",
            "java_toolchain_hints": sorted(set(
                re.findall(r"JavaLanguageVersion\.of\((\d+)\)", all_text)
                + re.findall(r"sourceCompatibility\s*=\s*(?:JavaVersion\.VERSION_)?(\d+)", all_text)
            )),
        },
        "files_examined": [str(p) for p in all_scan_files],
        "test_source_files_examined": len(test_source_files),
    }

    if args.tasks:
        cmd = gradle_command(root, "tasks", "--all", "--console=plain")
        if cmd is not None:
            task_probe = run_cmd(cmd, root, timeout=120)
            report["gradle_tasks"] = extract_tasks(task_probe.get("stdout", ""))
            if "error" in task_probe or task_probe.get("returncode") != 0:
                report["gradle_task_probe"] = {
                    "error": task_probe.get("error"),
                    "returncode": task_probe.get("returncode"),
                    "stderr_tail": task_probe.get("stderr", "")[-5000:],
                }
        else:
            report["gradle_tasks"] = []
            report["gradle_task_probe"] = {"error": "Gradle wrapper not found"}

    if args.probe_runtime:
        report["runtime_probe"] = {
            "java": run_cmd(["java", "-version"], root, timeout=20),
        }
        cmd = gradle_command(root, "--version", "--console=plain")
        if cmd is not None:
            report["runtime_probe"]["gradle"] = run_cmd(cmd, root, timeout=60)

    encoded = json.dumps(report, indent=2, ensure_ascii=False)
    print(encoded)

    if args.output:
        output = Path(args.output)
        if not output.is_absolute():
            output = root / output
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(encoded + "\n", encoding="utf-8")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
