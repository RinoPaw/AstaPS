# Mail attachment claim recovery (7.1)

Mail rewards use a persistent `RewardGrantJournal` receipt keyed by the Mongo
mail object ID and player UID. A matching receipt survives reconnects and
server restarts; duplicate requests cannot prepare a second receipt.

Claim order:

1. Validate the complete reward batch and capacity.
2. Insert the `PREPARED` receipt with the intended item IDs/counts/levels.
3. Persist the mail's `isAttachmentGot` flag.
4. Advance the receipt to `CONFIRMED`.
5. Apply inventory items or one-shot item effects.
6. Advance the receipt to `COMPLETED`.

`PREPARED` and `CONFIRMED` receipts **must not** trigger automatic
redelivery. They are ambiguous if a database write, client connection, or
server process failed during the claim. The inventory and the mail live in
different persistence units, and effects such as avatar unlocks are not
necessarily idempotent.

For manual inspection, in a Mongo shell connected to the **game database**:

```javascript
db.reward_grant_receipts.find(
  { source: "MAIL", stage: { $in: ["PREPARED", "CONFIRMED"] } },
  { _id: 1, uid: 1, operationId: 1, stage: 1, rewards: 1, createdAt: 1 }
).sort({ createdAt: 1 })
```

For each record, inspect the corresponding mail (the operation ID is its
Mongo ObjectId), player inventory/character unlocks, and logs. Record what
was delivered before manually restoring anything. Never delete receipts or
reset `isAttachmentGot` solely because a record is unresolved: that can
duplicate a partially delivered grant.

`COMPLETED` means the caller reported success; it does **not** imply a
single cross-collection Mongo transaction. This implementation does not
journal domain, shop, gacha or Battle Pass claims yet. It intentionally
does not auto-replay or auto-reconcile uncertain rewards.
