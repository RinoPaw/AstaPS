# AstaPS CLI / JLine 4 Shell 研究与设计

> 更新：2026-11-03。研究基准：MeChen618/AstaPS main 的 `ac9a7e6cec900e4ced28ec5dc16ceda82cf7538d`；工作分支：RinoPaw/AstaPS 的 `rino/refactor/jline4-shell`。
>
> **状态：研究与设计阶段，未修改实现。** 本文只记录现有源码事实、已批准规则、未决事项。后文的示例命令均不表示已经实现。

## 1. 项目边界与当前方向

- 目标：引入**完整 JLine 4 Shell**，研究脚本、管道、条件执行、变量、作业控制等能力；具体功能是否开放及其语义须分别决定。没有批准建设多管理员管理平台。
- **Java 21 保持不变**。Java 25 是长期方向，FFM 迁移另行决策。研究时的 JLine 4 正式候选版本为 **4.4.8**（2026-10-12 发布），尚无 AstaPS 编译或实机验证结果。
- **不擅自新增兼容、适配、包装、抽象或迁移层**；不以保留旧插件、旧命令、旧存档为默认目标。接口、模型、类名和参数名由用户决定。
- 优先使用 JLine / Picocli 现有扩展点，先核对真实源码和行为再设计。只在现有分支工作，不创建新分支。
- 目前仅授权整理研究。改造前需逐项批准；要求用户测试前及提交上游前运行完整相关 CI。项目既定构建产物名为 `grasscutter-7.1.0.jar`。

## 2. 已核实的技术事实

### 2.1 AstaPS 当前命令与终端

| 部位 | 当前行为及影响 |
| --- | --- |
| `build.gradle` | Java 21；JLine Reader / Console / Terminal-JNA **3.30.14**，Picocli **4.7.7**、`picocli-shell-jline3`。 |
| `Grasscutter.java` | 手写 `LineReader.readLine → CommandMap.invoke` REPL；`TerminalBuilder.jna(true)`，失败可用 dumb terminal；双击 Ctrl+C 退出。改用 `Shell.run()` 会影响退出、信号和日志交互。 |
| `CommandMap.java` | 处理事件、记录命令、分词、目标预解析、旧语法、查找命令、权限、目标约束、线程投递，再由每次创建的 Picocli `CommandLine.execute(args)` 解析具体参数并执行。**当前具体参数解析晚于权限检查。** |
| 特殊输入 | `target`、裸 `@UID` 可以绕过 Picocli；旧命令先经 `LegacyCommandSyntax` 改写。补全用的 Picocli 命令树与实际调用时创建的树不同。 |
| 命令线程 | `threading=true` 提交通用线程池；未因此保证命令在统一游戏逻辑线程运行。 |
| 命令插件 | `CommandHandler.createCommandLine` 默认仍使用 `LegacyCommandLine`；旧 `execute(..., List<String>)` 仍在。文档 `plugin-command-api-v5.md` 对旧接口已移除的描述与源码不完全一致。 |
| 输出 | `CommandOutput` 对控制台写 Logger；`CommandOutputCapture` 用 `ThreadLocal`，不能可靠跨异步任务捕获反馈。 |
| Web Console | `WebConsoleHandler` 验证管理令牌后以 `sender=null` 调用 `CommandMap.invoke`；本地与 Web 目前共享 `CONSOLE_ID` 下的目标状态；远程入口另有完整命令原文日志。 |
| 其他 | `StartupArguments` 独立解析 `-game/-dispatch/-noconsole/-dump/-version`；是否迁移未定。Issue [#242](https://github.com/MeChen618/AstaPS/issues/242) 报告 Windows 中文乱码和日志干扰输入，尚未复现归因。 |

当前路径摘要：

~~~text
本地 / Web / 游戏聊天
  → CommandMap.invoke
  → 事件、日志、分词及目标处理
  → 命令匹配 → 权限 / 目标约束
  → 直接执行或投递线程池
  → 本次创建 Picocli CommandLine → 具体参数解析 → GM 业务
~~~

安全注意：原始命令日志、Shell history、脚本回显、HTTP 日志可能包含密码及令牌；不能默认记录全部参数。

### 2.2 JLine 4 Shell 源码核验

- JLine 4 提供 `Terminal`、`LineReader` 和 `jline-shell` 的 `Shell` / `Command` / `CommandGroup` / `CommandDispatcher` / `CommandSession`。Shell 可支持 `history`、`alias`、变量、`source`、管道 `|`、条件执行 `&&/||`、重定向 `>`、后台 `&` 与作业命令；**不自动定义游戏业务语义**。
- JLine 4 移除旧 JNA / Jansi 终端提供者，Java 21 可研究 **JNI / Exec / dumb** 与 `jdk11` 整合包；**FFM 提供者需要 Java 22+**。模块式依赖与整合包尚未选择。
- `CommandSession.get/put/remove/variables` 可存会话状态；`ShellBuilder.variable(...)` 操作的是 LineReader 变量，不会自动写入 CommandSession。默认 Dispatcher 只有一个根会话，Web 会话不会自然隔离。开启变量命令后 `set/unset/export` 可改公共变量；独立 `NAME=VALUE` 行也能修改变量。
- 管道中的 `CommandSession.fork(in,out,err)` **浅拷贝**变量及工作目录：子会话改变量不自动写回根会话，共享可变引用有风险。目标 UID 放公共变量不会自动受到权限保护。
- 官方 `PicocliCommandRegistry` 直接调用 Picocli `sub.execute(args)`，**不把 CommandSession 传给业务命令**；有终端时还向 `terminal.output()` 设置输出流，可能绕过 `session.out()/err()`，导致管道和重定向无法捕获。
- 在 JLine 4.4.8 `DefaultCommandDispatcher.runCommandStage()` 中，命令正常返回即将 `lastExitCode` 设为 **0**，异常设为 **1**；Picocli 返回非零整数也可能被 Shell 视为成功。因此 `&&` 与失败码需专门处理、验证。
- `executeBackground()` 使用守护线程，管道各阶段可能各有线程；这与 AstaPS 的 `threading=true`、游戏线程是三种不同机制。管道生产者异常不一定决定最终状态。
- JLine Shell 默认 Ctrl+C 清行继续、Ctrl+D 结束 REPL；与现有双击 Ctrl+C 退出语义不同。

**可参考的实际项目**：Paper/Minecraft（命令排队进入服务器处理周期、RCON 等待反馈）、Minestom（解析与执行分离）、TShock（REST 独立命令源及反馈）、SourceMod、LunaGC/Grasscutter；Karaf、JShell、Spring Shell、Minecrell TerminalConsoleAppender 可参考会话、交互和日志。Brigadier 是命令树/上下文解析对照方案，**尚未批准引入**。

来源：[JLine What's New](https://jline.org/docs/whats-new/) · [Shell Features](https://jline.org/docs/shell-features/) · [Terminal Providers](https://jline.org/docs/modules/terminal-providers/) · [Shell + Picocli](https://jline.org/docs/shell-picocli/) · [Shell Migration](https://jline.org/docs/shell-migration/) · [CommandSession 源码](https://github.com/jline/jline3/blob/4.4.8/shell/src/main/java/org/jline/shell/CommandSession.java) · [DefaultCommandDispatcher 源码](https://github.com/jline/jline3/blob/4.4.8/shell/src/main/java/org/jline/shell/impl/DefaultCommandDispatcher.java) · [PicocliCommandRegistry 源码](https://github.com/jline/jline3/blob/4.4.8/picocli/src/main/java/org/jline/picocli/PicocliCommandRegistry.java)。

## 3. 已确定：命令目标、身份与权限

### 3.1 目标语法与会话

| 决议 | 生效规则 |
| --- | --- |
| S01–S04 / S05B | **只支持显式选择器 `@UID` 和 `username@`**；暂不支持 `@s/@p/@r/@a`，也不添加 `@username` 等其他写法。 |
| S06A | 账号用户名唯一；`username@` 指账号用户名，不依赖可重名的游戏昵称。 |
| T01A | 一条命令显式选择目标，只对该命令有效，**不更改**会话默认目标。 |
| T02A | 本地 Shell、每个 Web 管理会话各自维护默认目标；游戏玩家也使用自己的状态。不能共用 `CONSOLE_ID` 的全局绑定。 |
| T03B | 支持对一批命令临时指定目标，结束后恢复原目标；**语法、嵌套与生命周期尚未决定**。 |
| 无参 `target` | 清除覆盖绑定并恢复执行者。游戏玩家回到自己；管理控制台没有关联的 Player，因此恢复后默认目标为空。 |
| P06A | 游戏玩家下线即清除其目标绑定，不跨登录、不存入账号。 |

示例（目标语法已确定，未实现）：

~~~text
target @10001
give @10002 1001 50      # 本条给 10002，不修改已绑定的 10001
give 1001 5              # 仍给 10001
target                   # 恢复真实执行者
target rino@             # 绑定唯一账号用户名
~~~

### 3.2 执行者和授权

| 决议 | 生效规则 |
| --- | --- |
| P01A | 保留 `Player sender`；受信任的服务器控制台使用 `sender == null`，本轮不引入独立执行者类型。 |
| P02A | 玩家可用 `target` 绑定其他玩家，无须额外的 `target.others` 权限；操作命令仍须授权。 |
| P03A / P04A | 玩家操作别人须同时有**基础权限和对应 `.others` 权限**；是否为自己通过**稳定 UID** 比较，不能比较 Java 对象引用。 |
| P05A | 经管理密码及会话验证的 Web Console 以**完整权限控制台主体**执行；不同 Web 会话状态必须隔离。 |

目标绑定**不改变 sender 身份或权限**；不能让游戏玩家伪造 `sender=null`。执行流程倾向解析目标和参数后授权，业务副作用必须在授权后发生。**X02B：当权限错误与参数错误并存时，优先报告权限错误**。这项诊断优先级与内部解析顺序需共同满足，尤其遇到无法解析目标、`.others` 判断和可能有副作用的类型转换器时。

仍未定：目标名称合法字符、绑定离线目标是否允许、无权命令在帮助/补全中可见性、Shell 内置命令授权、会话变量能否由 `set/unset` 修改目标、权限检查使用哪个扩展点。

## 4. 已确定：Give 命令

### 4.1 输入和执行

~~~text
give [选择器] <item> [数量] [类型专用命名选项...]

give 1001
give 1001 50
give @10002 1001 50
give rino@ 1001 50
~~~

| 决议 | 生效规则 |
| --- | --- |
| G01B / G02A | 物品只识别数字 ID 与有效内部资源标识（如 `genshin.*`），不按本地化名称识别；带空格的参数须加引号。 |
| G03A | 数量为**十进制正整数**，位置参数，默认 **1**。 |
| G06A | `give` **只对在线玩家**执行；需有效活跃游戏会话。 |
| G07A / G08A | 删除 `-n/--amount` 和**尾置**选择器语法。 |
| G09C | 删除 `all/weapons/mats/materials/avatars/characters` 等内置批量类别。**整套圣遗物资源 ID 另按 B05A 保留支持。** |
| G10B | 武器、圣遗物、角色附加属性重新设计，不直接沿用旧语法。 |
| D01B | `give` **直接同步**在调用线程执行，移除本命令 `threading=true`；不自行加“异步然后等待”层。 |
| X01B | 完成实际发放/解锁业务、取得真实结果后才算命令完成；不额外承诺所有异步数据库写入均已落盘。 |
| X02B | 同时存在权限与参数错误时，**优先权限错误**。 |

显式选择器放在物品参数前；省略时从当前执行环境读取默认目标。游戏玩家默认自己，管理控制台只有绑定目标后才能运行需要 Player 的命令。通用目标与权限由命令体系负责，类型专用选项交给对应构造逻辑。

### 4.2 容量、截断与结果

| 决议 | 生效规则 |
| --- | --- |
| G04C | 超出**实际可存放数量**时截断发放；截断属于**正常成功结果**，包括实发 **0**。 |
| X03A | 实发正数且该部分成功入库，整体成功；报告请求数、实发数及截断。 |
| X04B | 因容量截断至 **0**，也视为成功；明确反馈“无物品变更”及原因。 |
| L01A | 材料、家具只按自身 `stackLimit`、已有数量、库存槽位容量约束；无额外单次命令限额。 |
| L02A | 武器、圣遗物只按装备库存剩余槽位约束件数；无额外单次命令限额。 |
| L03A | 虚拟货币按已有余额、数值及业务容量约束；无额外单次命令限额。 |
| L04A | 经验、树脂等即时效果遵循各自业务规则；无统一单次命令限额。 |
| L05A | 将来若批准单次命令限额，超过限额也采用正常截断；**目前未批准新增这种限额**。 |

**反馈要求**：背包容量不足时明确提示**背包空间不足**，以及请求、实发、未发数量；实发 0 提示无变更。由货币或其他业务容量导致的截断应报告其真实原因。**权限不足、非法参数、未知物品、目标离线、实际入库失败或执行异常仍是失败**，不得伪报成功。

实际源码核验：
- 材料/家具每个 itemId 一份堆叠，`ItemData.stackLimit` 是该 ID 的**总持有上限**；还有不同 itemId 的槽位容量。可研究按 `max(0, stackLimit - held)` 计算剩余，但最终计算细节尚须验证。
- 武器/圣遗物每件占一个装备槽位；配置默认 `weapons=2000/relics=2000/materials=2000/furniture=2000` 是库存容量配置，**不是 give 的固定单次阈值**。
- 虚拟货币/经验不一定进库存；余额溢出、树脂和即时解锁的业务边界需分类核验。
- 当前 `Inventory.addItems()` 默认 `ALL_OR_NOTHING`；`Inventory.addItem()` 返回 boolean。当前 `GiveCommand` **未检查结果，却可能输出成功**，必须修复。
- `InventoryGrantBuilder` 有 10,000 条展开上限，但当前 `GiveCommand` 的装备路径没有经过它；不能把这项实现限制当成已批准的 give 单次上限。
- 普通 `useOnGain` 在现有单件路径只执行一次直接效果，即使 `count > 1`，与批量预检行为不同；重构时须逐类处理。
- 同步调用可能占用游戏聊天或 Web HTTP 线程；D01B 已知代价，仍须研究游戏状态线程安全，未经批准不改为异步。

### 4.3 复杂属性的已批准行为

| 决议 | 生效规则 |
| --- | --- |
| A01A / A02A | 附加属性采用**命名选项**；显式属性越界**报参数错误**，不静默夹到上/下界。数量的 G04C 截断规则独立有效。 |
| A03A / A04A | 新武器默认 **1 级、精炼 1**。 |
| A05A / A06A | 新圣遗物默认强化 **+0**；输入游戏显示等级（如 +20），须转换为底层等级并按星级校验。 |
| A07A / A08A / A09A | 新角色默认 **1 级、0 命、三个基础天赋各 1 级**。 |
| A10A | 已有角色只更新**显式指定**的属性，其余保持原值；后述显式突破触发等级调整例外。 |
| B01A / B01B | 等级可自动确定所需突破阶段，也允许显式指定突破阶段。 |
| B02A / B03A | 未指定主/副词条时，圣遗物按游戏数据的合法规则生成。 |
| B04A / B04B | 可统一指定三个基础天赋等级，也可分别指定普攻、战技和爆发等级。 |
| B05A | 支持按套装资源标识一次发放整套圣遗物。 |
| B06A / B06B | 已拥有的角色若无显式属性变更，**成功且不改变角色属性**，同时提示已拥有。 |
| C01A | 统一天赋值与单项值共存时，**单项显式值覆盖对应统一值**，不受选项输入顺序影响。 |
| C02A | 等级、突破阶段均显式指定时以**指定突破阶段**为准；无法容纳指定等级则参数错误。 |
| C03C / C06B | **只指定突破阶段**时，将角色等级调整至该阶段允许的范围：低于最低门槛则升至门槛，高于最高允许等级则降至上限；已拥有角色同样适用。 |
| C04A | 套装数量按**完整套数**计，5 件套数量 2 即请求 10 件。 |
| C05B / C07A | 背包不足允许发放不完整套装；多套时**先保证尽量多的完整套**，再发下一套部分部件，按 G04C 成功并报告缺少件数。 |
| C08A / C08B | 发套装同样支持显式主、副词条；不能像当前 `giveWholeSet()` 一样直接丢弃这些输入。 |

**参数命名最新决定（覆盖旧 N05–N14）**：A01A 只确定命名选项形式；N01A 复合名称采用 kebab-case、N02A **仅长选项无短别名**、N03A 角色/武器/圣遗物共享一个等级选项名、N04A **大小写敏感**。**所有具体英文长参数名称均推迟决定**。之前讨论的 `--level`、`--sub-stat` 等属于历史候选，不能当成正式接口。

**尚未确定的属性细节**：
- C09：套装中某部位不支持显式主/副词条时怎么办；超出原版词条池的异常属性是否允许。
- 圣遗物显式主/副词条的值语法、重复/随机规则与部分套装的固定部件顺序；各系统业务细化。
- 已有角色只给数量、套装属性广播到哪些部件等具体行为与反馈措辞。

异常圣遗物源码事实：生之花原版主词条固定为平生命值，暴击可作副词条；`GameItem.mainPropId` 与 Proto 能持有任意数字 ID，现有命令数字路径未按主词条池验证，角色属性计算用全局 ID 查表。**不能据此推断原版 7.1 客户端能正确显示/装备/强化这种异常组合**，需要原版实测；不能替用户决定开放或禁止。

## 5. 物品模型研究（最新决定优先）

### 5.1 新统一接口

**已确认名称：`InventoryItem`**，表示可交给库存系统管理的运行时物品，即使当前尚未实际入库。已确定最小接口：

~~~java
int getItemId();
long getGuid();
~~~

- `itemId` 标识物品种类，**GUID 标识具体运行时库存实例/材料堆叠**。
- **武器、圣遗物、材料堆叠**均属于此接口范围；材料采用“一个物品种类对应一份可变数量的堆叠对象”。`MaterialStack` 目前只是候选类名。
- **GUID 归物品实例持有，由 `Inventory` 在接收新物品时分配**（第 33 节已确认）。GUID 写入方式、分配前状态、是否持久化、登录时是否重新分配、复制后身份及失效时机**尚未决定**。
- 原石/摩拉等余额、`Avatar` 与一次性解锁效果**暂不作为 InventoryItem 实例的默认范围**。
- 未决定其他接口方法（如 ItemData、数量或赋值方式）、具体类层次、持久化/恢复、装备关联及数据库映射。

### 5.2 构造职责与 GameItem 历史方案

**已确认的构造边界**：
- `GiveCommand` 解析目标、物品标识并选择所属类型，将**类型专用的原始词元、顺序和重复选项**交给该类型的构造入口；命令本身不解析或手工设置武器/圣遗物的专属属性。
- **武器、圣遗物**必须分别有自己的复杂属性构造逻辑；角色使用其专属初始化/更新逻辑。构造入口负责类型属性默认值、验证和实例初始化。
- **Inventory 负责入库、容量、堆叠、身份分配等库存行为**；相关系统负责持久化、通知、插件事件和特殊效果。构造时不因本条决定直接修改玩家背包或发包。
- `Avatar` 当前单独存储于 `avatars` 集合；已拥有角色的操作按 A10A 等规则更新，不能强制构造一个新角色。

**历史方案需按最新讨论重审**：第 30 节曾提出 `GameItem` 基类加武器/圣遗物派生类。第 31–33 节已进一步转向**从零设计 `InventoryItem` 体系、评估替代 GameItem**。所以**“GameItem 必须保留为基类”不应再视为已批准的最终类结构**；也没有批准直接删除其实现。先确定接口、对象模型与保存方案，再决定如何处理旧类，不擅自增加兼容层。

当前源码：`GameItem` 是 Morphia `@Entity(value="items", useDiscriminator=false)`；`DatabaseHelper.getInventoryItems()` 以 `GameItem.class` 读取。构造器按 `ItemType` 初始化多类物品，圣遗物有随机属性；`copyForGrant()`、反射注册、库存/奖励/商店/协议路径也依赖这一实体。实际重构需检查多态持久化与数据恢复，**不默认迁移旧存档**。旧 `MaterialInventoryTab` 按 itemId 存 `GameItem`，`EquipInventoryTab` 持有独立实例；这是现状，尚未批准作为新模型。

## 6. 未决问题与下一步研究

| 主题 | 必须继续决定或验证 |
| --- | --- |
| Shell 架构 | JLine 原生 Command 与 Picocli 的具体结合方式；按次创建命令模型；`CommandSession` 如何传入 GM；如何修复 Registry 输出绕流及 Dispatcher 退出码。 |
| Shell 功能 | `history/alias/source`、变量、`|/&&/||/;/>`、后台 `&/jobs` 的开放范围、文本/结构化输出、成功/失败/取消和文件系统授权。 |
| 目标与身份 | 临时批量目标作用域语法、Shell/Web/游戏会话创建销毁、玩家下线清理、变量是否可改默认目标、权限解析和错误优先级。 |
| 游戏线程 | 同步 give 的调用线程安全性；其他命令调度、反馈、持久化与 Shell 作业生命周期。 |
| Give 业务 | 类型专用选项的**具体名称**、圣遗物词条与 C09、套装部分发放顺序、即时效果及解锁的精细规则、真实库存入库结果。 |
| 物品模型 | `InventoryItem` 其余 API、具体实现与类关系、GUID 持久化/复制/分配前状态、数据库存储/恢复和装备引用。 |
| 终端与部署 | Windows/PowerShell 7 中文 IME、UTF-8、日志重绘、粘贴、Tab、Ctrl+C/D；Linux TTY/SSH/无 TTY、服务关闭生命周期、Web 输出与敏感日志。 |
| 其他 | `StartupArguments` 是否迁移、插件命令 API 是否破坏性升级、所需测试矩阵及 CI 验收标准。 |

**推荐推进顺序（尚非实施授权）**：先逐项定下 InventoryItem/Give 的模型边界和 Shell 接入机制 → 对照 AstaPS 当前代码覆盖解析、权限、目标、库存、存储、输出等路径 → 获准后修改现有分支 → 进行相关单元与集成测试、Windows 真机验收，并按要求运行 CI。

补充资料：[CLI Picocli 迁移](cli-picocli-migration.md) · [命令全量审计](cli-command-wide-audit-20261010.md) · [插件 API v5](plugin-command-api-v5.md) · [上游仓库](https://github.com/MeChen618/AstaPS)。本文保留**当前生效结论**，不再重复逐日讨论及被后续决定覆盖的候选方案。

## 34. 2026-11-03 装备操作成功与异步持久化：选择 ①

用户明确选择之前提出的 **①：装备操作在内存中成功后即可响应客户端，数据库异步保存**。

- 装备、卸装和跨角色交换完成运行时状态变更后即可报告操作成功；不要求等待 MongoDB 写入确认。
- 此决定接受在服务器崩溃或异常断电时最近装备操作未落盘、跨物品写入只完成一部分的可能性。
- 具体的加载校验与故障恢复策略、写入顺序和失败处理、运行时原子性/并发协调实现尚未确认；不得将本决定解释为允许任意数据丢失或自动批准某种恢复规则。
- 此决定不预设 MongoDB 多文档事务，不要求将装备关系持久化为某个具体 A/B/C 模型；装备关系的存储归属仍未决定。
- 当前仅记录架构决策，不修改代码、不新建分支、不运行 CI。

## 35. 2026-11-03 装备关系持久化权威采用 A；优先避免冲突

用户明确确定：**A：装备物品自身记录被哪个角色装备**。这是装备关系的持久化权威；角色的槽位映射可以在内存中建立，不据此另行批准角色侧持久装备列表或独立关系集合（B/C）。

用户随后补充：**尽量不要存在装备关系冲突**。设计应优先从操作不变量、并发协调和写入机制消除冲突，避免依赖登录时任选一件装备来事后掩盖冲突。以下是方向约束，尚未批准具体机制。

- 已有第 34 节决定继续有效：内存完成后即可向客户端响应，数据库异步持久化。
- 运行时必须研究如何防止同一角色同槽重复装备及一件装备重复归属多个角色；装备更换、跨角色交换、卸装、删除和转移要一致处理。
- 在 A 模型下，一次装备替换可能涉及多条物品记录。仅按玩家或物品对异步任务排序，可以避免乱序，但**不能保证崩溃时多个记录同时落盘**。是否要求数据库无中间状态、是否允许 MongoDB 事务或新增明确的槽位字段/唯一索引，仍须单独决定。
- 装备关系记录采用角色的 `avatarId`、角色 `ObjectId` 或其他玩家范围内稳定键；GUID 是否持久化；加载修复策略均未批准。
- **本次只记录用户确认的方案 A 和避免冲突的目标，不修改程序代码、不创建分支、不运行 CI。**
