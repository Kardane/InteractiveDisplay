---
name: mcdev-source-analysis
description: Use mcdev-mcp static analysis for Minecraft Java 1.21.8 to locate classes, methods, fields, inheritance, callers, callees, and implementation details before editing Fabric code or choosing Mixin targets. Use when Minecraft internals, mappings, lifecycle ordering, call paths, signatures, or version-specific behavior are uncertain, especially before guessing from older tutorials or another Minecraft version.
---

# mcdev Source Analysis

## Role in the merged skill set

Source-analysis helper used by $minecraft-fabric-server-dev and $fabric-server-validation. It may still be invoked directly when the user explicitly asks for this tool/lane, but it does not replace the top-level implementation or validation policy.


Use source evidence to reduce compile-debug guessing.

## Procedure

1. Ensure mcdev-mcp is initialized/selected for Minecraft `1.21.8` unless the repository explicitly targets another version.
2. Search by concept/symbol with `mc_search`.
3. Retrieve the smallest useful unit with `mc_get_method` or `mc_get_class`.
4. Use `mc_find_hierarchy` when interface/subclass ownership matters.
5. Use `mc_find_refs` when choosing an injection point or understanding who calls a method and what it calls.
6. Translate findings into the repository's mapping namespace if necessary, then verify by compilation.

Read `references/query-playbook.md` for efficient query patterns.

## Mixin discipline

Before adding or moving a Mixin injection:

- identify the actual 1.21.8 target class and descriptor/signature,
- inspect the surrounding method body,
- inspect relevant callers/callees when lifecycle ordering matters,
- prefer an existing stable semantic hook over a brittle ordinal/local capture,
- compile and run the relevant GameTest afterward.

Do not use “this was named X in 1.21.1” as evidence for 1.21.8.

## Runtime boundary

mcdev-mcp's static source/call-graph functions are the primary purpose of this skill. Do not assume an optional runtime bridge is compatible with the project's exact 1.21.8 setup. For runtime verification, delegate route selection to `fabric-server-validation`, which may choose GameTest, MCP Fabric + Carpet, Mineflayer, or a higher-fidelity gate according to the contract.

## Context economy

Do not dump whole Minecraft classes into the work log when a method or small surrounding section answers the question. Record the symbol and conclusion needed for the implementation.
