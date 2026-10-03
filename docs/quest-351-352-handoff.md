# Quest 351 to 352 handoff

Inspected against `experiment/no-legacy-fallback` at
`00969f3ba23b715c554d9ba5d0d26ecb7f2c18d9`.

## Resource and code evidence

- The published [QuestExcelConfigData](https://github.com/MeChen618/AstaPS-Resource/blob/main/ExcelBinOutput/QuestExcelConfigData.json)
  (blob `e52a8271f6da55b7250a05c3af33e5cd56872b0b`) gives 35200 exactly one
  accept condition: `QUEST_COND_STATE_EQUAL`, parameters `[0, 3]`. Thus
  `opensUnlinked(352)` is **true** for this pack; an absent quest 0 cannot be
  finished through `ConditionStateEqual`.
- `ResourceLoader.loadAll()` loads Excel resources before `BinOutput/Quest`.
  `FileUtils.getExcelPath()` selects a table in `resources/Server` first,
  otherwise `ExcelBinOutput`; within either directory the priority is TSJ,
  JSON, then TSV. This is selection, not merging both Excel tables.
- `MainQuestData.onLoad()` then calls `QuestData.applyFrom()`, which copies
  **only** `isRewind` and `finishParent`. It does not overwrite `acceptCond`.
- [351.json](https://github.com/MeChen618/AstaPS-Resource/blob/main/BinOutput/Quest/351.json)
  (blob `bb3ff6a404a596f973bf903124e1c6b464b87d07`) marks 35102 as
  `finishParent: true`, and suggests main quest 352. Its final content trigger
  is 1017 (`ENTER_REGION_35`, group 133003901, scene 3).
- `GameQuest.finish()` calls the parent's `finish()` when that merged flag
  is true. `GameMainQuest.finish()` saves the finished parent, grants its
  rewards, then performs the handoff. With no existing 352 parent and this
  pack, the original code should start 35200.

These establish the code path, not what happened in the user's running
session. Historical conversation retrieval was rate limited, and no runtime
log or player save was available. In particular, a missing 35102 completion
or a server-local Excel override remains possible.

Both reference projects checked, [LunaGC](https://github.com/girluh/LunaGC/blob/7.0.0/src/main/java/emu/grasscutter/game/quest/GameMainQuest.java)
and [HunkyMeow](https://github.com/AzureXuanVerse/HunkyMeow/blob/development/src/main/java/emu/grasscutter/game/quest/GameMainQuest.java),
still comment out the suggestion-based handoff. Their implementation does
not repair this resource pack's missing prerequisite.

## Fixed failure cases

1. A finished parent loaded from the database never replayed a missed handoff.
   Login now resumes only its handoff, after rewinding existing active quests.
   It does not call `finish()` or grant rewards again. A recovery exception is
   logged without failing the rest of login.
2. An existing successor parent was treated as already started even if every
   child was `UNSTARTED`. That parent can now start its opening. Any child
   already started, finished, or failed prevents a forced restart, as does a
   finished parent.
3. The unlinked predicate now requires the full `[0, FINISHED]` condition;
   quest 0 in state 0 is a satisfiable absence check and must not be forced.

The startup `[quest351] resources` line reports the selected Excel path,
35102's merged finish-parent flag, the successor list, and 35200's actual
accept conditions. Completion and handoff logs distinguish:

```text
[quest351] lifecycle action=finish ... sub=35102 ... finishParent=true
[quest351] main-finish ...
[quest351] handoff ... next=352 unlinked=true canStart=true
[quest351] handoff-result ... openingState=QUEST_STATE_UNFINISHED
```

If 35102 never finishes, investigate trigger 1017 before changing the 352
predicate. If the resource summary differs, investigate the selected table
or failed BinOutput load. An attempt without a result indicates an exception
during successor startup. `canStart=false` with an existing started successor
is intentional and does not reset that save.

## Local validation

`MainQuestHandoffTest` covers the Excel/BinOutput merge, real prerequisite
preservation, empty-parent startup and idempotence, protection of later or
failed steps, actual parent completion calling the handoff once, and login
recovery without repeating parent completion. The tests supply their own
quest data and do not start a game server or MongoDB.

The branch also had a compile blocker: `BattlePassCommand` referenced a
removed `BattlePassCompatHelper`. The command now reads the manager's
supported status directly and reports unsupported changes. The manager
currently always reports paid; no reflection fallback is restored.

CI was not run. Use Java 21 for a local build, then restart the server and
relog the affected account. Do not use `quest add` or `quest finish` while
validating natural progression; those commands bypass the path being tested.

```powershell
git fetch origin experiment/no-legacy-fallback
git switch experiment/no-legacy-fallback
git merge --ff-only origin/experiment/no-legacy-fallback
if ($LASTEXITCODE -ne 0) { throw "Branch update failed" }
.\gradlew.bat jar -PskipHandbook=1 -PjarFilename=grasscutter
if ($LASTEXITCODE -ne 0) { throw "Build failed" }
java -jar .\grasscutter.jar
```

Optional local regression check:

```powershell
.\gradlew.bat test --tests emu.grasscutter.game.quest.MainQuestHandoffTest -PskipHandbook=1 -PexcludeTags=integration
```
