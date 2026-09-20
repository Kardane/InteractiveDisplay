# Mineflayer harness setup

If the repository has no Node harness yet, create a small isolated directory such as `e2e/mineflayer/` and initialize it with the project's preferred package manager. Minimal dependency:

```bash
npm install --save-dev mineflayer
```

Run the bundled smoke template after copying/adapting it into that harness:

```bash
MC_HOST=127.0.0.1 MC_PORT=25565 MC_VERSION=1.21.8 node mineflayer-smoke.mjs
```

For a local offline-mode test server, a generated username is normally sufficient. If the test server requires Microsoft authentication, do not hard-code credentials or tokens into source control; use an existing secure test-auth setup.

Prefer one process per scenario or explicit bot cleanup so retries begin from a known connection state.
