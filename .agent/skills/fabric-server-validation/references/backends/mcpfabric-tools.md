# MCP Fabric server-side tool map

MCP Fabric currently exposes common/dedicated-server-capable groups including:

- status: `get_status`, `list_capabilities`
- world reads: `get_block`, `get_blocks_region`, `find_blocks`, `get_time_and_weather`, `list_dimensions`, `raycast`
- world writes: `set_block`, `fill_blocks`, `set_time`, `set_weather`
- entities: `query_entities`, `get_entity`, `summon_entity`, `remove_entity`
- player administration: `list_players`, `get_player`, `teleport_player`, `set_gamemode`, `give_item`, `apply_effect`, `message_player`, `kick_player`
- commands: `run_command`
- chat/events: `send_chat`, `get_recent_chat`, `poll_events`

Tool availability is capability-dependent. Call status/capabilities instead of assuming all tools exist.

## Carpet bridge pattern

Use `run_command` for Carpet actions, then verify with structured tools.

```text
run_command("player CodexBot spawn")
run_command("player CodexBot look down")
run_command("player CodexBot use once")
get_player("CodexBot")
get_block(...)
```

The exact MCP call schema is provided by the connected MCP server at runtime. Follow that schema rather than inventing argument names from this reference.
