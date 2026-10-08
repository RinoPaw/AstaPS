# Genshin 7.1 Mondstadt prerequisite source policy

Genshin-Reverse verifies 7.1 native full Quest owns finishCond/failCond
and finishExec/failExec, but NOT ordinary acceptCond/beginExec. The reviewed
BinOutput acceptCond data here is *historical compatibility*, not native.

A scoped audit checked 40 main quests / 298 subquests and reported 292
Bin/Excel acceptance differences. This is not 292 proven regressions;
some differences are serialization tuple length. The Excel converter also
creates physical-row predecessor chains that break parallel branches.

Only the 40 Mondstadt mainline/support groups are opted into nonempty
reviewed BinOutput acceptance precedence; absent/malformed values leave
the existing Excel condition intact. Bin-combinator values accompany
accepted rows when present, and reindexing drops old condition keys.

No chapter starts at birth (1001 starts from 35202 -> 36301).
Static CI is not proof of correct gameplay or recovery of old saved data.
Pair with RinoPaw/AstaPS-Resource integrate/mondstadt-prologue-71.

## Runtime execution diagnostics and known missing opcode

For reviewed Mondstadt main quests, unavailable QuestExec handlers and handler
failures must emit WARN with main/sub/action; runtime exceptions emit ERROR.
A successful JSON audit cannot prove an action has any Java implementation.

QUEST_EXEC_SET_WEATHER_GADGET (22) remains without a registered handler.
The historically reconstructed 35901 beginExec therefore cannot yet create
its intended weather-gadget effect. Do not substitute player weather/climate
changes for this opcode without 7.1 event-level evidence.

The quest group Lua notification captures the state at dispatch and drops
deferred notifications after the quest or scene changes. Scene group suite
refresh waits for script initialization without polling. Neither behavior
alone proves the client-visible monster chain.

## Quest exec dispatch state

QuestSystem snapshots the quest state and action parameters **before** queuing
each execution. QuestExecHandler's new state-aware overload is backward
compatible with ordinary handlers; ExecNotifyGroupLua uses the saved state
to choose QUEST_START versus QUEST_FINISH. Scene callbacks still discard
events when a quest has changed state, so a late scene load cannot execute
an obsolete tutorial spawn. This prevents an asynchronous start action
from being reinterpreted as a finish event.

## Prologue point lock (35106)

35106 finishExec locks scene 3 point 1720 using native protocol field 4
(locked point) and field 6 (hide point). The new QuestExec handler stores the
relocked point in the persisted player point state and sends the existing
ScenePointUnlockNotify.lock packet. Later unlockTransPoint clears its forced
lock. The ordinary waypoint 3/6 remains unaffected.

## Explicit quest suite restoration after protected combat groups

SceneGroup suite 1 of 133003136 has ban_refresh=true. The original
refreshGroup compared the *previous active* suite with the queued target,
so a pending switch to suite 2 could never satisfy the second-attempt
condition. Explicit REFRESH_GROUP_SUITE also reported success when
refreshGroup returned 0 (no scene mutation).

The comparison now uses the requested destination, and explicit quest suite
refreshes make the second attempt in the same operation. The wrapper only
announces and records a suite if the transition actually completed. This
is relevant to 36004's post-battle return to ambient group suite 2.

## Recorded Lua-progress handoff (35309 / 35310 / 35311)

Lua AddQuestProgress can happen before the next subquest's QUEST_CONTENT_LUA_NOTIFY
handler is active. At quest start, checkQuestAlreadyFulfilled now replays recorded
Lua progress and numeric ADD_QUEST_PROGRESS only when the exact progress key
meets the condition's required count. Unset keys and unrelated condition types
never replay. This preserves the three wave IDs and supports delayed quest
acceptance without re-spawning an already killed monster.

## Multi-objective dungeon completion (30901)

Both GCResource 3700 and 4000 record LOGIC_AND for 30901's three
QUEST_CONTENT_FINISH_DUNGEON conditions (IDs 1001, 1, 1003). The original
flattened 7.1 file omitted finishCondComb and QuestData's loader defaulted
to LOGIC_NONE, which acts as OR. MainQuestData now decodes full Quest
finishCondComb/failCondComb, and Mondstadt-only compatibility fallback
restores a nontrivial BinOutput finish combinator only if the flattened
Excel combinator is NONE and the objective has multiple conditions.
An explicit Excel combinator remains authoritative.

## Previously completed dungeons at quest start

30901 can become active after a player has completed one or more starter
dungeons. ContentFinishDungeon reads the persisted completedDungeons set,
but checkQuestAlreadyFulfilled previously did not dispatch FINISH_DUNGEON
for recorded completions. It now queues that event for each recorded
matching dungeon on subquest start. Together with its restored LOGIC_AND,
this permits all three completion flags to satisfy the hidden task in
any order, including completions preceding 30901 activation.
