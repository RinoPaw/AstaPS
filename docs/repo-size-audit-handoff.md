# Repository size / data duplication audit handoff

Date: 2026-10-05 17:39 +08:00

## Baseline

- Working branch for follow-up: `play/rino`
- `play/rino` head before this handoff commit: `b90a758929a5384350d4a6e98f220c9cb45c012b`
- `RinoPaw/AstaPS:main` observed during the audit: `3a0018d6ee56cdcc757f82458f8547a5ef7b0df6`
- This audit has not changed runtime code or data files yet.
- Do not delete or move any `data/` / `src/main/resources/defaults/data/` files until the structural and loader audits below are complete.

## Why this audit started

After replacing checked-in protobuf generated Java with reproducible protobuf generation, the next largest tracked content is mostly data. The two obvious targets are `GadgetSpawns.json` and `Spawns.json`, which appear both in root `data/` and bundled defaults.

## Confirmed file sizes on `RinoPaw/AstaPS:main`

Root runtime data:

- `data/GadgetSpawns.json`: 17,058,866 bytes, blob `280125f492f169c2c4cf9f506ab1e70046dc761c`
- `data/Spawns.json`: 8,483,264 bytes, blob `421847247aa73eba7ae6e6c531dd29545b8e20c5`
- `data/Shop.json`: 1,152,195 bytes, blob `684a3e6a20bd7053d06fc39d5b254328a5f69b05`

Bundled defaults:

- `src/main/resources/defaults/data/GadgetSpawns.json`: 3,546,740 bytes, blob `21beb3e05d5be10e114c1fc10a696159732008e2`
- `src/main/resources/defaults/data/Spawns.json`: 6,450,527 bytes, blob `bbd3684f7b3d2ad2847f4c7802d1a15c4615a22d`

The four spawn files alone account for about 35.5 MB of tracked working-tree content.

## Confirmed duplication signal

Some root/default data files are byte-identical. Example:

- `data/MonsterDrop.json`
- `src/main/resources/defaults/data/MonsterDrop.json`

Both use blob SHA `99fbed5f63e151707f6d3cb2f7f49eae0c0bd61d` and size 140,065 bytes.

This proves the repository does contain real duplicated tracked data, not only similarly named files.

## GadgetSpawns findings so far

- Root and bundled-default blobs are different and have very different textual sizes: ~17.1 MB vs ~3.55 MB.
- Manual sampling of the beginning showed corresponding entries with the same apparent payload while the root copy is much more expanded/formatted.
- Full canonical JSON equality has NOT been proven yet.
- Do not state that these two files are semantically identical until a complete parsed comparison is run.

## Spawns findings so far

- Root and bundled-default blobs are different: ~8.48 MB vs ~6.45 MB.
- Manual sampling found real content differences, including root entries with `level: 36` where the bundled default differs.
- Therefore `data/Spawns.json` cannot currently be treated as a formatting-only duplicate.
- The full set of differing fields and records has NOT been quantified yet.

## Runtime/default-data mechanism

Earlier inspection found that the project has a bundled-default mechanism under `src/main/resources/defaults/data/` and a runtime `data/` directory. Before deleting anything, reopen the relevant loader code and record exact behavior for:

1. when defaults are copied/extracted;
2. whether existing root `data/*` wins over bundled defaults;
3. whether spawn loaders read through a generic `DataLoader` path or directly from files;
4. whether missing root spawn files are automatically restored from bundled defaults;
5. whether runtime mutation writes back to these large files.

Do not rely on the previous verbal summary alone; re-read the exact current code on the branch used for the change.

## AstaPS-Resource status

The relationship with `AstaPS-Resource` has not been completed. The next session must check whether spawn/gadget spawn data already has a canonical source or generation/export path there. Do not move the files merely to make the code repository smaller if that creates a second unofficial source of truth.

## Required next steps

1. Start from latest `play/rino`; verify branch HEAD first to avoid version drift.
2. Materialize or locally fetch the four large JSON files and compare them by parsed JSON structure, not text diff.
3. For `GadgetSpawns.json`, calculate:
   - top-level type and item count;
   - canonical/minified JSON hash;
   - per-record identity/key comparison;
   - exact differing JSON paths, if any.
4. For `Spawns.json`, calculate:
   - item counts and stable record matching;
   - exact field-level difference counts;
   - distribution of `level` differences;
   - whether differences are only local gameplay customization or include coordinates/entities/scenes/groups.
5. Re-read the data/default extraction and spawn loading code and document precedence explicitly.
6. Search `RinoPaw/AstaPS-Resource` and relevant upstream/similar projects for the canonical source and expected packaging model.
7. Only after 2-6 decide among:
   - remove tracked root runtime copies and seed them from bundled defaults;
   - keep root data and remove bundled duplication;
   - move canonical large data to `AstaPS-Resource` and add an explicit sync/bootstrap path;
   - keep both if they are intentionally different, but document the distinction.
8. If a cleanup change is made, do it on a new branch based on latest `play/rino`. Do not mix it into warning cleanup or protobuf work.
9. Before asking the user to test or before upstream submission, run the full required CI. If local testing is needed, provide one complete PowerShell block including fetch, branch switch/sync, build/test commands.

## Important boundaries

- No deletion has been approved yet.
- Do not normalize or rewrite the huge JSON files just to reduce line count; that produces noisy history without solving ownership/source-of-truth.
- Do not use Git history rewriting (`filter-repo`) as part of this audit unless separately requested.
- Current goal is working-tree/repository hygiene and a clear resource ownership model, not historical repository GC.
- Keep protocol work separate. `protocol/7.1/protocol.desc` is small (~0.6 MB) and intentionally remains tracked as the canonical protobuf descriptor input.

## Resume prompt

`继续仓库大文件/data 双份审计，先读 docs/repo-size-audit-handoff.md`
