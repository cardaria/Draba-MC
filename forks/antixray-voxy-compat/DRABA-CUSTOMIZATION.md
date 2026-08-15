# Draba AntiXray Voxy compatibility build

This tree is based on the official MIT-licensed AntiXray tag
`fabric-1.4.16+26.1` at commit `4f54f42b149f931737d5232327de07c7b7068ccd`.

## Changes

- Uses version `1.4.16-draba.1+26.1`.
- Applies the unbound `PALETTE_ENTRIES` guard proposed upstream in PR #83.
- Guards the two packet-metadata reads used while writing palette data. This lets
  Voxy WorldGen serialize LOD sections outside a vanilla chunk-packet scope while
  preserving normal AntiXray processing for real player chunk packets.

## Build

```bash
./gradlew clean build
```

Deploy the Fabric jar from `fabric/build/libs/`; never deploy a sources or dev jar.
Keep the official upstream artifact available for rollback.
