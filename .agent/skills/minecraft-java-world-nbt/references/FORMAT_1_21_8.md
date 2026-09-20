# Minecraft Java 1.21.8 world-format implementation notes

This reference records the assumptions used by `scripts/mcworld_nbt.py`. It is not a license to invent missing schema fields: the skill must still inspect the actual file before editing.

## Version scope

Minecraft Java Edition 1.21.8 was released by Mojang on 2025-07-17. Mojang's release notes describe it as a hotfix focused on graphics corruption/freezing issues. The helper is intentionally version-scoped to 1.21.8, but it does **not** hard-code a DataVersion number; it reads version tags from the supplied NBT when present.

Primary source:

- Mojang Studios, “Minecraft Java Edition 1.21.8”: `https://www.minecraft.net/article/minecraft-java-edition-1-21-8`

## Region-file behavior checked against Paper 1.21.8

Paper's `ver/1.21.8` source patch for `RegionFileVersion` exposes four selectable region compression implementations:

- GZIP
- ZLIB / DEFLATE
- LZ4
- NONE

The helper maps the standard region compression IDs as:

```text
1 = gzip
2 = zlib/deflate
3 = uncompressed
4 = lz4-java LZ4Block stream
```

Paper's `RegionFile` patch on the same branch also shows:

- sector-count byte `255` requires reading the record's actual length to determine allocation size; the 1.21.8 Paper/Spigot calculation used here is integer `(length + 4) / 4096 + 1`, so an exact sector boundary intentionally reserves one additional sector;
- external chunk data is written to a separate external chunk file after the 5-byte region record prefix.

Primary source paths:

- PaperMC/Paper `ver/1.21.8`, `paper-server/patches/sources/net/minecraft/world/level/chunk/storage/RegionFile.java.patch`
- PaperMC/Paper `ver/1.21.8`, `paper-server/patches/sources/net/minecraft/world/level/chunk/storage/RegionFileVersion.java.patch`

## LZ4 codec

Minecraft/Paper region compression mode LZ4 uses the `lz4-java` block-stream form rather than the interoperable LZ4 Frame format. The format uses:

```text
magic: "LZ4Block" (8 bytes)
token: compression method + block-size level
compressed length: uint32 little endian
original length: uint32 little endian
checksum: xxHash32 little endian
payload
```

A zero-length block terminates the stream. The default checksum seed in lz4-java is `0x9747b28c`.

The helper implements this framing and uses Python `lz4.block` only for the inner LZ4 block codec.

Primary implementation source:

- lz4-java `LZ4BlockInputStream.java`
- lz4-java `LZ4BlockOutputStream.java`

## NBT strings

The helper implements Java modified UTF-8 semantics for NBT string fields so it can round-trip NULs and supplementary Unicode code points without silently changing their Java UTF representation.

## Terrain block-state editing

The helper expects the observed 1.21.x terrain chunk shape to include a root `sections` list, each section containing `Y` and `block_states`, with a `palette` and optional packed `data` long array. It does not fabricate this structure if absent.

Before any block edit, the helper verifies the structure in the actual target chunk. This is important because `entities/` and `poi/` region payloads use different schemas.

## Safety boundary

The helper is an offline binary editor, not a general DataFixer implementation. It should not be used to:

- upgrade/downgrade worlds by changing DataVersion;
- invent missing generated chunk metadata;
- repair lighting/heightmaps/ticks after large-scale arbitrary terrain generation;
- edit files while the server is running.

For those tasks, prefer a version-matched Minecraft server/DataFixer or a dedicated world editor, then use this skill for verification and targeted follow-up changes.

## v2 write preflight

Before any `.mca` mutation, `validate_region_for_write` scans every present location-table entry and fails closed on:

- offsets into the 8 KiB header;
- zero sector counts;
- allocations beyond EOF;
- overlapping live sector allocations;
- invalid chunk lengths;
- unknown region compression IDs;
- malformed external-chunk inline records;
- missing `c.<x>.<z>.mcc` files.

`region-check --deep` additionally decompresses and parses every present chunk NBT payload. The writer uses the validated allocation map when searching for free sectors; it never silently skips a malformed unrelated chunk.

## Block-state registry validation

Mojang's generated `reports/blocks.json` is the preferred validation source for `block-set`. The helper verifies the block ID, exact property keys, allowed property values, and enumerated state combination. When that report is not supplied, the offline fallback accepts only exact states already observed in the same region. See `BLOCK_VALIDATION_1_21_8.md`.
