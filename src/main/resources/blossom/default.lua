-- Common ley-line encounter entry points.
-- Every encounter uses these hooks. Authored scene Lua may replace either hook.
function blossom_on_start(context, group_id)
    return ScriptLib.StartBlossomCombat(context, group_id)
end

function blossom_on_combat_complete(context, group_id)
    return ScriptLib.CompleteBlossomChallenge(context, group_id)
end
