# CLI migration (Picocli)

This branch ports the command parser and all converted built-in commands from
`play/rino` onto the current upstream `main`, without bringing over the
unrelated quest, protocol, resource, combat or server changes.

## Command parsing

- Built-in commands declare their grammar using Picocli arguments, options and subcommands.
- The console uses JLine parsing and Picocli completion.
- `@UID` remains an AstaPS target selector, not a Picocli argument file.
- Commands can opt out of implicit target parsing when `@UID` is positional data.
- The command registry rejects duplicate names or aliases.
- Console feedback continues to reach the web-console output capture.

## Account operations

Console-only commands include:

```text
account create <source-independent-name> <password> [@UID]
account clone <source-account> <new-account> [@UID]
account delete <account>
account resetpass <account> <new-password>
```

The clone operation needs an offline source player and a new account name.
It copies player-owned gameplay collections and uses the database writer barrier
to avoid copying unfinished saves; friendships and shared beatmaps are excluded.
The clone inherits the source account password and permissions.

## Review and validation

- Compare the complete command registry and aliases against upstream before merge.
- Compile and run unit tests, including command parser/registry and database writer tests.
- Ensure game clients are not asked to test before CI has passed.
- Open the upstream PR only after successful CI, with any compatibility exceptions documented.
