# Deployed server software inventory

This is the rebuild inventory for the Draba network captured from the live
deployment on 2026-08-25. Exact archived jars and configuration are preferable
to substituting whatever versions happen to be current in the future.

## Platform

| Component | Version |
| --- | --- |
| Minecraft | 26.1.2 |
| Java | Eclipse Temurin OpenJDK 25.0.3+9 LTS |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.155.2+26.1.2 |
| Velocity | 3.5.1 |
| AutoModpack server mod | 4.0.6 |
| AutoModpack Velocity plugin | 0.2.0-beta |

Both Fabric servers use the same base mod set. Hardcore additionally uses
Draba Hardcore and Warband. The archive's exact jar files are authoritative.

## Common Fabric server jars

| Mod | Deployed jar/version |
| --- | --- |
| AdminScope | `AdminScope-0.6.3-Alpha+26.1.jar` |
| Beyond Enchant | `BeyondEnchant-1.8.0.jar` |
| Block Runner | `BlockRunner-v26.1.0-mc26.1.x-Fabric.jar` |
| Chunky | `Chunky-Fabric-1.5.3.jar` |
| Clumps | `Clumps-fabric-26.1.2-26.1.2.1.jar` |
| FabricProxy-Lite | `FabricProxy-Lite-2.12.0.jar` |
| Forge Config API Port | `ForgeConfigAPIPort-v26.1.5-mc26.1.x-Fabric.jar` |
| LuckPerms | `LuckPerms-Fabric-5.5.57.jar` |
| NeoEnchant, customized deployment | `NeoEnchant-5.15.1.jar` |
| Pl3xMap | `Pl3xMap-26.1.2-549.jar` |
| PrickleMC | `PrickleMC-fabric-MC26.1.2-26.1.2.6.jar` |
| Puzzles Lib | `PuzzlesLib-v26.1.14-mc26.1.x-Fabric.jar` |
| AutoModpack | `automodpack-mc26.1.2-fabric-4.0.6.jar` |
| CalcMod | `calcmod-1.5.1+fabric.26.1.jar` |
| Cloth Config | `cloth-config-26.1.154.jar` |
| Collective | `collective-26.1.2-8.32.jar` |
| Disable Elytra Outside The End | `deote-2.4.1-fabric+mc26.1.1.jar` |
| Draba Spectate | `draba-spectate-1.0.14+26.1.2.jar` |
| Draba Welcome | `draba-welcome-1.5.29+26.1.2.jar` |
| EnchantOnce | `enchantonce-26.1.2-fabric-2.6.1.jar` |
| Fabric API | `fabric-api-0.155.2+26.1.2.jar` |
| FerriteCore | `ferritecore-9.0.0-fabric.jar` |
| Inventory Sorter | `inventorysorter-fabric-3.0.0+mc26.1.2.jar` |
| Just Enough Items | `jei-26.1.2-fabric-29.29.0.77.jar` |
| Lithium | `lithium-fabric-0.24.7+mc26.1.2.jar` |
| ModernFix | `modernfix-5.27.19-build.1.jar` |
| Secret Spectator | `secret-spectator-1.1.0.jar` |
| Shulker Box Tooltip | `shulkerboxtooltip-fabric-5.4.0+26.1.1.jar` |
| Sodium | `sodium-fabric-0.9.1+mc26.1.2.jar` |
| Spectator Disclosure | `spectator-disclosure-1.0.1+26.1.2.jar` |
| Styled Chat | `styled-chat-2.12.0+26.1.2.jar` |
| Tree Harvester | `treeharvester-26.1.2-9.4.jar` |
| Vanilla Permissions | `vanilla-permissions-0.3.6+26.1.2.jar` |
| Simple Voice Chat | `voicechat-fabric-2.6.22+26.1.2.jar` |
| Voxy | `voxy-0.2.18-beta-26.1.2.jar` |
| Voxy World Gen V2 | `voxy-worldgen-2.2.4-26.1.2.jar` |
| Xaero's Minimap | `xaerominimap-fabric-26.1.2-26.4.2.jar` |
| Xaero's World Map | `xaeroworldmap-fabric-26.1.2-1.44.2.jar` |
| YetAnotherConfigLib | `yet_another_config_lib_v3-3.9.6+26.1-fabric.jar` |

## Hardcore-only Fabric jars

| Mod | Deployed jar/version |
| --- | --- |
| Draba Hardcore | `draba-hardcore-0.4.1+26.1.2.jar` |
| Warband | `warband-1.4.0.jar` |

Files under `mods.disabled` are deployment history, not active mods. Preserve
them in a complete archival copy, but do not move them back into `mods` during a
restore. Just Enough Backups is deliberately disabled in the captured hardcore
deployment and must not be assumed to provide a current backup.

## AutoModpack client distribution

Main and hardcore currently distribute the same client content from each
server's `automodpack/host-modpack/main` directory.

### Client mod jars

```text
AmbientSounds_FABRIC_v6.3.6_mc26.1.2.jar
BetterAdvancements-Fabric-26.1.2-0.5.0.63.jar
BlockRunner-v26.1.0-mc26.1.x-Fabric.jar
CreativeCore_FABRIC_v2.14.16_mc26.1.2.jar
CutThrough-v26.1.0-mc26.1.x-Fabric.jar
EffectInsights-v26.1.2-mc26.1.x-Fabric.jar
EnchantmentInsights-v26.1.2-mc26.1.x-Fabric.jar
ForgeConfigAPIPort-v26.1.5-mc26.1.x-Fabric.jar
Handful-1.0.0-fabric+mc26.1.jar
ImmediatelyFast-Fabric-1.15.3+26.1.jar
LeadPhysics-1.0.0.jar
MouseTweaks-fabric-mc26.1-2.31.jar
PresenceFootsteps-1.13.3+26.1.jar
PrickleMC-fabric-MC26.1.2-26.1.2.6.jar
PuzzlesLib-v26.1.14-mc26.1.x-Fabric.jar
ShoulderSurfing-Fabric-26.1.2-5.0.10.jar
autocrop-1.4-mc26.1.jar
bettersmoke-0.1.2.jar
chatanimation-fabric-1.3.0+mc26.1.jar
cloth-config-26.1.154.jar
collective-26.1.2-8.32.jar
continuity-3.0.1-beta.2+26.1.jar
controlify-3.1.0+26.1-fabric.jar
deote-2.4.1-fabric+mc26.1.1.jar
draba-neoenchant-descriptions-1.0.3+26.1.2.jar
draba-resources-1.0.1+26.1.2.jar
enchantonce-26.1.2-fabric-2.6.1.jar
entity_model_features-3.2.4-26.1-fabric.jar
entity_texture_features_26.1-fabric-7.1.jar
entityculling-fabric-1.10.5-mc26.1.jar
fabric-api-0.155.2+26.1.2.jar
fabric-language-kotlin-1.13.13+kotlin.2.4.10.jar
hotbar-keys-1.0.0+mc26.1.jar
inventorysorter-fabric-3.0.0+mc26.1.2.jar
iris-fabric-1.11.3+mc26.1.2.jar
jei-26.1.2-fabric-29.29.0.77.jar
mc2_interactivefoliage-1.1.1-fabric+26.1.2.jar
modernfix-5.27.19-build.1.jar
modmenu-18.0.0.jar
particlerain-4.0.0-beta.10+26.1-fabric.jar
placeholder-api-3.0.0+26.1.jar
selectivebounds-fabric-26.1.2-0.0.3.jar
shulkerboxtooltip-fabric-5.4.0+26.1.1.jar
sodium-fabric-0.9.1+mc26.1.2.jar
sound-physics-remastered-fabric-1.5.1+26.1.2.jar
sway-1.1.2-fabric+26.1.2.jar
treeharvester-26.1.2-9.4.jar
voicechat-fabric-2.6.22+26.1.2.jar
voxy-0.2.18-beta-26.1.2.jar
voxy-worldgen-2.2.4-26.1.2.jar
xaerominimap-fabric-26.1.2-26.4.2.jar
xaeroworldmap-fabric-26.1.2-1.44.2.jar
yet_another_config_lib_v3-3.9.6+26.1-fabric.jar
zoomify-2.16.1+26.1.jar
```

### Distributed configuration and resource packs

```text
config/enchantonce-common.toml
config/voxyworldgenv2.json
resourcepacks/Fresh-Animations-1.10.5.zip
resourcepacks/Fresh-Animations-Objects-2.1.2.zip
```

AutoModpack's generated manifest must be allowed to regenerate after changes.
When rebuilding, verify it contains the intended files rather than copying an
old manifest blindly.

## Velocity proxy plugins

| Plugin | Deployed jar/version |
| --- | --- |
| AutoModpack Velocity | `AutoModpackVelocity-0.2.0-beta.jar` |
| LoginPhaseProxy | `LoginPhaseProxy-0.2.1-beta.jar` |
| RelayLine, Draba build | `RelayLine-1.4.2-draba.1.jar` |
| Draba Network Notices | `draba-network-notices-1.3.0.jar` |

Velocity also contains plugin data directories for AutoModpack, RelayLine, and
bStats. Preserve the complete proxy directory, but rotate credentials and
secrets before deploying its archived configuration elsewhere.

## State and configuration that must not be lost

The world carries vanilla terrain and player state, but these external files
also matter:

| Path relative to each server root | Purpose |
| --- | --- |
| `config/draba-todos.json` | Private per-player `/todo` data |
| `config/luckperms` | Permissions database and configuration |
| `config/draba-spectate-sessions.json` | Spectate session state |
| `config/draba-changelog.json` | Player-facing changelog |
| `config/draba-client-mod-policy.json` | Required/allowed client-mod policy |
| `config/voicechat` | Simple Voice Chat settings and secrets |
| `config/pl3xmap` | Map configuration and generated map tiles |
| `automodpack` | Hosted client pack and generated content metadata |
| `server.properties` | World name, ports, mode, distances, and gameplay settings |
| `whitelist.json`, `ops.json`, `banned-*.json` | Access-control lists |

Hardcore additionally requires:

```text
config/draba-hardcore-state.json
config/warband.properties
```

Todo data is player-owned data. Never replace a populated restored file with an
empty bundled default. Any migration must preserve player UUID ownership and
task IDs.

## World-generation and gameplay notes

- Main is hard-difficulty survival with `level-type=minecraft:normal`.
- Hardcore uses `hardcore=true` and `level-type=minecraft:large_biomes`.
- Both use simulation distance 10 and normal view distance 12. Voxy supplies
  the distant view.
- Enabled datapacks are recorded in each world's `level.dat`; preserve the
  complete world and exact mod set for the first restored boot.
- NeoEnchant's Timber enchantment remains registered for existing items but is
  intentionally absent from natural-acquisition tags.
- Main and hardcore use a Velocity-authenticated backend arrangement. See the
  restore guide before changing `online-mode`, FabricProxy-Lite, or bind ports.

## Custom source locations represented by this repository

| Deployed component | Repository path |
| --- | --- |
| Draba Hardcore | `mods/draba-hardcore` |
| Draba Resources | `mods/draba-resources` |
| Draba Spectate | `mods/draba-spectate` |
| Draba Welcome | `mods/draba-welcome` |
| Spectator Disclosure | `mods/spectator-disclosure` |
| Draba Network Notices | `plugins/draba-network-notices` |
| RelayLine Draba fork | `plugins/relayline` |

NeoEnchant and EnchantOnce sources are not published here because of their
licenses. Preserve their exact deployed jars and any private reproducible source
separately. Do not treat a future artifact with the same version number as
equivalent without comparing hashes, because NeoEnchant is customized.
