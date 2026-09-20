# v2 regression results on a real Minecraft Java 1.21.8 region

Test input supplied during development:

```text
r.0.0.mca
size: 4,243,456 bytes
SHA-256: 6b07dfc1a057b9f27fb7e39b3abfbe83d752a8d6085386ea78148296e6dd6ff6
```

Observed structure:

```text
present chunks: 1024 / 1024
compression: ZLIB (codec 2) for all chunks
external .mcc chunks: 0
DataVersion: 4440 for all inspected chunks
Status: minecraft:full for all inspected chunks
```

v2 validation results:

- full structural + deep NBT preflight: **PASS**, 1024 chunks;
- byte-exact NBT parse -> serialize round trip: **1024/1024**, mismatch 0;
- total raw NBT tested: **6,578,755 bytes**;
- actual multi-palette section decode -> repack checks: mismatch 0 in prior full palette scan;
- ordinary block edit `air -> stone`: **PASS**;
- edited region deep preflight after write: **PASS**, 1024 chunks;
- palette boundary test: **16 -> 17 entries**, packed data **256 -> 342 longs**, target block readback correct, full deep preflight PASS;
- nonexistent block ID: **rejected** with exit code 2;
- intentionally corrupted unrelated location-table entry: **write rejected before output publication**;
- 255-sector exact-boundary unit case: helper returns **257 sectors**, matching the Paper/Spigot 1.21.8 integer formula used by this skill.

The real region itself is not bundled with the skill.

## Remaining integration boundary

The deterministic binary/NBT and region tests above are complete. A final end-to-end test in which a modified region is loaded and re-saved by Mojang's 1.21.8 server requires the official server binary to be available in the execution environment. The skill therefore still requires normal operational discipline: keep backups and test modified output on a disposable world copy before production use.
