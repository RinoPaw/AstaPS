# Quest 351 to 352 handoff

Rebuilt directly on `play/rino` at
`e976250f8a3c0c4d596e2cb9a348017d999b23c7`, on branch
`fix/quest-351-352-handoff`.

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

The subsequent runtime log on 2026-10-03 confirms these resource values. At
23:01:17, UID 70580 finished 35102 with `finishParent=true`, then logged
`main-finish` and `handoff next=352 unlinked=true canStart=false`; the result
was `openingState=QUEST_STATE_UNSTARTED`. Thus 351 completion and its handoff
both executed, and the skip happened in the existing-parent protection.

`PlayerProgressManager.onPlayerLogin()` runs after `QuestManager.onLogin()`.
Its statue setup called `addQuest(35205)` and silently marked it finished,
even with questing enabled. This creates main quest 352 before the handoff,
with 35200-35204 unstarted and only the final, hidden step 35205 finished.
The guard correctly rejects that inconsistent parent. The earlier regression
fixtures did not run this statue setup, so they missed the actual producer.

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
4. Statue setup now pre-finishes 35205 only when questing is disabled. With
   questing enabled, the Archon Quest keeps ownership of that step. The
   existing login rewind repairs the affected save: it selects 35200, clears
   the synthetic later completion, and starts the opening. The following
   statue setup no longer writes the synthetic completion back. No special
   database edit or weaker handoff guard is needed.

The startup `[quest351] resources` line reports the selected Excel path,
35102's merged finish-parent flag, the successor list, and 35200's actual
accept conditions. Completion and handoff logs distinguish:

```text
[quest351] sub-finish ... sub=35102 finishParent=true
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

Additional regression cases execute the real progress-manager login
setup: it must not consume 35205, and the normal parent rewind must clear the
old synthetic finish without statue setup restoring it. A third case preserves the
questing-off statue bypass. Ten cases pass.

## Follow-up: repeated Paimon talk in 352

The 23:22:38 log for UID 70581 confirms that the repaired handoff starts
35200 (`unlinked=true canStart=true`, opening `UNFINISHED`). That trace has
no 352 subquest or received talk/content diagnostics, so it does not prove
which later step stopped or whether 35203's failure rollback ran.

The talk validation did contain a separate defect: `TalkManager` compared
`TalkConfigData.npcId` against an entity's **scene placement configId**.
`EntityNPC.getEntityTypeId()` is the NPC/model identity (1005 for Paimon),
while its configId can be a different group-local number or zero for a quest
NPC. A completed client dialogue could therefore receive `NpcTalkRsp` while
the manager returned without recording the talk or queuing COMPLETE_TALK.
Validation now uses the NPC/model id. The request handler prefers
`npc_entity_id`, keeping `entity_id` as a fallback when the former is zero.
Client-local quest actors without a server entity remain supported.

The [Genshin-Reverse talk-chain findings](https://github.com/RinoPaw/Genshin-Reverse/blob/main/versions/7.1.0-global/windows-x64/analyses/statue-unlock/FINDINGS_2026-10-03_QUEST_TALK_CHAIN.md)
cross-check the 7.1 NpcTalkReq opcode/field mapping and the talk-to-quest path.
The finish/not-finish plot handlers in both LunaGC revisions inspected are
the same as AstaPS; there is no independent fix to copy for the suspected
35203 rollback. Its legitimate cancellation/team-death behavior is retained.

Three NPC identity cases cover a Paimon placement with a different configId,
a different NPC whose configId happens to be 1005, and a client-local actor.
Together with the ten handoff/login cases, thirteen local tests pass.

The new `[quest352]` logs cover talk reception/validation, single and batch
content reports for 352xx, start/finish/fail/rewind, and matched failure
conditions. The expected next transition after talk 35216 is finishing
35202 and starting 35203. If `fail-condition` precedes `rewind sub=35202`,
the trace identifies the exact rollback trigger; if a plot report never
arrives, it identifies a different gap. Do not force-finish 352 to mask it.

## Runtime validation: UID 70583 (2026-10-03)

The run on code commit `9d61066` confirms 35102 completion at 23:48:41,
the parent finish, and a successful handoff at 23:48:42:
`unlinked=true canStart=true`, with 35200 becoming `UNFINISHED`.
35200 and 35201 then finish, and talk 35216 finishes 35202.

At 23:49:45 the client sends `NOT_FINISH_PLOT(35203)`. The matched failure
condition logs `progress=[1,0]`, then 35203 fails and 35202 is rewound and
restarted. Another identical content report arrives, but no second
failure/rewind is observed. The repeated Paimon dialogue in this sample is
therefore a configured rollback, **not a rejected NPC identity check**.
Both talk requests are accepted through the client-local actor path
(`npc_entity_id=0`, fallback entity lookup absent). The earlier NPC identity
defect remains a separate fix; this trace does not establish that it caused
the previous repetition.

The second talk at 23:49:57 restarts 35203; 35203 and 35204 subsequently
finish. At 23:51:24, the client sends `FINISH_PLOT(35205)` and 35205 is marked
finished. No received `FINISH_PLOT(35203)` appears in the trace; trigger 1172
is another configured completion path, so the exact completion source and
client performance need further checking.

The log ends at the 35205 child completion. It does not independently
verify main quest 352 completion, finishExec side effects, rewards,
persistence, or acceptance of 353. The client reason for the earlier
`NOT_FINISH_PLOT` is also unconfirmed and will be checked against Playthrough
video; the failure condition is retained.

Other observed errors include login while resources are still loading
(AvatarData is null), a missing JSON field in
InvestigationMonsterDropHelper.loadPreviews, and a failed entity creation
for group 133003090/config 472. They are separate pending investigations;
this trace does not show that they caused the rollback.

Reusable diagnosis steps, acceptance boundaries, and the Playthrough
checklist are maintained in [Quest debugging notes](quest-debugging-notes.md).

CI was not run. Use Java 21 for a local build, then restart the server and
relog the affected account. Do not use `quest add` or `quest finish` while
validating natural progression; those commands bypass the path being tested.

```powershell
git fetch https://github.com/RinoPaw/AstaPS.git fix/quest-351-352-handoff
if ($LASTEXITCODE -ne 0) { throw "Fetch failed" }
git show-ref --verify --quiet refs/heads/fix/quest-351-352-handoff
if ($LASTEXITCODE -eq 0) {
    git switch fix/quest-351-352-handoff
} else {
    git switch -c fix/quest-351-352-handoff FETCH_HEAD
}
if ($LASTEXITCODE -ne 0) { throw "Branch switch failed" }
git merge --ff-only FETCH_HEAD
if ($LASTEXITCODE -ne 0) { throw "Branch update failed" }
.\gradlew.bat jar -PskipHandbook=1 -PjarFilename=grasscutter
if ($LASTEXITCODE -ne 0) { throw "Build failed" }
java -jar .\grasscutter.jar
```

Optional local regression check:

```powershell
.\gradlew.bat test --tests emu.grasscutter.game.quest.MainQuestHandoffTest --tests emu.grasscutter.game.talk.TalkNpcIdentityTest -PskipHandbook=1 -PexcludeTags=integration
```
