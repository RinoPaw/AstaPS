# Reward language: core semantics (working draft)

This document records the intended language independently of its implementation.
The code is being migrated incrementally. Syntax marked **planned** is not yet accepted by the runtime.

## Separation of responsibilities

- **Selector language:** chooses game objects and cascades reward declarations.
- **YAML value language:** represents values, weighted choices, nested compositions and named pools.
- **Reward materialization:** interprets selected values as game items, artifacts, currencies, or other domain outputs.
- **Settlement:** owns claim eligibility, transaction semantics and delivery. It must not be part of selector parsing.

A weighted choice is a generic operation over values. It does not intrinsically know about an item \`type\`.
A domain object may still have its own \`type\` attribute.

## Selector language (implemented by the condition adapter)

\`\`\`css
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
\`\`\`

Predicates:

| Expression | Semantics |
| --- | --- |
| \`[name]\` | attribute exists |
| \`[!name]\` | attribute is absent |
| \`[name = value]\` | exists and equals |
| \`[name != value]\` | exists and differs |
| \`[name < 10]\`, \`[name >= 10]\` | numeric comparison |
| \`[1 <= name < 10]\` | numeric bounded interval |
| \`[name in (1, 2)]\` | value belongs to a set |

A missing attribute **never** satisfies a comparison, including \`!=\`.
Malformed numeric attribute values do not satisfy numeric comparisons.
Mixed numeric/string set literals are rejected.
Numeric comparison uses arbitrary-precision decimals; no floating-point rounding is needed.

An extended predicate contributes the specificity of an ordinary attribute selector.
Existing cascading rules, \`!important\`, and source order still apply.
We are **not** adding \`:where()\` to the new language; its zero-specificity behavior is not needed for current reward rules.
Existing css4j support during transition does not constitute a promise of a future custom implementation.

The current implementation uses an adapter to feed conditions to css4j.
A fully independent selector parser and typed object schema are **planned**.
The adapter's injected attributes are internal implementation details, never configuration syntax.

## YAML choices (partially implemented)

Two supported representations of the **same weighted choice**:

\`\`\`yaml
compact:
  - A: 3
  - B: 1

explicit:
  - {value: A, weight: 3}
  - {value: B, weight: 1}
\`\`\`

A choice samples one value using relative weights. Neither \`value\` nor \`weight\` implies that the sampled value is an item.
A choice can contain a scalar or a structured value; the *consumer* determines what those values mean.
At present, \`YamlLootTables\` is a reward-materializing consumer and consequently still validates eventual item/pool outputs.
A first-class typed \`Value<T>\` evaluation layer usable by other consumers is **planned**.

An unweighted YAML sequence executes each entry independently.
A weighted sequence samples exactly one entry. Mixing weighted and unweighted entries at one level is rejected.
Weights do not need to sum to 100 and zero weights are ignored.
The distinction between \`choose(A, B)\` repeated N times and choosing once then granting N copies is part of the semantic model.

YAML anchors preserve shared graph identity. Probability and recursion safety checks remain mandatory.
All historical loot amounts and probabilities remain unchanged while source provenance is investigated.

## Variables and template calls (planned syntax)

\`\`\`css
boss[tag="maguu_kenki"] {
    --element: [anemo, cryo];
    --material: marionette_core;
    genshin.avatar_ascension.$material: 2;
    astaps.loot.world_gem_two(3, ...$element): 1;
}
\`\`\`

\`$element\` is a variable reference, \`element\` is a literal identifier.
\`$element[0]\` indexes a list and \`...$element\` expands arguments.
\`name(...)\` is a template call.
The existing angle-bracket template implementation is not yet migrated. Do not use the proposed syntax in production until that step lands.
