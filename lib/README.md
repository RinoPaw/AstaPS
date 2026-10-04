# Vendored libraries

## `kcp-1.5.1.jar`

AstaPS still vendors this JAR because the `kcp.highway` implementation used by the game server does not have a stable Maven Central coordinate that can replace it directly.

Provenance notes:

- The checked-in JAR is byte-for-byte the same Git blob used by LunaGC 6.6.0 (`e752fb034457298a790aa719d304f06257495808`, 590330 bytes).
- The `kcp.highway` package and `KcpServer` API trace to `zhaodice/grasskcpper`.
- Grasscutter's current `kcp-1.5.1.jar` is a different binary, so it must not be substituted without compatibility testing.

Keep this dependency explicit in `build.gradle`; do not restore a blanket `lib/*.jar` file tree. If a maintained, reproducible source build or published artifact becomes available, replace this JAR in a dedicated networking change and run the game-server networking tests before merging.
