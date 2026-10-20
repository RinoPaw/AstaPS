# Static reward audit (2026-10-20)

## Scope and source

Configuration: `src/main/resources/rewards/{loot.yaml,*.css}`.
Source snapshots: `data/ChestDrop.json`, `data/Server/DropTableExcelConfigData.json`,
and `RinoPaw/AstaPS-Resource` at `c0213668889ed05cbeb86660f424c3daf18bfacd`.
The source is historical server configuration, not a verified official-server loot study.

## CSS correctness

- The stylesheet is parsed once with css4j 6.2. Resource declarations use CSS custom properties,
  including CSS-escaped punctuation, instead of a custom pre-parser.
- Selector matching, Selectors Level 4 specificity, nesting, source ordering, and
  `!important` priority have focused regression tests.
- Numeric `data-range-*` attributes are reward-domain metadata supplied to the DOM matcher.
  Probability and execution-count strings are parsed by the loot engine, not by the CSS parser.
- No browser layout tree, media environment, or CSS-variable evaluation is supplied by reward
  entities. The bundled files do not depend on those facilities; they must not silently be
  advertised as implemented reward features.

## Native root coverage and parity boundaries

| Source | What static checks can establish | Exact original parity |
| --- | --- | --- |
| World Boss | 48 `sourceType=34` identities × 25 source level roots (1–120). A test resolves and rolls all 1,200 mapped source entries. | **Partial.** Some gem probabilities are integer-rounded. Precision Clockwork's missing artifact subpools borrow Pyro Regisvine candidate weights. |
| Weekly Boss | 10 `sourceType=35` identities × 25 level roots. Uses dungeon/native reward flow, outside this CSS. | Outside CSS audit. |
| Deep Abyss Baptist | 25 historical roots without a `sourceType`; deliberately left to the native fallback. | Outside configured CSS; inspect native fallback separately. |
| Chest, cities 1–4 | Native combat, puzzle, Dragonspine and artifact roots; city/grade/level selector coverage and representative probabilities tested. | **Partial.** Empty puzzle tables use regional combat substitutes; original weapon/ore child tables may be empty. |
| Chest, cities 5–9 | Existing generic chest configuration. | Historical regional source roots are unavailable. |
| Chest adventure EXP and regional Primogems | Explicit CSS-owned policies. | Policy, not literal source-table reconstruction. |
| Ley-line flower and scene points | Type/world-rank, scene/point rules and fixed payout tests. | Separate from the chest and boss historical table comparison. |

World Boss artifact comparison documented in
`docs/examples/world-boss-artifacts.md` previously checked 9,494 Boss/level combinations
and 25 artifact candidate pools using rational arithmetic. This comparison was an
offline source study, not a CI test. The current CI checks concrete configured
reward selection and source-root coverage, but **does not prove exact probability
equivalence for every original material, gem, and artifact subtable**.

## Claim path review

- Open-world Boss: `GadgetChest` → `DropSystem.rollBossChestDrop` →
  `RewardConfiguration.rollWorldBoss` (or native fallback) → inventory admission,
  resin debit and `RewardGrantJournal` receipt → client reward notification.
- Ordinary chest: `GadgetChest` → native quest/dungeon chest drop, otherwise
  `WorldChestLootHelper` → CSS/YAML roll → inventory or ground drops →
  chest-open state. A mid-delivery failure after the first item may leave part of
  the reward granted while the chest is still open; this route is **not crash-atomic**.
- Ley-line flower: `BlossomManager.claimReward` → `rollBlossom` →
  all-or-nothing inventory preflight and resin payment → chest claim.
  The operation lacks a durable receipt for a crash after charging resin.
- Teleport waypoint/statue and domain entrance: `PlayerProgressManager` →
  `RewardConfiguration.roll("transpoint")` → all-or-nothing inventory admission.
  Rejected grants no longer silently consume the initial activation; domain
  receipt updates only after successful grant. Crash-window atomicity still
  requires a durable transaction receipt.
- Weekly dungeon Boss: `DomainStatueDropService` pays and grants via the
  native dungeon reward service with its own admission and journal.

## Verdict

The **representation and major configured source roots can be checked**, but
the currently bundled rewards **cannot be called a perfect reconstruction**
of the historical native tables. Known approximations, inferred fallbacks,
missing original subpools, and unverified interruption behavior remain explicit.
A successful CI run permits functional testing of covered paths; it must not
be confused with proof of exact native distributions or transactional recovery.
