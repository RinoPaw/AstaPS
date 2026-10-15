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
experience_books: &books
  - {reward: genshin.item.heros_wit, weight: 1}
  - {reward: genshin.item.adventurers_experience, count: 2, weight: 1}

common_chest:
  - {reward: genshin.item.mora, count: "240~360"}
  - {reward: *books, count: "1:70"}
  - {reward: null}
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

For example, `loot.yaml` can contain:

```yaml
common_chest:
  - {reward: genshin.item.mora, count: "240~360"}
  - {reward: genshin.item.heros_wit, count: "1:70"}
```

The same item ID in `rewards.css` is encoded as a CSS custom property.
The initial `--` is required by CSS, and the remaining single hyphens
separate namespace, type and name:

```css
chest[kind="common"] {
    --genshin-item-mora: 300;
    --genshin-item-heros_wit: 1;
}

chest[kind="common"][scene="3"] {
    --genshin-item-mora: 0;
}
```

Only namespaced CSS item properties are supported; there are no old
properties such as `mora`, `primogems`, `adventureExp`, or `item-202`.
Each custom property names exactly one item, so CSS rules override items
independently. Explicit zero overrides the previous quantity.

The `ResourceId` model uses `namespace.type.name`, with exactly three
segments. CSS encoding uses `--namespace-type-name`; namespace and type
do not contain hyphens, and the final name may contain hyphens.
The CSS parser leaves source types and selector attributes open.
Its current cascade ordering is defined for element and attribute selectors,
not full browser CSS specificity.

The `CssItemRewards` parser is **not yet connected to live chest or
transport settlement**.

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
