# Public issue review for 7.2

Reviewed on 2026-11-20 against `04a94ef8` (main, including #252 and #254).
This document distinguishes code changes, existing fixes, and remaining reproduction or data gaps.
Offline tests do not establish that every affected client flow works in game.

## Changes in this branch

- Friendship reward requests with a GUID and no level now collect all eligible, unclaimed
  rewards. The response includes the reward ID list consumed by the 7.2 client.
  Inventory refusal leaves all involved levels claimable; retries do not grant them twice.
- Fuel pickups support `ITEM_USE_ADD_PHLOGISTON` (client enum value 60). Adsorbate items
  apply the effect on collection; ordinary consumables remain usable from the bag.
  Changes to the team fuel pool update `SGV_PlayerTeam_Phlogiston`. Invalid floats are rejected.
- Server fall damage respects `Actor_FallingDamageRatio` on currently attached modifiers.
  Removing the modifier restores normal fall damage. A character's ability templates alone
  cannot grant permanent immunity.
- Quest completion/failure conditions now check live global variables, inventory counts,
  presence in a specific scene, the declared scene range, and parent quest completion.
  Inventory changes, global-variable changes, scene readiness, parent completion, and a
  one-second range sample provide the corresponding events. Existing state conditions are
  also checked when a quest starts; this does not enable automatic quest acceptance.
- Batch open-state actions validate all ID/value pairs before changing any state.
  Daily-task notification actions send the current commission state without granting progress.
- Map unlocks and fog-disabled responses use resource-defined areas instead of IDs 1–999/1000.
  Map unlocks refresh statue interactions, preserve quest point locks by default, and clear
  those locks only with `--force`. The command does not finish activation quests or grant
  activation rewards as a side effect.
- Gradle dependency declarations use single-string coordinates and explicit property assignment.
  Log encoders explicitly use UTF-8, matching the JLine appender's decoder.

## Client and resource evidence

The supplied 7.2 Android sample provides these checks:

- `AvatarFetterLevelRewardReq`, CmdID 22668: GUID field 4, level field 1.
  Native sender `0xaa58564` allocates the request and writes the GUID at object offset `0x18`
  before sending, without writing the level at `0x20`. Zero is therefore a normal request
  from this button, rather than a request for a configured level-zero reward.
  The response callback at `0xaa5d2a0` traverses the reward ID list at offset `0x18`.
- Quest content enum values: global-variable comparisons 160/161/162/172,
  inventory comparisons 168/169/170, scene presence 177, parent state 186.
  Batch open-state execution is 123. The existing V2/time/batch-variable enum entries
  were also aligned to the client values 118/81/112.
- Quest 7710244 has a range center at
  `[3_14622:8769.230,87.200,8503.074,0.000,299.816,0.000]` in its condition's `param_str`.
  Its guide points elsewhere. Range checks use the condition point, including its scene,
  rather than substituting the navigation point.
- Material 2030 and related fuel particles declare `ITEM_USE_ADD_PHLOGISTON` with amount 20.
  The restoration amount comes from the item data.
- Vesna's resources declare `Actor_FallingDamageRatio` on the falling-anthem and skill
  protection modifiers. Both constant protection and dynamic ability-special values are supported.

## Issue disposition

| Issue | Result of this review | What remains before closure |
| --- | --- | --- |
| [#112](https://github.com/MeChen618/AstaPS/issues/112) | Fuel pickup actions and team-pool synchronization implemented. | Statue/waypoint proximity restoration, fuel-area mixins, and skill-specific fuel consumption still need their own authoritative behavior and in-game checks. This is not a complete Natlan fuel implementation. |
| [#174](https://github.com/MeChen618/AstaPS/issues/174) | Main already uses authored blossom Lua camps, adventure-rank gates, and receipt-based reward/currency settlement. Existing blossom tests cover these code paths. | Test an actual affected camp with resin usage enabled, including 1/2/3-roll costs and enemy-created gadget cleanup. The old 7.1 CmdID report must not be substituted into 7.2. No blanket closure. |
| [#195](https://github.com/MeChen618/AstaPS/issues/195) | Closed as not planned, following the repository owner's decision to keep GitHub. | None. |
| [#203](https://github.com/MeChen618/AstaPS/issues/203) | Fixed GUID-only batch claims and the response reward list. Tests cover level-one eligibility, both earned rewards, inventory rejection, and retry. | Confirm the profile button/red marker on the 7.2 client after deploying this branch. |
| [#218](https://github.com/MeChen618/AstaPS/issues/218) | Banner/activity configuration questions are separate from Odette's sustained Cryo behavior. Main already has client-owned skill gadget handling. | Need the affected gadget/attack and ability invocation sequence for sustained Cryo. Do not replace it with an invented periodic Cryo tick. Missing abyss scene/group scripts also require the matching resources. |
| [#223](https://github.com/MeChen618/AstaPS/issues/223) | Confirmed the reported quests and Travel Log reproduction; this cannot be dismissed as only a missing quest-finish event. | The server lacks the parent-quest video-key request implementation and matching video-key data. CmdID 9768's response has parent ID field 5 and two strings at fields 3/4, passed together by the native callback at `0xb2c5754`. Quest encryption keys are not interchangeable with these strings. Need matching key/asset evidence and the client video failure log before implementing a working response. |
| [#232](https://github.com/MeChen618/AstaPS/issues/232) | Fall damage now uses active modifier protection, including partial reduction. A removal test verifies immunity is not permanent. | Confirm the skill's attach/remove sequence in game; unsupported modifier invocation paths are a separate issue. |
| [#242](https://github.com/MeChen618/AstaPS/issues/242) | Main already contains legacy `give` normalization, bulk grant admission, HP-ratio preservation, separate quest trial avatars, and gacha title paths. This branch fixes fabricated map area IDs, statue interaction refresh, force-lock handling, and encoder charset consistency. | Recheck each reported subcase on current main plus this branch. Persistent world-boundary behavior, the exact ore-producing animal ID/drop table, handbook text parsing, and opening-movie ordering are not proven resolved by these changes. Existing saved fake area IDs are not silently deleted. |
| [#245](https://github.com/MeChen618/AstaPS/issues/245) | Added live-state conditions and their event sources, batch open states, and commission notifications. Reviewed #251's proposal; range checks use `param_str` and enum values come from the client. | Team/mirror-team unlock actions need actual state and notification behavior. They are deliberately not counted as implemented through log-only success handlers. #251 remains a separate open PR. |
| [#247](https://github.com/MeChen618/AstaPS/issues/247) | The attached HTML contains 16 deprecation warnings and no compiler error. Updated the reported Gradle DSL and documented how to collect the real failure. | Need the original failed task and exception if a Windows build still fails. Removing warnings alone does not prove that unidentified failure fixed. |
| [#250](https://github.com/MeChen618/AstaPS/issues/250) | Main already preserves command-added quest visibility while general questing is disabled, sends parent/child updates, and includes the recent resource patch. New state checks address additional stuck objectives. | The report lacks an exact subquest ID. Reproduce with the current server and matching QuestExcel/BinOutput, recording the added subquest, state, show/guide data, and outbound quest notifications. No default auto-accept or fake completion added. |
| [#253](https://github.com/MeChen618/AstaPS/issues/253) | Added a repeatable handler-registration census and implemented the supported conditions/actions above. | Main-coop save points, scene level tags, scenery/activity/GCG systems, mirror teams, map-layer state, and the other reported action types remain genuine gaps. Registration counts are not end-to-end coverage. |

## Reproducible quest census

Run against the exact resource table used by the server:

```sh
python tools/quest-coverage.py resources/ExcelBinOutput/QuestExcelConfigData.json
```

For the reviewed table (34,545 rows; SHA-256
`3fbc7cce3523fb3fd9dab2348f7c9962b4a3c0a96c15f3c09ee74e1487f462c8`):

| Recorded entries | Main before this branch | This branch | Still without a registered handler |
| --- | ---: | ---: | ---: |
| Finish/fail conditions, total 46,006 | 43,209 | 44,852 | 1,154 across 43 types |
| Begin/finish/fail actions, total 26,403 | 22,982 | 23,457 | 2,946 across 68 types |

This adds registered handling for 1,643 condition occurrences and 475 action occurrences.
The tool checks registration, including aliases. A handler may still lack a runtime event,
correct persistence, or client behavior; none of those are implied by this table.
The numbers are specific to this table and must not be compared directly with a different
7.1/7.2 resource snapshot's totals.

## Validation

```sh
./gradlew test jar -PexcludeTags=integration -PskipHandbook=1 --warning-mode all
```

Tests cover batch reward eligibility/refusal/retry, modifier removal and partial fall reduction,
fuel restoration and use-on-gain classification, state-condition registration and live values,
range-point parsing, atomic batch-parameter validation, commission notification without progress,
and map unlock/visibility with real areas and quest locks. Integration-tagged tests require a
running server environment and are excluded from the offline check.

Client flows still require testing on the matching 7.2 build. Keep unresolved issues open;
this review does not claim that all public issues are fixed.
