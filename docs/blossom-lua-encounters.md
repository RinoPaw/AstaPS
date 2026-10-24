# Ley-line encounters: original Lua only (Global 7.1)

Java selects the persistent camp cursor and settles rewards. Original Scene Group
Lua defines every eligible encounter: monsters, gadgets, Suites and event
triggers. Selecting worktop option 187 dispatches the group's
EVENT_SELECT_OPTION exactly once. Lua progresses its monster waves and invokes
CreateBlossomChestByGroupId on EVENT_BLOSSOM_PROGRESS_FINISH.

There is no default blossom Lua hook, random Java monster challenge, or
Spawns.json alternative. A ley-line outcrop is identified by its
70360056 / 70360057 gadget ID; no event-structure validation is performed.
Gold and blue flowers share each country's graph, with independent persisted
refresh cursors. Each country has its own candidate pool, occupancy set and
position-collision scope, including when several countries share one world
scene. The current collision policy walks nextCampIdVec edges in deterministic
breadth-first order, skipping occupied camps and missing Lua gadgets. There is
no wait state, random encounter, or legacy fallback. Map reward previews
come from each selected flower's own refresh ID, so one country's reward
configuration cannot change another country's map marker.
If no reachable authored Lua can provide a flower, one-per-type availability
cannot be guaranteed. Recovering the missing scripts is required.

Java retains only encounter progress accounting, entity/summon cleanup,
the single reward claim ledger, resin charging and saved camp rotation.
Monster IDs, spawn positions and stages come from source Lua.

The native Global 7.1 starting camp algorithm outside Mondstadt has not
been independently recovered. The current initial choice is provisional.
The snapshot's nextCampIdVec contains multiple edges for some camps
(6 Mondstadt, 7 Liyue, 2 Sumeru). Breadth-first conflict resolution is
a provisional local policy; the original client's branching rule remains unverified.

## Native Lua ownership checkpoint (2026-10-24)

- Original `BlossomRefreshExcelConfigData.dropVec` matches the nine base
  item/quantity tiers in bundled `blossoms.lcss`. Original selection of
  drop tier from world level, Adventure EXP, Companionship EXP, and
  per-node sampling remain unverified. LootCSS deliberately stays the
  authoritative server payout source with a journaled claim.
- `BlossomGroupsExcelConfigData.newGroupVec` owns Group references and
  `nextCampIdVec` the directed camp graph. `RefreshBlossomGroup`
  replaces the active Suite and dispatches GROUP_REFRESH; authored Lua
  chooses a worktop with `GetBlossomRefreshTypeByGroupId`.
- The authored reward `configId`, Group and position now flow from
  `CreateBlossomChestByGroupId` to the claimable server reward entity.
  The claimable gadget type is still selected by the blossom kind.
- One `BlossomActivity` collection tracks fighting, reward-ready and
  claimable phases; the Lua schedule state lives on the activity.
- The original `EVENT_BLOSSOM_CHEST_DIE` refreshes the same Lua Group.
  Java's successful-claim path instead advances to another native camp
  and unloads the completed Group. Calling both would re-activate
  the old camp. This difference remains explicit pending original
  server-side lifecycle evidence.
- `CreateGadget` now reports failures; native blossom worktop creation
  failures also abort their Lua trigger. Existing identical entities
  count as successful creation.
