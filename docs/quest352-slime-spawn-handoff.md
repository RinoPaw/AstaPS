# Quest 352 slime spawn handoff

Status captured 2026-10-05 from the fresh-account playthrough on `play/rino`.

## User-visible failure

A fresh account progressed naturally through the opening Mondstadt Archon Quest until the objective shown in the client was **Defeat the slimes**. The quest UI and navigation were present, but the expected slime encounter did not spawn.

Do not use `quest add`, `quest finish`, or manual database edits while reproducing. They bypass the lifecycle being investigated.

## Current repository heads at handoff

- `RinoPaw/AstaPS` `play/rino`: `b2eb97fbfeabbab509ed9abfb3ffa3b18a7cf94c`
- `RinoPaw/AstaPS-Resource` `main`: `b0f3a2791607cab2a4c24cb9ef249dd2d94d7ffd`
- The Resource head intentionally contains temporary discovery workflow `.github/workflows/tmp-quest352-slime-discovery.yml` for the **Unexpected Power** slime encounter. Inspect/remove it after the investigation is finished.

## Important correction: do not assume Quest 351 owns this encounter

Initial tracing found a real slime-related Quest 351 group refresh, but the current player-visible failure is more likely in **Quest 352**.

### Quest 351 evidence

`BinOutput/Quest/351.json` contains hidden subquest `35107` (order 3):

- finish condition: `QUEST_CONTENT_TRIGGER_FIRE 1101`
- finish exec: `QUEST_EXEC_REFRESH_GROUP_SUITE`, params `scene=3`, `group=133003429`, `suite=1`

`Scripts/Scene/3/scene3_group133003429.lua` is a real one-slime group:

- monster config id `1430`
- monster id `20011001`
- position around `(2701.025, 194.500, -1663.234)`
- `init_config.suite = 2`
- suite 1 contains the slime, region and triggers
- suite 2 is empty

Thus this group starts empty and Quest 351 can switch it to suite 1. This is valid evidence, but it must not be assumed to be the user's current **Defeat the slimes** objective without mapping the current subquest first.

### Quest 352 evidence

`BinOutput/Quest/352.json` currently has:

- `35200`: order 1, starts after `35102 FINISHED`
- `35201`: order 2, finishes on trigger fire `1001` or `1095`
- `35202`: order 3, finishes after talk `35216`
- `35203`: order 4, Paimon step with configured rollback on `NOT_FINISH_PLOT(35203)` / team death
- `35204`: order 5, visible Paimon-guided step, finishes on `QUEST_CONTENT_TRIGGER_FIRE 1003`
- `35205`: order 6, hidden final step, finishes parent

The Resource repository's temporary discovery workflow explicitly greps scene 3 for `3520[0-5]`, `35216`, `1172`, and especially trigger `1003`, and labels this as the **Unexpected Power slime encounter**. Therefore the next session should prioritize **35204 / trigger 1003** and only return to the 35107 group if the resource mapping proves they are the same encounter.

## Server-side spawn path already checked

The expected entity creation path is server-owned. The client does not request an individual slime spawn.

Relevant AstaPS flow:

```text
quest/script side effect
  -> SceneScriptManager.refreshGroupSuite(groupId, suiteId[, quest])
  -> refreshGroup(...)
  -> addGroupSuite(...)
  -> getMonstersInGroupSuite(...)
  -> createMonster(...)
  -> addEntities(...)
  -> SceneEntityAppearNotify to client
```

### `ExecRefreshGroupSuite`

`src/main/java/emu/grasscutter/game/quest/exec/ExecRefreshGroupSuite.java`:

- parses scene id and `groupId,suiteId` entries
- calls `scriptManager.refreshGroupSuite(groupId, suiteId, quest)`
- then marks the group `dontUnload = true`

### `SceneScriptManager.refreshGroupSuite`

If the group instance is absent, it calls `getGroupById(groupId)`, which loads the group and creates an instance. It then calls `refreshGroup`, broadcasts `PacketGroupSuiteNotify`, and the quest overload records a `QuestGroupSuite` on the parent quest.

### `refreshGroup` / `addGroupSuite`

`refreshGroup` removes the previous suite, calls `addGroupSuite`, then stores the active suite id. `addGroupSuite` creates both gadgets and monsters from the selected suite.

### Dead-entity cache is not the direct cause

`getMonstersInGroupSuite` currently does **not** suppress monster creation using `SceneGroupInstance.deadEntities`; that check is commented out as a TODO. Do not spend time on a stale-death-cache theory unless new runtime evidence contradicts this.

### Ordinary group loading

`Scene.onLoadGroup` initializes a loaded group through `refreshGroup(groupInstance, 0, false)`. A `0` suite resolves to the group's init suite. For group `133003429`, that means suite 2, which is empty, until something explicitly switches it to suite 1.

## Quest lifecycle detail that matters

`GameQuest.start()` executes only the quest's `beginExec` list. `GameQuest.finish()` executes the `finishExec` list.

Therefore the Quest 351 `35107 -> REFRESH_GROUP_SUITE 133003429,1` action occurs only **after 35107 finishes**. If the currently missing slimes are supposed to exist while Quest 352 / `35204` is active, that 35107 finish exec alone cannot be assumed to be the spawning mechanism.

This strongly suggests the next trace should locate the **trigger/script/Quest Actor side effect that creates the encounter before trigger 1003 can later report completion**.

## Highest-value next steps

1. Inspect the output/logs from `AstaPS-Resource/.github/workflows/tmp-quest352-slime-discovery.yml` at/after `b0f3a279...`.
2. Map `TriggerExcelConfigData` id **1003** to its scene, group and trigger name.
3. Fetch that scene group Lua and identify the exact action that is expected to create/refresh the slime encounter.
4. Search Quest 352 actor/share scripts (`AQ352`, Q352 share/client scripts) for group/suite or monster creation side effects if the scene trigger is only the completion signal.
5. Compare every ScriptLib call used by that action against AstaPS support. A missing or no-op ScriptLib implementation is a strong candidate.
6. If the action uses group suites, verify whether the group is loaded, which suite is active, and whether `refreshGroupSuite` returns true at runtime.
7. Trace callers of `Scene.loadGroupForQuest(...)` and verify saved `QuestGroupSuite` replay on relog only after the exact owning quest/group is known.
8. Fix the generic lifecycle/ScriptLib/group mechanism. Do **not** hardcode Quest 352, trigger 1003, or a group id unless reverse/resource evidence proves there is no generic mechanism.
9. Add focused logging/regression coverage. Before asking the user to retest, run CI and provide one complete PowerShell block: fetch remote, switch branch, build with Java 21, and run.

## Related but separate Quest 351 issue

`RinoPaw/Genshin-Reverse#9` tracks the always-visible **Return to quest point** client state during fresh Quest 351. The strongest historical clue is the fresh-born `DoSetPlayerBornDataNotify -> QuestModule.ResetTrackingLocalData([351])` path, but the issue remains unresolved for the exact 7.1 client. Do not use that hypothesis to explain the missing slime without direct evidence.

## Broader prerequisite-audit state

Separate from this spawn bug, AstaPS-Resource prerequisite restoration has progressed through **Inazuma Chapter 1207**. Permanent audit CI was guarding 678 regular repaired prerequisite rows plus the Chapter 1104 and 1206 missing-row restoration cases. The next region, **Sumeru Chapter III Act I, Through Mists of Smoke and Forests Dark**, was intentionally paused because 3.7/4.0 public Quest data degrades its prerequisites to `QUEST_COND_UNKNOWN`, while exact-7.1 public exports show schema/field misalignment. That work belongs under `RinoPaw/Genshin-Reverse#8` and should not be mixed into this slime-spawn fix.

## Suggested continuation prompt

> 继续新号序章史莱姆不刷问题，先读 `docs/quest352-slime-spawn-handoff.md`。先检查 AstaPS-Resource 的 `tmp-quest352-slime-discovery` 输出，锁定 35204 / trigger 1003 对应的实际 group/script spawn side effect，再追 AstaPS 是否执行。不要先硬编码 group。
