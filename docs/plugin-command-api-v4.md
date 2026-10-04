# Plugin command API v4

AstaPS plugin API v4 makes the command API picocli-only. This is an intentional breaking change: v3 command plugins must be updated and recompiled, and there is no legacy command adapter.

## What changed

- `CommandHandler` now describes a picocli command tree through `createCommandLine(Player sender, Player targetPlayer)`.
- The old `execute(Player, Player, List<String>)` command contract is removed.
- Command output helpers moved to `CommandOutput`.
- `ServerHelper.registerCommand(...)` accepts the v4 `CommandHandler` directly.
- Parsing, generated usage, JLine completion and parse/execution error handling all go through the same picocli path.
- Picocli argument-file expansion is disabled because AstaPS reserves `@...` for player/UID targeting.

Set `"api": 4` in `plugin.json` and rebuild the plugin against the v4 server API.

## Minimal migration

v3-style command:

```java
@Command(label = "foo")
public final class FooCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        CommandHandler.sendMessage(sender, "hello");
    }
}
```

v4 command:

```java
@Command(label = "foo")
public final class FooCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine((Runnable) () -> CommandOutput.sendMessage(sender, "hello"));
    }
}
```

Registration remains straightforward:

```java
ServerHelper.getInstance().registerCommand(new FooCommand());
```

For real commands, prefer typed picocli `@Parameters`, `@Option`, subcommands and converters instead of parsing a `List<String>` manually.

## Target selectors

AstaPS may consume a bare `@UID` argument as the command target before picocli parses command-local arguments. Commands that use `@UID` as their own argument must opt out:

```java
@Command(label = "account", inlineTarget = false)
```

With `inlineTarget = false`, command-local values such as `account clone source target @123` remain in the picocli argument list.

The standalone target command accepts both `target 123` and `target @123` for compatibility.
