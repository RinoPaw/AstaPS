# AstaPS first-unlock rewards

The root-level operator-owned file is now **rewards.css**. On first launch,
the bundled src/main/resources/rewards.css is copied to the server's
working directory. Existing rewards.css is never overwritten.

If the old rewards.toml exists and rewards.css does not, startup **fails**
instead of silently discarding custom rewards. Back up the TOML file and
port custom rules to CSS before restarting. The old TOML remains untouched;
there is no automatic conversion and no legacy fallback.

## Selectors and declarations

Selectors match game object attributes; the brace block contains only rewards:

```css
transPoint[kind="waypoint"],
transPoint[kind="statue"],
transPoint[kind="domain"] {
    adventureExp: 50;
    primogems: 5;
}

transPoint[scene="3"][point="6"] {
    adventureExp: 10;
    primogems: 0;
}

transPoint[scene="7"] {
    adventureExp: 0;
    primogems: 5;
    item-101692: 20;
}
```

The three supported transport kinds are waypoint, statue and domain.
The selector attributes are kind, scene and point. All IDs are positive
decimal numbers; omitting point means any point in that scene. Groups of
selectors separated by commas share one declaration block.

All valid CSS selectors are parsed using ph-css. The supported safe subset
allows only object types and equality-attribute selectors; no descendant
combinators, pseudo-selectors, at-rules or dynamic expressions.

CSS specificity applies: a selector with more equality attributes wins even
if a more general rule appears later. At equal specificity, the later
matching declaration wins. Fields not mentioned in a rule are inherited from
other matching rules; an explicit zero clears an item count.

adventureExp, primogems and mora are non-negative 32-bit integers.
Additional item amounts are written as item-<positive ID>: <non-negative count>.
Unknown properties, duplicate item IDs in a block, invalid amounts and
unsupported selectors stop startup instead of being ignored.

The old ExcelBinOutput/TransPointRewardConfigData.json and rewardId do
not decide rewards.

## Native first-unlock boundaries

For waypoints and statues, rewards use the existing permanent unlock state.
Releasing quest locks never grants an additional reward. Pre-unlocked or
hidden native points, portals and unknown subtypes are ineligible.

For domain entrances, UnlockTransPointReq for a native DungeonEntry is
the activation event, even for story entries without rewardDataId.
A persisted rewardedDungeonEntries activation receipt guards against duplicate
claims. The Adventurer Handbook only resynchronizes existing discoveries,
and entering or replaying a dungeon never triggers a first-unlock payout.

Existing saves can lack old activation receipts; no retroactive mass award
occurs. Domain completion loot, resin, repeated challenges and tree claims
are unaffected.
