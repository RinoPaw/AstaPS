# Configured reward review (2026-10-20)

## Provenance correction (2026-10-21)

The inherited reward values are not an authenticated original-game baseline.
Their history ends at a squashed private-server import. See
[source-provenance.md](source-provenance.md) for the inspected evidence and
rule families that still require source verification. The technical checks below
verify the implementation and agreement with historical references only.

## Scope

The independent reward system loads bundled `src/main/resources/rewards/{loot.yaml,*.lcss}`
and owns its configured claim paths. Historical tables remain reference data
for reward values and distribution structure. Their quotas and business
policies are not requirements of this system. There is no unpublished-format
compatibility layer. Native quest/dungeon chest routes and the existing native
dungeon reward service remain separate; this review does not migrate them.

## Configuration and representation

A standalone `RewardCssParser` parses LootCSS selectors and declarations;
css4j is not used for reward rules. Selector predicates support equality,
absence, numeric comparisons and ranges, and typed membership. Selector
attributes have declared `Integer` or `String` types. Integer selector
literals must fit within 32-bit signed bounds. Cascading uses selector
specificity and source order. `!important`, browser selectors and nesting
are excluded.

Resource declarations use dotted property names, exact `--name` bindings,
`$name` references and `name<args>` template calls. A literal such as
`<anemo>` cannot be shadowed by a variable named `anemo`; only `<$anemo>`
is a variable reference.

YAML supports independent entries, exclusive relative-weighted choices,
nested pools, dependent compositions, template expansion and artifact
properties. Reward counts are nonnegative integers, inclusive integer
ranges or weighted integer outcomes. Decimal weights are allowed; fractional
counts and stochastic rounding are not. A generic `Value<T>` layer represents
weighted choices independently of item materialization.

The configured 48 world Boss identities cover 25 source levels apiece;
fixed currencies/experience are independently checked against all 1,200
historical reference rows. Chest regional/tier, blossom world-rank and
transpoint selectors have their own tests. These checks establish technical
coverage and historical-reference consistency, not original-game authenticity.

Current choices remain explicit: some gem weights are rounded; Precision
Clockwork borrows artifact candidates; empty historical puzzle tables use
regional combat payouts; later regions use configured generic rewards.
Adventure EXP and regional Primogems are system-owned policies. These choices
do not indicate a language gap.

## Unified recursive safety

Every named pool, anonymous nested pool and instantiated template is analyzed
automatically. Each root includes its own execution at depth 1 and must satisfy:

- Probability of infinite execution: zero, established by finite positive exact
  expected-node solutions on the reachable invocation graph.
- Expected total nodes <=100.
- P(total nodes >100) <=1e-6.
- P(depth >64) <=1e-6.

The joint offspring PGF preserves correlations from exclusive choices and repeated
calls. Downward-rounded nonnegative coefficients give conservative tail upper
bounds. No Monte Carlo acceptance or runtime reward truncation is used. Runtime
execution uses an explicit continuation stack. Failed template instances are
removed, so retrying cannot bypass admission. Reports are available from
`YamlLootTables.safetyReports()`. Limits are per YAML root; multiple CSS grants
are separate invocations, not a combined settlement budget.

As a reference benchmark, all 16,895 checked-in server-table invocation laws pass
the new budgets. Their maximum mean is 22, maximum possible nodes 25, maximum
depth 3. These statistics exclude external DropSubTable candidates. The benchmark
checks language/model support; it does not connect historical business logic to
our system or require manual registration of each reward table.

## Claim ordering

Configured ordinary chests, ley-line flowers and scene-point rewards use the
shared `RewardClaimFlow`: complete preparation and inventory preflight, durable
intent, payment (when required), claim confirmation, reward writes, publication,
then durable completion. Automatic chest items are admitted as one batch. Ground
pickups are constructed and serialized before admission and published afterward.
An invalid pickup cannot silently disappear while closing the chest. Rejected
automatic currency grants do not fall back to an unusable ground pickup.

Ordinary placed chests use a stable scene/group/config receipt identity, so a
recreated entity cannot replay a completed or unresolved claim. Spawn-only chests
without stable placement metadata use a per-spawn identity. A completed receipt
allows chest state to finish without granting again. Ley-line flowers track each
player independently and retain one receipt identity per attempt; only a proven
unpaid refusal rotates that identity. Confirmation precedes inventory writes and
client notifications. Waypoint/statue/domain entrance claims have stable scene/point
receipts, so a lost activation save does not permit another reward batch.

The existing Boss/domain journal paths are unchanged. A receipt is not an atomic
transaction spanning inventory, player state and scene entities. Ambiguous payment
or partial persistence/publication failures retain unresolved receipts and block
blind replay. They require reconciliation; there is no automatic compensation.
Ground items remain transient scene entities. These boundaries also mean that
live client rendering, pickup and resource-name resolution still require testing.
