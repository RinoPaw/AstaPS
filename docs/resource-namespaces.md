# Resource namespaces (7.1)

Resource names are arbitrary-depth dotted paths. A path has ordered segments,
with no global `namespace/type/name` tuple or fixed depth. Consumers may
interpret a prefix for their own domain (for example, `genshin.avatar_ascension`
for inventory items), but the path and alias infrastructure does not.

Examples:

- `genshin.currency.mora`
- `genshin.currency.sigil.anemo`
- `genshin.avatar_ascension.vayuda_turquoise.sliver`
- `genshin.avatar_ascension.vayuda_turquoise.fragment`
- `genshin.avatar_ascension.vayuda_turquoise.chunk`
- `genshin.avatar_ascension.vayuda_turquoise.gemstone`
- `astaps.loot.world_lightning_prism_roll_2plus1pct38p33`

## Advancement resource paths

All progression resource categories use short `up` suffixes. The historical
`level_up` path segments are invalid (no compatibility aliases):

- Character leveling: `genshin.avatar_up`
- Character ascension: `genshin.avatar_ascension`
- Weapon leveling: `genshin.weapon_up`
- Weapon ascension: `genshin.weapon_ascension`
- Artifact leveling: `genshin.artifact_up`
- Talent leveling: `genshin.talent_up`

## Aliases

`ResourceAliases` supports aliases at **any path depth**. Longest prefix
wins, suffix segments are preserved, aliases can chain, and cycles are errors.

The Genshin namespace registers elemental family aliases, for example:

```text
genshin.avatar_ascension.anemo
  -> genshin.avatar_ascension.vayuda_turquoise

genshin.avatar_ascension.anemo.sliver
  -> genshin.avatar_ascension.vayuda_turquoise.sliver
```

Flattened gem names are invalid resource IDs after this breaking migration.

This is an **identity** alias. The item registry resolves the final leaf to
the existing native item ID. The family prefix on its own is not an item.

The namespace service can be instantiated with a map of arbitrary resource
path aliases. New consumers should reuse it instead of defining their own
element-to-name lookup in Loot, CSS or YAML.

## Recipes

Cookable recipe definitions and inventory unlock items share one recipe family:

```text
genshin.recipe.mora_meat          -> CookRecipeData ID (for example 100014)
genshin.recipe.mora_meat.item     -> inventory item ID (for example 109406)
```

Reward CSS and YAML can award the inventory `.item` child, but cannot grant
the recipe definition as though it were an inventory item. The old
`genshin.material.recipe_mora_meat` path is rejected. The native item registry
classifies names with the `recipe_` prefix as recipe inventory resources.

## Rewards CSS

The rewards stylesheet accepts resource IDs as declaration keys:

```css
boss[type="world"] {
    genshin.currency.mora: 4725;
    astaps.loot.artifacts_gladiators_wanderers_5star: "1:42";
}
```

The server's CSS adapter translates dotted declaration keys to temporary
CSS-safe identifiers before invoking ph-css and restores the full path after
parsing. Selectors, ranges, cascading and loot-count values are unchanged.

Old `--genshin-currency-mora` and `--astaps-loot-xxxhigh` declarations are rejected.
CSS declarations must use dotted resource paths. This is an intentionally breaking
migration, with no legacy syntax or flattened gem IDs.

## YAML

Bundled loot item IDs are canonicalized. For example:

```yaml
wind:
  - {reward: genshin.avatar_ascension.vayuda_turquoise.sliver, count: 3}
```

Aliases are accepted in item references too:

```yaml
wind:
  - {reward: genshin.avatar_ascension.anemo.sliver, count: 3}
```

`ResourceId` parses and validates paths; `ResourceAliases` resolves aliases;
the domain registry validates leaf existence and maps it to a native ID.

Template syntax such as `astaps.loot.world_boss_gem<anemo>` is a **separate**
future extension. A resource path or alias does not, by itself, define a
parameterized Loot template, and this refactor does not silently invent one.
