# mcdev-mcp query playbook

Expected static tools include:

- `mc_version` — select/set target Minecraft source version when required by the server
- `mc_search` — locate symbols by name/concept
- `mc_get_class` — retrieve a class source
- `mc_get_method` — retrieve a method with focused context
- `mc_find_hierarchy` — inspect subtype/interface relationships
- `mc_find_refs` — inspect callers/callees/reference graph
- package/class listing tools when discovery is broader

The connected MCP server defines the exact argument schema. Follow the runtime tool schema rather than fabricating parameters.

## Efficient sequences

### Unknown Mixin target

```text
mc_search(concept/symbol)
→ mc_get_method(candidate)
→ mc_find_refs(candidate, callers/callees)
→ choose semantic injection point
→ compile
→ GameTest
```

### Method renamed across versions

```text
set/select 1.21.8
→ search by class/concept
→ inspect implementation and signature
→ translate to repository mapping namespace if needed
```

### Lifecycle bug

```text
get suspected method
→ callers
→ callees
→ identify state mutation order
→ write regression GameTest that observes the externally relevant state
```
