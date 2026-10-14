# Standard exploration chest identification (7.1)

Status: **classification only**; no interaction, claimed-state, drop table or
reward-grant behavior changed.

Base: `MeChen618/AstaPS@4ae9884dc25703848f01fa274803a39e29dac70f`
(upstream `main`, **not** the fork's diverged `main`).

## Actual 7.1 gadget evidence

`MeChen618/AstaPS-Resource@85b81a691fcf3d9e8e7db50a8febc8f9043bc084`
contains 30,599 `GadgetExcel` definitions, **309** of which declare
`type = Chest`. This does **not** make them 309 standard treasure boxes.

Among the 309 definitions, the supported explicit regular-prefab patterns
cover **106** gadget IDs:

- `Lv1`: 29 variants (including `SceneObj_Chest_Rock_Lv1`) — `common`
- `Lv2`: 25 variants — `exquisite`
- `Lv4`: 32 variants — `precious`
- `Lv5`: 20 variants — `luxurious`

**203 are not accepted on prefab alone**, including `Lv3` (unverified
quality), `Relic`/furnishing, `SearchPoint`/investigation, `EssenceChest`,
`Ticket_Chest`, `MichiaeMatsuri`, `Hero`, `DeshretRune`, Mora
`CrudeChest`, event props and six `NormalChest` rows sharing an ambiguous
name. A plausible `Lv` suffix in a nonstandard prefab is never evidence.

The pinned scene scan found **14,084** Chest-type Lua gadgets, with
**9,020** carrying a `drop_tag`, **1,339** an explicit `chest_drop_id`
and **3,725** neither; this population includes non-treasure objects.
Examples of strong genuine standard evidence:

| Actual ID | Prefab | Typical matching `drop_tag` |
| --- | --- | --- |
| 70211001 | `SceneObj_Chest_Default_Lv1` | `战斗低级蒙德` |
| 70211101 | `SceneObj_Chest_Default_Lv1` | `解谜低级璃月` |
| 70211011 | `SceneObj_Chest_Default_Lv2` | `战斗中级蒙德` |
| 70211111 | `SceneObj_Chest_Default_Lv2` | `解谜中级璃月` |
| 70211021 | `SceneObj_Chest_Default_Lv4` | `战斗高级璃月` |
| 70211121 | `SceneObj_Chest_Default_Lv4` | `解谜高级稻妻` |
| 70211031 | `SceneObj_Chest_Default_Lv5` | `战斗超级蒙德` |
| 70211131 | `SceneObj_Chest_Default_Lv5` | `解谜超级蒙德` **or `灵龛蒙德`** |

The last case demonstrates why quality must *not* be inferred from a
recognizable prefect alone.

## Admission policy

`StandardChestClassifier.classify(EntityGadget)` returns a
`Classification(kind, exclusion)`; a non-null kind is possible **only**
when `isStandard()` is true. No new data fields are added to
`rewards.toml`.

The classifier admits a chest **only** when all conditions are met:

1. `GadgetExcel.type == Chest`, scene type `SCENE_WORLD`, and an authored
   positive Lua group ID are present.
2. There is no `boss_chest`, `chest_drop_id`, or `drop_id`. In particular,
   `1000100` (empty no-drop sentinel) and quest IDs
   `1050256..1050269` never fall back to a standard reward.
3. The prefab belongs to a verified four-grade regular chest family
   (`Default`, `Locked`, `Bramble`, `Frozen`, or `Rock_Lv1`),
   with recognized environmental suffixes. `Lv3` stays unresolved.
4. The exact `drop_tag` belongs to the **ordinary combat or puzzle** tag
   grammar (`战斗|解谜`, optionally prefixed `雪山`), with a known
   region suffix. `灵龛`, `逐月节`, `渊下宫活动`, `雪山玉髓`,
   `书页章节`, `搜刮点`, `摩拉石箱` etc. are excluded.
5. The source tag's four-grade quality agrees with the **prefab** grade.
   Examples of real conflicting data exist: `70211101` (Lv1)
   appears with two `中级` tags; `70211111` (Lv2)
   with one `低级` tag; `70211150` (Lv4)
   with three `低级` tags. Conflicts are rejected, not silently
   assigned the more favorable prize.

The `explore.name = "chest"` marker, standalone gadget ID, fallback
`WorldChestLootHelper.resolveTier`, and scripted `area_id` are **not**
admission evidence.

## Explicit limitations

- `UNVERIFIED_SOURCE` rejects standard-looking chests without a tagged
  native/source family, including synthetic marker spawns. They need
  separate trustworthy origin metadata before receiving fixed rewards.
- `EXPLICIT_DROP` keeps original quest, puzzle and bespoke root behavior;
  some explicitly rooted gadgets may visually be ordinary chests.
  They are not assigned fixed rewards until independently audited.
- An open-world quest script could intentionally reuse both a normal
  prefab and an ordinary-looking `drop_tag`. The classification is a
  *candidate for fixed rewards*, not authority to immediately grant
  and claim the chest. Existing Lua event/drop/claim semantics must be
  checked before runtime integration.
- `Lv3`, `NormalChest`, `Relic`, unusual chest art, new-area gadgets and
  one-off event objects remain unresolved rather than guessed.
- `Kind` and geographic `scene+area1+area2` are independent. The
  geographic selectors proven in Reverse are
  [documented here](https://github.com/RinoPaw/Genshin-Reverse/blob/research/world-area-boundaries-71/versions/7.1.0-global/analyses/progression/world/7.1-chest-primogem-region-selectors.md).
  `areaAt(x,y,z)` exists in a separate
  `feat/world-area-level2-71` branch and is not required by this module.

## Test and rollout

`src/test/java/emu/grasscutter/game/entity/gadget/chest/StandardChestClassifierTest.java`
checks all four grades, special families, dungeon/ungrouped inputs, explicit
drop roots, Boss chests, Shrines using a normal luxurious prefab, absent
source metadata, and conflicting name/tag quality.

This branch does **not** call the classifier from `GadgetChest` or modify
`WorldChestLootHelper`. The next phase must integrate it with the
area selector and a reward-grant success check before changing the
existing payout flow. In particular no Primogems, Adventure EXP or
sigils may be paid twice.
