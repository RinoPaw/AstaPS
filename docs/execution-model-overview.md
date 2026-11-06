# AstaPS 整体执行模型与迁移路线

> 2026-11-06。本文是整体执行架构的主视图，停止继续以零碎异常场景逐项增加架构决议。研究 fork：RinoPaw/AstaPS/rino/refactor/jline4-shell。上游现行基线：MeChen618/AstaPS/main@a49f51aa6f2787ffbc25cee46801b70dee860d23（7.2 WIP）。7.1 旧架构基线：ac9a7e6。原版证据：RinoPaw/Genshin-Reverse/main@0b4596ed（Global 7.1 客户端）。
>
> **状态：工程设计研究，尚未修改 Java 或运行 CI。** 用户已批准 R2/S2、E1、P1、F1、Q1、X1、S-F、W-H、G-C、QR3、C1 等行为边界，参见 [全局研究入口](server-architecture-research.md)。本文不设定新架构选项代号，不授权具体队列、线程池、类名或兼容层。

## 1. 根本原则

**谁拥有可变业务状态，谁在自己的串行执行域内修改该状态。入口可以不同，业务拥有者只有一个。** 这解决了 CLI、协议、Quest、插件、Lua 同时修改同一份游戏状态的问题，并使不同玩家和不同世界可以并行。

- **玩家 UID（R2）**：Inventory、Quest、成长、长期装备关系、玩家持久资料及必要恢复信息。一个 UID 内有明确执行顺序，不要求一个 UID 独占一个 OS 线程。
- **World 实例（S2）**：Scene、实体、战斗实时属性、Lua、场景组。一个 World 内单一串行修改；不同 World 可并行，不要求 World 和 UID 用同一个执行器。
- **游戏入口**：Network、JLine/Picocli CLI、Web、插件、定时器负责解析、权限、路由和结果展示。业务入口本身没有随意修改别人的 UID/World 状态的权利。
- **数据库 I/O**：按 P1 处理经过业务域确认的写入，不在数据库 writer 重跑游戏业务规则。
- **跨域**：World/UID 以请求和实际结果异步交接；双方均不在自身业务执行域同步阻塞等待另一域。continuation 重新进入自己的所有者域才有写入权。

所有权是逻辑约束，和 Java package、线程池大小、模块数量、物理线程一一映射无关。GameServer 负责协调、生命周期、Tick 调度和全局观察，不能继续作为全服所有 World/Player 游戏 Tick 的串行执行者。

## 2. 原版、上游、fork 对照

### 原版 Global 7.1 可直接证实的内容

[Genshin-Reverse 的 7.1 native Quest 提取](https://github.com/RinoPaw/Genshin-Reverse/blob/main/versions/7.1.0-global/windows-x64/analyses/quest-extraction/README.md) 完整解码 4,417 份 MainQuest payload，证实普通任务的 finishExec/failExec 数组以及相关场景组、任务等动作配置；[传送点原版协议证据](https://github.com/RinoPaw/Genshin-Reverse/blob/main/docs/case-studies/unlock-trans-point-7.1.md) 提供原版客户端请求和通知的协议验证。**这些属于客户端资源、协议与 native 证据。原版官方服务端怎样调度线程、处理 Exec 失败、在 World/UID 之间传回成功回执，目前无直接证据**。不能将 AstaPS 的既有实现或本文设计称作官方实现。

### 最新上游的执行现状

| 上游路径 | 已核实的机制 | 与目标的距离 |
| --- | --- | --- |
| [GameSessionManager](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameSessionManager.java) | 所有 KCP 会话收包进入一个 Netty DefaultEventLoop，GameServerPacketHandler 后续可进入业务 Handler | 网络入口依旧耦合业务修改，全局逻辑线程存在瓶颈 |
| [GameServer](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameServer.java) | 同步 onTick 顺序调用各 World.onTick、各 Player.onTick 和公共 ServerTaskScheduler.runTasks，由一个 Timer 驱动 | 没有实现 World 实例、UID 的独立并行 |
| [CommandMap / GiveCommand](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/command/CommandMap.java) | Picocli + JLine 已成熟；GiveCommand 的 threading=true 将工作提交至通用线程池并直接修改 Inventory | 命令是否属于通用线程池与其状态归属无关 |
| [Inventory](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/inventory/Inventory.java) | 已有 InventoryAddResult、ALL_OR_NOTHING、BEST_EFFORT、支付预检和局部 synchronized | 可保留成熟库存规则，但和 Quest/Avatar/玩家 Tick 没有同 UID 统一执行顺序 |
| [QuestManager / QuestSystem](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/QuestManager.java) | 全服静态单线程 Quest eventExecutor，容量 1000、CallerRunsPolicy；Exec false 未从 QuestSystem 传播给调用者 | 全服 Quest 共用一个串行瓶颈；拥塞时 CallerRunsPolicy 还可能在调用方线程执行 |
| [World / Scene](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/World.java) | World 有另一个静态四线程 eventExecutor，Scene 自己有 scheduler；SceneScriptInitGate 已用 CompletableFuture 完成初始化信号 | 存在线程池和 Future，但没有统一的状态唯一写者和真实业务结果 |
| [DatabaseHelper](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/database/DatabaseHelper.java) | 四类 bounded writer pool：default/account/item/group；写入准入、背压、合并已存在 | 复用现有 writer，明确 UID 内存修改的先后与写入确认 |
| [GameServer.onServerShutdown](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameServer.java) | 关闭新连接，停止 Timer，借全局逻辑线程登出玩家，再保存 World、关闭线程池 | 切换所有权模型后，需要统一停机时序及业务排空 |

对比了 fork 与现行上游六个关键 Java 文件（GameServer、GameSessionManager、CommandMap、GiveCommand、Inventory、QuestManager），**blob SHA 全部一致**。因此应改进共同架构，无需为 fork 臆造独立执行机制。

**版本风险**：上游 [README](https://github.com/MeChen618/AstaPS/blob/main/README.md) 已明确 7.2.0 是 WIP，部分 7.1 功能尚未恢复；最新 [build.gradle](https://github.com/MeChen618/AstaPS/blob/main/build.gradle) 标记 7.2.0，仍使用 Java 21。当前分支继续以 7.1 为目标，不擅自把 7.2 协议、资源和 Jar 名字带进来。架构可以跟踪上游最新实现，版本升级属于独立的重大决定。

### 同类服务端参考

[Grasscutters/Grasscutter development](https://github.com/Grasscutters/Grasscutter/tree/development/src/main/java/emu/grasscutter/game/quest) 同样存在 Quest 执行器直接操作 Scene 和玩家记录的历史结构，说明该耦合是继承的问题，不能证明正确。

[Minecraft Folia 的区域所有权](https://docs.papermc.io/folia/reference/overview/) 和 [多种 Scheduler](https://docs.papermc.io/paper/dev/folia-support/) 证明一个重要工程模式：数据只允许所属执行区域修改；不同区域可以借共享线程池并行；跨区域业务通过正确的调度 API 交接。AstaPS 无需复制 Chunk 区域拆分、Folia 的插件兼容机制或整套 Regionizer，因为已确定的 World/UID 所有权足以作为第一阶段边界。

## 3. 一份统一的业务执行合同

业务输入先识别目标、鉴权，再路由至拥有状态的 UID/World 执行域；业务结果返回给请求来源，由来源负责显示或发送协议包。

1. **操作身份**：属于哪个 UID/World、请求来源、目标业务对象及已授权的权限。随异步操作流转。执行前核查是否仍有权访问目标；不能因最初持有一个 Player 引用就永久获得写权限。
2. **顺序**：同一 UID 的 Give、Inventory、Quest 和个人 Tick 由同一所有者排序；同一 World 的场景、实体、Lua、战斗与 World Tick 由自己的所有者排序。允许独立 UID/World 并行。工作线程可复用，不要求每一所有者一个线程。
3. **入口共用**：CLI/Picocli、网络 Handler、Web、插件只调用对应的游戏业务能力。JLine Shell 的输入/完成/历史记录与业务状态的写入调度分离。不能依靠旧 @Command.threading 标志表达所有权。
4. **异步跨域**：例：UID Quest 发起动态组请求 → World 实际加载，返回具体 suite 或失败 → 回 UID 域确认任务上下文、更新依赖的 QuestGroupSuites → 通知本次业务结果。World/UID 域内禁止阻塞互等。若只是独立 World 后置 Lua，遵循 Q1，无需一律纳入此成功屏障。
5. **真正结果**：区分已接收、已入队、已执行、实际业务成功、业务拒绝及持久化确认。借用已有 InventoryAddResult 的表达方式；不要把所有业务统一压缩成模糊 boolean，也不预设一个复杂万能 Result 类。F1 Shell 在业务完成后回报。
6. **错误与生命周期**：旧 World/Scene、重复登录、新任务周期、停服排空等属于操作执行和完成结果的有效性契约。尽量复用已有 World/Scene identity 判定，具体情况按第一个纵向用例验证，不再逐项发明架构选项编号。不增加默认回滚、补偿或持久重试账本。
7. **持久化**：R2/S2 完成自有内存更新后提交 P1 写入；数据库线程不能替业务拥有者修改任务或库存。关闭时先拒绝新请求，再收尾已承认的请求、落实必要持久化确认，最后停止业务域/数据库执行器。

这些约束也适用于现阶段 C1 的客户端主导伤害校验、W-H 房主共享场景、X1/S-F 的 Quest 完成以及 QR3 的允许部分奖励。不能通过“重写线程”偷偷改变玩法规则。

## 4. 三条完整业务链，取代几十个小决议

**Give → 验证 UID 执行域**：Console 或游戏内命令解析目标、检查权限 → UID/R2 执行 Inventory.addItems → 返回现有 InventoryAddResult → P1 保存或达到当前合同的准入/确认边界 → F1 回报准确结果。验证同一 UID 的两个并发来源有确定顺序、不同 UID 能并行、库存和 Quest 的个人事件不交叉竞争。优先迁移它，因为业务范围清楚且不涉及 World。

**Quest → 验证领域交互与真实结果**：任务事件进入 UID/R2、更新条件和状态 → 即时个人 Exec 按 X1/S-F 运行、QR3 奖励试发 → 需要 World 结果的混合 Exec 按 G-C 非阻塞往返 → Q1/F1 报真实完成状态。保留原版 7.1 动作数据和上游成熟 Handler；先证明全服静态 Quest 单线程可以安全被 UID 顺序替代，再扩展具体任务。不要为极端跨 World 竞态提前创造一套新事务系统。

**World Tick/战斗 → 验证 S2 并行**：全局时间驱动只发起调度；每个 World 自己顺序驱动 Scene、实体、Lua 和战斗，玩家个人 Tick 交给 R2；跨域状态变化以请求传递。两个不同 World 的慢 Tick 不应互相延误。已有 [GameServerWorldTickConcurrencyTest](https://github.com/MeChen618/AstaPS/blob/main/src/test/java/emu/grasscutter/server/game/GameServerWorldTickConcurrencyTest.java) 和 [WorldSceneTickConcurrencyTest](https://github.com/MeChen618/AstaPS/blob/main/src/test/java/emu/grasscutter/game/world/WorldSceneTickConcurrencyTest.java) 检验部分锁序、快照，不等于实现了 S2；后续应增加所有权和跨 World 并行断言。

## 5. 迁移节奏及验收

**阶段一：打通 UID 业务纵切片**。设计最少的所有者提交/结果接口，保留 Picocli/JLine、Inventory 与现有 DB writer，以 Give 验证完整路径；测试两入口同 UID 并发、两个 UID 并行、权限与在线身份、拒绝及数据库背压。未经批准不一次性迁走所有 CommandHandler。

**阶段二：Quest 纳入 UID 入口和跨域结果**。去除全服 Quest 单线程对业务状态的隐含权威，复用现有任务条件、执行器和原版资源；实现 Q1/X1/S-F/QR3/G-C 的业务结果合同；验证 Success→Failure→Success 只执行前两项、已有副作用不回滚、World 真实失败不能写 UID 成功记录。

**阶段三：World Tick 和 Scene 全部归 S2**。先审计全球 Tick 的计时、调度器、玩家 Tick 和 World 销毁，再将 World Tick 独立调度，逐步处理 Scene 脚本、战斗和消息发送的跨域调用。保留 C1。

**阶段四：网络/插件/Web/关服适配与观测**。把未迁移的外部入口导向共享业务能力；统计每个所有者的排队、执行、拒绝和持久化状态；验证登录、登出、切换 World 和停服的排空/保存。

**CI：** 文档和无 Java 修改的研究不要求每次运行 CI；任何要求用户测试的代码以及提交给上游前必须完成相关 CI。用户测试时提供 PowerShell 7 中包含远端 fetch、放弃本地状态、切换分支、编译的完整命令。7.1 构建产物名称仍为 grasscutter-7.1.0.jar。

## 6. 什么时候再请用户决定

只在改变既定业务规则、技术路线会造成**全局且实质性的权衡**时集中请用户决定，例如未来 7.2 升级、把 World 进一步分片、改变插件能力边界、从 C1 切换 C2，或选择确有必要的独立构建模块边界。局部代码返回值、某个延迟回调是否过期、何时检查 Scene 对象身份、是否使用 CompletableFuture 等优先作为实现和测试问题处理。

**后续直接从阶段一的 Give 纵切片建立执行域契约，避免再次陷入单个 Quest 回执的零碎讨论。**