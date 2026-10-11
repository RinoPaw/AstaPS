# Amber 35601: 7.1 input lock after dialogue (upstream baseline)

Status: **reproduced, not yet fixed**. This branch was created directly from
`MeChen618/AstaPS:main` at `5a5950eb20aa`, without importing `play/rino` or the
failed first-login refactor. Issue: https://github.com/MeChen618/AstaPS/issues/40.

## Reproducible facts

- In the 7.1 Global Windows client, after the first city conversation with Amber
  (`NpcTalkReq(35601)`), the avatar sometimes remains uncontrollable.
- Prior tests showed server quest `35601` FINISHED and `35602` / `35603`
  UNFINISHED, with continued network traffic and no world pause. Reconnecting
  restores controls. Correcting NpcTalkRsp request-sequence headers did **not**
  resolve the lock.
- In the official flow, a mandatory quest-button tutorial appears and the player
  dismisses it by using the quest UI. Old `AQ356.lua` invokes
  `StartGuide("GuideQuestGuide")` at `35601` completion.
- A 7.1 **CN** guide asset still contains the iOS→Standalone
  `GuideQuestGuide` / `GuideQuestGuidePC` continuation, targeting main
  quest **356**. The exact Global runtime call remains unconfirmed.
- Pinned 7.1 Global reverse engineering confirms `GuideManager.CanStart` can
  defer a guide name, which the notification **711** callback can retry. A
  `false` preflight is not proof of a missing/failed guide.
- Existing evidence does **not** prove a specific OpenState (7/17), response
  opcode, input gate or packet ordering causes the lock.

Reverse evidence: https://github.com/RinoPaw/Genshin-Reverse/tree/research/amber-2178-quest-notify-20261010
and `versions/7.1.0-global/windows-x64/analyses/amber-pause-ack/` there.

## Evidence-only instrumentation in this branch

`AmberQuestHandoffTrace` starts only for `NpcTalkReq(35601)` and lasts up
to 45 seconds. It logs:

- Effective questing flag and stored/default OpenState 1,2,7,8,16,17,24,60.
- Post-KCP-write outgoing `NpcTalkRsp` with the actual talk ID and
  `QuestListUpdateNotify` entries for 35601/35602/35603, including parent
  ID and quest state. The `[AmberGuideOrder]` lines follow the exact format
  accepted by `Genshin-Reverse/tools/runtime/compare_amber_handoff_71.py`.
- Presence and numeric value of field 7 on C2S CmdId 27447 and field 15 on
  C2S CmdId 2178; no guessed semantic names, raw payloads or credentials.
- At window end, the three quest states and world/player pause flags.

The trace never updates quests, OpenStates, player input, cutscenes, wire
responses or packet ordering. Observed outbound order does **not** prove the
client received those packets in the same order.

## OpenState 7/8 and native guide candidates (2026-10-11)

The loaded 7.1 resource `ExcelBinOutput/OpenStateConfigData.json` has
`id=7, defaultState=true, allowClientOpen=true`, while `id=8` has
`allowClientOpen=true` but no `defaultState=true`. The upstream
`OpenStateNames` legacy registry identifies 7 as `QUEST_REMIND` and 8
as `GAME_GUIDE`; it does **not** identify 7 as the quest-menu button.
Neither legacy name proves how the exact 7.1 Global client gates the quest UI.

`PlayerProgressManager.DEFAULT_OPEN_STATES` deliberately excludes default-open
states whose `allowClientOpen` is true. In this 7.1 resource, **41** states
have both flags, including 7. The login `PacketOpenStateUpdateNotify`
serializes stored entries before consulting the default set; an absent stored
entry cannot be treated as an explicit zero. Whether the client itself applies
the resource default when the server omits a key is **not observed**.
Changing the filter wholesale would alter 41 states, not just the Amber quest.
Do **not** forcibly set 7 or 8 or change the global default policy as an
unverified Amber fix.

The server has a defined `PacketShowClientGuideNotify` and a world-script
`ScriptLib.ShowClientGuide`, but `BinOutput/Quest/356.json` gives subquest
35601 only a `QUEST_CONTENT_COMPLETE_TALK` finish condition, with no explicit
`finishExec` invoking a server guide packet. The historical *client-local*
`AQ356.lua` instead invokes `StartGuide`. Existence of the server packet
is **not evidence** it should be injected after NPC talk 35601.

The exact 7.1 native `GuideManager.CanStart` disassembly confirms config
lookup, preflight gates, and deferred-list insertion; it does not establish an
OpenState-7 dependency. The next decisive observation is the matched-client
trace from before 35601 starts: identify the actual `StartGuide` request,
preflight/deferred retry, UI mask and avatar input transitions, alongside the
already-scoped outgoing 35603 quest packet. A missing client callback after a
late attach remains unknown, never a proved absence.

## Next evidence gate

Capture one complete exact-7.1 client run using the existing **read-only**
`capture_amber_guide_locks_71.py` probe, attached **before 35601 begins**
(ideally before 36005 ends), together with the server trace. The launcher
verifies the hash and actual process path and refuses mismatches.

Compare `FinishCurrTalk(35601)` return; `StartGuide`, CanStart, deferred
retry; `GuideQuestGuidePC` dispatch; BlackMask setup; `StopLocalAvatar`,
`EnablePlayerInput` and UI-lock transitions.

Only then choose a behavioral fix. Do not force-finish 35602, forcibly
unlock the avatar, guess a CmdId, or call a diagnostic CI pass a gameplay fix.
