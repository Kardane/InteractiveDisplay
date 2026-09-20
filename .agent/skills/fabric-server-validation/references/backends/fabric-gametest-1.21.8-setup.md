# Fabric 1.21.8 GameTest setup reference

The Fabric docs 1.21.8 reference build uses Minecraft `1.21.8`, Fabric API `0.134.0+1.21.8`, Java 21-era tooling, and a Loom test configuration of this shape:

```gradle
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "your-mod-test"
        eula = true // only set when the developer has agreed to the Minecraft EULA
    }
}
```

Do not blindly pin the Fabric API version above if the repository already has a compatible/newer 1.21.8 version. Preserve the project's version catalog/properties.

With a separate source set, use:

```text
src/gametest/java/...
src/gametest/resources/fabric.mod.json
```

A minimal test mod resource pattern is:

```json
{
  "schemaVersion": 1,
  "id": "your-mod-test",
  "version": "1.0.0",
  "name": "Your Mod Tests",
  "environment": "*",
  "entrypoints": {
    "fabric-gametest": ["your.package.YourGameTests"]
  }
}
```

Current Fabric docs show server tests using `net.fabricmc.fabric.api.gametest.v1.GameTest` with `GameTestHelper`. Exact imports/method names must match the project's 1.21.8 mappings and Fabric API.

Server GameTests configured by Fabric/Loom can be run as part of the normal Gradle `build`; inspect actual tasks rather than assuming a custom task name.
