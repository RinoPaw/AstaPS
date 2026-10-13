# WorldArea level-2 support (7.1)

The root source is `resources/BinOutput/Scene/WorldArea/scene<N>_worldArea.json`.
`WorldAreaIndex` continues to support the existing 2-D
`level1AreaAt(x, z)`. It also exposes:

- `areaAt(x, y, z)` → `AreaIds(level1, level2)`
- `level2AreaAt(x, y, z)` → level-2 ID

A zero ID means the corresponding boundary was not located. A known level-1
ID with level-2 = 0 must not silently inherit a guessed subarea reward policy.

## Verified 7.1 resource fields

- `OBKNAJCIHEA[]`: list of level-1 parent records
- `LKBLPFHOAFB.BBLLBIOLMHP`: level-1 ID
- `OPHHFKKPLDI[]`: child area polygons
- `OPHHFKKPLDI[].BBLLBIOLMHP`: expected level-1 parent ID
- `OPHHFKKPLDI[].EOIFEEDBAFJ`: level-2 ID
- `JLFEAGHGCLP.BFLLDPOAJGD[]`: polygon points, `_x`/`_z`
- `JLFEAGHGCLP.MHLOLALFHHF._y`: optional minimum height
- `JLFEAGHGCLP.NFFDCMLAAFO._y`: optional maximum height

The geometry is **three-dimensional** for subareas, even though the
horizontal shape is a polygon. Omitted Y bounds are treated as unbounded.
The order of the game's source polygon array is retained when multiple
polygons match, and separate polygons sharing one area ID are retained.
We have not independently proven the original client's overlap precedence.

## Scene 3 coverage

Source snapshots:
- [WorldAreaConfigData.json](https://github.com/MeChen618/AstaPS-Resource/blob/main/ExcelBinOutput/WorldAreaConfigData.json), blob `ca05682b11a4cc742c4f98d4f610bf11c9ffe980`
- [scene3_worldArea.json](https://github.com/MeChen618/AstaPS-Resource/blob/main/BinOutput/Scene/WorldArea/scene3_worldArea.json), blob `d2687fe28c350d9c4684b0a29a5b24f3f6260936`

The table defines 65 level-1 and 409 level-2 area records in scene 3.
The scene 3 boundary source contains 65 level-1 parents and 400 level-2
polygon records, covering **396 distinct areaID2 values**. Three area IDs
have multiple disjoint/overlapping polygons: `4913` has three, while
`4903` and `4905` have two each. 273 of the 400 subarea polygons have
at least one non-default or missing Y bound (rather than both -1000 and 1000).
This is why a 2-D child lookup is inadequate.

Thirteen level-2 IDs from the config table have no child polygon in the
corresponding scene 3 boundary source:

| areaID1 | Unresolved areaID2 |
| --- | --- |
| 41 | 4105, 4107 |
| 25 | 2501 |
| 32 | 3120 |
| 35 | 3512 |
| 36 | 3602 |
| 39 | 3912 |
| 43 | 4301 |
| 48 | 4805, 4807, 4808 |
| 73 | 7301 |
| 75 | 7503 |

The current parser **does not invent outlines** for those IDs. They require
a validated alternate source before being used as precise reward selectors.

## Verify

```powershell
python tools/audit_world_area_level2.py --resources resources --scene 3 --output world-area-level2.json
python -m unittest discover -s tests -p test_world_area_level2_audit.py -v
```

The Java `WorldAreaIndexTest` exercises mocked vertical overlaps, disjoint
polygons, absent child outlines and, when the 7.1 resource is installed,
ground/high-elevation points from scene 3. Existing users of level-1 lookups
are unchanged.

This branch does not yet change chest reward calculation, `rewards.toml`,
or player state. For chest selectors, use scene ID plus the resolved level-1
and level-2 IDs, and preserve unresolved cases explicitly.
