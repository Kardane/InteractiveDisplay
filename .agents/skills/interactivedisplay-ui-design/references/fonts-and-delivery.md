# Pretendard fonts and applying changes

> **Created**: 2026-10-08 KST  
> **Last updated**: 2026-10-08 KST  
> **Modified by**: Codex, OpenAI  
> **Version**: 1.0.0

## Select a weight

The pack contains nine unmodified Pretendard v1.3.9 static TTF weights:

| Weight | Name | Font ID |
| --- | --- | --- |
| 100 | Thin | `interactivedisplay:ui_thin` |
| 200 | ExtraLight | `interactivedisplay:ui_extralight` |
| 300 | Light | `interactivedisplay:ui_light` |
| 400 | Regular | `interactivedisplay:ui_regular` |
| 500 | Medium | `interactivedisplay:ui_medium` |
| 600 | SemiBold | `interactivedisplay:ui_semibold` |
| 700 | Bold | `interactivedisplay:ui_bold` |
| 800 | ExtraBold | `interactivedisplay:ui_extrabold` |
| 900 | Black | `interactivedisplay:ui_black` |

Plain text and text inputs default to Regular; button labels default to Medium. Text components preserve explicit non-default JSON font styles. For example:

```yaml
content: '{"text":"가나다 Aa 123","font":"interactivedisplay:ui_semibold"}'
fontSize: 0.40
shadow: false
```

Button `label` is literal text, so JSON in a label does not select its font. There is no arbitrary variable-axis or per-button weight field. Use `font_showcase` to compare static weights at the same scale. The license is bundled at `assets/interactivedisplay/font/license-pretendard.txt`; retain it with redistributed fonts.

## Keep text sharp and paths valid

Every provider has `size: 10.0` and `oversample: 8.0`. `size` affects metrics; `oversample` increases raster resolution without changing the nominal size. Higher sampling uses more glyph texture memory; do not raise it indefinitely. Actual projection, display scaling, and client settings still affect sharpness.

Resource identifiers must have lowercase paths. The TTF loader automatically prefixes `font/`, so the provider file is `interactivedisplay:pretendard-regular.ttf`, while the physical file is `assets/interactivedisplay/font/pretendard-regular.ttf`. Do not write `interactivedisplay:font/pretendard-regular.ttf`. Font ID `interactivedisplay:ui_regular` resolves to `assets/interactivedisplay/font/ui_regular.json`.

Spaces and unsupported glyphs fall back to the Minecraft default font. Geometry-only background text retains vanilla metrics; never apply a global `minecraft:default` font override to sharpen this UI, because backgrounds use space advances to size their surfaces.

## Apply the correct layer

| Changed surface | Application step |
| --- | --- |
| Existing runtime window YAML | Validate it, reload its ID, then reopen the window. |
| New runtime YAML | Validate it, use the full reload to discover it, then create/open it. |
| Shipped default YAML | Rebuild for distribution; update authorized existing operator YAML explicitly. Defaults only copy when missing. |
| Font JSON/TTF or Java | Build the JAR, back up the installed mod, normally stop/save the verified server, replace that mod JAR, and restart. Reconnect and accept the new pack. YAML reload alone cannot replace loaded mod resources. |

Do not restart merely to preview YAML. A resource/JAR update may require a restart within the user's authorized scope. Use the verified server's normal console `stop` command and wait for world saves and port release. Do not kill arbitrary Java processes, enable RCON, change authentication/access lists, or replace worlds to obtain a preview. Server-start instructions are in `run/start.ps1` when that local server exists. Do not install this checkout's JAR into a different server without confirming its path and versions.

After starting, check `run/logs/latest.log` for `Done`, InteractiveDisplay runtime readiness, and successful Polymer pack generation. Inspect `run/polymer/resource_pack.zip` for the expected font providers, lowercase TTF paths, and license. JAR and installed-copy SHA-256 hashes should match. Pack presence alone does not prove client loading.

## Diagnose what the client sees

- **All characters are squares:** read the current client `logs/latest.log` after pack reload. Search for `Unable to load font`, `Invalid path`, `Not a valid resource location`, and `FileNotFoundException`. Check lowercase identifiers and the implicit `font/` prefix first. A rejected provider can also prevent its fallback from loading.
- **Only some characters are squares:** check glyph coverage and the reference to `minecraft:default`; verify that the intended provider and TTF are delivered.
- **Old pixels or old font after an update:** verify the running server's installed JAR, the regenerated pack, and the client log's new server-pack reload. Reconnect/accept the pack rather than deleting unrelated client files.
- **Text is blocky:** check the delivered provider's oversampling before enlarging layout bounds. Use the same screenshot conditions. Client font loading and actual rendered sharpness are separate evidence.
- **Button wraps or becomes vertically misaligned:** inspect `ButtonBoxModel`, resolved content width, padding, and the actual glyphs. Server estimates are not exact font measurements.

Client log locations depend on the launcher and instance. Find the active instance instead of hardcoding a previous user's PrismLauncher path. The skill's YAML validator does not execute the client font engine. For resource changes, use GameTest pack checks plus client loading/log or a targeted Minecraft TTF-loader probe, and state when no screenshot was observed.
