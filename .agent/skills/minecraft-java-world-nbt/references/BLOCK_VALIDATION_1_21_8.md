# Minecraft Java 1.21.8 block-state validation

`block-set` is fail-closed by default. It will not write an arbitrary `Name`/`Properties` pair merely because the NBT shape is syntactically valid.

## Preferred source: Mojang generated `reports/blocks.json`

Use the **official Minecraft Java 1.21.8 server JAR** from Mojang's 1.21.8 release page. Mojang's bundled server supports running the data generator with a different main class:

```bash
java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports
```

Use the generated `reports/blocks.json` as the validator input:

```bash
python scripts/mcworld_nbt.py block-set world/region/r.0.0.mca 100 64 200 \
  --state '{"Name":"minecraft:oak_stairs","Properties":{"facing":"north","half":"bottom","shape":"straight","waterlogged":"false"}}' \
  --blocks-report /path/to/reports/blocks.json \
  --out edited_world/region/r.0.0.mca
```

The validator checks:

- block ID exists;
- property names exactly match the registered state schema;
- each property value is allowed;
- when the report provides enumerated states, the exact property combination exists.

The helper also accepts the PrismarineJS 1.21.8 `blocks.json` array as a secondary interoperability source, but the Mojang-generated report is preferred.

## Offline fallback

If `--blocks-report` is omitted, the helper accepts only an **exact block state already observed somewhere in the same region file**. This is intentionally conservative: it prevents typo IDs and invented property combinations without needing network access, but it can reject a legitimate block state that simply does not occur in that region.

Example:

```bash
python scripts/mcworld_nbt.py block-set world/region/r.0.0.mca 100 64 200 \
  --state '{"Name":"minecraft:stone"}' \
  --out edited_world/region/r.0.0.mca
```

If `minecraft:stone` with that exact property set is present elsewhere in the region, validation succeeds with `state_validation: "region-observed"`.

## Unsafe bypass

`--allow-unvalidated-state` disables the registry/observed-state gate. It exists for expert recovery workflows only. Do not use it as the normal path.

## Block entities

`block-set` edits only `sections[].block_states`. It therefore rejects common block types that normally require block-entity NBT (chests, signs, shulker boxes, command blocks, spawners, etc.) unless `--allow-new-block-entity` is explicitly supplied. Existing block-entity coordinates are separately rejected unless `--allow-existing-block-entity` is supplied.

These overrides do **not** create or repair block-entity NBT. The caller must coordinate the dependent data separately.
