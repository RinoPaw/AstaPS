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
astaps.loot.currency:
  - {reward: genshin.item.mora, count: "240~360"}

astaps.loot.experience_books: &books
  - {reward: genshin.item.heros_wit, weight: 1}
  - {reward: genshin.item.adventurers_experience, count: 2, weight: 1}

astaps.loot.book_bundle:
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

## Responsibility boundary

`loot.yaml` contains only named loot tables and their contents. It does
not match or inspect chests, monsters, scene IDs, regions, levels, qualities,
or any other attributes of a reward source. Table names describe reusable
contents, such as `currency` or `experience_books`, not where those
tables will be used.

`rewards.css` owns the source selection, the number of independent times
to run each named table, and fixed item overrides. A CSS loot property
`--astaps-loot-currency` references the `astaps.loot.currency` key
in `loot.yaml`.

For example:

```css
chest[kind="common"] {
    --astaps-loot-currency: 1;
    --genshin-item-primogem: 2;
}

chest[kind="common"][level="6"] {
    --astaps-loot-experience_books: 2;
}

monster[rank="elite"] {
    --astaps-loot-currency: 3;
}
```

The two matching loot properties on the level-6 common chest execute
separately. Each table count is its own CSS property; a more specific rule
can override it independently, including with `0` to disable the table.
The monster can use the same table without any monster-specific data in
the YAML file.

The `CssRewards` parser resolves both `item` and `loot` custom
properties through their resource IDs and validates that every referenced
table exists. `RewardRoller` runs the selected tables, merges resulting
item counts, then applies fixed CSS item quantities (including zero to
remove a rolled item). It does not grant rewards or mark anything claimed.

For fixed items, the same resource ID is encoded as an independent CSS
custom property:

```css
chest[kind="common"] {
    --genshin-item-mora: 300;
    --genshin-item-heros_wit: 1;
}
```

No legacy properties, compatibility aliases, or repeating `reward:`
CSS declarations are supported. The `ResourceId` model uses exactly
`namespace.type.name`, encoded as `--namespace-type-name` in CSS.
The CSS parser leaves source element names and selector attributes open.
Its current cascade ordering is defined for element and attribute
selectors, not full browser CSS specificity.

These remain **standalone configuration and evaluation prototypes**,
not live chest, monster, or transport reward settlement.

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
