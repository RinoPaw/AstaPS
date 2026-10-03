# Upstream synchronization

AstaPS is a fork of `MeChen618/AstaPS`. Keep the fork's custom maintenance/documentation work while regularly incorporating upstream implementation fixes.

## Preferred sync behavior

Before starting substantial work, compare `RinoPaw/AstaPS:main` with `MeChen618/AstaPS:main`.

If upstream only changes files that do not overlap local work, merge upstream promptly so the fork retains upstream ancestry. Avoid reproducing upstream patches as unrelated local commits when a clean merge is possible; that makes later comparisons harder.

If both sides changed the same code, inspect the semantic conflict rather than accepting one side wholesale. Preserve local fixes only when they still apply to the current upstream behavior.

## AI workflow

```text
compare fork main vs upstream main
  -> inspect upstream-only commits and changed files
  -> no overlap: merge upstream
  -> overlap: analyze both patches and resolve deliberately
  -> run fast build/tests for code changes
  -> update regression notes when behavior changed
```

Do not block normal investigation on long CI runs. Use source-level comparison and local/fast validation first; CI is confirmation, not the primary debugging loop.
