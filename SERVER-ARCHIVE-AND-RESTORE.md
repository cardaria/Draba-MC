# Server deployment and recovery

This document defines the data boundaries, topology, and recovery procedure for
the Draba Minecraft network. It contains no access instructions, credentials,
addresses, player records, or backup transport details.

The deployment baseline was last verified on 2026-08-25. See
`SERVER-SOFTWARE-INVENTORY.md` for exact mod and plugin versions.

## Architecture

The network consists of a Velocity proxy and two Fabric backends:

| Component | Deployment directory | Bind address and port | Role |
| --- | --- | --- | --- |
| Velocity | `/srv/minecraft-velocity` | public interface, TCP `25565` | Authentication and routing |
| Main | `/srv/minecraft/server` | loopback, TCP `25566` | Survival world |
| Hardcore | `/srv/minecraft-hc/server` | loopback, TCP `25567` | Hardcore world |

Both backends use Velocity modern forwarding through FabricProxy-Lite. They use
`online-mode=false` and must remain inaccessible from public networks. Velocity
is the authenticated entry point.

AutoModpack is hosted separately by each backend on TCP `8080` and `8081`.
Simple Voice Chat also requires the UDP ports configured under each backend's
`config/voicechat` directory.

## Recovery set

A complete recovery set contains:

| Path | Required data |
| --- | --- |
| `/srv/minecraft/server` | Main world, player state, mods, configuration, AutoModpack content, access lists, and runtime files |
| `/srv/minecraft-hc/server` | Hardcore world, player state, hardcore state, mods, configuration, AutoModpack content, and runtime files |
| `/srv/minecraft-velocity` | Proxy configuration, plugins, and plugin data |
| `/srv/minecraft/admin` | Main service and administration helpers |
| `/srv/minecraft-hc/admin` | Hardcore service and administration helpers |

The two world roots are:

```text
/srv/minecraft/server/world
/srv/minecraft-hc/server/world
```

Each world root contains all visited dimensions, terrain, entities, player
inventories, ender chests, advancements, statistics, maps, datapacks, raids,
and scoreboard state. Preserve the directory as a unit.

World-only backups are insufficient for full recovery. Mod-owned state outside
the world includes LuckPerms, private todo data, spectate state, the player
changelog, AutoModpack metadata, voice-chat configuration, and hardcore state.
The exact required files are listed in `SERVER-SOFTWARE-INVENTORY.md`.

## Consistent snapshots

Filesystem copies of an active Minecraft world are not guaranteed to be
consistent. A snapshot procedure must establish a stable point in time:

1. Quiesce player traffic.
2. Issue `save-all flush` to both backends.
3. Stop both Fabric backends cleanly.
4. Stop Velocity or otherwise prevent backend connections.
5. Capture the complete recovery set.
6. Generate a cryptographic checksum manifest for the captured artifacts.
7. If service is continuing, start the backends, verify successful startup, and
   then start Velocity.

Storage-level snapshots are acceptable only when they provide crash-consistent
coverage of every required directory. Backups must be stored separately from
the host they protect.

## Archive validation

Before retiring a deployment, verify all of the following:

- archive checksums match the checksum manifest;
- every archive can be read from beginning to end;
- both worlds contain `level.dat`, `region`, `playerdata`, `advancements`, and
  `stats`;
- visited dimensions are present under `DIM-1` and `DIM1`;
- both populated `config/draba-todos.json` files are present;
- hardcore `config/draba-hardcore-state.json` is present;
- server `mods`, `config`, and `automodpack` directories are present;
- Velocity configuration, plugins, and plugin data are present;
- access lists and server properties are present;
- at least two verified copies exist on independent storage.

Backup artifacts contain player data and secrets. Apply appropriate encryption,
access control, retention, and deletion policies.

## Full-network recovery

The baseline runtime is Linux with Java 25 and a dedicated unprivileged
`minecraft` user and group. Provide sufficient capacity for world growth,
generated map tiles, and backups.

1. Install the runtime versions recorded in the software inventory.
2. Create the service account and deployment directories.
3. Restore the recovery set to its original layout. The following paths must
   exist without an additional nesting level:

   ```text
   /srv/minecraft/server/world/level.dat
   /srv/minecraft-hc/server/world/level.dat
   /srv/minecraft-velocity/velocity.toml
   ```

4. Assign the restored trees to the service account and restrict access to
   configuration containing secrets or player data.
5. Install and review the systemd units from the administration directories.
6. Generate a new Velocity forwarding secret and configure both proxy and
   backend forwarding. Do not reuse an archived secret.
7. Preserve loopback binding and the distinct backend ports in each
   `server.properties` file.
8. Configure firewall rules for proxy TCP, AutoModpack TCP, and voice-chat UDP
   traffic.
9. Start each Fabric backend and confirm a clean `Done` message without fatal or
   mixin errors.
10. Start Velocity and verify routing to both backends.
11. Validate player inventories, ender chests, permissions, `/todo`, `/guide`,
    `/changelog`, hardcore state, dimension travel, voice chat, and AutoModpack.
12. Establish automated backups before admitting production traffic.

The previous deployment allocated 4 GB initial and 10 GB maximum heap to each
Fabric backend, and 512 MB initial and 2 GB maximum heap to Velocity. Treat
these as historical baselines and size the replacement host from observed load.

## Standalone backend recovery

A backend may be recovered without Velocity, but it must be converted from the
proxied trust model before exposure:

1. Work from a separate copy of the restored backend.
2. Remove FabricProxy-Lite.
3. Clear `server-ip`, select the intended public port, and set
   `online-mode=true`.
4. Retain the archived world, mods, and configuration for the first boot.
5. Restrict firewall exposure to required service ports.
6. Validate player UUID and inventory continuity before production use.

Place the recovered world at the directory selected by `level-name` before the
first production start. If a test start generated a replacement world, stop the
server and move that generated directory aside before restoring the archive.

## Security requirements

- Rotate proxy forwarding, management, voice-chat, and plugin credentials after
  recovery.
- Never publish backend ports configured with `online-mode=false`.
- Do not commit worlds, player data, access lists, logs, configuration secrets,
  or backup artifacts to this repository.
- Preserve UUID ownership and task identifiers when migrating todo data.
- Retain exact customized NeoEnchant artifacts or reproducible private source;
  the public repository cannot recreate that build.
