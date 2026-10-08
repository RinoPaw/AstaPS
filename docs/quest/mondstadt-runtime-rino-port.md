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
- replay of already recorded quest Lua progress and dungeon completion.

## Open

Weather action `QUEST_EXEC_SET_WEATHER_GADGET` in 35901 requires correct
client effect realization. WeatherExcel identifies its first parameter as a
weather-area ID: 3 = Mondstadt city storm, 1 = normal Mondstadt weather.
The second parameter is a 0/1 activation flag. This is **not** equivalent to
choosing a scene or a climate value. No guessed handler is included.

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
