# Server startup options

The executable JAR starts at `ServerLauncher`, which uses Picocli to parse
arguments before initializing Grasscutter. `--help` and `--version` exit
without loading the server configuration, resources or encryption keys.
Invalid arguments print usage and exit with code 2.

| Option | Effect |
| --- | --- |
| `-h`, `--help` | Show help and exit |
| `-V`, `--version` | Print the build version and commit and exit |
| `--game` | Run only Game Server |
| `--dispatch` | Run only Dispatch Server; mutually exclusive with `--game` |
| `--no-console` | Disable the local JLine shell |
| `--test` | Disable the local shell and HTTP encryption (testing only) |
| `--lang=<locale>` | Set the preferred language |
| `--debug`, `--debug=all` | Enable debug logging, optionally including protocol logging |
| `--dump=<kind,language>` | Export commands, avatars, items, scenes, entities, quests or areas and exit |
| `--dump-packet-ids` | Write the packet ID map and exit |

Examples:

```powershell
java -jar grasscutter-7.2.0.jar --version
java -jar grasscutter-7.2.0.jar --dispatch --no-console
java -jar grasscutter-7.2.0.jar --dump=commands,en-US
```

The old single-hyphen startup options are removed. Local console defaults
continue to come from `server.console.enabled` and the run mode.
