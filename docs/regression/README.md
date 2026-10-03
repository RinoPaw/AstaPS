# Regression checks

This directory records behavior that is important enough to preserve but cannot always be covered by a clean-checkout unit test. Prefer an automated test whenever practical; use this document for live-client/resource-dependent checks.

## Fast automated baseline

A clean checkout should be validated with JDK 21:

```bash
./gradlew jar -PskipHandbook=1
./gradlew test -PexcludeTags=integration
```

On Windows:

```powershell
.\gradlew.bat jar -PskipHandbook=1
.\gradlew.bat test -PexcludeTags=integration
```

`GrasscutterTest`-style integration tests may require MongoDB and the 7.1 resource pack, so they are intentionally outside the clean-checkout CI baseline.

## How to add a regression

For every bug fix, preserve the failure in one of these forms:

1. a small JUnit test that runs without a live client;
2. a focused integration test when server/resources are genuinely required;
3. a short manual scenario below when only the real 7.1 client can prove the behavior.

A manual scenario should state setup, actions, expected behavior and useful logs/evidence if it fails. Keep it short enough that somebody will actually rerun it.

## Core smoke test

Use this after changes that touch shared startup/session/world code:

- Start MongoDB and AstaPS with the matching 7.1 resources.
- Start a 7.1 Global Windows client redirected to the dispatch server.
- Sign in to an existing account.
- Confirm login reaches the open world without an encryption/magic warning.
- Teleport once within the current world.
- Trigger one ordinary interaction such as opening a chest, talking to an NPC or collecting an item.
- Log out cleanly and confirm the player is removed without a tick/session cleanup exception.

Expected: the world continues ticking, normal packets continue flowing, and no unrelated player/world is affected by the session lifecycle.

## Fresh-account / born flow

Relevant when changing login, initial character selection, born position or first scene entry.

Setup: use a new account with no player character initialized.

Check:

- sign in and reach character selection;
- choose the traveler;
- enter the expected 7.1 initial scene/position;
- confirm the scene load completes and normal movement/input begins;
- reconnect once and confirm the account no longer returns to the born-selection state.

If it fails, capture the session state transition and the scene-entry packets around `SetPlayerBornDataReq` and first `PlayerEnterScene` flow.

## Multiplayer join / leave / teleport

Relevant when changing `MultiplayerSystem`, world/scene membership, team sync, peer IDs or enter-scene handling.

Setup: two accounts, A hosting and B joining.

Check:

- A enters the open world normally;
- B requests to join and reaches A's world;
- both clients can see each other's active character;
- both can move and interact;
- host teleports within the same world and the session remains usable;
- guest changes/loads scene where supported by the normal flow;
- B leaves or disconnects;
- A remains responsive and the world continues ticking;
- reconnect B and repeat once to catch stale world/scene membership.

Failure evidence: host/guest UID, peer IDs, world/scene IDs, scene-load state and the last relevant MP/enter-scene packets.

## Home-world login versus game tick

Relevant when changing `GameServer.onTick()`, `homeWorlds`, world registration or synchronization.

Check with one player logging in/loading home-world state while another normal world is active. Repeatedly enter/leave the home world or reconnect the owner while observing the server.

Expected: no deadlock; existing open-world players continue moving and receiving updates; `/api/status`/runtime monitor continues advancing rather than showing a stuck game loop.

If it fails, collect a thread dump before restarting the process. Lock ownership is more useful than packet spam for this regression.

## World exception isolation

Relevant when changing tick exception handling.

Use a controlled development-only fault or known reproducible bad world/scene action.

Expected: the affected world logs the exception, while other worlds continue ticking and the server scheduler still runs. A single world failure must not kill the timer/game loop.

## NPC / scene-group loading

Relevant when changing NPC spawn-table parsing, `/npc`, `SceneScriptManager`, scene group loading or 7.1 obfuscated resource keys.

Check:

- enter a scene with a known NPC group;
- load the group through the same path normal scene scripts use;
- confirm the NPC is placed in the player's loaded block and is visible/interactable where applicable;
- unload/re-enter the area and confirm there is no duplicate/stale entity;
- if using `/npc` as a diagnostic, compare it with normal group loading rather than treating command-spawn success as proof that script loading works.

If it fails, record scene ID, group ID, config ID/NPC ID and whether the resource row, group load and entity creation each occurred.

## Quest weather/time lock

Relevant when changing quest execs, weather/time state, `PROP_IS_GAME_TIME_LOCKED` or world-time handling.

Check a quest path that locks world state and then unlocks/completes/skips it.

Expected: the property and `World.timeLocked` state agree; the client receives the updated time state; after unlock or the intended quest transition, world time advances again and remains correct after relogin.

If the open world appears frozen without exceptions, inspect `PROP_IS_GAME_TIME_LOCKED` before diagnosing the game tick.

## Mavuika / Skirk special energy

Relevant when changing 7.1 skill-resource mapping, special-energy fields, Nightsoul action names or energy synchronization.

With the matching 7.1 resource pack:

- add/use Mavuika and exercise the mechanic that consumes/builds her special energy;
- add/use Skirk and exercise her special-energy flow;
- verify the UI resource changes in response to the expected actions;
- swap characters/teams and re-enter a scene to catch state that only initializes once;
- relog and repeat the core action once.

Expected: energy state is sourced from the 7.1 skill/resource fields and remains synchronized after team/scene lifecycle changes. If it fails, record avatar ID, skill/action, resource field values loaded by the server and the related notify/state packets.

## Packet/CmdId regression

Relevant whenever a packet mapping, alias or handler registration changes.

Check:

- the observed opcode is tied to current 7.1 evidence in Genshin-Reverse;
- direction (client request / server response or notify) is established;
- the annotated handler registers with a positive opcode;
- the session state permits the request at the point it is sent;
- the response uses a current positive opcode and is not silently suppressed by `GameSession.send()`;
- no broad shape-based fallback starts consuming unrelated unknown packets.

For unresolved mappings, keep the state unresolved and capture evidence; do not turn a plausible parse into a canonical semantic name just to make the test pass.

## User test handoff template

When the maintainer cannot run the client, provide a single copy-paste sequence that starts by fetching the exact remote state. Example shape for PowerShell:

```powershell
git fetch origin
# checkout the exact branch or commit supplied by the maintainer
git switch <branch>
git pull --ff-only origin <branch>
.\gradlew.bat jar -PskipHandbook=1
java -jar .\grasscutter.jar
```

Then give only the smallest in-game action sequence needed to distinguish success from failure, plus the exact logs/state to return on failure.
