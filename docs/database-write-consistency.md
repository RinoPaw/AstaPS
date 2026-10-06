# Database write ordering and administrative barriers

This change addresses audit R4 (item saves overtaking deletion) and R10
(administrative barriers bypassed by caller-run or direct writes). It builds on
the accepted P1 shutdown-drain behavior.

## Item persistence

`ItemPersistenceService` reserves a Mongo ObjectId before the first asynchronous
save is admitted. That ID remains the ordering key when Morphia persists the
item and when a different Java instance represents the same document. Only one
write for a given key executes at a time; other items can still write in
parallel. Putting saves and deletes in the same multithreaded pool alone would
not provide this ordering.

An admitted save observes mutable item state when it executes. A consumed item
is deleted instead of upserted with a zero count. Deletion uses the same ordering
key, including consumption while the first save is still in flight. Assigning
the ID before admission also preserves the existing `GameItem.save()` deletion
intent before Morphia has performed any database I/O.

## One admission gate

All core runtime asynchronous and direct database writes use
`DatabaseWriterManager`. Direct writes use
`DatabaseHelper.runSynchronousDatabaseWrite()` or
`callSynchronousDatabaseWrite()`; their result and exception semantics remain
synchronous. The writer tracks admitted work before dispatch, including queued
ordered successors and pool-saturation work running on the caller.

An administrative barrier closes external admission and waits for every
previously admitted write to complete. Its acquisition and drain use one
deadline. It does not insert tasks into executor queues, change pool sizes, or
assume that an executor worker fence also covers caller-run work.

While a barrier is pending or held, external write attempts fail explicitly
with `RejectedExecutionException`. They are not held as stale writes to replay
after a hard delete. Barrier owners and already admitted writer tasks can make
nested synchronous writes through their existing permit. A writer task cannot
acquire a barrier that would wait for its own completion. Barrier timeout or
interruption releases the admission closure; shutdown keeps admission closed.

Clone and hard-delete operations perform their synchronous database work inside
the barrier. Clone failure cleanup remains inside the same exclusive window.
The older public account-deletion helper delegates to the maintained hard-delete
service. Index creation and recovery during `DatabaseManager.initialize()` are
bootstrap operations before normal runtime writers, rather than gated runtime
writes.

## Failure and scope boundaries

Admission rejection is visible to the submitting caller. This change does not
add a business-operation retry queue, dirty-state recovery, or durable outbox.
Database-disconnection handling and recovery of rejected/failed economic
operations remain audit R5. In particular, callers must not mistake admission
for durable Mongo commit or silently ignore rejection.

The barrier coordinates writers in this server process. It is not a Mongo
transaction or a cross-process lock. Ma-passport's existing database routing is
preserved and remains audit R8. Combine award rollback, root `data/` auditing,
and historical repository formatting debt are outside this batch.

One diagnostic follow-up remains: a successor dispatch rejection can be
attributed to the preceding successful task by executor failure metrics. It
does not change write ordering or drain accounting, but task failure attribution
should be separated in a focused diagnostics change.

## Regression validation

Standalone tests use delayed fake persistence callbacks and bounded executor
fixtures. They cover item first-save/consume races, consumed queued saves,
different instances sharing one durable ID, and successive updates. Writer
tests cover cross-pool ordering, queued and caller-run drain, synchronous writes,
barrier ownership and rejection, timeout/interruption recovery, exceptional task
cleanup, and bounded shutdown. These tests do not connect to a live MongoDB or
claim live-client validation.

Validated on 2026-10-06 with Temurin Java 21.
`gradlew.bat jar test -PskipHandbook=1` passed all 196 tests (21 new regressions and 175 existing
tests), with no failures or skips. Spotless validation was scoped to the five
new or rewritten consistency files; historical whole-repository formatting
debt was not mixed into this change.
