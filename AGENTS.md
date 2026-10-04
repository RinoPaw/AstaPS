# AstaPS agent guide

This file is the stable contract for AI-assisted maintenance of this fork. Keep implementation details in code or focused docs; keep reverse-engineering evidence in `RinoPaw/Genshin-Reverse`.

## Target and repository boundaries

AstaPS targets **Genshin Impact 7.1.0 Global / Windows x64** unless a task explicitly changes the target. Do not silently mix evidence from CN, beta, mobile, or older clients.

Use the repositories by responsibility:

- `RinoPaw/AstaPS`: server implementation, runtime validation, integration behavior and deployable server-side fixes.
- `RinoPaw/AstaPS-Resource`: version-bound resource data used by the server.
- `RinoPaw/Genshin-Reverse`: reproducible client reverse-engineering evidence, protocol research and historical investigation results.

Comparable implementations worth checking include upstream `MeChen618/AstaPS`, Grasscutter, LunaGC and HunkyMeow. Similar code is useful precedent, not proof that 7.1 behaves identically.

## Maintenance workflow

1. Search the current implementation and recent history before adding code.
2. Identify the owning layer: server logic, resource data, protocol/client behavior, or persistence.
3. Compare related projects when useful.
4. If a fix depends on uncertain client/protocol behavior, consume an existing `Genshin-Reverse` result instead of guessing in AstaPS.
5. Make the smallest coherent fix and avoid unrelated refactors during gameplay/protocol repair.
6. Add an automated regression test when the behavior can be tested without a live client/resources; otherwise leave a short reproducible manual validation note.

Prefer direct commits for focused maintenance changes in this fork. Use a PR only when review, discussion or a multi-commit series materially helps.

## Repository cleanliness

Keep one maintained implementation, one normal entry point and one current source of truth for each behavior. Git history is the rollback archive; do not keep superseded code, scripts, workflows or configuration merely as `legacy`, `old`, `deprecated`, `backup` or `workaround` variants.

When a replacement is accepted, remove the superseded path in the same change unless the supported 7.1 runtime still depends on it. Keep temporary compatibility shims narrow and document the dependency that allows their later removal.

Preserve historical reverse-engineering results in `Genshin-Reverse` and commit history, not as dead executable code in AstaPS. Retire temporary probes and one-off test branches after their durable result has been preserved.

## Build and fast validation

AstaPS uses JDK 21 APIs. Fast local validation is:

```bash
./gradlew jar -PskipHandbook=1
./gradlew test -PexcludeTags=integration
```

On Windows, use `gradlew.bat` in place of `./gradlew`.

## CI policy

Keep routine CI fast. It should compile and run standalone tests without downloading the full resource pack, booting a real game client, or running heavy reverse-engineering jobs. Documentation and repository-only maintenance should not trigger expensive builds when no runtime artifact can change.

Do not wait on CI when local/static evidence is already sufficient to continue maintenance.

## Runtime cautions

`GameServer.onTick()` drives worlds, players and scheduled tasks. One world/player failure must not kill unrelated worlds or the game loop.

World/scene transitions have lock-order sensitivity. Read the surrounding synchronization before changing `GameServer.onTick()`, `World.addPlayer/removePlayer()` or `Scene.addPlayer/removePlayer()`.

Preserve persistence failure visibility and player-progress durability when changing database writes or executor/backpressure behavior.

## User testing handoff

When live-client testing is needed, provide one complete copy-paste command sequence containing remote fetch, branch/ref checkout, build and run commands. State the expected in-game result and the exact logs or evidence to return if it differs.

## Definition of done

A maintenance change is done when the owning layer is understood, the smallest appropriate code/data change is made, fast validation is used where applicable, and regressions are preserved with a test or reproducible validation procedure. Keep unknowns explicit instead of hiding them behind guessed constants or silent fallbacks.
