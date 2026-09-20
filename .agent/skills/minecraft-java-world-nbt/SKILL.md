---
name: minecraft-java-world-nbt
description: "Safely inspect, analyze, diff, and make targeted edits to Minecraft Java Edition 1.21.8 NBT .dat files and Anvil .mca region files. Uses a probe-first, path-targeted workflow, preserves NBT types and unknown tags, understands region headers/compression/external chunks, and validates every write before delivery."
---

# Minecraft Java 1.21.8 World NBT Skill

Use this skill when the task involves **Minecraft Java Edition 1.21.8** world data such as `level.dat`, `playerdata/*.dat`, `data/*.dat`, `region/r.*.*.mca`, `entities/r.*.*.mca`, or `poi/r.*.*.mca`.

The core principle is:

> **probe -> narrow -> inspect -> edit one semantic target -> round-trip validate -> semantic diff**

Do not start by dumping an entire NBT tree or decompressing every chunk in every region.

## Tool

The bundled deterministic helper is:

```bash
python scripts/mcworld_nbt.py --help
```

It supports:

- NBT `.dat`: probe, tree, get, typed export, scalar/array/string edits, semantic diff
- Anvil `.mca`: header-only inventory, fail-closed `region-check`, targeted chunk NBT read/edit, semantic diff
- terrain regions: world-coordinate `block-get` and `block-set`
- region compression: GZIP, ZLIB/DEFLATE, uncompressed, and lz4-java `LZ4Block` when Python `lz4` is installed
- external chunk streams (`c.<x>.<z>.mcc`)

## Non-negotiable safety rules

1. **Never edit a live world.** The Minecraft server must be fully stopped before a write. A running server can overwrite the result or race the region write.
2. **Never overwrite the only copy.** All bundled write commands require a distinct output path.
3. **Do not mutate `DataVersion` just to make a file “look like 1.21.8”.** Observe and report it. Version conversion belongs to Minecraft's DataFixer pipeline, not manual integer replacement.
4. **Preserve exact NBT tag types.** A `TAG_Long` must not silently become `TAG_Int`; a byte boolean must not become an integer unless the user explicitly requests a schema change.
5. **Preserve unknown tags.** Modify only the requested node or block state. Do not rebuild a chunk from a hand-written schema.
6. **For `.mca`, keep the canonical filename** `r.<rx>.<rz>.mca`. Put the modified region in a separate directory instead of renaming it.
7. **Do not hand-edit packed `LongArray` block data.** Use `block-get` / `block-set`, which decodes and repacks the palette container.
8. **Treat `block-set` as a block-state-only operation.** It does not recalculate heightmaps, lighting, fluid/scheduled ticks, or block-entity NBT. Do not use it for chest/sign/spawner/command-block-style block-entity transformations or large terrain surgery unless all dependent data will be repaired by a version-matched world editor/server workflow.
9. **Do not create a missing chunk or missing section by improvisation.** Missing sections involve more than block-state data. Refuse or use Minecraft/world-editing software that can generate complete valid chunks.
10. **Validate after every write.** Re-open/parse, run a focused `get`, then run `diff` against the original.
11. Treat a region-header error, unknown compression id, missing `.mcc`, impossible array length, duplicate compound key, NaN write-safety refusal, or NBT parse failure as a corruption/unsupported-format signal. Stop rather than guessing.

## 1. Identify the file before reading deeply

### `.dat`

```bash
python scripts/mcworld_nbt.py probe /path/to/level.dat
```

This identifies compression, root type/name, hashes, and common version markers without producing a giant tree.

### `.mca`

```bash
python scripts/mcworld_nbt.py probe /path/to/r.0.-1.mca
python scripts/mcworld_nbt.py region-check /path/to/r.0.-1.mca
python scripts/mcworld_nbt.py region-list /path/to/r.0.-1.mca
```

`region-check` performs fail-closed structural validation of the whole region before any write. Add `--deep` to decompress and parse every present chunk NBT payload. `region-list` is header-first; add `--inspect-nbt` only when chunk metadata is actually needed:

```bash
python scripts/mcworld_nbt.py region-list /path/to/r.0.-1.mca --inspect-nbt
```

## 2. Resolve coordinates correctly

For a block coordinate `(x, y, z)`:

```text
chunk_x  = floor(x / 16)
chunk_z  = floor(z / 16)
region_x = floor(chunk_x / 32)
region_z = floor(chunk_z / 32)
```

Use mathematical floor for negatives. In Python, integer `//` already has the desired behavior.

Examples:

```text
block x = 0      -> chunk x = 0   -> region x = 0
block x = 511    -> chunk x = 31  -> region x = 0
block x = 512    -> chunk x = 32  -> region x = 1
block x = -1     -> chunk x = -1  -> region x = -1
block x = -512   -> chunk x = -32 -> region x = -1
block x = -513   -> chunk x = -33 -> region x = -2
```

Typical storage locations:

- `world/region/` - terrain chunk NBT, sections, block states, block entities, ticks, heightmaps, etc.
- `world/entities/` - entity chunk NBT
- `world/poi/` - point-of-interest chunk NBT
- `world/DIM-1/` - Nether equivalents
- `world/DIM1/` - End equivalents

Do not assume a file in `entities/` or `poi/` has terrain `sections`.

## 3. Inspect only the useful subtree

Start shallow:

```bash
python scripts/mcworld_nbt.py tree level.dat --depth 2
```

Then narrow:

```bash
python scripts/mcworld_nbt.py tree level.dat --path /Data --depth 2
python scripts/mcworld_nbt.py get level.dat /Data/DayTime
```

For a region chunk:

```bash
python scripts/mcworld_nbt.py tree r.0.0.mca --cx 3 --cz 7 --depth 2
python scripts/mcworld_nbt.py get r.0.0.mca --cx 3 --cz 7 /Status
```

Arrays are previewed by default. Avoid exporting `Heightmaps`, packed block-state `data`, or other large arrays unless the task specifically requires them.

Use full typed export only when the structure itself must be analyzed:

```bash
python scripts/mcworld_nbt.py export level.dat --out level.typed.json
python scripts/mcworld_nbt.py export r.0.0.mca --cx 3 --cz 7 --out chunk_3_7.typed.json
```

## 4. Make scalar edits with explicit types

Always inspect the existing type first:

```bash
python scripts/mcworld_nbt.py get level.dat /Data/DayTime
```

Then preserve it:

```bash
python scripts/mcworld_nbt.py set level.dat /Data/DayTime \
  --type long --value 6000 \
  --out edited/level.dat
```

Other examples:

```bash
python scripts/mcworld_nbt.py set level.dat /Data/raining \
  --type byte --value 0 \
  --out edited/level.dat

python scripts/mcworld_nbt.py set level.dat /Data/LevelName \
  --type string --value '"New World Name"' \
  --out edited/level.dat
```

`--value` is JSON. Strings therefore need JSON quotes.

Do not use `--allow-type-change` unless changing the NBT schema is intentional and justified.

Do not use `--create` until the exact expected tag and type are established from a trusted same-version structure.

## 5. Terrain block analysis/editing

For block-oriented tasks, use world coordinates directly. The helper resolves chunk/section coordinates, reads the block-state palette, and decodes the packed long array.

Inspect:

```bash
python scripts/mcworld_nbt.py block-get world/region/r.0.0.mca 100 64 200
```

Edit into a separate mirror directory while keeping the canonical region filename:

```bash
mkdir -p edited_world/region
python scripts/mcworld_nbt.py block-set world/region/r.0.0.mca 100 64 200 \
  --state '{"Name":"minecraft:stone"}' \
  --out edited_world/region/r.0.0.mca
```

`block-set` is **fail-closed on block-state validity**. Preferred validation uses Mojang's 1.21.8 generated `reports/blocks.json`:

```bash
python scripts/mcworld_nbt.py block-set world/region/r.0.0.mca 100 64 200 \
  --state '{"Name":"minecraft:oak_stairs","Properties":{"facing":"north","half":"bottom","shape":"straight","waterlogged":"false"}}' \
  --blocks-report /path/to/reports/blocks.json \
  --out edited_world/region/r.0.0.mca
```

Without `--blocks-report`, only an exact state already observed somewhere in the same region is accepted. `--allow-unvalidated-state` is an explicit unsafe expert bypass, not a normal workflow. See `references/BLOCK_VALIDATION_1_21_8.md`.

The helper deliberately refuses to invent a missing section. It refuses `block-set` when the target coordinate already contains `block_entities` NBT, and also refuses common target block types that normally require new block-entity NBT. `--allow-existing-block-entity` and `--allow-new-block-entity` are explicit expert overrides for workflows that are separately coordinating dependent NBT.

**Important consistency boundary:** `block-set` changes only the section's `block_states` palette/container. It does not update heightmaps, light arrays, scheduled block/fluid ticks, or `block_entities`. For a block with dependent data, use this command only as one controlled step in a broader, schema-aware repair.

### Block-state packed-array rule

For the 1.21.x section palette representation used here, block-state indices are treated as 4096 values in:

```text
index = (local_y << 8) | (local_z << 4) | local_x
```

The helper determines bits per value from palette size, decodes values without crossing `long` boundaries, and repacks after a palette addition. Do not duplicate this by manual bit arithmetic in the reasoning response when the helper can perform it deterministically.

## 6. `.mca` write behavior

A region has an 8 KiB header:

- first 4 KiB: 1024 chunk location entries
- second 4 KiB: 1024 timestamps
- data starts at sector 2
- sector size: 4096 bytes

The helper:

- preserves all untouched chunk bytes
- preserves untouched timestamps
- keeps the edited chunk's original compression codec
- reuses the old allocation when safe, otherwise allocates free sectors
- matches the 1.21.8 Paper/Spigot `255`-sector oversized calculation, including the exact-sector-boundary `+1` behavior
- performs a fail-closed whole-region preflight before calculating free space, rejecting malformed allocations, overlap, missing `.mcc` files, bad lengths, and unknown codecs
- reads/writes external `c.<chunkX>.<chunkZ>.mcc` payloads when necessary
- copies unmodified external sibling chunk files when the output region is staged in another directory
- verifies the modified NBT and re-validates the complete staged region before publishing the output file
- stages external `.mcc` payloads with fsync/rename and refuses unsafe force-replacement of an existing external-output pair

Do not write raw sector offsets yourself unless repairing a corrupt header is the explicit task.

## 7. Mandatory verification

After a `.dat` edit:

```bash
python scripts/mcworld_nbt.py get edited/level.dat /Data/DayTime
python scripts/mcworld_nbt.py diff level.dat edited/level.dat
```

After a chunk NBT edit:

```bash
python scripts/mcworld_nbt.py get edited/region/r.0.0.mca --cx 3 --cz 7 /SomePath
python scripts/mcworld_nbt.py diff region/r.0.0.mca edited/region/r.0.0.mca --cx 3 --cz 7
```

After a block edit:

```bash
python scripts/mcworld_nbt.py block-get edited_world/region/r.0.0.mca 100 64 200
```

The semantic diff should contain the requested change and no unexplained semantic changes.

## 8. Efficiency rules for AI analysis

Prefer this order:

1. `probe`
2. `region-check` for `.mca` before writes (`--deep` when corruption is suspected)
3. `region-list` for `.mca`
4. shallow `tree`
5. targeted `get`
6. edit
7. targeted `get` on output
8. `diff`

Avoid these expensive patterns unless required:

- full typed export of a large chunk
- decompressing all 1024 chunks to answer a one-coordinate question
- printing complete `LongArray` / `ByteArray` data
- decoding block palettes when only a chunk-level scalar is needed
- scanning unrelated dimensions/folders

When looking for an unknown tag, widen gradually: depth 2 -> depth 4 -> targeted export of one chunk. Do not export an entire world.

## 9. Version handling

This skill is scoped to **Java Edition 1.21.8**. The official 1.21.8 release was a hotfix release, but still treat file contents as authoritative rather than assuming a particular `DataVersion` integer.

If observed structures disagree with the expected 1.21.x layout:

1. report the observed `DataVersion` and relevant paths;
2. do not force-convert them;
3. determine whether the world was last saved in another version, a modded server, or a non-vanilla storage implementation;
4. fall back to NBT-level edits only where the actual structure is understood.

## 10. Output to the user

For modifications, return:

- the modified file(s), never only a pasted NBT dump
- a compact change summary: file, chunk/block coordinate if applicable, NBT path, old value/type, new value/type
- validation result
- any required companion `.mcc` file(s)

If editing one region from a world, preserve its relative directory role (`region/`, `entities/`, or `poi/`) in the deliverable so restoration is unambiguous.

## References bundled with this skill

Read `references/FORMAT_1_21_8.md` for implementation notes, `references/BLOCK_VALIDATION_1_21_8.md` for block-state validation, and `references/TEST_RESULTS_1_21_8.md` for the real-region regression record.

## Smoke test

Run after changing the helper:

```bash
python tests/smoke_test.py
```

A successful run prints:

```text
smoke_test: OK
```
