# Protocol, resources and persistence boundaries

This note exists to stop three different classes of failure from being patched in the wrong place. A missing packet, a missing resource row and a bad server implementation can produce very similar in-game symptoms.

## Three layers of truth

Use this model when investigating version-specific behavior:

```text
Genshin-Reverse
  current-client evidence: protocol identity, IL2CPP metadata, message shapes, xrefs

AstaPS-Resource / resource pack
  version-bound data: Excel/bin outputs, spawn/resource-derived tables and reproducible intermediates

AstaPS
  server behavior: packet handlers, gameplay state machines, world/entity logic and persistence
```

Do not compensate for missing evidence in one layer with a magic constant in another.

## Protocol layer in AstaPS

Relevant server paths include:

```text
src/main/java/emu/grasscutter/net/packet/
src/main/java/emu/grasscutter/net/proto/
src/main/java/emu/grasscutter/server/packet/recv/
src/main/java/emu/grasscutter/server/packet/send/
src/main/java/emu/grasscutter/server/game/GameServerPacketHandler.java
```

The generated/current protocol classes and `PacketOpcodes` describe what AstaPS currently believes. They are implementation inputs, not independent proof of an unknown semantic mapping.

For an unknown or questionable 7.1 packet, use this workflow:

```text
runtime symptom / observed opcode
  -> search existing PacketOpcodes and handlers
  -> query canonical 7.1 artifacts in Genshin-Reverse
  -> inspect message shape / handler / sender / metadata relationships
  -> use focused runtime validation only when needed
  -> promote semantic identity only when the investigation's evidence gate is satisfied
  -> implement the minimal AstaPS handler/packet change
```

Never promote a historical CmdId solely because its number matches the current observation. CmdIds are version-remapped.

Shape-based fallback parsing in `GameServerPacketHandler` is compatibility code for exceptional cases. It should not become the normal discovery mechanism for new packet identities.

## Genshin-Reverse handoff

`RinoPaw/Genshin-Reverse` is the preferred place for reusable reverse-engineering work. Start with its canonical artifacts under the matching `versions/7.1.0-global/windows-x64/` directory before building a new executable probe.

Keep evidence states explicit: `CONFIRMED`, `HIGH_CONFIDENCE`, `CANDIDATE`, `REJECTED`, `UNRESOLVED`.

If server code depends on a non-obvious reversed fact, leave a short pointer in the code or maintenance note to the relevant Genshin-Reverse analysis/artifact. Keep raw proprietary binaries out of Git history.

## Resource layer

There are two resource concepts that are easy to confuse:

- top-level `data/` contains server-side JSON/tables shipped with this repository;
- runtime `resources/` is the extracted version-specific resource pack required by the server and is not expected in a clean Git checkout.

The Java loading/representation layer lives primarily under:

```text
src/main/java/emu/grasscutter/data/
```

`Grasscutter.main()` calls `ResourceLoader.loadAll()` before starting normal gameplay. Some systems are constructed earlier and therefore deliberately perform a second initialization/load step after resources become available.

When a field appears missing after a version update, first determine which of these is true:

1. the current resource pack does not contain/recover the field;
2. AstaPS's data class does not map the current field/key;
3. the mapped field is loaded but gameplay code ignores it;
4. the client expects additional packet/state synchronization.

These four cases belong to different fixes.

## AstaPS-Resource handoff

Use `RinoPaw/AstaPS-Resource` for target-version resource material and reproducible derived data. Version/sample-bound reverse intermediates should record provenance and hashes rather than committing raw game executables or metadata samples.

If the fix is purely a corrected resource table, keep it out of Java when possible. If Java must support a newly recovered field or obfuscated key, document enough provenance that a later maintainer can tell why the mapping exists.

## Persistence layer

MongoDB-backed persistence is implemented under:

```text
src/main/java/emu/grasscutter/database/
```

Persistent domain objects also live in gameplay packages such as account/player/inventory/quest-related code.

AstaPS uses bounded database execution/backpressure and a database watchdog. Preserve these properties when changing saves:

- player progress should not be silently dropped because a queue is full;
- database failure should remain visible;
- unbounded enqueueing during an outage is unacceptable;
- shutdown should drain/terminate persistence executors deliberately;
- a gameplay fix should not introduce synchronous database work onto a latency-sensitive network/tick path without a strong reason.

When progress appears lost, determine whether the state was never mutated, mutated but never scheduled for save, queued but blocked, or saved to the wrong persisted representation before changing serialization.

## Version migration checklist

When moving beyond 7.1, do not begin by mass-editing constants. Establish the new target sample and then move layer by layer:

```text
client/sample provenance
protocol registry + metadata
message shapes / semantic mappings
resource extraction and data-class compatibility
login/session path
world/scene baseline
feature-specific gameplay regressions
```

Keep old-version compatibility explicit. Avoid silently accepting multiple protocol/resource layouts in the same code path unless the project intentionally becomes multi-version.
