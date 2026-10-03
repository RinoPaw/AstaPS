# AstaPS agent guide

This file is the short, stable contract for AI-assisted maintenance. Keep it concise. Put architecture details under `docs/architecture/`, reverse-engineering working discipline under `docs/reverse-workflow.md`, and runtime-only regression procedures under `docs/regression/`.

## Target and repository boundaries

AstaPS targets **Genshin Impact 7.1.0 Global / Windows x64** unless a task explicitly changes the target. Do not silently mix evidence from CN, beta, mobile, or older clients.

Use the repositories by responsibility:

- `RinoPaw/AstaPS`: server implementation, runtime probes, integration behavior and deployable server-side fixes.
- `RinoPaw/AstaPS-Resource`: version-bound resource data and derived resource/reverse-engineering intermediates used by the server.
- `RinoPaw/Genshin-Reverse`: reproducible client reverse-engineering evidence, CmdId registry, metadata indexes, protobuf/message-shape work, xrefs and focused protocol investigations.

Comparable implementations worth checking include upstream `MeChen618/AstaPS`, Grasscutter, LunaGC and HunkyMeow. Similar code is evidence and a source of ideas, not proof that 7.1 behaves the same way.

## Maintenance workflow

For a concrete bug or missing feature:

1. Read the relevant file in `docs/architecture/`.
2. Search the current AstaPS implementation and recent history before adding code.
3. Identify the owning layer: server logic, resource data, protocol/client behavior, or persistence.
4. Compare the same behavior in related projects when useful.
5. If current client behavior or a packet identity is uncertain, use `Genshin-Reverse` before guessing.
6. Make the smallest coherent fix. Avoid unrelated refactors during protocol or gameplay repair work.
7. Add an automated regression test when the behavior can be tested without a live client/resources. Otherwise update `docs/regression/README.md` with a short reproducible manual check.
8. Update architecture notes only when an ownership boundary, important execution path or invariant changes.

Prefer direct commits for focused maintenance changes in this fork. Use a PR when review, discussion or a multi-commit series materially helps.

## Reverse-engineering work discipline

Before starting reverse-engineering work, read `docs/reverse-workflow.md` and search the relevant `Genshin-Reverse` files and history. Check previous candidates, rejected conclusions, notes, scripts and commits before repeating an experiment.

Save useful progress as soon as it becomes expensive to reconstruct. Make small checkpoint commits after meaningful discoveries or reusable tooling, and push/checkpoint before risky bulk edits, long-running analysis, large rebases or other steps that could destroy local state. Important progress must not exist only in chat history, terminal scrollback, an unsaved decompiler database or an untracked local file.

When pausing unfinished work, leave a resumable handoff containing the target, confirmed facts, candidates, rejected paths, relevant artifacts and the next useful step. Store reverse-engineering evidence in `Genshin-Reverse`; AstaPS should consume stable results rather than becoming the scratch workspace.

## Evidence rules

Never treat an older-version CmdId as a current mapping merely because the number matches. Current protocol claims must be bound to 7.1 evidence.

For reverse-engineering work, preserve the evidence vocabulary used by `Genshin-Reverse`: `CONFIRMED`, `HIGH_CONFIDENCE`, `CANDIDATE`, `REJECTED`, and `UNRESOLVED`. Candidate probes must not overwrite canonical mappings.

When server code depends on non-obvious reversed behavior, leave a compact source pointer to the relevant `Genshin-Reverse` artifact or analysis. Do not paste large reverse-engineering dumps into AstaPS.

## Build and fast validation

The source/target compatibility is Java 17, while the build toolchain must be **JDK 21** because current code uses JDK 21 APIs.

Fast build:

```bash
./gradlew jar -PskipHandbook=1
```

Fast tests that work on a clean checkout without MongoDB or the resource pack:

```bash
./gradlew test -PexcludeTags=integration
```

If Java formatting is touched, run the relevant Spotless check before considering formatting work complete.

On Windows, use `gradlew.bat` in place of `./gradlew`.

## CI policy

Keep normal CI fast. It should compile, run standalone tests and perform lightweight static/format checks. Do not make routine CI download the full resource pack, boot a real game client, perform heavy executable analysis, or run long reverse-engineering pipelines.

Heavy reverse-engineering and live-client validation should remain explicit local/manual workflows or narrowly triggered jobs. Do not wait on CI when local/static evidence is already sufficient to continue development.

## Runtime and concurrency cautions

`GameServer.onTick()` drives worlds, players and scheduled tasks. One world/player failure must not be allowed to kill unrelated worlds or the game loop.

World/scene transitions have lock-order sensitivity. Read the comments around `GameServer.onTick()`, `World.addPlayer/removePlayer()` and `Scene.addPlayer/removePlayer()` before changing synchronization. Do not introduce a nested lock order that can deadlock login, home-world creation or players crossing scenes.

Database writes use bounded executors/backpressure. Preserve failure visibility and player-progress durability when changing persistence behavior.

## Packet work

Inbound path, at a high level:

`KCP -> GameSessionManager -> GameSession -> GameServerPacketHandler -> PacketHandler`

Outbound path:

`BasePacket -> GameSession.send() -> encryption -> KCP`

Before implementing an unknown packet, determine its current 7.1 identity and direction. Prefer canonical `Genshin-Reverse` artifacts over opcode-shape guessing. Fallback parsers in `GameServerPacketHandler` are exceptional compatibility code and should not become the default way to identify packets.

## User testing handoff

When a change needs the user to test a live client, provide one complete copy-paste command sequence including remote fetch, branch/ref checkout, build, and the run command. Do not assume the user's checkout is already on the right commit.

State exactly what should be observed in-game and what log lines or packet evidence to return if the result differs.

## Definition of done

A maintenance change is done when the owning layer is understood, the smallest appropriate code/data change is made, fast validation passes where applicable, and the regression is preserved as either a test or a documented manual check. Unknowns should stay explicitly unknown rather than being hidden behind guessed constants or silent fallbacks.
