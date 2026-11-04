# AstaPS 全局架构审计与重构方向

> 更新：2026-11-04。源码基准：`MeChen618/AstaPS/main@ac9a7e6cec900e4ced28ec5dc16ceda82cf7538d`。研究分支：`RinoPaw/AstaPS/rino/refactor/jline4-shell`。
>
> **已批准目标 A：以整个 AstaPS 的统一、清晰架构为长期重构目标，CLI 与库存为首批切入点，之后逐步覆盖其他业务。**
> **已批准 M2：允许采用 Gradle 多模块工程，按需要划定独立编译、依赖和构建产物边界。已批准 D1：保留 HYBRID、GAME_ONLY、DISPATCH_ONLY 三种运行模式，允许 Game/Dispatch 独立进程，也允许同进程运行。** 仍处于源码审计与设计阶段；尚未批准模块数量、具体模块接口、独立 JAR 数量或开始代码迁移。保持 Java 21，Java 25 作为未来方向；不自建无实际需要的兼容层，不新建分支。

## 0. 当前有效决议与阅读指南（2026-11-04）

**本文件是全局架构决议的入口。** 当前只进行设计与源码审计，Java/Gradle 未迁移。源码证据基于 `MeChen618/AstaPS/main@ac9a7e6`；历史研究章节中“尚未决定”的表述只代表**当时**状态，若有后续正式决议，以此表和相应决议节为准。

| 决议 | 当前生效的边界 | 详细章节 |
| --- | --- | --- |
| **A：整体重构** | CLI、库存为首批验证，逐步覆盖业务；不把 fork 上未合入的历史设计当作兼容约束 | 第 1–6 节 |
| **M2：工程多模块** | 允许 Gradle 多模块、Game/Dispatch 独立编译；**未决定模块清单、可执行 JAR 数量和发布包方案** | 第 8–9 节 |
| **D1：运行形态** | 保留 `HYBRID`、`GAME_ONLY`、`DISPATCH_ONLY`；编译模块数与进程数独立 | 第 8–9 节 |
| **E1：权威业务入口** | 插件、CLI、Web 等外部扩展通过业务能力修改状态；原版场景 Lua 按核心玩法语义处理 | 第 10–11 节 |
| **K1：核心玩法内置** | 7.1 战斗、任务、世界、库存等作为内置玩法；第三方插件可以做高级玩法扩展 | 第 11.4 节 |
| **S2：World 执行域** | **以 World 实例为单位**序列化世界模拟；不同 World 可并行。仅约束 World 自身拥有的状态 | 第 13 节 |
| **R2：Player 执行域** | **以 UID 为单位**序列化玩家个人状态；不同玩家可并行，Inventory 不随当前 World 改变执行归属 | 第 14–16 节 |
| **B1：World 战斗运行态** | 已正式选择：World 权威管理活动角色实时 HP、能量、战斗属性和动态效果；Player 持有等级、天赋、升阶、装备关系等个人长期资料及其必要的持久恢复信息 | 第 18–19 节 |
| **C1：本分支伤害计算策略** | **本重构分支暂时保留客户端计算主要攻击伤害、World 校验并应用**；不在本阶段实施全量服务端伤害公式或自动切换 C2。后续若要改为 C2，须单独决策与原版 7.1 验证 | 第 20.3 节 |
| **Q1：Quest 个人业务完成边界** | UID/R2 完成任务状态、即时个人 Exec 与奖励发放尝试后，才可报告成功；QR3 允许部分奖励，X1 要求即时个人 Exec 全部成功；个人 Exec 失败后不恢复既有状态/副作用（第 31 节）。**原版 7.1 资源/服务端边界见第 32 节**；跨域依赖与全拒绝奖励仍待定 | 第 25–32 节 |
| **X1：即时个人 Exec 必须成功** | 正常 Quest 完成路径的即时个人 FinishExec 必须实际成功，才能报告 Quest 成功；失败如实报告。**S-F：首个即时个人 Exec 失败后停止后续尚未执行的即时个人 Exec**；既有状态/副作用不恢复，不回滚、不自动补偿或重试 | 第 30–34 节 |
| **S-F：即时个人 Exec 失败即停止** | 按本次 Quest 即时个人 FinishExec 的顺序执行；首个未成功的 Exec 后，不再执行剩余即时个人 Exec；Q1/X1 返回失败，已发生状态/副作用原样保留。World 后置、混合依赖、Begin/FailExec 不自动套用 | 第 34 节 |
| **QR3：奖励容许部分发放** | Quest 奖励可部分交付，不强制完整奖励作为任务成功条件。未批准补发、欠账持久化、回滚；实际接受情况与个人 Exec 的失败结果必须区分 | 第 26.3、27 节 |
| **Inventory 操作语义：Remove / Consume** | 已批准**区分清理与消耗**：删除/清理以目标已不存在为成功条件、可幂等；扣除指定数量采用 `consume` 一类显式操作，数量不足应能表达业务拒绝。**具体 API 名称、签名、迁移策略未批准** | 第 29 节 |
| **持久化 P1** | R2 内完整同步修改玩家内存；同 UID 异步有序写入；经济操作先获得写入准入；奖励回执保留同步持久确认例外 | [CLI/库存第 7、9 节](cli-jline4-shell-research.md) |
| **CLI/Give D01B 与 F1** | 直接在调用线程修改 Inventory 的旧规则被 R2 覆盖；**F1 已决定 Shell 等待真实业务完成再产生退出状态**，只允许外部交互线程等待，游戏执行域间禁止阻塞等待 | [CLI/库存第 4.1、10.2、11 节](cli-jline4-shell-research.md) |

**按主题阅读**：第 1–5 节工程现状与原则 → 第 6–7 节推进次序及未决边界 → 第 8–11 节模块化与插件研究 → 第 12 节历史线程审计 → 第 13–34 节 S2/R2、跨域业务、F1、B1/C1、实体生命周期、跨 World 隔离及任务调度归属审计。具体 Give、InventoryItem、TPS、数据库写入与登录竞态以 [CLI/库存研究](cli-jline4-shell-research.md) 为主，避免两份文档维护重复的细粒度决定。

**仍未决定**：B1 下个人成长来源向 World 实时战斗态同步、动态效果与可持久恢复数据边界；第 21–34 节识别的退出保存缺口、跨 World EntityId 索引、S2/R2 任务调度归属、异步执行边界及 Tick 时间单位不一致的具体修正技术；**C1 已作为当前分支的阶段性策略选定，C2 未获批准（见第 20.3 节）**、Player/World 非阻塞协调、会话网络进出顺序；F1 以外的 Web/API 等待/超时/查询行为与输出传递技术、执行器和队列具体实现、账号/登录生命周期接入、模块边界与发布产物。**F1 Shell 等待真实业务结果、Q1 Quest 个人完成边界、QR3 允许部分奖励、X1 即时个人 Exec 成功前置条件、S-F 失败即停止后续即时个人 Exec，以及个人 Exec 失败后不做状态恢复均已正式选择**。Java 21 不变，Java 25 只是长期方向；未经批准不引入 Actor 库、兼容层或改动既有 JAR 名称。

### 后续研究固定证据顺序（2026-11-04 用户要求）

后续每次推进具体架构/业务设计时，都必须**实际重新检查**：
1. **原版 Global 7.1**：优先引用 `RinoPaw/Genshin-Reverse` 里的同版本 EXE、metadata 或原始资源直接证据；缺乏官方服务端证据时标注“原版服务端行为未验证”，不把私服经验冒充原版；
2. **上游最新 `MeChen618/AstaPS/main`**：检查当时的最新 HEAD、具体实现调用链和已有可复用机制；明确区别于过去某一提交的历史行为；
3. **现有 fork 与已选决议**：核对 `RinoPaw/AstaPS` 当前研究分支及现有 Q1、QR3、X1、R2、S2 等约束，列出实际差异和未经批准的实现选择。

**每次都给出原版行为与上游行为**，包含证据来源、验证程度；只有原版尚未证实时才明确写未知。不要仅依据上一次审计的缓存陈述上游“当前仍然如此”。这种核查规则属于研究流程，不授权自行决定未决技术方案，也不要求每次都修改 Java、运行无意义 CI 或新建分支。

## 1. 先定义系统实际是什么

现有工程是一个 Java/Gradle 服务端程序，有 `HYBRID`、`GAME_ONLY`、`DISPATCH_ONLY` 运行模式，支持游戏 KCP 会话与 HTTP/Dispatch；底层数据使用 MongoDB/Morphia；通过反射注册游戏网络包处理器、配置资源和插件。已经存在大量可复用的成熟领域规则，不应未经核验整体重写。

**全局职责关系（概念视图，并非五个 Gradle 模块的预设）：**

```text
客户端网络 / 终端 CLI / Web Console / 插件 / Lua与定时系统
                  │ 请求、事件、脚本调用
                  ▼
          命令与游戏业务执行
                  │
         ┌────────┴────────┐
         ▼                 ▼
  Player / Avatar      World / Scene
  Inventory / Quest    Entity / Ability
         └────────┬────────┘
                  ▼
       共享资源 / 持久化 / 通知
                  │
        MongoDB / 原版 7.1 协议
```

这一图只用于定位责任。现已确定 **R2 的 Player 个人执行域**及 **S2 的 World 模拟执行域**：二者按实际状态所有权独立管理，跨域业务另行协调，不自动等于两个 Gradle 模块或两条物理线程。Dispatch 的账号与授权也可能有自己的生命周期。

## 2. 已核实的真实启动、注册与执行路径

| 子系统 | 真实现状 | 全局重构应关注的责任 |
| --- | --- | --- |
| `Grasscutter.main()` | 按序创建 `CommandMap`、数据库、鉴权和权限组件、`GameServer`/`HttpServer`、`PluginManager`；随后加载资源、初始化部分配表、启动服务、启用插件、启动控制台 | 初始化依赖、运行模式、关闭顺序应有明确契约；资源就绪前不应误用业务数据 |
| `GameServer` | 构造时直接实例化 Inventory/Gacha/Shop/Quest/WorldData 等十余个 System，自己管理在线玩家、World 集合、Tick 和 Session 生命周期 | 主机运行环境、系统注册与具体业务执行的边界；避免新业务继续堆进总控 |
| `GameServerPacketHandler` | 注册包 Handler、校验会话状态、调用插件 `ReceivePacketEvent`，然后直接调用 Handler；若无 Handler，部分业务还有 fallback 处理 | 客户端协议应是输入入口；重构时检查业务直接写入、例外处理和授权位置 |
| `GameSessionManager` / `GameServer.onTick()` | 收包进入 Netty `DefaultEventLoop`；Tick 由另一 `Timer` 调用；登录读取在专门线程池，部分 GM 操作走通用线程池 | 各业务状态的修改归属；会话、玩家、世界三个生命周期的关系 |
| `CommandMap` | 自行完成输入、目标、权限、线程投递与 Picocli 执行，部分操作查询数据库与服务器在线状态 | JLine Shell 接入后应沿业务能力边界运行，不把业务逻辑复制到 CLI |
| `PluginManager` / `Event.call()` | 插件按事件类注册并同步回调；`Event.call()` 通过全局 `Grasscutter.getPluginManager()` 分发 | 插件在何时可以观察、取消或改变业务操作；扩展合同影响所有子系统 |
| `ResourceLoader` / `GameData` | 使用反射资源定义、静态数据表及加载顺序；`GameServer` 部分系统在资源完成加载前已经构建，后续在主入口重新初始化部分数据 | 原版 7.1 数据和服务运行态的生命周期应分清 |
| `DatabaseManager` / `DatabaseHelper` | 游戏和账号 datastore 随运行模式选取；Player/Avatar/Item 等各自直接保存；数据库写入队列独立处理 | 数据归属和生命周期与内存业务边界协调，保留已批准 P1 和玩家级顺序原则 |

**关键源码**：
[Grasscutter.java](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/Grasscutter.java) ·
[GameServer.java](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameServer.java) ·
[GameServerPacketHandler.java](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameServerPacketHandler.java) ·
[GameSessionManager.java](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameSessionManager.java) ·
[PluginManager.java](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/plugin/PluginManager.java) ·
[DatabaseManager.java](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/database/DatabaseManager.java)。

## 3. 目前最重要的结构性耦合

### 3.1 入口、业务和副作用相互穿透

- `Inventory` 会改变持有物品、调用 `item.save()`、直接向 Player 发 `PacketStoreItemChangeNotify` 并触发 `PlayerObtainItemEvent`。
- `Avatar` 在装备操作中同时调用保存和 `PacketAvatarEquipChangeNotify`；`QuestManager` 修改状态时会保存并直接发送任务通知。
- `CommandMap` 解析目标时直接访问 `GameServer` 和 `DatabaseHelper`；`Event.call()` 获取全局 PluginManager。

这表示修改一次业务状态的执行时机、失败语义、通知时机很难在一个位置全局控制。**先按实际业务操作划归主导者，再检查哪些副作用需要统一执行时序**；不预设创建一个万能服务、额外适配层或通用事务框架。

### 3.2 状态归属跨越多个生命周期

- `Player` 的 Morphia 持久数据和会话变量混合，内部构造大量 Player Manager，并保存库存、任务、外观、体力、TPS 等多类状态。
- `World` 持有 Players/Scenes，`Scene` 持有实体、脚本、刷新与挑战；运行时的关系并非单向简单树。
- 普通库存装备、试用装备、TPS loadout、世界实体及持久账户分别有不同生存期；此前已经决定普通装备关系归属物品、TPS 保持专有关系权威。

**建议先定义权威归属（谁拥有状态、谁有权修改、何时创建/失效、如何观察）**，之后再决定是否要更改类结构和持久化映射。不建议先把 `Player` 机械拆成几十个服务。

### 3.3 线程、数据库和插件是横切的架构约束

- 当前网络、Tick、命令与脚本/定时任务可以进入同一玩家或世界的修改路径，线程来源本身不提供完整业务互斥。
- `DatabaseWriterManager` 的写入顺序与内存中的业务执行不同域；已定玩家级异步串行持久化及 P1 拒绝不可接纳经济操作。
- 插件事件可取消或修改预期操作。任何未来的统一操作时序，都必须回答扩展点何时运行、谁复验以及成功何时对外可见。

这三者应作为**整个服务端的共同设计约束**，但各业务的实际状态归属仍由玩家、世界或独立服务决定。

## 4. 从同类项目得到的结构性参考

- [Minestom `ServerProcess`](https://github.com/Minestom/Minestom/blob/master/src/main/java/net/minestom/server/ServerProcess.java) / [`MinecraftServer`](https://github.com/Minestom/Minestom/blob/master/src/main/java/net/minestom/server/MinecraftServer.java)：可研究服务器进程、全局注册、管理器与对外调用接口的边界。它也暴露静态访问入口，不能把“完全无全局访问”当成项目成熟的必要条件。
- [Minestom `PacketListenerManager`](https://github.com/Minestom/Minestom/blob/master/src/main/java/net/minestom/server/listener/manager/PacketListenerManager.java)：网络包注册与转交的入口可以有单独的责任；不等于其内部完全脱离状态/业务。
- [LunarCore `GameServer`](https://github.com/Melledy/LunarCore/blob/main/src/main/java/emu/lunarcore/server/game/GameServer.java) / [`InventoryService`](https://github.com/Melledy/LunarCore/blob/main/src/main/java/emu/lunarcore/game/inventory/InventoryService.java)：服务端注册与库存业务编排有现成划分。但库存服务仍覆盖多类业务，不能只照搬类名。
- [Grasscutter development](https://github.com/Grasscutters/Grasscutter/tree/development)：用于追溯上游继承结构和已有 API；本项目的实际代码和 7.1 行为才是改造依据。

这些项目提供**可验证的取舍案例**，没有一个是 AstaPS 应当整套复制的架构。

## 5. 目标 A 的研究准则（已确定范围，具体设计未批准）

- **一个业务能力只有一个权威执行路径**：协议 Handler、JLine CLI、Web、插件和脚本进入对应业务；避免多个入口各自实现强化、给物品、改变任务等完整流程。
- **每类可变状态具有明确的所有者和生命周期**：至少区分账号/鉴权、玩家会话、玩家持久资料、World/Scene/Entity 与静态 7.1 数据。多人行为不能被玩家单体模型吞掉。
- **业务能明确表达完成、失败及可见结果**：谁校验、谁修改、谁通知、谁保存要能追踪；游戏业务同步、数据库 I/O 异步及 P1 规则继续有效。
- **扩展接口有明确时序和授权**：插件、命令与 Lua 应保留必要能力，是否破坏性升级 API、脚本权限、事件重定义均需独立决策。
- **工程级模块化（M2）**：允许 Gradle 多项目组织、按职责拆分源码与模块依赖，并允许独立构建产物；最终模块数量、模块间 API、JAR 数量仍需依照实际依赖设计。现阶段保留 Java 21、现有构建产物命名 `grasscutter-7.1.0.jar`，未经授权不改名、不新增兼容层。
- **运行形态（D1）**：必须保留 `HYBRID`、`GAME_ONLY`、`DISPATCH_ONLY`；Game 和 Dispatch 可各自运行，也可以同进程联合运行。Gradle 模块划分不自动决定进程边界，不强制新增进程间通信协议。

以上是判定未来方案优劣的准则。**尚未批准具体包结构、模块数、依赖注入框架、事件总线、Actor、统一 Scheduler 或数据库集合方案。**

## 6. 全局研究/实施的合理推进次序

1. **架构地图与治理决议**：已选 M2（工程级模块化）和 D1（三种运行模式）；进一步核对 Game/Dispatch 的共享编译依赖、账号与鉴权边界、插件/脚本生存期，识别实际切分点，暂不锁定模块清单。
2. **通用执行语义**：对客户端、CLI、Web、插件、Lua 和 Tick 的业务调用能力与可见副作用建立共同约束；识别玩家和 World/Scene 两类重要并发域。这是跨业务的规则，不要求立刻实现一套通用引擎。
3. **首批垂直切片**：利用 JLine 4 Shell 的全新入口与库存/装备业务来验证架构，端到端覆盖授权、执行、状态修改、通知、存储、重连；现有 CLI 研究决议继续生效，必要时与全局方向对齐。
4. **按领域逐步推广**：任务、奖励、商店、抽卡、战斗、世界与脚本按实际收益和依赖次序重构；每批必须交代可运行范围和删除的旧路径。保持主线为 `MeChen618/AstaPS/main`，不要创建孤立分支。
5. **验证和上游提交**：文档研究期间不用无意义 CI；开始代码后运行直接相关测试，要求用户真机测试前及提交上游前完成相关 CI。

## 7. 当前仍需决定的架构边界

S2、R2、M2、D1、E1、K1 已正式选择。后续重点是如何实现这些决定，**无需反复重问 Player/World 是否独立、插件是否应经权威业务入口**。

1. **R2/S2 跨域协作、保存屏障及共享战斗态**：拾取、宝箱与世界 Boss 奖励、联机迁移、角色装备影响世界实体时，结果、去重、失败与状态可见性如何协调；**EntityAvatar 与 Avatar 共用可变战斗属性**的现状已在第 18 节证明；**B1 已正式选择**，需落实两侧分离及联动规则。参阅第 15–19 节。
2. **命令/Web/插件反馈**：**F1 已选择 Shell 等待真实业务完成**；Web/API 的等待/超时和查询方式、跨线程输出及插件事件参与的具体技术仍需设计；原 D01B 调用线程直接修改状态的细节已被 R2 覆盖。
3. **玩家生命周期**：登录世代、离线个人操作、退出幂等、每 UID 持久化成功屏障，以及奖励同步 PREPARED 回执怎样不阻塞 World；参阅 [CLI/库存第 9 节](cli-jline4-shell-research.md)。
4. **具体调度与工程结构**：工作线程/队列、Tick 频率、公平性、M2 Gradle 模块、Game/Dispatch 发布产物和插件 API。尚未批准 Actor 框架、每玩家/World 独占线程、具体模块或 JAR 数量。

## 8. 2026-11-03 M2 与 D1：工程模块化、运行模式保留（正式决议）

**决议 M2**：允许把 AstaPS 改为 Gradle 多模块工程，明确模块源码、编译期依赖、单元测试和可选独立构建产物。模块拆分以真实源码依赖和业务生命周期为依据；**没有批准预设 `core/game/dispatch/protocol` 四模块方案，也没有批准所有模块直接对应 JAR 或进程**。

**决议 D1**：必须同时支持以下三种**运行模式**：

| 模式 | 必须保留的能力 |
| --- | --- |
| `HYBRID` | 同一运行进程同时提供 Game 和 Dispatch |
| `GAME_ONLY` | 单独运行 Game，并连接外部 Dispatch |
| `DISPATCH_ONLY` | 单独运行 Dispatch，不启动 Game |

**三个维度分别决策**：

- **Gradle 模块**：编译期源码与依赖边界；M2 已批准可以拆分。
- **运行进程**：Game/Dispatch 可分开，也可同进程；D1 已确认同时保留。
- **可执行发布包**：单个联合 JAR、独立 Game/Dispatch JAR 或组合发布均仍是设计选项，不应从 M2、D1 推出特定数量。迁移阶段既有 `grasscutter-7.1.0.jar` 构建行为和命名继续保持，若未来调整发布策略需另行决定。

**核对当前实现**：`settings.gradle` 只有 `rootProject.name = 'Grasscutter'`；`build.gradle` 的 `application.mainClass` 是 `emu.grasscutter.Grasscutter`，根工程 `jar` 打包并设置 `archiveBaseName = 'grasscutter'`。`Grasscutter.main()` 根据 `ServerRunMode` 选择初始化/启动 `GameServer` 和 `HttpServer`。这为 D1 提供现有行为基准，但目前各模块的编译依赖仍缠绕在同一工程中。

**后续研究的最高优先级**：梳理 Game、Dispatch、账号与认证、协议与资源、CLI/插件之间的真实编译依赖，找出循环依赖与共享运行环境。先决定哪些责任应独立编译、哪些应由 HYBRID 组装，最后再决定具体模块和产物。整个过程中维持既有三种可运行入口，不批准额外进程框架或旧兼容逻辑。

本轮文档决议不启动 Java 代码重构，也不要求运行 CI。

## 9. 2026-11-03 全局模块依赖审计（M2/D1 后续研究）

研究方式：直接检查 `MeChen618/AstaPS/main@ac9a7e6` 的主启动器、Game/Dispatch、HTTP、认证、账号数据库、CLI、插件、数据加载与脚本代码，以及 `settings.gradle`、`build.gradle` 和 `gradle/protocol-generation.gradle`。下表是**已在具体类中确认的编译依赖与运行调用**，尚未完成全项目自动化编译依赖图；不能据此声称已经找出了所有强连通分量。

### 9.1 当前源码中真实存在的依赖环

| 依赖关系 | 直接证据 | 对 M2 的影响 |
| --- | --- | --- |
| Game ↔ Dispatch 链接 | `GameServer` 构造 `DispatchClient`；`DispatchClient` 直接引用 `GameServer`、`Grasscutter.getGameServer()`，并负责玩家字段和抽卡历史查询 | 当前不能按 `server/game` 和 `server/dispatch` 两个目录原样建立彼此独立的 Gradle 模块。应分清 Game 侧连接能力、Dispatch 服务端及请求业务处理的归属 |
| Dispatch 链接 → HTTP 页面 → Game | `DispatchClient.fetchGachaHistory()` 调用 `GachaHandler.fetchGachaRecords()`；HTTP `GachaHandler` 接受 `Player` 并读 `DatabaseHelper`；`gachaDetails()` 从 `GameServer` 取卡池 | 通用的 Game/Dispatch 通信层目前直接依赖 Web 页面处理器的业务方法；需要明确抽卡历史查询能力的业务所有者，避免让通讯模块依赖 HTTP 页面 |
| 账号认证 → 游戏账号/HTTP 框架 | `AuthenticationSystem` API 使用 `game.Account`、HTTP JSON 对象及 Javalin `Context`；`DispatchServer` 直接使用 `DatabaseHelper.getAccountById()` | 当前账号/认证接口与 HTTP 表现形式、游戏包名和具体数据访问耦合；是否成为独立模块须先明确账号的归属和共享接口 |
| 数据资源 ↔ 游戏/脚本 | `GameData` 引用 `game.quest`、`game.world` 的部分类型；`ResourceLoader` 引用 Activity、Quest、World 和 Scripts；`Player`、`SceneScriptManager` 反过来依赖 `GameData` | `data/` 目录不能原样整体独立为一个单向底层模块；需要分离静态 7.1 数据与运行时加载/业务解释职责 |
| 数据库 ↔ 玩家/道具等领域类型 | `DatabaseHelper` import `Player`、`Avatar`、`GameItem`、`SceneGroupInstance` 等；`Player`、`Inventory` 和场景脚本反过来调用 `DatabaseHelper` | 目前不存在可以单独抽出的完整纯 `database` 模块。Mongo/写入基础能力与依赖具体领域实体的存取逻辑应分别审视，不能先强制创建万能 Repository 层 |
| 插件 API ↔ 服务器内部 | `plugin.api.ServerHelper` 同时暴露 `GameServer`、`HttpServer`、`CommandMap`、`Player`、鉴权和调度器；`Player` 引用 `plugin.api.PlayerHook` | 插件 ABI/API 是全局模块依赖的主要外露边界。若想独立编译插件 API，必须决定是否继续暴露内部可变对象；不能先迁移包路径后再处理 |
| CLI/Web Console → 游戏运行态 | `CommandMap` 直接读取 `GameServer` / `DatabaseHelper` 的 Player 与 Account；`WebConsoleHandler` 直接调用 `Grasscutter.getCommandMap()` | 命令解析、执行授权、玩家查询和业务操作混在入口中。JLine 4 Shell 是实际验证新边界的优先切片 |

以上“环”是源码/类层面的实际依赖或跨子系统回指，**不代表这些目录已经是编译模块**。按包名直接搬文件会导致编译失败；应先确定业务能力及对外接口。

### 9.2 启动和构建也有独立的跨模块边界

- `Grasscutter.main()` 用 `ServerRunMode` 选择 GameServer / HttpServer 构造与启动，并创建 `ServerHelper`、命令注册和插件管理。**将来支持 D1 的组合逻辑应由可运行入口负责**，不要让 Game 和 Dispatch 通过互相实例化来决定部署方式。
- 现有单工程 `settings.gradle` 尚无 `include` 子项目；`build.gradle` 的 `application.mainClass` 和 `jar` manifest 均指向 `Grasscutter`，根工程 `jar` 将运行时依赖解包为单个可执行 JAR，放置在工程根目录。这是既有 `grasscutter-7.1.0.jar` 行为的基准。
- `gradle/protocol-generation.gradle` 已有独立 `generatedProtocol` `sourceSet`、独立 Java 编译任务和严格 lint；生成源从 7.1 协议描述文件和补充协议重建。**协议生成是有证据的模块化候选，但尚未批准迁移任务**。避免重编译生成的约两千个 Java 源文件，也不能丢失发布包中的生成协议类。
- `Grasscutter.reflector` 以 `emu.grasscutter` 为扫描根，并明确跳过 `net.proto`；资源定义、命令和 Handler 的反射发现依赖类路径。在多模块 JAR 发布时要验证服务注册、扫描范围、资源打包和插件类加载行为。
- Gradle 测试已有无资源包单元测试与需要 MongoDB/游戏数据的集成测试区分。拆分模块后应保持该区分以及根工程的集成检查；不利用 M2 跳过要求测试前/上游提交前的 CI。

### 9.3 从现有事实推导出的组织约束（建议，尚非模块名单）

```text
     Game 可执行入口       Dispatch 可执行入口
            \                 /
             \               /
              HYBRID 组合入口（概念）
                   │ 负责装配和生命周期
                   ▼
       游戏业务         账号/Dispatch 业务
            \             /
             共享且无环的契约或静态数据
                       │
                  7.1 协议生成
```

图中接口、代码模块和可执行入口的实际数量均未确定。关键的依赖规则是：**组合者可以知道具体实现，底层实现不应再反向依赖组合者；Game/Dispatch 互通以明确业务契约和通讯边界为前提**。HYBRID 组合入口属于概念上的运行角色，不预设一定新增一个 Java main class、Gradle 模块或可执行包。

不要无依据地创建 `common`、`core`、`api` 等大杂烩；如果模块必须暴露整个 `GameServer`、`Player` 和 `DatabaseHelper` 才能编译，那么模块边界尚未真正建立。

### 9.4 类似项目的参考点

- [GeyserMC/Geyser `settings.gradle.kts`](https://github.com/GeyserMC/Geyser/blob/master/settings.gradle.kts)：同一多模块工程内区分 `api`、`common`、`core` 与多个独立运行平台的 bootstrap。这验证了“核心能力与部署/宿主组合分离”的技术可行性；AstaPS 不需要复制其模块数量。
- [Minestom/Minestom `settings.gradle.kts`](https://github.com/Minestom/Minestom/blob/master/settings.gradle.kts)：把生成器、测试和主运行体分开组织；可参考构建与工具职责解耦，不能据此推出游戏业务必须拆成许多模块。
- 既有 [LunarCore `GameServer`](https://github.com/Melledy/LunarCore/blob/main/src/main/java/emu/lunarcore/server/game/GameServer.java) 和 [`InventoryService`](https://github.com/Melledy/LunarCore/blob/main/src/main/java/emu/lunarcore/game/inventory/InventoryService.java) 继续作为业务入口与主机注册的比较案例。

### 9.5 下一轮应当解决的全局问题

1. **业务所有权与公开边界**：明确插件、脚本、管理员入口是直接访问 `Player/World` 内部状态，还是通过公开业务能力操作。这会决定哪些模块可独立编译、哪些公共 API 会被长期锁定。
2. **资源和账号的归属**：先整理 7.1 静态数据与运行态的耦合，以及独立 Dispatch 查询玩家资料、历史记录的实际调用链，再确定切分。
3. **选择真正的首批拆分对象**：优先选现有依赖已接近单向且有明确可验证产物的部分，如协议生成；若优先用 CLI/库存验证领域 API，也应先解决其依赖方向。取舍前给出编译与运行证据。
4. **保留 D1 验收**：三个运行模式都必须能正确初始化、联网、认证、关服；保持现有产物名直至发布策略另行批准。

本节未选择新模块名单、插件策略或数据所有权，未改 Java/Gradle 文件，未运行 CI。

## 10. 2026-11-03 E1：扩展通过权威业务入口（正式决议）

**已选 E1**：对外扩展（第三方插件、管理命令、Web 及其他入口）在修改玩家、世界和经济状态时，应使用明确的业务操作契约，由具有状态所有权的业务模块承担权限、有效性检查、同步状态修改、对外通知和已批准的持久化规则。**内部可变集合/字段不作为扩展随意写入的默认接口**。

- 保留可读观察、订阅事件和专用的高级扩展点；必要的复杂玩法可以明确增加业务能力，但不自动增加通用服务层、包装器、DI 框架或事件总线。
- 原版场景 Lua 是游戏规则的重要组成，已经依赖 Scene/World/Entity 深度操作。E1 不代表一律将所有内部 Lua 操作改成通用插件 API；其权限、可变状态执行域和内外部扩展边界需要在之后独立确认。
- GM/JLine Shell 可有管理员专属强操作能力；权限高不意味着可绕开库存 P1、玩家会话归属和持久化不变量。
- Java 插件仍在服务器进程执行，公开业务 API 对守约代码提供架构约束，不能作为恶意代码的 JVM 沙箱。
- E1 规定**未来方向**，并未批准立即删除既有插件接口或承诺兼容老二进制插件。具体插件 API 名称、插件最低版本、ABI 破坏策略、是否提供开发者调试后门和旧接口删除时机均未确定。

### 10.1 为什么现有 LegacyCommandLine 存在：直接历史证据

上游提交 [`47555ab`](https://github.com/MeChen618/AstaPS/commit/47555ab579a3642f6e51164e57e4166ddd888122)（2026-10-11，`fix(command): keep pre-picocli plugins loading`）**明确点名两个运行失败的插件**：

- **AttackModifier**：只实现旧 `CommandHandler.execute(sender,targetPlayer,List<String>)`；新 `createCommandLine` 曾是抽象方法，引起 `AbstractMethodError`。提交新增 `LegacyCommandLine`，把 Picocli 命令行的参数原样转交旧 `execute`，并恢复旧接口默认签名。
- **openworld-plugin**：依赖旧 `CommandHelpers.refineRegex`，触发 `NoSuchFieldError`；同一提交补回公开的旧参数解析辅助成员。仅有该提交确认其名称，尚未核实其公开仓库和支持版本。
- 同期另一提交 [`b2027c2`](https://github.com/MeChen618/AstaPS/commit/b2027c2881043570ece09aa7e3cd4edd56e26dba) 新增 `LegacyCommandSyntax`，目的**另有不同**：让玩家和脚本使用 `heal`、`setStats`、`lv90 x10 c6` 等旧命令/简写时自动转成 Picocli 的规范语法。该语法兼容层服务的是命令输入，不等同于插件 ABI 兼容。

这证实以前恢复旧接口有实际报错与具体对象，**但没有证据表明这些插件需要长期兼容至 AstaPS 7.1 的全局重构完成后**。

### 10.2 当前插件生态和 API 版本的核验状态

- `PluginManager.loadPlugins()` 从配置对应的 `plugins` 目录加载 `.jar` 和 `plugin.json`，运行时插件名单由部署目录决定；仓库没有提交可据以认定用户当前安装了哪些插件的清单。
- 源码 [`PluginManager.API_VERSION`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/plugin/PluginManager.java) **当前是 2**，加载器会拒绝 `plugin.json.api` 与之不同的 JAR。
- [`docs/plugin-command-api-v5.md`](https://github.com/MeChen618/AstaPS/blob/main/docs/plugin-command-api-v5.md) 却写“当前需要 API 5”，且声称旧 `execute` 已删除。**这两处与实际 `PluginManager` / `CommandHandler` 源码矛盾**，未来重构不能以这份文档独立作为真实兼容策略。
- [NotThorny/AttackModifier](https://github.com/NotThorny/AttackModifier) 的源码 `plugin.json.api=2`，`AttackModifierCommand.execute` 仍是旧命令签名。它是旧适配的真实用例，不能据此声称该版本 JAR 已通过 AstaPS 7.1 的完整运行测试。
- [jie65535/gc-opencommand-plugin](https://github.com/jie65535/gc-opencommand-plugin) 也声明 `api=2`，公开对外命令接口；[4Benj/gcgm-plugin](https://github.com/4Benj/gcgm-plugin)、[Coooookies/Grasscutter-MeaNotice](https://github.com/Coooookies/Grasscutter-MeaNotice)、[NotThorny/MobWave](https://github.com/NotThorny/MobWave) 等说明社区扩展涉及远程管理、定时公告和战斗玩法。但这些社区项目的存在不等于其已适配当前 7.1 版本。

### 10.3 E1 对 M2 的实际影响与下一个研究对象

下一步审计面向扩展的**真实业务能力与状态修改入口**，优先涉及 `ServerHelper`、`PlayerHook`、`CommandHandler`、`ScriptLib` 和插件事件。现状下 `Plugin.getServer()` 暴露完整 `GameServer`，`ServerHelper` 暴露 `Player`、`HttpServer`、注册命令和授权组件；`PlayerHook.teleport` 直接修改位置并发协议包。这些 API 不能原样作为严格的 E1 边界。

可研究原生支持的三类扩展要求：**查询/观察、业务指令、明确许可的高级规则扩展**；逐项确定哪些操作需要稳定的扩展合同。先明确能力与权威归属，再选择实际 Gradle 模块、API 类型和插件版本号。旧 `LegacyCommandLine` / `LegacyCommandSyntax` 是否删除仍保留为需要确认的迁移决议，不以已选 E1 自动批准删除。

本轮只记录源码证据及 E1 决议，未修改任何 Java / Gradle 源文件，也未运行 CI。

## 11. 插件架构的全局审计：E1、M2、D1 的交叉点（2026-11-03）

此节继续对公开插件源码和上游 `MeChen618/AstaPS/main@ac9a7e6` 调用点做审计。**E1 已决定作为长期对外扩展原则**；这里的机制与候选规则尚未获准直接修改实现。

### 11.1 已核实的插件生命周期和扩展机制

| 方面 | 实际代码 | 架构含义 |
| --- | --- | --- |
| 加载 | `Grasscutter.main()` 在 Game/HttpServer 创建后构造 `PluginManager`，此时执行 `loadPlugins()` 和各插件 `onLoad()`；之后才加载 Game 资源、启动服务，最后执行 `enablePlugins()` → `onEnable()` | `onLoad` 阶段不保证 7.1 Excel/Scene 资源已完成初始化；扩展的初始化前置条件要有清楚的生命周期 |
| 模式 | `PluginManager` 在三种运行模式都会创建、启用；`ServerHelper` 在 `DISPATCH_ONLY` 下持有 `gameServer == null`，在 `GAME_ONLY` 下持有 `httpServer == null` | 插件接口的功能可用性与 D1 的运行模式直接相关，不能默认所有插件在三模式都具有 Game 与 HTTP 能力 |
| 命令 | `ServerHelper.registerCommand` 经 `CommandMap.registerCommand` 注册，但 `PluginManager.disablePlugin` 仅调用插件自身 `onDisable` 和 `removeListeners` | 没有宿主自动回收插件命令的机制；有插件手动注销、有插件忽略这一环节 |
| HTTP 扩展 | `ServerHelper.addRouter` 经 `HttpServer.addRouter` 直接对 Javalin 注册，`PluginManager.disablePlugin` 不负责回收已添加的 HTTP 路由 | 在实现插件重载/卸载之前需定义注册资源的所有权，不允许假装简单调用 `onDisable` 就完整卸载 |
| 调度 | `ServerHelper.getScheduler()` 暴露 GameServer 的调度器；插件也可自行启动线程/Socket | 宿主不追踪全部由插件创建的任务和外部资源；声明安全热重载前应建立可验证的撤销范围 |
| 事件 | `Event.call()` 获取全局 `PluginManager`，`invokeEvent()` 查精确事件类并在调用者线程上按优先级同步执行监听器；`cancel()` 只对实现 `Cancellable` 的事件生效 | 调用线程、回调异常和业务取消时机尚未形成对外稳定契约；不能只把所有事件定义移动进独立 API 模块便宣称 E1 完成 |
| 类加载 | `loadPlugins()` 收集所有插件 JAR URL 构造一个共享 `URLClassLoader` 来加载插件类，再用每个 JAR 自己的 loader 检索 `plugin.json` 和插件资源 | 当前插件代码在一个共享类加载空间内，不能视为逐插件隔离，更不能认为它提供安全沙箱。M2 多模块构建后还要验证插件的宿主类路径 |
| 版本 | `PluginManager.API_VERSION=2`，加载前按 `plugin.json.api` 精确校验；`docs/plugin-command-api-v5.md` 声称需要 API 5、且旧 `execute` 已删除，源码实际仍保留旧接口 | 文档/源码分裂已经证实。真正升级插件 API 时必须使版本号、文档及自动化验证一致；不预设兼容旧 JAR |

**源码**：[PluginManager](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/plugin/PluginManager.java)、
[Plugin](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/plugin/Plugin.java)、
[ServerHelper](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/plugin/api/ServerHelper.java)、
[EventHandler](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/event/EventHandler.java)、
[Event](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/event/Event.java)、
[CommandMap](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/command/CommandMap.java)。

### 11.2 真实插件对应哪些扩展能力

| 实际社区项目 | 可核实的行为 | E1 需要承认的扩展能力 |
| --- | --- | --- |
| [AttackModifier](https://github.com/NotThorny/AttackModifier) | 监听 `ReceivePacketEvent`，注册 `/attack` / `/at`，生成 `EntityGadget` 并交给 Scene | 高级战斗/网络事件观察、实体生成、命令注册；不适合只提供“给予物品”这类简单业务接口 |
| [MeaNotice](https://github.com/Coooookies/Grasscutter-MeaNotice) | `onEnable` 注册 `meanotice` 命令并启用自己的公告调度；`onDisable` 明确手动注销命令和停止定时公告 | 公告、调度及命令注册的生命周期，应能随插件停用整体回收 |
| [gc-opencommand-plugin](https://github.com/jie65535/gc-opencommand-plugin) | 监听命令反馈及玩家事件，在 `GAME_ONLY` / `DISPATCH_ONLY` / `HYBRID` 走不同路由/Socket 路径，注册 HTTP 管理端点 | 授权的远程命令能力、玩家信息观察、Game/Dispatch 各自的扩展能力及跨服务器通信 |
| [Grasscutters/PluginTemplate](https://github.com/Grasscutters/PluginTemplate) | 示例采用 `Plugin.onLoad/onEnable/onDisable`、事件和命令；模板较旧 | 只能用作继承关系及生态形态参考，不能当作当前 7.1 的可运行插件 API 样板 |

这些仓库说明插件能力涉及玩家、世界、指令、HTTP、事件和定时器，**并不证明以上旧插件 JAR 已能在 AstaPS 7.1 实机运行**。

其中两个可复查的具体例子：
- [AttackModifier.java](https://github.com/NotThorny/AttackModifier/blob/master/src/main/java/thorny/grasscutters/AttackModifier/AttackModifier.java) 的 `onDisable()` 没有手动注销命令，而宿主只自动删除事件监听器。
- [MeaNoticeCore.java](https://github.com/Coooookies/Grasscutter-MeaNotice/blob/master/src/main/java/io/github/gmw/MeaNoticeCore.java) 的 `onDisable()` 会显式 `CommandMap.unregisterCommand`，体现了当前注册资源的回收责任落在插件作者身上。

### 11.3 全局建议：按功能能力及运行上下文建立扩展合同

M2 下，建议先明确插件**希望调用的能力**，再设计实际的公共类型。先将能力分为四组来研究，**此处的四组不代表四个 Gradle 模块或四个新接口类**：

1. **只读观察**：玩家/世界快照、服务器状态、事件观察；数据可见范围和时间点可追踪。
2. **业务操作**：物品、任务、传送、伤害、公告、账户管理等带有校验/通知/持久化语义的操作；命令、插件、Web 复用相同权威路径。
3. **明确的高级玩法扩展**：战斗回调、实体创建、任务脚本、定制算法与系统可接受的内部扩展点；原版 `ScriptLib` / `SceneScriptManager` 不自动被强行包装为普通第三方 API。
4. **宿主管理资源**：命令、事件订阅、HTTP 路由、任务/调度器、外部连接及文件资源；对宿主登记的资源要能够随插件停用撤销。插件自行创建的原生线程或连接的关闭范围，需要单独说明，不能假设宿主可魔法式回收一切。

**D1 环境下的能力可用性**：Game-only 运行环境可访问 Game 业务与实体；Dispatch-only 可访问鉴权/账号/HTTP 能力；HYBRID 同时拥有两者。插件可以声明其实际运行需求，再由宿主明确加载/启用策略。此处推荐“按能力描述运行条件”，尚未确定元数据字段名、插件安装分类、是否允许同一插件在不同模式启动不同部分。

**线程与事件**：E1 的业务操作必须按照所属的玩家、世界或独立服务的执行域运行；是否需要返回值、异步提交、事件前后阶段等，结合现有调用链再定，不能先承诺所有插件回调都在同一线程。事件是否可取消、是否允许写入内容，必须由事件的业务语义逐类约束。

**版本与迁移**：有权进行破坏性插件 API 升级，不自动保留旧二进制 ABI、`LegacyCommandLine`、`LegacyCommandSyntax` 或过时插件版本；删除具体实现时仍需考虑是否存在具有明确业务价值的当前行为。文档中的 API 5 与源码中的 API 2 冲突应在正式定义新 API 版本时一并解决。

### 11.4 K1：原版核心玩法保持内置，插件承担扩展（2026-11-04 正式决议）

**用户选择 K1**：原版 7.1 的战斗、任务、世界、背包以及相关核心游戏规则，作为 AstaPS 自身源码与 Gradle 模块（M2 允许独立编译）的一部分维护；第三方插件通过 E1 业务能力、事件和明确的高级玩法扩展点增加规则、功能和管理能力。

具体含义：
- **核心系统可编译时模块化**。Gradle 的项目拆分、独立测试、依赖方向和部署组装照常推进；**不要求**原版核心系统能够像第三方 JAR 一样在运行中卸载或替换。
- **插件仍可创造复杂玩法**。例如 AttackModifier 所需的战斗观察、实体生成、命令注册应作为扩展能力的需求证据；不能因 K1 而把插件功能限制成简单管理命令。
- **内置场景 Lua 保持游戏规则实现地位**。它通过 ScriptLib 操作 Scene、Entity、Quest 的大量原版逻辑，不需要机械迁移为第三方插件。是否增加自定义 Lua 扩展、脚本沙箱及对外语言 API，分别研究。
- **D1 三模式继续有效**。Game-only 与 Dispatch-only 的插件能力应依运行环境明确；HYBRID 可组合，两种部署方式下核心玩法不必重新作为插件加载。
- **保留选择删除旧 ABI 的权利**。此前为了 AttackModifier 和 openworld-plugin 保留的 LegacyCommandLine、旧 CommandHelpers 等，其历史动机已经得到确认，K1 不意味着要为旧插件维护长期兼容层；E1 的新插件 API 仍需要单独批准具体设计及升级策略。
- **不预设模块名单或接口实现**。是否把战斗、任务、背包分别建设成 Gradle 子项目，要依据源码依赖与可运行性证据；K1 不自动批准对原有 Player、GameServer、DatabaseHelper 或 ResourceLoader 大规模拆分。

K1 明确了**内置玩法与第三方扩展的长期所有权边界**。下一阶段应当优先研究跨玩家/世界的逻辑执行归属及事件与操作的时序，确保 E1 公开的能力有可执行的一致性承诺。是否支持插件热重载可在确有需要时另行决定。

本轮仅研究与记录，无 Java / Gradle 改动，不运行 CI。

## 12. 运行时状态执行域与跨线程业务边界审计（2026-11-04）

**研究结论：现有 AstaPS 没有一个覆盖客户端包、Tick、GM、Lua、事件与延迟任务的统一状态修改顺序。** 此节通过上游 `main@ac9a7e6` 源码确认问题和约束，**不批准任何具体 Actor、锁、任务队列、线程池或类名**。关联的库存研究已有 [CLI/库存第 9.1–9.2 节](cli-jline4-shell-research.md) 的详细分析与既有决议，避免重复推翻“游戏业务同步、数据库 I/O 异步”和 P1。

### 12.1 已核实的并发入口

| 来源 | 源码事实 | 受影响的状态 |
| --- | --- | --- |
| KCP / 网络请求 | `GameSessionManager.handleReceive()` 通过全服一个 Netty `DefaultEventLoop logicThread.execute` 转交 `GameSession.handleReceive()`，再由 `GameServerPacketHandler` 同步调用 Handler | Session、Player、Inventory、World/Scene，包入口彼此有序但只覆盖网络入口 |
| 世界、玩家 Tick | `GameServer.start()` 用独立 `Timer.scheduleAtFixedRate` 调用同步的 `GameServer.onTick()`；依次遍历 World、Scene、Player、调度任务及 ServerTickEvent | Tick 线程可以与 KCP 逻辑线程同时读写玩家及世界状态；锁住 GameServer 并不序列化网络请求 |
| GM / JLine / Web | `CommandMap.invoke()` 在调用入口解析并授权，`threading=true` 交给 `Grasscutter.getThreadPool()`；`threading=false` 则在当前调用线程同步执行 | 来源线程可能是游戏网络、控制台、HTTP、公共线程池；“同步业务”不代表天然处于相同逻辑线程 |
| World 延迟事件 | `World.queueTransferPlayerToScene()` 在静态 `eventExecutor` 中睡眠并调用 `transferPlayerToScene()` | 玩家位置、Scene 归属、场景实体及任务触发，能与 KCP、Tick、GM 并发 |
| ServerTaskScheduler | `runTasks()` 由 Tick 线程调用；同步任务直接运行，异步任务由 `new Thread(task)` 启动，完成回调在 Tick 运行 | 插件/脚本/内置任务既可在 Tick 修改状态，也可在其他线程修改状态；定时与所有权是两个问题 |
| 插件与 Lua | `Event.call()` 同步调用监听器，继承触发者线程；`ScriptLoader` 将 `ScriptLib` 作为 Java 对象暴露至 Lua；`ScriptLib` 可操作 Scene、实体、Quest | E1 对外扩展必须限制状态的写入口，但不能假设事件/脚本本身有独立的安全线程 |
| 持久化 | `Player.save()` 转 `DatabaseHelper.savePlayer()`；`ItemPersistenceService` 当前按物品 ObjectId 排序，异步任务执行时读取可变 `GameItem` | 数据库排队顺序不同于内存操作顺序；已批准每玩家有序异步保存及 P1 是独立的一致性要求 |

**参照源码**：[GameSessionManager](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameSessionManager.java)、
[GameServer](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameServer.java)、
[CommandMap](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/command/CommandMap.java)、
[World](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/World.java)、
[ServerTaskScheduler](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/scheduler/ServerTaskScheduler.java)。

### 12.2 状态所有者不局限于 Player

目前至少应分辨如下**逻辑所有权域**；这些是业务一致性的分类，**并非对应线程/Gradle 模块的既定名单**：

- **单玩家业务操作**：库存、装备、货币、个人任务、玩家会话中重要状态。依据已批准的业务同步操作，整次操作必须在一个连贯的运行时互斥边界中完成；P1 要先能接纳其持久化，才允许经济状态改变。另一条每玩家异步写入线负责 I/O，不负责内存互斥。
- **多人 World / Scene / Entity**：多人进入离开、移动实体、战斗与场景脚本会触及多名玩家；不能把它们强行归入某一个玩家的个人操作。世界/场景操作需稳定协调其自己的状态所有权和所影响的玩家。
- **账号 / Dispatch**：登录令牌和账户记录即使在 `DISPATCH_ONLY` 中也有自己的操作语义；与 Game 状态之间通过已有连接协议及明确的操作契约协调。D1 保持有效。
- **静态 7.1 资源**：加载完成后供各游戏系统查询；全局资源重新加载需要自己的就绪及可见性约束，不套用玩家锁。

### 12.3 已存在的锁顺序约束，以及插件事件的关键风险

- `World.addPlayer/removePlayer` 使用 World monitor，其中调用 `Scene.addPlayer/removePlayer`；Scene 自身的 `synchronized` 方法会修改 Player 和实体。跨世界/场景切换同样需要谨慎处理两个 World / Scene 的锁顺序。现有 `Scene.addPlayer` 特意在进入本 Scene monitor 前先离开旧 Scene，显式避免两场景相互等待。
- `GameServer.onTick` 锁的是 GameServer；`Player.onTick` 锁的是 Player；`Inventory.addItems` 和 `payItems` 锁的是 Inventory。三个 monitor 不能自动形成一条完整玩家/世界业务操作。
- 更值得重视的是 `Inventory.addItemsInternal` 在持有 **Inventory monitor** 的同步方法内部调用 `PlayerObtainItemEvent.call()`。监听器可能操作 World、Scene、其他 Player 或触发其他库存行为；当前代码没有为这一回调建立可验证的全局锁顺序。**这说明“把插件事件放进一个 synchronized 操作里”同样不是可靠方案**。
- `World.queueTransferPlayerToScene` 在单独线程执行传送，而 `PlayerTeleportEvent` 可以由插件取消。不能默认该事件仅在某个逻辑主线程运行；若将来改变回调时机，必须保留允许阻止传送的业务语义。

**工程约束（建议）**：先按真实游戏业务划定一次完整操作的权威归属与可见结果。同步操作不意味着执行器必须是专属线程；也不能持有未知 World/Scene 锁同步等待跨域操作或数据库。前置可取消事件、完成后通知/事件应有清楚的阶段；插件改动前置输入后需按最终输入复验，避免提前确认再被改写。具体投递、互斥和事件时序需在相关垂直业务切片中验证。

### 12.4 旧技术路线比较（已由 S2/R2 后续决议更新）

| 路线 | 对 AstaPS 的实际影响 | 审计判断 |
| --- | --- | --- |
| **少量明确的业务互斥域，保留同步调用线程** | 从玩家完整经济/装备操作和 World/Scene 跨域操作的所有权开始；CLI、包和 Lua 调用同一能力，运行时用相应业务互斥保护，持久化 I/O 另行异步 | 与现有“业务同步完成”“give 在调用线程同步执行”和 P1 一致，迁移风险相对可控；但必须严格解决多 World/Scene 与多个 Player 的锁顺序及事件回调，不能机械地铺 `synchronized` |
| **统一执行循环或按 World 分片的消息调度** | 入口提交操作给所属模拟执行器，可能改变 GM、插件调用语义及 Tick、网络包的执行线程；跨场景迁移、回调重入、长操作和背压规则均需重新设计 | 理论上有清晰线程所有权；但与已批准的直接同步命令调用语义存在冲突，不应为了当前库存重构擅自引入全局 Actor/队列或重写服务器调度 |

**历史范围**：本表形成于 S2、R2 选择之前。“调用线程互斥”不再是目标方案；S2 确立 World 模拟执行域，R2 确立 Player 个人执行域。下列成本评估仍供设计时参考。

真实同类案例：[Minestom 的 `ThreadDispatcherImpl`](https://github.com/Minestom/Minestom/blob/master/src/main/java/net/minestom/server/thread/ThreadDispatcherImpl.java) 通过 partition 和 tick threads 分配实体/区块工作；[`Acquirable`](https://github.com/Minestom/Minestom/blob/master/src/main/java/net/minestom/server/thread/Acquirable.java) 显式说明实体线程所有权与跨线程获取；[`ServerProcessImpl`](https://github.com/Minestom/Minestom/blob/master/src/main/java/net/minestom/server/ServerProcessImpl.java) 调度 tick 和分片更新。这说明并行模拟的所有权模型有成熟工程实例，同时也证明其需要完整的调度、迁移和获取协议；不能通过“添加几个线程池”复制。

### 12.5 建议的落地次序与验收方向（无新架构批准）

1. **从已批准的库存切片做可复现测试**：并发 GM/client 同玩家库存、强化与 Tick、可取消/后置插件事件、P1 容量拒绝、异步写入快照与重登录先读后写；这可以检验调用线程同步操作是否满足需求。
2. **再验证跨域业务**：多人进入/退出 World、双人同时跨 Scene、延迟传送与主动传送竞争、插件在事件中发起另一项操作；记录真实锁获取顺序与是否可能观察半完成状态。
3. **针对已证明的问题确定必要的最小协调策略**：保留 K1 核心玩法内置、E1 扩展统一入口、M2 工程模块化、D1 三运行模式。除非测试和运行期证据要求，否则不添加全局 Actor、协调器、事务框架或新的线程层。
4. **最后按受影响业务扩展**：任务、奖励、商店、抽卡、战斗、世界逐批收敛入口；保留奖励回执 PREPARED 的同步持久确认例外，以及普通玩家状态的异步保存语义。

本轮结论是**源码证实的执行风险与推荐研究/落地方式**，没有推定已经获得运行时架构修改授权，也没有假称已完成竞争压力测试。文档研究不运行 CI，实施阶段按既有测试/CI 约束执行。

## 13. S2：以 World 为逻辑执行域，允许不同 World 并行（2026-11-04 正式决议）

**用户选择 S2，取代第 12.4 节中“仅保留调用线程互斥”的默认推荐方向。** 目标架构将每个活跃 `World` 视为**世界模拟状态**的逻辑执行域：同一 World 内的 Scene/Entity/世界规则按明确顺序执行；不同 World 在满足隔离条件下可以并行运行。**这里不能推导出 Player 的 Inventory/个人数据必须由当前 World 拥有执行权**；这一过度推论已在第 14 节纠正。逻辑执行域**不等于一条永久独占物理线程**；多个 World 可以共享有限的工作线程，也允许按负载分配。

这项决议要求建设足以支撑 S2 的调度与状态所有权机制，不能再把现有 `GameServer.onTick()` 顺序遍历当作必须保留的设计前提。**此处尚未决定**线程数量、Tick 频率、分片/抢占策略、消息队列具体实现、是否使用 Actor 库、精确类名和 Gradle 模块数量。

### 13.1 World 模拟的已选方向及需要继续确定的跨域规则

1. **World 模拟操作序列化**：同一 World 的 Scene/Entity Tick、场景 Lua、世界实体交互与修改该 World 状态的命令/插件回调应在其执行域内有序执行；不同 World 之间可并行。涉及 Player 个人状态的操作需要按实际状态所有权协调，不能仅凭玩家的当前 World 判断操作目标。
2. **Player 个人数据所有权独立于 World 成员关系**：Inventory、Avatar、货币等属于玩家，并随玩家在 World 之间移动；其操作入口以玩家身份为目标。**R2 已选择按 UID 的 Player 逻辑执行域**；仍需决定的只是调度器、线程与结果 API 的具体实现。玩家的世界参与状态与所属 World 的实体交互仍需明确协调；登录、离线、重连不能通过假造 World 来获得执行权。
3. **World 内 Scene/Entity**：该 World 的 Scene、怪物、场景脚本和世界状态沿相应执行域修改。一个 World 可以存在多个 Scene，当前 S2 仅确立 World 粒度；不自动批准在 World 内将 Scene/Entity 再细分并行。
4. **跨 World/跨账号操作**：多人邀请、联机进入/退出、跨世界传送以及涉及两个所有者的操作，必须规定协调流程、失败取消、超时和重复提交语义。严禁在持有一个 World 执行权时同步等待另一个 World 执行权而形成死锁。
5. **阻塞边界**：不在 World 模拟执行器上等待耗时 MongoDB 或其他慢 I/O。保留既定 P1：经济类变更在内存修改前确认持久化工作可接纳；每玩家异步串行落盘；奖励 `PREPARED` 回执确需同步持久确认的例外应采用不阻塞其他 World 的协调机制，具体由后续设计研究。
6. **插件与 Lua**：沿用 E1、K1。第三方插件通过目标状态的权威能力修改 Player 或 World；场景 Lua 保留核心规则。插件事件的执行上下文、重入以及可取消事件的校验/提交时点，必须匹配**具体被修改状态**的所有权，不允许以旧 `PlayerHook` 直接可变字段方式绕过边界。
7. **D1 与 M2**：`GAME_ONLY` 和 `HYBRID` 中部署 Game 运行时的 World 调度；`DISPATCH_ONLY` 仍有账号、认证和 HTTP 工作，不强制创建 Game World。模块化（M2）和运行部署（D1）与每 World 逻辑执行域是互相独立的设计维度。

### 13.2 D01B 与 S2：撤回此前过度推导的冲突声明

[CLI/库存第 4.1 节](cli-jline4-shell-research.md) 的 **D01B** 要求 `give` 在命令调用线程直接同步执行业务。第 13 节最初将这视作与 S2 必然冲突，理由是“目标 World 执行域才可以修改 Player/Inventory”。

**该理由不成立**：`Inventory` 属于 `Player`，同一个玩家在不同 World 间迁移，背包的所有权不改变。源码的 `CommandMap.getTargetPlayer()` 按玩家 UID / 账号定位，且 `GameServer.getPlayerByUid(uid, true)` 可读取离线玩家；不需要先定位 World。**S2 约束世界模拟，不能以此要求每个 `give` 必须投递当前 World。**

当时这一纠偏说明 S2 本身不要求把纯库存 give 投递到当前 World；此后 R2 确定了按 UID 执行个人状态操作，覆盖 D01B 的任意调用线程直接写库存的旧细节。**后续第 16 节已正式选择 R2（按 UID 的 Player 串行执行域）**。因此此处当时仍待选择的互斥/执行域分歧已经关闭，D01B 中“调用线程直接操作状态”的旧约束也因 R2 不再可原样实现。具体 CLI 等待与结果反馈策略仍需研究。

当命令直接影响 World/Scene/Entity 时，例如生成怪物或修改世界机关，它仍应通过目标 World 的执行权；同时涉及玩家库存和世界实体的领奖/掉落交互，需要定义跨域业务时序。

### 13.3 真实代码切入点与先后次序（研究，非实施许可）

- `GameSessionManager.logicThread` 目前全服共用一个 Netty `DefaultEventLoop` 接收全部游戏包。需要调查协议分发、同玩家包内顺序，以及请求最终修改的是 Player 个人状态还是 World 模拟状态；不能把所有 Handler 一律投递当前 World，也不能随机发到线程池。
- `GameServer.start()` 当前用独立 `Timer` 串行迭代全部 World/Player/Task，未来应把 World/Scene/Entity 的模拟 Tick 归属于逻辑 World，保留整体节拍和必要全服任务；`Player.onTick()` 同时涉及个人与世界数据，需要按实际状态拆分或协调，不能机械整体塞进 World，也不能与 World 并发写同一实体。
- `World.queueTransferPlayerToScene()` 当前会在线程池延迟后直接改变 Player/Scene；在 S2 下其定时部分和状态提交部分必须明确区分执行域。
- `World.addPlayer/removePlayer`、`Scene.addPlayer/removePlayer`、`Inventory.addItemsInternal` 的现有 `synchronized` 和插件事件是迁移风险最大的关键路径，应以所有权断言和并发测试检测未正确投递或跨域读写。
- 先建立**Player 与 World 分别的所有权、跨域协作和命令执行语义**的行为测试与设计，再逐步迁移 Tick、包处理、GM、Lua、插件、存储交界面；针对多 World 并行增加真实并发与负载测试，在让用户测试和提交上游前运行 CI。

参考 [Minestom `ThreadDispatcherImpl`](https://github.com/Minestom/Minestom/blob/master/src/main/java/net/minestom/server/thread/ThreadDispatcherImpl.java) 的分配逻辑及 [`Acquirable`](https://github.com/Minestom/Minestom/blob/master/src/main/java/net/minestom/server/thread/Acquirable.java) 的显式线程所有权。它们作为技术证据，**不是强制采用 Minestom 的 API 或并行粒度**。

本节保留 S2 当时的正式选择及 D01B 历史分歧；**当前结论以第 14–16 节及 R2 为准**。原轮未改 Java/Gradle、未新建分支、未执行 CI。

## 14. 状态所有权纠偏：Player 个人数据与 World 模拟独立（2026-11-04）

用户提出准确的反例：**管理员向玩家发放物品应以玩家为目标，与玩家当前处于 World A 还是 World B 无关。** World 在源码中是具体 `emu.grasscutter.game.world.World`，没有指代所有状态执行域的泛化含义。此前将 World 执行域当成 Player / Inventory 的统一所有者，并认定 D01B 必须修订，是过度推导，现已在第 13 节就地更正。

### 14.1 原生源码支持的事实

- [Player.java](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/player/Player.java)：持有 transient `Inventory inventory`、`AvatarStorage avatars` 等玩家个人业务对象；同时有 transient `World world`、`Scene scene` 引用，后者可随加入与离开变化。
- [World.java](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/World.java)：`addPlayer()` 在世界归属变更后调用 `Scene.addPlayer()`，不会重建 Inventory 或更改其业务归属。
- [GameServer.java](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameServer.java)：`getPlayerByUid(uid, true)` 可以按 UID 获取在线 Player 或通过数据库加载离线 Player，因此以 World 为目标的 give 不能覆盖真实命令需求。
- [CommandMap.java](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/command/CommandMap.java)：命令目标按 UID/账号解析及持久记忆，不要求命令提供 World 身份。

### 14.2 逻辑状态归属（R2 已选，具体调度实现未定）

| 逻辑归属 | 典型状态 | 实际操作举例 |
| --- | --- | --- |
| **Player 个人域** | Inventory、装备/Avatar 归属、货币、个人进度、每玩家写入顺序 | `give(uid, item)`、强化、装备、邮箱授予、离线管理 |
| **World 模拟域（S2 已选）** | World/Scene/Entity、怪物与机关、场景脚本、世界内战斗模拟 | 怪物生成、Scene/Entity Tick、世界机关触发、多人场景交互 |
| **Account / Session 域** | 登陆资格、当前有效会话、账号认证及重连 | 授权、上线、下线、拒绝旧会话写入 |
| **跨域业务流程** | 涉及玩家和世界两方状态的协作过程 | 拾取世界掉落物、开箱领奖、装备能力反映到场景角色、世界之间的玩家迁移 |

这些是**状态语义上的所有权分类**，不预设“一类等于一个物理线程、一个 Gradle 子项目或一个 Actor”。S2 的 World 并行目标保持正式决议；**随后已选择 R2：Player 个人域按 UID 逻辑串行、不同玩家可并行**，不要求每个玩家独占物理线程。P1、业务同步完成和每玩家异步有序持久化继续有效；D01B 关于“任意调用线程直接修改个人状态”的细节须受第 16 节的上层新决议约束。

最需要进一步验证的真实难点在**跨域业务**：玩家拾取 World 的掉落物会同时影响世界实体和个人库存；装备变化还可能影响 World 中角色实体的战斗属性。需要明确定义谁决定操作成功、什么时候占用/消费世界资源、玩家库存容量与写入 P1 如何预检、失败怎么撤销/重新开放、客户端与插件何时观察到完成状态。不要仅靠在两个域间同步相互等待来解决，容易形成死锁。

**本轮澄清了“个人库存操作不必经过当前 World”，保留 S2。随后 R2 覆盖了 D01B 的直接调用线程细节，详见第 16 节。** 未批准额外通用协调器、数据库事务或兼容框架。

## 15. Player 与 World 跨域业务：拾取、领奖、联机迁移（2026-11-04 源码审计）

依据 `MeChen618/AstaPS/main@ac9a7e6`。本节针对已批准 **S2（World 模拟按 World 并行）**和第 14 节已澄清的 **Player 个人状态独立**，列出实际跨域业务、失败边界和待审设计；除既有 S2 / E1 / K1 / D01B / P1 等决议外，不额外宣布新的技术选型。

### 15.1 四种实际代码路径的所有权

| 路径 | 真实调用链 | 跨域事实 |
| --- | --- | --- |
| **纯个人库存 `give`** | `GiveCommand.Args.run()` → `targetPlayer.getInventory().addItem(s)/addItems()`；`CommandMap.getTargetPlayer()` 按玩家标识找目标 | 物品发放业务归 Player；无须查询或投递其当前 World，个人库存操作须进入 R2 的 UID 执行域。但 `GiveCommand.updateAvatar()` 在**降低命座**时调用 `World.transferPlayerToScene()` 两次并广播实体，此分支确实跨 Player/World；不能按**命令名**一刀切确定归属 |
| **拾取地面实体** | `HandlerGadgetInteractReq` → `Player.interactWith()` → `EntityItem.onInteract()` → `Inventory.addItem()` → `player.onGadgetInteracted()` → `Scene.removeEntity()` → 回包/广播 | 先入包再移除，保护了满背包拒绝时地面物品不丢失。但完整操作由 World 上的实体可用性与 Player 的库存接纳共同决定，现有代码没有跨域整体序列化契约 |
| **宝箱与世界 Boss 奖励** | `GadgetChest.onInteract()` 为 `synchronized`；普通宝箱经 `WorldChestLootHelper.grant()` / `RewardClaimFlow.grant()`，自动发放到 Inventory、地面物品创建到 Scene；Boss 奖励经 `RewardGrantJournal` 在扣树脂前持久准备、发放后更新凭据/结算标记 | 一个操作关联 World 内的宝箱/掉落状态、Player 领取/余额、MongoDB 的回执。部分路径在整个同步交互过程中做 `insertOne()/updateOne()`；迁到 S2 后若原样在 World 执行器中运行，会阻塞当前执行器及其承载的其他 World；也不能草率把持久回执改成响应后异步 |
| **多人世界迁移** | `MultiplayerSystem.applyEnterMpReply()`、`leaveCoop()`、`kickPlayer()` 经 `World.addPlayer()`；`World.addPlayer` 会先调用旧 World 的 `removePlayer`，然后设置 `Player.world` 并 `Scene.addPlayer()` | Player 个人库存仍归 Player；成员关系、角色世界实体、场景协议涉及旧/新两个 World。`MultiplayerSystem` 多个入口目前 `synchronized` 在同一个全局系统实例上，可能成为 S2 世界并行化的串行点。两 World 移交不能靠原样嵌套锁或双方互相等待 |

源码：
[EntityItem](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/entity/EntityItem.java)、
[Player.interactWith](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/player/Player.java)、
[Scene.removeEntity](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/Scene.java)、
[GadgetChest](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/entity/gadget/GadgetChest.java)、
[WorldChestLootHelper](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/entity/gadget/chest/WorldChestLootHelper.java)、
[RewardClaimFlow](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/reward/RewardClaimFlow.java)、
[RewardGrantJournal](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/database/RewardGrantJournal.java)、
[MultiplayerSystem](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/systems/MultiplayerSystem.java)、
[GiveCommand](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/command/commands/GiveCommand.java)。

### 15.2 同步互斥的现状能解决一部分，但不满足 S2

`GadgetChest.onInteract()` 对同一个 GadgetChest 实例串行，`Scene.removeEntity()` 锁住 Scene，`Inventory.addItem()` 通过 Inventory 锁进行库存接纳，`MultiplayerSystem` 使用服务级大锁。这些局部措施有实际价值，但不构成贯穿 World/Player/Session 的统一提交顺序；同时 `Inventory.addItemsInternal()` 在持有库存 monitor 时触发插件事件，插件可以重新进入别的状态域。

特别注意：`EntityItem.onInteract()` 的 `share` 属性和 Owner GUID 控制拾取权限/广播行为，不能从名字直接推导“一份掉落应奖励多个玩家”。源码成功拾取会删除实体；需要对不同生成路径和各玩家独立掉落的实际模型分别测试，避免错误的多人重复领取规则。

### 15.3 按业务来源协调两个域（建议，非已选通用框架）

**地面拾取示例**，建议把 World 中的实体当作可消费资源，把 Player 的 Inventory 视为独立的接收方：

1. **World 验证并暂时保留实体**：实体确实存在、可拾取、归属/距离/玩家互动条件满足时，对该实体建立一个未完成的领取尝试，防止同时被另一个玩家消费。等待时不占用 World 执行线程，也不把实体立刻销毁。
2. **Player 按 R2 执行库存业务**：在目标 UID 执行域验证个人状态、容量及 P1 持久化准入，完整同步接收物品并形成成功或失败结果。世界所在位置不决定 Inventory 的执行域。
3. **World 根据结果结案**：Player 失败则取消尝试、保持原实体；Player 成功才移除实体并广播。结果可能延迟到达，因此要核对同一实体身份、尝试身份和 World 生命周期，避免旧回调关闭新实体。
4. **不可把“先加库存再删实体”称为跨域原子事务**：Player 已成功但 World 后续失败时，不能简单再次发物品或盲目回滚已经对外可见的库存变更。需要可重复完成的世界侧收尾/必要的来源侧记录；具体保障应按是否持久奖励分别设计。只有原有要求的奖励回执具有已知持久确认语义，一般地面掉落不能默认得到完全崩溃原子性。

这是一条可研究的**操作编排方式**，不是已批准引入某个通用两阶段提交协议、分布式数据库事务、EventBus 或 Actor 框架。

**付费/一次性奖励**更严格：必须维持 PREPARED 的同步持久确认点、唯一领取身份以及 CONFIRMED/COMPLETED 的来源专属核对。可以研究让等待 MongoDB 的阶段离开 World 执行器，确认结果后重新进入相关权威状态域做最后校验和发放。其间必须解决奖励是否仍可领取、扣费能力是否改变、队列准入是否失效等问题；不能在等待时锁死整个 World，也不能仅以重试替代已保存的不确定回执状态。

**跨 World 玩家迁移**应独立作为“成员关系所有权移交”研究：旧 World 停止接受该玩家的旧世界实体操作并完成离开；新 World 验证容量/权限后接收并构建对应 Scene/Entity；Player 的个人状态和登录会话保持连续。发生失败时必须有唯一、确定的有效成员关系；不允许两 World 的执行器相互同步等待，也不把迁移误作“搬走 Inventory”。

### 15.4 扩展、调度与 API 的直接后果

- **业务能力以实际状态为目标**：`give(playerUid, itemSpec)`、`pickup(worldEntity, playerUid)` 和 `enterWorld(playerUid, targetWorld)` 的所有权不同。当前命令字符串或 Java 包归属都不能代替业务语义。
- **插件事件应按操作阶段分类**：例如拾取前的可取消事件在决定资源占用和发放前触发；成功通知在状态已有效后触发。插件失败不能让 Player 已收物品、World 又重新开放同一实体。禁止随意在一域事件回调中同步等待另一域锁。
- **独立 Worker 不等于并行正确**：当前 Netty 网络入口全服串行只是偶然提供部分请求序列化；S2 需逐步让模拟状态操作服从 World 域，按照 R2 使 Player 个人状态操作有序执行，独立处理跨域协调。
- **监控优先于盲目性能优化**：至少观察 World 每次 Tick 的耗时、领取流程各阶段等待、跨域取消/重试次数、World 迁移冲突及有序持久化队列背压。

### 15.5 需要继续确定的高层问题与测试

**后续已决定 R2**：Player 个人状态采用按 UID 的有序执行域，操作不跟随玩家当前 World；不得把用户选择的 S2 偷换为“Inventory 归 World 执行”。下面原列测试仍需要实施。

优先测试：两名玩家同时拾取同一地面实体、满背包后重试、库存成功而世界收尾失败、重复网络包、世界退出时仍有未决拾取；Boss 花多人领取与同步回执等待；两组玩家在不同 World 并行组队/切换，不可因全服 `MultiplayerSystem` monitor 产生不必要的等待；`give` 修改已拥有 Avatar 并触发场景重置时，个人域与 World 的后置动作要保持一致。

**此节是研究记录，不批准实现、线程池配置、API 类型、兼容层或新的持久化数据结构。** Java/Gradle 未改，不触发研究阶段 CI。

## 16. R2：按 Player UID 串行执行个人状态操作（2026-11-04 正式决议）

**用户正式选择 R2**。目标架构从此有两类独立的逻辑执行域：

- **S2 / World 执行域**：按 World 实例管理世界模拟、Scene/Entity、场景脚本及世界内行为；不同 World 可以并行，同一 World 的变更有序。
- **R2 / Player 执行域**：按玩家 UID 管理 Inventory、Avatar/装备归属、货币和其他玩家个人状态的完整业务操作；同一玩家的已接纳操作有明确顺序、不并发修改；不同玩家可以并行。**不以玩家当前的 World 作为个人数据操作的路由键。**

R2 的**逻辑串行队列不等于每玩家一条物理线程**。可以用共享的有限工作线程运行多个 UID 的任务，但同一 UID 不能同时运行两项需要串行的个人业务操作。并行调度和跨域通信的确切实现尚未批准；不能误写成全服玩家共用一条串行队列。

### 16.1 对原有决议的影响

| 原有决议 | 与 R2 的关系 |
| --- | --- |
| **S2** | 两者并存，各自拥有对应状态；World A、B 与 Player 10001、10002 的逻辑执行域是**不同归属维度** |
| **D01B：give 在命令调用线程直接同步执行** | **“在任意命令调用线程直接修改玩家状态”部分被 R2 上层决议覆盖**。纯库存 give 必须经目标 UID 的 Player 执行域；不因目标所在 World 而投递 World。D01B 希望的“业务完整执行后报告真实结果”仍保留；CLI 调用线程要异步回调还是允许安全等待，另行确定 |
| **装备/背包业务同步完成** | 保留：一项业务在 Player 执行域内完成校验、受控修改与可见的内存结果，不能被该 UID 的另一项操作穿插 |
| **P1** | 保留：经济变更需先取得足以接纳整次操作的异步持久化工作能力，拒绝时不变更业务状态；R2 的业务任务排队与 P1 的 DB 写入准入是不同的排队问题 |
| **每玩家异步有序数据库写入线** | 保留且**独立于 R2**：运行时个人操作的 UID 序列化负责内存一致性，DB 写入队列负责 I/O 顺序；任务完成不代表 MongoDB 已持久成功；离线重新加载仍需已有成功屏障 |
| **E1 / K1 / M2 / D1** | 第三方扩展通过权威业务能力进入适当的 Player/World 域；原版规则内置；运行域选择不预设 Gradle 模块数量，Dispatch-only 不需要 World 实例 |

### 16.2 必须明确的跨域语义（尚未选择实现）

1. **入口和回执**：网络包、控制台、Web、插件以及 World/Lua 请求个人数据修改时，按 UID 投递；如果调用已位于同 UID 执行域，需明确同步内联与防止业务重入的规则。如果入口没有该 UID 执行权，不可直接操作可变 Inventory。应返回可表示最终成功、拒绝或失效的操作结果；具体是否使用 Future、callback 或其他 API 尚未定。
2. **命令同步交互**：此前 give 的 D01B 禁止“异步再等待”是针对旧执行方式的决定；R2 的串行调度使其不能原样覆盖跨线程调用。能否在**控制台外部线程**安全等待真实完成结果、是否默认异步显示，是需要独立批准的 CLI 交互设计；绝不允许在 Player/World 执行域互相阻塞等待以致死锁。
3. **跨 Player 交互**：交易、多人角色关系变更等涉及两个 UID 的业务需要明确协调、失败与防重复，不把全服玩家退化为一把大锁；是否存在跨 UID 同时修改的原版 7.1 必需操作须从调用点验证。
4. **跨 Player/World 交互**：地面拾取、世界奖励、联机移动、装备影响场景实体时，按第 15 节分开个人状态和世界实体的修改，定义跨域交互结果/取消、提交后通知和来源侧防重复，避免同步循环等待。跨域操作的业务完成语义可能涉及多个执行阶段，不能把一次完整 Player 经济变更切成可被另一项 Player 任务穿插的半操作。
5. **登录/退出/离线**：以 UID 为归属的个人操作在登录未完成、登出中、离线管理时仍需明确准入和生命周期，结合 [CLI/库存第 9.3 节](cli-jline4-shell-research.md) 已审计的登录世代、重复退出与异步写入成功屏障；不要因为没有 World 就给离线 Player 生成临时 World。
6. **执行器负载**：单个 Player 的长任务不应无界堆积，也不应让一个 UID 的慢数据库操作拖住其他 UID 或 World。专属线程、队列容量、同一 UID 公平性、超时/背压与 Tick 协调策略需要性能和失败用例，当前 R2 不批准任何具体线程数量和锁框架。

**下一个真正的架构问题**是命令/Web/插件的最终结果语义与两个执行域间的非阻塞协作契约。先设计操作所有权和可观察结果，再实施具体调度，避免把旧的 `threading=true` 命令模型原样包装成另一层任务队列。

本轮正式记录 R2 决议，未修改 Java/Gradle、未建立新分支、未运行研究阶段 CI。

## 17. S2/R2 的操作提交、完成结果与事件时序（2026-11-04 源码研究与提案）

**状态：S2、R2、E1、K1、P1 及 F1 外部 Shell 反馈策略已经正式选择；本节其他调度机制仍为研究建议。** 这些决议是已确定的输入约束；本节不预设 Java `Future`、Actor、消息总线、特定 executor/线程数或网络协议变更。

### 17.1 当前入口实际如何工作

| 入口/行为 | 已核源码事实 | 迁移后的问题 |
| --- | --- | --- |
| 游戏包 | `GameSessionManager.handleReceive()` 将收包提交给单个 Netty `logicThread`；`GameSession.handleReceive()` 可在**同一 KCP datagram** 内顺序处理多个包；`GameServerPacketHandler.handle()` 先检查会话状态，再同步调用可取消/改写数据的 `ReceivePacketEvent`，最后执行包 Handler | 分域投递后必须保存**同玩家包内先后关系**，且 Handler 不能再直接越权写 Player/World；队列延迟时要重新核对会话世代 |
| 命令 | `CommandMap.invoke()` 返回 `void`；`ExecuteCommandEvent` 可取消或修改目标及文本，随后解析命令、校验权限/目标；`@Command(threading)` 决定在当前调用线程或全局池执行 `Runnable`，任务执行前重验部分目标在线条件 | 解析/授权、状态修改、真实完成结果、反馈消息是不同阶段；改成 R2/S2 后当前 `void` 无法让调用者了解真正执行状态 |
| Web Console | `WebConsoleHandler.command()` 用 `CommandOutputCapture.capture(() -> CommandMap.invoke(...))`，成功返回 HTTP 200 与 `output` 数组；`CommandOutputCapture` 只捕获当前线程上的消息（`ThreadLocal`） | 被线程池/执行域投递的任务可能在 HTTP 已返回后才执行；空输出与 HTTP 200 **不能证明命令业务成功**，也不应将“已排队”伪装为“已完成” |
| 命令反馈 | `CommandOutput.sendMessage()` 调用可取消且可修改消息的 `ReceiveCommandFeedbackEvent`，再记录日志或发消息；与命令业务返回值无统一关联 | 插件过滤消息和业务成功/失败须分别表示，不能根据有没有消息断言业务结果 |
| 插件事件 | `Event.call()` 在当前调用路径同步 `PluginManager.invokeEvent()`；`PlayerObtainItemEvent` 在 `Inventory.addItemsInternal()` 取得库存 monitor 后同步发出，异常被记录但已发放物品不会因此回滚 | 事件的执行上下文与前置取消/后置通知语义应由**目标状态所有权与事件阶段**决定，不能随手异步化可取消事件；事件回调更不能绕过 E1 直接写跨域可变状态 |

证据：
[GameSessionManager](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameSessionManager.java)、
[GameSession](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameSession.java)、
[GameServerPacketHandler](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameServerPacketHandler.java)、
[CommandMap](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/command/CommandMap.java)、
[CommandOutputCapture](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/command/CommandOutputCapture.java)、
[WebConsoleHandler](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/http/console/WebConsoleHandler.java)、
[CommandOutput](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/command/CommandOutput.java)、
[Event](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/event/Event.java)、
[Inventory](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/inventory/Inventory.java)。

### 17.2 跨来源业务应有的结果层次（建议）

**需要区分三件事**：

1. **接纳/排队**：调度器承诺有机会执行，仍可能因会话失效、权限变化、目标消失或处理错误而拒绝。
2. **业务完成**：在权威 Player 或 World 域内实际完成校验和变更，结果能表达具体成功、部分成功、主动取消、业务拒绝或故障；例如 `give` 容量截断到 0 按既定 G04C 是**正常业务成功**，P1 写入队列拒绝是**业务失败**。
3. **消息投递/落盘**：聊天输出、Web 回执、网络响应及异步 MongoDB 保存有各自的完成状态；输出被插件过滤不会将成功变成失败；普通经济业务已在内存成功不等于数据库已保存成功。已定 PREPARED 必要的同步持久确认例外必须先完成才能进行不可逆支付。

因此建议权威业务形成一个**可被调用方观察的完成结果**（包含业务数据及来源约束），而入队返回、终端输出、HTTP 状态和数据库持久化完成应明确分开。**具体公共接口类型、名称、对象层数和序列化格式未获批准**；避免为了多个简单命令创建万能结果/事务框架。

### 17.3 调用与跨域执行的最低行为约束（建议）

- **确定目标**：按实际修改的状态找到 UID/World 域；`give(uid)` 进入 R2，`spawn(world)` 进入 S2，拾取与领奖需要跨域流程，不能仅凭调用方来源或包名推断域。
- **入口先预检、所有者最终复验**：CLI 解析和权限、网络会话状态、Lua 来源等可以先检查，但排队到执行存在时间差。真正修改之前必须在相应权威边界重新验证关键前提（有效会话/UID 世代、目标、权益、库存、世界实体存在等），避免陈旧引用和“排队前有权限、执行时已失效”。
- **单域操作**：一项 Player 业务在自己的 UID 域完成全部同步内存变化，下一项同 UID 业务不能插入；同 World 模拟操作也有同等顺序。不同 UID/World 可并行。事件的前置可取消/可修改阶段应在权威验证、不可逆修改前，最终输入改变后要复验；后置事件在业务已有效后执行。事件可以处在内部同步业务调用栈，但不能在不具执行权的线程直接篡改其他域状态。
- **跨域操作**：一域提交到另一域后，通过完成回调或状态续接处理，不允许占着 Player/World 域阻塞等待对方。若操作会先对一个域产生不可逆副作用，再完成另一个域，需要来源专属的去重、收尾/恢复协议；仅靠返回一个 Future 不提供跨域原子性。
- **无界/重复与生命周期**：必须处理同 UID 入队顺序、重复网络包、超时与取消（未开始的任务和已开始的不可逆业务不能采用相同取消含义）、玩家断线/重登录、World 实例销毁、CLI/Web 请求结束。技术实现可异步，但一次业务内存修改仍应按既有规定同步完成。
- **避免慢任务占用游戏域**：不得在 Player/World 执行器中同步等待 MongoDB、HTTP、另一个域或 Shell/Web 用户输入。对需持久确认的奖励应研究分阶段冻结必要业务前提与恢复检查，并保留既有回执状态，不直接将它变成普通异步写入。
- **脚本与插件**：执行域归属以具体变更为准，场景 Lua 对世界状态的访问在 World 上下文，若需个人库存变更应请求 Player 能力并异步取得结果；可取消事件若业务必须立即根据插件结果决定提交，则监听器需在该操作的权威阶段同步给出决定，不隐式同步跨域等待。插件中异常与拒绝也需要明确报告，不能吞掉最终业务失败。

### 17.4 同类项目的真实取舍

- [PaperMC Folia 开发指南](https://docs.papermc.io/paper/dev/folia-support/)明确区分 Global / Region / Async / Entity Scheduler，Entity 调度跟随实体移动；[Folia 设计说明](https://docs.papermc.io/folia/reference/overview/)区分独立 Region Tick、全局所有权与跨区操作，**没有要求每 Region 一条专属物理线程**。
- [Folia EntityScheduler API](https://jd.papermc.io/folia/26.1.2/io/papermc/paper/threadedregions/scheduler/EntityScheduler.html)明确处理实体跨区域迁移及 scheduled task 因实体被移除而无法执行时的退休回调。这与 AstaPS 的 UID 会话更替/World 生命周期导致任务失效的风险相似，但 **Folia 的 Entity 属于区域模拟，AstaPS 的 Player 个人状态有独立 R2 所有权，不能原样复制调度粒度**。
- [Minestom Acquirable](https://javadoc.minestom.net/net.minestom.server/net/minestom/server/thread/Acquirable.html)支持查询/断言线程所有权，也提供同步获取机制；[Minestom ThreadDispatcher](https://javadoc.minestom.net/net.minestom.server/net/minestom/server/thread/ThreadDispatcher.html)区分逻辑 partition 与 tick threads。它的同步获取是一个实现取舍，**不等于 AstaPS 的两个域可以任意嵌套阻塞获取**。
- [Folia README 的事件/命令约定](https://github.com/PaperMC/Folia/blob/ver/26.2.x/README.md)指出 Region 并行时事件与命令也可能并行执行；AstaPS 若升级原有同步插件回调，应明确归属域，不能沿用“所有插件总在一个主线程”的假设。

### 17.5 F1：Shell 在外部等待真实业务完成（2026-11-04 正式决议）

**用户已正式选择 F1。** 对普通前台 Shell 命令，处理流程为：

1. Shell 在其**允许阻塞的外部交互上下文**解析命令、完成相应入口校验，再向实际状态所有者（R2 的 Player UID 或 S2 的 World）提交受控操作。
2. 目标域执行时复验关键条件，完成一项同步内存业务操作（必要时含跨域非阻塞协调的所有必需阶段），产生**真实业务完成结果**。仅完成排队/受理不得被视为成功。
3. Shell 等待此完成结果，然后输出本命令的最终反馈、退出状态，再继续执行下一条前台命令及 `&&`/`||` 等依赖本命令成败的 Shell 组合。此处的“等待”指等待一次**业务完成**，不要求等待普通 MongoDB 异步持久化；奖励 `PREPARED` 等同步确认仍按原有业务约束。
4. **等待边界**：Shell 控制线程可以等待；Player/World 执行线程**禁止因为 F1 在处理另一个 Player/World 任务、MongoDB、HTTP 或 Shell 输出时同步阻塞等待**。从 Lua、网络 Handler、插件同步事件中调用另一执行域的能力时，必须使用不占住原执行域的协调续接方式。F1 不授权将游戏逻辑重新收敛成全服串行线程。

**操作结果与用户可见输出是两条语义**：`ReceiveCommandFeedbackEvent` 可以取消或修改展示消息，但不能据此将已经成功的操作改判为失败；`give` 截断到 0 属于成功，P1 队列拒绝属于失败。线程局部的旧 `CommandOutputCapture` 不能直接保留为异步执行的权威结果来源。脚本以完成结果决定控制流；是否支持 Shell 管道的真正流式传输、后台 jobs、Ctrl+C 后的业务取消语义仍需独立设计。

### 17.6 F1 尚未决定的工程与接口细节

- **Web Console / HTTP**：F1 只确定前台 Shell 的外部等待语义，并要求 Web 不得将“已排队”谎报为“已完成”。Web 采用限时等待返回最终结果，或返回明确待完成状态及后续查询，是下一层独立设计；目前不批准新 HTTP endpoint、超时阈值或响应码规范。
- **结果承载**：需要可关联的业务完成结果与输出反馈，避免现有 `void`/`ThreadLocal` 截断，但尚未批准 `Future`/`CompletionStage`、回调接口、通用结果对象或 JLine 插件包装层。
- **失效与取消**：玩家会话切换、权限重验失败、World 消失、排队过载、外部等待中断，以及业务已提交但调用者不再在线，要分别定义最终结果与可见反馈；“调用者不再等待”不能意味着已经提交的经济变更被自动撤销。
- **业务完成边界**：有跨域副作用的命令何时算真正完成，必须依据该命令的业务语义确定，不能一概以第一个 Player 或 World 操作执行完为成功。
- **已否决的默认 F2**：把“受理成功”当成普通前台 Shell 命令完成的默认语义与已选 F1 冲突。F2 的历史取舍仍可参考上文研究，不据此取消未来可能独立批准的后台作业能力。

本节记录上游源码事实、F1 的正式决议和剩余未批准的技术细节。无 Java/Gradle 改动，无新增分支；文档研究无需运行 CI。

## 18. S2/R2 下的真实共享对象：Avatar 战斗属性、网络 Session（2026-11-04 进一步源码审计）

**核心发现：S2/R2 虽已明确逻辑归属，但上游现有对象图中有同一个可变对象同时供 Player 和 World 修改，且网络发送没有统一序列化契约。** 如果仅将 Tick/命令分发改到两个并行执行器，会把目前靠偶然执行顺序掩盖的问题显性化。以下保留已验证源码及当时的候选研究；**后续第 19 节已正式选择 B1**，没有决定新增第三类 Session 游戏状态执行域。

### 18.1 EntityAvatar 与 Player Avatar 真实共享同一张 fightProperties Map

1. [`EntityAvatar.java`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/entity/EntityAvatar.java) `EntityAvatar.avatar` 是原始 `Avatar` 对象引用；`getFightProperties()` **直接返回 `avatar.getFightProperties()`**。因此 World 的 `EntityAvatar` 和 Player 的装备/养成系统访问的是**同一份可变 map**，未做快照或领域隔离。
2. [`Avatar.java`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/avatar/Avatar.java) 将 `fightProperties` 定义为 `Int2FloatOpenHashMap`，`recalcStats()` 先读取当前 HP/能量后**直接 `clear()` 整张表并重建**；装备、等级、圣遗物等个人业务会触发这一过程。另一方面 [`GameEntity.java`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/entity/GameEntity.java) 的战斗写入调用 `getFightProperties().put()`；[EntityAvatar.heal/revive/energy](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/entity/EntityAvatar.java) 直接读写同一张表。
3. `Avatar.currentHp` 与 `currentEnergy` 是玩家角色要恢复的个人持久状态；[AvatarStatePersist](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/avatar/AvatarStatePersist.java) 在重算后恢复血量/能量。**“状态归属 Player”不能简单理解成战斗时整个可变 Avatar Map 只由 R2 执行；“世界模拟归 World”也不自动转移 Avatar 持久存档所有权**。
   - 更具体地，`EntityAvatar` 的元素能量操作会调用 `Avatar.setCurrentEnergy(FightProperty, float)`，后者直接 `this.save()`；[Avatar.save()](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/avatar/Avatar.java) 最终调用 [DatabaseHelper.saveAvatar()](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/database/DatabaseHelper.java) 进入现有通用异步写入池。**World 活动战斗操作可能直接安排 Player 的持久化写入**，在新架构下必须统一到已选的 UID 有序保存链路，不能仅迁移 `Inventory` 的写入来满足 P1/A1。
4. `Avatar.queueFightPropNotify()` 使用一个**独立单线程 ScheduledExecutorService** 延迟发送，延迟回调读取同一 `Avatar`，构造 [PacketAvatarFightPropNotify](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/packet/send/PacketAvatarFightPropNotify.java) 时拷贝 `avatar.getFightProperties()`，并可能从场景活动 `EntityAvatar` 再读取血债。若 Tick 与 R2 真正并行，**第三个定时线程**也可能参与读取活动战斗状态。线程安全集合/Map 的某些替换不能自动解决“装备重算清空一半时已被世界计算或发包观察”的业务一致性。

此处可确认存在**共享可变对象及无同步的潜在竞态路径**；未在本轮实机复现，不能声称当前已观测到 map 破坏、丢血或封包错误。

### 18.2 活动角色战斗状态：历史候选比较（后续已正式选择 B1）

| 候选 | 活动角色战斗属性/HP/能量的实时权威 | 与已选 R2/S2 的关系 | 主要代价 |
| --- | --- | --- | --- |
| **B1：World 持有 Live Combat State，Player 持有持久 Avatar**（已选，第 19 节） | EntityAvatar 拥有当前战斗态；Player 的装备/养成/持久角色状态经明确的更新/同步协议投影到 World，离场/退出时把需持久化的 HP/能量结果提交回 Player | World 热路径本地处理伤害/元素能量，Player 个人业务继续按 R2；**两个权威各司其职** | 定义快照/修订序列、实时装备更新、战斗同时重算、HP/能量同步/登出屏障，避免双重权威和旧结果覆盖 |
| **B2：Player 持有战斗属性实时权威** | World 的伤害、治疗、能量修改统一请求 Player 域，World 只消费已有结果 | 玩家完全独占 Avatar mutable state，能减少持久与实时状态区分 | 战斗高频操作需要跨域等待/续接，增加延迟和顺序协调；严禁 World 阻塞 R2 等结果 |
| **B3：活动 Avatar 战斗态由所在 World 临时租用** | Player 在线进入 World 时转移活动战斗态写权，离开/切换时归还；养成业务需要与租用期间的 World 协作更新 | 尽量复用一份战斗状态，世界热路径仍本地 | 生命周期租约、跨 World 移交和非活动角色状态更难规定，装备业务完成/存档时点跨 R2/World |

**B1 的推荐依据**：把高频实时模拟留在 S2 World，把长期身份、持有/装备与个人存档留在 R2 Player；世界切换不会改变 `Inventory` 所有权。**B1 已在第 19 节由用户正式选择**；具体同步协议、类结构和数据库结构仍未决定，不得据此自动新增 `CombatState` 类或持久化字段。

**B1 不能理解为直接复制一次数值表**。进一步检索上游 `recalcStats()` 的实际调用点：[`WaterResonanceHelper.refresh()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/WaterResonanceHelper.java) 会根据**活动队伍共鸣**重算活跃 `Avatar`，[`AbilityMaxHpRatioHelper.refresh()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/AbilityMaxHpRatioHelper.java) 在**世界内能力 Modifier 增减**时调用 `recalcStats(true)` 并重新套用实时 MaxHP 比例，[`MavuikaSpiritHelper.onAfterRecalc()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/MavuikaSpiritHelper.java) 会在属性表重建后重新注入实时战斗增益。这表明同一张表目前混合了**装备/成长来源**、**队伍/环境修正**和**运行中能力状态**。B1 要研究“基础属性来源、实时修饰、当前 HP/能量与客户端展示”之间的明确计算/同步规则，否则从 Player 复制旧基础表到 World 会覆盖 Buff，简单把战斗数值复制回 Player 又会把临时能力状态持久化。该类动态来源是 B1 的实际迁移成本，不代表需要把 `Avatar` 的整个成长系统归 World。

实现研究必须先回答：活动角色/非活动角色的属性计算谁负责；多玩家联机同一 World 中角色快照如何一致；带数值修饰的元素能力、动态生命变化何时推送到 Player；玩家在世界中强化/更换装备时能否保证在下一次相关战斗动作前生效；登出/切场景/掉线时 HP/能量持久化更新与已有每 UID 有序写入成功屏障如何衔接。**直接在 R2 上清空 `Avatar.fightProperties`，同时让 S2 战斗线程读取同一 map，是不可接受的目标架构。**

### 18.3 独立于 S2/R2 的网络输入与输出顺序

- **输入**：[GameSessionManager](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameSessionManager.java) 当前通过**一个** Netty `DefaultEventLoop` 调 `GameSession.handleReceive()`；[GameSession](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameSession.java) 会顺序处理同一个 datagram 的多个协议包。未来网络按业务归属将包分别投递 R2 与 S2 时，**两个执行域分别有序不保证跨两域的协议依赖顺序**。例如同一玩家装备变更（`WearEquipReq` → R2）与随后战斗/世界互动（→ S2）如果存在业务先后依赖，必须有显式因果规则。无须为所有无依赖请求引入全服顺序屏障。
- **输出**：[Player.sendPacket](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/player/Player.java) 直接进入 `GameSession.send()`；`send()` 无 `synchronized`，对需要自动头的封包会调用 `getNextClientSequence() = ++lastClientSeq`，字段是普通 `int`。World/Player/定时通知若并发调用，**自动头 sequence 分配并无原子性保证**。发送还会同步调用可取消的 `SendPacketEvent`，加密并调用 `KcpTunnel.writeData()`。是否要求同会话全部发送经串行出口、是否采用线程安全计数器及 KCP 对并发写的容忍度需要实际验证；**只把计数器变成原子变量也不能保证封包顺序与插件事件顺序**。
- **独立的定时发送**：前述 `Avatar.FIGHT_PROP_FLUSH` 从定时线程对 Player/Session 发送通知，现有 `Scene.broadcastPacket` 和插件事件也可能从 World 入口发送。应在统一协议出口定义每会话发送顺序、有效 Session 世代、断线后的旧消息处理；这个网络串行出口属于**传输一致性要求**，无须由此自动创造“第三类 Player/World 状态执行域”或新的 Gradle 模块。
- **会话退出**：当前 [GameSession.handleClose](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameSession.java) 会在网络 close 回调路径调用 `Player.onLogout()`，而该操作含 World 离开及异步保存。S2/R2 下应按各状态所有者安全衔接，而不能在网络 EventLoop 上阻塞等待 Player/World 互相同步完成；同时保留先前第 9.3 节研究的登录世代与旧消息失效规则。

### 18.4 先做的验证（源自真实竞争路径）

- **角色战斗态**：装备/强化触发 `Avatar.recalcStats()` 时同 World 持续伤害、治疗和能量更新；捕获 HP/攻防属性是否出现中间态或结果倒退，尤其多玩家同 World。
- **异步战斗通知**：大量连续 `queueFightPropNotify()` 加世界切换/退出/重新登录，检查延迟通知不会读取新的错误状态或发送至过期连接。
- **网络输入先后**：同一个 KCP datagram 内与跨 datagram 的装备修改→世界互动/战斗，验证具因果关系的事件按预定语义生效；不能只以分别两个队列 FIFO 为测试成功。
- **网络发送**：Player R2、World S2、异步回调并发给同一 GameSession 发送带自动头的封包；检查序号唯一性、调用顺序、插件可取消/修改行为和关闭后的旧通知，不从 `KcpTunnel` 接口假定线程安全。
- **身份移交**：Player 由 World A 进入 B 期间旧 World 的战斗事件、旧 Session 的通知、R2 个人装备变化同时发生，确认唯一有效成员关系和个人数据一致；MongoDB 写入成功屏障仍独立生效。

### 18.5 玩家 Tick 与 Scene Tick 在实际代码里交叉访问并重复执行（关键迁移阻塞点）

[`GameServer.onTick()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameServer.java) 当前先遍历并 Tick 各 World/Scene，随后遍历所有在线 Player 调用 `Player.onTick()`；**两段 Tick 当前顺序执行**（不代表网络/GM 与之串行）。未来若分别把 World Tick 交给 S2、Player Tick 交给 R2，这两个阶段可以真正并行，但其函数内部拥有交叉访问：

| 源码中的 Tick 行为 | 代码位置 | 实际所有权交叉点 |
| --- | --- | --- |
| **同一 BoL 结算逻辑在两条 Tick 路径均被调用** | [`Player.onTick()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/player/Player.java) 调 `ArlecchinoBurstBoL.onTick(this)`；[`Scene.onTick()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/Scene.java) 遍历 Scene 玩家同样调用 `ArlecchinoBurstBoL.onTick(player)` | 当前可能同一轮内重复进入该调用路径，但 Tick 路径上顺序执行；迁移后两个执行域可同时调用。经进一步核验 [ArlecchinoBurstBoL.onTick](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/ArlecchinoBurstBoL.java) 对 PendingBurst 使用 `synchronized(avatar)` 和 `PENDING.remove(key, value)`，**已有局部防重协调**。这些锁可避免一部分重复清算，却会在 S2/R2 之间产生共享 monitor 和回调耦合；需要确认唯一逻辑所有者或审查全部锁/回调时序，不能直接推断会重复领奖/清算 |
| Player Tick 处理能量、Buff、任务和个人每日重置 | `Player.onTick()` 内 `EnergyManager.onTick()`、`BuffManager.onTick()`、`QuestManager.onTick()`、`doDailyReset()` | 大多数属于 Player 个人域，但 Buff/能量可能触及活动场景战斗态；要按实际写入对象分类，不是把整个 `onTick()` 作为一个不可拆的 R2 任务 |
| Player 的日常重置直接通知 Scene | `Player.doDailyReset()` 调 `scene.getBlossomManager().notifyIcon()` | 个人进度更新从 R2 触发 World Scene 的通知/权威数据，不能直接跨域进入 World 可变状态 |
| Scene Tick 直接进入玩家管理器 | `Scene.onTick()` 通过第一个 Player 的 `TowerManager.onTick()`、并调用与 Player 相关的 BoL 结算 | 场景/副本权威与玩家个人任务管理器有耦合。需先明确 Tower 状态依赖与生命周期，再确定 S2 调用还是 R2 续接 |

**结论**：仅将 `GameServer.onTick()` 的 World 循环和 Player 循环提交给两个线程池，会直接违反已选 R2/S2。下一步要把 `Player.onTick()`、`Scene.onTick()` 的每个真实**状态变更**按所属者审计，并对共享的 BoL、能量、Tower 和奖励/日常重置建立明确的单一有效执行路径。该分类不预设“所有 Player 管理器必须在 Player 域”“所有 Scene 调用都只改 World 状态”。对于周期性数据，必须保留原版 tick 时序的行为测试；不能把物理线程数量当成调度完成的判据。

### 18.6 其他游戏服务端的可验证设计：B1/B2/B3 并无行业唯一答案

**对比范围说明**：下面只说明可查的开源服务端、框架或公开架构，不能把 TrinityCore 当成暴雪 WoW 内部源码，也不能从 Unreal 通用功能推断某个商业游戏具体部署了多少线程。“战斗状态存放在谁的对象中”“谁有并发修改权”“哪台服务器权威”“如何存档”是四个不同层次，不应混成一句“由 World 管”。

| 案例 | 有代码/文档支持的事实 | 对 AstaPS 的借鉴 |
| --- | --- | --- |
| **TrinityCore（WoW 服务端模拟）** | [`Player.h`](https://github.com/TrinityCore/TrinityCore/blob/master/src/server/game/Entities/Player/Player.h) 中 `Player : public Unit`，同一 Player 内有背包/装备条目，Unit 负责战斗状态；[`Map::Update`](https://github.com/TrinityCore/TrinityCore/blob/master/src/server/game/Maps/Map.cpp) 遍历地图成员并调用 `Player::Update`；[`WorldSession.cpp`](https://github.com/TrinityCore/TrinityCore/blob/master/src/server/game/Server/WorldSession.cpp) 以 MapSessionFilter/WorldSessionFilter 按线程安全与游戏状态分流网络协议 | 更接近“在线 Player 作为场景中的 Unit，由 Map 更新其运行时状态”的**整合型模型**，并未证明独立 R2 UID 队列是行业必需 |
| **Folia（Minecraft 多线程服务端）** | [官方开发指南](https://docs.papermc.io/paper/dev/folia-support/) 明确 RegionScheduler 归地点，EntityScheduler **跟随实体跨 Region 迁移**；Entity 操作由当前所属 Region 的执行上下文完成 | 更接近**随位置迁移执行权**的 B3 风格；但 Folia 的 Region 细分与 Minecraft Player 个人经济模型不同。它说明**每人永远独立于场景的执行域不是唯一成熟做法** |
| **Unreal Gameplay Ability System** | [Epic 官方](https://dev.epicgames.com/documentation/unreal-engine/gameplay-ability-system-component-and-gameplay-attributes-in-unreal-engine?lang=en-US)：能力系统 ASC 可以在 Character/Pawn 上，也可以归 PlayerState，使角色死亡、重生或重新占据 Pawn 后保留长期冷却/能力状态 | 是**长期个人身份与短期世界 Actor 分开**的直接参考；但 ASC 具体放哪、HP/技能/装备如何划分可配置，**不会自动规定 B1 或独立线程** |
| **rAthena（Ragnarok Online 服务端模拟）** | [`pc.hpp`](https://github.com/rathena/rathena/blob/master/src/map/pc.hpp) 中 `map_session_data` 同时持有个人 `inventory` 和 `mmo_charstatus`；[源码说明](https://github.com/rathena/rathena/blob/master/doc/source_doc.txt) 区分 Map Server 在线运行与 Char Server 持久数据 | 证明传统 MMORPG 也采用**在线会话持有个人/战斗相关状态、角色存档由独立服务承担**的组合。独立存档服务不意味着战斗和 Inventory 必须在两个线程域 |

**对已选 B1 的影响**：AstaPS 采用 B1 是用户在 S2/R2 前提下做出的正式选择，不能用“其他游戏普遍这么做”作为其正当性论据。相反，TrinityCore/Folia 说明**同一个活跃角色的装备、HP、战斗技能在同一个执行所有者内完成**有很强的一致性优势，B1 为保留独立 R2 而增加的跨域数据同步成本应被正面测量和测试。B3 的“活动时执行权随世界迁移”值得作为严肃竞争方向保留，前提是**不破坏已批准的 R2 对 Inventory/个人经济操作的 UID 串行保证**；无需将所有 Player 状态移交给 World。

本节保留共享状态审计及历史 B1/B2/B3 比较；**现行正式决议为第 19 节 B1**。仍未批准新的 Session 调度对象或具体同步/持久化实现。文档阶段不运行 CI。

## 19. B1：World 拥有活动战斗态，Player 提供角色长期属性来源（2026-11-04 正式决议）

**用户正式选择 B1**，并明确设计理由：脱离 World 的角色战斗数值几乎没有独立意义。Player 可以拥有等级、天赋、突破/升阶、装备关系，并由这些资料求出基本数值；实际进入 World 后，还会受到环境、敌人、队伍、Buff、技能、能力效果等彼此联动的影响。因此战斗属性的**最终运行时计算与权威修改属于 World/S2**。

### 19.1 两类状态与一类运行时计算

| 层次 | 所有权 | 典型内容及边界 |
| --- | --- | --- |
| **角色个人资料和属性来源** | **Player / R2（按 UID）** | 角色身份及拥有关系、等级与经验、突破、命座、天赋、武器与圣遗物持有/装备关系、可持久角色成长进度。可在脱离 World 时计算角色的基础面板，用于展示、初始化及验证，但该面板不对活动战斗结算具有独立的最终写权 |
| **活动实体与实时战斗态** | **World / S2（按 World）** | 当前实体 HP、元素/特殊能量、护盾、动态 Buff/Debuff、能力状态、当前队伍与环境引入的数值修饰、世界行为关联的战斗属性、战斗行动及其先后顺序。活动角色与怪物在同一 World 内计算，World 负责将长期来源与实时修饰结合 |
| **一次具体战斗效果的上下文计算** | **World / S2** | 攻击者当前属性、目标防御/抗性/状态、元素反应、环境规则、条件触发和其他实体联动等共同决定该次结算。**不能把所有上下文效果都提前折算进一个“角色最终面板”并持久保存**；部分数值仅在具体攻击或事件发生时才有意义 |

**关键区分**：Player 为战斗提供长期的数值**来源**；World 确定这些来源在当前世界条件下形成什么运行时效果。角色所属 UID 不随 World 变化，Inventory/经济操作仍按 R2；World 中角色战斗实体的运行时状态遵守 S2。这样与既有 R2、S2、P1 和 E1 决议一致。

**额外区分（第 20.3 节已证实）**：B1 的 World 运行时状态权威与“普通攻击伤害由服务器独立重算”是两项不同决定。当前 AstaPS 的常规 `AttackResult.damage` 大多来自客户端，World/Scene 负责规则检查与实际扣血。未来是否改为完整服务器伤害公式属于单独的 C1/C2 选择；不要从 B1 推导出当前代码已经拥有全套原版 7.1 服务器伤害公式。

### 19.2 已明确的跨域方向与需验证的实际语义

1. **角色进入 World**：Player 向 World 提供该 UID 角色的长期资料或必要的只读数据快照；World 构建自己的活动战斗态，结合场景初始条件、队伍、能力/环境修饰。不可将 Player 的可变 `Avatar.fightProperties` 引用交给 World 并让双方各自写入。
2. **活动中换装/升级/天赋变化**：Player 先按 R2 完成并确认个人资料变更；随后向当前 World 交付**有顺序的属性来源更新**，由 World 结合当前动态效果重新评估受影响部分。**不得通过拷贝一张旧 `fightProperties` Map 覆盖正在持续变化的战斗状态**。若来源更新必须在下一次伤害前生效，需明确跨域操作的因果屏障；F1 命令的成功边界也要包含其所必需的 World 更新阶段。
3. **实际战斗**：World 处理伤害、治疗、元素能量、Buff、护盾与环境/怪物联动，不将每次 Tick/命中变成阻塞的 Player 请求。技能、Buff 的即时生效、消失及叠层属于运行时规则，须由世界模拟中的明确权威计算。
4. **玩家离场、换世界、退出或断线**：World 将按游戏规则**需要恢复/持久化**的字段（例如角色剩余 HP、必要能量）形成一致的结果交付 Player/R2；Player 负责个人存档与既有 UID 写入成功屏障。临时世界效果及只依赖当前场景的状态不得随意写入永久角色记录。跨 World 转移还需防旧世界的延迟结果覆盖新世界结果。
5. **非活动角色**：仍属于 Player 的长期角色资料；其离线或非活动展示数值可以由这些资料计算，不能与 World 中活动实体争夺运行时权威。**是否在团队但暂不出场时持续持有世界战斗态、如何保留能量及临时效果，尚待按原版行为审计**。
6. **网络与事件**：技能与战斗数据从 World 侧产生一致的快照/通知；客户端封包仍要遵守第 18.3 节的每 Session 顺序和旧会话失效要求。CLI、插件、Lua 对来源和战斗态的修改分别遵守 R2/S2 权限边界。

### 19.3 与现有上游代码的迁移差异

- [EntityAvatar.getFightProperties()](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/entity/EntityAvatar.java) **目前直接返回** [Avatar.getFightProperties()](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/avatar/Avatar.java) 的同一张 `Int2FloatOpenHashMap`；这与已选 B1 的分离要求冲突。
- `Avatar.recalcStats()` 将装备成长、队伍共鸣、能力增益和当前 HP 合在一次可变 Map 重建中；第 18.2 节列有 `WaterResonanceHelper`、`AbilityMaxHpRatioHelper`、`MavuikaSpiritHelper` 的直接证据。需要根据**来源和上下文**拆分计算归属；这里批准的是**权威边界**，还未批准算法、具体类/抽象层或缓存。
- `EntityAvatar` 的能量变化会调用 `Avatar.setCurrentEnergy(...)` 并排队保存角色。B1 下世界热路径应避免越权直接写持久角色对象，也不能因此丢失按原版规则需要恢复的能量。要让世界结果经 R2 一致性交付，普通异步持久化继续遵守 P1 和按 UID 的写入顺序。
- `Avatar.queueFightPropNotify()` 的独立延迟线程及 `PacketAvatarFightPropNotify` 的跨 World 数据读取要遵守快照一致性，不能在世界正在修改数据时读到半更新状态。

### 19.4 当前尚未选择的实现细节

**仍待基于原版 7.1 行为和上游源码验证**：基础/派生属性计算的具体位置；环境与目标相关修饰哪些计算时即时求值；活动/离队/死亡、World 转移时 HP/能量与临时效果的保留规则；装备变更对正在施放技能的生效时点；如何处理不同 Player 源版本和过期世界回执；F1 的跨域完成判定；是否需要不可变的增量/快照数据及其 API。没有授权创建新的 `CombatState` 类、第三执行域、数据库 Schema、线程池、兼容层或重复可变“基础面板”权威。

**测试方向**：同时换装与持续伤害、治疗和元素反应；多玩家协同 Buff 与环境修饰；角色死后/复活时数值恢复；当前世界有未结束技能时切换 World；来源更新与旧世界回执乱序；断线/重登录及持久化屏障；掉线后缓存的战斗通知不得影响新 Session。

**仅记录 B1 正式决议及用户的状态归属理由，不代表已经实施。**

## 20. B1 属性计算链路与伤害可信度：实际调用点审计（2026-11-04，研究）

**状态：遵循已选 B1，记录代码事实与尚未选择的协议边界。** 本节首先拆开三个经常混淆的判断：**谁拥有角色养成资料**（R2）、**谁拥有活动战斗状态与结算时序**（B1/S2）、**一次伤害的数值由客户端还是服务器计算并验证**（仍须评估）。最后一项无法仅从 B1 自动推导出来。

### 20.1 当前计算管线的五类输入和输出

| 层次 | 真实上游代码和行为 | B1 下应当确认的所有权边界 |
| --- | --- | --- |
| **A. 持久角色养成来源** | [`Avatar.recalcStats()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/avatar/Avatar.java) 从 `AvatarData`、等级、突破配置、武器等级/精炼、圣遗物主副词条、套装和天赋/命座 `openConfig` 中汇总数值及能力定义 | Player/R2 拥有等级、装备关系和可持久的成长资料；**由谁计算并缓存**只依赖这些来源的基础结果仍是实现问题，不能因预计算而赋予其对 World 活动战斗态的写权限 |
| **B. 当前实体的派生属性** | 同一个 `recalcStats()` 用 [`FightProperty.forEachCompoundProperty()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/props/FightProperty.java) 合成 HP、攻击、防御的 base/flat/percent；将 `fightPropOverrides` 写入后，以原 HP 比例重新设置 `CUR_HP` | 若角色处于 World，**World 的活动状态**才是战斗结算中的有效值。单纯重算个人来源不能清空 World 运行时的 HP、护盾、Buff、能力效果；HP 最大值变化时的保留百分比、保留绝对 HP 或其他规则必须按原版行为具体验证 |
| **C. 世界中的动态修饰** | [`WaterResonanceHelper`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/WaterResonanceHelper.java) 查活动队伍共鸣并加成 HP；[`AbilityMaxHpRatioHelper`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/AbilityMaxHpRatioHelper.java) 在能力 Modifier 增减时通过 `refresh()` 再次调用 `recalcStats(true)`，并保留当前 HP 比例；[`MavuikaSpiritHelper`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/MavuikaSpiritHelper.java) 在重算后重新注入动态加成 | World/B1 需要持续追踪这些修饰的来源、生效和失效条件；它们可能依赖队伍、世界实体、时间或能力状态，**不能将当前最终数值当作可持久装备属性重新保存到 Player** |
| **D. 一次能力动作的局部求值** | [`ActionGetFightProperty`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/actions/ActionGetFightProperty.java) 从施放者当前 `FightProperty` 与能力特殊参数求值，把结果放进目标 `globalAbilityValues`；[`ActionHealHP`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/actions/ActionHealHP.java) 汇总施放者/目标双方当前 HP、攻击、防御、治疗加成与能力配置计算一次实际治疗 | 结算须在当前 World 的真实实体上下文中求值。**这些结果可能仅在某一瞬间或一次事件内成立**，不适合预先存在 Player 的“最终角色数值”里 |
| **E. 战斗输入与实际扣血** | [`HandlerCombatInvocationsNotify`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/packet/recv/HandlerCombatInvocationsNotify.java) 解析客户端 `EvtBeingHitInfo.AttackResult.damage`，少数特殊路径修复目标或按服务器属性重算伤害；一般攻击把此结果加入玩家的攻击队列。[`Scene.handleAttack()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/Scene.java) 取得防御目标并直接把 `result.getDamage()` 交给 `GameEntity.damage()`，后者进行有效性检查、护盾/无敌判定、可取消事件、HP 变化与广播 | **现有 7.1 AstaPS 主要依赖客户端上报的伤害数额**，World/Scene 执行生命值结算和特定规则校正。B1 确定 World 的运行时状态写权限，**目前还未决定要不要把所有伤害公式迁为服务器独立计算** |

**重要细节**：[`FightProperty`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/props/FightProperty.java) 的同一个枚举同时列出 `BASE_ATTACK`、`ATTACK_PERCENT`、`CUR_ATTACK`、`CUR_HP`、元素能量、治疗加成、反应暴击相关属性等。**不能按 enum 或属性编号自动划分“哪些是 Player、哪些是 World”**。应根据*属性来源、生命周期、谁修改、在哪次计算中使用、是否须持久化*逐项分类；同一属性可能存在个人资料推导值、World 实时值和一次动作临时求值结果，但这不意味着要保存三份可变权威对象。

### 20.2 一次战斗动作具有顺序依赖，不能机械拆成异步散任务

[ActionApplyModifier](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/actions/ActionApplyModifier.java) 在 `onAdded` 中用 `executeActionNow()` **依次处理**子动作；[DoActionByElementReactionMixin](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/mixins/DoActionByElementReactionMixin.java) 的注释特别标明 `ApplyModifier → GetFightProperty → HealHP` 必须顺序执行。这个链条可能先建立 Buff、再读取增强后的属性、最后决定治疗量；将每个小动作独立投递工作线程会得到不同结果。

[HandlerUnionCmdNotify](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/packet/recv/HandlerUnionCmdNotify.java) 依次处理批量子包，在最后刷新 Combat/Ability Invoke，再调用 `applyQueuedAttacks()`；单独到达的 CombatInvocationsNotify 则自行结算队列。当前使用 `ThreadLocal` 标记批处理状态。S2/R2 迁移时，要**保持原有批内因果顺序、所有必要的世界态更新先于对应的攻击结算**；不能简单给每个子包创建异步任务，并寄希望于两个逻辑队列各自 FIFO。

B1 的最小交互逻辑应先表达**业务依赖**：Player 个人变更完成 → 把新的长期属性来源交给目标 World → World 在有序的模拟边界应用并计算实时修饰 → 后续相关战斗动作看见新版本。当前版本号、增量/全量更新、进行中的技能是否采用旧快照、玩家切 World 时如何拒绝旧回执，都还需要设计和测试。**不自动增添统一依赖图引擎、事件总线、属性复制框架或三层持久缓存。**

### 20.3 从战斗状态写权限到伤害数值可信度，是独立的架构选择

**观察到的上游行为**：普通攻击的 `AttackResult.damage` 主要由客户端提交；`Scene.handleAttack` 和 `GameEntity.damage` 能检查当前世界中实体是否存在、是否无敌、护盾吸收、插件取消、有限数值等，**这些校验不等于独立重算过攻击者属性、技能倍率、敌人抗性和反应公式后的完整服务器权威伤害**。特殊技能（如该 Handler 中的部分 Hexenzirkel 攻击）存在有针对性的服务端重新计算，不能据此宣称所有伤害已服务器重算。

这是一项独立于 B1 的架构决定。**用户已明确选择本重构分支现阶段维持 C1**；下面保留 C1/C2 对比作为决策依据，不表示立即引入 C2：

| 方向与当前状态 | 对一次伤害数值的处理 | 实际代价与影响 |
| --- | --- | --- |
| **C1：沿用客户端计算的主要攻击伤害，World 校验并应用（本分支已选）** | 以现有客户端 `AttackResult` 为主要伤害输入，继续研究实体归属、时序、异常上限、能力特例、作弊与多人可信度 | 改动相对可控，能先落实 B1/S2/R2；客户端与服务端数值权威不完全一致，必须明确最低限度的可信性规则 |
| **C2：World 服务器完整计算或复核攻击伤害（未批准）** | 客户端提供输入/命中事实，World 根据攻击者、技能、目标、环境、元素反应、随机因素等独立推导伤害数值 | 可建立更强的数值权威，但 7.1 客户端能力公式/动态修饰与服务端数据覆盖度是巨大研究及逆向工作；需专门依据原版 7.1 客户端/metadata 和严格回归测试，不能凭社区表格补推 |

**当前有效决定：本分支采用 C1。** B1 继续要求 World/S2 对 HP、Buff 和世界实体变化进行唯一有效的处理，现有客户端伤害输入、必要的特殊技能重算、护盾/无敌/伤害事件和批处理顺序均作为迁移时的行为基线。C1 不意味着客户端数值天然可信；保留现有有效性检查并通过实测识别风险，但不擅自添加固定伤害上限或伪造一套不完整的服务器公式，也不在重构中悄悄更改原有伤害语义。**C2 是将来可以单独提出的研究方向，当前没有批准其实现，也没有确定升级日程。**

### 20.4 实施前应具备的证据与验收情境

1. 记录一项**纯个人来源变更**（如升级武器）在角色**未进入世界、活动中、联机世界中**分别改变哪些属性；World 的动态 Buff 不应被覆盖，Player 的物品状态不应等待 World 才能被所有个人业务读取。
2. 固定一项**动态环境/能力修饰**，先触发 Buff 再更换装备，或先更换装备再触发 Buff；检查实际属性、护盾/HP 保留行为与 `recalcStats()` 原有 hook 顺序。
3. 对**一次治疗及一个复合元素动作**，保留 `ApplyModifier → GetFightProperty → HealHP` 的原始时序与所得数值；不要把未来多线程测试简化成只检查最终数值。
4. 捕获一条常规 `AttackResult` 的处理轨迹：客户端上报伤害、特殊修复、`Scene.handleAttack`、`EntityDamageEvent`、防护、扣血、通知。当前 C1 应记录并保持该链路的输入与实际结算语义；若将来单独批准 C2，再要求从原版 7.1 证据重算一致并可追查差异。
5. 从 World A 切到 B，旧 World 的 Buff、延迟治疗/伤害或 HP 回传不得作用于 B 的新实体；个人长期来源与需要恢复的生命/能量不能丢失，保存写入继续按 UID 排序。

**本节记录源码审计和本分支已选的阶段性 C1 决议**；B1/S2/R2/F1 等正式决议不变，C2 未获批准。没有修改 Java/Gradle、数据库结构或线程模型；研究文档无需 CI。

## 21. B1 的来源更新、实体生命周期与 HP/能量持久化（2026-11-04 源码审计）

**状态：B1、S2、R2、F1、C1 已定；本节只推导它们必须满足的行为不变量，不预先批准类、消息总线、独立服务或数据库结构。** 研究中的一个重要区别是：B1 所说的 *World 拥有活动战斗态*，落实到代码时活动实体实际**随 Scene 重建和队伍编辑发生创建/销毁**。World 是串行计算域，实体存活期却不等于 World 存活期。

### 21.1 目前进入场景、组队和角色切换的真实行为

| 场景 | 真实源码路径 | B1 迁移中的不变量 |
| --- | --- | --- |
| 进入 Scene | [`Scene.addPlayer → setupPlayerAvatars`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/Scene.java)：从 Player `TeamInfo` 读取各角色 `Avatar`，**为队伍中每个角色**构造 `EntityAvatar(scene, avatar)`，并存于 `TeamManager.activeTeam` | 即使当前只显示一位角色，场景中也可能存在**多位活动队伍角色的运行时实体**；进入时要装载各角色个人资料及应保留的战斗状态 |
| 切换上场角色 | [`TeamManager.changeAvatar`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/player/TeamManager.java)：在同一 `activeTeam` 列表内切换 `currentCharacterIndex`，用 `Scene.replaceEntity` 替换上场实体；同步能量显示 | **换上场角色不等于销毁旧角色战斗态**；离场角色的 HP、能量、持续效果的保留应以原版实际规则判定 |
| 重新编队 | [`TeamManager.updateTeamEntities`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/player/TeamManager.java)：按 `avatarId` 重用部分 EntityAvatar、创建新实体、移除旧实体，随后更新队伍共鸣/能力通知 | B1 的运行态随实际实体生命周期迁移；重用的实体不能因装备来源重算就丢失世界修饰，移除的实体必须先处理可恢复状态 |
| 切换 Scene/World | [`Scene.removePlayerAvatars()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/Scene.java) 先 `removeEntity`，再对所有团队实体 `EntityRuntimeStateCleanup.clear`，最后 **`activeTeam.clear()`**；下个 Scene 的 `setupPlayerAvatars` 重新创建实体 | 同一个 World 内跨 Scene 就会销毁/重建活动实体。World A→B 还涉及两个 S2 执行域；需要**先取得一致的应保留结果，再销毁旧实体，最后构建新实体**。清理暂态和保存必要状态的先后顺序必须明确 |
| 退出登录 | [`Player.onLogout()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/player/Player.java)：先退出副本、`World.removePlayer`，最后 `TeamManager.saveAvatars` | 退出的可恢复状态必须在活动实体消失前交付 UID/R2；然后走持久化和既定重新登录读取成功屏障。不能把 `saveAvatars` 当成已实现的跨域状态提交 |

具体需要拆清的事件不是简单的“实体存在/不存在”：**Player 长期角色拥有关系、当前活动队伍、正在场景中显示的实体、可恢复的 HP/能量/特殊状态、随 Scene/技能消失的临时效果**具有不同存活期。B1 允许在 World 内复用必要的派生数值缓存，但避免制造另一份跨域可写的 Player 属性权威。

### 21.2 已核实的退出时序缺口：保存队伍可能发生在队伍已清空后

上游代码按以下顺序执行：

1. [`Player.onLogout()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/player/Player.java) **先调用** [`PlayerRuntimeStateCleanup.clear(this)`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/player/PlayerRuntimeStateCleanup.java)，清理命之契、技能临时状态及定时任务等，再退出副本并调用 `this.getWorld().removePlayer(this)`。如果 B1 的可恢复信息依赖其中的活动状态，**离场结果提取必须发生在这类有损清理之前**。
2. [`World.removePlayer()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/World.java) 调 `scene.removePlayer(player)`；[Scene.removePlayerAvatars](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/Scene.java) 会 **`team.clear()`**。
3. `onLogout` 的 `finally` 才执行 `this.getTeamManager().saveAvatars()`，但 [`TeamManager.saveAvatars()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/player/TeamManager.java) 只遍历 `getActiveTeam()` 调 `entity.getAvatar().save()`。在常规有场景的退出路径上，**此时 activeTeam 通常已经为空，循环不会保存任何 Avatar**。

并且 [`Avatar.@PrePersist`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/avatar/Avatar.java) 只有在实际持久化该 Avatar 时，才从 `fightProperties` 取 `CUR_HP`、对应元素能量等生成存档数值；它本身不会触发保存。战斗中扣 HP 一般修改共用的 `fightProperties`，是否还有其他能力、物品或定时流程顺手 `Avatar.save()`，取决于具体战斗路径。**因此目前退出时没有一条可依赖的“全部活动角色最后 HP/能量成功持久化”保证。**

这是从控制流直接得出的**保存时序缺口**，不等于已进行实机测试并证实每次退出都丢血量。该缺口对 B1 更敏感：分离对象后，异步 DB 序列化不得再从 World 的活动 `fightProperties` 偷读；应由 World 形成必要的**一致性结果**，在对应 UID 的 R2 域接收并构建持久个人记录，然后按已有有序异步 DB 写入规则保存，并维护重登录读屏障。**退出保存不能在旧 World 仍可更改这些值时拍一张无版本快照就宣布完成。**

### 21.3 来源更新的建议消息方向与业务完成边界（未选具体传输 API）

```text
Player/R2：持久角色资料、装备、成长、队伍长期配置
   │ 个人业务有序完成，生成对应角色的来源变更说明/稳定只读视图
   ▼
World/S2：确认角色仍在本 World/Scene、来源是否仍为当前版本
   │ 在世界串行边界处理装备、共鸣、动态 Modifier 与技能状态
   ▼
活动 EntityAvatar：只由 World 写入当前 HP、能量、Buff、实时 FightProps
   │ 从同一活动状态生成 Scene/Avatar 客户端通知
   ▼
必要时由 World 提供恢复数据 → Player/R2 → 有序 DB 保存与登出屏障
```

- **对普通换装/养成**：R2 在不阻塞 World 的情况下完成个人记录；若同一操作的外部命令 F1 要求活动角色生效才算成功，其完成回执需要等**对应 World 侧必须完成的副作用**，但 Shell 可以等待，World/R2 线程不互相阻塞。是否在非 World 角色上计算基础属性及其缓存位置仍是实现选择。
- **对来源和运行时状态融合**：已有 `Avatar.recalcStats()` 在重算时会清空整个 Map，并以当时 HP 百分比恢复生命；B1 必须区分装备造成的基础属性变化与临时 MaxHP 比例修饰，不能把旧 `CUR_HP`、能力增益和 `fightPropOverrides` 直接作为另一份 Player/World 并行可写表。是否按比例/绝对值保留 HP，因来源和原版能力规则而异，**此处不擅自统一算法**。
- **对客户端两个视图**：[PacketAvatarFightPropNotify](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/packet/send/PacketAvatarFightPropNotify.java) 构造 `AvatarGuid` 对应角色面板；[PacketEntityFightPropUpdateNotify](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/packet/send/PacketEntityFightPropUpdateNotify.java) 构造 `EntityId` 对应场景实体数值；[PacketSceneTeamUpdateNotify](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/packet/send/PacketSceneTeamUpdateNotify.java) 在多人场景同时编码 `EntityAvatar.toProto()` 与 `Avatar.toProto()`。当前这些路径依赖同一张 Map 的偶然一致。**B1 拆开属性所有权后，面板与场景实体的展示需要来自相互一致的状态版本**；不应把“两个包”误认为“必须保存两套最终战斗属性”。
- **对离场/存档**：World 需有一个结束本轮活动实体写入、交付恢复结果的边界；同一 UID 新 World 应拒绝旧 World 迟到的写入。现有 R2 业务序列与 DB 保存序列虽有序，但无法单独排除**旧 World 的回执在新 World 状态之后到达**。具体关联凭据/序号、是否保存非活动队员的暂态和异常退出恢复机制留待源码与实测推导。

**基于已定 B1 的建议**：先冻结上述所有权与业务完成行为测试，再评估源数据是否需一次性快照或按领域增量。当前无须选择消息总线、版本字段名称、`CombatState` 类型或第三类调度域。

## 22. S2 多 World 并行的隐藏碰撞：实体 ID 被当成全局身份（2026-11-04 源码审计）

**结论有明确代码证据**：[`World.nextEntityId`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/World.java) 是**每个 World 自己从 0 开始的 AtomicInteger**，`getNextEntityId(EntityIdType)` 按实体类别前缀加本 World 递增序号。因此整数 `entity.getId()` 对一个 World 内的实体有区分能力，**却没有跨 World 的全局唯一性保证**。

然而多个战斗辅助系统使用全服 `static Map<Integer, ...>` 直接以 `entity.getId()` 为键：

| 真实路径 | 证据 | S2/B1 的具体风险 |
| --- | --- | --- |
| 最大 HP 动态 Modifier | [`AbilityMaxHpRatioHelper.ACTIVE`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/AbilityMaxHpRatioHelper.java) 是全局静态 `Map<entityId, Map<modifierName, Entry>>`；`onModifierAdded`、`totalRatio`、`clear` 都只传裸 ID | 两个 World 恰好有相同整数 ID 的实体时，可**共享或清除对方的 Modifier**；`clear` 无 World 或实体引用身份核验。即使所有操作都在正确的 S2 域内也无法解决键冲突 |
| 阿蕾奇诺命之契与爆发 | [`ArlecchinoBoLUtil`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/ArlecchinoBoLUtil.java) 有大量静态 `Int2LongMap`/`ConcurrentHashMap<Integer,...>`；`clearEntityState(entityId)` 删除对应键；[`ArlecchinoBurstBoL.PENDING`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/ArlecchinoBurstBoL.java) 同样按整数 ID 索引，但**部分操作额外验证对象身份** | 相同 ID 的不同 World 技能会互相覆盖、错误抑制或清理；**不能把 ArlecchinoBurstBoL 有局部身份校验的逻辑一概宣称无保护**，需逐 Map 审计 |
| 玛薇卡防御削弱领域 | [`MavuikaSpiritHelper.defShredApplied`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/MavuikaSpiritHelper.java) 为静态 `Map<Integer, Float>`，扫描 `player.getScene().getEntities()` 并按怪物 ID 缓存防御削弱；重复任务由全局 `GameServer.scheduler` 驱动 | 不同 World 的同 ID 怪物可能使对方**跳过增益或错误恢复**；且定时回调可能在 World 执行域外修改 Scene 实体，违反 S2 |
| 场景销毁清理 | [`EntityRuntimeStateCleanup.clear(entity)`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/entity/EntityRuntimeStateCleanup.java) 取得一个 `entityId` 后连续调用多个 Helper 的 `clearEntityState(entityId)` | World A 实体销毁可删除 World B 同 ID 实体的全局能力状态。按 ID 清理的行为**不能仅以使用 ConcurrentHashMap 就视为线程安全或逻辑隔离** |
| 能力动作池 | [`AbilityManager.executeAction()/executeMixin()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/AbilityManager.java) 使用 **4 线程静态全服 `eventExecutor.submit`**，之后才在异步回调里执行对目标实体的 Modifier/Ability Action；另有 `executeActionNow()` 保留同步顺序 | 即使实体 ID 键正确，**池中的异步能力回调依然可能直接从 S2 以外的线程修改同一 World 状态**，且与同 World 伤害结算、队伍变化乱序；`isLive` 只能部分淘汰已失效实体，不能代替 World 侧执行权与正确的动作顺序 |

**设计约束（B1/S2 的直接推论，具体存储结构未定）**：

1. **World/Scene/Entity 的状态必须按真实作用域识别**。EntityId 是原版协议中的 World 内运行时标识，可以保持协议原貌，但在服务器全局静态索引里必须有明确 World/实体身份或将临时状态真正归回 World/实体所有者。不能通过凭空引入全局唯一 EntityId 改变 7.1 封包意义来掩盖内部索引错误。
2. 需把**索引键冲突、写入执行域、定时回调执行域、清理生命周期**分别检查；复合键消除碰撞却不保证回调在 World 线程安全运行，反过来 S2 队列正确也无法修复全局裸 ID 冲突。 [`AbilityManager.executeActionNow()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/AbilityManager.java) 已承认 Modifier 动作链的同步因果顺序；迁移时不得一股脑把全部同步动作也改到无序池。
3. **不要一律把所有静态 Map 移入 World**：有些键是 UID、持久 Avatar Guid、配置 Hash 或全局游戏规则，可能需要不同生命周期。重点先审战斗中 `getId()` 产生的实时 EntityId、持有 Entity 引用的全局任务与所有 `clearEntityState`。
4. **最小验证**：启动两个不同 World，使它们产生相同数值的怪物/角色 ID；只对 A 添加 MaxHP Modifier，检查 B 不受影响；销毁 A 对应实体，再验证 B 的 Modifier 未清除；同时触发不同 World 的阿蕾奇诺 BoL/玛薇卡减防持续效果与场景切换，检查两边数值和回调独立。
5. **修复优先级**：这是 S2 多 World 并行和 B1 World 实时状态唯一写权的前置风险，应在大规模拆分线程之前有一套能真实碰撞的回归测试；**目前仅源码证明风险路径，没有实机复现**，不能把潜在冲突描述为已观察到游戏故障。

不授权新增模块、公共缓存框架、持久字段或无关的兼容逻辑。本节仍处于研究，不运行 CI；待开展代码实施并要求用户测试/向上游提交时按既定要求运行 CI。

## 23. 调度任务的运行权与时间语义：S2/R2 的关键迁移入口（2026-11-04 源码研究）

**研究结论**：已有 `GameServer.scheduler`、`Scene.scheduler`、`World.eventExecutor`、`AbilityManager.eventExecutor` 并行存在，但它们提供的主要是“何时/在哪个线程调用 Runnable”，没有统一定义 **Runnable 可以修改哪位 Player、哪一个 World**。当前 `GameServer.onTick()` 的串行顺序暂时掩盖部分越权调用；真正实施 S2/R2 后，不能按类名判断执行域，也不能给现有异步池加一把全服锁了事。

### 23.1 目前实际有哪些调度入口

| 入口 | 代码事实 | 当前架构下的状态权威风险 |
| --- | --- | --- |
| `GameServer.onTick()` | [GameServer](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameServer.java) 由一个 `Timer.scheduleAtFixedRate` 驱动，依次处理**全部 World Tick → 全部 Player Tick → 全局 scheduler → ServerTickEvent**，并在数据库 Watchdog 判定故障时暂停本轮 Tick | 这些步骤现在同属一个 Timer 调用栈；S2/R2 后不再能依靠该物理顺序保护跨域状态；数据库故障暂停全局 Tick 也是现有业务策略，不能重构时无意改变 |
| `Scene.scheduler` | 每个 Scene 自己持有 [ServerTaskScheduler](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/scheduler/ServerTaskScheduler.java)，在 [Scene.onTick](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/Scene.java) 内仅于 Scene 未暂停时驱动同步任务；`ExecRefreshGroupSuite` 已将脚本加载后修改 Scene 的操作交回 Scene scheduler | **这是可复用的正确方向**：由 S2 World 驱动 Scene 的时间任务。但 scheduler 对象属于 Scene 并不保证异步 callback、不安全插件调用也归 World；任务仍应检查实体/Scene 生命周期 |
| `GameServer.scheduler` | 在整个服务器的 World/Player Tick 结束后运行；[`QiqiEHealHelper`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/QiqiEHealHelper.java) 的周期治疗从该任务中直接读 `EntityAvatar` 并调用 `active.heal()`；[`MavuikaSpiritHelper`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/MavuikaSpiritHelper.java) 的周期减防直接修改怪物属性；[`ScriptLib`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/scripts/ScriptLib.java) 的延迟通知也可经该入口访问 Scene | 目前这些任务由 Timer 线程在各 World Tick 之后执行；**S2 后仍从“全局任务线程”直接改多个 World，将破坏各 World 的唯一写者规则**。未来任务的**目标 World**由实际操作决定，不能因为任务来自 `GameServer` 就直接写 |
| `World.eventExecutor` | [World.queueTransferPlayerToScene](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/World.java) 将任务投入全服静态 4 线程池，先 `Thread.sleep(delayMs)`，再直接调用 `transferPlayerToScene(player,...)`；保存一个 `Future` 供取消 | 多个延迟传送会占用池内工作线程等待；睡眠结束回调可能与 World Tick、UID 业务、其他世界迁移同时修改 `Player.scene/world`、活动角色实体；`Future.cancel` 并不自动证明世界变化已被取消 |
| `AbilityManager.eventExecutor` | [AbilityManager.executeAction/executeMixin](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/AbilityManager.java) 用全服静态 4 线程池独立提交动作，`isLive()` 在执行前确认某些实体仍属于活跃队伍/当前 Scene；另有 `executeActionNow()` 保证必要动作顺序 | 存活检查不能给回调授予 World 写权，也不能修复同一 World 内动作先后；C1 保留客户端伤害输入，不等于允许 Ability 副作用在任何线程执行 |
| `ServerTaskScheduler.scheduleAsyncTask()` | `runTasks()` 为异步任务创建**新的物理 Thread**，完成后在调用 `runTasks()` 的线程执行其回调 | 若未来以异步任务做耗时计算，**计算阶段不能直接写 World/Player**；完成后的应用阶段仍要投递回实际所有者，不能只凭“回调是在 Tick 时执行”就默认该回调属于目标 UID/World |

**时间单位矛盾**：[`ConfigContainer.Game.tickRateMs`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/config/ConfigContainer.java) 的**默认值是 200 ms**，[`GameServer.start()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/game/GameServer.java) 直接将它用作 `Timer` 周期。现有 [ServerTaskScheduler 文档注释](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/scheduler/ServerTaskScheduler.java) 却写“一 Tick 是一真实秒”，[`TowerManager`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/tower/TowerManager.java) 同样假设 scheduler 延迟参数以**秒**计；另一批场景脚本如 [SceneScriptLifecycleTest](https://github.com/MeChen618/AstaPS/blob/main/src/test/java/emu/grasscutter/scripts/SceneScriptLifecycleTest.java) 则明确用 `ceil(1000 / tickRateMs)` 把真实秒数换成 Tick。**这已是两套不一致的单位约定**。更具体地，`TowerManager.secondsToSchedulerDelay(int seconds)` [实际实现](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/tower/TowerManager.java) 只是 `Math.max(1, seconds)`，并没有按 Tick 毫秒值换算；因此在**默认 200 ms 配置、无严重延迟**时，标称 **10 秒的 mid-half fallback** 约在 **10 Tick，即 2 秒** 后触发，运行时实际值仍受配置与调度卡顿影响。这是明确的代码单位缺口，可先用单元/模拟 Tick 证实，不需推断客户端表现。

### 23.2 Scheduler 本身还有三个已确认的机制缺口

1. **只有异步任务时根本不会运行**：[`ServerTaskScheduler.runTasks()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/scheduler/ServerTaskScheduler.java) 开始即 `if (this.tasks.size() == 0) return;`，而异步任务在下方 `asyncTasks.values()` 遍历。若该 scheduler 没有任何同步任务，单独 `scheduleAsyncTask()` **不会被启动或收尾**；这可以用独立单元测试验证，无需客户端。
2. **异常和跨线程完成可见性**：[`AsyncServerTask.run()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/scheduler/AsyncServerTask.java) 在 `task.run()` **正常返回后**才写 `finished=true`，没有 `finally`，回调的完成状态由另一个线程轮询，`started/finished` 也并非 volatile/同步字段。任务抛异常时无法标记完成或触发正常收尾；无同步时跨线程读到标志的时间亦无保证。
3. **任务取消作用域混淆**：[`ServerTask.cancel()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/scheduler/ServerTask.java) 无条件调用 `Grasscutter.getGameServer().getScheduler().cancelTask(this.taskId)`，但 `ServerTask` 也可能来自 **Scene 自己的 scheduler**，同整数 taskId 在两个 scheduler 中可并存。若调用 Scene 任务的 `ServerTask.cancel()`，可能错误取消全局 ID 相同的任务而保留原 Scene 任务。[`ServerTaskScheduler.cancelTask()`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/scheduler/ServerTaskScheduler.java) 还只从同步 `tasks` Map 删除，没有处理异步队列的独立取消语义。

这三点是**源码可证明的行为缺口**，但未在本轮做构建/运行回归；它们属于有边界的 scheduler 修正候选，不能据此提前决定把所有定时任务迁到新框架。

### 23.3 与 S2/R2/B1 一致的执行约束（建议，具体 API 未定）

- **调度负责时间，所有者负责修改**：全局计时可以产生“Qiqi 治疗现在到期”的信号；实际治疗与 Buff 调整须由该角色**当前所在 World/S2** 在正确时序执行，身份及场景失效时拒绝。角色个人成长/库存回调由其 **UID/R2** 执行。
- **跨 World 传送是显式生命周期事务**：延迟器只负责在指定时间发起转移请求。转移过程由 World A、Player UID、World B 各自管理自己的状态，通过非阻塞续接确认旧实体下线、新实体初始化和新 Session 有效性，不能在任意睡眠线程直接改三者；此处不假设已决定原子性、超时或失败回退协议。
- **同一 World 的技能动作因果顺序必须存在**：`AbilityManager.executeActionNow()` 已指出“先写 Modifier 再读属性”的业务要求。独立异步计算可以在不读取/修改 World 可变实体的情况下执行；结果与安全回执须回到 World 域应用。需要等待另一域时不得占住正在运行的 S2/R2 执行器。
- **保留合理的停顿语义**：当前 Scene 暂停时停止自己的 scheduler，但全局任务及某些 Scene Tick 逻辑仍可能运行。迁移之前逐类决定哪些时间使用**现实经过时间**、哪些服从**World/Scene 模拟时间**、哪些属于**玩家账号日常时间**；不要把所有 delay 都改成墙钟毫秒而无意破坏剧情倒计时或暂停。
- **按生命周期废弃旧任务**：同一玩家重登录、Scene 重建、World A→B 转移、实体销毁会令延迟任务绑定的实体引用过期。任务在实际执行点仍需检查所属 World/Scene、当前实体世代及操作有效性；仅持有旧 `Player` 或旧 `EntityAvatar` 引用、`Future.cancel` 或 `isLive` 布尔量均不足以覆盖全部竞态。
- **控制故障传播**：`GameServer` 当前在数据库 Watchdog 故障时跳过 Tick，以控制无法落盘的状态积压。若让各 World 独立推进，必须明确仍然接受相同的全服故障暂停行为或另行正式选取降级策略，不能默默绕过持久化 P1。

### 23.4 可立即执行的回归测试设计（不要求玩家先实测）

| 测试主题 | 真实初始条件 | 检查点 |
| --- | --- | --- |
| 异步任务独立运行 | 一个全新的 `ServerTaskScheduler`，仅安排 `scheduleAsyncTask` | 发现当前提前返回缺口；后续修复验证“启动一次 → 结束一次 → 完成回调一次” |
| 取消域隔离 | GameServer 和 Scene scheduler 分别注册**同整数 taskId**，取消 Scene 的 `ServerTask` | Scene 自身任务应停止，全局任务应继续；当前实现不满足 |
| 时间换算 | 在 `tickRateMs=200` 下分别计划 1/10 秒剧情任务与 Tower 中转任务 | 真正延迟须符合原版业务预期，确认不同调用点的 unit 转换；禁止以注释为测试依据 |
| World A 与 B 的周期 Buff | A、B 同时有 Qiqi 治疗和 Mavuika 减防，且两边产生相同整数 EntityId | 各自在自己 World 中生效，受限于正确的定时和身份；World A 销毁不改变 World B |
| 传送任务晚到 | `World.queueTransferPlayerToScene` 休眠中玩家退出 World A 并进入 B，或断线重新登录 | A 的旧任务不得将新实体又移回旧 Scene，也不得越权清理 B |
| 技能动作链 | 先应用 Modifier 后立即 GetFightProperty/HealHP；同时 World Tick 和其他玩家命中 | 同 World 内连续逻辑操作不乱序；不同 World 可并行，C1 伤害输入仍按原先协议 |
| 故障/停顿 | 数据库 Monitor 判故障、Scene 暂停和恢复、某任务抛异常 | 持久化 P1 不丢；世界停顿与真实时间事件按预期；异步任务无永久悬挂 |

已有 [GameServerWorldTickConcurrencyTest](https://github.com/MeChen618/AstaPS/blob/main/src/test/java/emu/grasscutter/server/game/GameServerWorldTickConcurrencyTest.java) 检查当前 Tick 的全局集合锁及注册并发，[SceneScriptLifecycleTest](https://github.com/MeChen618/AstaPS/blob/main/src/test/java/emu/grasscutter/scripts/SceneScriptLifecycleTest.java) 覆盖场景计时；可在这些现有测试方向上延伸，不需要另建仓库或引入新 Actor 库。此处只给出测试策略，不创建测试代码、不运行 CI。

**研究结论**：下一轮实现前须把 scheduler 调用点按 **World / UID / 纯异步计算 / 全服时间与管理任务** 分类，再选最少必要的执行途径。S2/R2 的所有权规则已经批准，具体定时器/执行器的接口形态尚未决定。不能为了“统一调度”擅自加入第三个状态权威或强制所有业务经过全局串行入口。

**截至第 23 节仍未进入代码改造阶段**。以下第 24 节继续开展静态源码归属审计，无 Java 迁移。与 [CLI/JLine 4 研究](cli-jline4-shell-research.md) 配套使用；后者记录已经确认的细粒度行为决议。

## 24. 调度调用点的归属分类与首批迁移阻塞点（2026-11-04 源码审计）

**状态：只补足第 23 节提出的调用点分类；S2/R2/B1/C1/F1 决议不变。** 本轮逐一核对当前上游 `main@ac9a7e6` 的生产调用，而不根据 `scheduler`/`Executor` 类名推定谁可修改状态。一次业务可能涉及 **何时到期、哪位 UID 的长期状态、哪个 World 的实时状态、与哪次 Session 关联** 四个维度；这四个维度不一定对应四个可变对象，更不构成已批准的通用调度框架。

### 24.1 源码调用点及归属

| 现有来源与可核实行为 | 期望归属 / 真实问题 | 第一阶段验证价值 |
| --- | --- | --- |
| [QiqiEHealHelper](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/QiqiEHealHelper.java) 的治疗周期从全服 scheduler 查 UID 和活动 EntityAvatar、调用 `tickHeal`；[MavuikaSpiritHelper](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/MavuikaSpiritHelper.java) 的 C2 光环周期遍历当前 Scene 怪物并变更减防 | **World/S2** 持有治疗、敌方 Buff 与战斗实时状态；UID 可用于重新查找玩家，不能让全局 Timer 成为这些状态的写入者。Qiqi 的任务键按 UID 保存，实际目标却是 World 中的实体；跨 World/离线时须避免任务延迟执行到新实体 | 同 UID 换 World、两个 World 同数值 EntityId、Buff/治疗到期 |
| [HutaoC6Helper](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/HutaoC6Helper.java)、[ShinobuC6Helper](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/ShinobuC6Helper.java) 在全服 scheduler 定时撤销 EntityAvatar 属性；[EscoffierHealUtil](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/EscoffierHealUtil.java) 按 UID 和 EntityId 回查治疗目标 | **World/S2**；实体 ID 及捕获的 EntityAvatar 引用均不能代替 World 身份和实体生存期证明。按 UID 找到新登录 Player 也不能证明旧治疗仍有效 | 清理旧实体后旧任务到期、会话替换、双 World ID 相同 |
| [SceneScriptManager](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/scripts/SceneScriptManager.java) 的 `callEvent` 提交到全服 4 线程 `eventExecutor`；按特殊组分别维护 `blossomGroupEvents` 等顺序链，普通事件仍可并行执行 `realCallEvent`，通过 ScriptLib 触及 Scene；[DomainDungeonHelper.finishDomainEnterDeferred](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/dungeons/DomainDungeonHelper.java) 在同一池直接加载/刷新场景、应用出生点 | **World/S2** 拥有 Scene/实体修改，耗时资源准备可以异步；按特殊剧情组的 Future 链仅保证该链上的局部顺序，未给整个 World 提供唯一写入权。旧异步回调必须在已销毁 Scene / 重登之后失效 | 同 Scene 多组事件、进入副本后立刻离开/重进、脚本加载与 Tick 重叠 |
| [QuestManager](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/QuestManager.java) 通过 **全服静态单线程** `eventExecutor` 排列所有玩家的 `queueEvent`，登录恢复也向此池提交任务；[QuestSystem.triggerExec](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/QuestSystem.java) 同样提交 Quest Exec | 玩家任务进度、奖励与个人状态主要归 **UID/R2**，触发场景 Lua/刷怪则跨 **World/S2**。当前“所有 UID 共用一线程”阻碍不同玩家并行；**线程池串行不等于跨来源的 UID 顺序**，因为 Player Tick 与网络/世界事件依然直接触碰任务相关状态 | 两个 UID 同时推进任务、同 UID 不同来源事件排序、任务执行引起 Scene 效果 |
| [World.queueTransferPlayerToScene](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/World.java) 在全服静态池 `Thread.sleep(delayMs)` 后调用 `transferPlayerToScene`，实际执行会移除旧 Scene 玩家、创建新 Scene 实体并更新 Player/队伍 | 明确的 **World A → UID/R2 → World B** 生命周期协调问题；延迟只提供到期信号，不能让睡眠线程直接拥有三方状态。现有 `Future.cancel(true)` 可中断休眠，但不能作为已开始转移的回滚证明 | 延迟传送时换 World/退出、旧新回调竞争、跨域中途失败 |
| [EntryNotice](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/player/EntryNotice.java)、[PushTipsSuppressHelper](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/player/PushTipsSuppressHelper.java)、[PacketPlayerStoreNotify](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/packet/send/PacketPlayerStoreNotify.java) 从全局 scheduler 延后触发公告/玩家提示/登录商店通知 | 以 **UID、登录会话有效性** 为主，实际发包还涉及 Session 出站顺序；单用 `isOnline`、`isConnected` 或捕获旧 Player 不足以识别同 UID 的新登录。此类任务不需要为发通知建立 World 归属 | 重登后旧登录定时包、同 UID 旧 Session 回调与新 Session 串包 |
| [ScriptLib.DelWorktopOption](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/scripts/ScriptLib.java)、[DomainChallengeKeyHelper](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/dungeons/DomainChallengeKeyHelper.java) 从全局 scheduler 延后向 Scene 广播或刷新机关；[ExecRefreshGroupSuite](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecRefreshGroupSuite.java) 已选择 Scene scheduler，并重新核对 World/Scene/Quest 身份 | 前两者属于应交回 **World/S2** 的场景任务；第三者可作为现有最小切口参考，但 Scene scheduler 只在既定 Tick 上由 Scene 驱动，仍须解决其他异步来源越权与 Scene 删除 | 场景销毁后旧机关刷新、两个 World 的同 SceneId 机关任务独立 |

**新增的实际吞吐/隔离问题**：[QuestManager.eventExecutor](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/QuestManager.java) 设定 1 个工作线程、容量 1000 的队列及 `CallerRunsPolicy`。满队列时（执行器未关闭）提交线程会自己运行被拒绝入队的任务：这既会让网络/World 调用线程执行本来交给任务池的逻辑，也可能与池中正在执行的另一任务并发。原注释称单线程防止 Quest 状态竞态，这一策略在饱和情况下本身无法提供该保证。应以可控饱和测试核实，不将其与当前已经复现的游戏故障混为一谈。相比之下，SceneScriptManager 与 AbilityManager 选择有界队列加拒绝策略，饱和行为及任务失败反馈须在迁移过程中显式测量，不能用静默吞掉异常来掩盖。

**新发现的跨 World 键名冲突候选**：[DomainChallengeKeyHelper](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/dungeons/DomainChallengeKeyHelper.java) 的全局 `PENDING_HIDE` 以 `(sceneId << 32) | entityId` 组合键记录待隐藏机关；`sceneId` 是游戏场景类型编号，并未包含 World 实例身份。因此两个 World 中相同 SceneId、相同 EntityId 的任务理论上会共享键。与第 22 节的实体缓存问题同源，须通过真实双 World 相同键测试确认影响，不能仅凭代码路径宣称已发生跨玩家干扰。

### 24.2 时间语义必须逐项厘清，不能一次替换成统一“秒”

当前 [ServerTaskScheduler](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/scheduler/ServerTaskScheduler.java) 的 `delay`/`period` 实际都按 `runTasks()` 调用次数计数；[ServerTask.shouldRun](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/scheduler/ServerTask.java) 的重复任务在首次 `delay` 到期后，继续使用**从创建以来的绝对 tick 计数取模**。例如 `period=3, delay=5` 可在第 5、6、9 次 Tick 执行，第一次到第二次仅间隔一个 Tick；应当保持全局 Tick 对齐还是首次运行后完整间隔，是独立的行为语义问题，尚未批准修改。

另外，[QiqiEHealHelper](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/QiqiEHealHelper.java) 以 `durationSec=15`、`TICK_SEC=1` 编排 15 次周期，但 `period=1` 实际每 Tick 触发；按默认 200ms 的理想稳定 Tick 估计，15 次约覆盖 **3 秒**，而代码字段表达的是 15 秒。[HutaoC6Helper](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/HutaoC6Helper.java) 与 [ShinobuC6Helper](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/ability/ShinobuC6Helper.java) 直接 `round(durationSec)` 用于 scheduler ticks，存在相同的计量混用。它们和第 23 节已证实的 Tower 延时问题一起表明：重构调度时须对**明确表达真实秒的参数**进行单位测试，同时保留原本按 Scene 模拟 Tick、游戏内时间和现实钟计时的业务差别。上述秒数仅是按当前代码与默认配置推算的调度值，未经原版 7.1 运行表现对照。

目前静态搜索 `scheduleAsyncTask` 仅查到 [ServerTaskScheduler](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/scheduler/ServerTaskScheduler.java) 定义，没有找到当前生产源码对该接口的直接调用。其实现缺陷仍需测试，但**不应让无调用的 API 抢走多 World、Quest、Lua、Ability 实际越权路径的迁移优先级**。

### 24.3 第一批验证与代码迁移边界（建议，尚未批准实施）

1. **先刻画调用契约**：对所有会写状态的延迟回调记录来源、目标 UID/World/Scene、会话或实体生存期、时间单位和是否有跨域副作用；以 Qiqi 周期治疗、Quest Exec、普通 Lua 触发和延迟传送作为四个具体样本。不要在执行代码里凭任务所属类或物理线程“猜”状态权威。
2. **先证明隔离与顺序**：在无需用户客户端的测试中构造两个 World 的相同 SceneId/EntityId、两个 UID 并发 Quest、同 UID 的任务饱和与重登、同 Scene 事件乱序；保留 modifier→属性读取→治疗的因果顺序，给可跨域的操作明确成功/取消/异常边界。
3. **计时与停顿单独测试**：固定 `tickRateMs=200`、自定义 Tick 间隔与暂停的 Scene，对 Qiqi/胡桃/久岐忍/Tower 与 scheduler `period=3,delay=5` 做可重复计数测试；不能以运行几秒钟的真实睡眠测试代替逻辑 Tick 契约。
4. **选最小首批迁移切口**：优先选择一个**只修改单 World 战斗态**的周期回调与一个**UID 任务事件**，分别验证 S2/R2 的目标拥有者执行，随后再进入 Quest↔Scene、延迟跨 World 传送等需多域协调的业务。不能简单把全服单线程池换成更多线程，也不能在 World 域里 `join/get` 等另一个域的结果。

**尚未决定**：World/UID 执行器具体形态、定时任务身份和取消票据的接口、全服数据库 Watchdog 暂停如何作用于各域、Quest↔World 提交/失败/顺序语义、Session 世代标识与终止策略、重复任务延迟相位契约。所有事项继续以用户批准的架构决议为准；本轮只补文档，不修改 Java/Gradle、不额外建分支、不触发 CI。

## 25. Quest 完成链路：UID 与 World 之间的真实业务完成语义（2026-11-04 源码审计）

**状态：研究记录，不作新的架构决议。** S2（World）、R2（UID）、B1、C1、F1、P1、E1 继续有效。这里核对真实的 Quest→Inventory→World 依赖和完成状态，列出**需要用户决策**的高层行为问题。未批准 Java/Gradle/接口/线程/超时/持久化设计。

### 25.1 真实调用链和写入者

| 来源/阶段 | 已验证的上游源码行为 | 对 S2/R2 的待解决影响 |
| --- | --- | --- |
| World 产生任务事件 | [EntityMonster.onDeath](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/entity/EntityMonster.java) 给 Scene 中玩家逐个提交 MONSTER_DIE、KILL_MONSTER 和 CLEAR_GROUP_MONSTER；[Inventory](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/inventory/Inventory.java) 也产生 OBTAIN_ITEM 事件 | 怪物死亡事实由 World/S2 形成，玩家 Quest/Inventory 归 UID/R2；业务事实可跨域传递，但不能凭事件来自 World 就把该玩家 Quest 也交给 World 写 |
| Quest 事件入队与判定 | [QuestManager.queueEvent](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/QuestManager.java) 投给**全服**静态 Quest 线程；`triggerEvent` 查询条件并经 [GameMainQuest.tryFinishSubQuests](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/GameMainQuest.java) 调用 `GameQuest.finish()` | 全服 Quest 线程只排序提交给它的任务。其他直接写 Quest 的入口、Player Tick 和登录恢复并没有自动得到同 UID 顺序保障 |
| 子任务、父任务、奖励 | [GameQuest.finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/GameQuest.java) 仅在局部 `synchronized(this)` 内检查/设置已完成，其后的父任务结束、FinishExec、场景 Dungeon 事件、`gainItems` 发放及 `save()` 在锁外；[GameMainQuest.finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/GameMainQuest.java) 先设置完成并 `save()`，随后发父任务奖励 | 已完成字段、Inventory 奖励与 World 后置效果目前**不是一个不可分割的提交**；不能把互斥的 `setState` 解读为对整个奖励链原子执行。Quest 状态和 Inventory 发放属于 UID/R2，场景后置动作属于 World/S2 |
| Quest Exec 分发 | [QuestSystem.triggerExec](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/QuestSystem.java) 返回 `void`，把每个 Exec 的 `boolean` 结果交给 Quest 线程处理；失败只记 debug 日志，没有传回原 `GameQuest.finish()` | 上层最多知道 Exec 被调用了，无法由此推断 Exec 成功、相关 World 改动完成或失败应如何影响 Quest |
| 具体 Exec 的归属混合 | [ExecDelPackItem](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecDelPackItem.java) 扣 Inventory；[ExecAddQuestProgress](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecAddQuestProgress.java) 修改个人进度；[ExecSetGameTime](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecSetGameTime.java) 修改 World 时间；[ExecRefreshGroupSuite](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecRefreshGroupSuite.java) 刷 Scene 组；[ExecGrantTrialAvatar](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecGrantTrialAvatar.java) 触及个人队伍与场景角色 | Exec 是**业务动作种类**，并非统一的执行域：可能只写 UID、只写 World，或需要两个域确认。将整个 Exec 列表“都搬到 R2”或者“都搬到 S2”均不满足已定所有权 |
| 脚本通知可能延后到未来进入场景 | [ExecNotifyGroupLua](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecNotifyGroupLua.java) 注册脚本初始化和玩家进场门闩，随后向 `SceneScriptManager.callEvent` 提交通知；**`execute()` 在注册后就返回 true** | `true` 只能证明已接受安排；玩家未进入目标 Scene 时真正 Lua 事件尚未触发。若“Quest 完成”需要等待该 World 事件，则等待可能跨越任意长时间，甚至遭遇场景销毁 |
| DB 写入/网络告知 | [DatabaseHelper.saveQuest](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/database/DatabaseHelper.java) 委托 `saveGameAsync`；子任务完成可能先发送 Quest 状态通知、之后再发奖励，父任务也先保存后给奖励 | `save()` 已调用不等于确认为持久存储成功；现有 P1 与奖励回执例外继续有效。新架构必须在“内存业务完成”“异步落盘”“客户端已收到反馈”三件事之间明确合同 |

### 25.2 两个新增的顺序断点

1. **条件重查另有独立线程池**：[QuestManager.checkQuestAlreadyFulfilled](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/QuestManager.java) 在 `Grasscutter.getThreadPool().submit` 中读取 Inventory 数量、解锁传送点/区域与玩家等级，再调用 `queueEvent`。因此即使把静态 `QuestManager.eventExecutor` 换成按 UID 逻辑排队，条件的**读取与事件入队之间**仍可能跨越其他库存/解锁操作。应以“读取什么版本的玩家状态、何时评估条件”描述业务要求，不能简单假定 FIFO 足够。
2. **Scene 门闩不携带执行线程所有权**：[SceneLoadCallbackGate](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/SceneLoadCallbackGate.java) 与 [ScenePlayerEntryGate](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/ScenePlayerEntryGate.java) 在条件已满足时**直接在调用线程执行回调**，条件随后满足时也在触发 `complete/enter` 的线程执行。它们能协调“等加载/等玩家进入”，但不提供 World/S2 或 UID/R2 执行保证。特别是 [ExecRefreshGroupSuite](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecRefreshGroupSuite.java) 的未初始化分支已经另投 Scene scheduler；若脚本已初始化，则 `apply` 直接在 Quest Exec 的调用线程执行，两个路径的写入上下文可能不同。未来的归属检查不能仅看函数名。

另有现状：[QuestManager.onTick](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/QuestManager.java) 从当前 World 直接读取游戏天数/小时；[GameQuest.start](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/GameQuest.java) 也读取 World 游戏天数。S2/R2 下 **World 时间的观察值应如何供 UID Quest 判定**属于独立读一致性问题；可以评估快照或有序请求，尚未批准做法。

### 25.3 Q1 正式决议：Quest 个人业务完成（2026-11-04）

在已有 F1（Shell 等待**真实业务完成**）下，必须区分 **Quest/Inventory 个人提交**、**World 剧情副作用**、**数据库持久确认** 和 **通知已投送**。由于 `ExecNotifyGroupLua` 可能要等玩家将来进入场景，“一直等待全部剧情实际播放”会在某些合法业务路径下没有有限完成时间。

**用户正式选择 Q1，后续又选择 QR3 作为奖励完整性的明确例外。** 在 Quest/GM 对外报告成功时，承诺该 UID 的任务状态及所需即时个人 Exec 依规则处理、奖励发放已在 R2 执行；**奖励不必全部接受（QR3）**。World 的剧情/Scene/Lua 后置效果可以继续异步执行，不构成这次 Quest 的成功屏障。F1 Shell 仍等待上述真实个人完成结果，普通异步 MongoDB 写入及网络投送不自动纳入屏障。下表保留历史比较，不表示 Q2/Q3 仍待选择：

| 候选 | Quest / GM 报告完成的边界 | 优点 | 实际代价 |
| --- | --- | --- | --- |
| **Q1：个人提交完成** | Quest 状态、即时 UID Exec、约定的 Inventory 奖励已在 R2 内处理完成；World Exec 可单独排队并独立报告结局 | 不因等待玩家进入 Scene 而阻塞；玩家个人事务边界清晰 | “任务完成”不保证场景效果已生效，管理命令与脚本若依赖 World 结果，需要另定观察/失败语义 |
| **Q2：必要 World 效果也完成** | 对本次 Quest 定义哪些 World 副作用属于必须完成，并在其应用后才给最终成功 | 对即时剧情/副本 GM 操作具有更强一致感 | 哪些效果必须等待、离线或目标 Scene 不出现怎么办、失败/超时如何区分，都必须明确；`ExecNotifyGroupLua` 不能无条件等 |
| **Q3：分阶段业务结果** | 分开表示“UID 已提交”“World 效果完成/待触发/失败”等阶段，前台 Shell 对具体命令选择所需完成层次 | 不同命令可准确描述不同完成条件，可观察 Deferred Lua | 结果语义和测试维度更多；如随意为每条命令特判，会引入难维护的复杂性；需要先定义通用到何种程度 |

**Q1 的适用边界，按 QR3、X1 修订**：World 效果不进入 Quest 成功屏障，不能据此免除 World/S2 唯一写者规则；与 World 结果直接关联的个人记录、个人 Exec 失败仍需单独分析。已确认**部分奖励失败可以容忍，但即时个人 Exec 必须全部成功（X1）**；全数奖励被拒、奖励定义缺失、玩家收到何种部分结果反馈仍未决定。Q1 不保证原子回滚、恰好一次、不受限地立即完成，也不授权 Future 类型、跨域协议、任务追踪表或补偿机制。普通 MongoDB 写入不自动算作 F1 阻塞条件，既有 P1/奖励 PREPARED 例外维持。

### 25.4 可以先验证而不做架构决定的事实

- 同一 Quest 由 World 怪物死亡和 GM/任务脚本相继完成：观察任务状态、父子奖励、实际库存、FinishExec 与 Scene 事件顺序；区分源码可推断与测试才可证明的行为。
- 同一 UID 在开始 Quest 后立即修改 Inventory/区域解锁：构造 `checkQuestAlreadyFulfilled` 的查询和 `queueEvent` 交错，验证条件观察时间。
- 一次 ExecDelPackItem 与一次 ExecRefreshGroupSuite：确认 Quest 状态与 UID 修改的业务结果，以及脚本已初始化/未初始化时 Scene 写入时点。
- ExecNotifyGroupLua 指向暂时未进入的场景：检查“已安排”“待进场”“已调用 Lua”确实是三个不同阶段；旧 World/Scene 销毁后不得对新的 World 错写。
- 两名不同 UID 同时完成不同任务、同 UID 同一任务多次触发、异常 Exec/插件取消与数据库不可用：逐项记录当前行为，不臆造“已经精确一次”或自动回滚保证。

**本节确定 Q1，后续第 27 节选 QR3 允许部分奖励，第 30 节又选 X1 要求即时个人 Exec 全部成功；全拒绝/奖励失效反馈、Exec 失败后的状态处置、跨域协议及错误恢复仍未决。** 测试/实施前需用户决定；不改变当前 Java、构建、运行模式、插件 API 或数据库结构。不因研究文档运行 CI。

## 26. Q1 的工程切入：全量 Quest Exec 直接调用点与奖励失败传播审计（2026-11-04）

**状态：Q1 已正式选定；本节只核验上游代码、暴露实现阻塞点与供用户选择的语义。** 研究基准仍为 `MeChen618/AstaPS/main@ac9a7e6`。逐个查看 `src/main/java/emu/grasscutter/game/quest/exec` 下 **45 个 Exec 类** 的 `execute` 直接调用，下面按可观察副作用归组；辅助方法可能继续触发不同域的操作，因此本分类**只说明直接路径，不宣称完成了所有传递调用审计**。正式迁移前仍须核对目标业务的间接副作用。

### 26.1 Q1 具体包含哪些操作

| 直接调用点类别 | 已验证实例 | Q1 在 R2/S2 下需要的解释 |
| --- | --- | --- |
| 明确修改 UID 个人资料 | `ExecSetQuestVar` / `ExecBatchSetQuestVar` / `ExecIncQuestVar`、`ExecSetQuestGlobalVar`、`ExecAddQuestProgress`、`ExecSetOpenState`/V2、`ExecUnlockArea`、`ExecChangeMapAreaState`、`ExecStartBargain`、`ExecActiveItemGiving` | 原则上属于 **R2/Q1 即时个人阶段**；须尊重输入和相关个人状态顺序。具体副作用可能引发其他事件，不能把 `boolean true` 一概当成全部业务效果生效 |
| 直接修改 Inventory | [ExecDelPackItem](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecDelPackItem.java) 直接 `removeItemById`；[ExecDelPackItemBatch](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecDelPackItemBatch.java) 逐项移除，即使有某项失败仍处理后续项目 | 必须在 UID/R2 中执行；当前 Batch **可能发生部分扣除后返回 false**。是否把这一失败视为整次 Quest 失败、何时停止后续 Exec、能否回滚，Q1 没有替用户决定 |
| 直接修改 World/Scene | [ExecSetGameTime](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecSetGameTime.java) 改 World 时间；`ExecSetGameTimeLocked`/V2 锁 World 时间；[ExecRefreshGroupMonster](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecRefreshGroupMonster.java) 直接刷新 Scene 怪物；`ExecRefreshGroupSuiteRandom` 直接改 Scene 组 | 属于 **World/S2** 操作；按 Q1，独立的 World 后置效果不能阻塞 Quest 个人完成，也不能跑在 UID 域里直接写 World |
| 需要额外拆清的混合调用 | [ExecRegisterDynamicGroup](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecRegisterDynamicGroup.java) 先对 World 的 Scene `loadDynamicGroup`，再把返回的 `suiteId` 写入个人 `QuestGroupSuites`；`ExecUnregisterDynamicGroup` 先注销 Scene 组再删个人记录；`ExecGrantTrialAvatar` / `ExecRemoveTrialAvatar` 操作 TeamManager；`ExecAddCurAvatarEnergy` 更新当前角色能量；`ExecChangeAvatarElemet` 调整长期元素来源并可能影响活动实体 | **这里是真正的 Q1 边界难点**：如果个人记录必须使用 World 实际返回的结果，不能把 World 操作简单丢到后台并立刻宣布“个人部分已完成”。未决定的是如何协调这种混合动作，不改变已选 B1/S2/R2 |
| 触发未来场景/脚本的 Exec | [ExecNotifyGroupLua](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecNotifyGroupLua.java) 的 `true` 仅表示已登记等待脚本初始化/玩家进入场景；[ExecRefreshGroupSuite](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecRefreshGroupSuite.java) 可能等待脚本初始化再在 Scene scheduler 应用 | Q1 不等待最终剧情生效，但应分清**已接受安排、可执行前置条件满足、实际已应用**，不能凭登记成功就伪称 World 已成功。若涉及由 World 结果决定的个人存储，仍按混合调用处理 |

**其他已核对 Exec 族**：`ExecClearTimeVar` / `ExecInitTimeVar` / `ExecRandomQuestVar` 处理任务时间变量；`ExecLockPoint` / `ExecUnlockPoint` / `ExecUnlockPlayerWorldScene` 处理玩家解锁状态（`UnlockPoint` 的特殊剧情入口需传递调用核验）；`ExecDelSceneTag` / `ExecAddSceneTag` 管玩家 SceneTag；`ExecSetFlyable` / `ExecSetWeatherLocked` 管个人属性；`ExecSetWeatherGadget` 使用玩家当前 Scene 和天气规则；`ExecRollbackQuest` / `ExecRollbackParentQuest` 改 Quest 与 Player 位置并构造场景通知；`ExecSetWeatherLocked` 和 `ExecStopBargain` 等没有消除业务失败可能。全部 45 个类已检查过直接 `execute` 路径；这些辅助分类不代表已选具体的实现顺序或线程拆分。

### 26.2 当前 Quest 返回值/发放策略无法兑现 Q1

1. [GameQuest.finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/GameQuest.java) 返回 `void`。可取消的 `PlayerCompleteQuestEvent` **先于**“已完成”检查，取消时函数直接返回；即便被取消或已完成，外层也拿不到业务结果。子任务首先置 `FINISHED`，之后执行父任务结束、发起 FinishExec、场景 Dungeon Event、逐项 `gainItems` 和 `save`。
2. [QuestCommand.Finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/command/commands/QuestCommand.java) 找到 Quest 后直接 `quest.finish()`，然后**无条件发送完成消息**；插件取消、重复完成、奖励拒绝都无法使此消息反映真实业务结果。加上 Picocli `Runnable` / `CommandMap.invoke():void`，这与已选 F1/Q1 的条件执行语义冲突。
3. 子任务 `gainItems` 对每项调用 `Inventory.addItem(item, QuestItem)`，忽略其 `boolean`。父任务 [GameMainQuest.finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/GameMainQuest.java) 在给父任务奖励前先 `save()`，之后通过 `Inventory.addItemParamDatas(..., QuestReward)` 发放；[Inventory.addItemParamDatas](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/inventory/Inventory.java) 直接采用 `BEST_EFFORT`，返回 `void`，丢弃已有的 `InventoryAddResult`。这是现有任务状态与实际奖励可能脱离的直接证据。
4. [Inventory.addItems](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/inventory/Inventory.java) **已有** `ALL_OR_NOTHING` 授权/预检查 API 和能表达被拒原因的 `InventoryAddResult`，但它注释明确无法回滚数据库写入或其他外部副作用，`useOnGain` 项目也可能立即产生额外操作；不能把“有预检查”误读为整个 Quest 已具备事务回滚。
5. [QuestSystem.triggerExec](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/QuestSystem.java) 返回 `void`、异步提交每条 Exec。Exec 的 `boolean false` 只做 debug 日志，未回传子任务的发放/完成；因此 Q1 所说的“即时个人 Exec 已处理”当前完全无法从 `GameQuest.finish` 的返回时刻证明。
6. 库存插入还会 [triggerAddItemEvents](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/inventory/Inventory.java) 投递新的 Quest 事件。**Q1 不自动表示所有由奖励连锁引起的后继 Quest 都已完成**；这类传递闭包若无终点，会让简单命令无限等待。当前应明确本次个人提交与后继独立业务的边界，未授权建立通用依赖图引擎。

### 26.3 奖励失败策略的历史候选与 QR3 正式选择（2026-11-04）

**用户已正式选择 QR3：允许奖励部分交付，Quest 可以完成。** 这修订了 Q1 对奖励完整性的原边界，但**仅适用于奖励发放**；`ExecDelPackItemBatch` 等即时个人 Exec 失败、全部奖励被拒是否仍算成功、失败项目如何向用户呈现都没有因此获得决定。下表保留当时的方案比较，QR1/QR2 未选：

| 方案 | 奖励不全 / 即时个人 Exec 失败时的行为 | 成本和风险 |
| --- | --- | --- |
| **QR1：个人完成整体拒绝** | 预先校验所需个人操作；无法完整交付时，不宣布 Quest 完成，保留可重试条件 | 失败前发现拒绝最容易；部分效果已执行才失败时仍可能无法回滚，需要明确先后顺序及是否允许不可逆个人动作 |
| **QR2：任务先完成，缺失个人效果记为待补偿** | 对外不隐瞒奖励/Exec 未完成，记录待补发或待处理状态 | 需要可恢复的欠账/去重/重试语义和可能的新持久状态；用户尚未批准新增结构 |
| **QR3：允许部分奖励（已选）** | 容许部分奖励发放失败而 Quest 仍完成；**不涉及允许个人 Exec 失败**。实际接受/拒绝项目可从 Inventory 现有结果中区分，具体反馈方式仍待定 | 放宽 Q1 的奖励完整性要求，但不降低个人 Exec 和 R2/S2 所有权的要求 |

**决议状态**：QR3 已选定，QR1/QR2 仅供历史比较；本次仍未批准任何具体实现手段。原有 P1（经济操作准入）继续有效。特别是“Quest 标记完成后奖励拒绝时如何保持一致”不能简单地在任何异常上将 `state` 改回未完成：可能已触发奖励通知、个人进度、其他 Quest、可取消插件事件与后续 World 剧情。

**验收建议**（无需先实机）：插件取消 `PlayerCompleteQuestEvent`、Quest 已完成再次 GM finish、子/父奖励容量不足、`useOnGain` 执行失败、`ExecDelPackItemBatch` 中途失败、World Exec 延迟触发、混合 `ExecRegisterDynamicGroup` 的 World 成功与 UID 记录更新；检查每个操作是否正确体现 Q1/F1、避免重复奖励及不同 UID 被全服锁串行。尚未设定任何新的业务结果类型、任务队列或恢复存储。

**尚待用户决定**：奖励全数被拒及奖励定义缺失时的任务成功条件、部分奖励反馈；混合 Exec 的个人完成条件；**X1 已明确即时个人 Exec 失败不允许报告 Quest 成功，但失败后的副作用与状态处置仍待定**；是否纳入由奖励触发的后继 Quest；其他仍待定事项维持原状态。此次只更新研究文档，不修改源码、构建、分支、数据库或 CI。

## 27. QR3 决议落地前的奖励结果证据与业务边界（2026-11-04）

**正式决议**：用户选择 **QR3（允许部分奖励）**。在已选 **Q1（Quest 个人业务完成边界）** 下，**奖励中有项目被拒绝，不应仅因未能完整发放而强制整个 Quest 失败**。保留成功发放的项目，不擅自增加补偿队列、自动重试、事务回滚、欠账存储或兼容层。Q1 的 **即时个人 Exec 必须按尚待确定的个人动作规则处理**，QR3 对其没有授权。S2/R2/B1/C1/P1/F1 等决议不变。

### 27.1 可直接复用的上游证据

1. [InventoryAddResult](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/inventory/InventoryAddResult.java) 已有每项 `Entry(itemId,requested,added,status)`、`allAccepted()` 与 `partiallyAccepted()`。其 `partiallyAccepted()` 精确定义是“至少一项被接受且并非全部被接受”；**全数失败不会被它标为部分成功**。`Entry.accepted()` 还要求 `added == requested`，状态为 `ADDED` 或 `EFFECT_APPLIED`。这套现成结构足以在内存中表示**本次批次**的接受/拒绝事实，但不是持久事务日志。
2. [Inventory.addItemsInternal](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/inventory/Inventory.java) 对每个 `GameItem` 处理并返回 `InventoryAddResult`；拒绝原因含 `CAPACITY_FULL`、`STACK_LIMIT`、`INVALID_ITEM`、`UNSUPPORTED_TYPE`、`EFFECT_FAILED`，并可体现重复拥有 `ALREADY_OWNED`。**同一次 Quest 奖励有正常物品和 use-on-gain 直接效果时，`EFFECT_APPLIED` 表示个人效果已执行，没有新增背包槽位**。
3. [GameMainQuest.finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/GameMainQuest.java) 对多个 `rewardId` 分批遍历；`rewardData == null` 时**静默跳过**；通过 `Inventory.addItemParamDatas` 发放，该接口是 `void`，内部选择 `BEST_EFFORT` 并**丢弃完整返回结果**。子任务 [GameQuest.finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/GameQuest.java) 用 `getGainItems().forEach(addItem)`，**丢弃每个 boolean**。所以当前确实允许部分奖励，但没有形成 Q1/F1 可观察的业务结果。
4. [InventoryRewardAdmission](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/inventory/InventoryRewardAdmission.java) 的 `ALL_OR_NOTHING` 预检查包括容器容量和累计堆叠，但该语义没有被 Quest 的父奖励路径使用。QR3 **不授权强制将 Quest 改成 ALL_OR_NOTHING**；同时 P1 的经济写入准入仍为独立先决约束。
5. [QuestCommand.Finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/command/commands/QuestCommand.java) 在 `quest.finish(): void` 返回后无条件输出成功文本。当前区分不了插件取消、已经完成、部分奖励、全部奖励失败。**QR3 允许部分奖励，不等于任何情况下都可以谎报“刚刚完成了任务”**。
6. [ForcedQuests.apply](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/ForcedQuests.java) 的 `quest forcefinish` 只给 Player 记录一个客户端可见的“已完成主任务 ID”并发包，**不会执行正常 `GameQuest.finish` / Quest Exec / 奖励**；因此不能把该命令的“force-finished”结果拿来测试 QR3 是否发奖。其对外准确承诺需要在命令审计中另行对待。

### 27.2 仍未批准的后续业务决策

| 情境 | QR3 已决定的事实 | 必须继续由用户决定的边界 |
| --- | --- | --- |
| 两项奖励 A 成功、B 因背包满拒绝 | **任务可以完成**，不强制回滚 A，不因 B 拒绝而把任务判为失败 | 失败项目如何告诉玩家/GM（发包、Shell 文本、日志、结构化结果等） |
| 所有奖励均被拒绝，或 `RewardData` 丢失 | QR3 **只明确允许部分奖励**；未授权把“全拒绝”和“奖励定义缺失”一并解释为正常成功 | Quest 是否仍完成、是否有严重配置错误需要上报；切勿擅自等同于部分成功 |
| 奖励 `isUseOnGain` 的个人效果失败 | `InventoryAddResult.Status.EFFECT_FAILED` 可以表达拒绝 | 它能否与普通背包容量不足同等容忍，特别是直接影响角色解锁/能力的奖励 |
| `ExecDelPackItemBatch` 部分扣除、即时个人 Exec 失败 | **QR3 不适用**；已选 Q1 仍需真实个人执行结果 | Exec 失败如何影响 Quest、后续 Exec、既有个人副作用及 Shell/F1 结果 |
| World/Scene Lua 和个人 `QuestGroupSuites` 相互依赖 | Q1 不等待独立 World 剧情完成 | World 返回结果为个人记录前提时，是否视为即时个人业务依赖、如何协调与失败 |
| 由发奖励触发下一项 Quest | QR3 只放宽**本次 Quest 奖励的交付完整性** | Q1 是否包括整个连锁后续 Quest（尚未选择），不可偷换成“一切任务队列已排空” |

**研究优先级，非实施批准**：保留现有 `BEST_EFFORT` 的部分发奖能力，研究如何**不丢掉已有 InventoryAddResult 信息**，把“任务已完成、部分奖励被接受、哪些拒绝”供业务命令合理消费；优先记录父任务 `rewardId` 跳过与子任务逐项 `boolean` 丢失的路径。**不自动创建新的业务结果类型、额外持久化结构、统一 Exec 协调器或改写 Java**。**下一项已决策 X1**：个人 Exec 失败阻断 Quest 的 Q1 成功报告；失败后的任务状态与部分副作用处置、UI/日志输出技术仍待用户选择。

## 28. QR3 的边界隔离与既有回归测试（2026-11-04 源码对照）

**状态：补充证据，不做新决定。** 第 27 节已正式采纳 QR3 作为 **Quest 奖励** 策略；没有将其扩展到所有从 Inventory 发放物品的业务。

### 28.1 相同 Inventory 接口，现有业务合同却不同

| 上游业务 | 当前可验证的完成合同 | QR3 的影响 |
| --- | --- | --- |
| **Quest 子任务奖励** | [GameQuest.finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/GameQuest.java) 逐项调用返回 `boolean` 的 `Inventory.addItem`，目前不统计拒绝结果 | **已选 QR3**：允许部分奖励，仍需能区分部分交付事实及 Quest 实际是否提交成功 |
| **Quest 主任务奖励** | [GameMainQuest.finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/GameMainQuest.java) 调用 `Inventory.addItemParamDatas`，内部为 `BEST_EFFORT`，返回 `void`，丢弃 `InventoryAddResult` | **已选 QR3**：保留部分奖励语义；要为 Q1/F1 提供有根据的回执需进一步设计结果返回路径，未批准接口 |
| **成就奖励** | [Achievements.takeReward](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/achievement/Achievements.java) 验证奖励数据后用默认 `Inventory.addItems`，检查 `allAccepted()`；未全部接受则返回失败 Retcode，并保持成就奖励未领取；目标奖励同理 | **QR3 不适用**。未经用户另行决定，不能为“统一奖励逻辑”改成 Quest 的部分发放策略 |
| **GM forcefinish** | [ForcedQuests.apply](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/ForcedQuests.java) 只维护已标记完成的主任务列表和客户端通知，不产生 Quest 奖励 | **不属于 QR3 的奖励发放案例**；应单独描述其命令语义 |

[InventoryRewardAdmissionTest.resultDistinguishesPartialFromTotalDelivery](https://github.com/MeChen618/AstaPS/blob/main/src/test/java/emu/grasscutter/game/inventory/InventoryRewardAdmissionTest.java) 已有 `ADDED` 与 `CAPACITY_FULL` 混合的部分发放结果测试；[AchievementRewardDeliveryTest](https://github.com/MeChen618/AstaPS/blob/main/src/test/java/emu/grasscutter/game/achievement/AchievementRewardDeliveryTest.java) 验证拒绝后可重新领取成就奖励。两者覆盖**各自局部工具或业务现状**，**没有证明 Quest QR3 的命令回执正确**。将来针对 Quest 应单独验证父任务、多 `rewardId`、子任务单项拒绝、插件取消及重新完成行为；不应为了测试 QR3 反向修改其他业务策略。

### 28.2 结果的三个独立维度

- **任务状态**：`GameQuest` / `GameMainQuest` 是否已经根据规则进入完成态，是否被插件取消或被重入跳过。
- **奖励结果**：`InventoryAddResult` 的每项接纳/拒绝，以及 `useOnGain` 的 `EFFECT_APPLIED`；有多批次、多 `rewardId` 时，业务层是否汇总仍待决定。现有 `partiallyAccepted()` 只证明**单批次**至少有一项成功。
- **命令承诺**：F1 不能再以“已经调用 `quest.finish()`”或“打印 Quest finished”作为 Q1 成功证据；但**部分奖励失败本身按 QR3 不再构成 Quest 必然失败**，不能把 `!allAccepted()` 简单映射为失败退出码。

**用户保留的下一轮决定**：全数奖励拒绝的业务结果、`useOnGain` 类奖励失败是否与普通背包满等价、部分奖励结果的 UI/CLI/插件呈现方式、**X1 个人 Exec 失败后的任务状态与既有副作用处置**及混合 World→UID 完成依赖。继续研究这些高层边界后才能批准代码层接口、流程顺序与异常处理。本轮没有修改 Java、测试代码或构建，不运行 CI。

## 29. 库存 Remove 与 Consume 的语义边界（用户决定，2026-11-04）

**正式语义方向**：用户要求“扣除应该用 consume 之类的函数”；结合前述“删除应该是幂等”的意图，明确分开**清理目标**与**消耗数量**。这里只批准行为概念，没有批准具体 Java API、过渡方式、是否全局改名或如何处理每一种 Quest Exec。

| 概念 | 已批准的方向 | 需要注意 |
| --- | --- | --- |
| **Remove / 清理** | 将某物品或某种任务标记清理至目标状态，例如清理该 ID 的全部残留；目标本来已不存在即成功，同一清理可重复执行 | 要有明确**目标状态**。现有 `removeItemById(id, count)` 实际做减法，重复调用会反复扣数量；**不可仅通过“找不到返回 true”就称其幂等** |
| **Consume / 扣除** | 明确要求从玩家持有量扣除 `count`；数量不足、参数无效需要能报告失败；同一业务操作重复执行会重复扣除，因此**不是天然幂等** | 经济操作还受已定 P1 约束；如有重试需求，重复执行身份/去重属于**另行设计**，本轮不授予自动补偿或账本 |
| **Quest Exec 映射** | 实际是 Remove 还是 Consume 应看**任务业务语义**，不应直接按 `DEL_PACK_ITEM` 名称武断归类 | 原版 7.1 是否用于任务道具清理还是精确支付仍需数据验证；`ExecDelAllSpecificPackItem` 的“清空”用途与议价等物品付费用途应区别审计 |

### 29.1 当前上游可复用实现与缺陷

- [Inventory.removeItemById](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/inventory/Inventory.java) 找不到物品便返回 `false`，内部 `removeItem(GameItem,count)` 只拒绝 `count<=0` 或 `item==null`，**没有检查 count 是否大于原有数量**；物品有 2 个时请求扣 3 个，内部先产生 -1 再删除，返回 `true`。因此旧接口同时不满足幂等清理与精确扣费合同。
- 同文件已有 `payItem` 和 `payItems`，前者先判断 `getVirtualItemCount(id) >= count`，后者借助 [InventoryPaymentQuote.calculate](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/inventory/InventoryPaymentQuote.java) 合并重复 ItemId、检查算术溢出，然后在同一 Inventory 锁内检验本次所需量、逐项付费。**这是 Consume 语义的现有参考实现**，可以优先研究复用；但它主要适配所支持的虚拟物品和 Material，不能未经审计宣称适合每一种装备扣除及所有副作用的原子回滚。
- [ExecDelPackItem](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecDelPackItem.java) 和 [ExecDelPackItemBatch](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecDelPackItemBatch.java) 使用旧 `removeItemById`；后者逐项删除、失败后继续，并不能保证整批精确扣费。切换前需确认原版配置的用途和 Q1 下个人 Exec 失败合同。
- [ExecDelAllSpecificPackItem](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecDelAllSpecificPackItem.java) 已通过 `count <= 0 || removeItemById(id,count)` 手工做出“缺失即成功”的清空行为；[LiyueBargainSupport.handleOffer](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/LiyueBargainSupport.java) 在钱不足时拒绝、满足时再用旧 `removeItemById` 完成议价消费。这两个例子说明同一低层删除接口正承担**不同业务意图**。已批准的语义区分能够消除调用方的歧义，但**本轮没有改动这些路径**。
- **Java/C++ 异常合同另行区分**：即使未来某个 Consume 返回 `false` 表示业务拒绝，也不代表抛异常；C++ `noexcept` 或 Java 受检异常声明不赋予业务必然成功。Exec 的异常传播与业务失败规则仍待确定。

### 29.2 保留给用户的实现选择

- 精确命名采用 `consume` / `consumeItem` / `payItems` 的哪一个、是复用已有内部实现还是改入口。
- Remove 清理哪种目标（指定实例、全部 ItemId、任务指定量的残留），是否保留独立的部分清理操作；对“Remove”已选的幂等性质必须由**明确的目标状态**保证。
- 旧 `removeItemById(id,count)` 的去向及哪些现有调用方实际是支付/清理；无须替尚未进入上游的兼容设计保留旧语义，但任何删除 API 调用点必须审计。
- Quest 中每个 `DEL_PACK_ITEM` 类型如何根据原版验证归类；个人 Exec 失败已正式选 **X1**；其业务状态/副作用失败恢复策略仍未决。QR3 只约束 Quest 的奖励部分交付。

**执行状态**：此节是用户授权的语义决定 + 源码事实记录；未经用户后续选择，不改 Java API、执行器、存储、分支与构建，也未运行 CI。

## 30. X1 决议：Quest 即时个人 Exec 必须成功（2026-11-04）

**用户正式选择 X1**：在已经批准的 **Q1** 完成合同中，属于本次任务完成路径的**即时个人 Exec 必须实际执行且全部成功**，才能对外报告 Quest 业务成功；已选 **QR3** 对**奖励**的部分交付容忍不扩大到个人 Exec。**明确不选择 X2（个人 Exec 失败照常成功）与 X3（引入必要/可选等级）**。这项决议确定了**成功的必要条件**，没有授权自动回滚、再次执行、忽略异常或改变已写存档。

### 30.1 上游源码怎样违反 X1

1. [GameQuest.finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/GameQuest.java) 在 `PlayerCompleteQuestEvent` 许可后先写 `state=QUEST_STATE_FINISHED`，随后父任务可能先写完成、通知、调用 `save()`、发奖及启动下个 Quest，然后才迭代 `getFinishExec()` 进入 [QuestSystem.triggerExec](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/QuestSystem.java)。这使**“已提交完成状态/已触发奖励”**早于个人 Exec 成功已知的时刻。
2. `QuestSystem.triggerExec` 返回 `void`，把 Exec 异步提交到全服 `QuestManager.eventExecutor`；执行返回 `false` 时仅记录 debug，调用者无法知道；**未找到 Handler** 时也只记录 debug 并跳过。Java `ExecutorService.submit` 的任务异常会进入返回的 `Future`，但当前调用方不持有该 Future。无法给 X1 提供可靠的成功证明。
3. [QuestExecHandler.execute](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/handlers/QuestExecHandler.java) 明确返回 `boolean`，因此业务拒绝本来就是当前接口允许的结果。例如 [ExecDelPackItemBatch](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecDelPackItemBatch.java) 会先消耗部分材料，再发现缺失返回 false；[ExecBatchSetQuestVar](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecBatchSetQuestVar.java) 会逐个修改变量，途中参数解析失败/索引错误可能留下一部分已修改的变量。这些都是 **X1 失败信号与部分副作用同时存在** 的情形。
4. 现有 [QuestManager.eventExecutor](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/QuestManager.java) 是全服单线程、容量 1000 的队列，饱和时 `CallerRunsPolicy` 允许从调用者线程运行，使“都从同一任务执行器提交”不能证明 UID 操作只有唯一写者。X1 的成功判断必须与既定 **R2** 归属相容，不应建立全服同步的等待屏障。
5. [QuestCommand.Finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/command/commands/QuestCommand.java) 找到 Quest 后立即 `quest.finish()` 并打印“finished”，未区分插件取消、重复操作及个人 Exec 的失败。与 X1+Q1+F1 均不相容。

### 30.2 X1 的精确定义与尚未授予的能力

| 范围 | 已正式批准 | **仍由用户决定** |
| --- | --- | --- |
| **本次 Quest 正常完成** | 所有**即时、个人状态归属**的 `FinishExec` 必须实际执行且成功，才能报告本次 Quest 成功；不可仅凭“已投递”或“没有抛出异常”断言成功 | **后续用户已决定失败后不恢复任务状态（见第 31 节）**；正常路径何时提交任务完成状态、是否先验证再写状态仍未决定 |
| **已经发生的副作用** | **不允许在即时个人 Exec 失败时谎报成功** | **后续用户已决定不回滚、不补偿、不自动重试既有变更（见第 31 节）**；尚未决定失败后是否继续本轮剩余 Exec、正常事务内部的先后顺序及重复执行约束 |
| **异常 / 无 Handler** | 要获得 X1 成功结果必须能确知动作实际成功；未执行、抛出异常、返回 false 时不能声称“全部个人 Exec 成功” | 是否把程序错误与业务拒绝分不同外部结果、是否停止执行、诊断及上报方式 |
| **World / 混合域** | Q1 不等待独立 World/Scene/Lua 后置效果；S2/R2 状态所有权不变 | 动作如 `ExecRegisterDynamicGroup` 先在 World 取 `suiteId` 才能保存个人 `QuestGroupSuites` 时，怎样定义必要依赖、归属协调以及等待范围 |
| **BeginExec / FailExec / 后继 Quest** | X1 确定本次 Quest **正常完成的即时个人 FinishExec** 条件；QR3 继续只管奖励 | 开始任务、失败任务、连锁后继 Quest 的相应 Exec 成功/失败合同暂未从此自动推出 |
| **数据库 / 通知 / Shell** | 已选 P1 的个人异步顺序写入及奖励回执特例不变；F1 前台 Shell 必须等待真实业务结果，不能将 X1 个人执行失败报告为成功 | 具体 Java 返回类型、Future/回调、持久失败的反馈方案、消息结构、异常传播与重试机制 |

**特别说明**：X1 不是“所有 Quest Exec 都必须成功”。World 后置 Exec 根据 Q1 可独立处理，其最终成功不构成本次个人 Quest 完成屏障；对跨域混合 Exec 的具体拆分必须另审。此前的“Remove/Consume 分离”也不等于删除任务物品从此必定成功：幂等 Remove 有自己明确的目标状态，而 Consume 仍可以合法拒绝余额不足。

### 30.3 后续验证重点（研究，非技术设计批准）

- `GameQuest.finish` 进入时插件取消、任务原本已完成、子任务 FinishExec 多个同一 UID、父任务先发奖的顺序与 X1 的承诺是否匹配。
- `ExecDelPackItemBatch` 的“第一项已成功、第二项失败”、`ExecBatchSetQuestVar` 的“前一变量已写、后续非法参数”、未注册 Exec Handler，以及 `ExecutorService.submit` 内抛异常是否使调用者仍误报成功。
- 同 UID / 不同 UID 并发完成任务、World 无法即时应用 Scene 效果、后续 Quest 启动，均需在遵守 Q1/R2/S2 的情况下检查实际因果顺序。

**工程状态**：X1 仅作为正式业务成功条件写入决议文档；第 31 节已明确选择失败后不恢复既有状态/副作用。没有更改 Java / Gradle / 测试代码；未选新接口、执行器、定时/存储方案。CI 在本次纯文档提交阶段不运行；需要用户测试和提交上游前不得省略。

## 31. X1 失败后已发生状态与副作用：不处理（2026-11-04 正式决议）

**用户决定**：对于“即时个人 Exec 失败后，已经发生的任务状态和个人副作用如何处理”，选择 **不处理**。工程含义限于**事后恢复**：

- **保留已实际发生的修改**：已扣除/清理的物品、已写入的 Quest 变量、任务当前内存状态以及已经发出的通知，不因为随后某个 Exec 失败而主动还原。
- **不引入自动补偿、自动回滚、自动重试、欠账或特殊错误状态机**。该决议不会自动删除既有的正常持久化操作；P1 的持久化和奖励准入约束继续有效。
- **X1 不变**：任何即时个人 FinishExec 失败后，**本次 Quest 不能对外报告业务成功**，Shell/F1 也不能返回“执行成功”的控制流结果。报告失败与保留已有状态可以同时成立，例如目前旧逻辑可能已经把 Quest 标为 FINISHED，这个事实不会因 Shell 给出失败而被掩盖。外部如何准确表述“业务失败，但任务已标完成”仍待设计；不能偷偷把数据库状态恢复成未完成。
- **QR3 不变**：Quest 奖励部分发放失败本身允许任务完成；此处“不处理”仅针对 X1 **个人 Exec 失败后的状态/副作用恢复**，不扩大为忽略 Exec 失败。
- **本次决议没有确定**：应在什么时候设置 `FINISHED`、Exec 是否按顺序短路/继续执行、如何处理尚未开始的 Exec、程序异常与业务拒绝的消息结构、World→UID 混合动作的必要等待、任务重复触发的身份和防重复规则、全数奖励被拒时的完成条件。这些属于不同问题，仍由用户决定。

**上游对应风险**：[GameQuest.finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/GameQuest.java) 先设置 `FINISHED` 再提交异步 FinishExec；[ExecDelPackItemBatch](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecDelPackItemBatch.java) 会处理每一项并在某项失败时保留先前已扣物品；[ExecBatchSetQuestVar](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecBatchSetQuestVar.java) 逐项更新变量。此决议表示未来**允许这些已发生变更留存，同时必须准确报告 X1 业务失败**；绝不将现有行为的全部其他缺陷视为已批准。

**工程状态**：仅正式记入研究文档，未更改 Java/Gradle/数据库/分支，未运行 CI。实施所需接口、错误回传与执行顺序仍未授权。

## 32. 原版 Global 7.1 Quest Exec 证据与 AstaPS 行为对照（2026-11-04）

**优先报告原版证据，再作服务器架构判断**。原版样本以 [RinoPaw/Genshin-Reverse/main@0b4596ed](https://github.com/RinoPaw/Genshin-Reverse/tree/0b4596ed47eacfcb2ea65ddad7f1aea1c4e5bfce) 的 [Quest native extraction](https://github.com/RinoPaw/Genshin-Reverse/blob/main/versions/7.1.0-global/windows-x64/analyses/quest-extraction/README.md) 和原始 7.1 Windows x64 EXE/metadata 读取记录为准；AstaPS 对照基线仍为 `MeChen618/AstaPS/main@ac9a7e6`。**特别区分原版客户端资源结构与未公开的原版服务端执行实现**。原版客户端 Quest 数据不能直接证明官方服务器在某个 Exec 执行失败后的返回码、短路策略、事务或回滚行为。

### 32.1 原版已证实的任务数据所有权

| 范围 | 原版 Global 7.1 直接证据 | 对本次架构重构的影响 |
| --- | --- | --- |
| **普通 full-Quest 子任务** | 原版 `BinOutput/Quest/351` 字节精确消费 920/920，普通行阅读器 RVA `0x9C15DF0`；行上保留四组数组：`failExec`、`failCond`、`finishCond`、`finishExec`。整体 4,417/4,417 MainQuest payload 全部消费，无解析失败 | **FinishExec、FailExec 是当前可确认的原版数据来源**；其触发后官方服务端如何报告业务失败，目前尚无证据 |
| **普通 `beginExec` 与 `acceptCond`** | 原版 7.1 普通行没有第五个 `beginExec` 数组，也找不到对应普通 `acceptCond` 的拥有者；本地已验证原版 2.8→3.0 间格式移除；7.1 的 `RandomQuestExcelConfig` 仍有独立接受条件和 Exec，证明 metadata 可识别这些字段 | AstaPS 运行的部分 BeginExec/AcceptCond 是**兼容层重建的数据**；不应当宣称从 7.1 普通 Quest 客户端 EXE/metadata 提取到原版 `beginExec` |
| **普通子任务 `gainItems`** | 原版 7.1 普通 Quest metadata 未发现 `gainItems` / `trialAvatarList` 所有者；当次审计的 AstaPS-Resource `QuestExcelConfigData.json` 33,217 行均没有两字段（随机 Quest 有单独 `_awardItems`） | 现有 `GameQuest.finish().getGainItems()` 是**服务端兼容/扩展入口**。不能把它讲成普通 7.1 原版子任务奖励的已验证来源；若要保留需要具体运行需求/证据 |
| **主任务奖励** | [Genshin-Reverse 原版 full-Quest decoder](https://github.com/RinoPaw/Genshin-Reverse/blob/main/genshinre/questbin.py) 的 MainQuest 根结构读取 `reward_id_list` 并输出 `rewardIdList` | 原版主任务的奖励 ID 是当前 7.1 可保留的显式数据来源；仍不能从这个字段推断官方发奖失败策略 |
| **Exec 类型与参数** | 原版 7.1 decoder 全量恢复 100 个实际出现的 QuestExec 类型 ID；语义名字通过同版本公共提取物按 `mainId/subId/字段/位置` 对齐获得，71,051 个条目零名称冲突；ID `1` 对齐 `DEL_PACK_ITEM`，ID `18` 对齐 `DEL_PACK_ITEM_BATCH`，ID `14` 对齐 `ROLLBACK_QUEST` | 可确认**原版确有这些类型 ID 和参数配置**；语义名称是经过同版本交叉比对的标签，不能提升成“客户端 native method 已证明服务器 Delete/Consume 行为” |

**经过原版证实的具体例子**：原版 Quest 351 的子任务 `35101` 有 `TEAM_DEAD -> ROLLBACK_QUEST(35100)` 的失败条件/动作配置，来自 exact 7.1 payload。它证明**任务正常失败事件可以显式请求回退任务状态**；该 `ROLLBACK_QUEST` 是任务数据中的一个具体 FailExec，**不能据此推断其他某条 Exec 自身失败时，官方服务器也会自动回滚前面所有 Exec**。此前用户选择“即时个人 Exec 失败后不处理既有状态和副作用”，不删掉原版剧情中明确配置的 `ROLLBACK_QUEST` 动作，也不将其擅自改成统一自动回滚。

### 32.2 AstaPS 当前怎样处理

- [QuestData.java](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/data/excels/quest/QuestData.java) 同时接受 `beginExec`、`finishExec`、`failExec`、`gainItems`，并在 `applyFrom(MainQuestData.SubQuestData)` 中优先使用已加载 Excel 数据，必要时补充 materialized BinOutput 版本。该过程同时包含**7.1 原版可验证字段**和**服务端兼容/人工修复字段**；不能认为所有 JSON 字段都原生存在于 7.1。
- [原有 PR71 审计](https://github.com/MeChen618/AstaPS/blob/main/docs/quest/pr71-native-chain-audit.md) 已明确：`35302` 的 BeginExec 用于刷新场景 3 的 group `133003002`、suite `2` 并生成教学史莱姆，是服务端当前能跑早期剧情所用的兼容动作。**简单删除所有非原版 7.1 客户端字段可能使任务流程退化**；仅凭客户端无字段，也不能证明官方服务器不存在相应开始任务逻辑。
- 同一 PR71 审计记载过资源层 `35402.gainItems = [{itemId:1021,count:1}]` 的兼容材料奖励，这不是 7.1 普通 Quest 原生字段已证实的样例。第 27–28 节讨论的 QR3 可作用于服务端 Quest 奖励执行路径，**但不构成其来源都是原版的证据**。
- [GameQuest.finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/GameQuest.java) 先置 `FINISHED`、可能完成父任务并发奖，再提交 FinishExec；[QuestSystem.triggerExec](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/QuestSystem.java) 异步投递、忽略 `boolean false`、不向调用方传播执行结果。这是**AstaPS 实际行为**，不能冒充原版。
- 当前行为导致 **X1 所需的“全部即时个人 FinishExec 成功后才报告成功”无法得到证明**。已批准的“失败后不恢复既有副作用”也不会自行解决前置的正确执行/真实反馈。

### 32.3 原版未知项与后续研究路径

**仍未知**：官方 7.1 服务端在单条 Exec 返回业务失败或抛异常时，是否停止执行剩余 Exec；任务何时对客户端提交完成状态；失败后是否自动重放/回滚；`DEL_PACK_ITEM` 在每个任务中究竟表达清理、消耗还是其他目标。这些**不能仅凭客户端原始 Quest 表、枚举名称或私服实践作结论**。研究它们需要**可验证的官方运行行为/抓包、客户端由失败结果驱动的状态观察**或其他直接原始证据，不能从 AstaPS 的执行器推导官方服务器规则。

**当前确定的工程合同**：Q1（本次个人完成边界）、X1（所有即时个人 FinishExec 必须成功）、QR3（允许部分奖励发放）、失败后不主动恢复既有状态、R2/S2 权威归属和 Remove/Consume 语义分离。以上均属**用户选择的服务端重构政策**，不冒充已验证原版服务器行为。**保留给用户的真实设计问题**：现有兼容 BeginExec 如何定长期归属；在执行出错时是否继续同轮剩余个人 Exec；World→UID 混合 Exec 如何协调；全部奖励被拒绝的成功条件。先验证原版可证事实，再讨论是否保留兼容、技术实现与 CI。

**本轮仅文档研究，无 Java/Gradle/数据库修改及 CI。**


### 32.4 原版 7.1 类型覆盖与 AstaPS 内部编号的区别

以 [Genshin-Reverse 7.1 QuestExec 映射](https://github.com/RinoPaw/Genshin-Reverse/blob/main/genshinre/questbin.py) 对照 [AstaPS QuestExec.java](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/enums/QuestExec.java)：

- 当前原版 7.1 资源解码器标记了 **100 个实际出现的 Exec 类型 ID**；AstaPS 对照时的 `game/quest/exec/` 目录有 **45 个处理器类**，`QuestExec` 枚举含 **74 个值**（包括 NONE/UNKNOWN）。这些数字是**不同集合**：原版全游戏数据中的类型、服务器枚举和已注册实现。不能直接把“100-45”称为 55 个当前玩家会触发的缺陷，更不能把已有 X1 解释成必须立即实现全部 100 种操作。
- **已验证的局部可用性**：Genshin-Reverse 早期蒙德主线 39 份 MainQuest 资料涉及 20 种 QuestExec，当时配套的 AstaPS 运行源码覆盖了所需动作类型。这项验证属于特定范围、特定源码修订，不证明最新全游戏所有 Quest 已完整实现。
- 重要编号陷阱：原版 7.1 类型 ID `112 = BATCH_SET_QUEST_VAR`、`81 = SET_IS_GAME_TIME_LOCKED_V2`、`118 = SET_OPEN_STATE_V2`，但 AstaPS 的 `QuestExec` 枚举使用 **71 / 72 / 73** 对应这三个名称。原版 **71 / 72 / 73** 实际对齐为 `REFRESH_WORLD_QUEST_FLOW_GROUP_SUITE`、`HIDE_SCENE_POINT`、`UNHIDE_SCENE_POINT`。
- **这还不能断言运行时错误**：AstaPS [QuestData.QuestExecParam](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/data/excels/quest/QuestData.java) 的 `type` 是以 Gson enum 名称从资源 JSON 读取；QuestSystem 根据该 enum 的**内部编号**注册并分派 Handler，且源码注释明确把末尾 71–73 称为 internal only。若资源以语义**字符串名称**给出，枚举内部编号不需要等于原版数值。**但未来如果直接导入原版整数 ID，应先明确映射，不可复用这些内部编号作为 7.1 原版 opcode。**
- 当前最有价值的覆盖核验路径，是用**原版 7.1 原始资源**确认一段具体任务实际调用哪些 Exec，再检查 AstaPS 对应命名是否存在 Handler 及其返回/副作用，而非凭枚举注释或社区 JSON 补齐一堆未证明需要的兼容代码。

## 33. X1 之下的幂等目标、Exec 完成回执和 R2/S2 跨域触发（2026-11-04）

**研究限定**：重新读取 `MeChen618/AstaPS/main@ac9a7e6cec900e4ced28ec5dc16ceda82cf7538d` 的 Quest、PlayerProgress、Scene 和调用方；原版以 `RinoPaw/Genshin-Reverse/main@0b4596ed47eacfcb2ea65ddad7f1aea1c4e5bfce` 的 7.1 native Quest 与协议证据为准。本节补充事实和待决选项，**不新增业务决议**。

### 33.1 原版 Global 7.1 已验证什么

- 原版 full-Quest 数据保留有序的 `finishExec` 数组，7.1 客户端数据中存在 `QUEST_EXEC_UNLOCK_POINT`（ID 2），可验证任务确实通过执行动作驱动解锁。详见 [Genshin-Reverse Quest 原始资源研究](https://github.com/RinoPaw/Genshin-Reverse/blob/main/versions/7.1.0-global/windows-x64/analyses/quest-extraction/README.md)。
- 原版 7.1 Windows x64 EXE/metadata 确认 `UnlockTransPointReq` **CmdId 9369**、`ScenePointUnlockNotify` **CmdId 25567**；`ScenePointUnlockNotify` 中 field 4 是**锁定列表**、field 15 是**已解锁列表**。这证实客户端可观察的解锁状态应以正确状态通知呈现。见 [原版 7.1 协议实例研究](https://github.com/RinoPaw/Genshin-Reverse/blob/main/docs/case-studies/unlock-trans-point-7.1.md)。
- 上述证据**不能证明官方服务器**在传送点早已解锁时，`QUEST_EXEC_UNLOCK_POINT` 返回何种内部业务值；不能证明官方服务器是依次短路还是继续执行整组 Exec，亦不能证明其跨 Player/World 线程调度方式。切勿用 AstaPS 的枚举名、布尔返回或 Java 代码代替官方服务器证据。

### 33.2 最新上游同一个解锁动作在三种入口具有三种成功解释

| 上游入口与可验证行为 | 对同一个“已解锁”状态的处理 | 对 X1 的意义 |
| --- | --- | --- |
| [ExecUnlockPoint.execute](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecUnlockPoint.java) | 直接返回 `PlayerProgressManager.unlockTransPoint(...)` 的 `boolean`；已解锁、没有解除临时强制锁定时，该函数返回 `false` | **Quest Exec 层当前把“没有发生状态变化”当作失败信号**；若盲目照搬到 X1，合法的重复解锁会使 Quest 无法报告成功 |
| [HandlerUnlockTransPointReq.handle](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/packet/recv/HandlerUnlockTransPointReq.java) | 捕获 `unlocked` 用于日志，最后仍发送 `PacketUnlockTransPointRsp(RET_SUCC)`；普通路点入口还处理独立的秘境奖励 | **同一个底层 `false` 没有强迫该网络入口报告失败**。这里说明上游的 `boolean` 更接近“发生转移”，不能统一视为业务成功/失败 |
| [StatueCommand.unlock](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/command/commands/StatueCommand.java) | 已解锁时用 `Already unlocked / refreshed` 文案，并补发客户端状态通知 | 上游 GM 已在业务层区分“新解锁”与“已有解锁”，但没有统一的 Quest Exec 成功合同 |

[PlayerProgressManager.applyTransPointUnlock](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/player/PlayerProgressManager.java) 使用 `unlockedPoints.add(pointId)` 与 `forceLockedPoints.remove(pointId)` 分别计算 `newlyUnlocked`、`wasForceLocked` 和 `changed`；没有变化时 `unlockTransPoint` 尝试待发奖励后返回 false。在 [PlayerProgressManagerTransPointStateTest](https://github.com/MeChen618/AstaPS/blob/main/src/test/java/emu/grasscutter/game/player/PlayerProgressManagerTransPointStateTest.java) 中，已有用例验证第一次解锁、解除任务强制锁与发奖去重；**它们没有证明 Quest/X1 应把重复解锁视为失败**。

**推论（未批准 API 方案）**：要满足 X1 的“动作目标完成”，至少需要区分 `changed=false` 的**状态已满足**和 `ScenePointEntry==null` 的**非法目标**。不能未经审计将底层 `unlockTransPoint` 所有返回 false 全改为 true，否则会改变其他调用方、解锁奖励去重和日志的含义。此前用户批准的幂等 Remove 与 Consume 语义分离，为此类“目标状态成功”提供了原则，但**未批准全局改写解锁业务接口**。

### 33.3 同一 API 还直接跨越 UID 和 World

- `PlayerProgressManager.unlockTransPoint` 先更改 UID 所属的已解锁集合、强制锁定集合、待发奖励记录，再向该玩家的 QuestManager 投递 `QUEST_CONTENT_UNLOCK_TRANS_POINT`，同时会**直接调用当前 Scene 的 ScriptManager.callEvent(EVENT_UNLOCK_TRANS_POINT)**，随后向该玩家发 `ScenePointUnlockNotify` 并保存。将来 R2 执行这段代码时，**直接调用 Scene Lua 是 S2 写入归属的风险点**。原版数据可确认解锁协议/Exec 类型，但未证明这种线程安排属于原版。
- 现有 [ExecNotifyGroupLua](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecNotifyGroupLua.java) 在脚本尚未初始化或玩家不在目标 Scene 时，安排后续回调便 `return true`，之后回调可能因为玩家离开/任务状态变化而舍弃。它的 true 表达**已登记异步投递**，不是已完成 Scene Lua。符合 Q1 将**独立 World 后置效果**排除出 Quest 成功屏障，但不能把它归为 X1 必须立即完成的个人 Exec。
- [ExecRegisterDynamicGroup](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecRegisterDynamicGroup.java) 先在 World/Scene `loadDynamicGroup` 获得 `suiteId`，再写入 UID 主任务 `QuestGroupSuites`；[ExecUnregisterDynamicGroup](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/exec/ExecUnregisterDynamicGroup.java) 先执行 `Scene.unregisterDynamicGroup`，成功后才移除 UID 套件记录。两者均为 World→UID 的**真实结果依赖**，不能凭“后置 World 异步即可”假装个人状态已正确更新。
- [Scene.unregisterDynamicGroup](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/world/Scene.java) 在找不到指定 ScriptGroup 时返回 false，`ExecUnregisterDynamicGroup` 随后**跳过**个人 `QuestGroupSuites` 的删除。这个 false 有可能表示资源缺失，也可能出现在其他情况下；不能未经查证将其统一当成“成功清理”或直接以自动回滚处理。**World 依赖如何影响 Q1 完成条件继续由用户决定**。

### 33.4 历史候选（后续第 34 节已正式选定 S-F）：X1 遇到第一个失败后怎么办

[GameQuest.finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/GameQuest.java) 使用 `forEach` 投递每个 `FinishExec`；[QuestSystem.triggerExec](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/QuestSystem.java) 把每个处理器提交到静态 `QuestManager.eventExecutor`，处理器返回 false 只写 debug。**上游现状是所有 Exec 均会被投递，不会因先前 Exec 的业务失败而短路**；异步任务异常被 `ExecutorService.submit` 对应 Future 保存，旧代码没有读取 Future。原版服务端是否短路**未验证**。

在已定 **X1（即时个人 Exec 全部成功才报告 Quest 成功）+ 失败后不恢复既有状态/副作用** 下，原有两种候选已由用户在第 34 节**正式选择 S-F**；下列说明仅保留历史比较：

- **S-F：首次个人 Exec 失败后停止其余尚未执行的即时个人 Exec**，报告失败，保留以前已经发生的修改。这减少失败后新增副作用，但可能使部分独立任务标志不更新。
- **S-A：继续本轮剩余即时个人 Exec，最后汇总是否有失败**，只要有失败便报告失败，保留全部实际修改。这更接近当前 AstaPS 全部投递的行为，但会在已经知道本轮无法满足 X1 时继续产生副作用。

**特别限定**：后续第 34 节已选定**即时个人 FinishExec 按 S-F 短路**；正常 `FailExec`、World 后置效果、跨域依赖是否采用相同规则仍未决定；用户此前的“不处理”仅批准**不做事后恢复**。不得自行引入事务、补偿或新队列。

**可核查测试缺口**：上游 [QuestCommandTest](https://github.com/MeChen618/AstaPS/blob/main/src/test/java/emu/grasscutter/command/commands/QuestCommandTest.java) 当前只测 Quest 列表/可读文本，未断言正常 `quest finish` 的 Exec 结果与 F1 Shell 回执；`PlayerProgressManagerTransPointStateTest` 测状态转移与发奖去重，未覆盖 Quest Exec 对重复解锁的 X1 解释。未来对应的原位特征测试应分别建立，不能用修改原有测试掩盖 X1 尚未实现的事实。

**执行状态**：此节为原版与上游的源码审计；第 34 节正式选择 S-F，其余 World/UID 协调策略与具体 Java API 仍未授权；未改 Java/Gradle、未运行 CI。

## 34. S-F 决议：首个即时个人 Exec 失败后停止（2026-11-04）

**用户正式选择 S-F（回复“F”）**：正常 Quest 完成路径中，按照配置动作顺序处理本次属于 UID/R2 的即时个人 `FinishExec`。**首个即时个人 Exec 未实际成功，即停止本次剩余尚未执行的即时个人 Exec，不再尝试它们，并向 Q1/X1 返回失败**。已经执行过的动作及其个人副作用**保持原样**，不回滚、不自动重试或补偿（第 31 节）。**S-A（失败后继续全部个人 Exec）不采用**。这是 Quest 完成的**业务合同**，不是提前指定循环代码、Future 或调度模型。

### 34.1 原版 7.1 / 上游 / fork 复核

- **原版 7.1**：[原版 Quest 资源及 native 解码证据](https://github.com/RinoPaw/Genshin-Reverse/blob/main/versions/7.1.0-global/windows-x64/analyses/quest-extraction/README.md)确认 full-Quest 有 `finishExec` 数组，`35101` 有明确数据配置的 `TEAM_DEAD -> ROLLBACK_QUEST(35100)`；这些证明动作及配置来源，**不能证明官方服务器的动作失败短路策略**。原版官方服务端是否 S-F **未验证**。当前 S-F 是用户选择的 AstaPS 重构语义，不冒充原版。
- **上游 `MeChen618/AstaPS/main@ac9a7e6`**：[GameQuest.finish](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/GameQuest.java) 使用 `getFinishExec().forEach(e -> triggerExec(...))`，先将 Quest 状态设为 `FINISHED`，还可能先完成父任务；[QuestSystem.triggerExec](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/quest/QuestSystem.java) 为每个动作单独 `eventExecutor.submit`，`execute()==false` 仅写 debug，无法阻断已投递的后续 Exec；缺少 Handler 则跳过，没有 X1 成功回执。**上游目前不符合 S-F**。
- **fork `RinoPaw/AstaPS/rino/refactor/jline4-shell`**：截至本次研究该分支的 Quest Java 与上游仍一致，**尚未实际实现 S-F**；本轮仅记录决议。

### 34.2 已确定的规则与非授权范围

| 场景 | S-F 已决定的含义 | 未决定或独立合同 |
| --- | --- | --- |
| 第一个即时个人 FinishExec 成功，第二个失败，第三个尚未执行 | 第一个效果保留；第二个失败如实报告；**第三个不执行**；Quest Q1 不可宣告成功 | 未定义特殊补偿/错误存档，也没有授权修改任务已写状态 |
| 第一个即时个人 FinishExec 已经是业务上完成的目标状态 | **需能区分状态目标已满足与真正失败**，只有根据最终合法合同判为失败才触发 S-F；例如已解锁 Point 返回旧 `false` 不足以证明业务失败 | 对解锁/清理等每种动作的精确 API 与成功判定还待设计，不直接修改所有 `boolean` |
| 未注册 Handler、程序异常、明确业务拒绝 | 不能算“该动作成功”；不得继续后续即时个人 Exec 或报告 X1 业务成功 | 失败信息的结构、异常具体传播和显示形式仍未批准 |
| 独立 World 后置 Lua / Scene 事件 | S-F **只冻结即时个人 FinishExec 的后续执行**；不自动撤回已经安排的 World 动作 | World 后置效果的失败/取消/排队、混合 World→UID 动作的完成依赖另议 |
| `BeginExec`、`FailExec`、由当前任务启动的下一任务 | 本轮 S-F **不自动推广**至这些路径 | 如果需要统一执行策略，必须分别给出原版+上游证据，由用户选择 |

**实施难点（研究结论）**：要真正做到 S-F，个人 Exec 的先后顺序、执行完成与拒绝结果必须可靠可观察；上游“先把所有 Exec 异步 `submit()`，执行失败仅记日志”的机制无法实现。处理 World 后置动作时仍遵循 Q1/S2；混合动作涉及结果依赖时仍需新决策。用户没有授权同步等待 World、增加事务、引入恢复层或调度具体实现。

**验证目标（非已执行测试）**：序列 `Success,Failure,Success` 中第三个从未开始；第一个副作用保留；Q1/F1 报告失败；重复解锁目标已满足与无效 Point ID 正确区别；正常 Quest `failExec` 的显式原版回滚不受“禁止自动补偿”的误伤。本次**无 Java/Gradle 改动，无 CI**。

