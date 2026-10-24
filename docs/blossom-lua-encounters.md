# Single Lua-driven ley-line encounter path

Global 7.1 AstaPS uses exactly one encounter lifecycle:

1. Java selects the city, blossom kind and camp from `BlossomGroups`.
2. The scene gadget is created from its authored Lua definition when present.
3. The worktop option 187 creates **one** `BlossomActivity`.
4. The engine calls the Lua hook `blossom_on_start(context, group_id)`.
5. The script controls the encounter. On success it calls
   `ScriptLib.CompleteBlossomChallenge(context, group_id)`.
6. Java creates the reward flower, settles the resin/reward receipt and
   advances `nextCampIdVec` after a successful claim.

There is **no opt-in flag** and **no Java fallback challenge**. Every accepted
interaction goes through the Lua hook. The legacy `EVENT_SELECT_OPTION`
notification is suppressed for ley-line worktop option 187, avoiding a second
script-triggered challenge.

## Bundled default Lua

`src/main/resources/blossom/default.lua` is shipped with the server jar.
It defines both standard hooks. It starts the built-in monster-combat primitive
through `ScriptLib.StartBlossomCombat(context, group_id)`. On combat completion,
Java invokes `blossom_on_combat_complete(context, group_id)`, which calls
`ScriptLib.CompleteBlossomChallenge`.

The combat primitive generates monsters and tracks kills using the existing
`WorldChallenge` implementation. **Lua is the sole controller** that starts
the combat and accepts the result. A script can replace the default hook with
its own stages and win condition.

## Custom encounter Lua

Add either or both of these functions to the camp's authored scene-group Lua:

```lua
function blossom_on_start(context, group_id)
    -- Configure monsters, puzzles, timers, triggers and suites.
    -- Use ScriptLib.StartBlossomCombat(context, group_id) to run ordinary
    -- ley-line combat first, or organize entirely custom gameplay.
    return 0
end

function blossom_on_combat_complete(context, group_id)
    -- Combat is done. Continue to the next scripted phase or finish here.
    return ScriptLib.CompleteBlossomChallenge(context, group_id)
end
```

The `blossom_on_combat_complete` hook runs once, and only when combat was
started with `ScriptLib.StartBlossomCombat`. A puzzle-only script can call
`CompleteBlossomChallenge` directly from its existing scene trigger once
its objective is complete. Both hooks return `0` for success.

The group-local `blossom_on_start` / `blossom_on_combat_complete`
definitions override the matching bundled default functions independently.
The original scene events and ordinary ScriptLib functions remain available
for the rest of the challenge.

## Scope and source constraints

This integration does not fabricate missing Fontaine gadget positions.
`BlossomGroups` remains responsible for camp topology, and a camp still
needs a source-backed gadget location to become available.

The original `RefreshBlossomGroup`, `SetBlossomScheduleStateByGroupId`,
`RefreshBlossomDropRewardByGroupId` interfaces remain unimplemented until
their native-client meanings are verified. The bundled Lua hooks are an
explicit AstaPS extension, not claimed to be original Global 7.1 code.
