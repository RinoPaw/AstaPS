# Ley-line encounters: original Lua only (Global 7.1)

Java selects the persistent camp cursor and settles rewards. Original Scene Group
Lua defines every eligible encounter: monsters, gadgets, Suites and event
triggers. Selecting worktop option 187 dispatches the group's
EVENT_SELECT_OPTION exactly once. Lua progresses its monster waves and invokes
CreateBlossomChestByGroupId on EVENT_BLOSSOM_PROGRESS_FINISH.

There is no default blossom Lua hook, random Java monster challenge, or
Spawns.json alternative. Camp selection holds a reference to the authored
Lua SceneGadget directly; no intermediate SpawnDataEntry is synthesized.
A ley-line outcrop belongs to the camp when its Group is referenced by
BlossomGroups.newGroupVec. The selected worktop is then matched by its
authored config/Gadget identity. Only Lua with an initial GROUP_LOAD trigger
and the GROUP_REFRESH, SELECT_OPTION, monster progress and reward event chain
is eligible. A bare blossom Gadget definition does not establish a runnable
encounter.
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

The original Global 7.1 starting-camp algorithm has not been independently
recovered for any city. The current initial choice is the lowest eligible
native camp ID, followed by occupancy resolution.
The snapshot's nextCampIdVec contains multiple edges for some camps
(6 Mondstadt, 7 Liyue, 2 Sumeru). Breadth-first conflict resolution is
a provisional local policy; the original client's branching rule remains unverified.

## Native Lua ownership checkpoint (2026-10-24)

- The 7.1 resource snapshot `BlossomRefreshExcelConfigData.dropVec`
  points to the same nine base quantity distributions as bundled
  `blossoms.lcss`. Directly inspecting all 18 root DropTable nodes and seven
  nested nodes confirms the item IDs, quantity ranges, 100 Adventure EXP and
  Companionship EXP at all tiers. All 20 configured WL0..9 comparisons match
  a capped tier-8 source projection. The original client's world-level mapping,
  random draw implementation and source-file authenticity are independent,
  still-unverified questions. LootCSS deliberately stays the server payout
  authority with a journaled claim.
- `BlossomGroupsExcelConfigData.newGroupVec` owns Group references and
  `nextCampIdVec` the directed camp graph. `RefreshBlossomGroup`
  replaces the active Suite and dispatches GROUP_REFRESH; authored Lua
  chooses a worktop with `GetBlossomRefreshTypeByGroupId`.
- The authored reward `configId`, Group and position now flow from
  `CreateBlossomChestByGroupId` to the claimable server reward entity.
  The claimable gadget type is still selected by the blossom kind.
- One `BlossomActivity` collection tracks fighting, reward-ready and
  claimable phases; the Lua schedule (1 before encounter, 2 fighting,
  3 completed) is derived from this phase and not stored separately.
- The original `EVENT_BLOSSOM_CHEST_DIE` refreshes the same Lua Group.
  Java's successful-claim path instead advances to another native camp
  and unloads the completed Group. Calling both would re-activate
  the old camp. This difference remains explicit pending original
  server-side lifecycle evidence.
- `CreateGadget` now reports failures; native blossom worktop creation
  failures also abort their Lua trigger. Existing identical entities
  count as successful creation.

## Single worktop creation path

The selected camp registers its original SceneGroup, then lets its authored
GROUP_LOAD / GROUP_REFRESH trigger call CreateGadget. The Scene entity-created
hook attaches its selected SpawnDataEntry and equips the worktop option handler.
There is no parallel Java-created fallback worktop. Script-owned monsters in
inactive BlossomGroups are suppressed so incomplete native encounters do not
leak monsters into the scene. This is a resource-structure eligibility check,
not a claim of original executable/metadata provenance.

## Restart and receipt identity

The host's persisted BlossomRunState stores camp ID, monotonically increasing
incarnation, pending reward and exhausted-camp status. Reloading a pending reward
reconstructs the claimable entity from the original Lua reward marker; no duplicate
interactive worktop is spawned. An interrupted fight starts again from the author's
original Lua encounter; monster-progress snapshots are intentionally not persisted.

The reward journal key includes the host UID, refresh ID, camp incarnation,
original Group/config identity, claimant UID and durable unpaid-attempt counter.
Explicitly unpaid attempts receive a fresh counter. A completed receipt from
before restart closes the claim without paying or granting rewards again.
Ambiguous CONFIRMED/PREPARED receipts still require existing journal inspection;
there is no automatic replay of uncertain payments.

## Event ordering

The native camp's GROUP_LOAD, GROUP_REFRESH, monster-death and reward callbacks
share a per-Group serial event queue on the existing script executor. Distinct
Groups may progress independently. This prevents cross-thread reordering of
dependent Lua stage events without changing unrelated scripts.
