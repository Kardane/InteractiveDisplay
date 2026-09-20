#!/usr/bin/env python3
"""Lightweight integrity check for this skill package."""

from __future__ import annotations

import re
import sys
from pathlib import Path


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    skill = root / "SKILL.md"
    errors: list[str] = []

    if not skill.exists():
        errors.append("missing SKILL.md")
    else:
        text = skill.read_text(encoding="utf-8")
        if not text.startswith("---\n"):
            errors.append("SKILL.md missing YAML front matter")
        if not re.search(r"^name:\s*\S+", text, flags=re.MULTILINE):
            errors.append("SKILL.md missing name")
        if not re.search(r"^description:\s*.+", text, flags=re.MULTILINE):
            errors.append("SKILL.md missing description")

    for rel in [
        "references/validation-ladder.md",
        "references/fabric-test-recipes.md",
        "references/production-run-validation.md",
        "references/vanilla-compatibility-e2e.md",
        "references/split-process-e2e.md",
        "references/diagnostic-playbooks.md",
        "references/integration-fixture-matrix.md",
        "references/packet-tracing.md",
        "references/trace-schema.md",
        "references/source-notes.md",
        "assets/trace-assertions.example.json",
        "assets/trigger-evals.json",
    ]:
        if not (root / rel).exists():
            errors.append(f"missing {rel}")

    for script in (root / "scripts").glob("*.py"):
        try:
            compile(script.read_text(encoding="utf-8"), str(script), "exec")
        except (OSError, SyntaxError) as exc:
            errors.append(f"syntax error in {script.name}: {exc}")

    if errors:
        for error in errors:
            print(f"FAIL: {error}")
        return 1
    print("PASS: skill structure and Python syntax")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
