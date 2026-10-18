# World Boss artifact configuration

The final section of `src/main/resources/rewards/rewards.css` now configures
artifacts for 47 world Boss identities, using twelve generic definitions in
`src/main/resources/rewards/loot.yaml`. The obsolete two-Boss example files
have been removed; the bundled resources are the configuration to inspect.

## Shared definitions

| Native rarity | Generic definitions | Actual candidate pools |
| --- | ---: | ---: |
| 1 star | 1 | 3 |
| 2 stars | 1 | 3 |
| 3 stars | 2 | 7 |
| 4 stars | 2 | 6 |
| 5 stars, including special native rows | 6 | 6 |
| Total | 12 | 25 |

Definitions use the existing `name<set1, set2>` grammar and quoted
`genshin.artifact.${set1}` interpolation. Parameters replace set identities
only. Candidate probabilities, slot choices, rarities, and initial substat
counts are preserved. Every artifact explicitly specifies its properties;
no uniform default replaces a native 66%/34% distribution.

The 47 complete Boss programs form nine groups. CSS has 101 rules, with
Boss tags grouped when their complete declarations agree and level ranges
in ascending order within each group. Open-ended ranges continue the last
native row beyond level 120. Other rewards are unchanged.

## Independent rolls and linked properties

Each source artifact branch rolls independently. When two branches resolve
to the same candidate pool, repeating its CSS property would overwrite one
branch. Instead, the complete independent execution-count distributions are
convolved, then each resulting execution rolls that candidate pool afresh.
This preserves the entire resulting item-count distribution.

For example, one source branch executing 1/2 times at 95%/5% and another
executing 0/1 times at 79%/21% become 1/2/3 executions at
75.05%/23.9%/1.05%. No expected-value approximation is used.

The unusual native rows for Frostnight Scion and similar Bosses are retained
literally. A fixed slot remains fixed. Where slot and initial-substat count
are linked, weighted entries keep them together. Zero-substat five-star
variants are selected explicitly when the source requires them. These rows
are not replaced with ordinary five-slot artifact drops.

## Original-source comparison

Native roots come from this repository's `data/ChestDrop.json`, expanded
through `data/Server/DropTableExcelConfigData.json`. Referenced subtables and
native artifact attributes come from `RinoPaw/AstaPS-Resource` at
`c0213668889ed05cbeb86660f424c3daf18bfacd`:

- `Server/DropSubTableExcelConfigData.json`
- `ExcelBinOutput/ReliquaryExcelConfigData.json`

The bundled CSS and YAML were read back and compared using exact rational
arithmetic against native item IDs, candidate probabilities, and complete
execution-count distributions. Coverage is levels 1–200, 1000, and
2147483647 for every configured Boss: 9494 Boss/level combinations. The
comparison includes all 25 candidate pools and 197 native artifact variants.

A separate manual parser inspection used the actual `ArtifactResolver`,
`YamlLootTables`, generic expansion, and `CssRewards` implementations, backed
by 4352 native artifact metadata rows. All 9494 configured combinations
matched native probabilities within 1e-11, including slot/substat correlations
and merged execution counts. The 202 Precision Clockwork combinations were
confirmed to have no artifact declarations. Non-artifact item services were
isolated for this inspection; it does not verify full server startup or real
inventory delivery. No test suite or CI was run.

## Unresolved source and loading issues

Precision Clockwork references 542901, 542902, 542903, 542904, 542905,
542906, 542908, and 542909. They are absent from the examined primary tables,
subtables, and item resources. Its five unresolved candidate pools are not
configured. No replacement item or probability is guessed.

The separately identified material/gem name-resolution failure remains a
bundled configuration loading issue; these artifact changes do not repair it.
Artifacts for chests and weekly Bosses remain outside this configuration.
