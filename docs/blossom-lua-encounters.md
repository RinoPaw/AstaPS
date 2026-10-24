# Script-controlled ley-line encounters (Global 7.1)

AstaPS separates **camp and reward ownership** from **scene encounter scripting**.

* Java (`BlossomManager` / `BlossomNativeCamps`) selects the eligible camp and
  bloom kind, owns one active worktop, the reward receipt, resin debit, and the
  `nextCampIdVec` transition.
* Lua owns the monster waves, gadgets, regions, triggers, and success criteria
  **when the scene group explicitly opts in**.
* The existing Java encounter remains in charge of groups without this opt-in.
  Those groups do not receive a second `EVENT_SELECT_OPTION` from the packet
  handler, preventing simultaneous Java/Lua monster waves.
* Native-camp gadget entities are materialized using the parsed Lua gadget
  metadata. The scene manager refuses a duplicate live gadget with the same
  `(groupId, configId, gadgetId)`.

## Opt-in contract

Place `blossom_scripted = true` in the **actual** camp group Lua before it is
loaded. Only groups selected by the native camp table and currently loaded by
`SceneScriptManager` can opt in. This is an AstaPS extension, not a claim
about the original client's Lua format.

A Lua-owned encounter receives `EVENT_SELECT_OPTION` after the player selects
worktop option `187`. It may use ordinary `ScriptLib` actions and suite/region
triggers. When the authored challenge is completed, call:

```lua
return ScriptLib.CompleteBlossomChallenge(context, group_id)
```

Return code: `0` when the current group's running Lua encounter was newly
marked complete; `1` for a missing, mismatched, Java-owned, or already completed
encounter. This call never creates a reward directly. The scene's next tick
creates the reward gadget; `RewardClaimFlow` charges and pays once, and
`BlossomNativeCamps.advance` runs after a successful claim.

`ScriptLib.GetBlossomRefreshTypeByGroupId(context, group_id)` reports `1`
for the selected wealth (gold) gadget, `3` for revelation (blue), or
`nil` when the group is not currently active. These meanings are supported
by existing authored Lua mappings; exact native-client semantics remain under
investigation.

## Minimal example

The following is an **example extension**, not an original resource patch.
It can be appended to the verified `scene3_group133403311.lua` during local
experimentation. It reuses that file's existing gadget
(`config_id = 311008`) and positions, adding no guessed scene coordinates.

```lua
blossom_scripted = true

table.insert(triggers, {
    config_id = 9311001,
    name = "BLOSSOM_SAMPLE_SELECT",
    event = EventType.EVENT_SELECT_OPTION,
    source = "",
    condition = "condition_BLOSSOM_SAMPLE_SELECT",
    action = "action_BLOSSOM_SAMPLE_SELECT",
    trigger_count = 0
})
table.insert(triggers, {
    config_id = 9311002,
    name = "BLOSSOM_SAMPLE_TIMER",
    event = EventType.EVENT_TIMER_EVENT,
    source = "blossom_sample",
    condition = "",
    action = "action_BLOSSOM_SAMPLE_TIMER",
    trigger_count = 0
})
table.insert(suites[1].triggers, "BLOSSOM_SAMPLE_SELECT")
table.insert(suites[1].triggers, "BLOSSOM_SAMPLE_TIMER")

function condition_BLOSSOM_SAMPLE_SELECT(context, evt)
    return evt.param1 == 311008 and evt.param2 == 187
end

function action_BLOSSOM_SAMPLE_SELECT(context, evt)
    return ScriptLib.CreateGroupTimerEvent(context, 133403311, "blossom_sample", 10)
end

function action_BLOSSOM_SAMPLE_TIMER(context, evt)
    return ScriptLib.CompleteBlossomChallenge(context, 133403311)
end
```

The timer is a stand-in for any scripted objective. In a real challenge,
replace it with suite/monster/puzzle triggers, and ensure those scripts
clean up their own encounter entities. The sample's trigger config IDs are
custom example identifiers, not reverse-engineered original data.

## Boundaries

The native Lua functions `RefreshBlossomGroup`,
`SetBlossomScheduleStateByGroupId`, and
`RefreshBlossomDropRewardByGroupId` have **not** been given speculative
semantics. They still need native evidence. This integration does not
synthesize unavailable Fontaine gadget locations or reconstruct missing Lua.

After source-backed camp resources are added, opt in one group at a time.
