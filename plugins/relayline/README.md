# RelayLine: Draba fork

This fork is based on the MIT-licensed RelayLine 1.4.1 release. Upstream did not publish a source URL, so the released bytecode was reconstructed with CFR 0.152 before applying the network-specific change.

The fork adds `local-only-message-prefixes`. Matching protocol messages remain on their origin backend while ordinary global chat and the `/global` and `/local` commands keep their normal behavior.

## Build

Provide an official Velocity jar through `VELOCITY_JAR`, then run the build script.

```bash
VELOCITY_JAR=/path/to/velocity.jar ./build.sh
```
