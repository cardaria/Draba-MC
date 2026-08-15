# Spectator Disclosure

Spectator Disclosure is a server-side companion for Draba Spectate and AdminScope. It supplies `/watch`, restores the watcher's original state, and shows the watched player an anonymous spectator count without revealing identities.

The permission node is `drabax.watch`. Operators are allowed by default. Secret Spectator can be installed separately to hide active watchers from the normal player list.

## Build

Build the sibling `draba-spectate` project first, then build this project.

```bash
../draba-spectate/gradlew -p ../draba-spectate clean build
./gradlew clean build
```
