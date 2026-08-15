# Draba MC

This repository contains the source for the custom Minecraft mods and proxy plugins used by the Draba network. The projects target Minecraft 26.1.2, Fabric Loader, and Velocity.

## Projects

| Project | Platform | Purpose |
| --- | --- | --- |
| `mods/draba-hardcore` | Fabric | Hardcore cooldowns, controlled spectating, and contribution-based item ownership |
| `mods/draba-resources` | Fabric client | Managed resource-pack ordering and server-aware icons |
| `mods/draba-spectate` | Fabric | Safe player spectating and a compact network status interface |
| `mods/draba-welcome` | Fabric server | Welcome messages, player guide, changelog, and private todo tools |
| `mods/spectator-disclosure` | Fabric server | Permission-controlled watching with anonymous spectator counts |
| `plugins/draba-network-notices` | Velocity | Cross-server presence notices and status metadata |
| `plugins/relayline` | Velocity | Local-message support added to the RelayLine chat bridge |
| `forks/antixray-voxy-compat` | Fabric | AntiXray compatibility fixes for Voxy WorldGen |

## Building

Every Fabric project includes its own Gradle wrapper. Run `./gradlew clean build` from the project directory. Build artifacts are written to that project's `build/libs` directory.

The Velocity projects use a small shell build because they compile against the exact proxy API jar supplied by the operator. Set `VELOCITY_JAR` to an official Velocity jar before running `./build.sh`.

```bash
VELOCITY_JAR=/path/to/velocity.jar ./build.sh
```

Java 25 is required for the Minecraft 26.1.2 projects. The Velocity plugins currently target Java 21.

## Repository scope

This is a source repository. It intentionally excludes server deployment configuration, addresses, credentials, player data, logs, worlds, backups, and compiled artifacts. Hostnames in the published source use reserved example domains.

NeoEnchant and EnchantOnce source is not included because their licenses do not permit standalone republication. The AntiXray and RelayLine forks retain their upstream licenses and attribution.

## License

Original Draba code is provided for inspection under the repository license. Third-party forks remain subject to the license included in their own directory.
