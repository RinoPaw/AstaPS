# 7.2.0 resource patch for avatar and chapter text hashes

Use this tool on a copy of the 7.2.0 resources that matches the bundled mappings. It updates the avatar names used by server lookups such as handbook and command search. It also aligns chapter hashes in the resource data for consumers of those fields. AstaPS currently sends the client a chapter ID and state, so an in-game chapter title fix still needs client-side verification.

The mappings cover 154 avatar names, 92 hash fields across 46 chapters, and 22 Simplified Chinese TextMap entries. The checks establish which values are present in the selected resources. The target text mappings still need to be checked against the intended 7.2.0 resource build.

## Apply and verify

Run from the repository root.

```sh
python tools/data-patch-7.2.0/validate_data_patch.py /path/to/resources
python tools/data-patch-7.2.0/apply_data_patch.py /path/to/resources --dry
python tools/data-patch-7.2.0/apply_data_patch.py /path/to/resources
python tools/data-patch-7.2.0/validate_data_patch.py /path/to/resources \
    --expect tools/data-patch-7.2.0/expected-report.json
```

The tools follow the server's table precedence. It searches `Server/` first, then `ExcelBinOutput/`, and accepts an active JSON table. If a higher-priority TSJ or TSV file is active, both tools report which file the server would load.

The preview reports fields that would change, values already applied, and working values it will preserve. The default validator checks that the resource set can be patched. With `--expect`, it also checks that every mapped field and each of the 22 exact TextMap entries is present on disk. A variant that preserves different working values can pass the preview and fail strict verification.

Each changed file is backed up with a unique suffix and replaced atomically. The tool checks the inputs before writing and publishes new text entries before the tables that refer to them. Running it again after a partial write completes the remaining changes. A fully applied patch is a no-op.

Restart the server after updating its resources. If the tables changed while the TextMap file stayed the same, move `TextMap/TextMapCache.bin` under the configured cache directory aside before restarting so AstaPS indexes the new hashes.

## Safety checks

- A hash changes only when the current value matches the expected old value, its Chinese text is missing, and the replacement has nonblank Chinese text.
- If a change would remove a working translation from another installed TextMap, the preview reports the affected field.
- Missing rows, unexpected unresolved hashes, missing target text, and conflicting TextMap additions stop the patch before any resource files are written.
- `--dry` leaves resource files and backups untouched.

## Tests

```sh
python -B -m unittest discover -s tools/data-patch-7.2.0 -v
```

The tests use temporary fixtures. They cover preview, strict verification, reruns, backups, loader precedence, working translations, and recovery after a failed write.
