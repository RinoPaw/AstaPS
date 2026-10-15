# Nested YAML loot prototype (7.1)

The CSS and YAML evaluators now feed **ordinary world-chest settlement**.
Configuration is generated at first start in `config/rewards/loot.yaml`
and `config/rewards/rewards.css`, using classpath templates bundled in the jar.
The server loads and validates the two files **after game resources** are
available. Invalid configuration stops startup: there is no legacy fallthrough.

The live entry is `GadgetChest.grantOrdinaryChestLoot`, which delegates to
`WorldChestLootHelper.grant` after the existing quest-chest eligibility check.
CSS replaces that helper's old hard-coded tier quantities. The server rolls the
selected YAML tables, overrides the specified fixed items, grants adventure EXP
via `Player.earnExp` (item 102), and creates ground item entities for other
items, preserving the chest's pickup behavior. The chest claim path still
handles state, notifications, and despawn.

This does not replace quest-chest native quest-item drops, boss flowers,
monster kills, or trans-point rewards. Those are separate settlement paths.
The bundled `rewards.css` now maps **52 tagged ordinary chest variants
across 192 source rows and six minimum-adventure-rank thresholds** to
**108 distinct native roots**, via the `level` (adventure rank band) and
`dropTag` attributes. The bundled `loot.yaml` contains their full
reachable DAG of **679 nodes and 1,843 drop entries**. This is a historical
AstaPS compatibility snapshot, based on `Server/DropTableExcelConfigData.json`
blob `b46d383b535babd1be6b95e3ea031eef64d1fdfd` and
`Server/DropSubTableExcelConfigData.json` blob
`9397fb0b15c8c4cc82194ec429682f2ef4333767` from
`RinoPaw/AstaPS-Resource`. That shared dataset predates game 7.1;
**its probabilities must not be described as verified original 7.1 values.**

A chest without a recognized `dropTag` uses conservative, randomized
approximate fallback loot (not the inflated legacy compositions). CSS owns
the fixed 7.1-era **30 Adventure EXP** and 0–10 Primogem decisions, while
YAML owns random items and Mora. Ordinary Mondstadt/Liyue chest tags use
the low early-region Primogem amounts (0/2/5); other and untagged chests
use 2/5/10, with 10 for luxurious chests. Native rules replace the entire
fallback `--loot` list without discarding the independent fixed CSS items.
The existing generic sigil fallback is conservative (1/2/3/4 by tier).
These defaults approximate vanilla behavior; they are not complete exact
reconstructions for shrines, remarkable chests or special quest chests.

The bundled `src/test/resources/loot/chest-native-selectors.json` pins all
192 source-to-root mappings. The tests assert all selections and four-tier
fallback composition, in addition to the existing YAML semantics. A GitHub build/test check has passed on the prototype; the changed defaults
and live chest interactions still require CI and client verification.

## Syntax

Each top-level key names a loot list. List entries accept exactly three fields:

- `reward`: a namespaced item ID, English item name, numeric item ID, a nested
  YAML list, or `null` for no reward. A YAML alias referencing another list is
  treated exactly like a nested list.
- `count`: defaults to `1`. On an item it specifies quantity; on a list it
  specifies **independent execution count**.
- `weight`: defaults to `null` (including `weight: null` explicitly).
  If every entry of a list has null weight, the entries execute independently.
  If every entry has numeric weight, one is drawn proportionally to its weight.
  A mixture is a configuration error. Numeric zero disables one candidate in a
  weighted list; a weighted list with all zero weights is invalid.

No separate `all`, `pick`, `table`, or `chance` keys are needed.

```yaml
currency:
  - {reward: genshin.item.mora, count: "240~360"}

经验书: &books
  - {reward: genshin.item.heros_wit, weight: 1}
  - {reward: genshin.item.adventurers_experience, count: 2, weight: 1}

book_bundle:
  - {reward: *books, count: "1:70"}
```

YAML anchors must be defined before their aliases, and they are scoped to the
same YAML document. Cyclic aliases are rejected.

## Count syntax

| Count | Effect |
| --- | --- |
| `3` | Always three |
| `"1~3"` | Uniform 1, 2 or 3 |
| `"1;3"` | Native countRange notation, same uniform result |
| `"2.2"` | 80% two, 20% three |
| `"0.33"` | 33% one, otherwise zero |
| `"3:20"` | 20% three, otherwise zero |
| `"0:40,1,2:18"` | 40% zero, 42% one, 18% two |

The percentages are out of 100, not relative weights. At most one outcome may
omit its percentage; it receives the remainder. If none omits a percentage,
the remainder is added to the zero outcome. Duplicate outcomes, negative
numbers, probability totals over 100, and counts greater than 100000 fail
validation.

## Item IDs (shared with rewards.css)

A common `ItemIdRegistry` resolves `genshin.item.mora`, `genshin.item.heros_wit`
and other currently unambiguous English TextMap slugs. The five known
keys (Mora, Primogem and the three Character EXP books) are explicitly
pinned. Other generated names remain **provisional** until checked
against stable, versioned resource mappings. A collision is an error;
items without usable names can still be addressed by numeric ID.

`reward` now replaces `item` throughout the YAML parser, exporter,
fixtures and tests. Only `item` resources are supported in this phase.
A `genshin.avatar.amber` reference is rejected instead of treating an
avatar ID as a bag item.

## Named loot resources and responsibility boundary

The top-level keys in `loot.yaml` are **local table names**, not qualified
resource IDs. On load, AstaPS publishes each table as `astaps.loot.<name>`.
For example, `currency` becomes `astaps.loot.currency`, and `经验书`
becomes `astaps.loot.经验书`. The owner namespace belongs to the
loader, not to individual table definitions.

A qualified loot resource is independent of the system using it. Code and
future commands can execute `astaps.loot.surprise` directly by resolving
its global ID, without running a CSS selector. `YamlLootTables.roll()`
accepts the qualified ID; `resolveName()` resolves a short name in the loader's namespace
(or an explicitly qualified ID). The AstaPS loader defaults to `astaps`.

Loot tables define **contents only**: items, quantities, weights, nesting,
and optional empty results. They do not inspect area, level, chest quality,
object kind, scene ID or other source metadata.

`rewards.css` selects loot based on source attributes. `--loot` is one
CSS custom property whose value is a comma-separated list. Each name
means one execution unless followed by an explicit count:

```css
chest[kind="common"] {
    --loot: currency, 经验书 2;
    --genshin-item-primogem: 2;
}

monster[rank="elite"] {
    --loot: "astaps.loot.currency" 3;
}
```

The common chest executes `astaps.loot.currency` once and
`astaps.loot.经验书` twice. Explicit qualified loot IDs work alongside
short names. Qualified IDs must be quoted as CSS string values; short local
names such as `currency` and `经验书` need no quotes. Repeating the same table
in one list adds execution counts.

`--loot` follows CSS property replacement: a later, higher-priority
matching rule replaces the **entire** list, rather than appending to it.

```css
chest[kind="common"][level="6"] {
    --loot: surprise;
}

chest[kind="common"][scene="7"] {
    --loot: none;
}
```

`none` is an empty loot list. Fixed item declarations such as
`--genshin-item-primogem` cascade independently of `--loot`.
`RewardRoller` executes the selected tables, merges their items and
then applies CSS fixed item quantities. A fixed quantity of zero removes
that item from the result. The table definition never determines which
CSS selector should use it.

The `ResourceId` format remains `namespace.type.name`. CSS item
properties use the corresponding `--namespace-type-name` spelling.
Only `--loot` selects tables: the earlier per-table
`--astaps-loot-currency: 1` spelling is not supported. No legacy
CSS reward fields or compatibility paths are retained.

The current runtime integration covers ordinary world chests only.
Other loot consumers can call `RewardConfiguration.current().rollLoot(
"astaps.loot.surprise", random)` without CSS; the `give` command has
not yet been extended.

## Native migration

`NativeLootYamlExporter` converts a selected set of native drop table roots to
named YAML pools `native_<id>` using YAML anchors in dependency order.

- Native `randomType=0` becomes a weighted list.
- Native `randomType=1` becomes an independent list.
- A native independent item with a roll chance below 100% is wrapped in another
  list when needed, so chance and countRange remain independent rolls.
- Empty tables become `[]`.

The native tables in `resources/Server` are not overwritten. Their exported
YAML copies are bundled into the live configuration, with CSS as the
selection authority for ordinary world chests. Resource contents remain
separate from the chest source and region selectors. This export is a
historical compatibility source, not a reverse-engineered 7.1 probability
verification.

## Validation

Focused JUnit tests exercise nesting, independent repetition, weighted
selection, null rewards, mixed-weight rejection, percentage distributions,
alias reuse, cyclic references, and native-to-YAML conversion. The library also
enforces an evaluation budget and nesting-depth limit.

These tests have been committed. CI and client-side chest interaction
verification are required before treating the implementation as validated.

## Vanilla-style fallback and existing configuration

The earlier server defaults of 66/166/366/566 Primogems per chest were
non-vanilla, inherited from upstream, and are no longer bundled. The new
fallback gives modest randomized Mora, ores and EXP materials. Fixed chest
Primogems are selected by CSS; the original snapshot's native loot tables
usually do not contain Primogems, so those are supplied independently.

The default 30 Adventure EXP follows the game's 7.1-era chest normalization;
older exported native tables can still contain 10 or 20 and are overridden
at the CSS settlement stage. Chest-tag probabilities remain historical.
Tagged high-value or region-specific special chests need their own verified
source rules; ordinary fallback is not a substitute.

The server never overwrites existing files in `config/rewards`. If an older
instance already generated the inflated template, back it up and remove
`config/rewards/loot.yaml` to regenerate it on the next start. An old
untouched template emits a startup warning when its signature is detected.
Modifications to `rewards.css` are also not overwritten automatically;
merge or regenerate it to receive updated selectors and fixed rewards.
