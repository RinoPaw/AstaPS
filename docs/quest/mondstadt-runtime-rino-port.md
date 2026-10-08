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
