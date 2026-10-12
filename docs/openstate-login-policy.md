# OpenState unlocks on login

AstaPS gameplay configuration lives in `game.json` on `play/rino`.
Mass unlock is independent of the quest system:

```json
{
  "quests": { "enabled": true },
  "openStates": { "unlockAllOnLogin": false }
}
```

- `openStates.unlockAllOnLogin: false` (default, even if the key is absent)
  prevents the login-time mass unlock of every feature. Legitimate resource
  defaults, quest/level prerequisites and client-owned unlocks still work.
  Disabling quests no longer implicitly opens every feature.
- `openStates.unlockAllOnLogin: true` explicitly restores the login-time
  bulk unlock, except blacklisted/ignored entries, independently of the
  questing setting. Intended for debug/sandbox servers.
- Previously stored player unlocks are preserved, even after turning this off.
  Test with a new account to observe the new default behavior.
- This setting neither forces `quest_remind` (7) nor bypasses native tutorials.

Additionally, 7.1 `OpenStateConfigData.json` specifies condition type as
`cond_type`. The server now parses this field (and accepts old `condType`)
before evaluating automatic unlocks. Empty condition lists on non-default
states no longer count as satisfied prerequisites.
