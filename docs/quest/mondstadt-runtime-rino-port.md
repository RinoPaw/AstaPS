# Mondstadt prologue 7.1 — play/rino runtime port

Baseline: `play/rino` (initial commit `92c3fab`).  
Working branch: `fix/mondstadt-runtime-rino-71`.

## Source precedence

The existing `QuestData` / `NativeQuestOverlay` architecture is preserved.
Do not replace it with the older integration branch's broad `applyFrom`
policy. The 7.1 native Quest overlay remains authoritative for independently
decoded finish/fail conditions and finish/fail execution lists.

A reviewed compatibility exception covers the 40 Mondstadt prologue
MainQuests only. It allows historical BinOutput to supply:

- acceptance prerequisites and their combinators, absent from ordinary native
  7.1 Quest records;
- beginning actions and rewards only when QuestExcel has no populated list;
- independently checked multi-predicate finish combinators for 30710,
  30810, 30814, 30901, 31101, 35901;
- independently checked multi-predicate fail combinators for 35203,
  37602, 39703, 38802, 39404.

All other fields retain the existing per-field source policy. No BinOutput-only
subquest row may be inserted into runtime merely because the Bin file exists.
Each selected compatibility value reports source `BIN_OUTPUT` via
`getFieldSource`; native values report `NATIVE_QUEST`.

## Runtime safety improvements

Ported changes handle:

- QuestExec enum name/value lookups;
- source-specific scene-entry and group-clear event matching;
- exclusion of dead-but-not-yet-removed monsters from group clear checks;
- the last-party-member-dead quest failure event;
- independent delivery of content events when another subquest throws;
- persisted and legacy quest group-suite state, including login rehydration;
- replay of already recorded quest Lua progress and dungeon completion;
- verified 7.1 weather-area/gadget activation and cross-scene cleanup.

## Remaining validation

Quest weather action `QUEST_EXEC_SET_WEATHER_GADGET` now has a
WeatherExcel-aware server handler. It interprets parameter 1 as the weather
area ID and parameter 2 as a 0/1 activation flag; the handler validates the
area's WeatherExcel gadget and scene before updating the player's area
notification. 7.1 `SceneAreaWeatherNotify` has a separate
`weather_gadget_id` field; this now comes from WeatherExcel
(area 3 = Mondstadt city storm / gadget 70020003; area 1 = Mondstadt
general / gadget 70020001). The independent 35901 actor 70700004 stays
untouched. Deactivation only resets the player's override when it targets
the currently selected area.

All 20 reviewed quest-action types have a concrete registered Java handler,
including weather gadget actions. The source coverage test and paired
resource audit agree on all 20 types.

**Client behavior still needs verification**: this first pass uses the
existing per-player weather notification. It does not yet track concurrent
weather gadgets as a world-wide scene state, automatically restore active
quest weather after reconnection, or implement polygon crossing and client
SetSceneWeatherAreaReq handshakes. Do not claim full visual/weather parity
until those flows are validated on the 7.1 client.

This branch has unit/static coverage but still requires paired 7.1 resource
validation and full-client scene and battle testing before upstream merge.

## Persist queued suite transitions after quest start

`GameQuest.start()` saves its new state immediately after queueing beginExec.
`SceneScriptManager.refreshGroupSuite` applies the suite on the quest worker
later. The old code updated the in-memory `questGroupSuites` list but never
saved it, so a server restart could restore a pre-refresh group. A successful
change now triggers `quest.save()` after the list lock is released. Duplicate
refreshes and resets are no-ops; malformed legacy duplicate entries are
normalized on the next successful refresh.

## Release group unload protection after a successful reset

`ExecRefreshGroupSuite` previously set `group.dontUnload = true` for every
refresh, including suite 0 (the default reset). That flag remained sticky after
the saved override had been removed, so expired quest groups stayed loaded
outside player visibility. The group is still pinned while its suite changes;
a successful reset releases the pin unless another active quest override
references the group. Failed refreshes restore the previous pin. This logic
is covered by regression tests.

## Active quest weather after relog / returning to scene

Player.weatherId and Player.climate are transient. When the server restarts
during 35901, the quest still has state UNFINISHED but weather defaults to
clear even though the storm action ran at quest start.

At SceneInitFinishReq, the QuestManager now examines **only active subquests**.
A quest weather activation is rehydratable when its beginExec activates a
WeatherExcel gadget and the same subquest's finishExec explicitly deactivates
that area. The weather area's owning scene and valid gadget ID must match,
and competing active areas are left unchanged rather than choosing one
arbitrarily. Existing nonzero weather selections are never overwritten.

The restore operation runs before the initial scene weather notify and sends
that notify through Player.setWeather exactly once. State is reconstructed
from persisted quest progress and source-gated native/resource definitions,
without adding a new persistent weather field or duplicating action scripts.
Regression tests cover 35901 in scene 3, leaving Mondstadt, missing resets,
malformed parameters, foreign-scene data and ambiguous active areas.

This remains a per-player weather model. Polygon/area transitions and
world-wide multi-gadget weather composition need client-level confirmation.

## Acceptance event isolation and failed-state persistence

The same source event can satisfy acceptance prerequisites for multiple
subquests. A malformed condition previously aborted the entire acceptance
batch. QuestManager now reuses QuestContentDispatch for candidate isolation,
with the failed quest ID and event opcode in the diagnostic log; remaining
candidates are still checked. QuestContentDispatchTest covers this scenario.

GameQuest.fail previously mutated the failed quest state and sent client
notifications without saving the parent quest. It now persists the failure
transition, so a disconnect before rewind cannot restore an obsolete active
battle state. This retains the normal failExec and team-cleanup ordering.

## Reviewed 35901/39403 finish-action recovery

NativeQuestParser still treats WEATHER_GADGET numeric opcode as unconfirmed,
so any 7.1 native finishExec list containing it remains audit-only. QuestExcel
may also provide an empty finishExec list, leaving the weather activation
without its corresponding cleanup on quest completion. Historical GCResource
3.7 and 4.0 and the paired 7.1 BinOutput agree on **exact action ordering**:

- 35901: SET_WEATHER_GADGET(3,0), then SET_WEATHER_GADGET(1,0).
- 39403: REMOVE_TRIAL_AVATAR(5), SET_WEATHER_GADGET(2,0),
  NOTIFY_GROUP_LUA(3,133007183).

Only these two subquest IDs can recover those exact finishExec sequences, and
only if QuestExcel has no populated finishExec. Unexpected params or opcodes
invalidate the entire fallback; explicit QuestExcel actions remain authoritative.
All other BinOutput finishExec stay subject to the original audit-only policy.

## Cross-scene Act III cleanup

Q394 subquest 39403 uses quest actors in dungeon scene 1008 but its finish
notification targets scene 3 Lua group 133007183. The group's native
QUEST_FINISH trigger expects (39403, success=1) and references gadgets in
five other scene-3 groups. ExecNotifyGroupLua now resolves the scene given
by the quest action and defers until target script initialization and scene
load. It retains the queued quest-state snapshot so obsolete actions cannot
be reclassified as a different quest event.

ScriptLib.KillEntityByConfigId now honors the explicit Lua `group_id` argument
instead of always searching the currently executing trigger group. This is
required for all five cross-group Q394 cleanup targets. Tests cover the scene
transition gate, quest event type, and explicit/default target group IDs.

## Permanent Lua gadget removal after 39403

The scene-3 cleanup group 133007183 calls `KillEntityByConfigId` for
five other group IDs. Four of the native 7.1 seal gadgets have no
`isOneoff/persistent` metadata and can respawn on group reload. The Lua
operation now stores an explicit script-destroyed gadget config ID in its
own scene group record, including when a target is outside the visible grid.
The scene-group gadget spawn filter honors this separate tombstone for any
gadget type. Ordinary combat deaths continue to use the original temporary
or oneoff/persistent rules. Unit tests cover both paths and legacy group
records without the new optional field.

## Scene loaded before returning player

When 39403 completes in dungeon scene 1008, Mondstadt scene 3 may already
have completed its loading cycle while having no players. Using only
`runWhenFinished` immediately invoked the cleanup callback and discarded it
because the player was still in the dungeon. The target scene now also waits
for that specific player to enter; script initialization, player presence,
and scene loading must all be satisfied before the saved quest-finish event
is delivered. The new player-entry callback gate runs callbacks once and is
tested for both callback-registration orders and concurrent scene returns.

## Repeated dungeon clear events and durable first-clear history

PlayerProgress.markDungeonAsComplete previously returned immediately when the
completed-dungeon history already contained an ID. This prevented the quest
engine from receiving FINISH_DUNGEON on any later successful run of the same
domain. The history now records each unique dungeon once, but emits a quest
event on **every** successful settlement. The first clear is saved immediately
so a late-activated quest (e.g. 30901) can replay its historical objectives
after reconnection. Unit tests cover repeated and distinct clears.
