# Genshin 7.1 player birth flow validation

This branch implements the fresh-account birth flow validated against the 7.1 client.

Confirmed protocol values:

- `DoSetPlayerBornDataNotify`: CmdId `22899`, empty payload.
- `SetPlayerBornDataReq`: CmdId `26105`.
- `SetPlayerBornDataRsp`: CmdId `4385`; `retcode` is protobuf field `7`. Success is an empty payload.
- `PlayerNicknameNotify`: CmdId `3064`; `nickname` is protobuf field `12`.
- `PlayerEnterSceneNotify`: CmdId `9582`.

Validated questing-enabled order:

1. Receive `SetPlayerBornDataReq`.
2. Persist the selected Traveler, nickname, head image, and initial team.
3. Establish the player's World/Scene context.
4. Run `QuestManager.onPlayerBorn()` so Quest 351 can start before the first scene entry.
5. Send `SetPlayerBornDataRsp` (`4385`).
6. Send `PlayerNicknameNotify` (`3064`).
7. Run ordinary player login, which sends the first `PlayerEnterSceneNotify` (`9582`).

Runtime validation completed successfully with no reconnect, no repeated nickname screen, and normal scene entry.

Configuration behavior:

- `newAccountIntro.enabled=true`, questing enabled: native Traveler selection, born quest lifecycle, then login.
- `newAccountIntro.enabled=true`, questing disabled: native Traveler selection, born response/nickname sync, then login.
- `newAccountIntro.enabled=false`, questing enabled: automatic default Traveler, born quest lifecycle, then login.
- `newAccountIntro.enabled=false`, questing disabled: automatic default Traveler, then login.

The previous timed intro fallback is intentionally not used by the validated 7.1 path. A real first-run cinematic can take well over 15 seconds before `SetPlayerBornDataReq` arrives, so timing out during character creation can persist the wrong Traveler before the player finishes the intro.
