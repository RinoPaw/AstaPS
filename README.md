# AstaPS

English · [繁體中文](README_zh-TW.md)

A private server for Genshin Impact **7.1.0**, built on Grasscutter.

> This is a research and preservation project. It is not affiliated with, endorsed by, or connected to HoYoverse / miHoYo in any way, and it is not for commercial use.

If you can fix a bug, please help me.

## What it is

- **Up-to-date content.** Monster and gadget spawn data current with 7.1.0, Spiral Abyss rotations, domains, the artifact shop, battle pass, and the rest of the live-service surface.
- **Built to survive a bad day.** Database writes are split across four bounded pools that apply backpressure instead of dropping a player's progress; one world throwing during a tick no longer stops all the others.
- **Visible when it is unwell.** A status readout logs CPU, memory, GC and every thread pool's queue depth on an interval, and `/api/status` serves the same figures over HTTP.
- **English throughout.** Source, comments, commit messages and command output.

## Requirements

| | |
|---|---|
| Java | 21 to build. The sources target 17, but virtual threads and other 21 APIs compile against the JDK's own classes. |
| MongoDB | Community Server. Must be running before the server starts. |
| Game client | Genshin Impact 7.1.0. The official client checks the region's signature, so it needs a client patch to connect to a private server, such as [hk4e-patch-universal](https://github.com/capyb2222/animegamepatch). AstaPS does not ship one. |
| Resources | A 7.1.0 resource pack, extracted to `resources/` in the server directory. If you don't have Resources, you can download it [here](https://github.com/MeChen618/AstaPS-Resource). |

## Building

```
./gradlew jar -PskipHandbook=1
```

`grasscutter.jar` lands in the project root. Drop `-PskipHandbook=1` to build the in-game handbook as well; that step needs NodeJS and fails without it.

On Windows use `.\gradlew.bat`, or run `gradlew-jar.bat`.

## Running

1. Start MongoDB.
2. Put a 7.1.0 resource pack in `resources/`.
3. Run the jar once. It writes a `config.json` and stops if anything essential is missing.
4. Start it again. The dispatch server listens on `8088` and the game server on `22101` by default.
5. Point the client at the dispatch server. A proxy such as Fiddler or mitmproxy will do it, as will a client patch.

### Accounts

There is no registration page. An account is created either way:

- **From the console.** `account create <username> [uid] [password]`
- **At sign-in.** Signing in with a name nobody holds registers it. With `account.useIntegrationPassword` on, put `name&&password` in the username box and leave the password box alone — useful when proxying a bunch of clients at once and you don't want to set up a user for each.

Passwords are BCrypt-hashed. The console needs `server.game.enableConsole` set to `true`.

## Commands

`help` lists them. A few worth knowing:

| | |
|---|---|
| `give` | Avatars, weapons, artifacts and materials. Level 100 by default. |
| `account` | Create and delete accounts, reset passwords. |
| `banip` / `unbanip` | Ban an address. Banning one also bans the account arriving from it. |
| `sysmail` | Send system mail to every player. |

## TPS shooting (7.1)

Snezhnaya's third-person shooter mode works: guns and grenades worn next to an avatar's normal weapon, aimed and fired in TPS domains. The commands need the `player.tps` and `player.enterdungeon` permissions.

**1. Get the weapons**

```
/tps give          all eight TPS weapons (224001–224008), or one: /tps give 224001
/tps accessory     unlock every accessory on the weapons you own
```

**2. Play a TPS domain**

```
/dungeon 10955                       the shooting range
/dungeon 10953, 10960 to 10964       the Emerged Grey Field stages
```

Inside, your team is swapped for the TPS Traveler (matching your Traveler, level 20), wearing your TPS loadout, or 224001 the first time. Weapons you switch to there are kept as your loadout. Leaving the domain restores your team.

**3. Outside a domain**

Any avatar can wear TPS weapons, which is handy for trying them out:

```
/tps wear 224001 224004    the field avatar wears a rifle and a grenade (at most 2 guns and 1 grenade)
/tps refill                refill all ammunition
```

Ammunition handling is still partly experimental. See [docs/tps/README.md](docs/tps/README.md) for what the server does, the `/tps ammo` switches, and what is not settled yet.

## Licence

Released under the **GNU General Public License v3.0**. See [`LICENSE`](LICENSE).

`LICENSE-ClassGraph.txt` is not this project's licence. ClassGraph is an MIT-licensed dependency whose compiled classes ship inside `grasscutter.jar`, and MIT asks only that its notice travels with them.

## Credits

This server is based on **Grasscutter**. Reference projects: **LunaGC**, **HunkyMeow**.

The client patch linked under Requirements, [hk4e-patch-universal](https://github.com/capyb2222/animegamepatch), is maintained by **capyb2222**, based on [xeondev](https://git.xeondev.com/reversedrooms/hk4e-patch)'s original hk4e-patch and [oureveryday](https://github.com/oureveryday/)'s original hk4e-patch-universal. It is a separate project under its own GPL-3.0 licence.

The import commit at the root of this repository credits by name the authors whose work it carries.

## Proto Sources

Protocol definitions sourced from [genshin-protocol](https://gitlab.com/kitkat-multiverse/genshin-protocol).
