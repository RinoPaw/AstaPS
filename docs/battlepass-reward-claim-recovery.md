# Battle Pass reward claim receipts (7.1)

This branch adds persisted claim receipts to the existing validated
Battle Pass reward path. Both selectable chests and ordinary rewards now
flow through BattlePassCompatHelper.takeReward; the packet handler must not
bypass the receipt journal for selected items. Each claimed reward uses a stable key:

`BATTLE_PASS : player UID : schedule ID . season begin . reward ID`

The key does not include the selected item index because the reward
itself is claimable only once. Retrying with another selection after
a prepared claim must not generate a second grant. Different seasons,
players and reward IDs have separate identities.

The claim order is:

1. Check reward membership, level, claimed state, selection and inventory
   capacity. Construct the exact item snapshot.
2. Insert `PREPARED` in `reward_grant_receipts`.
3. Mark `takenRewards` in memory, enqueue its asynchronous save, and advance the receipt to `CONFIRMED`. The save is **not** known to have completed at confirmation.
4. Apply the prepared rewards.
5. Advance to `COMPLETED` and return the reward response.

On failure in steps 2–4, do not automatically rerun or restore the
claim; some items may already have been delivered. `CONFIRMED` proves
that the claim state was marked in memory before the inventory write began,
not that the entire batch was delivered.

Inspect unresolved entries against Battle Pass reward state and player
inventory in the game MongoDB:

```javascript
db.reward_grant_receipts.find(
  { source: "BATTLE_PASS", stage: { $in: ["PREPARED", "CONFIRMED"] } },
  { _id: 1, uid: 1, operationId: 1, rewards: 1, stage: 1, createdAt: 1 }
).sort({ createdAt: 1 })
```

Existing receipts cannot be replayed automatically because the reward
batch may include unlock effects, virtual balances or stacked materials.
No cross-collection transaction or automatic recovery is claimed.
This patch does not alter the actual selection tables or reward contents.

Persistence boundary: `BattlePassManager.save()` delegates to `DatabaseHelper.saveBattlePass()`, which calls `saveGameAsync()`. The receipt transition uses a separate synchronous Mongo write. Thus a `CONFIRMED` (or even `COMPLETED`) receipt does **not** prove that the Battle Pass claim flag or inventory changes reached MongoDB. On restart, inspect all three independently; do not replay or clear receipts automatically.
