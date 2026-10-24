# Ley-line encounters: original Lua only (Global 7.1)

Java selects the persistent camp cursor and settles rewards. Original Scene Group
Lua defines every eligible encounter: monsters, gadgets, Suites and event
triggers. Selecting worktop option 187 dispatches the group's
EVENT_SELECT_OPTION exactly once. Lua progresses its monster waves and invokes
CreateBlossomChestByGroupId on EVENT_BLOSSOM_PROGRESS_FINISH.

There is no default blossom Lua hook, random Java monster challenge, or
Spawns.json alternative. A camp without original select/progress-finish
triggers and authored monsters is ineligible. Missing camps and incomplete
scripts are logged. The selected cursor is not silently replaced with an
unrelated camp; rotation follows the first nextCampIdVec edge, and an
unavailable successor stays unavailable until its resources are restored.

Java retains only encounter progress accounting, entity/summon cleanup,
the single reward claim ledger, resin charging and saved camp rotation.
Monster IDs, spawn positions and stages come from source Lua.

The native Global 7.1 starting camp algorithm outside Mondstadt has not
been independently recovered. The current initial choice is provisional.
The snapshot's nextCampIdVec contains multiple edges for some camps
(6 Mondstadt, 7 Liyue, 2 Sumeru). Selecting its first edge is also provisional;
the original client's branching rule remains unverified.
