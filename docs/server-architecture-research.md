# AstaPS 全局架构审计与重构方向

> 2026-11-03。源码基准：`MeChen618/AstaPS/main@ac9a7e6cec900e4ced28ec5dc16ceda82cf7538d`。研究分支：`RinoPaw/AstaPS/rino/refactor/jline4-shell`。
>
> **已批准目标 A：以整个 AstaPS 的统一、清晰架构为长期重构目标，CLI 与库存为首批切入点，之后逐步覆盖其他业务。**
> **已批准 M2：允许采用 Gradle 多模块工程，按需要划定独立编译、依赖和构建产物边界。已批准 D1：保留 HYBRID、GAME_ONLY、DISPATCH_ONLY 三种运行模式，允许 Game/Dispatch 独立进程，也允许同进程运行。** 仍处于源码审计与设计阶段；尚未批准模块数量、具体模块接口、独立 JAR 数量或开始代码迁移。保持 Java 21，Java 25 作为未来方向；不自建无实际需要的兼容层，不新建分支。

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

这一图只是用来定位责任。运行时既有玩家状态，也有多人世界/场景状态，不能假设所有业务都只属于某个 Player。Dispatch 的账号与授权也可能有自己的生命周期。

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

## 7. 真正影响全局的待决边界（暂不替用户决定）

1. **M2、D1 已决；构建产物边界仍待研究**：允许多个 Gradle 模块并保留三种运行模式。是否需要 Game、Dispatch 分别发布可执行 JAR，以及是否仍提供联合打包的单 JAR，尚未决定；先分析跨模块依赖与部署价值，不能把“独立构建模块”误写成“强制独立部署”。
2. **状态与执行模型**：玩家级/世界级状态操作希望最终如何拥有执行权，是否逐步收敛到明确的逻辑执行域？当前多个线程可写同一状态，现有锁没有统一约束。具体线程/锁/API 在该方向确定之后再谈。
3. **扩展能力范围**：重构后的插件、Lua、GM 是否允许直接操作内部可变对象，还是必须遵守业务操作契约？这影响整个生态的扩展与隔离能力。
4. **模块划分与依赖边界**：M2 已允许 Gradle 模块化；模块数量、公共 API、能否共享服务器启动/协议/资源、构建产物如何组成、是否引入显式服务注册，仍需依赖证据和具体决策。

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

**尚未进入代码改造阶段**。与 [CLI/JLine 4 研究](cli-jline4-shell-research.md) 配套使用；后者记录已经确认的细粒度行为决议。本文件只覆盖全局架构与迁移方向。
