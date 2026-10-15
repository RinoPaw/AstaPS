# Nested YAML loot prototype (7.1)

This is an **experimental library**, not a live chest-reward replacement. The
upstream world-chest payment path is unchanged. Do not use this branch to test
in-game rewards until the evaluator is explicitly connected to reward settlement.

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
    --loot: astaps.loot.currency 3;
}
```

The common chest executes `astaps.loot.currency` once and
`astaps.loot.经验书` twice. Explicit qualified loot IDs work alongside
short names. Repeating the same table in one list adds execution counts.

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

Current implementation is an **independent parser and evaluator**.
It does not yet change actual chest, monster or transport reward delivery.
A future `give astaps.loot.surprise` command will be able to call the
same table evaluation API; this branch does not add that command yet.

## Native migration

`NativeLootYamlExporter` converts a selected set of native drop table roots to
named YAML pools `native_<id>` using YAML anchors in dependency order.

- Native `randomType=0` becomes a weighted list.
- Native `randomType=1` becomes an independent list.
- A native independent item with a roll chance below 100% is wrapped in another
  list when needed, so chance and countRange remain independent rolls.
- Empty tables become `[]`.

The original native resource tables are not overwritten. The exporter is for
research and migration checks; it does not establish a new live authority.

## Validation

Focused JUnit tests exercise nesting, independent repetition, weighted
selection, null rewards, mixed-weight rejection, percentage distributions,
alias reuse, cyclic references, and native-to-YAML conversion. The library also
enforces an evaluation budget and nesting-depth limit.

These tests have been committed, but CI has not yet verified the branch.
