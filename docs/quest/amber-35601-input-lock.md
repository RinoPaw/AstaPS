## 2026-10-10 tutorial/quest-visibility hypothesis

A player confirmed on 2026-10-10 that the client is still input-locked after the fully CI-tested request-sequence fix `517c8327` (UID `70630`). That test sent `NpcTalkRsp` with echoed client sequence 26765; both QuestDestroyNpcReqs were distinct (NPC 1002 seq 26795, NPC 1005 seq 26833). Client opcode 27447 had no explicit bool field 7 (default false), but that alone does not prove its exact cleanup semantics. **The request-sequence candidate did not fix the lock.**

The user's description identifies the *native quest-tracking tutorial* immediately after talking to Amber in Mondstadt: it dims the rest of the screen and forces the quest-menu button. [Independent original-game walkthrough](https://www.powerpyx.com/genshin-impact-city-of-freedom-walkthrough/) also documents this exact transition. This is a more specific hypothesis than a network pause or NPC cleanup timeout.

AstaPS has a potential conflict when `questing.enabled=false` (the default). `PlayerProgressManager.onPlayerLogin` fills almost every `OpenState` with 1, including client tutorial control states 16, 17 and 60. Separately, `PacketQuestListNotify` and `PacketQuestListUpdateNotify` intentionally hide all *unfinished* quests while questing is off. The server can still progress quest 35601 → 35602/35603, leaving the client tutorial without a selectable task, depending on the actual config and client behavior. The config was **not logged in the failing capture**, so this is not yet confirmed as the user's runtime state.

This isolated compatibility branch makes only the two immediate follow-up steps 35602 (hidden) and 35603 (visible quest to track) visible to the client when `questing.enabled=false`, **after** 35601 finishes; other unfinished quests remain filtered, and no `OpenState` or saved quest states are changed. Both initial quest list and incremental updates use the same visibility rule. The trace additionally records `questingActive`, relevant `OpenState` values and outgoing quest list counts. If questing was already active, this branch will not change quest visibility and only provides useful instrumentation. This is a **testable hypothesis, not a demonstrated input-lock fix**. Do not alter default global unlocking, bypass the tutorial, or claim resolved until runtime evidence is available.

## 2026-10-10 candidate: request-correlated talk response headers

The server already echoes a client request sequence in `GetPlayerSocialDetailRsp` because that client callback was observed to reject an unrelated sequence. The 7.1 Amber handlers did not: `NpcTalkRsp` had no header and `QuestDestroyNpcRsp` used `super(opcode,true)`, generating a **new server sequence**. In `fix/amber-talk-rpc-sequence-71`, both handlers now extract `PacketHead.clientSequenceId` from each request and place it into the response header. The response bodies and opcodes are unchanged. QuestDestroyNpcReq logs its `npcId`, `parentQuestId` and request sequence so the two requests observed during the 45-second lock can be distinguished without assuming retransmission.

Exact Global 7.1 client evidence narrows the callback: `NpcTalkRsp(3514)` is `IMLLBMOMMEH`; native reader `NLGBJEEJDLG @ 0x11E83970` places protobuf **field 7** in object offset `+0x24`. Handler `HBBDJPCGCAH.JCIMEKEEFOH @ 0xA5E18C0` loads `+0x24` and tail-calls `InteractionManager.FinishCurrTalk(uint32) @ 0xFE57430`. AstaPS already puts talk `35601` into field 7; do **not** repurpose field 5 (`retcode`). The handler `HBBDJPCGCAH.HGDBBLBCPMH @ 0xA5E1D30` for `QuestDestroyNpcRsp(3992)` appears to return immediately when the parsed response is non-null, so it is not presently supported as the direct input-unlock callback.

The native handler identities and `FinishCurrTalk` call are confirmed. Whether this handler **requires** a matching `PacketHead.clientSequenceId` to be dispatched is still **unresolved**; some protocol handlers may be dispatched only by command ID. This is therefore a bounded, CI-tested **candidate**, not a proven fix. Retain the previous 45-second stuck/reconnect logs for comparison; do not claim the client is repaired until tested.

# Mondstadt Amber dialogue input lock — 7.1 Global

Status: **unresolved, instrumented**. Source: AstaPS issue [#40](https://github.com/MeChen618/AstaPS/issues/40). Native-client protocol investigation: [Genshin-Reverse #23](https://github.com/RinoPaw/Genshin-Reverse/issues/23).

## Reproduction and observed event order

After entering Mondstadt, the first interaction with Amber (talk `35601`) leaves the 7.1 Global Windows client unable to continue normally. Force-closing the client and logging in again restores normal gameplay. This is an observation about the **client session**; do not replace it with a claim that quest `35601` failed to complete.

From the 2026-10-09 test log, at 20:01:23 local time:

1. `NpcTalkReq` with `talk=35601`, `npcEntity=0`, `entity=116393191`.
2. `NpcTalkRsp sent`, `talk=35601`.
3. `Quest finish 35601`, `QUEST_COND_STATE_EQUAL id=35601 state=3`.
4. `Quest accept 35603 ... accepted=true`, `Quest start 35603`.

No `35602` lifecycle lines were emitted by the tested build: **that build did not instrument 35602**, so absence from its log proves nothing about whether 35602 was active.

Repeated `QUEST_COND_LUA_NOTIFY` exceptions earlier in the same session came from indexing `params[0]` on an empty int array. `diagnostics/amber-input-lock-35601` includes a zero-param fix and unit tests. It is a separate confirmed defect; the causal link to Amber's input lock is unproven.

## Resource-level chain

Current resource paths:

- `BinOutput/Quest/356.json`: talk `35601` is auto-start, `35604` is manual; `35602` is a hidden `QUEST_CONTENT_TRIGGER_FIRE(1126)` step; `35603` is `QUEST_CONTENT_TRIGGER_FIRE(1102)`.
- `ExcelBinOutput/TriggerExcelConfigData.json`: trigger `1126` = scene 3 / group `133004901` / `ENTER_REGION_289`; trigger `1102` = scene 3 / group `133004901` / `ENTER_REGION_244`.
- `Scripts/Scene/3/scene3_group133004901.lua`: region `289` is a sphere centered at `(2267.016, 209.656, -856.152)`, radius 60, condition is avatar + subquest `35602` UNFINISHED; region `244` is centered at `(2309.591, 249.990, -775.226)`, radius 5, condition is avatar + subquest `35603` UNFINISHED.
- `QuestData.applyFrom()` applies a reviewed gate `35601 FINISHED -> 35603` to avoid flattening's dependency on hidden `35602`. This only establishes server acceptance conditions, not that the client unlocks controls.

Historical Grasscutter documentation independently records **possible softlock while tips are displayed** during quest `35602`:
[Prologue Act I table](https://github-wiki-see.page/m/Anime-Game-Servers/Grasscutter-Quests/wiki/The-Outlander-Who-Caught-the-Wind-%28Prologue-Act-1%29).
That report is from a different, older client; treat it as a competing hypothesis, not proof for 7.1.

## Known protocol gap

- AstaPS `PlayerSetPauseReq=5963`.
- `PlayerSetPauseRsp=0` (7.1 CmdId unresolved; historic 7.0 value **must not** be reused).
- `HandlerPlayerSetPauseReq` constructs a success response, but `GameSession.send()` returns without sending when `opcode <= 0`. Thus **no pause acknowledgement reaches the client**. It is unknown whether the client needs that acknowledgement for this particular input lock.
- AstaPS `NpcTalkRsp=3514` and `CutSceneEndNotify=472` are server mappings; their role in this particular handoff is still to be confirmed against the exact client.

## 2026-10-10 completed stuck-vs-reconnect comparison (UID 70629)

The extended console transcript captures **both 45-second summaries on the same running server**.

| Observation | Stuck after dialogue, starting 19:43:41 | Reconnect, starting 20:09:32 |
| --- | --- | --- |
| Quest 35601 | Finished immediately at 19:43:41 | Persisted as FINISHED |
| Quest 35602 / 35603 | Both UNFINISHED at +45s | Both UNFINISHED at +45s |
| Server worldPaused / playerPaused | false / false | false / false |
| Server timeLocked | true (already true before talk) | true (unchanged) |
| SceneTimeNotify | is_paused=false throughout | is_paused=false throughout |
| PlayerTimeNotify | not present in this already-loaded trace | is_paused=false at scene-enter completion |
| PlayerSetPauseReq / response | no pause request in 45s | one is_paused=false at +1648ms; PlayerSetPauseRsp dropped because CmdId=0 |
| Interaction marker CmdId 27447 | one C2S at +1050ms; payload unlogged on this version | not observed during 45s re-enter |
| QuestDestroyNpcReq / Rsp | two of each in 45s (timing of second unknown) | zero |
| Other | Ping and ongoing UnionCmdNotify continue | complete scene reinitialization, PostEnterSceneRsp, ability init |

**Causal interpretation:** Client control recovery after reconnection despite a **still-missing** PlayerSetPauseRsp weighs strongly against missing ACK being *sufficient to explain* the stuck dialogue state. Neither `timeLocked=true` nor ordinary server pause distinguishes the stuck vs recovered state. New scene-enter notifications plus client-local InteractionManager reset are confounded; cannot separate the two without a narrower test. The stuck trace lacks a C2S false transition of 27447. The absence of such a transition is evidence for an interaction-lifecycle gap, but the precise sender semantics and expected close conditions are still unresolved.

The uploaded log was captured on upstream-sync commit `aa0e951`, **before** the subsequent diagnostic change that decodes 27447 protobuf bool field 7. Do not claim this uploaded sample gives the bool value. The new diagnostic CI (`b8adb5b`) passed independently; it requires a new process to take effect and does not retroactively decode the old log. Do not require another gameplay reproduction before extracting additional native-client evidence.

## 2026-10-10 stuck dialogue capture: interaction marker CmdId 27447

The provided server console transcript ends about **2.7 seconds after** NpcTalkReq(35601), so it does not contain the expected 45-second summary or forced-reconnect comparison.

At 19:43:41 the server sends NpcTalkRsp(3514), finishes quest 35601 and starts both 35602/35603. At 19:43:42 the client sends unknown CmdId **27447**, followed by QuestDestroyNpcReq(23280); the server responds with QuestDestroyNpcRsp(3992). At 19:43:43 SceneTimeNotify(1307) explicitly says `is_paused=false`. World `timeLocked=true` predates the dialogue by more than 20 minutes and is not, on its own, evidence for the new client input lock.

Pinned Global 7.1 native analysis resolves 27447 to `NDAJDBCBAAE` (typeDefinition 75026) with **one bool protobuf field 7**; native serializer emits tag `0x38`. `LLCGIEDMIIG.GKJKIBOALJO(bool)` at RVA `0xC22D780` constructs/sends the request. Direct callers originate in `InteractionManager`: `OnCreateTalkFinish` calls with **true**; `ClearOnDisconnect`, `ResumeGameTime`, `ClearAll` and `ClearAfterKeyListFinish` call with **false**. Source runs: [structural lookup](https://github.com/RinoPaw/Genshin-Reverse/actions/runs/38049568765), [native serializer/sender](https://github.com/RinoPaw/Genshin-Reverse/actions/runs/38049670386), [native direct call sites](https://github.com/RinoPaw/Genshin-Reverse/actions/runs/38049772524).

This is a strong **interaction-lifecycle lead**, but the server transcript did **not** capture the 27447 payload. Do not claim the observed packet value was true, infer a response is required, or force-send a guessed unlock packet.

The current diagnostic branch decodes and logs only the bool at field 7 (or `null` when absent) for inbound CmdId 27447 during its Amber trace. The wire probe has focused unit coverage. A complete 45-second trace and reconnect comparison still form the causal evidence gate.

## Exact 7.1 protocol cross-check (Genshin-Reverse #23)

The [pinned 7.1 Global native analysis](https://github.com/RinoPaw/Genshin-Reverse/blob/main/versions/7.1.0-global/windows-x64/analyses/amber-pause-ack/README.md) resolves the request construction path: CmdId `5963`, obfuscated type `GLIHKBGFALC`, generated bool tag `0x58` (**field 11**) and the `Miscs.PauseLevelTime(bool, ...Talk)` caller. A hosted inspection of this repo's `protocol/7.1/protocol.desc` confirmed `PlayerSetPauseReq.is_paused = 11`. Thus the request field-number mapping is **consistent** with the current client.

That same *server descriptor* retains `PlayerSetPauseRsp.retcode = 4`, but the native 7.1 response identity and field number are **not confirmed**. If it is still an inbound one-int32 response at field 4, parser evidence conditionally narrows it to CmdIds `22120` or `24380`. The pinned native client handlers for both return immediately on success; neither directly changes the receiver's `+0x1D0` pause field. A pending-RPC callback remains possible. **Do not patch either candidate opcode without semantic verification.**

The pinned client also demonstrates that incoming CmdIds `1307` and `20114` update receiver pause state and can cause a new `5963` request. The diagnostics branch therefore also traces the outgoing `SceneTimeNotify` / `PlayerTimeNotify` paused fields, logging transitions rather than all repeated notifications.

## Diagnostic branch instrumentation

Branch: `diagnostics/amber-input-lock-35601` (based on the post-PR71 handoff fixes). Logs **packet names and limited decoded state only, never whole decrypted frames or auth payloads**.

- `[AmberWire]`: 45-second packet-name/direction trace following talk `35601`, first occurrence and packet counts; every pause request (value + world/player pause flags); cutscene finish and end-ack IDs; explicit warning if the pause response is dropped.
- On a **new session**, `EnterSceneDoneReq` automatically arms a fresh 45-second `[AmberWire]` trace before sending `PlayerTimeNotify` when persisted quest `35601` is FINISHED and `35603` remains UNFINISHED. This supplies the missing reconnect comparison; no instrumentation is activated for other quest states.
- `[AmberState]`: world/player/time-lock flags at quest completion, `35602` start position, candidate acceptance and region triggers `1126` / `1102`.
- `[Prologue]`: 35601, 35602, 35603 quest start/finish and talk response ordering.

A normal test should compare this trace **at the moment control becomes stuck** with the state observed on reconnect. Specifically distinguish: paused world that stays paused; missing cutscene acknowledgement; hidden tip/35602 step; and a client-local input state even after normal server transitions.

## Boundaries and release gate

No forced `Quest finish`, artificial timer, unconditional `world.setPaused(false)`, guessed CmdId, or client-state spoofing before the missing transition is established. Do not describe this as fixed until the **continuous 7.1 client path** is verified. Before asking for a new client run or submitting upstream, confirm successful `jar -PskipHandbook=1` and non-integration JUnit tests, and report actual GitHub Actions status if available. The jar's canonical name is `grasscutter-7.1.0.jar`.
