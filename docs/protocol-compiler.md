# Protocol compiler workflow

AstaPS tracks a moving game protocol. A client update can change packet names, CmdIds, protobuf field layouts, and UnionCmd message ids independently. The goal of `tools/protocol_compiler.py` is to turn protocol migration into a repeatable audit instead of a sequence of hand edits.

## Inputs

Keep raw inputs local. The repository already ignores `/proto` and `/local`.

Recommended layout:

```text
proto/                         raw .proto dump for the target client version
local/nameTranslation.txt      optional obfuscated-name -> semantic-name mapping
local/protocol-report.json     generated audit report
```

`PacketOpcodes.java` remains the checked-in server-side truth:

```text
src/main/java/emu/grasscutter/net/packet/PacketOpcodes.java
```

Do not hand-edit the raw proto dump.

## First pass

Run:

```powershell
uv run python .\tools\protocol_compiler.py doctor
```

or, with Python directly:

```powershell
python .\tools\protocol_compiler.py doctor
```

The tool scans proto files for CmdId annotations, applies `nameTranslation.txt` when present, reads the current `PacketOpcodes.java`, and classifies mappings into:

- **confirmed**: dump CmdId matches the checked-in opcode;
- **safe fill candidates**: server currently has `0`, while the dump gives one unambiguous positive CmdId;
- **mismatches**: server and dump disagree; these require manual review;
- **conflicting names**: one semantic packet name maps to several CmdIds;
- **duplicate CmdIds**: several semantic packet names claim one CmdId;
- **unresolved server opcodes**: constants still set to `0`.

The tool deliberately does not modify Java source. Static extraction is evidence, not proof.

To keep a machine-readable snapshot:

```powershell
uv run python .\tools\protocol_compiler.py report
```

This writes `local/protocol-report.json`.

## Why there is still a dynamic step

A proto dump can be incomplete, a translation can be wrong, and a packet can appear through a UnionCmd message id or a wire opcode that differs from the proto CmdId. For this reason, a version migration should use two phases:

```text
client proto dump
      ↓
static audit
      ↓
proposed semantic name ↔ CmdId map
      ↓
live packet observation
      ↓
confirmed wire opcode / UnionCmd aliases
      ↓
PacketOpcodes.java + handler aliases
```

A safe automated writer can be added later, but only for entries that have passed both the static and dynamic checks.

## Translation file

The parser accepts common two-column forms such as:

```text
ABCDEFG -> PlayerLoginReq
ABCDEFG=PlayerLoginReq
ABCDEFG PlayerLoginReq
```

Unknown names remain under their raw/obfuscated identity in the report. This is intentional; guessing a semantic name is more dangerous than leaving it unresolved.

## CmdId annotations

The first version recognizes common annotations such as:

```text
// CmdId: 12345
// cmd_id = 12345
option (cmd_id) = 12345;
```

If a dump uses another format, extend `CMD_PATTERNS` rather than preprocessing or modifying the raw dump.

## Next steps

The intended progression is:

1. static CmdId audit;
2. capture unknown wire opcodes with action context;
3. decode UnionCmd children and record their message ids;
4. score candidate protobuf parsers for unknown payloads;
5. generate a reviewed patch for `PacketOpcodes.java` and alias registration.

That keeps protocol migration reproducible across future game versions.
