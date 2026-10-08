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
