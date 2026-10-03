# World, scene and multiplayer map

This note documents the ownership and concurrency boundaries around `GameServer`, `World` and `Scene`. Read it before changing multiplayer joins, scene transitions, world ticking, entity lifetime or scene-script loading.

## Object graph

At runtime the important ownership chain is:

```text
GameServer
  -> online Player map
  -> World set
       -> host Player
       -> Player list
       -> Scene map
            -> Player list
            -> entity maps
            -> loaded blocks/groups
            -> SceneScriptManager
            -> dungeon/challenge state
            -> scene scheduler
```

`GameServer` also owns a separate `homeWorlds` cache keyed by host UID. A `HomeWorld` is still a `World` and participates in the server world set.

## GameServer tick

`GameServer.start()` schedules `GameServer.onTick()` at `GAME_INFO.tickRateMs`.

The tick performs three major phases:

```text
worlds -> world.onTick()
players -> player.onTick()
server scheduler -> runTasks()
```

Each world/player/scheduler call is guarded so one failure does not terminate the entire tick. Preserve this isolation when moving work into the tick.

When the database watchdog marks MongoDB unavailable, `GameServer.onTick()` returns early. This is intentional backpressure: continuing to mutate gameplay state while writes cannot drain would grow save pressure and risk misleading progress.

## Lock ordering around home worlds

`GameServer.onTick()` locks `homeWorlds` before iterating/removing from `worlds`. The current order exists because home-world creation uses `computeIfAbsent`, constructs a `HomeWorld`, and registers that world in the world set.

Changing this order can recreate the login-versus-tick deadlock that previously froze the server. Treat the comment in `GameServer.onTick()` as a concurrency invariant.

## World

`World` owns:

- the host player;
- synchronized player list;
- synchronized scene map;
- world-level entity;
- entity-ID allocation;
- multiplayer flag and peer IDs;
- world time / pause / time-lock state.

A player joins a world through `World.addPlayer(...)`. The important sequence is:

```text
remove from previous World if present
set player.world
add to world player list
assign peer/team state
get/create target Scene
Scene.addPlayer(player)
update multiplayer player/team information when needed
```

`World.getSceneById()` lazily constructs a `Scene` from `GameData` and registers it in the world.

`World.onTick()` removes an empty world, ticks non-empty scenes, periodically synchronizes game time, and persists the host's current world time while time is not locked.

World time can be paused or quest-locked. A stale `PROP_IS_GAME_TIME_LOCKED` therefore presents as an open world that appears frozen without an exception. Check this state before diagnosing a tick failure.

## Scene

`Scene` is the live container for scene-local state. It owns:

- players;
- normal and weapon entities;
- spawned/dead spawn entries;
- loaded blocks and groups;
- NPC born entries;
- `SceneScriptManager`;
- dungeon/challenge state;
- scene scheduler;
- scene/world-time relationship.

Scene construction wires resource-derived scene data, routes, script management, blossom management, the scene entity and scheduling state.

## Scene transition locking

`Scene.addPlayer()` deliberately removes the player from the previous scene **before** entering the new scene's synchronized section. Holding one scene lock while waiting for another can deadlock two players swapping scenes in opposite directions.

Do not "simplify" this into nested synchronized scene calls.

When changing world/scene membership code, preserve this broad rule:

```text
never hold Scene A while waiting for Scene B
be deliberate about World -> Scene lock order
be deliberate about homeWorlds -> worlds order
```

If a change needs a new lock, write down the intended global order in code comments and test two-player cross-scene/login behavior.

## Entities and visibility

`Scene` resolves several entity classes specially: scene entity, world-level entity, team entities, avatars and weapon entities. General entities are stored in concurrent maps.

A missing object in the client can originate from several layers:

```text
resource/spawn record absent
  -> scene block/group never loaded
  -> script group/suite condition not active
  -> entity never constructed/added
  -> vision/create packet not sent
  -> client protocol field/opcode mismatch
```

Work down that chain before adding special-case spawns.

## Scripted scene content

Scene script behavior crosses these areas:

```text
scripts/
lua/
SceneScriptManager
SceneIndexManager
SceneBlock / SceneGroup / suites / triggers
```

NPCs, gadgets, monsters, triggers and quest-driven group changes may therefore be correct in Java while absent because a resource/script group was never loaded, or correct in resources while Java does not yet implement the relevant action/condition.

## Multiplayer triage

For join/leave/teleport failures, inspect in this order:

1. `MultiplayerSystem` request/response path.
2. Host and guest `World` references and peer IDs.
3. `World.addPlayer/removePlayer` membership.
4. `Scene.addPlayer/removePlayer` membership.
5. scene-load state and enter-scene handshake packets.
6. MP team copy/update and sync packets.
7. entity visibility for both players.
8. disconnect/logout cleanup.

A bug where host and guest disagree about membership is usually more fundamental than a later visual/entity symptom.

## Freeze triage

For an apparent freeze:

- If every world freezes, inspect `GameServer.onTick()`, database-down state, global lock contention and timer-thread exceptions.
- If one world freezes, inspect that world's `onTick()`, time-lock/pause state and scene calls.
- If one scene freezes, inspect `Scene.onTick()`, its scheduler and script manager.
- If movement works but interactions do not, inspect network logic/packet handlers before the tick.
- If only one player is affected, inspect `Player.onTick()`, scene-load state and that player's managers/session.

## Regression expectations

Any fix involving world membership, scene membership, home-world caching or lock order should preserve at least these cases:

- single-player login and normal teleport;
- second player joins and leaves a host world;
- two players transition scenes without deadlock;
- a home-world login cannot stall the server tick;
- one world throwing during tick does not stop other worlds;
- empty worlds/home worlds release their cached references;
- world-time lock/unlock survives login and does not accidentally freeze unrelated state.

Runtime-only checks live in `docs/regression/README.md`.
