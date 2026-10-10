# Fontaine weapon-domain compatibility coverage

The 7.1 resource snapshot in `RinoPaw/Genshin-Reverse` identifies the following
native roots as missing from the pinned Server/DropTable source. The resource
`RewardPreviewExcelConfigData` provides **terminal item IDs** and
**guaranteed** AR experience/Mora/Companionship experience, but not the full
drop probabilities.

| Dungeon IDs | Weapon materials | Schedule |
| --- | --- | --- |
| 4470–4473 | 114049–114052 | Monday/Thursday, plus Sunday |
| 4500–4503 | 114053–114056 | Tuesday/Friday, plus Sunday |
| 4504–4507 | 114057–114060 | Wednesday/Saturday, plus Sunday |

To keep these 12 domains playable while native drop roots are unavailable,
`data/DungeonDrop.json` now provides explicit compatibility proxies. Their
material-count ranges and probability weights are derived from the existing
Cecilia Garden 4310–4313 server proxies. **These are not native 7.1 rates.**
Only the item families, allowed rarities, and guaranteed quantities above
are source-backed. Do not infer native material drop probabilities from the proxies.

At runtime, a loaded non-empty native root takes priority. The proxies are
used only when the native root cannot be resolved. Invalid rolls reject the
claim before resin is deducted.

Evidence: `versions/7.1.0-global/analyses/progression/economy/7.1-domain-drop-links.json`
and `7.1-dungeon-reward-previews.json` in `RinoPaw/Genshin-Reverse`.
