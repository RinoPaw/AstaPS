# Ordinary chest reward preflight (7.1)

The branch now combines three read-only components:

1. `StandardChestClassifier` accepts only verified ordinary four-grade world treasure chests with matching Lua `drop_tag` and prefab quality. It excludes special chests, explicitly rooted quest rewards, Shrines of Depths, boss flowers, unverified synthetic sources, and prefab/tag conflicts.
2. `WorldAreaIndex` resolves world coordinates to the level-1 region and, when possible, the level-2 region. Level-2 outlines may be height-limited; an unknown level-1 region is not guessed.
3. `StandardChestRewardResolver.preview` uses the classification, region and operator-owned `RewardConfig.current().chest()` selectors. It returns the proposed direct rewards and last matched selector, or an empty result for excluded/unresolved sources.

The existing `WorldChestLootHelper` now uses CSS for **all fixed quantities**.
Verified ordinary world chests use area selectors; other legacy
fallback chest objects use CSS quality defaults only and do not
assume regional overrides. Defaults explicitly define Adventure EXP,
Primogems, Mora, regional sigil counts, ores and books.
Java contains no numeric quality reward table. Native quest and Boss
drop paths remain separate. `GadgetChest` claim timing and ground-item
delivery remain unchanged, including their existing safety limitations.

The resolver deliberately fails closed when the level-1 world area is unknown: it cannot safely assume that a chest belongs to a high-Primogem region. If level-1 is known but level-2 is unknown, level-1 overrides can still apply.

Remaining limitations before expanding this integration:

- Native fixed currency/EXP must not be paid twice when an operator-owned direct reward takes authority. Native randomized material/weapon loot and per-object quest rewards must remain.
- Inventory or ground-drop delivery must return a meaningful success result before a chest is marked claimed. The current `WorldChestLootHelper.grant` and ground-entity creation calls do not offer this confirmation, while `GadgetChest.grantOrdinaryChestLoot` currently marks `ordinaryClaimed` before delivery.
- Lua interactions and intentionally empty, one-off, special and unknown chest metadata must not be redirected to generic quality rewards.

Run `./gradlew test -PexcludeTags=integration -Dorg.gradle.jvmargs=-Xmx4g`. The world-area integration assertions that require extracted resources skip automatically when those resources are missing.

Related evidence: `docs/chest-rewards-css-71.md`, `feat/chest-kind-special-classifier-71`, `feat/world-area-level2-71`, `audit/chest-data-alignment-71`. Some world-area geometry also exists in older resource snapshots; its provenance must not be treated as independently confirmed native 7.1 geometry.
