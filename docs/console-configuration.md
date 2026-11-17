# Local console configuration

The local JLine Shell belongs to the server process, not specifically to Game Server.

Add `server.console.enabled` in `config.json` to override the default:

```json
{
  "server": {
    "console": {
      "enabled": true
    }
  }
}
```

If `enabled` is absent, the console defaults to **on** in `HYBRID`
and `GAME_ONLY` and **off** in `DISPATCH_ONLY`. Set it explicitly to
`true` to enable the local console in Dispatch-only mode, or `false`
to disable it in any mode.

The `-noconsole` and `-test` startup options disable the local
console even when the configuration explicitly enables it.

Dispatch-only mode has no in-process Game Server. Its local Shell registers
`help`, `info`, and `stop` (alias: `shutdown`). Game-dependent
commands and player targeting are unavailable.

The password-protected HTTP web console (`server.http.webConsole`) is
configured separately and is unaffected by `server.console.enabled`.
