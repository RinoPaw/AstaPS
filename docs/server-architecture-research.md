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
| **持久化 P1** | R2 内完整同步修改玩家内存；同 UID 异步有序写入；经济操作先获得写入准入；奖励回执保留同步持久确认例外 | [CLI/库存第 7、9 节](cli-jline4-shell-research.md) |
| **CLI/Give D01B 与 F1** | 直接在调用线程修改 Inventory 的旧规则被 R2 覆盖；**F1 已决定 Shell 等待真实业务完成再产生退出状态**，只允许外部交互线程等待，游戏执行域间禁止阻塞等待 | [CLI/库存第 4.1、10.2、11 节](cli-jline4-shell-research.md) |

**按主题阅读**：第 1–5 节工程现状与原则 → 第 6–7 节推进次序及未决边界 → 第 8–11 节模块化与插件研究 → 第 12 节历史线程审计 → 第 13–18 节 S2、Player/World 纠偏、跨域业务、R2、结果语义与真实共享状态审计。具体 Give、InventoryItem、TPS、数据库写入与登录竞态以 [CLI/库存研究](cli-jline4-shell-research.md) 为主，避免两份文档维护重复的细粒度决定。

**仍未决定**：Player/World 跨域非阻塞协调与活动 Avatar 战斗属性所有权 B1/B2/B3、会话网络进出顺序；F1 以外的 Web/API 等待/超时/查询行为与输出传递技术、执行器和队列具体实现、账号/登录生命周期接入、模块边界与发布产物。**F1 Shell 等待真实业务结果已正式选择**。Java 21 不变，Java 25 只是长期方向；未经批准不引入 Actor 库、兼容层或改动既有 JAR 名称。

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

1. **R2/S2 跨域协作及共享战斗态**：拾取、宝箱与世界 Boss 奖励、联机迁移、角色装备影响世界实体时，结果、去重、失败与状态可见性如何协调；**EntityAvatar 与 Avatar 共用可变战斗属性**的边界已在第 18 节证明，B1/B2/B3 尚未决定。参阅第 15–18 节。
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

**核心发现：S2/R2 虽已明确逻辑归属，但上游现有对象图中有同一个可变对象同时供 Player 和 World 修改，且网络发送没有统一序列化契约。** 如果仅将 Tick/命令分发改到两个并行执行器，会把目前靠偶然执行顺序掩盖的问题显性化。以下是已验证源码与设计候选，**没有批准 B1/B2/B3，也没有决定新增第三类 Session 游戏状态执行域**。

### 18.1 EntityAvatar 与 Player Avatar 真实共享同一张 fightProperties Map

1. [`EntityAvatar.java`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/entity/EntityAvatar.java) `EntityAvatar.avatar` 是原始 `Avatar` 对象引用；`getFightProperties()` **直接返回 `avatar.getFightProperties()`**。因此 World 的 `EntityAvatar` 和 Player 的装备/养成系统访问的是**同一份可变 map**，未做快照或领域隔离。
2. [`Avatar.java`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/avatar/Avatar.java) 将 `fightProperties` 定义为 `Int2FloatOpenHashMap`，`recalcStats()` 先读取当前 HP/能量后**直接 `clear()` 整张表并重建**；装备、等级、圣遗物等个人业务会触发这一过程。另一方面 [`GameEntity.java`](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/entity/GameEntity.java) 的战斗写入调用 `getFightProperties().put()`；[EntityAvatar.heal/revive/energy](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/entity/EntityAvatar.java) 直接读写同一张表。
3. `Avatar.currentHp` 与 `currentEnergy` 是玩家角色要恢复的个人持久状态；[AvatarStatePersist](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/game/avatar/AvatarStatePersist.java) 在重算后恢复血量/能量。**“状态归属 Player”不能简单理解成战斗时整个可变 Avatar Map 只由 R2 执行；“世界模拟归 World”也不自动转移 Avatar 持久存档所有权**。
4. `Avatar.queueFightPropNotify()` 使用一个**独立单线程 ScheduledExecutorService** 延迟发送，延迟回调读取同一 `Avatar`，构造 [PacketAvatarFightPropNotify](https://github.com/MeChen618/AstaPS/blob/main/src/main/java/emu/grasscutter/server/packet/send/PacketAvatarFightPropNotify.java) 时拷贝 `avatar.getFightProperties()`，并可能从场景活动 `EntityAvatar` 再读取血债。若 Tick 与 R2 真正并行，**第三个定时线程**也可能参与读取活动战斗状态。线程安全集合/Map 的某些替换不能自动解决“装备重算清空一半时已被世界计算或发包观察”的业务一致性。

此处可确认存在**共享可变对象及无同步的潜在竞态路径**；未在本轮实机复现，不能声称当前已观测到 map 破坏、丢血或封包错误。

### 18.2 持续活动的角色战斗状态：三条实质不同的设计路线（待决定）

| 候选 | 活动角色战斗属性/HP/能量的实时权威 | 与已选 R2/S2 的关系 | 主要代价 |
| --- | --- | --- | --- |
| **B1：World 持有 Live Combat State，Player 持有持久 Avatar**（推荐研究） | EntityAvatar 拥有当前战斗态；Player 的装备/养成/持久角色状态经明确的更新/同步协议投影到 World，离场/退出时把需持久化的 HP/能量结果提交回 Player | World 热路径本地处理伤害/元素能量，Player 个人业务继续按 R2；**两个权威各司其职** | 定义快照/修订序列、实时装备更新、战斗同时重算、HP/能量同步/登出屏障，避免双重权威和旧结果覆盖 |
| **B2：Player 持有战斗属性实时权威** | World 的伤害、治疗、能量修改统一请求 Player 域，World 只消费已有结果 | 玩家完全独占 Avatar mutable state，能减少持久与实时状态区分 | 战斗高频操作需要跨域等待/续接，增加延迟和顺序协调；严禁 World 阻塞 R2 等结果 |
| **B3：活动 Avatar 战斗态由所在 World 临时租用** | Player 在线进入 World 时转移活动战斗态写权，离开/切换时归还；养成业务需要与租用期间的 World 协作更新 | 尽量复用一份战斗状态，世界热路径仍本地 | 生命周期租约、跨 World 移交和非活动角色状态更难规定，装备业务完成/存档时点跨 R2/World |

**B1 的推荐依据**：把高频实时模拟留在 S2 World，把长期身份、持有/装备与个人存档留在 R2 Player；世界切换不会改变 `Inventory` 所有权。**这只是一项待深入验证的模型建议**，不是用户新批准的 B1，也不要求立即新增 `CombatState` 类或改数据库模式。

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

本节只核实对象共享与协议顺序风险，提出 B1/B2/B3 候选。**没有修改 Java、持久化、模块、线程池、插件 API；未批准上述三选一或新的 Session 调度对象。** 文档阶段不运行 CI。

**尚未进入代码改造阶段**。与 [CLI/JLine 4 研究](cli-jline4-shell-research.md) 配套使用；后者记录已经确认的细粒度行为决议。本文件只覆盖全局架构与迁移方向。
