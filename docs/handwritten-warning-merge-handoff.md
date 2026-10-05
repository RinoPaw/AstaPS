# Handwritten warning merge handoff

## Goal

Finish integrating the hand-written Java warning cleanup into `play/rino`, then continue with the protobuf/generated-code warning chain.

User instruction: once the hand-written warning cleanup is ready, merge it into `play/rino` immediately. Do not ask the user to test before CI passes. When testing is eventually requested, provide one complete copy-paste command that includes remote fetch, branch switch, and build.

## Current integration state

- Repository: `RinoPaw/AstaPS`
- Integration target: `play/rino`
- `play/rino` head at handoff: `02ccf85bfc91303dd02db62e018b48d1321e211d`
- `play/rino` head message: `ci: count unique protocol inputs`
- Warning source head: `fix/death-lifecycle-7.1` at `5fdfd84ad9ce073c690b8f77600df8c557ad2ad8`
- Temporary integration branch: `tmp/merge-handwritten-warnings`
- Temporary PR: #13, `tmp: merge handwritten warning cleanup`
  - head: `fix/death-lifecycle-7.1`
  - base: `tmp/merge-handwritten-warnings`
  - automatic merge has conflicts
  - this PR is temporary only; do **not** merge it blindly

Before making any write, re-fetch `play/rino` because it may have advanced after this document was committed.

## Important scope boundary

Do **not** merge the whole `fix/death-lifecycle-7.1` branch into `play/rino`.

That branch contains unrelated lifecycle/gameplay work. The desired integration contains only the hand-written warning fixes. Keep these out of the warning merge:

- generated Java under `src/generated/main/java`
- protobuf generation/build-chain changes
- unrelated death-lifecycle/gameplay changes
- the bad Scene commit `7bd32c6c...`

The warning cleanup was previously treated as the diff from:

- base: `ca7585f2524e30060d3f928abbc0dd2456058022`
- warning head: `5fdfd84ad9ce073c690b8f77600df8c557ad2ad8`

Re-verify that this range contains only the intended hand-written warning cleanup before committing.

## Warning baseline

Original `warnings-full.log` summary:

- total warning lines: 100,021
- generated Java: 99,868
- hand-written main code: 132
- tests: 21

Categories:

- deprecation: 99,317
- serial: 645
- this-escape: 28
- lossy-conversions: 12
- cast: 12
- fallthrough: 4
- static: 2
- overrides: 1

Most remaining warnings are generated protobuf Java using deprecated APIs. The current task stops before that generated-code cleanup.

## Known hand-written cleanup commits near the end

- `776ea3b845821d5160fd28ff11b034b9976e2989` — EntityWeapon final
- `72a0a1a2f43f229d7214d63057a941545f27a532` — EntityMonster final
- `1d5ef5b2fac56e77a536a97a61e38e42978314e7` — EntityGadget final
- `96e16fe5972e227a51974820a66800f451cce1e6` — avoid virtual Gacha load during construction; private `loadInternal`
- `2518d9f2d811d12aa96b145431fc4387b25fb817` — remove redundant Inventory casts
- `76da32633d1029f210a01a63edfff647461c01c5` — narrow suppressions for intentional World construction escape
- `15116db6a4e221a1298a2ca99544c2938362f7be` — use `GAME` config in TeamManager
- `3b1427413439e0f1ce0244c06253a5a4f9594077` — use `GAME` config in TowerManager
- `5fdfd84ad9ce073c690b8f77600df8c557ad2ad8` — Scene constructor avoids virtual `getId`; use `GameData.getSceneRoutes(sceneData.getId())`

Earlier cleanup also covered Position, FileUtils, Language.TextStrings serial UID, AbilityModifier/AbilityMixinData serialization, config deprecations, Player equals/hashCode, Player constructor suppression, tests, EntityAvatar, and related hand-written warnings.

## Merge strategy

A whole-branch merge is unsafe because `play/rino` and the warning branch have diverged, and `play/rino` has newer protocol/TPS work.

Use a file-level three-way merge for the warning diff. The intended patch was previously estimated at about 40 files; about 10 warning-touched files also have later `play/rino` changes.

For every warning-touched path, compare base / warning-head / latest-play versions:

1. If `play == base`, take the warning-head version.
2. If `warning-head == base`, there is no warning change to apply.
3. If `play == warning-head`, it is already integrated.
4. If both changed, perform a real three-way content merge and preserve all later `play/rino` changes.

Do not overwrite current `play/rino` files wholesale in case 4.

Recommended final history: create one clean integration commit directly on the latest `play/rino` tree, containing only the warning-cleanup file changes. A commit message such as `Merge handwritten warning cleanup into play/rino` is appropriate. Do not make `fix/death-lifecycle-7.1` a merge parent because that would pull unrelated commits into ancestry.

## Connector workflow

Useful GitHub connector actions already used successfully:

- `fetch_file`
- `fetch`
- `compare_commits`
- `create_branch`
- `create_pull_request`
- `update_ref`

For an atomic multi-file integration, prefer Git data operations if available:

1. fetch latest `play/rino` head and tree
2. create blobs for merged file contents
3. create a tree based on the latest play tree
4. create one commit with the latest play head as its parent
5. update `play/rino` with an expected-head lease

If those low-level actions are unavailable, sequential contents-API updates are acceptable only if branch movement and partial-commit risk are controlled carefully.

## Temporary artifacts

PR #13 exists only to expose GitHub's merge/conflict state. It should not be merged. After the real integration is complete, close PR #13 and remove `tmp/merge-handwritten-warnings` if convenient.

Old orphan blobs from earlier failed work must not be reused:

- `9b6455f02ffd8938ac2cd862ec520586bf829c8a`
- `e933c0384781ab27e8a06d1092444df1548ad2df`
- `9c85f25aaa58e12e288c1070eb4799e1f4ae3e12`
- `5cc5e4590eb03098f756cf18d9301d064b38d02f`

## CI policy

Do not let CI slow down iterative merge work. CI is mandatory before:

- asking the user to test
- submitting upstream

The existing `death-lifecycle-validate.yml` workflow is scoped to `fix/death-lifecycle-7.1`, so confirm what validation applies to `play/rino` after the clean integration commit.

## Next task after this merge

Continue the protobuf/generated warning chain.

Known build context:

- Java 21
- protobuf runtime 3.25.9
- no protobuf Gradle plugin in the warning branch build file
- generated Java is checked in under `src/generated/main/java`
- source `.proto` files were not found in the working repo during the previous investigation

For reverse engineering / regeneration provenance, inspect `Genshin-Reverse` and comparable projects before changing the generation chain.

## Immediate next actions for the new session

1. Read this file.
2. Re-fetch latest `play/rino` and verify the head.
3. Re-run compare `ca7585f...` -> `5fdfd84...` and obtain the exact warning-touched file list.
4. Three-way merge warning changes into latest `play/rino`, preserving all later play-side edits.
5. Commit the clean warning-only integration to `play/rino`.
6. Verify the commit contains no generated/protobuf or lifecycle-only changes.
7. Close temporary PR #13 / clean the temporary branch.
8. Then start protobuf generation-chain work.
