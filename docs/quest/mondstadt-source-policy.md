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
