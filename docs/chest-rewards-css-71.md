# Standard chest fixed rewards — rewards.css (7.1)

The operator owns one root-level **rewards.css** for transport and chest rules.
The file is copied once from src/main/resources/rewards.css; existing files
are not overwritten. The loader validates **all** rules before publishing an
updated snapshot. A malformed selector or reward amount prevents startup.

**Important:** transport reward rules are live. Verified standard world chests
now use the CSS fixed rewards on the existing WorldChestLootHelper fallback path.
Random/native quest loot and special chest flows remain separate. Chest claim
lifecycle and ground-item delivery retain their existing behavior.

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
All quantities must be non-negative signed 32-bit integers.
No !important, @import, media queries, CSS layout properties or expressions.

The bundled policy preserves the historical low-gem areas 3:1..7 and 3:9;
3:1:104 (望风山地) and 3:7:703 (孤云阁) restore higher amounts.
Dragonspine 3:10, Lisha 3:8 and the Chasm 3:19/6:300 use higher defaults.

Matching CSS amounts override the corresponding fallback item ID. Explicit zero
prevents that item from dropping, while omitted fields retain the existing
fallback values (e.g. Mora, materials and sigils). Legacy tier fallback gems
are 2/5/10/10, never the obsolete 66/166/366/566 values.

Native random drops, quest rewards, boss flowers, Shrines of Depths and
special chests remain outside this direct-reward policy. See
[chest-reward-preflight-71.md](chest-reward-preflight-71.md)
for live payout boundaries.

## Migration

The former rewards.toml is not read. If it exists when rewards.css does not,
startup fails with instructions to back it up and port any custom rules.
A new CSS file must be generated from the bundled template, then reviewed
against customized TOML. Do not silently assume that an old custom TOML
file and the new bundled CSS represent the same intent.
