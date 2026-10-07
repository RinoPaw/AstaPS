# Native 7.1 Quest overlay

AstaPS can optionally consume `data/quests.json` as a third quest source after
`QuestExcelConfigData.json` and legacy `BinOutput/Quest/*.json`.

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
- native-only subquest rows are not materialized into runtime yet.

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
  "quests": [
    {
      "mainId": 351,
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

The server treats this file as an overlay. Removing `quests.json` returns AstaPS to its existing
QuestExcel + legacy BinOutput behavior.
