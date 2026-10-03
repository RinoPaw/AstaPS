# Reverse-engineering status

This page is the maintainer-facing index of reverse-engineering work that may affect AstaPS. It exists to answer one practical question quickly: **what has already been done, and where is the result?**

The source of truth for technical evidence remains [`RinoPaw/Genshin-Reverse`](https://github.com/RinoPaw/Genshin-Reverse). Do not copy conclusions out of this index and treat the copy as new evidence. Follow the linked artifact or commit before changing server behavior.

Last reviewed against `Genshin-Reverse` main: `9d3a29f51bb4f215fb455c030f7814eee8afc7bd` (2026-10-04).

## Status vocabulary

- **COMPLETED** — the investigation or infrastructure milestone has reached its stated result and has a durable artifact.
- **VERIFIED** — the result also passed the validation required by that work, such as exact-sample validation or runtime control.
- **INTEGRATED** — the relevant result has been applied to AstaPS or its maintained resource path.
- **FOLLOW_UP** — the main direction/result is established, but a bounded validation or integration step remains.
- **ACTIVE** — research is still needed to answer the stated question.
- **PAUSED** — unresolved, but deliberately not being pursued until a stated trigger appears.
- **SUPERSEDED** — a newer conclusion or method replaces an older one; preserve the reason in history/indexes rather than keeping dead executable paths.

These workflow states do not replace the evidence vocabulary used by `Genshin-Reverse` (`CONFIRMED`, `HIGH_CONFIDENCE`, `CANDIDATE`, `REJECTED`, `UNRESOLVED`).

## Completed / reusable results

| Work | State | Durable result | AstaPS relevance |
| --- | --- | --- | --- |
| 7.1 canonical CmdId identity registry | **COMPLETED · VERIFIED** | `Genshin-Reverse/versions/7.1.0-global/windows-x64/registry/registry.csv` and `registry.summary.json`; 4,896 unique CmdIds with a strict slot/type/CmdId bijection | New packet investigations should begin here instead of rebuilding the registry from the executable. |
| 7.1 canonical metadata indexes | **COMPLETED · VERIFIED** | `metadata/types.csv`, `fields.csv`, `methods.csv`, `method-pointers.csv`, `type-methods.json`, `runtime-types.csv`; publication manifest in `generated-artifacts.json` | Type/method/field and method-pointer lookup is available infrastructure. Do not regenerate it ad hoc for each bug. |
| Confirmed 7.1 protocol anchors | **COMPLETED · VERIFIED** | `reports/protocol-map.md` currently records confirmed identities for `DoSetPlayerBornDataNotify`, `SetPlayerBornDataReq`, `SetPlayerBornDataRsp`, `PlayerNicknameNotify`, `PlayerEnterSceneNotify`, and `UnlockTransPointReq` | Reuse these as controls and current-version anchors. Historical opcode equality is still insufficient for other messages. |
| Scene-handler dispatch slot extraction | **COMPLETED** | `analyses/scene-handler-dispatch/` preserves the exact-sample scan, provenance and reproducible `genshinre scene-handler-slots` workflow | The one-off Actions artifact has been converted into a durable analysis plus reusable extractor. Future work should reuse the maintained command. |
| 7.1 plaintext packet capture boundary | **COMPLETED · VERIFIED** | `analyses/unlock-trans-point/RUNTIME_CAPTURE_RECOVERY_2026-10-02.md` preserves exact-build framing/XOR recovery; maintained collector is `tools/runtime/capture_game_packets_71.*`; generic correlation is `genshinre correlate-capture` | Reuse the current 7.1 observation layer instead of rebuilding packet framing or restoring retired framing Actions. |
| Ordinary waypoint live unlock path | **COMPLETED · VERIFIED · INTEGRATED** | `analyses/unlock-trans-point/README.md` records that corrected `ScenePointUnlockNotify` is sufficient for physical activation, immediate map usability and teleport; exact `UnlockTransPointRsp` is not required by the tested path | Integrated in AstaPS by `21ce7b5c` (`fix(scene): trust 7.1 waypoint unlock notify`) and `e28d5c8e` (`fix(scene): encode 7.1 waypoint unlock fields correctly`). |

## Follow-up work with a bounded next step

| Work | State | What is already established | Remaining bounded work |
| --- | --- | --- | --- |
| Normal Statue of the Seven activation / quest 303 | **FOLLOW_UP** | `analyses/statue-unlock/` establishes the data-driven `NpcTalkReq(303xx) -> COMPLETE_TALK -> quest finishExec -> unlock point/area` model, and same-version 7.1 quest data extends the chain through `30362`. The earlier apparent `30317` cutoff was a resource-pack omission. | One narrow locked-statue runtime trace is still requested, then the server/resource implementation can be integrated. Do not restart protocol discovery unless that validation contradicts the established model. |

## Active investigations

| Work | State | Current boundary |
| --- | --- | --- |
| CmdId 186 semantic identity | **ACTIVE** | Exact Global 7.1 identity is closed to `186 / NLOMEGMJDGJ`; field 14 is packed repeated `uint32`; the Global sender path through `JKFCCMCAMGA` is closed. `GetActivityInfoReq` is the maintained **HIGH_CONFIDENCE** semantic candidate from matching current-version CN evidence. Promotion still waits for an exact-Global semantic edge. See `analyses/cmdid-186/` and issue #1. |
| Barbara C6 revive authoritative wire invoke | **ACTIVE** | Ability trigger/config and the 900-second cooldown are config-confirmed; the exact authoritative revive wire invoke remains unresolved. See `analyses/barbara-c6-revive/`. |

## Paused investigations

| Work | State | Resume condition |
| --- | --- | --- |
| Exact `UnlockTransPointRsp` identity | **PAUSED** | `UnlockTransPointReq` is confirmed as CmdId `9369`; response recovery remains tied between `36641 / NCBEHBOCBJJ` and `20290 / MAFFAFNMEBM`. Resume only if gameplay develops a response dependency, a genuine official-current capture becomes available, or new static evidence creates a unique semantic binding. |

## Maintenance rules for this index

Update this page when a research task materially changes state. In particular:

1. add a **COMPLETED** entry when a result becomes durable enough that another researcher should reuse it instead of repeating the investigation;
2. add **VERIFIED** only when the source investigation says its required validation has actually happened;
3. add **INTEGRATED** only after the corresponding AstaPS/resource change exists, and record the commit or stable path;
4. move unresolved work to **PAUSED** when the research record explicitly says further effort has no current value;
5. record **SUPERSEDED** conclusions when the reason matters for avoiding repeated dead ends, but keep retired code/workflows/probes out of the maintained tree once their useful evidence is preserved;
6. keep detailed evidence, candidate lists and research chronology in `Genshin-Reverse`; this page stays a compact index.

When reviewing other people's work, prioritize completed and closed items first. Missing completion metadata is itself a maintenance problem: if the evidence clearly says `published`, `closed`, `validated`, or otherwise meets its stated gate, make that state discoverable here.
