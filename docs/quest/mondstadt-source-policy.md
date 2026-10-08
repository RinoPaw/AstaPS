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

## Team-wipe quest failure condition

The whole Mondstadt prologue condition census found QUEST_CONTENT_TEAM_DEAD
in failCond of 35101, 35203, 37602, 39703 and 38802. The server enum
previously marked it missing. EntityAvatar's two death entrypoints now emit
one explicit quest content event only if the final living member of the
active player team dies. ContentTeamDead accepts only that event; a single
party member's death does not fail a quest. This is a server-side event repair,
not an assertion of complete in-client retry mechanics.

## Restored combat failure combinators (37602 / 39703 / 38802)

GCResource 3700 and 4000 both specify LOGIC_OR for these two-predicate
failCond lists. With their materialized failCondComb missing, QuestData
previously defaulted to LOGIC_NONE and tested only the first predicate,
so 37602 ignored the later TEAM_DEAD condition. MainQuestData already
decodes failCondComb; QuestData now applies the same scoped conservative
BinOutput fallback for failing quests as for finishing quests. Explicit
QuestExcel fail logic remains authoritative. This completes the data path
for the new TEAM_DEAD event.

## Last-monster death ordering in 39703 / 38802

EntityMonster.onDeath queues QUEST_CONTENT_CLEAR_GROUP_MONSTER before the
dead monster is necessarily removed from Scene's entity table. Previously,
SceneScriptManager.isClearedGroupMonsters could still count that dead
entity as a surviving group monster, and the completion event would be
lost. The check now ignores dead entities with matching group ID, but
continues to block completion while a living member remains. Unit tests
cover dead-in-scene and living-group cases.

## Source-specific world entry and group-clear conditions

CONTENT_ENTER_MY_WORLD must match the entered scene ID against the
condition's required scene ID and the player's actual scene; previously it
checked only player scene == event scene and could falsely complete a quest
for another scene. CONTENT_CLEAR_GROUP_MONSTER must match the group ID
from the monster death event before inspecting whether its own target group
is cleared. Without this guard an unrelated monster death could finish
39703 or 38802 after its target group's entities disappeared from the
scene. Unit tests cover event isolation and missing event arguments.

## QuestExec ID/name lookup correction

QuestExec's static lookup builder accidentally filtered names with the
QUEST_CONTENT_ prefix; no QuestExec member uses this prefix, so dynamic
lookup by name or value always returned QUEST_EXEC_NONE. The corrected
QUEST_EXEC_ filter indexes every opcode including SET_WEATHER_GADGET.
This is decoding/lookup infrastructure only: it does not add weather-gadget
handler semantics. Enum round-trip tests cover all registered IDs.

## Candidate isolation for quest events

GameMainQuest formerly wrapped an entire group of matching finish/fail
candidates in one exception boundary. A malformed quest condition aborted
processing of the rest of the batch. QuestContentDispatch now invokes each
candidate independently; failures report main/subquest IDs and the event
opcode, while the remaining candidates still receive that event. The same
helper is exercised by finish and fail processing, with regression tests.

## Quest group suite persistence across relogs

SceneScriptManager.refreshGroupSuite previously appended every quest suite to
the MainQuest saved list. Repeated refreshes accumulated duplicate and
obsolete values (for instance group 133003002 suite 1 followed by suite 2),
and refreshing to suite 0 never cleared the saved override. On later
scene entry, Scene.loadGroupForQuest replayed stale suites in order.
The persistence list now keeps only the current suite per scene/group pair,
and a successful reset to suite 0 removes the corresponding override.
Unit tests cover update, deduplication, reset and independent scene/groups.
