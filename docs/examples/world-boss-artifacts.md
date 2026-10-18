# World Boss artifact configuration

Electro Hypostasis and Maguu Kenki artifact rules are now included in
`src/main/resources/rewards/rewards.css` (at the end of the file), with their
candidate pools in `src/main/resources/rewards/loot.yaml`.

`world-boss-artifacts.yaml` and `world-boss-artifacts.css` remain standalone
review copies of those artifact rules. The runtime loads the bundled files.
Other Bosses, chests, and weekly sources still have no artifact configuration.

The two Bosses share quantity rules and a five-star pool. Their lower-rarity
candidate sets differ. Nine YAML pools contain 22 weighted set entries; CSS
has eleven consecutive level ranges per Boss. Every active rarity pool rolls
independently. CSS explicitly includes zero outcomes for conditional drops.
YAML `properties.substats` uses the existing percentage syntax.

At levels 45–59, including level 50:

| Boss | Three-star pool, one set chosen per artifact | Four-star pool, one set chosen per artifact |
| --- | --- | --- |
| Electro Hypostasis | Instructor, The Exile, Prayers for Wisdom, Traveling Doctor: 1/4 each | Gladiator's Finale, Wanderer's Troupe, Instructor, The Exile, Prayers for Wisdom: 1/5 each |
| Maguu Kenki | Berserker, Instructor, Lucky Dog: 1/3 each | Gladiator's Finale, Wanderer's Troupe, Berserker, Instructor: 1/4 each |

Both Bosses award three three-star artifacts with probability 22%, or four
with probability 78%. Independently, they award one four-star artifact with
probability 42%, or none with probability 58%. Prayers for Wisdom always
selects a circlet; the other sets select each of their five slots uniformly.
Three-star artifacts start with one or two substats at 66%/34%; four-star
artifacts start with two or three at 66%/34%.

## Source comparison

Native roots are selected from the repository's `data/ChestDrop.json`, then
expanded through `data/Server/DropTableExcelConfigData.json`. Referenced
subtables and artifact attributes come from `RinoPaw/AstaPS-Resource` at
`c0213668889ed05cbeb86660f424c3daf18bfacd`:

- `Server/DropSubTableExcelConfigData.json`
- `ExcelBinOutput/ReliquaryExcelConfigData.json`

The bundled artifact rules were read back and compared with all native candidate
item IDs and their complete probabilities using exact rational arithmetic.
The comparison covers both Bosses at levels 1–200, 1000, and 2147483647:
404 Boss/level combinations. It preserves quantity distributions, independent
rarity rolls, set weights, slots, and initial substat counts. Levels above
120 continue the final native row. No test suite or full server was run.

## Remaining decisions and source gaps

The unusual Frostnight Scion and similar Boss distributions are not inferred
from these examples. Their fixed slots and linked substat counts require
separate weighted entries if preserved. The earlier question about retaining
those source distributions remains unresolved.

Precision Clockwork references 542901, 542902, 542903, 542904, 542905,
542906, 542908, and 542909. They are absent from the examined native primary
tables, subtables, and item resources. No replacement is guessed here.

The separately identified material/gem name-resolution failure remains a
bundled configuration loading issue; these artifact examples do not repair it.
