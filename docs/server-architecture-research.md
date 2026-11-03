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

**尚未进入代码改造阶段**。与 [CLI/JLine 4 研究](cli-jline4-shell-research.md) 配套使用；后者记录已经确认的细粒度行为决议。本文件只覆盖全局架构与迁移方向。
