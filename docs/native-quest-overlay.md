# Native 7.1 Quest overlay

AstaPS consumes `resources/quests.json` as the native MainQuest truth layer after
`QuestExcelConfigData.json`. Legacy `BinOutput/Quest/*.json` remains optional: when that
directory is absent, the native bundle materializes the parent MainQuest skeleton from proven
`mainId`, `subId`, and `order` fields.

The file is generated from the fail-closed Genshin-Reverse native MainQuest decoder. Its purpose is
to carry fields proven from the 7.1 client wire format into the server without importing synthetic
legacy prerequisite chains.

Source policy in the first parser revision:

- native `mainId`, `order`, `isRewind`, and `finishParent` are authoritative when a matching QuestExcel row exists;
- native `finishCond`, `failCond`, `finishExec`, and `failExec` may override runtime data only
  when every type in that list has independently confirmed 7.1 semantics;
- an absent native finish/fail list is an authoritative empty list;
- a list containing an unresolved numeric type id is parsed but remains audit-only;
- `acceptCond` and `beginExec` do not exist in the native contract and are never synthesized;
- `isRewind` is consumed by rewind target selection and `finishParent` decides whether finishing a child quest completes its parent, so both flags are carried from the exact native row rather than left to legacy BinOutput fallback;
- native-only subquest rows are not materialized into the QuestExcel runtime map yet; the current
  115-MainQuest bundle has 1,261 native rows and all 1,261 already exist in the 33,217-row
  QuestExcel table, so this limitation has no effect on the current mainline resource set;
- missing parent MainQuest objects are materialized from native `mainId/subId/order` so
  `GameMainQuest` can be created without legacy BinOutput/Quest files;
- native `suggestTrackMainQuestList`, `rewardIdList`, and talk ids are copied into the
  runtime parent when present, so native-only parents can hand off, grant parent rewards, and
  register talk ownership without legacy BinOutput/Quest files.

The runtime gate follows the Genshin-Reverse product contract instead of maintaining a second
handwritten allowlist. Genshin-Reverse emits a `type` name only when the numeric type id has been
aligned against the exact 7.1 Quest corpus. AstaPS accepts a native condition/exec only when:

1. the exported `type` name is present;
2. AstaPS has an enum constant with that exact name;
3. the enum constant's numeric value equals the exported `typeId`;
4. AstaPS has an actual runtime handler for that condition/exec.

If any check fails, the whole native list remains audit-only and the QuestExcel runtime list is
left untouched.

At the current Genshin-Reverse semantic set and AstaPS handler inventory this makes 39
QuestContent ids and 37 QuestExec ids directly usable by this branch. On the companion
115-MainQuest resource bundle (1,261 subquest rows), 2,116 finish/fail condition or exec lists are
non-empty. The old bootstrap allowlist could authoritatively apply 557 of them; the current
semantic-plus-handler gate applies 2,022, a gain of 1,465 lists. The remaining 94 stay on the
QuestExcel compatibility data.

Types such as `QUEST_CONTENT_CITY_LEVEL_UP` and `QUEST_EXEC_SET_WEATHER_GADGET` are
semantically identified but remain audit-only because the current server has no matching handler.
The latter affects native finish execs in Mondstadt MainQuests 359 and 394, but no maintained
Grasscutter/Luna implementation was found and the 7.1 client probe currently proves only the enum
identity. The QuestExcel fallback rows 35901 and 39403 were compared against the native bundle and
their finish-exec type/parameter sequences are identical, so leaving these two lists on the
compatibility source does not change current Mondstadt behavior. This keeps native truth from
replacing compatibility data with behavior the server cannot execute.


## Minimal bundle shape

```json
{
  "schemaVersion": 1,
  "gameVersion": "7.1.0-global",
  "coverage": {
    "total": 4417,
    "fullConsumed": 3993,
    "failed": 424
  },
  "mainQuests": [
    {
      "mainId": 351,
      "payloadSize": 920,
      "payloadSha256": "285d825bedce0611494d114687bc7981789b0ef6d0a724f5abfecf423b6335de",
      "resId": 1001,
      "quests": [
        {
          "mainId": 351,
          "subId": 35100,
          "order": 2,
          "finishCond": [
            {
              "typeId": 4,
              "type": "QUEST_CONTENT_FINISH_PLOT",
              "param": [35100, 0]
            }
          ]
        }
      ]
    }
  ]
}
```

When legacy `BinOutput/Quest` exists, the native file overlays its proven fields. When that
directory is absent, the native file also supplies the minimal parent-quest structure required by
the runtime. Removing `quests.json` therefore requires restoring legacy MainQuest resources.


The current 7.1 exporter emits only fully consumed payloads in `mainQuests`. Unsupported payloads
stay in `failedMainQuests` with their failure family. AstaPS validates the coverage counts and
native identity graph before applying any overlay: successful/failed MainQuest ids may not overlap,
MainQuest and SubQuest ids must be unique, every row must belong to its containing MainQuest, and
present payload SHA-256 values must be well formed. A malformed bundle fails closed before runtime
quest state is changed.


## Mainline-only resource checkpoint

The companion AstaPS-Resource branch `rino-native-mainline-quests` removes legacy
`BinOutput/Quest/*.json` and provides a 115-MainQuest native `quests.json` covering the
opening through Sumeru. The runtime loader therefore treats the legacy directory as optional and
materializes parent MainQuest skeletons from native data when it is absent.
