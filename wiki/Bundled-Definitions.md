# Bundled Definitions

A consumer mod can ship default InteractiveDisplay windows and groups inside its own JAR. This is the recommended way to distribute a ready-to-use UI without requiring server operators to create every file manually.

## JAR layout

For a mod with ID economy:

    data/economy/interactivedisplay/windows/shop.yaml
    data/economy/interactivedisplay/groups/shop_group.yaml

The resource path must use the owning mod's ID. The files are discovered during server initialization.

## Namespaced IDs

Every bundled definition must use the owning mod namespace:

    id: economy:shop

For groups, window references should also be namespaced:

    id: economy:shop_group
    initialWindowId: economy:shop
    windows:
      - windowId: economy:shop

Do not ship a bundled definition under the interactivedisplay namespace.

## Installation behavior

On startup, missing bundled files are copied to:

    config/interactivedisplay/windows/
    config/interactivedisplay/groups/

The target filename uses this pattern:

    <modid>__<original-filename>.yaml

The installer:

- copies only YAML files;
- never overwrites an existing target file;
- skips installation when an operator YAML already declares the same definition ID;
- treats the operator definition as authoritative;
- logs invalid namespace or installation errors as warnings.

This means a server operator can edit the installed file safely, but a later consumer-mod update will not automatically overwrite that edit.

## Recommended update strategy

Use stable IDs and treat the installed YAML as operator-owned after first startup.

If a schema change is required:

1. document the migration;
2. choose a new filename only if the definition ID also needs to change;
3. provide a compatibility path for existing operator files;
4. never rely on automatic overwrite for upgrades.

## MAP assets

Bundled MAP assets are not part of API v1. A consumer mod that needs a MAP window must currently use the supported operator configuration and image path behavior rather than assuming that arbitrary MAP assets inside the consumer JAR will be installed automatically.
