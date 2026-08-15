# Draba Hardcore

Draba Hardcore is a Fabric mod for Minecraft 26.1.2. It manages death cooldowns, player-locked spectating, and contribution-based ownership for items stored in shared containers.

Ownership follows item contributions instead of assigning an entire container to one player. Transfers, merges, splits, hoppers, furnaces, brewing stands, crafters, bundles, sorting, dropped items, and vehicle containers are covered. Items that existed before ownership tracking remain permanent.

The client side provides the cooldown and spectating screens used while a player is waiting to return.

## Build

```bash
./gradlew clean build
```

The release jar is written to `build/libs`.
