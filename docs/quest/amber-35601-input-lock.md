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

## Diagnostic branch instrumentation

Branch: `diagnostics/amber-input-lock-35601` (based on the post-PR71 handoff fixes). Logs **packet names and limited decoded state only, never whole decrypted frames or auth payloads**.

- `[AmberWire]`: 45-second packet-name/direction trace following talk `35601`, first occurrence and packet counts; every pause request (value + world/player pause flags); cutscene finish and end-ack IDs; explicit warning if the pause response is dropped.
- `[AmberState]`: world/player/time-lock flags at quest completion, `35602` start position, candidate acceptance and region triggers `1126` / `1102`.
- `[Prologue]`: 35601, 35602, 35603 quest start/finish and talk response ordering.

A normal test should compare this trace **at the moment control becomes stuck** with the state observed on reconnect. Specifically distinguish: paused world that stays paused; missing cutscene acknowledgement; hidden tip/35602 step; and a client-local input state even after normal server transitions.

## Boundaries and release gate

No forced `Quest finish`, artificial timer, unconditional `world.setPaused(false)`, guessed CmdId, or client-state spoofing before the missing transition is established. Do not describe this as fixed until the **continuous 7.1 client path** is verified. Before asking for a new client run or submitting upstream, confirm successful `jar -PskipHandbook=1` and non-integration JUnit tests, and report actual GitHub Actions status if available. The jar's canonical name is `grasscutter-7.1.0.jar`.
