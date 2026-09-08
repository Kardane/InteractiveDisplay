# Lifecycle hardening notes

This branch intentionally removes the display entity pool and makes standalone window rebuilds transactional.

- Rebuilds now spawn the replacement window first and keep the current active window untouched when spawning fails.
- After a successful replacement, state is swapped before the previous runtime entities are destroyed.
- Component runtimes are destroyed immediately when a window is released instead of being hidden and retained for 100 ticks.
- The previous pool signature (`type:id`) was not a safe render fingerprint for changed item/block/map/text definitions.

The public debug `pooledEntityCount()` metric remains temporarily as a compatibility shim and reports `0`; it can be removed in a later command/debug cleanup without affecting runtime behavior.
