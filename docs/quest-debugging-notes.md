# 任务链调试经验：351 → 352

更新：2026-10-04。依据 2026-10-03 的运行日志；最新样本 UID 70583。
代码版本：[`9d61066`](https://github.com/RinoPaw/AstaPS/commit/9d61066bde12722d5602cdf17f29331e33eab6c3)，分支 `fix/quest-351-352-handoff`，基于 `play/rino`。
本页是线上排查记录，不依赖本地日志或工作目录。只摘录相关事件，不上传原始日志中的登录凭据。

## 本次验证结果与边界

351 → 352 的交接已经由运行日志验证。35200 至 35205 均出现完成记录，但 35203 中途发生一次配置指定的回退。
因此可以确认主线已经继续推进，不能据此宣布两段任务的表现、最终奖励、存档和后续 353 全部正确。

| 时间 | 事件 | 可确认的结果 |
| --- | --- | --- |
| 23:42:34 | 资源摘要：35102 finishParent=true，next=[352]，35200 acceptCond=[STATE_EQUAL(0,3)] | 实际加载资源与预期一致 |
| 23:48:41–42 | 35102 完成、351 main-finish、handoff unlinked=true canStart=true | 351.finish() 的交接执行；35200 进入 UNFINISHED |
| 23:48:54–57 | 35200、35201 完成 | 自动推进到 35202 |
| 23:49:21 | talk 35216 accepted=true；35202 完成 | 对话接收成功，启动 35203 |
| 23:49:45 | 客户端 NOT_FINISH_PLOT(35203)，fail-condition progress=[1,0] | 35203 FAILED，回退并重新启动 35202 |
| 23:49:45 | 同参数 NOT_FINISH_PLOT 再次收到 | 未观察到第二次 fail/rewind，不能把重复收包计为第二次回退 |
| 23:49:57 | talk 35216 再次 accepted=true | 回退后的派蒙对话完成，重新启动 35203 |
| 23:50:42–54 | 35203、35204 完成 | 推进到 35205 |
| 23:51:24 | 客户端 FINISH_PLOT(35205)，随后 35205 FINISHED | 日志到此结束，证明到子任务状态更新为止 |

两次 talk 35216 的 `npc_entity_id=0`，使用 `entity_id=116393015` 回退字段；服务端查不到对应实体，日志为 `npcId=null configId=null accepted=true`。这与客户端本地演员兼容路径一致。**这次派蒙对话重来是 35203 回退导致，并非 NPC 校验拒收。**

本次没有收到 `FINISH_PLOT(35203)` 的诊断记录。资源还允许 trigger 1172 完成该步骤，因此单凭 `35203 FINISHED` 不能证明客户端过场完整播放；具体命中的完成条件仍待核对。

35205 的完成日志位于后续流程之前。日志没有提供主任务 352 完成、finishExec 解锁、奖励发放、保存成功或 353 接取的完整证据，后续验证需分别检查。

## 已定位并修复的问题

详细资源版本、代码路径和复现见 [351 → 352 专项记录](quest-351-352-handoff.md)。

1. **登录兼容逻辑抢先完成真实任务。** `PlayerProgressManager.onPlayerLogin()` 在任务登录恢复之后创建并标记 35205 完成，生成“开头未开始、末尾已完成”的父任务。交接日志明确是 `unlinked=true canStart=false`，不能归咎于 351.finish() 没执行。
   修复将这段神像兼容逻辑限制在 questing 关闭时执行；开启任务系统时由任务链管理 35205。正常 rewind 可以清除旧存档的人工末尾完成，后续兼容逻辑不再重新写入。
2. **交接保护与恢复不足。** 允许所有子任务均 UNSTARTED 的现有父任务启动；仍保护已开始、已完成或失败的任务。登录恢复只补交接，不重复调用父任务 finish()，避免重复奖励。
3. **无前置入口判断必须包含状态。** 35200 的唯一条件是 `QUEST_COND_STATE_EQUAL [0,3]`；本资源包 `opensUnlinked(352)=true`。不能把任意 quest 0 条件都当作无前置入口：状态 0 为 NONE，1 为 UNSTARTED，3 为 FINISHED；检查“任务不存在”与“任务完成”语义不同。
4. **NPC 身份与场景放置编号混用。** TalkManager 原先将 TalkConfigData.npcId 与实体 configId 比较。修复使用 EntityNPC 的 NPC/model 身份 getEntityTypeId()，优先取请求 npc_entity_id，零值时回退 entity_id，并保留客户端本地演员支持。这是独立代码缺陷，最新样本没有证明它是此前重复对话的原因。

本次记录不新增任务行为修改。之前本地回归共 13 项通过：交接/登录 10 项，对话身份 3 项；未运行 CI。这些测试不替代客户端剧情表现验证。

## 可复用的排查顺序

1. **先确认正在运行的版本和实际资源路径。** ResourceLoader 先加载 Excel，再加载 BinOutput/Quest。FileUtils 优先 Server 目录，否则 ExcelBinOutput；每个目录内 TSJ > JSON > TSV。这是选文件，不是合并两份 Excel。
   MainQuestData.onLoad() → QuestData.applyFrom() 只补 isRewind、finishParent，不覆盖 acceptCond。必须检查运行时合并后的值，不能只查看某一份 JSON。
2. **沿完整事件链记录证据。** 客户端收包 → 字段与实体解析 → 条件匹配及进度 → 子任务状态 → exec → 父任务完成/奖励/保存 → 后继启动。响应包成功或收到请求不等于任务条件成功；finish 日志也不自动证明后面的 exec、持久化与交接成功。
3. **有断链时找首次分歧。** 35102 未完成先查 trigger 1017；finishParent 错先查资源合并；handoff=false 先查谓词；canStart=false 检查现有父任务的每个子状态，并追踪是谁写入它。不要先放宽所有保护或强制完成。
4. **把重复对话与回退分开。** 当前资源 35202 由 COMPLETE_TALK(35216) 完成；35203 的 NOT_FINISH_PLOT(35203) 或 TEAM_DEAD 可触发失败，failExec 回退 35202。用 fail-condition → fail → rewind → start 的连续日志确认原因，并区分重复收包和实际重复状态变更。
5. **回退位置与状态不是同一件事。** ExecRollbackQuest 先调用 rewindTo()，之后才检查返回位置是否为空。即使执行器返回 false，也不能推断任务没有回退；需检查状态日志，另查传送和 NPC 重建。
6. **兼容逻辑要有明确适用模式。** 搜索 PlayerProgressManager、登录初始化、神像/区域/角色元素解锁等是否直接写真实任务状态。任务开启时不要由独立兼容路径预完成中间或结尾步骤；不要通过重放 finish() 修复存档。
7. **回归测试要覆盖异常状态的生产者。** 只手工造一个干净 QuestManager 会漏掉真实登录副作用。本次增加执行实际 PlayerProgressManager 登录逻辑的回归，同时验证旧异常状态恢复、恢复幂等和 questing 关闭模式。
8. **参考项目是线索，仍需对照本资源和版本。** LunaGC/HunkyMeow 的 suggest 交接存在注释实现，不能当作已经解决本问题的证据；已检查的 FinishPlot/NotFinishPlot 实现也没有提供可直接复制的修复。逆向参考见 [Genshin-Reverse 7.1 对话链记录](https://github.com/RinoPaw/Genshin-Reverse/blob/main/versions/7.1.0-global/windows-x64/analyses/statue-unlock/FINDINGS_2026-10-03_QUEST_TALK_CHAIN.md)，区分协议映射交叉核对与客户端行为的直接证据。

## 待 Playthrough 核对

- **35203 为何上报 NOT_FINISH_PLOT。** 服务端回退符合当前资源；客户端触发原因未定。对照视频检查对话结束、过场开始/退出、输入操作、场景加载和 NPC 出现时间，并与收包时间对应。不要删除 failCond 或强行完成来掩盖原因。
- **35203 第二次的完成来源。** 补看实际命中的 finish 条件，是 FINISH_PLOT 还是 trigger 1172；核对是否跳过应有表现。
- **神像前后细节。** 核对派蒙位置、对白次数、交互/过场顺序、七天神像地图解锁、元素变化、任务奖励和后继 353。
- **存档检查点。** 在 35202、35203、35205 各阶段正常退出并重新登录，核对任务/NPC/位置是否恢复正确，奖励是否只发一次。验证自然推进时不使用 quest add/finish。

## 本样本的其他异常

以下分别列为待查项；本日志未证明它们造成上述 35203 回退。

| 时间/提示 | 已确认现象 | 后续排查方向 |
| --- | --- | --- |
| 23:42:26–37 资源加载；23:42:30 登录异常 | 未加载完成时处理登录，默认角色 10000007 的 AvatarData 为空，回退同一角色后抛 NullPointerException | 检查资源就绪与登录入口时序；避免将未加载当成缺少某个角色 |
| 23:48:31 InvestigationMonsterDropHelper.loadPreviews | JSON 字段缺失，get(...).getAsInt() 抛 NullPointerException | 核对数据格式与必需字段，定位具体行；不要凭此判定主线条件失败 |
| 23:48:46 group 133003090 / config 472 | 场景实体创建失败；已检查的发布脚本请求 472，但该组 monsters 列表无此编号 | 对照实际运行脚本及视频确认预期实体；未确认意图前不替换编号 |
| 加载阶段多处资源提示 | StatuePromote 表缺失、部分表只保留一行、圣遗物权重/商店区域数据不完整、场景块缺失 | 分别核对资源完整性、字段映射及版本；不概括为“全部正常” |
| 23:50:14 MarkPlayerAction(3002,4,1) 未实现 | Lua 调用缺少实现 | 作用尚未确认；没有直接证据认定它引发回退 |

后续发现其他任务问题时，追加“运行版本/资源、首次分歧事件、已确认原因、未确认假设、修改与验证边界”，避免把一次推进成功写成整条任务完全正确。
