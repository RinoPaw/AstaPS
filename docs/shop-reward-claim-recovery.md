# Shop payment receipts (7.1)

The generic inventory admission from `refactor/reward-infrastructure-71`
checks complete rewards and the full payment quote before anything is deducted.
`HandlerBuyGoodsReq` additionally records a Mongo receipt for a *single
server-side purchase attempt*.

Claim order for ordinary goods:

1. Materialize the full batch; check inventory capacity and price.
2. Insert a `PREPARED` receipt containing requested rewards and debits.
3. Deduct the payment using `Inventory.payItems`.
4. Persist purchase limits and advance the receipt to `CONFIRMED`.
5. Add purchased items.
6. Advance the receipt to `COMPLETED` and reply.

Costume goods follow the same receipt sequence, but delivery unlocks a costume
rather than adding equipment to the bag.

## Operator inspection

Use the game's Mongo database and inspect unresolved records:

```javascript
db.reward_grant_receipts.find(
  { source: "SHOP", stage: { $in: ["PREPARED", "CONFIRMED"] } },
  { _id: 1, uid: 1, operationId: 1, rewards: 1, debits: 1, stage: 1,
    createdAt: 1 }
).sort({ createdAt: 1 })
```

Compare the player's balances, inventory, unlocked costumes, shop limits and
server logs before deciding what actually happened. **Do not blindly replay**
or delete unresolved receipts. `PREPARED` may already have taken payment;
`CONFIRMED` may have delivered some or all of the rewards. `COMPLETED`
means reported delivery, not an atomic Mongo transaction.

The receipt key contains UID, shop type, goods ID and a random UUID. The
client request does not supply a verified stable transaction ID. A newly
retried client request creates a different key; the journal does not by
itself deduplicate retries. Purchase limits and current balances are still
rechecked. Unlimited goods need a protocol-level or persisted transaction
identity for exactly-once delivery.

This implementation is designed for diagnosis and fail-closed receipts, not
automatic refunds or reconciliation. No Mongo multi-document transaction
spans the player, inventory and receipt documents.

When the inventory payment method returns a clean `false` after receipt
creation, no payment has been made: the receipt is marked `ABORTED`.
Exceptions remain unresolved and require investigation. Fresh shop retries
receive a distinct UUID and must not be treated as exactly-once operations.
