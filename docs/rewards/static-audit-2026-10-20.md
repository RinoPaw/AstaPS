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
| World Boss | 48 `sourceType=34` identities × 25 source level roots (1–120). Tests resolve and roll all 1,200 mapped source entries and independently expand
  the historical native DropTable graph for fixed item IDs 101, 102, 105 and 202;
  any CSS/source mismatch fails CI. | **Partial.** Some gem probabilities are integer-rounded. Precision Clockwork's missing artifact subpools borrow Pyro Regisvine candidate weights. |
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

## Probability-budget and representation review

The per-root limits are now: nontermination probability zero, expected total pool
executions <=100, P(N>100)<=1e-6, and P(H>64)<=1e-6. The root counts as a node
at depth 1. These checks retain the joint offspring law, and use downward-rounded
PGF coefficients so reported tails are conservative upper bounds. YAML execution
uses an explicit continuation stack; no reward is cut off at the analysis bounds.
The budgets are per YAML root, not the sum of all CSS grants in one settlement.

An independent static traversal of the checked-in
`data/Server/DropTableExcelConfigData.json` found 16,895 tables, 51,144 entries,
11,862 exclusive tables, 5,033 independent tables, and no table-reference cycles.
Entry counts comprise 42,650 integer, 7,639 fractional, and 855 interval values.
Within this file's table-reference graph, maximum expected executions are 22,
maximum possible executions are 25, and maximum depth is 3. All 1,771 ChestDrop
rows reference an existing root. `NativeDropGraphSafetyTest` reconstructs the
native offspring laws and checks all 16,895 roots against the four budgets.
External DropSubTable candidates are not included in these structural totals.

The DSL represents the source's two random modes, relative exclusive weights,
independent basis-point chances, nested table references, integer counts,
inclusive integer intervals, and fractional stochastic rounding. Existing exporter
and reward tests cover these operations and concrete artifact variant properties.
This establishes representation of reward distributions, not complete migration
of every native root into the bundled CSS.

Whole-table behavior has additional fields. The native dataset contains nonzero
`everydayLimit`, `historyLimit`, and `activityLimit` values; these are not modeled
by the reward DSL or enforced by `DropSystem.processDrop`. Among the 7,930 tables
reachable from ChestDrop roots, 13 have a history limit: four shrine roots and
nine Inazuma/Sumeru page roots. The graph checks do not validate those quotas,
`dropLevel`, or delivery placement. They must not be counted as covered behavior.

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

## Remaining blockers before declaring the entire claim flow sound

Ordinary chest rewards are delivered item by item. A failed write after the first
successful item leaves a retryable chest with partial delivery. Ground delivery
also calls `Scene.addItemEntity`, which silently returns for an unknown item;
`WorldChestLootHelper` currently validates neither the full item batch nor that
return outcome before marking the chest claimed. A rejected automatic virtual
currency grant may fall back to an unusable ground item. These are delivery-path
issues, separate from CSS/YAML probability representation.

Ley-line payment has no durable receipt and marks the player claimed only after
its reward notification returns. An interrupted grant or notification can therefore
leave a retryable flower after charging. Transpoint rewards also retain a crash
window between inventory writes and saving activation state. Boss/domain journals
retain unresolved receipts for reconciliation; they do not make all writes atomic.
A successful compilation and unit suite do not resolve these interruption cases.

## Verdict

The **representation and major configured source roots can be checked**, but
the currently bundled rewards **cannot be called a perfect reconstruction**
of the historical native tables. Known approximations, inferred fallbacks,
missing original subpools, and unverified interruption behavior remain explicit.
A successful CI run permits functional testing of covered paths; it must not
be confused with proof of exact native distributions or transactional recovery.
