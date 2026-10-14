# Ordinary chest rewards: staged native drops and limited live claims (7.1)

The branch now combines three read-only components:

1. `StandardChestClassifier` accepts only verified ordinary four-grade world treasure chests with matching Lua `drop_tag` and prefab quality. It excludes special chests, explicitly rooted quest rewards, Shrines of Depths, boss flowers, unverified synthetic sources, and prefab/tag conflicts.
2. `WorldAreaIndex` resolves world coordinates to the level-1 region and, when possible, the level-2 region. Level-2 outlines may be height-limited; an unknown level-1 region is not guessed.
3. `StandardChestRewardResolver.preview` uses the classification, region and operator-owned `RewardConfig.current().chest()` selectors. It returns the proposed direct rewards and last matched selector, or an empty result for excluded/unresolved sources.

## Native-drop ownership and staged delivery

`DropSystem.rollChestTag(tag, level)` can now roll a validated tag/root without
spawning an entity or touching inventory. Its `ChestRoll` retains the native
`fallToGround` flag. A missing root is distinct from an accepted, empty
random roll. The original `handleChestDrop` method still serves older call sites.

`ChestRewardConfig.Match.ownedItemIds()` records **every** item ID specified by
matched TOML tables, including explicit zero clears. The final nonzero
`Match.rewards()` is only the desired fixed amount; it cannot alone express
which native items should be suppressed. `ChestRewardOverlay.plan()`
keeps every unowned native roll item (and its rolled equipment properties),
removes only owned IDs, and attaches the final configured direct rewards.
The read-only `StandardChestRewardResolver.stage(gadget, dropSystem)` composes
this configured preview with a native tag roll and returns both original routing
metadata and the filtered overlay. It never calls `GadgetChest` interaction or
inventory methods. It makes no delivery attempts. `Plan.inventoryBatch()` can build a complete
in-memory inventory grant from the retained native items and configured direct
items, rejecting missing/mismatched item definitions before any grant. The
caller still has to establish one verified admission and delivery operation.
Converting originally ground-spawned native items to direct inventory delivery
would change player-visible behavior. Thus the live claim only uses this batch
when the native root has `fallToGround=false`. Mixed inventory + ground-entity
deliveries are not yet supported as a single commit.

## Ground pickup staging (not yet connected to the claim)

`ChestGroundDropPlan.prepare` now partitions the merged native roll using the
same rule as `DropSystem.dropItem`: DropMaterial.autoPick and non-gadget virtual
currency are delivered directly; remaining native items keep physical ground
pickup behavior. Every TOML fixed item is direct. The planner rejects missing
item definitions or ground loot without a pickup gadget before changing state.
`StandardChestRewardResolver.Staged.prepareDelivery` exposes the result; the
existing inventory-only path consumes it with an explicit assertion that no
ground entities were selected.

`Scene.prepareChestGroundEntities` validates the **entire** physical batch,
including per-unit meat/equipment expansion (bounded to 10,000 entities),
then constructs unpublished, owner-only `EntityItem` instances. Native rolled
item properties survive both spawn and pickup; regular `EntityItem` pickup
now grants a copy of the stored roll instead of constructing a fresh item.
`Scene.publishChestGroundEntities` validates the prepared batch and
registers all items before sending appear packets. If `onCreate` fails, it
removes all registered items without publishing any. After successful
registration, a network notification failure is logged without treating
the batch as unspawned.

**These scene operations are not durable persistence.** Scene entity registration
can be lost at server restart. A claim receipt alone cannot guarantee an
uncollected ground item survives restart, and an inventory grant plus scene
spawn is not one atomic transaction. Consequently `GadgetChest` does **not**
publish these staged ground items or mark those chests claimed through the new
reward path yet. Before enabling them, the server needs persistent pending
pickup records, restoration on world/scene load, and explicit coordination with
direct rewards in the same claim receipt.

## Limited live claim path

`GadgetChest.grantOrdinaryChestLoot` now attempts staged delivery for authored,
ordinary four-grade exploration chests when the native `ChestDrop.json` tag
resolves to an existing `DropTable` root with `fallToGround=false`, and the
chest has a stable positive scene/group/config identity and a known level-1
area. Other chests (including native ground-drop roots, special objects and
unknown sources) still use the **legacy** code path. This does **not** mean
all ordinary world-chest behavior is corrected.

The supported path preflights a combined inventory batch and uses
`ChestInventoryClaim` with `RewardGrantJournal.Source.CHEST`. A key is scoped
to uid + scene + authored group + config; runtime entity IDs are not used.
The journal PREPARED and CONFIRMED writes occur within inventory admission
callbacks. Only a fully accepted grant permits the normal chest-open event,
notification and claim flag. Capacity refusal before journal preparation
leaves the chest retryable. An ambiguous/partially delivered CONFIRMED receipt
blocks further automatic grants of the same chest, pending operator review.
This is a duplicate guard, **not an atomic transaction with player/inventory
persistence**. A receipt completion write failure after acknowledged delivery
is logged but does not replay or reverse delivered items.

The configured `rewards.toml` fixed amounts are now effective for this
limited inventory-only population. Editing operator TOML does not alter
legacy or ground-drop chest payouts. An optional empty native roll may still
receive operator-owned fixed amounts when its resolved root is valid.


The resolver deliberately fails closed when the level-1 world area is unknown: it cannot safely assume that a chest belongs to a high-Primogem region. If level-1 is known but level-2 is unknown, level-1 overrides can still apply.

Remaining work before enabling all standard chest payouts:

- Validate the limited path in-game with representative common/exquisite/precious/luxurious chests and compare native IDs/amounts; the overlay now suppresses duplicated fixed IDs, including explicit zero overrides.
- Ground-spawned loot needs prevalidated entity creation and an acknowledged delivery/claim boundary. The legacy `WorldChestLootHelper.grant` still uses `void` ground spawning and claims before verification. Do not silently convert ground pickups into inventory grants.
- Keep Lua interactions, one-off quests and intentionally empty/special chests out of this path. Verify journal key uniqueness against script respawn cases, and audit unsupported tags/roots before replacing the legacy tier fallback.

Run `./gradlew test -PexcludeTags=integration -Dorg.gradle.jvmargs=-Xmx4g`. The world-area integration assertions that require extracted resources skip automatically when those resources are missing.

Related evidence: `docs/chest-rewards-toml-selectors-71.md`, `feat/chest-kind-special-classifier-71`, `feat/world-area-level2-71`, `audit/chest-data-alignment-71`. Some world-area geometry also exists in older resource snapshots; its provenance must not be treated as independently confirmed native 7.1 geometry.
