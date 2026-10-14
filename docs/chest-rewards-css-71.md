# Standard chest fixed rewards — rewards.css (7.1)

The operator owns one root-level **rewards.css** for transport and chest rules.
The file is copied once from src/main/resources/rewards.css; existing files
are not overwritten. The loader validates **all** rules before publishing an
updated snapshot. A malformed selector or reward amount prevents startup.

**Important:** transport reward rules are live. Verified standard world chests
now use the CSS fixed rewards on the existing WorldChestLootHelper fallback path.
Native random loot is rolled once for verified standard chests, then CSS-owned
fields replace matching item IDs before payout. Native quest loot and special
chest flows remain separate. Rejected standard-chest area preflight now leaves
the chest unopened; confirmed inventory/ground delivery remains outstanding.

## Four editable ordinary-chest loot pools

AstaPS uses **two operator-owned files** so fixed rewards and native random
probabilities can both be edited without losing the original drop graph:

- `rewards.css` controls Adventure EXP, Primogems, any explicit item-ID
  overrides, and the regional exceptions described below.
- **`chest-loot.json`** controls *all* ordinary-chest random pool contents:
  Mora, sigils, books, ores, equipment, quantity ranges and probabilities.
  On the first server launch after this change, the server exports the actual
  loaded `ChestDrop.json` + `DropTableExcelConfigData.json` tables to the
  file in the working directory. Later launches **read the user's file without
  rewriting it**. If the underlying native data changes, delete or rename
  `chest-loot.json` to regenerate it; preserve custom edits first.

The JSON root contains `chests` with exactly four keys: `common`,
`exquisite`, `precious`, and `luxurious`. Each has `sources` (the original
`drop_tag`, minimum chest level and table/root ID) and `tables` (every
reachable random subtable). One grade has multiple sources because region,
chest level and combat/puzzle origin can change the outcome. Reusing the
original IDs retains their relationships, and changing a table affects only
that grade; the other grades have independent copies.

Example excerpt, **illustrative rather than real 7.1 drop rates**:

```json
{
  "chests": {
    "common": {
      "sources": [{ "tag": "战斗低级蒙德", "minLevel": 1, "dropId": 51010000 }],
      "tables": [{
        "id": 51010000,
        "randomType": 1,
        "fallToGround": false,
        "dropVec": [
          { "itemId": 202, "countRange": "1000;1500", "weight": 10000 },
          { "itemId": 104001, "countRange": "1;2", "weight": 5000 }
        ]
      }]
    },
    "exquisite": { "sources": [], "tables": [] },
    "precious": { "sources": [], "tables": [] },
    "luxurious": { "sources": [], "tables": [] }
  }
}
```

`randomType: 0` chooses **one** positive-weight entry proportionally;
`randomType: 1` checks each entry independently using `weight / 10000`
(the engine treats weights above 10000 as guaranteed). `countRange` is
an integer (`"3"`), inclusive integer range (`"1;3"`), or fractional
expected count (`"0.5"`). An `itemId` that is also present in `tables`
recursively rolls that child table; otherwise it is a final item ID.
`fallToGround` on the root controls chest loot delivery mode.
The JSON is validated on startup. An invalid file reports an error instead
of being replaced by defaults.

When a configured root is unavailable, the existing CSS
`chest[pool="missing"]` compatibility policy applies. Special/quest chests,
Shrines of Depths and boss chests still use their original paths, not this
four-grade config. The generated values reflect the **server's installed
resource tables**, whose exact 7.1 client probability provenance is not yet
independently established. No substitute probabilities are invented.

**CSS precedence:** a value such as `chest[kind="common"] { mora: 2500; }`
replaces the rolled Mora total for common chests; omitting `mora` leaves the
JSON-defined random amount intact. An explicit `item-104001: 0;` removes
that item from the rolled result. Avoid specifying an item in both files
unless replacement is intended. `adventureExp` is still delivered separately
from item 102.

## Selectors

This is actual CSS-style syntax with a restricted set of game attributes:

```css
chest[kind="common"] {
    adventureExp: 30;
    primogems: 2;
}
chest[kind="exquisite"] { adventureExp: 30; primogems: 5; }
chest[kind="precious"] { adventureExp: 30; primogems: 10; }
chest[kind="luxurious"] { adventureExp: 30; primogems: 10; }

/* Historic low-gem area */
chest[kind="common"][scene="3"][area1="1"] {
    primogems: 0;
}

/* A level-2 exception inside the same parent */
chest[kind="common"][scene="3"][area1="1"][area2="104"] {
    primogems: 2;
}
```

Supported attributes for chest: kind, scene, area1 and area2.
Valid kinds: common, exquisite, precious and luxurious. Area1 requires
scene; area2 requires area1. Unknown level-1 areas are rejected by the
runtime preview instead of assuming the high-Primogem default.

## CSS cascade and fields

For this supported selector subset, specificity equals the number of
attribute equality tests. Every rule has one element type. Higher specificity
wins **per reward field**; ties resolve by later source order.
Thus a high-gem child exception remains effective even when a broad parent
rule is written below it.

A missing property does not clear a value inherited from another matching
rule. Zero does clear it. adventureExp -> item 102, primogems -> item 201,
mora -> item 202. Other items are written as item-104001: 2; etc.
The chest-only sigils declaration configures the nation's sigil *quantity*;
the existing source/region mapping chooses the item ID. Explicit
item-<id> overrides the generic sigils field for the same item.
All quantities must be non-negative signed 32-bit integers.
No !important, @import, media queries, CSS layout properties or expressions.

The bundled policy preserves the historical low-gem areas 3:1..7 and 3:9;
3:1:104 (望风山地) and 3:7:703 (孤云阁) restore higher amounts.
Dragonspine 3:10, Lisha 3:8 and the Chasm 3:19/6:300 use higher defaults.

Universal chest defaults (Adventure EXP and Primogems) live in CSS.
All four grades receive 30 Adventure EXP since Version Luna I (6.0); see the
[official update notes](https://genshin.hoyoverse.com/en/news/detail/159349).
The historical 10 / 20 / 30 / 30 quality pattern is outdated in 7.1.
Native DropTable supplies variable Mora, regional sigils,
ores, books and weapons. Optional CSS `mora`, `sigils` and `item-ID`
declarations replace those native items, including explicit zero.
Java's Tier enum contains quality names only. Regional rules override named fields. Old numerical
fallback amounts 66/166/366/566 have been removed.

Existing operator CSS is never rewritten. Older CSS files with `mora`,
`sigils` or `item-104xxx` continue to override the native quantities.
Remove those declarations to use native rolls and region-specific sigils.
Native tables are historical, and their 7.1 probabilities are not independently
verified. Older cached operator CSS values of 10 / 20 remain active unless
manually changed to 30, since the server does not rewrite user files.
If a native root is missing, the server logs a warning and grants only the
CSS-configured values; it does not fabricate random material rewards.
Existing operator CSS is read without modification. Missing chest reward
fields inherit bundled defaults in memory, including for old CSS versions.
Explicit user values (including zero) take precedence. The loader does not
write migrated files, backups or temporary files. Invalid CSS syntax and
unknown properties still fail validation. Set a property to zero to suppress
that reward.

Native random drops, quest rewards, boss flowers, Shrines of Depths and
special chests remain outside this direct-reward policy. See
[chest-reward-preflight-71.md](chest-reward-preflight-71.md)
for live payout boundaries.

## Migration

The former rewards.toml is not read or modified. When rewards.css does not
exist, AstaPS generates it from bundled defaults even if a legacy TOML file
is present. Any settings in legacy TOML have no effect until the operator
ports them manually. No backup is created.

## Missing native pools

`chest[kind="..."][pool="missing"]` in the bundled `rewards.css` applies
only if a regular chest's original native `ChestDrop.json` tag/root cannot
be used. It supplies clearly labelled compatibility Mora, sigils, books
and ores from the older server fallback. Those numbers are **estimates**,
not recovered 7.1 drop rates. Normal native chest rolls do not match these
rules and therefore never duplicate their random yields. Optional explicit
CSS zeroes can disable any compatibility item.

Dragonspine chests tagged with "蒙德" use Anemo Sigils, not Snezhnaya's
Cryo Sigils. Existing root `rewards.css` files are intentionally never
overwritten. To activate new rules, merge them into the operator CSS file,
or copy the bundled template after preserving any custom settings.

Runtime loading inherits absent `pool="missing"` fields from the bundled
template in memory, while preserving the existing operator CSS byte-for-byte.
Explicit custom values (including zero) take precedence.
