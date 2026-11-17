# 7.2 protocol gaps

Only messages the server code uses are listed.

## Verified bindings in this update

The initial recovery verified 40 CmdId bindings: enabled 34 missing bindings and corrected 6 stale active bindings. Corrected 80 field numbers. 30 corrections come from matched original native field accesses; the others use verified current named fields, including ascension and the swapped home resource value/capacity. Three conflicting legacy fields were moved to unused compatibility slots rather than overwriting the confirmed roles.

The initial 2,105-file descriptor delta was checked: no unplanned schema changes, no new opcode collisions, and the hand-corrected `GachaInfo` is preserved. Native validation covers 22 ascension sender cases, 332 field instruction cases and 4254 additional native flow/getter cases in the two client builds. Java regression tests cover ascension, home resources, gacha flags, furniture output, profile requests, home music, team deletion, mail operations, push-tip reward replies, distinct empty-request routes and both achievement reward parsers. This does not replace a live 7.2 client test.

The corrected empty queries now use profile pictures 21907, friend applications 21795, friend list 1684, online players 22361, city reputation map 29401, map tips 20856 and return to own world 28289. The old bindings routed some of these requests into unrelated handlers. The native map-tip reply has retcode field 10; its existing empty-success builder now omits defaults instead of writing an unrelated field-1 zero. Both achievement claim parsers use the generated field constants while preserving their validation boundaries. Coop-task cancellation uses CmdId 26546 and chapter field 4. Trial-avatar reward claims use CmdId 20742 and index field 2; home realm switching uses CmdId 21043 and target field 10. Activity watcher claims use CmdId 1504, activity field 8 and watcher field 6; the former server value 25045 belongs to a reply-shaped native message. Story unlock uses CmdId 28530 and personal-line field 13; home edit mode uses CmdId 29979 and bool field 12. Tower buff selection uses request 705 with buff field 2 and reply 8282 with buff field 12/retcode field 15. First realm choice uses request 8792 with module field 3; its already-bound reply is 3766.

Push-tip reward claims already used request 24813 and ID-list field 2, but their reply was disabled and still used old field numbers. The reply now uses CmdId 9626, ID-list field 7 and retcode field 11. Its native error branch is executed; the list name uses the matched reply scope and the same current typed field symbol as the identified request. No live echo transaction or native read of the reply list is claimed.

The original checklist below is retained as a baseline. A broader source audit also finds 55 disabled constants omitted from that checklist, including empty request handlers and compatibility paths. That earlier audit counted 119 disabled constants using its narrower source filter. The integrated repair now has 125 source-referenced zero constants and 49 zero-valued handler annotations across the complete Java source scan. A source reference alone does not establish runtime reachability or prove that a current client CmdId exists. No historical CmdId is assigned merely to clear the list.

The main protocol export also identifies `HomeSceneInitFinishReq` (28172), `PlayerApplyEnterMpResultReq` (4544), `PlayerApplyEnterHomeResultReq` (7623) and `PlayerApplyHomeCreateBlueprintResultReq` (25479). Their server bindings remain disabled while the corresponding replies are verified. The home-scene reply currently has three indistinguishable error-only consumer candidates: CmdIds 26934, 860 and 2648. Existing captures contain none of these home/invitation operations; no candidate was selected by field shape alone.

`TpsEquipChangeNotify` is identified as CmdId 26670 through the native `TPSWeaponEquiped` event. Its server binding remains disabled while the nested weapon payload is checked.

The compact evidence index is `protocol-7.2-verified-bindings.json`. The remaining candidate pairs below have not been applied solely because they are the only fields left with a matching type.

## Integrated public-main repair

This branch includes the earlier verified recovery and the 7.2 crafting/artifact implementation. Compared with public `main`, 50 zero-valued bindings are enabled, six stale active bindings are corrected, and 39 named protocol aliases are added. The five old `ReliquaryOffer*` names intentionally alias their current named messages; the unified artifact handler owns both request routes.

- Crafting uses the native reward-list fields and reports per-combine output. Output capacity is checked before payment. Unknown packets are no longer routed to crafting by payload shape, and the zero-valued union alias is not registered.
- Artifact transfer, batch locking, lock settings/schemes, custom equipment plans, equipment preview, and backpack filters use the native 7.2 layouts. Filters and plans survive relogging. Reforge candidates use durable item identity and cannot be consumed while pending.
- The transmuter validates the complete material/GUID list, schedule, cycle cap, and output capacity before charging. Empty or malformed requests never create free progress. Fractional progress, crafted-set limits, and pending results are persisted in the player save; the old transmuter state file is migrated without deleting it.
- Multiplayer applications now read target UID field 12; application notifications use thread field 1 and app field 13. Home applications use player field 14 and app field 9. Kick requests, team-entity deletion, home edit completion, and tower team/surrender replies have current bindings.

The resource pack must supply `ConstValueExcelConfigData`, `ReliquaryOfferPurchaseExcelConfigData`, the lock/general-option and set-scheme tables, and normal reliquary main/append/level data. Missing configuration produces an error rather than fabricated rewards. Bundled default lock rules cover only the independently confirmed set 15041 and can be overridden with `data/ReliquaryLockSchemes.json`.

The 2,177 server schemas compile. All 72 newly imported schemas and seven further corrected message layouts match the current client field numbers, types, cardinalities, and nested type identities. No live Android session was available for verification.

### Validation

Java 21 compilation, strict generated-protocol compilation, JAR packaging, and the JAR ZIP integrity check pass. The focused protocol/artifact/achievement/inventory run passes all 129 tests. The complete non-integration suite runs 1,528 tests with 74 failures and four skips; the same-environment public-main baseline runs 1,491 tests with 82 failures and four skips. Comparing individual failing cases finds no newly failing case and eight resolved failures. The remaining failures include stale 7.1 protocol assertions; the full suite is not green. Neither run starts a live Android client or a production database.

### Remaining work

This does not implement the whole missing-feature list. In particular, the two multiplayer/home result replies, home-scene initialization reply, world revival reply, entity-update payload, train/ice/TPS payloads, full Stygian Onslaught gameplay, and the separate quest-condition repairs remain incomplete. Identified requests whose reply identity is unresolved remain disabled. Custom lock defaults are not invented for other sets; absent recommendation statistics return typed empty results.

## Disabled CmdId bindings (57)

These remain 0 in `PacketOpcodes`. Their handlers are not registered and outbound packets are not sent. Some messages are identified, but still need their fields or nested payloads checked.

`AvatarChangeElementTypeRsp`, `AvatarDelNotify`, `BuyBattlePassLevelRsp`, `CheckUgcStateReq`, `CheckUgcStateRsp`, `DeleteFriendReq`, `DungeonPlayerDieRsp`, `EntityAiKillSelfNotify`, `ExecuteGadgetLuaRsp`, `ExecuteGroupTriggerRsp`, `GetFriendShowAvatarInfoReq`, `GetFriendShowNameCardInfoReq`, `GetHomeLevelUpRewardRsp`, `GetWorldMpInfoRsp`, `HomeAvatarSummonEventRsp`, `HomeAvatarSummonFinishRsp`, `HomeResourceTakeHomeCoinRsp`, `HomeSceneInitFinishReq`, `HomeSceneJumpRsp`, `IceCreatureDataNotify`, `IceCreatureGadgetDisappearNotify`, `IceCreatureSetTypeGadgetReq`, `MarkNewNotify`, `MusicGameSettleReq`, `MusicGameSettleRsp`, `MusicGameStartRsp`, `NALBKOPFIFD`, `PlayerApplyEnterHomeResultReq`, `PlayerApplyEnterHomeResultRsp`, `PlayerApplyEnterMpResultReq`, `PlayerApplyEnterMpResultRsp`, `PlayerChatRsp`, `QuestDelNotify`, `ReadPrivateChatRsp`, `ReformFireworksRsp`, `SceneEntityDrownReq`, `SceneEntityDrownRsp`, `SceneEntityUpdateNotify`, `SetBattlePassViewedRsp`, `SetPlayerHeadImageRsp`, `SetPlayerPropRsp`, `ShowClientGuideNotify`, `SkipPlayerGameTimeReq`, `SkipPlayerGameTimeRsp`, `SnezhnayaTrainCallNotify`, `SnezhnayaTrainCallReq`, `SnezhnayaTrainSetRouteReq`, `SnezhnayaTrainStateNotify`, `TpsAmmunitionChangeNotify`, `TpsEquipChangeNotify`, `UnfreezeGroupLimitNotify`, `WatcherChangeNotify`, `WearTpsEquipReq`, `WearTpsEquipRsp`, `WidgetDoBagRsp`, `WorldPlayerReviveRsp`, `_TakeAllForgeOutputReq`

## Message schemas still requiring 7.2 bindings (68)

These server protocol paths still require verified 7.2 bindings. Some empty requests are identified but await their paired replies or business transitions; other payloads remain incomplete.

`AABLEDJBAKK`, `AbilityMetaSpecialEnergy`, `AvatarChangeElementTypeRsp`, `AvatarDelNotify`, `AvatarTraceEffectChangeNotify`, `BMJNJLKCODG`, `BPAEPJHBOFM`, `BuyBattlePassLevelRsp`, `CheckUgcStateReq`, `CheckUgcStateRsp`, `CheckUgcUpdateReq`, `DeleteFriendReq`, `DungeonPlayerDieRsp`, `EntityAiKillSelfNotify`, `ExecuteGadgetLuaRsp`, `ExecuteGroupTriggerRsp`, `GetFriendShowAvatarInfoReq`, `GetFriendShowNameCardInfoReq`, `GetHomeLevelUpRewardRsp`, `GetShopReqNew`, `GetShopRspNew`, `GetUgcBriefInfoReq`, `GetWorldMpInfoRsp`, `HomeAvatarSummonEventRsp`, `HomeAvatarSummonFinishRsp`, `HomeResourceTakeHomeCoinRsp`, `HomeSceneInitFinishReq`, `HomeSceneJumpRsp`, `IceCreatureDataNotify`, `IceCreatureGadgetDisappearNotify`, `IceCreatureGadgetInfo`, `IceCreatureSetTypeGadgetReq`, `MarkNewNotify`, `MusicGameSettleReq`, `NALBKOPFIFD`, `PlayerApplyEnterHomeResultReq`, `PlayerApplyEnterHomeResultRsp`, `PlayerApplyEnterMpResultReq`, `PlayerApplyEnterMpResultRsp`, `PlayerChatRsp`, `PropChangeDetailInfo`, `QuestDelNotify`, `ReadPrivateChatRsp`, `ReformFireworksRsp`, `SceneEntityDrownReq`, `SceneEntityDrownRsp`, `SceneEntityUpdateNotify`, `SetBattlePassViewedRsp`, `SetPlayerHeadImageRsp`, `SetPlayerPropRsp`, `ShowClientGuideNotify`, `SkipPlayerGameTimeReq`, `SkipPlayerGameTimeRsp`, `SnezhnayaTrainCallNotify`, `SnezhnayaTrainCallReq`, `SnezhnayaTrainSetRouteReq`, `SnezhnayaTrainStateNotify`, `TowerMiddleLevelChangeTeamNotify`, `TpsAmmunitionChangeNotify`, `TpsEquipChangeNotify`, `TpsWeaponAmmunitionInfo`, `UnfreezeGroupLimitNotify`, `WatcherChangeNotify`, `WearTpsEquipReq`, `WearTpsEquipRsp`, `WidgetDoBagRsp`, `WorldPlayerReviveRsp`, `_TakeAllForgeOutputReq`

## Fields with no verified 7.2 number (251 in 83 messages)

Parked at field 20000 and up until verified. These are not current client field numbers; protobuf still writes them if server code explicitly sets them. The three displaced legacy fields stay available as Java API names, but must not be used as current client fields.

- `AbilityIdentifier`: NBHCDKPBBOI, DLLKGHINFBJ
- `AbilityMetaModifierChange`: MFBDIICANOF, NBBGHMGOALL, PKJMOLPCEBG, OCAMKBCONED
- `AbilityMetaUpdateMoonOvergrowValue`: FOMPMBNENPH
- `AbilityMetaUpdateTpsWeaponAmmunition`: FENKFADBDID
- `ActivityInfo`: JJFJONCHEIK, meet_cond_list, expire_cond_list, is_starting, OPPDJKGIKBJ, DIEFHMGFNIJ, is_common_content_closed, is_banner_cleared
- `AddNoGachaAvatarCardNotify`: initial_level, initial_promote_level
- `AiSyncInfo`: COFJBBPPANA, MBNNMNHEDAB
- `AttackResult`: resolved_dir, DNMDKFFCNPJ, OHPEIAGBOIK, EFBFOAOJADK, PGPBACPJGNM, OOFNGENKHKG, FDEGAMMGNGI, CFKHDFPCHCL, EKDNPKHMJIE, MDBPICGKMLB, LNFCPOINHKK, IFHJIFEBKAO, EPLABAAGCKE, DMIEAHDCHFA, NJFMEBDIICN, IDENIGMCBJB, PKOFLCBFEOA, OMJGKPMPFBI, GGAMPLOEPMG, PLNPCMILPPD, NILAPFIGLLH, MHJDKCODNAA, MDECMKGHDAK, GMNALDFDANK, JCPDJCGGJCC, BJHAEPGOAKM
- `AvatarEnterSceneInfo`: tps_weapon_list
- `AvatarInfo`: tps_weapon_list
- `BargainSnapshot`: PGNJBNJCBLP, FLCAIJFGLNP
- `BattlePassProduct`: LGOGOFAFJBK, LJKGANJLFNJ
- `BattlePassSchedule`: JIPEKMKKHOI, cur_cycle_points, paid_platform_flags
- `CardProductRewardNotify`: OABOKBGPEHG
- `ChangeAvatarReq`: is_move, FGGPBEPIJOG
- `ChapterStateNotify`: MLBMNAKEEOM, EHKKFCAGELK
- `ChatInfo`: ALKIPKCAPFL
- `CodexDataUpdateNotify`: OBEHJKBNDJG
- `CompoundQueueData`: wait_count, output_time, output_count
- `CoopChapter`: ODECGJCJBAB, JKAGGEGDKOH, lock_reason_list
- `DailyDungeonEntryInfo`: ODDLACNLJOF, EDEOIILGGJC, AKNPDDFHEKO
- `DoGachaRsp`: is_epitomized
- `DungeonEntryInfo`: max_boss_chest_num, boss_chest_num
- `DungeonPlayerDieNotify`: murderer_entity_id, OBOKABMLGHI, AOMDIHGJMNK
- `EnterSceneDoneRsp`: PPKIKFGEINK
- `EntityFightPropChangeReasonNotify`: EFIBMPKIJJH
- `EvtAvatarEnterFocusNotify`: KLNNOPGOMKN, JLNKDGGPICE, ADMFCLBHILF
- `EvtBeingHealedNotify`: GOKLCPHOLGL, real_heal_amount, CMCCNKMGKHG, heal_amount
- `EvtBulletHitNotify`: single_bullet_id, hit_entity_id
- `EvtCreateGadgetNotify`: AAMABJDMIFN, NHNFGNMNDDE, LMLMALLLGBB, PBLACGNNCDF
- `ForgeQueueData`: next_finish_timestamp, unfinish_count, queue_id, total_finish_timestamp
- `FurnitureMakeRsp`: NDADFCDJOBK
- `GadgetInteractReq`: DOGHJJCHDLF, IKMNHOHNBKJ, LPCDPGNKGFL, IKDIFBEOEKM, HDJABIODGCI, EBEELPIEKEL
- `GetAllMailResultNotify`: total_page_count, page_index
- `GetPlayerTokenReq`: BIIJMKDGAGI, account_type, sub_channel_id, FDCECMCNKGC
- `GetPlayerTokenRsp`: gm_uid, reg_platform, MGNAAEGCEDL, KONAINKDJCN, CGPIOKGGDPB, is_proficient_player, account_type, sub_channel_id
- `GetScenePointRsp`: JHHBKAOPBAH, IEMCNADNPED, OLEEDBKCAAN
- `GetShopmallDataRsp`: DPHCDGFOLCN
- `HitCollision`: OMEMJMGDNPP, EGIAGADINML, PFLEHLDGODB
- `HomeBlockArrangementInfo`: deploy_furniure_list, persistent_furniture_list
- `HomeMarkPointSceneData`: teapot_spirit_pos, safe_point_pos
- `HomeSceneArrangementInfo`: stair_list, MEEACPBAEJN, IILKHACKPAI, door_list, block_place_pos_list, OADJEMPMKIF, KGGKIAHPDGO
- `IceCreatureAllGadgetInfoNotify`: ice_creature_gadget_info_list
- `IceCreatureBuildReq`: global_value_list, unk_11, build_detail, replace_guid_list
- `IceCreatureBuildRsp`: ice_creature_gadget_info
- `IceCreatureGadgetRemoveReq`: unk_15
- `InvestigationMonsterUpdateNotify`: JDEPBCMJPOP
- `ItemAddHintNotify`: CLKIDLBMBAM, BAIIJPFGOKI, JAKPAGGBINC, OLBMJDOKNAC
- `MarkTargetInvestigationMonsterNotify`: GHPPPJJLDJN
- `MassiveEntityElementOpBatchNotify`: user_id, op_idx, source_element_type, reaction_source_type
- `MusicGameActivityDetailInfo`: NCCMPNEBJLA, EPEEHJNIPNB
- `OneoffGatherPointDetectorData`: CBENILCDDCB
- `ParentQuest`: GFFBOKNBJLC, HONCJCKFDJE, EODCGKGCGDN
- `PersonalLineAllDataRsp`: NIPKCPGAMKF
- `PingReq`: BLOPFLFBDAE, MCCPJDKDNMJ
- `PlayerEnterDungeonReq`: BAEJLCIOPMI
- `PlayerEnterSceneNotify`: PCBFOAKLMKG, ALLIIBMKFLN, HBCAHGNHINM, BGICEPHCGEO
- `PlayerLocationInfo`: HPOIENCPHFN
- `PlayerLoginRsp`: stop_server_info, KKNBKGBLHDM, DPPKDPCKANP, is_sc_open, is_use_ability_hash, GICKBBGJGCK, JBEGCLMHOPF, KLKDKMBAPGA, GHAJDNKLEDA, OEMHPIKBDMB, HACEPFEEKGA
- `PlayerOfferingData`: taken_random_level_reward_map
- `PlayerWorldSceneInfoListNotify`: IIFJABJJJBF
- `ProudSkillExtraLevelNotify`: talent_index, talent_type
- `QueryCodexMonsterBeKilledNumRsp`: GOIJKJBPKAF, BMCNAMPBGAF
- `QueryPathReq`: GEAKGEBKLGB, CPMFACKILJN
- `QueryPathRsp`: INAAJKIFKIF, FBLCJIHADLH
- `QuestTransmitReq`: NCEIBFLFIPM
- `ReliquaryUpgradeRsp`: old_append_prop_list, cur_append_prop_list
- `SceneAvatarInfo`: tps_weapon_list
- `SceneTeamAvatar`: tps_weapon_list
- `Shop`: city_reputation_level, hogdkcckgap
- `ShopGoods`: MEODDILKAAD, ELPGDNACFOA, mcoin, OCFMGIPGLDK, scoin, HBFOJABHIND, AJCBLGEDLFK, single_limit, NGPHJJIEDEH, MMMFLNHEDNI, LDCJFLOPNMP, AIALJDFIEAI, EKBHAAKJINF, KAGKMENGAFH, MBMIKDBJNAN, DCMBDJMCGGP
- `StoreWeightLimitNotify`: CDHAIJGOKFC, DKIEKFJMMIH, OAJHCKBKFPP, reliquary_count_limit, KADABCKKMGA, _beyond_hall_facility_count_limit
- `TeamEnterSceneInfo`: JFAHOBMFLFE, FOMPMBNENPH
- `TowerAllDataRsp`: commemorative_reward_id, EFABCDDNIFA, valid_tower_record_num, skip_to_floor_index, GKIFECKDHKD
- `TowerCurLevelRecord`: cur_level_index, cur_floor_id
- `TowerLevelEndNotify`: continue_state, next_floor_id
- `TowerLevelStarCondData`: cond_value, star_cond_index
- `TpsAmmunitionChange`: ammunition_config_id
- `TrialAvatarActivityDetailInfo`: CBPJGGEBMBI
- `TrialAvatarActivityRewardDetailInfo`: _passed_training_step_list, _available_training_step_list, _is_avatar_owned, passed_dungeon, _is_skip_training
- `UgcMusicBriefInfo`: CCJOGFPEKID, COAEPKLMKPE, self_max_score, HHBOBIJKFPJ, NLCMMJILBKJ, IIPJKAOBBBD, MOIDOMCHPFG, note_count, CDEMBFGJAPH
- `WeaponPromoteRsp`: old_promote_level, cur_promote_level
- `WorldPlayerDieNotify`: OBOKABMLGHI, murderer_entity_id, AOMDIHGJMNK

### Unconfirmed candidates

Pairs where a message has exactly one unmatched 7.1 field and one unmatched 7.2 field of the same type (`recover_lost_72.py --single`). Not applied: check each against a capture first.

- AbilityMetaModifierChange.MFBDIICANOF: 20000 -> 2  (7.1 LFGDCBHDFIL = 7.2 JHCJKEDLDAD, single-message)
- AbilityMetaModifierChange.OCAMKBCONED: 20003 -> 4  (7.1 GMEPMCLHEDP = 7.2 HKFKIALDHIO, single-message)
- AbilityMetaUpdateMoonOvergrowValue.FOMPMBNENPH: 20000 -> 11  (7.1 GCNOLPNPHPA = 7.2 value, single-message)
- TpsAmmunitionChange.ammunition_config_id: 20000 -> 6  (7.1 PPFJCENMNEO = 7.2 item_id, single-message)
- AbilityMetaUpdateTpsWeaponAmmunition.FENKFADBDID: 20000 -> 1  (7.1 FENKFADBDID = 7.2 AEIMHKMPHOG, single-message)
- TrialAvatarActivityDetailInfo.CBPJGGEBMBI: 20000 -> 6  (7.1 MMHPPBIEGFG = 7.2 OIEPLMDMEAM, single-message)
- OneoffGatherPointDetectorData.CBENILCDDCB: 20000 -> 1  (7.1 HDCCPLEDJMH = 7.2 CDLEKEGELMD, single-message)
- AvatarInfo.tps_weapon_list: 20000 -> 37  (7.1 MJGFMDADPJE = 7.2 weapon_list, single-message)
- SceneAvatarInfo.tps_weapon_list: 20000 -> 31  (7.1 MJGFMDADPJE = 7.2 weapon_list, single-message)
- AvatarEnterSceneInfo.tps_weapon_list: 20000 -> 12  (7.1 LMAIAOIPHJL = 7.2 weapon_list, single-message)
- BattlePassSchedule.JIPEKMKKHOI: 20000 -> 8  (7.1 DFDNNGJHJIC = 7.2 AMBABCAPMGE, single-message)
- ShopGoods.KAGKMENGAFH: 20013 -> 1055  (7.1 AEHELDJPPPH = 7.2 LAJHLJGOMPH, single-message)
- CardProductRewardNotify.OABOKBGPEHG: 20000 -> 8  (7.1 DDOINBPHJMP = 7.2 FOFGCGMLPIB, single-message)
- ChatInfo.ALKIPKCAPFL: 20000 -> 4  (7.1 LOEJDBPKKNF = 7.2 MIBOFEIBECN, single-message)
- CodexDataUpdateNotify.OBEHJKBNDJG: 20000 -> 2  (7.1 NELOMLLAKME = 7.2 KBGFCJCMNFP, single-message)
- DailyDungeonEntryInfo.ODDLACNLJOF: 20000 -> 1  (7.1 HAOOOFEBAMI = 7.2 BMOKOMEOKCP, single-message)
- EnterSceneDoneRsp.PPKIKFGEINK: 20000 -> 12  (7.1 OGCIHDJAHHL = 7.2 MFLHLEMFIGI, single-message)
- EntityFightPropChangeReasonNotify.EFIBMPKIJJH: 20000 -> 3  (7.1 HNBCFDELLDN = 7.2 GCFBFOHHHJJ, single-message)
- EvtBeingHealedNotify.CMCCNKMGKHG: 20002 -> 13  (7.1 AFAHAOFIJJO = 7.2 is_mute, single-message)
- EvtBeingHealedNotify.GOKLCPHOLGL: 20000 -> 2  (7.1 JONDCAMLJOF = 7.2 heal_tag, single-message)
- ParentQuest.GFFBOKNBJLC: 20000 -> 7  (7.1 PAJJMIMGHHI = 7.2 KANNCEMKHIG, single-message)
- FurnitureMakeRsp.NDADFCDJOBK: 20000 -> 11  (7.1 IKCDMDEPMDC = 7.2 OEGCONFIHLI, single-message)
- IceCreatureAllGadgetInfoNotify.ice_creature_gadget_info_list: 20000 -> 10  (7.1 _ice_creature_gadget_info_list = 7.2 _ice_creature_info_list, single-message)
- IceCreatureBuildReq.global_value_list: 20001 -> 4  (7.1 LNLLJKPOIOL = 7.2 FLJBADCNDMB, single-message)
- IceCreatureBuildReq.unk_11: 20002 -> 12  (7.1 IFBHPHJDCEO = 7.2 JCNJDMFCMHM, single-message)
- IceCreatureGadgetRemoveReq.unk_15: 20000 -> 11  (7.1 IIMAFOBOEMA = 7.2 FCBLDNACNJK, single-message)
- InvestigationMonsterUpdateNotify.JDEPBCMJPOP: 20000 -> 4  (7.1 GAKDKMHBFGA = 7.2 ICDBOBCNCMB, single-message)
- MarkTargetInvestigationMonsterNotify.GHPPPJJLDJN: 20000 -> 5  (7.1 ILJJLKDMFIK = 7.2 JNLDEIPGDPI, single-message)
- TeamEnterSceneInfo.FOMPMBNENPH: 20002 -> 9  (7.1 GCNOLPNPHPA = 7.2 AGJIEAHJPJC, single-message)
- PlayerEnterSceneNotify.ALLIIBMKFLN: 20001 -> 763  (7.1 NJEOMOIDMNM = 7.2 LFPEKBJIJNG, single-message)
- PlayerEnterSceneNotify.BGICEPHCGEO: 20003 -> 46  (7.1 NJDJMFJCMBM = 7.2 LMKLCDPJOBJ, single-message)
- PlayerLocationInfo.HPOIENCPHFN: 20000 -> 4  (7.1 IEPPHCJANGA = 7.2 HFFCDKKCIMN, single-message)
- PlayerLoginRsp.KKNBKGBLHDM: 20002 -> 265  (7.1 HMMPPDIBFAO = 7.2 HIHJANHKAMO, single-message)
- PlayerLoginRsp.stop_server_info: 20000 -> 1314  (7.1 BMCDKDDKIBH = 7.2 LBDHEONCLNL, single-message)
- PlayerOfferingData.taken_random_level_reward_map: 20000 -> 2  (7.1 PKOKLIFNFIB = 7.2 CBMCLHIIEKD, single-message)
- PlayerWorldSceneInfoListNotify.IIFJABJJJBF: 20000 -> 693  (7.1 FMIPEDBGOMN = 7.2 unlocked_area_id_list, single-message)
- QueryPathRsp.FBLCJIHADLH: 20001 -> 3  (7.1 MLPCGBFDBGC = 7.2 OCOOOCJJPCC, single-message)
- QueryPathRsp.INAAJKIFKIF: 20000 -> 10  (7.1 ANBCOBHEMFO = 7.2 EGDBEDNAFGL, single-message)
- QuestTransmitReq.NCEIBFLFIPM: 20000 -> 13  (7.1 EOPPLJLCEIJ = 7.2 HEJLIMPMKLL, single-message)
- SceneTeamAvatar.tps_weapon_list: 20000 -> 645  (7.1 LMAIAOIPHJL = 7.2 weapon_list, single-message)

## Handwritten parsers

`AvatarPromoteReqParser` now uses the generated 7.2 descriptor: CmdId 6528, `uint64 guid = 3`. Empty or malformed input still returns 0. Backup-team deletion also uses its generated descriptor, with `backup_avatar_team_id = 5`; the obsolete manual field-4 preference was removed. Other handwritten wire code remains subject to review.

## Handwritten wire code still on 7.1 numbers

These build or read protobuf bytes by hand with 7.1 field numbers or CmdIds and need checking against 7.2 captures:

- `emu/grasscutter/game/entity/GameEntity.java`
- `emu/grasscutter/game/inventory/ReliquaryProtoCompat.java`
- `emu/grasscutter/game/player/BeyondProfilePictureWire.java`
- `emu/grasscutter/game/player/PushTipsSuppressHelper.java`
- `emu/grasscutter/net/proto/AvatarExtraLevelUpgradeReqParser.java`
- `emu/grasscutter/server/packet/recv/HandlerBattlePassSetRewardPlanReq70.java`
- `emu/grasscutter/server/packet/send/PacketBeyondCreateHallRsp.java`
- `emu/grasscutter/server/packet/send/PacketBeyondFull.java`
- `emu/grasscutter/server/packet/send/PacketBeyondPlayerDetailRsp.java`
- `emu/grasscutter/server/packet/send/PacketBeyondProfilePictureDataNotify.java`
- `emu/grasscutter/server/packet/send/PacketDoGachaRsp.java`
- `emu/grasscutter/server/packet/send/PacketGetBeyondPlayerInfoRsp.java`
- `emu/grasscutter/server/packet/send/PacketGetProfilePictureDataRsp.java`
- `emu/grasscutter/server/packet/send/PacketListProbe.java`
- `emu/grasscutter/server/packet/send/PacketPlayerNicknameNotify.java`
- `emu/grasscutter/server/packet/send/PacketSeeMonsterRsp.java`
- `emu/grasscutter/server/packet/send/PacketSetPlayerBornDataRsp.java`
- `emu/grasscutter/server/packet/send/PacketTeamHexenzirkelChangeNotify.java`
- `emu/grasscutter/server/packet/send/PacketTowerCurLevelRecordChangeNotify.java`
- `emu/grasscutter/server/packet/send/PacketWindSeedClientNotify.java`
- `emu/grasscutter/utils/ProtoWire.java`
