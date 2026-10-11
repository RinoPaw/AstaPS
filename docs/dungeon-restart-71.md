# Dungeon restart (7.1)

## Verified client flow

7.1 client CmdId `9381` is received as `DungeonRestartReq` following a
successful domain reward claim. Validated on the Fontaine weapon dungeon 4471,
scene 40771, 2026-10-11:

- 19:04:51 first successful reward claim (5 items)
- 19:04:54 `DungeonRestartReq`, new scene `restored=1/1`
- 19:04:57 compatible challenge starter gadget spawned again
- 19:05:18 second successful reward claim (5 items)

The validated flow used the resource branch
`fix/fontaine-weapon-scenes-isolated-71`.

## Unified server behavior

The packet handler routes through `DungeonRestartGuard`, based on live dungeon
state rather than a scene-ID or dungeon-ID allowlist:

- Active dungeon scene must match the current `DungeonData.sceneId`.
- The challenge must have finished successfully.
- The requester must have a successful reward claim recorded in the **current**
  `DungeonManager`. An unfinished or unpaid challenge cannot restart.
- Only an empty 7.1 retry request is accepted.
- Multiplayer restarts are rejected until a consent/coordination handshake exists.
- Tower/Abyss restarts use their separate flow and are not handled here.

`DungeonSystem.restartDungeon` creates a new scene and new `DungeonManager`.
This creates a new claim identity and reinitializes the domain startup path.
Where scripts have incomplete data, `MissingDomainFallbackManager` installs
the compatible starter key. Other regions need their own valid scene data; this
server change does not manufacture missing quest, spawn, or gadget scripts.

## Scope

Only the 4471 end-to-end sequence has been verified in a game client. Other
dungeons that use the same claim tracking are enabled by the generic guard and
covered by eligibility unit tests, but still need client compatibility checks.
The opcode-to-message association for 7.1 is supported by this client trace,
not by a fully reconstructed 7.1 command registry.

Crash/relogin restoration is distinct: an orphaned saved dungeon returns the
player to a safe open-world scene; this does not resume a partially completed run.
