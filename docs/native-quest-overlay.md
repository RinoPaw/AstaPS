# Native 7.1 Quest overlay

AstaPS consumes `resources/quests.json` as the native MainQuest truth layer after
`QuestExcelConfigData.json`. Legacy `BinOutput/Quest/*.json` remains optional: when that
directory is absent, the native bundle materializes the parent MainQuest skeleton from proven
`mainId`, `subId`, and `order` fields.

The file is generated from the fail-closed Genshin-Reverse native MainQuest decoder. Its purpose is
to carry fields proven from the 7.1 client wire format into the server without importing synthetic
legacy prerequisite chains.

Source policy in the first parser revision:

- native `mainId` and `order` are authoritative when a matching QuestExcel row exists;
- native `finishCond`, `failCond`, `finishExec`, and `failExec` may override runtime data only
  when every type in that list has independently confirmed 7.1 semantics;
- an absent native finish/fail list is an authoritative empty list;
- a list containing an unresolved numeric type id is parsed but remains audit-only;
- `acceptCond` and `beginExec` do not exist in the native contract and are never synthesized;
- native-only subquest rows are not materialized into the QuestExcel runtime map yet;
- missing parent MainQuest objects are materialized from native `mainId/subId/order` so
  `GameMainQuest` can be created without legacy BinOutput/Quest files;
- native `suggestTrackMainQuestList`, `rewardIdList`, and talk ids are copied into the
  runtime parent when present, so native-only parents can hand off, grant parent rewards, and
  register talk ownership without legacy BinOutput/Quest files.

The first confirmed semantic set is intentionally narrow:

- QuestContent 4 FINISH_PLOT
- QuestContent 6 TRIGGER_FIRE
- QuestContent 21 TEAM_DEAD
- QuestContent 23 UNLOCK_TRANS_POINT
- QuestExec 14 ROLLBACK_QUEST
- QuestExec 17 LOCK_POINT
- QuestExec 19 REFRESH_GROUP_SUITE

This gate can expand as Genshin-Reverse proves more native type identities.


## Minimal bundle shape

```json
{
  "schemaVersion": 1,
  "version": "7.1.0-global",
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
stay in `failedMainQuests` with their failure family. AstaPS validates the top-level coverage counts
before applying any overlay.
