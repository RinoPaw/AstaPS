# Reverse-engineering workflow

This document defines the working discipline for reverse-engineering tasks that feed AstaPS. The goal is to make investigations reproducible, resumable, and easy for another person to continue.

## Before starting

Do not begin from a blank slate.

1. Read `AGENTS.md`, this document, and the relevant file under `docs/architecture/`.
2. Check `docs/reverse-status.md` for completed, verified, integrated, paused, and bounded follow-up work.
3. Search `RinoPaw/Genshin-Reverse` for the same feature, packet, symbol, CmdId, field, or subsystem.
4. Read the relevant history, not only the latest file. Check recent commits and previous rejected/candidate conclusions so old dead ends are not repeated.
5. Search AstaPS history and comparable projects when they can provide implementation context.
6. Write down the exact target before probing: client version, platform, subsystem, and the question being answered.

Existing experience is part of the evidence base. Repeating an already documented experiment without a reason wastes time and makes conflicting conclusions more likely.

## Repository boundaries

Use each repository for its own job:

- `RinoPaw/Genshin-Reverse` stores reverse-engineering evidence, scripts, indexes, xrefs, protocol work, hypotheses, and investigation notes.
- `RinoPaw/AstaPS` stores server code, integration behavior, runtime probes, tests, and deployable changes.
- `RinoPaw/AstaPS-Resource` stores version-bound resource data used by the server.

Temporary dumps, exploratory scripts, decompiler notes, and uncertain mappings belong in `Genshin-Reverse`, not in AstaPS production code.

## Save progress early and often

Reverse-engineering work is expensive to reconstruct from memory. Treat recoverability as a requirement.

Create a checkpoint whenever a meaningful unit of work has been completed, for example after:

- identifying or rejecting a candidate;
- extracting a useful xref or call chain;
- finishing a packet/message shape probe;
- producing a reusable script or index;
- reaching a result that would take more than a few minutes to reproduce.

Use small commits on investigation branches. Push/checkpoint before risky bulk edits, long-running scripts, large rebases, or any step that could destroy local state. A temporary WIP commit is preferable to losing an hour of evidence; noisy WIP history can be cleaned when work is promoted.

Do not leave the only copy of important progress in chat history, terminal scrollback, an unsaved decompiler database, or an untracked local file.

## Leave resumable state

When pausing an unfinished investigation, leave enough state for another person to continue without rediscovering the whole path. Record at least:

- **Target** — what is being investigated and for which client build/platform;
- **Confirmed** — facts supported by current evidence;
- **Candidates** — plausible leads that still need proof;
- **Rejected** — approaches or identities already disproved, with the reason;
- **Artifacts** — scripts, addresses, xrefs, captures, files, or commits that matter;
- **Next step** — the smallest useful action to continue the work.

If the investigation has a dedicated note or index in `Genshin-Reverse`, update that file instead of creating a second competing source of truth.

## Evidence discipline

Use the shared confidence vocabulary consistently:

- `CONFIRMED`
- `HIGH_CONFIDENCE`
- `CANDIDATE`
- `REJECTED`
- `UNRESOLVED`

Keep observations separate from interpretations. Record why a conclusion has its confidence level and what evidence would raise or lower it.

Do not promote a candidate mapping into canonical protocol/resource data just because it makes one test pass. Cross-version or cross-platform matches are leads unless current 7.1 Global / Windows x64 evidence supports them.

When a conclusion changes, preserve the reason. Replacing an old value without documenting why it was wrong makes future regressions harder to diagnose.

## Prefer reproducible tooling

If a manual action is repeated, turn it into a script or documented command sequence. Prefer deterministic inputs and outputs over one-off GUI state.

Keep heavy executable analysis, resource extraction, live-client captures, and long reverse pipelines outside routine CI. Regular CI should stay fast enough that it does not become a reason to avoid committing progress.

## Promoting results into AstaPS

AstaPS should consume reverse-engineering results only after the relevant evidence is stable enough for implementation.

For non-obvious protocol or client-behavior dependencies, leave a compact pointer in the commit, code comment, or architecture note to the relevant `Genshin-Reverse` artifact or commit. Do not copy large reverse dumps into the server repository.

Keep the server-side change focused. Reverse findings and server implementation may advance at different speeds; an unresolved reverse question should remain explicit instead of being hidden behind guessed constants or broad fallbacks.

## Track completed outcomes

When an investigation becomes reusable, update `docs/reverse-status.md`. This is especially important for work that is **COMPLETED**, **VERIFIED**, **INTEGRATED**, deliberately **PAUSED**, or replaced by a newer result.

The status page is an index, not a second evidence store. Record the topic, state, durable artifact, integration commit when one exists, and the bounded remaining work. Keep detailed proof and chronology in `Genshin-Reverse`.

A completed result that cannot be discovered by the next researcher is only partially maintained.

## Handoff standard

A handoff is good when the next person can answer these questions quickly:

1. What were we trying to learn?
2. What do we know now?
3. What did we already try and reject?
4. Where is the evidence saved?
5. What should be done next?

If any of those answers exist only in someone's memory, the work is not safely saved yet.
