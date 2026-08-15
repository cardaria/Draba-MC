# Draba Network Notices

Draba Network Notices is a Velocity plugin that mirrors initial join and final leave messages to players on the other backend. It also replaces technical backend names in disconnect screens and supplies the pause menu with player names and transient join times.

Backend chat remains responsible for local messages, which prevents duplicate notices. Survival and Hardcore notices use distinct badges.

## Build

Provide an official Velocity jar through `VELOCITY_JAR`, then run the build script.

```bash
VELOCITY_JAR=/path/to/velocity.jar ./build.sh
```

The script compiles the plugin, runs its assertions, and writes the release jar to `build/libs`.
