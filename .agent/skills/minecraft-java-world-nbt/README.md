# Minecraft Java 1.21.8 World NBT Skill

A reusable AI skill for efficient, conservative inspection and targeted modification of Minecraft Java Edition 1.21.8 `.dat` NBT and `.mca` Anvil region files.

## Contents

- `SKILL.md` - AI workflow and safety rules
- `scripts/mcworld_nbt.py` - deterministic NBT/region helper
- `references/FORMAT_1_21_8.md` - format/implementation notes
- `references/BLOCK_VALIDATION_1_21_8.md` - block ID/property validation workflow
- `references/TEST_RESULTS_1_21_8.md` - real 1.21.8 region regression results
- `tests/smoke_test.py` - synthetic round-trip, corruption-guard, validator, and edit smoke tests
- `tests/real_region_regression.py` - non-destructive regression suite for a real `r.x.z.mca`

## Quick check

```bash
python tests/smoke_test.py
python tests/real_region_regression.py /path/to/r.0.0.mca
```

## Important

Only edit an offline copy of a world. Keep original backups.

## v2 safety changes

- fail-closed whole-region preflight before `.mca` writes;
- corrected Paper/Spigot 1.21.8 oversized `255`-sector boundary handling;
- block ID/property validation using Mojang generated `reports/blocks.json`, with conservative region-observed fallback;
- guards against creating common block-entity-backed blocks with a block-state-only edit;
- full staged-region validation before publication.

Run a deep region check with:

```bash
python scripts/mcworld_nbt.py region-check world/region/r.0.0.mca --deep
```
