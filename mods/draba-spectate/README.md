# Draba Spectate

Draba Spectate is a Fabric mod for Minecraft 26.1.2 with coordinated server and client behavior.

Spectating starts through a vulnerable stand-still countdown. Movement, damage, hazards, or an actively targeting visible mob can cancel the request. The server controls the target and restores the player's prior state when the session ends.

Simple Voice Chat integration keeps proximity audio anchored to each spectator's saved position instead of the watched player. Normal and whisper ranges, dimensions, and voice-group routing remain intact, and a spectator's microphone is never transmitted from the watched player's location.

The client adds a compact pause-menu interface with network population, player join times, refresh controls, and spectating controls. Published hostnames use reserved example domains and should be changed for another deployment.

## Build

```bash
./gradlew clean build
```
