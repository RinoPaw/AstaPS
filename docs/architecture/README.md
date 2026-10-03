# AstaPS architecture map

This is a navigation map for maintainers and AI agents. It is intentionally selective: it records ownership, execution paths and debugging entry points rather than cataloguing every class.

## Repository roles

- **AstaPS** owns server behavior, runtime integration and deployable fixes.
- **AstaPS-Resource** owns version-bound resource data and derived intermediates consumed by the server.
- **Genshin-Reverse** owns reproducible evidence about the 7.1 client, protocol identities, metadata, message shapes and client-side relationships.

If the question is "what should the server do?", start here. If the answer depends on "what does the 7.1 client actually send/do?", move to Genshin-Reverse. If the behavior is driven by Excel/bin/spawn/resource data, check AstaPS-Resource.

## Main execution path

```text
emu.grasscutter.Grasscutter.main()
  -> load config / crypto / database
  -> construct GameServer and/or HttpServer
  -> load resources
  -> start HTTP/dispatch and KCP game server
  -> enable plugins / watchdog / runtime monitor

KCP datagram
  -> GameSessionManager
  -> GameSession.handleReceive()
  -> GameServerPacketHandler.handle()
  -> server.packet.recv.Handler*
  -> game subsystem / Player / World / Scene
  -> BasePacket
  -> GameSession.send()
  -> KCP client
```

The detailed startup/network path is in [`startup-networking.md`](startup-networking.md).

## Core ownership map

| Area | Primary paths | Start here when... |
|---|---|---|
| Process/bootstrap | `Grasscutter.java`, `config/`, `bootstrap/` | boot, config, lifecycle, server mode |
| HTTP/dispatch/login front end | `server/http/`, `server/dispatch/`, `auth/` | region/dispatch/auth HTTP behavior |
| Game transport/session | `server/game/`, `net/packet/` | KCP, encryption, session state, packet routing |
| Packet implementations | `server/packet/recv/`, `server/packet/send/` | a concrete request/notify/response is wrong |
| Player aggregate | `game/player/` | login state, properties, managers, per-player state |
| World/scene | `game/world/`, `game/entity/` | multiplayer, scene entry, spawn, entity visibility, ticking |
| Scripted scene content | `scripts/`, `lua/` | groups, suites, triggers, Lua scene behavior |
| Quest | `game/quest/` | quest state, conditions, execs, quest-driven world state |
| Combat/abilities | `game/ability/`, `game/managers/`, entity combat code | character mechanics, energy, stamina, combat state |
| Gameplay systems | `game/*`, `server/game/GameServer.java` | inventory, gacha, shop, dungeon, tower, chat, MP, etc. |
| Static/runtime data | `data/`, `src/main/java/emu/grasscutter/data/` | Excel/bin/server data ingestion or lookup |
| Persistence | `database/`, persistent classes under `game/` | MongoDB, saves, account/player durability |
| Plugins/commands | `plugin/`, `command/` | extension points and operator commands |
| Scheduling/health | `server/scheduler/`, `server/threading/`, `ServerWatchdog.java` | stalls, queues, periodic tasks, runtime status |

## Important objects

`GameServer` is the central runtime coordinator. It owns online players, live worlds, home worlds, the packet handler and the major gameplay systems. Its tick invokes each world, each player and scheduled tasks with per-object failure isolation.

`GameSession` represents one game connection. It owns the account/player binding, session state, encryption state, frame parsing and outgoing packet construction.

`Player` is the large per-account gameplay aggregate. Many feature-specific managers hang from it. Before adding state to `Player`, check whether an existing manager/system already owns that domain.

`World` owns a host, players, scenes, world time and the world-level entity. Multiplayer membership and cross-scene movement converge here.

`Scene` owns the scene's players, entities, loaded blocks/groups, script manager, dungeon/challenge state and scene scheduler. See [`world-scene.md`](world-scene.md) before changing scene transition synchronization.

## Triage by symptom

| Symptom | First places to inspect |
|---|---|
| Client cannot reach server | `Grasscutter`, HTTP/dispatch config, KCP bind, Fiddler/client redirect |
| Connects but never reaches login | `GameSession` decryption/frame magic, token/login handlers, session state |
| Unknown/missing packet | `PacketOpcodes`, `GameServerPacketHandler`, matching handler, then Genshin-Reverse |
| Login succeeds then scene hangs | player login handler path, `World`, `Scene`, scene-entry packets, script/resource loading |
| One world freezes | `GameServer.onTick()`, `World.onTick()`, `Scene.onTick()`, exception logs |
| Whole server appears frozen | tick thread, lock ordering, database watchdog, scheduler/thread-pool health |
| Multiplayer join/leave/teleport bug | `MultiplayerSystem`, `World.addPlayer/removePlayer`, `Scene.addPlayer/removePlayer`, team sync packets |
| NPC/gadget/monster missing | resource/spawn data, scene block/group loading, `SceneScriptManager`, entity creation |
| Quest changes time/weather/scene incorrectly | quest exec/condition implementation plus `World` state and relevant packets |
| Character mechanic wrong | ability/manager implementation, current 7.1 resource fields, protocol only if client/server sync is involved |
| Progress lost or saves lag | `DatabaseHelper`/database executors, player/world save call sites, watchdog/backpressure |

## Cross-repository decision rule

Use this order to avoid guessing:

```text
Known server bug?
  -> inspect AstaPS implementation/history

Data looks absent/wrong?
  -> inspect AstaPS-Resource and resource loader/data classes

Packet/client semantics uncertain?
  -> query Genshin-Reverse canonical 7.1 artifacts
  -> add focused reverse investigation only if canonical evidence is insufficient

Comparable server has an implementation?
  -> use it as a lead
  -> still verify version-specific assumptions before copying constants or packet IDs
```

## Documentation maintenance

Architecture notes should change much less often than implementation code. Update these files when an ownership boundary, critical execution path, concurrency invariant or investigation workflow changes. Do not duplicate ordinary class documentation here.
