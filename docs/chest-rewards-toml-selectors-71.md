# Standard exploration chest reward selectors (7.1)

## Scope

The root-level `rewards.toml` is a **server-operator owned** file alongside
`config.json`. It now includes both `[transPoint]` and `[chest]` tables.
On first server startup it is copied from the bundled
`src/main/resources/rewards.toml`; an existing file is never overwritten.
Invalid TOML or an invalid rule prevents startup with an error naming the
configuration file, field or selector. Local `rewards.toml` is gitignored.

The shared loader validates and publishes both reward subsystems atomically.
TransPoint rewards are already consumed by the running server, and their selector
rules now use the same source-order and field-override semantics. This phase
adds **chest configuration parsing and rule resolution only**.
`GadgetChest` and `WorldChestLootHelper` still use their existing reward
paths. The read-only `StandardChestRewardResolver` now combines
`WorldAreaIndex.areaAt(x,y,z)` and `StandardChestClassifier` with these
selectors. `GadgetChest` does not call this preflight yet. **Editing this TOML
still does not change in-game drops.** See
[chest-reward-preflight-71.md](chest-reward-preflight-71.md).

## Selectors

Only these selector forms are recognized:

- `[chest."kind=common"]`: all ordinary common treasure chests
- `[chest."kind=common,area=3:1"]`: the matching quality in scene 3,
  first-level WorldArea 1
- `[chest."kind=common,area=3:1:104"]`: additionally require second-level
  WorldArea 104

Valid kinds: `common`, `exquisite`, `precious`, `luxurious`.
Four default `kind` tables are required. `city`, glob wildcards, arbitrary
Boolean expressions, `remarkable` and reordered selector properties are
rejected. All area IDs must be positive decimal 32-bit integers without
leading zeroes.

## Matching order and field override semantics

**Rules apply in written order, top to bottom. The last matching value wins
for each reward field or item ID.** There is no implicit priority for more
specific regions. Put generic defaults first, then broad regional rules,
then precise region exceptions. Moving a general rule below a specific rule
will intentionally override the specific rule.

```toml
[chest."kind=common"]
adventureExp = 30
primogems = 2

[chest."kind=common,area=3:1"]
primogems = 0 # Adventure EXP stays 30

[chest."kind=common,area=3:1:104"]
primogems = 2 # Stormbearer Mountains; Adventure EXP stays 30
```

The TOML source location is read using tomlj's `inputPositionOf`, then
rules are sorted by line/column. No code relies on unspecified map iteration
order. Every accepted selector is parsed once at startup.

If a field is not present in a matching table, its value from a preceding
matched rule remains. An explicit zero clears a reward:

- `adventureExp` (item 102), `primogems` (201), and `mora` (202)
  accept **non-negative** integer counts; zero removes the item.
- `items = [{ id = 104001, count = 2 }]` sets an arbitrary direct reward.
  A later matching `items` entry with the same ID *replaces* its count;
  count zero removes it. Repeating an item ID *inside one table* is invalid.
- All IDs/counts are bounded to signed 32-bit integers, item IDs must be
  positive, and each reward table can contain **only** these direct reward
  fields. Negative amounts, floats, unknown keys, random-drop metadata and
  duplicate TOML tables are rejected.

A successful `resolve(kind, sceneId, areaID1, areaID2)` returns the final
direct reward list and the last matched selector for debugging. Unknown level
2 (zero) never matches a level-2-only rule; unknown scene/parent use the
quality defaults. Matching is independent of source order *among TOML
properties within the same table*.

## Default policy

The template includes four base quality entries with standard higher-region
fixed Primogems and Adventure EXP. Historical low-reward
areas `3:1–7, 3:9` override gems for common/exquisite/precious.
Then `3:1:104` (望风山地) and `3:7:703` (孤云阁) restore higher gems.
龙脊雪山 `3:10`, 璃沙郊 `3:8`, 层岩巨渊 `3:19` and
underground `6:300` already use the higher defaults. More recently
added `3:40`, `3:41`, `3:47`, `3:77` are explicitly not added to
the old low-gem parent rules.

The geographical rule evidence is pinned at
[Genshin-Reverse 7.1 chest region selectors](https://github.com/RinoPaw/Genshin-Reverse/blob/research/world-area-boundaries-71/versions/7.1.0-global/analyses/progression/world/7.1-chest-primogem-region-selectors.md).
The standard chest special-object exclusions are developed on
`feat/chest-kind-special-classifier-71`. Quest, Shrine of Depths,
remarkable, event and boss chest reward behaviors must remain separate;
native random drops must not double-award fixed currency/EXP when integrated.

## Tests

`./gradlew test -PexcludeTags=integration` exercises unified TOML parsing, bundled
defaults, first-run file creation and edits, unknown keys, invalid amounts,
source order, broad-rule-over-specific-rule ordering, missing area2 and
zero-value overrides. These tests do not require game resources or MongoDB.

## Related policy

The `transPoint` selector grammar and explicit zero overrides (for the
scene-3 tutorial waypoint and scene-7 Three Realms Gateway Offering) are
documented in [trans-point-rewards.md](trans-point-rewards.md). There is no
second transport-reward TOML file.
