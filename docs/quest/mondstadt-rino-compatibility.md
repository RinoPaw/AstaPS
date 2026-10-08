# Rino-based Mondstadt 7.1 quest compatibility policy

Base: `RinoPaw/AstaPS:play/rino`; retain its native 7.1 Quest parser,
field-source ledger, merge normalization and existing Java 21 build.

Runtime ownership:
- Genshin-Reverse native Quest overlay owns verified 7.1 finish/fail
  conditions and actions. It does not expose historical acceptCond/beginExec.
- For 40 reviewed Mondstadt MainQuests only, restored BinOutput compatibility
  owns *nonempty* acceptCond and acceptCondComb, regardless of the flattened
  QuestExcel's synthetic quest-zero chain.
- A populated Excel action/reward list stays authoritative. If it is empty,
  restored compatibility begin/finish/fail actions and rewards may fill it.
- For a multi-condition finish/fail objective, a reviewed BinOutput AND/OR
  can recover an Excel missing/LOGIC_NONE combinator. An explicit non-NONE
  Excel combinator is never overwritten.
- Every such fallback is marked `QuestSource.REVIEWED_COMPAT`, never
  `NATIVE_QUEST`. Outside the reviewed 40 MainQuests, the existing
  `play/rino` precedence is unchanged.
- Unrecognized native semantic codes are still fail-closed; this patch does
  not expand Genshin-Reverse's confirmed type map.

Regression checks exercise 35302's scene 3 slime suite, 30901's three-dungeon
AND, and 37602's alternative failure OR. This migration is deliberately
smaller than merging the divergent `integrate/mondstadt-prologue-71` history.
Other runtime/scene fixes from that branch still need independent ports.

The default Gradle artifact is `grasscutter-7.1.0.jar`. The CI workflow
must not change its name through `-PjarFilename`.
