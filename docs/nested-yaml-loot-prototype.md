# Nested YAML loot prototype (7.1)

This is an **experimental library**, not a live chest-reward replacement. The
upstream world-chest payment path is unchanged. Do not use this branch to test
in-game rewards until the evaluator is explicitly connected to reward settlement.

## Syntax

Each top-level key names a loot list. List entries accept exactly three fields:

- `item`: an English resource key, English item name, numeric item ID, a nested
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
  - {item: Genshin.HerosWit, weight: 1}
  - {item: Genshin.AdventurersExperience, count: 2, weight: 1}

common_chest:
  - {item: Genshin.Mora, count: "240~360"}
  - {item: *books, count: "1:70"}
  - {item: null}
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

## Naming

`LootItemNames.fromGameResources()` derives readable English names and
`Genshin.`-prefixed keys from the current game's English TextMap, then resolves
those keys back to the loaded numeric item ID. Wiki-verified names for Hero's
Wit, Adventurer's Experience, Wanderer's Advice, Mora and Primogem are also
registered. Ambiguous keys are rejected; numeric IDs remain available.

English names are derived from current game data. Wiki classification metadata
is currently supplied only for the three character EXP materials. A complete
Wiki category reconciliation and a stable-version alias manifest are future
work, not claims of the prototype.

Sources:
- https://genshin-impact.fandom.com/wiki/Character_EXP_Material
- https://wiki.hoyolab.com/pc/genshin/entry/475?lang=en-us
- https://wiki.hoyolab.com/pc/genshin/entry/303?lang=en-us

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
