# Runtime configuration layout

The server operator owns **rewards.css** and **config/**. The project and resource pack must not
silently overwrite local policy. `config.json` remains the global settings
entry point for network, database, services, and existing global tuning.

The first-unlock and standard-chest fixed-reward definitions now share one file:

```text
AstaPS/
├── config.json                          # Existing global settings
├── rewards.css                         # Unified first-unlock + chest fixed rewards
├── config/
│   ├── drops/
│   │   ├── chests.toml                 # Future, not yet created
│   │   ├── monsters.toml               # Future, not yet created
│   │   └── domains.toml                # Future, not yet created
│   ├── artifacts/
│   │   └── rolls.toml                  # Future, not yet created
│   └── domains/
│       └── schedules.toml              # Future, not yet created
├── data/                                # Server data tables and some runtime state
└── resources/                           # Client-extracted and server-supported game resources
```

## Ownership and precedence

- `resources/`: client-extracted point, dungeon, item, quest, and Excel data.
  Native IDs and scene topology may be used to identify a target. They do not
  override an operator's gameplay policy.
- `data/`: server-defined data tables, especially `ChestDrop.json`,
  `MonsterDrop.json`, and `DungeonDrop.json`. Most DataLoader calls prefer a
  local `data/` copy over the bundled `src/main/resources/defaults/data/` copy,
  whereas certain bundled-first loaders intentionally reverse that priority.
  This directory currently also contains state and generated files; it has mixed
  ownership and should not become the default home for new human-written policy.
  Existing tables and their load behavior remain untouched.
- `rewards.css`: the **single** server-owner authority for transport first-unlock
  rewards and standard exploration chest fixed rewards. CSS equality-attribute specificity determines
  precedence; later source order resolves equal-specificity fields.
- `config/`: server-owner policy for other gameplay systems written in TOML
  when concise enough to maintain by hand. Large, machine-generated data should not be converted to huge TOML
  arrays merely for consistency.
- Database and existing claimed-state files: runtime player/claim state, never
  treated as configurable reward definitions.

Each subsystem must document exactly one policy authority and a single precedence
scheme. Reject malformed or conflicting operator files; do not silently return to
stale native rewards. Configured values may refer to native IDs, but they are not
overwritten by native asset changes.

## Future subsystem boundaries

- `rewards.css` / `transPoint`: first discovery/activation rewards for
  waypoints, Statues of The Seven and manually activated domain entrances.
- `rewards.css` / `chest`: fixed amounts for standard exploration chest
  kinds and area overrides. These values are parsed but not yet used by
  the chest runtime path.
- `drops/chests.toml`, `drops/monsters.toml`, `drops/domains.toml`:
  loot pool policy, drop probabilities and counts. These do not define
  the first-activation domain reward or domain unlock rules.
- `artifacts/rolls.toml`: generation of artifact main stats, substats,
  initial rolls, and upgrade weights; independent of the dungeon's chance to
  produce an artifact.
- `domains/schedules.toml`: domain availability by day/time and reset-time
  interpretation; independent of domain pool/drop rates. Keep native dungeon
  identity and client-visible schedules consistent with server admission.

Do not introduce a global generic selector engine or shared hot-reload behavior
until another subsystem needs them. Each feature owns validation and tests.

## Deployment

Bundled defaults belong to `src/main/resources/`. The operator-owned
root-level `rewards.css` and runtime files under `config/` are gitignored.
First startup generates the unified rewards file if missing. If legacy
`rewards.toml` exists but `rewards.css` does not, startup refuses to ignore
operator customizations; back up/migrate the legacy file explicitly.
Existing CSS is never overwritten. Unknown selectors, properties and invalid
amounts fail validation.

Only the documented unified rewards path is read. The old
`config/rewards/trans-point-rewards.css` is no longer read; operators with
custom transport rules must transfer them into `transPoint` selector rules in `rewards.css`.
No automatic legacy-path imports,
migration branches, or undocumented fallbacks are added for draft features.
Breaking schema changes must be documented and tested explicitly.
