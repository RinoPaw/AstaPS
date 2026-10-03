# Startup and game networking

This note captures the shortest useful path from process start to a packet handler. Read it before debugging login, encryption, packet routing or server lifecycle issues.

## Process startup

The application entry point is `emu.grasscutter.Grasscutter`.

Its static initialization loads configuration and language state, configures logging and performs startup checks. `main()` then performs the runtime startup in this order:

```text
Crypto.loadKeys()
StartupArguments.parse()
CommandMap
DatabaseManager.initialize()
DefaultAuthentication / DefaultPermissionHandler
GameServer and/or HttpServer construction
PluginManager construction
HTTP route registration
optional early HTTP/dispatch start
ResourceLoader.loadAll()
post-resource system initialization
GameServer.start()
login Lua shell
runtime monitor
ServerWatchdog
plugins
shutdown hook
console
```

The ordering matters. `GameServer` is constructed before resources are loaded, so systems that depend on resource tables may have an explicit post-resource initialization step. Do not move resource-sensitive initialization earlier without checking this assumption.

Run modes are `HYBRID`, `DISPATCH_ONLY` and `GAME_ONLY`.

## HTTP and dispatch side

`HttpServer` owns the HTTP-facing routes used by dispatch/login-related client traffic and server APIs. Routes are registered from `Grasscutter.main()` after the plugin manager is constructed.

Important paths include:

```text
server/http/
server/http/dispatch/
server/http/handlers/
server/dispatch/
auth/
```

In normal hybrid operation the HTTP side and KCP game server live in the same process. `GAME_ONLY` connects to a separate dispatch server through `DispatchClient`.

When a client never reaches KCP, investigate HTTP/region/auth routing and the client redirect before touching game packet handlers.

## KCP game server

`GameServer` extends `KcpServer`. During construction it configures KCP, initializes the listener from `GameSessionManager`, creates `GameServerPacketHandler`, and constructs the main gameplay systems.

The inbound transport path is:

```text
KcpServer
  -> GameSessionManager.KcpListener
  -> one GameSession per Ukcp
  -> GameSessionManager.logicThread
  -> GameSession.handleReceive(byte[])
  -> GameServerPacketHandler.handle(...)
  -> PacketHandler subclass
```

`GameSessionManager` deliberately serializes received datagrams and close cleanup onto its `DefaultEventLoop` logic thread. If packet processing stalls globally while the game tick is still alive, inspect work running on this logic thread.

## GameSession responsibilities

`GameSession` owns:

- connection tunnel/address;
- account and player binding;
- login/session state;
- dispatch/session encryption key selection;
- frame decoding and framing validation;
- packet logging;
- outbound packet encryption and transport.

Inbound frames are XOR-decrypted when enabled. The session can try both the dispatch key and negotiated session key and uses frame magic to determine which one the client is actually using. A bad frame magic warning is therefore strong evidence of key/client-version mismatch; do not immediately diagnose it as a missing packet handler.

The decoded frame format is read as:

```text
head magic
opcode
header length
payload length
header bytes
payload bytes
tail magic
```

A valid frame is then handed to `GameServerPacketHandler`.

## Session-state gate

`GameServerPacketHandler` validates packet handling against `GameSession.SessionState` before invoking most handlers. Special transitions include token exchange, player login and initial character selection; normal gameplay requests require the session to be active.

When a known handler never runs, check both handler registration and session state before changing the handler itself.

## Handler registration

`GameServerPacketHandler` discovers `PacketHandler` subclasses through the global Reflections scan and registers handlers from their `@Opcodes` annotation. Non-positive, disabled or absent opcodes are skipped.

There are explicit compatibility registrations/aliases for cases where reflection or current protocol mapping requires special treatment. Treat these as exceptions. New protocol work should first establish the correct 7.1 identity rather than growing shape-based fallback routing.

Important paths:

```text
net/packet/PacketHandler.java
net/packet/PacketOpcodes.java
server/game/GameServerPacketHandler.java
server/packet/recv/
server/packet/send/
```

If an opcode or semantic name is uncertain, switch to `RinoPaw/Genshin-Reverse` and query the canonical 7.1 registry/evidence before assigning meaning.

## Outbound path

Gameplay code usually constructs a `BasePacket` subclass and sends it through the player's session:

```text
Packet* object
  -> GameSession.send()
  -> optional packet header build
  -> SendPacketEvent
  -> packet.build()
  -> XOR encryption when enabled
  -> KcpTunnel.writeData()
```

A packet with a non-positive opcode is suppressed. This commonly means the current target has no known CmdId for that packet class. Fixing that requires protocol evidence; changing the suppression is usually the wrong layer.

## Game loop versus network logic

The network logic thread and game tick are distinct.

`GameServer.start()` schedules `GameServer.onTick()` at the configured tick interval. The tick updates worlds, players and scheduled tasks. Packet handlers may mutate objects that are later observed by the tick, so concurrency changes must consider both paths.

Useful distinction during diagnosis:

- KCP traffic stops but world time continues: inspect session/network logic.
- Packets still arrive but every world stops progressing: inspect the game loop, database-down gate, world locks and tick exceptions.
- One player/world stalls while others continue: inspect that object and its scene/world code before the global server loop.

## Fast debugging sequence

For "client sent something and nothing happened":

1. Confirm the client reached KCP and a `GameSession` exists.
2. Look for bad frame magic/key mismatch.
3. Enable targeted packet logging rather than all noisy loop packets.
4. Confirm the opcode has a current 7.1 identity.
5. Confirm a handler is registered for it.
6. Confirm the current `SessionState` allows it.
7. Trace the handler into the owning gameplay subsystem.
8. Add a regression test if the fault can be reproduced without a live client; otherwise document the live check in `docs/regression/README.md`.
