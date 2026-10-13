# Runtime configuration layout

Server-operator configuration files belong in the AstaPS working directory,
alongside `config.json`. **Do not create a `config/` directory** for new
settings. Packaged default templates remain in `src/main/resources/`.

```text
AstaPS/
├── config.json                  # Existing global server settings
├── rewards.toml                # Unified operator-owned reward policy (implemented)
├── data/                       # Server data tables and some runtime state
└── resources/                  # Client-extracted and server-supported game resources
```

## Ownership and precedence

- `config.json`: network, database, services and global settings.
- `rewards.toml`: operator-owned reward policies. Currently only the
  `transPoint` section is active. It owns first activation rewards for
  waypoints, Statues of The Seven and domain entrances, but not dungeon
  completion or resin rewards.
- `resources/`: client-extracted point, dungeon, item, quest and Excel data.
  Native IDs and scene topology identify the target, but do not override
  documented operator policy.
- `data/`: server-owned data tables, including `ChestDrop.json`,
  `MonsterDrop.json` and `DungeonDrop.json`. This also contains some
  generated state. Large drop graphs remain in their own JSON files; do
  not duplicate them as huge hand-written TOML arrays.
- Database and claim-state files: player progression, not reward definitions.

Each reward feature must declare a single authority and matching priority.
Unknown `rewards.toml` fields fail validation; native tables do not silently
replace invalid operator settings.

## Future features

New reward families (world chests, Boss rewards, domain completion rewards)
may receive independent namespaces in `rewards.toml` once their grant/claim
semantics and data have been verified. These namespaces are not active today.
Unrelated gameplay configuration needs its own review; no `config/`
subdirectory hierarchy is planned.

## Deployment and legacy cleanup

The bundled reward template is `src/main/resources/rewards.toml`.
On startup, if the root-level `rewards.toml` is missing, the loader first
imports a previous `config/rewards.toml`, or otherwise converts an older
`config/rewards/trans-point-rewards.toml`. It validates before writing,
does not overwrite a root-level file, and removes the successfully migrated
old reward file. If two old generations coexist, an older copy is removed
only when its migrated contents are identical to the new root file.
Any now-empty directories are removed. Unrelated or divergent files inside
`config/` are never deleted automatically. Once the
root-level file exists, it is the sole authority.

Both `config.json` and `rewards.toml` are ignored by Git. Generated
runtime files must not be committed; malformed files fail startup rather than
being silently replaced with defaults.
