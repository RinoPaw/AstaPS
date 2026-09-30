# Resource compiler workflow

This repository can build a generated `resources/` directory from an upstream game-data dump instead of editing the dump in place.

The intended layout is:

```text
animegamedata2/      raw upstream dump, never edited
resources-custom/    optional hand-maintained Server/Scripts/ScriptSceneData overlay
resources/           generated output consumed by AstaPS
tools/resource_compiler.py
```

`animegamedata2/` and `resources/` are local-only and already ignored by Git. Keep the raw dump immutable so every transform is reproducible.

## First-time migration

If the Dimbreath checkout is currently named `resources`, rename it once:

```powershell
Rename-Item .\resources animegamedata2
```

Then build generated resources:

```powershell
uv run python .\tools\resource_compiler.py build
```

If `resources/` already exists and should be regenerated:

```powershell
uv run python .\tools\resource_compiler.py build --force
```

The compiler currently performs one real normalization: duplicate object keys in `BinOutput/Ability/Temp` are resolved with last-value-wins semantics. It also writes `resources/.resource-compiler.json` so the generated tree records what was changed.

## Diagnose resources

Run the doctor against either the raw dump or generated output:

```powershell
uv run python .\tools\resource_compiler.py doctor .\animegamedata2
uv run python .\tools\resource_compiler.py doctor .\resources
```

The doctor currently reports:

- required top-level resource directories;
- missing `Server`, `ScriptSceneData`, and `Scripts` overlays;
- several tables AstaPS already reported missing;
- whether `ConfigGlobalCombat.defaultAbilities` is still obfuscated;
- Scene Point files whose top-level shape is an array instead of the object shape AstaPS currently expects.

To scan only Ability JSON duplicate keys without modifying anything:

```powershell
uv run python .\tools\resource_compiler.py scan-duplicates .\animegamedata2
```

## Development rule

Do not patch files inside `animegamedata2/` manually. When a new incompatibility is understood, add a deterministic transform or validator to `resource_compiler.py`, rebuild `resources/`, and keep AstaPS runtime code focused on server behavior.

For unknown schema changes, diagnose first. A transform should only be added once the old and new semantics are understood; generating plausible-looking data just to silence startup errors makes later debugging harder.
