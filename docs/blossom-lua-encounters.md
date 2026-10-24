# Single ley-line lifecycle, Lua-controlled encounters (Global 7.1)

The server has **one** camp/reward pipeline. Java selects a camp from
`BlossomGroupsExcelConfigData` and follows `nextCampIdVec` when a reward is
claimed. Its selected cursor is stored on the world host's player document,
keyed by `BlossomRefreshExcelConfigData.id`. Restarting the scene or server
does not reset the current camp.

Only source-backed Lua Gadget definitions are used to create the outcrop.
`Spawns.json` and synthetic `EntityGadget` locations are not fallback camp
sources. If no Lua in the original graph has the requested gadget, no camp is
spawned.

## Interaction, native Lua and custom scripts

Selecting worktop option 187 creates exactly one `BlossomActivity`, and
`BlossomScriptController` chooses one Lua handler in this order:

1. An authored `blossom_on_start(context, group_id)` function, when provided.
2. The original `EVENT_SELECT_OPTION` Lua trigger, when the registered group
   has the original select and blossom-progress-finish events plus monster data.
3. The bundled Lua `blossom/default.lua` hook for an incomplete source group.

The packet handler does **not** dispatch the worktop event a second time.
Full native groups use the scene's normal Lua trigger evaluation and ScriptLib.
Group instances are registered even when `BlossomGroups` refers to a group
missing from the scene-block index, giving Lua the normal Suite and variable
context. When a selected group is claimed, its triggers are unregistered.

The native script's `RefreshBlossomGroup` uses the existing group suite
engine. Native `AddBlossomScheduleProgressByGroupId` reports deaths against
the group's authored monster definitions; the last required death dispatches
`EVENT_BLOSSOM_PROGRESS_FINISH`. The native
`CreateBlossomChestByGroupId` reports completion to Java. No second chest
is created by Lua. `RefreshBlossomDropRewardByGroupId` leaves the payout
alone, because Java's claim transaction is authoritative.

## Default Lua hook

`src/main/resources/blossom/default.lua` is bundled inside the JAR:

```lua
function blossom_on_start(context, group_id)
    return ScriptLib.StartBlossomCombat(context, group_id)
end

function blossom_on_combat_complete(context, group_id)
    return ScriptLib.CompleteBlossomChallenge(context, group_id)
end
```

The Java combat primitive supplies monsters and kill counts, but only the Lua
hook requests the primitive and accepts its result. A custom script may use
`StartBlossomCombat`, advance to a puzzle after combat, or directly call
`CompleteBlossomChallenge` when its own objective is complete.

All routes end at the same Java reward owner. Resin charging, reward receipts,
and camp rotation remain Java operations. When a start hook fails, the
partially created challenge is aborted, group monsters are removed and the
worktop stays available. Failed combat-completion hooks abort the activity
and allow the authored outcrop to spawn again.

## Research boundaries

The grouping of `1` (wealth) and `3` (revelation) comes from the authored
Lua Gadget selection expressions. This work does not claim independent Global
7.1 EXE verification of the entire Blossom schedule system. Completing native
`EVENT_BLOSSOM_PROGRESS_FINISH` after all authored group monsters die is
currently an emulator rule inferred from the available group scripts.

Missing Fontaine source locations remain missing. The engine traverses only
actual `nextCampIdVec` edges, visiting each linked camp at most once.
