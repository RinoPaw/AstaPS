# Reward receipt failure windows (7.1)

The journal is a durable **audit trail and duplicate-key guard**, not an atomic
transaction or an automatic recovery queue. A successful admission check only
prevents known-invalid batches *before* payment; it does not roll back effects.

| Failure point | Observable receipt | What is known | Required action |
| --- | --- | --- | --- |
| Preparation rejected before DB write | none | No journal acknowledgement; caller must stop | Inspect errors; do not assume a retry is safe after an ambiguous response |
| Preparation applied but acknowledgement lost | PREPARED | Payment may not have begun, but caller saw an exception | Manual inspection; never reissue grant automatically |
| Payment/claim flag changed, confirmation not recorded | PREPARED | Charge/claim may have succeeded | Reconcile payment, claim state and inventory before any action |
| Confirmation recorded; insertion not begun or incomplete | CONFIRMED | Claim/payment was acknowledged, grant amount is uncertain | Reconcile item-by-item, including virtual/direct-use rewards |
| Item delivery succeeded; completion write failed | CONFIRMED or COMPLETED | Client/in-memory outcome may differ from DB | Verify durable player/inventory state and journal separately |
| Server crashes before player/inventory persistence | PREPARED or CONFIRMED | Receipt does not prove item persistence | No automatic refund, resend, clearing or replay |

For domain claims, a new DungeonManager or restart produces a new UUID.
Old unresolved receipts remain available for manual inspection, but the UUID
alone cannot attest that a previous run's payment and inventory persisted.

For Battle Pass, a stable season key depends on the schedule identifier and
season start value. Reusing both in a later season would collide with older
claim receipts. Do not rotate the key for an *incomplete* claim to evade a
collision.

The fault-injection unit tests model insert/transition failures occurring
before or after a write and inventory callbacks failing before or after grant
starts. They do **not** simulate Mongo durability, actual process crashes or
cross-collection atomicity. These need integration tests before stronger
guarantees are claimed.

## Database writer boundary

`RewardGrantJournal` submits Mongo writes synchronously, but `DatabaseHelper.saveBattlePass()`, `savePlayer()` and `saveItem()` enqueue asynchronous saves. `DatabaseWriterManager.callSynchronous()` is an admission gate for a direct write; it does **not** drain earlier queued writes. Some asynchronous save failures are logged rather than propagated to the claim caller. Therefore even a `COMPLETED` receipt cannot prove claim flags, property debits, or inventory items are durable. This remains true when the normal process is stopped before outstanding writes finish.
