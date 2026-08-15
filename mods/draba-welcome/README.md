# Draba Welcome

Draba Welcome is a server-side Fabric mod for Minecraft 26.1.2. It provides the network welcome card, `/help`, `/guide`, `/changelog`, and a private UUID-owned `/todo` system.

Todo updates use atomic file replacement. The bundled changelog is only a safe example; server operators can maintain their own `config/draba-changelog.json` without rebuilding the mod.

## Build

```bash
./gradlew clean build
```

The build runs the guide and todo audits with the normal test suite.
