# AstaPS TODO

## Gameplay and compatibility

- [ ] **Replace scene transfers used to refresh constellation state.** `ConstellationCommand.reloadScene()` currently calls `world.transferPlayerToScene(player, 1, pos)` and then transfers the player back to the original scene after a constellation is lowered or bulk-updated. Investigate a safe in-place refresh of avatar talents, entity state, abilities, and client notifications without changing scenes. Test in the open world, domains, quests, and co-op; verify that lower/reset operations update skills correctly, preserve player position and quest state, and do not disturb teammates. Keep the existing command syntax unchanged. *(Recorded 2026-10-10; pending investigation.)*
