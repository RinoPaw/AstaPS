# Lootweave: LootCSS (.lcss) and YAML reward tables

This document records the intended language independently of its implementation.
The code is being migrated incrementally. Syntax marked **planned** is not yet accepted by the runtime.

## Separation of responsibilities

- **Selector language:** chooses game objects and cascades reward declarations.
- **YAML value language:** represents values, weighted choices, nested compositions and named pools.
- **Reward materialization:** interprets selected values as game items, artifacts, currencies, or other domain outputs.
- **Settlement:** owns claim eligibility, transaction semantics and delivery. It must not be part of selector parsing.

A weighted choice is a generic operation over values. It does not intrinsically know about an item `type`.
A domain object may still have its own `type` attribute.

## Selector language (standalone parser)

```css
chest[grade="luxurious"][70 <= level <= 79] {
    genshin.currency.primogem: 10;
}

blossom[type="wealth"][WR >= 6] {
    genshin.currency.mora: 60000;
}

chest[city in (1, 2)][grade != "common"] {
    genshin.currency.primogem: 5;
}

chest[!grade] {
    genshin.currency.mora: 0;
}
```

Predicates:

| Expression | Semantics |
| --- | --- |
| `[name]` | attribute exists |
| `[!name]` | attribute is absent |
| `[name = value]` | exists and equals |
| `[name != value]` | exists and differs |
| `[name < 10]`, `[name >= 10]` | numeric comparison |
| `[1 <= name < 10]` | numeric bounded interval |
| `[name in (1, 2)]` | value belongs to a set |

A missing attribute **never** satisfies a comparison, including `!=`.
Malformed numeric attribute values do not satisfy numeric comparisons.
Mixed numeric/string set literals are rejected.
Numeric comparison uses arbitrary-precision decimals; no floating-point rounding is needed.

An extended predicate contributes the specificity of an ordinary attribute selector.
Existing cascading rules, `!important`, and source order still apply.
We are **not** adding `:where()` to the new language; its zero-specificity behavior is not needed for current reward rules.
Existing css4j support during transition does not constitute a promise of a future custom implementation.

The **LootCSS** `.lcss` parser and selector matcher are implemented independently of css4j. Supported selectors use object types, attribute predicates, numeric ranges, membership lists and comma-separated alternatives. Unsupported syntax includes `:is()`, `:not()`, `:where()`, `&` and `!important`. A typed object attribute schema remains future work.

## YAML choices (partially implemented)

Two supported representations of the **same weighted choice**:

```yaml
compact:
  - A: 3
  - B: 1

explicit:
  - {value: A, weight: 3}
  - {value: B, weight: 1}
```

A choice samples one value using relative weights. Neither `value` nor `weight` implies that the sampled value is an item.
A choice can contain a scalar or a structured value; the *consumer* determines what those values mean.
At present, `YamlLootTables` is a reward-materializing consumer and consequently still validates eventual item/pool outputs.
A first-class typed `Value<T>` evaluation layer usable by other consumers is **planned**. The current YAML runtime already understands the generic `value` / `weight` branch structure, but it still materializes rewards at the output boundary.

An unweighted YAML sequence executes each entry independently.
A weighted sequence samples exactly one entry. Mixing weighted and unweighted entries at one level is rejected.
Weights do not need to sum to 100 and zero weights are ignored.
The distinction between `choose(A, B)` repeated N times and choosing once then granting N copies is part of the semantic model.

YAML anchors preserve shared graph identity. Probability and recursion safety checks remain mandatory.
All historical loot amounts and probabilities remain unchanged while source provenance is investigated.

## Variables and template calls (new syntax under migration)

```css
boss[tag="maguu_kenki"] {
    --element: [anemo, cryo];
    --material: marionette_core;
    genshin.avatar_ascension.$material: 2;
    astaps.loot.world_gem_two(3, ...$element): 1;
}
```

`$element` is a variable reference, `element` is a literal identifier.
`$element[0]` indexes a list and `...$element` expands arguments.
`name(...)` is a template call.
The CSS declarations and YAML template definitions now accept the new syntax. The bundled tables and older tests still contain angle-bracket expressions during migration; the normalizer converts function calls to existing internal table keys. End-to-end Gradle validation is required before release.

## Implementation checklist

- [x] Numeric selector comparisons, bounded ranges, `in`, `!=` and missing-attribute predicates.
- [x] Bundled CSS migrated away from `data-range-*`.
- [x] YAML weighted shorthand and explicit `{value, weight}` branch forms.
- [x] Dollar references and function-style template calls accepted by CSS declarations.
- [x] Dollar parameters and function-style template definitions accepted by YAML.
- [ ] Migrate all bundled YAML and CSS template uses from angle brackets.
- [x] Implement the standalone selector parser and AST. A typed object attribute schema remains pending.
- [ ] Implement domain-independent typed value-expression compilation and execution.
- [ ] Run full Gradle tests and CI before server testing or an upstream submission.

## Files

- `*.lcss`: **LootCSS**, the Lootweave selector, variable and cascading declaration language. Loaded in filename order.
- `loot.yaml`: structured reward pools and weighted choices.
- `.lw` and `.css` reward files are not loaded.
