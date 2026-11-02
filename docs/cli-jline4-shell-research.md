# AstaPS CLI / JLine 4 Shell 重构研究记录

> 更新：2026-11-02。研究基准：MeChen618/AstaPS 的 main，提交 `ac9a7e6cec900e4ced28ec5dc16ceda82cf7538d`。工作分支：`RinoPaw/AstaPS:rino/refactor/jline4-shell`。本文件记录调研与待决策问题，**不代表已经批准的实施方案或已运行的测试结果**。

## 0. 状态、范围和设计主权

- 当前分支从上游 `main` 的上述提交创建；本文提交前，与基准相比无代码变更。
- 用户明确选择研究「引入**完整 JLine 4 Shell**」，愿意承担重构周期和风险。完整引入依赖、真正启用哪些 Shell 功能、游戏端共享哪些语义、接口如何变更，仍须逐项决定。
- **禁止未经明确批准添加兼容层、适配层、包装层、抽象层或迁移层**；也不得默认为了兼容旧插件、旧命令或旧数据而保留设计。这里提及现有 Legacy 代码，只是在描述事实。
- **权限检查时机的明确倾向**：先完成 GM 命令的参数解析，再做权限及目标约束检查，检查通过才允许游戏业务产生副作用。具体使用 Picocli `IExecutionStrategy`、JLine `CommandDispatcher` 或其他现有扩展点，尚未决定。
- 长期技术方向包含 Java 25，但**当前 Java 21 不变**；升级 Java / 选择 FFM 是另一个独立决策。
- 本次只整理调研文档：不修改源码、构建文件、CI、插件 API 或任何命令行为。

### 研究对象与名词

- **CLI（Command-Line Interface）**：通过文字命令管理服务器的界面。
- **REPL**：读取一行输入、处理、显示结果，再读取下一行的循环。
- **Shell**：支持命令组织、组合、变量、脚本等能力的交互式命令解释器。
- **终端模拟器**：Windows Terminal 等窗口/字符显示程序；PowerShell 7 是宿主 Shell；JLine 是 Java 进程内部的终端交互库。
- **Picocli**：Java 命令定义与参数解析库，提供位置参数、选项、类型转换、子命令、帮助、解析结果与执行策略；实际游戏权限和游戏逻辑由应用提供。
- **CommandGroup / CommandDispatcher / CommandSession**：JLine 4 Shell 的命令组织、分派与会话接口，后文详述。
- **Brigadier**：Mojang 的命令树解析与执行库；是有价值的对照方案，目前未决定引入。

## 1. AstaPS 当前源码事实

### 1.1 版本与入口

[`build.gradle`](../build.gradle)：Java toolchain 与编译目标均为 **21**；当前主要依赖：

~~~gradle
implementation 'org.jline:jline-reader:3.30.14'
implementation 'org.jline:jline-console:3.30.14'
implementation 'org.jline:jline-terminal-jna:3.30.14'
implementation 'info.picocli:picocli:4.7.7'
implementation('info.picocli:picocli-shell-jline3:4.7.7') {
    exclude group: 'org.jline', module: 'jline'
}
~~~

[`Grasscutter.java`](../src/main/java/emu/grasscutter/Grasscutter.java) 创建全局 `CommandMap`、管理 `LineReader`，调用 `TerminalBuilder.builder().jna(true).build()`，异常时退回 dumb terminal，并通过 `startConsole()` 的手写循环调用 `readLine(commandMap.getConsolePrompt())` 和 `commandMap.invoke(null, null, input)`。Ctrl+C 双击退出、EOF、终端关闭等特殊行为均在这一层实现；替换为 JLine 4 `Shell.run()` 将改变这些行为，必须明示是否接受。

[`StartupArguments.java`](../src/main/java/emu/grasscutter/utils/StartupArguments.java) 独立解析 `-game`、`-dispatch`、`-noconsole`、`-dump`、`-version` 等 JVM 启动后的参数；当前不进入 Picocli。**启动参数是否属于本轮 CLI 重构尚未决定。**

### 1.2 CommandMap 的实际处理顺序

[`CommandMap.java`](../src/main/java/emu/grasscutter/command/CommandMap.java) 集中负责注册、别名、补全、旧语法重写、目标选择、权限、调度和执行。主路径（以当前代码为准）：

~~~text
本地终端 / Web Console / 游戏内命令
  -> CommandMap.invoke(sender, target, rawMessage)
  -> ExecuteCommandEvent （可取消或更改输入）
  -> 按配置记录原始命令
  -> JLine DefaultParser 分词
  -> 特殊处理 target / @UID 目标设置
  -> 解析命令名称、旧命令名/参数改写、查找处理器
  -> getTargetPlayer（可消费内联 @UID）
  -> PermissionHandler.checkPermission
  -> targetRequirement 检查
  -> threading=false: 当前线程直接执行
     threading=true : 向 Grasscutter 线程池提交任务
  -> 为本次调用创建 Picocli CommandLine，调用 execute(args)
  -> 具体 GM 业务
~~~

**与设想的「先 Picocli 完整解析，再检查权限」不同**：当前先确定目标并完成权限检查，之后才调用 Picocli `execute` 解析具体命令参数。当前 `CommandMap` 还会在排队任务真正运行前重新检查目标有效性，但不自动重新做全部权限检查。

`executeCommand(Runnable runnable, boolean threaded, Executor executor)` 本质上只在「当前调用线程直接运行」和「提交通用线程池」之间二选一。现有代码**未通过该函数保证所有世界修改命令进入统一游戏逻辑线程**。

### 1.3 不是所有命令都进入 Picocli

- `target @10001` 以及直接输入 `@10001`：由 `CommandMap` 的保留语法处理，绕过 Picocli 命令调用。
- 未找到的命令直接报错；旧式名称/参数可能先经 [`LegacyCommandSyntax.java`](../src/main/java/emu/grasscutter/command/LegacyCommandSyntax.java) 改写，再走 Picocli。
- 游戏 GM 命令一般最终通过 Picocli，但 **补全树** 与 **本次真实执行的命令树** 是两种用途：`rebuildPicocliTree` / `createCompletionCommandLine` 用于命令查找、补全与帮助；实际业务执行通过 `handler.createCommandLine(sender,target)` 新建命令模型。
- `@UID` 属于 AstaPS 的全局目标语法，当前 `getTargetPlayer` 可以在 Picocli 解析前从参数列表移除；`@Command(inlineTarget=false)` 可使某些命令将 `@UID` 保留为自己的位置参数。Picocli 的 `@file` 参数文件展开在当前命令树中禁用。
- 上述保留语法之外，`history`、`alias`、`jobs` 等未来的 **JLine Shell 内置命令**也可能直接由 JLine 执行；是否启用以及如何授权均未决定。

### 1.4 插件契约与文档差异

[`CommandHandler.java`](../src/main/java/emu/grasscutter/command/CommandHandler.java) 定义 `createCommandLine(Player sender, Player targetPlayer)`，默认实现仍调用 `LegacyCommandLine.create(...)`；同时保留旧 `execute(Player, Player, List<String>)` 的默认方法。**这与** [`plugin-command-api-v5.md`](plugin-command-api-v5.md) **宣称旧命令契约已经移除的表述不完全一致**。研究或删除旧 API 前应以实际源码和插件载入检查为准，禁止为了保留它而擅自创建兼容层。

命令迁移和语法历史参见 [`cli-picocli-migration.md`](cli-picocli-migration.md)，已有全量静态审计参见 [`cli-command-wide-audit-20261010.md`](cli-command-wide-audit-20261010.md)。注意某些命令还存在内部额外权限检查，单独替换根命令的权限规则可能改变权限范围。

### 1.5 输出、日志与远程入口

- [`CommandOutput.java`](../src/main/java/emu/grasscutter/command/CommandOutput.java)：发送给玩家或服务器日志；`sender == null` 时由服务器 Logger 输出并尝试放入 `CommandOutputCapture`。
- [`CommandOutputCapture.java`](../src/main/java/emu/grasscutter/command/CommandOutputCapture.java) 使用 `ThreadLocal<List<String>>`。仅捕获**同一调用线程**的反馈；`threading=true` 后反馈可能无法出现在 HTTP 响应里。
- [`WebConsoleHandler.java`](../src/main/java/emu/grasscutter/server/http/console/WebConsoleHandler.java) 通过登录令牌验证 HTTP 请求后，仍使用 `commandMap.invoke(null,null,line)` 调命令，且记录完整命令文本。Web 与本地终端都使用空玩家（`null`）调用者，`CommandMap` 中的 `CONSOLE_ID` 导致目标选择状态可能共享。
- [`JLineLogbackAppender.java`](../src/main/java/emu/grasscutter/utils/objects/JLineLogbackAppender.java) 把编码后的日志文本交给 `Grasscutter.getConsole().printAbove(...)`。需要验证终端生命周期、UTF-8 编码、日志并发、输入重绘和无 TTY 退化。
- [`CommandMap.java`](../src/main/java/emu/grasscutter/command/CommandMap.java) 在开启 `SERVER.logCommands` 时记录原始命令；账号创建/重设密码等命令可能含敏感数据。远程控制台也有单独的原文日志路径。未来 Shell 历史存盘、脚本回显、审计日志都必须纳入同一敏感信息审查。

### 1.6 已知质量反馈

[上游 Issue #242](https://github.com/MeChen618/AstaPS/issues/242) 反馈 Windows 控制台中文乱码、日志干扰命令输入，以及若干 7.1 游戏功能故障。终端问题**尚未复现和归因**；JLine 4 的 Unicode/终端改进不能被直接当成已经修复该 Issue 的证据。

## 2. JLine 4 的技术定位、成熟度与边界

### 2.1 版本与 Java

2026-11-02 查到最新正式 **JLine 4.4.8**，发布于 2026-10-12（[版本列表](https://mvnrepository.com/artifact/org.jline/jline/versions)）；这是研究候选版本，尚未在 AstaPS 编译。JLine 4 基础最低 Java 11，AstaPS Java 21 可以采用 JNI / Exec 终端提供者。**FFM 提供者需要 Java 22+**；未来希望迁移 Java 25，但当前不修改 Java 版本。

JLine 4 移除了旧的 JNA / Jansi 终端提供者，改用 JNI、FFM、Exec、dumb 等路径；现在的 `.jna(true)` 与 `jline-terminal-jna` 不能直接保留为 JLine 4 方案。官方有面向 Java 11–21 的 `org.jline:jline:<version>:jdk11` 整合包变体，是否采用整合包或模块式依赖，仍是待决策项。JDK 21 下**不能把 FFM 当作与 Java 25 上相同的正式终端方案**。

资料：[What's New in JLine 4](https://jline.org/docs/whats-new/)、[Terminal Providers](https://jline.org/docs/modules/terminal-providers/)、[Getting Started](https://jline.org/docs/intro/)。

### 2.2 JLine 基础模块与 Shell 模块

- `Terminal`：终端设备、字符输入输出、尺寸、信号与原生提供者。
- `LineReader`：行编辑、快捷键、历史、补全、高亮、`printAbove` 等。
- `Shell`：JLine 4 的交互式 REPL；`Shell.builder()` 建立输入循环、提示符和命令分派器，`run()` 管理读取与执行。
- `Command`：一条可执行 Shell 命令，拥有名称、别名、描述、补全、`execute(CommandSession, String[])`。
- `CommandGroup`：命令组织与发现，可以查找命令，不必自己实现分派/执行逻辑。
- `CommandDispatcher`：命令分派、组合执行、补全与会话管理的协调点。
- `CommandSession`：Shell 的输入/输出/错误流、变量、工作目录、退出码、前台任务等；**不包含 AstaPS 玩家 UID、游戏授权、在线状态等概念**。
- `jline-picocli` 中的 `PicocliCommandRegistry`：JLine 官方把 Picocli `CommandLine` 子命令映射为 `CommandGroup` / JLine 命令，提供补全、描述、执行（会直接调用 Picocli `execute`）。

**注意**：JLine 4 的 `CommandSession` 已经存在。不能在未经决策时再造一个同名概念，或自动新增 `CommandContext` / `CommandEngine`。哪些游戏专用信息放在何处应先确定。

JLine 4 `jline-shell` 提供的可选能力包括 `history`、`help`、`alias`、命令高亮、变量展开、脚本 `source`、`|` 管道、`&&` / `||` 条件执行、`;` 顺序执行、`>` / `>>` 重定向、`&` 后台任务，以及 `jobs` / `fg` / `bg`。**支持这些语法不等于 AstaPS 的任意 GM 命令已经具有正确的管道、退出码、取消和文件访问语义**。

资料：[Shell 入门](https://jline.org/docs/shell-getting-started/)、[Shell 功能](https://jline.org/docs/shell-features/)、[Shell 与 Picocli](https://jline.org/docs/shell-picocli/)、[Shell 迁移指南](https://jline.org/docs/shell-migration/)；[Shell.java](https://github.com/jline/jline3/blob/4.4.8/shell/src/main/java/org/jline/shell/Shell.java)、[CommandSession.java](https://github.com/jline/jline3/blob/4.4.8/shell/src/main/java/org/jline/shell/CommandSession.java)、[PicocliCommandRegistry.java](https://github.com/jline/jline3/blob/4.4.8/picocli/src/main/java/org/jline/picocli/PicocliCommandRegistry.java)。

### 2.3 与 Picocli 的关系及限制

Picocli 强在 `@Command`、`@Parameters`、`@Option`、类型转换、子命令、帮助、`ParseResult`、`IExecutionStrategy`；它可以直接调用任意复杂的 Java 游戏业务。**解析库不会自动管理游戏世界权限、目标玩家或线程安全**。

- Shell `CommandDispatcher` 可以处理命令组织及 Shell 语法；Picocli 能解析具体 GM 参数。
- Picocli `IExecutionStrategy` 是**解析后、调用业务前**介入通用权限/目标检查的候选扩展点。
- 解析后权限检查还需满足：解析器/转换器不产生未授权副作用；失败时不执行命令；补全与帮助显示哪些无权命令另行确定。
- JLine 的 `PicocliCommandRegistry` 内部会为子命令设置输出流，并调用 `sub.execute(args)`；共享可变 `CommandLine` 被多线程或多会话使用时，输出流和解析字段可能相互干扰。AstaPS 目前为**每次真实调用创建 Picocli 命令模型**，不可未经研究就抹掉这种区别。
- 现有 `picocli-shell-jline3` 使用的是老 JLine 集成路径，需核对与 JLine 4 的编译/运行兼容性；不可只修改 Gradle 版本字符串后直接断言成功。

## 3. 研究过的三条技术路线（A / B / C）

| 路线 | 改造范围 | 收益 | 主要代价 |
| --- | --- | --- | --- |
| **A：终端升级** | JLine 4 输入、终端提供者、日志重绘，沿用主要 CommandMap/Picocli 分派 | 终端体验与平台支持可测量改进 | 旧 CommandMap 职责仍集中 |
| **B：官方 Picocli 集成** | A + `jline-picocli` / `PicocliCommandRegistry`，重审命令分派 | 利用现成命令元信息进行补全/帮助，减少重复维护 | 现有权限/目标/每次创建实例的语义必须重审 |
| **C：完整 JLine Shell** | B 的能力范围 + `jline-shell` 的 REPL、命令组合与可选扩展 | 脚本、管道、别名、条件执行、作业管理等长期扩展能力 | 完成/失败、输出流、并发、事务边界、脚本和文件权限均要定义 |

用户提出「完整 JLine Shell」作为方向，**不能据此推定 B 必须先改造、A 必须保留旧架构，或者一切 Shell 功能均默认开放**。A/B/C 是用于比较代价的路线，具体组合必须再作决策。

### 三种产品目标

1. **服务器管理控制台**：可靠输入、历史、补全、高亮、日志重绘、错误与命令结果。
2. **专业交互式 Shell**：一的体验 + 脚本、管道、别名、变量、条件执行、作业管理。
3. **服务器管理平台**：多管理员会话、远程管理接口、任务查询、跨客户端输出、身份授权、审计与插件扩展。

目前用户倾向深入研究 2，也可将 3 视为未来方向；**不存在已批准的完整管理平台开发任务**。

## 4. Brigadier 与其他方案

### 4.1 Brigadier

[Mojang/brigadier](https://github.com/Mojang/brigadier) 是 Minecraft Java 版使用的命令树框架。`literal` 节点代表固定命令字，`argument` 节点代表参数；`requires` 可设置节点访问条件，`executes` 定义执行。`parse(input, source)` 与 `execute(parseResult)` 可分开调用，解析和补全对执行者上下文敏感。

与 Picocli 的重点差异：

- **Brigadier**：围绕节点、上下文敏感语法、Minecraft 游戏内补全和命令树同步设计；节点条件会参与解析/可见性。
- **Picocli**：围绕 Java 命令对象、位置参数、选项（如 `--amount`）、类型转换、帮助生成及执行策略设计。
- 采用 Brigadier 不会自动解决 AstaPS 的游戏线程、输出或权限业务；与 JLine Shell 的结合也需要研究。**目前 Brigadier 是参考对象，没有决定引入或取代 Picocli。**

### 4.2 JLine 的竞争及优秀实践

- **Æsh**：Java 交互式命令行和 Readline/命令框架候选；功能、终端输入和执行模型可与 JLine 对比，尚无 AstaPS 实测。
- **Lanterna**：Java 文字图形界面（TUI）库，适合全屏终端面板。与 JLine 的行编辑用途不同。
- **Java Console / GNU Readline**：分别是简化 JDK 控制台 API 与原生行编辑库；涉及功能覆盖和 Java 跨平台接入取舍。
- **JShell（OpenJDK）**：上下文感知补全、历史、交互帮助。
- **Apache Karaf**：服务器命令组织、SSH 管理与会话经验。
- **Paper / Minecrell TerminalConsoleAppender**：日志与可编辑输入行共存、终端能力探测和无 TTY 环境处理。
- **Spring Shell 4**：Shell 框架与终端实现分离、多种运行模式的参考。
- **JLine** 本身是 Java 终端/交互式 Shell 基础设施，既不等同于 Windows Terminal，也不等同于 PowerShell。Picocli 是其可组合的命令解析工具。

资料：[OpenJDK JShell](https://github.com/openjdk/jdk)、[Apache Karaf](https://karaf.apache.org/manual/latest/remote)、[TerminalConsoleAppender](https://github.com/Minecrell/TerminalConsoleAppender)、[Spring Shell](https://github.com/spring-projects/spring-shell)。

## 5. 其他游戏服务端：实际执行路径

| 项目 | 已观察到的源码行为 | 可借鉴之处 |
| --- | --- | --- |
| **Minecraft / Paper** | `PaperConsole.runCommand` 将文本交给 `DedicatedServer.handleConsoleInput`；服务器用并发队列保存本地控制台命令，`handleConsoleInputs` 在服务器执行阶段取出，再调用命令系统。远程 RCON 通过 `executeBlocking` 调入服务器并等待反馈。 | 将控制台读取线程与游戏执行线程解耦；命令源携带反馈路径。 |
| **Minestom** | `CommandManager.execute(sender, text)` 先 `parseCommand`，再 `parsed.executable().execute(sender)`，返回 `CommandResult`。部分条件在解析后、执行前检查。 | 解析结果、执行者和执行状态明确；调用方仍负责线程安排。 |
| **Terraria / TShock** | `HandleCommand(TSPlayer, text)` 分词、查找命令、检查 `CanRun` 与 `AllowServer`，再运行处理函数；REST `rawcmd` 根据令牌创建 `TSRestPlayer`，调用同一处理入口并收集其输出。 | 远程请求具备自己的执行身份和输出收集对象；某些敏感命令可选择不记录参数。 |
| **SourceMod（Source 游戏生态）** | 提供控制台/管理员/服务器命令注册、命令缓冲区、`ReplyToCommand` 一类接口。 | 按游戏循环处理命令及按来源反馈，可作为概念参考；具体线程与时序仍需目标版本源码核验。 |
| **Grasscutter / LunaGC** | 继承集中式 `CommandMap`、控制台目标 ID 和命令注册模型，AstaPS 又添加了 Picocli 迁移。 | 便于比较游戏行为与继承而来的职责耦合，不足以证明应继续保持相同架构。 |

参考源码：[PaperConsole.java](https://github.com/PaperMC/Paper/blob/main/paper-server/src/main/java/com/destroystokyo/paper/console/PaperConsole.java)、[Paper DedicatedServer 补丁](https://github.com/PaperMC/Paper/blob/main/paper-server/patches/sources/net/minecraft/server/dedicated/DedicatedServer.java.patch)、[Minestom CommandManager](https://github.com/Minestom/Minestom/blob/master/src/main/java/net/minestom/server/command/CommandManager.java)、[TShock Commands](https://github.com/Pryaxis/TShock/blob/general-devel/TShockAPI/Commands.cs)、[TShock REST](https://github.com/Pryaxis/TShock/blob/general-devel/TShockAPI/Rest/RestManager.cs)、[Grasscutter](https://github.com/Grasscutters/Grasscutter)、[LunaGC 7.1 存档](https://github.com/c976/LunaGC-Archive/tree/LunaGC-7.1.0-capyb2222)。

**调研归纳**：命令解析、授权、游戏线程调度、输出归属、命令完成状态，是需要分别定义的五件事。同类项目经常使不同入口共享命令执行器，并明确控制台/RCON/REST 的身份与反馈来源。没有一个框架可以自动替 AstaPS 规定全部游戏业务语义。

## 6. 有代表性的设计分歧与风险

### 6.1 解析与权限

**用户已表达的倾向**：GM 命令先完成参数解析，再在业务执行前检查权限。可参考 Minestom 的执行条件模式，Picocli `IExecutionStrategy` 是一个候选位置。

仍要决定：
- 权限按**根命令**、**子命令**、**操作类型**、**目标玩家**还是组合判定？当前部分 GM 子操作存在额外权限逻辑。
- 无权限命令是否出现在 Tab 补全、`help`、客户端命令列表？Brigadier 的 `requires` 会影响解析/可见性，不能直接等同于 Picocli 的解析后授权。
- 解析时执行的转换器可能查数据库或访问状态；怎样保证**未获授权的命令不会在解析时写入或改变游戏状态**？
- Shell 内置的 `source`、`alias`、`history`、`>`、`&` 等采用何种权限？不能直接假定它们自动经过 GM 的 Picocli 执行策略。

### 6.2 目标玩家与执行者

当前 `target` / `@UID` 属于全局预解析，并存储「当前选中玩家」；Web 和本地控制台共享 `CONSOLE_ID`。需要确定：
- 目标语法继续使用 `@UID`、改为明确选项，还是直接用 Picocli 的类型转换能力？是否允许单独 `target` 作为 Shell 命令？
- 输入命令的**执行者**、命令作用的**目标玩家**、接收结果的**会话**是否必须分别可识别？
- 「记住上一次选择目标」是否属于 Shell `CommandSession` 的状态？多个终端/网页/游戏玩家如何隔离？
- 允许目标离线、目标掉线、排队执行后目标变更时怎样判定？

### 6.3 线程与 Shell 作业

JLine 的 `&` / `jobs` 表示 Shell 作业；AstaPS 的 `@Command(threading=true)` 只表示用服务器线程池异步执行；游戏世界是否需要专用线程、怎样安排安全访问是第三个问题。三者不宜混为一谈。

示例（**尚未支持**）：

~~~text
give 1001 --amount 100 && teleport scene 3
player list | grep rino
long-operation &
~~~

需分别定义：
- `&&` 所谓「成功」是指已入队、已完成业务、已持久化，还是客户端已收到通知？
- Shell 的退出码、Picocli 的返回值与游戏执行结果怎样对应？
- `|` 传递什么数据：格式化日志、文本输出、结构化对象、玩家聊天消息？
- 后台任务如何取消？取消时已发生的游戏世界修改是否回滚？脚本中途失败是否继续？
- 共享可变 Picocli 命令实例、IO 流和会话变量是否存在竞态？多条游戏命令能否并行安全执行？

### 6.4 输入、日志、安全与部署

- Windows Terminal / PowerShell 7、中文 IME、UTF-8、Tab、历史搜索、Ctrl+C/Ctrl+D、粘贴多行、日志连续刷新，需要真实终端测试；Issue #242 现象还不能归因于 JLine 3。
- 标准输入关闭、服务方式启动、stdout/stderr 重定向、Linux TTY、SSH、dumb terminal，需要明确支持范围。
- `source` 脚本与 `>` 文件写入涉及文件系统边界；历史文件、Web 命令请求、Logger 与插件事件可能暴露令牌或密码。
- CLI 生命周期应与关闭服务器、停止线程池、关闭终端及日志 appender 的顺序一致；不能仅在演示程序里测试 `Shell.run()` 就认定已适用于生产服务器。
- `StartupArguments` 是否纳入本轮重构仍未决定；它当前是独立手写解析器。

## 7. 设计决策清单（未授权就保持未决）

| 编号 | 决策问题 | 当前状态 |
| --- | --- | --- |
| D01 | 产品目标：只做交互 Shell，还是也建设完整多客户端管理平台？ | 长期方向已讨论；实施边界未定 |
| D02 | 使用 JLine 4 `Command` 原生模型，还是 `jline-picocli` / Picocli 定义 GM 命令？ | 未定；已详细比较 |
| D03 | 是否完整启用 `jline-shell` 的别名、变量、脚本、管道、重定向、作业控制？ | 方向为完整 Shell；逐项启用未定 |
| D04 | GM 解析完成后在何处验证权限？ | **倾向先解析后授权**；具体扩展点未定 |
| D05 | 无权限命令在帮助和补全中是否可见？ | 未定 |
| D06 | `target`、`@UID` 与位置参数的最终语法？ | 未定 |
| D07 | 本地、Web、游戏玩家之间如何定义执行者、会话、反馈归属？ | 未定 |
| D08 | 游戏世界修改命令要不要进入统一游戏线程？ | 未定 |
| D09 | 同步、异步、Shell 后台作业的完成/失败/取消语义？ | 未定 |
| D10 | 命令输出是文本流、结构化数据还是两者都有？ | 未定 |
| D11 | Shell 内置命令与文件系统/脚本功能的授权边界？ | 未定 |
| D12 | 插件命令 API 是否破坏性升级、哪些旧接口直接删除？ | 未定；**不得自行新增兼容层** |
| D13 | 启动参数 `StartupArguments` 是否迁移到 Picocli？ | 未定 |
| D14 | JLine 4 使用模块依赖还是 Java 21 `jdk11` 整合包？ | 未定；当前 Java 21 不变 |
| D15 | Java 25 / FFM 迁移何时进行？ | **未来方向，当前不迁移** |
| D16 | CI、集成测试和 Windows 真机验收标准？ | 需要正式确定 |

**先查清源码/API → 提供备选方案与影响 → 用户选择 → 才修改。** 不自动创建新分支，不自动加抽象、不自动迁就未批准的旧行为。

## 8. 后续研究与验证次序（候选，尚无实施授权）

1. 读 JLine 4.4.8 的 `Shell`、`Command`、`CommandGroup`、`CommandDispatcher`、`CommandSession` 及官方 `PicocliCommandRegistry` 原始实现，确认生命周期、退出码、异步/管道与输出流行为。
2. 用现有 AstaPS 命令按「来源 → 分词 → 解析 → 授权 → 目标 → 调度 → 反馈」列出覆盖矩阵，特别关注 `target`、`@UID`、权限不同的子操作、原有 `threading=true` 和 Web Console。
3. 逐一决策 D01–D16，记录用户明确选项；尽量直接使用 JLine/Picocli 原生能力，无批准不新增层。
4. 获准后在**既有** `rino/refactor/jline4-shell` 分支修改，先验证真实编译、单元测试和终端输入；再做游戏内/Web 和任务/管道集成验证。
5. 用户实机测试前和提交上游前运行完整相关 CI；未通过不得声称可测试或可提交。保留项目既定 `grasscutter-7.1.0.jar` 构建产物命名。

本文件是研究基线。所有示意命令只是讨论 Shell 语义，**不表示 AstaPS 已经支持示例功能**。


## 9. 2026-11-02 补充：用 JLine CommandSession 实现后台目标选择的源码核验

**本节是新增研究，未修改 AstaPS 的命令行为。** 根据 JLine 4.4.8 原始代码而非 API 印象，确认以下事实：

### 9.1 后台命令源、身份与 `@s`

Minecraft 的命令执行源有身份、执行实体与执行位置等信息；在游戏中玩家发出的 `@s` 可指向执行实体，原生后台控制台没有玩家执行实体，`@s` 不会凭空匹配到玩家。`@p` 需要执行位置。原版 Minecraft 的 `/give` 要求目标参数，不能因为玩家输入而任意省去玩家选择器。

AstaPS 现状：`CommandMap.selectedTargets` 按玩家 account ID / 固定 `CONSOLE_ID` 保存 `SelectedTarget(uid, username)`；`getTargetPlayer()` 先读内联 `@UID`，其次显式目标、记住的目标，最后用 `sender` 作为回退；`target @UID`、裸 `@UID` 已有记忆功能，`@s` **尚无 Minecraft 式语义**。本地和 Web 控制台目前共享 `CONSOLE_ID`。

用户提出将后台会话的默认目标存入 **JLine Shell 的上下文**。技术上可实现，例如：
```java
session.put("astaps.targetUid", 10001); // 设计示例，尚未提交代码
Object targetUid = session.get("astaps.targetUid");
```
`CommandSession.get/put/remove/variables` 为 JLine 4 原生接口。`DefaultCommandDispatcher` 创建持久 `CommandSession`，可通过其 `session()` 取得，且 JLine `Command.execute(CommandSession, String[])` 收到该会话。绑定目标、发起者权限身份、输出接收者应区分；绑定玩家**不意味着以其身份登录/授权**。提示符可以通过 `Shell.builder().prompt(Supplier<String>)` 读取会话状态。

注意：`ShellBuilder.variable(name,value)` 写到 **LineReader 的变量**，不会自动写到 `CommandSession`。这两个接口的存储用途不同。

### 9.2 会话状态不是自动隔离的安全属性

- `CommandSession` 的变量表也可由 JLine 内置 `set`、`unset`、`export` 命令修改（启用 `variableCommands(true)` 时）。`DefaultCommandDispatcher` 还会把独立一行的 `NAME=VALUE` 写入会话变量。
- 因此 `session.put("astaps.targetUid", 10001)` **只是状态存储位置，不提供验证和访问控制**。若开放变量命令，用户可以把同名条目改为字符串。每次读取必须根据实际选择器/玩家状态验证，且不能靠变量值赋予 GM 权限。
- 管道组中的 `CommandSession.fork(in,out,err)` 会**浅拷贝**变量表和工作目录。因此在子会话内设置变量不自动写回主会话，管道中的目标切换语义需要明示；若保存可变对象，还可能跨会话共享引用。保存不可变 UID 值是值得考虑的方案，尚未批准。
- 单一 `DefaultCommandDispatcher` 仅构造一份根 `CommandSession`，不能假定 HTTP 管理员或游戏玩家使用它就天然获得互相隔离的会话。远程入口的会话生命周期待决定。

源码：[CommandSession.java](https://github.com/jline/jline3/blob/4.4.8/shell/src/main/java/org/jline/shell/CommandSession.java)、[ShellBuilder.java](https://github.com/jline/jline3/blob/4.4.8/shell/src/main/java/org/jline/shell/ShellBuilder.java)、[VariableCommands.java](https://github.com/jline/jline3/blob/4.4.8/shell/src/main/java/org/jline/shell/impl/VariableCommands.java)。

### 9.3 官方 PicocliCommandRegistry 的两项实际限制

核对 JLine 4.4.8 [PicocliCommandRegistry.java](https://github.com/jline/jline3/blob/4.4.8/picocli/src/main/java/org/jline/picocli/PicocliCommandRegistry.java) 的 `PicocliCommand.execute(session,args)`：

1. 它接收 `CommandSession`，但**不把会话对象传给 Picocli 命令**，而是直接 `sub.execute(args)`。原样套用无法让 GM 业务代码自动获得 `session.get("astaps.targetUid")`；要确定使用 JLine 原生 `Command` 接口、Picocli 执行策略或其他直接接入方式，不应擅自新增适配层。
2. 当 `session.terminal() != null` 时，它调用 `sub.setOut(new PrintWriter(terminal.output(), true))` / `setErr(terminal.output())`，**绕过 `session.out()/err()`**。因此不能未经改造就假定通过官方 Registry 执行的 Picocli 输出可被 `|` 管道或 `>` 重定向正确捕获。AstaPS 当前 `CommandOutput` 还把无玩家反馈写进日志而非 Shell 命令流。

### 9.4 条件执行码和异步语义的源码级风险

核对 [DefaultCommandDispatcher.java](https://github.com/jline/jline3/blob/4.4.8/shell/src/main/java/org/jline/shell/impl/DefaultCommandDispatcher.java)：
- `runCommandStage()` 调用 `stageCmd.execute(session,args)` 之后，对正常返回直接 `session.setLastExitCode(0)`；异常才设置 `1`。也就是说，若 Picocli 命令**正常返回一个非零整数退出码**，当前 Dispatcher 没有从这个结果计算非零 `lastExitCode`。因此 `cmd1 && cmd2` 可能把 Picocli 的参数/业务失败误当成功；是 JLine 4.4.8 的具体源码行为，需设计验证或上游修复路径，不得宣称已经正确支持。
- `executeBackground()` 新建守护线程，在同一 Dispatcher 下调用 `executePipelineStages`；这不是 AstaPS 的游戏世界安全执行线程。
- 多阶段管道将各阶段放在 `session.fork(...) ` 子会话；生产者使用单独线程。生产者异常可被忽略，最终状态依赖后续阶段行为。
- `Shell.run()` 对 Ctrl+C 默认清行继续，Ctrl+D 退出 REPL；AstaPS 现在 Ctrl+C 连按两次退出进程。生命周期行为需要用户决策。

**直接结论**：JLine 4 Shell 的完整能力是既定接入目标，但「依赖已引入」与「GM 命令具备正确的权限、目标、管道、完成状态」必须分别验收。

### 9.5 接下来需优先裁决的问题（新增 D17–D20）

| 编号 | 待裁决问题 | 状态 |
| --- | --- | --- |
| D17 | `@s` 在本地/远程 Shell 表示已绑定玩家，还是 Shell 自身执行主体？`target` 与内联 `@UID` 如何统一？ | 已提出会话绑定方向，最终语义未批准 |
| D18 | 当前目标使用 `CommandSession` 公共变量保存时，是否允许 `set` / `unset` 修改它？变量校验和可见性怎样处理？ | 未定 |
| D19 | 用 JLine 原生 `Command.execute(session,args)` 直接获得会话，还是通过 Picocli 集成的其他调用方式？怎样保留完整参数解析且避免额外层？ | 未定 |
| D20 | `PicocliCommandRegistry` 的终端输出绕流和 Dispatcher 的返回码逻辑怎样处理，才能真正保证 `|`、`>`、`&&` 的含义？ | 已发现代码级障碍；未定 |

## 10. 2026-11-02 已确定：`give` 命令语法

用户明确指定今后的命令形式：

```text
give [选择器] <item> [数量]
```

**已确定的规则**：
- 目标选择器放在物品参数之前，可以省略。
- `<item>` 是必填项。
- 数量为可选的位置参数，默认 **1**。
- 省略选择器时，**从执行命令的环境读取默认目标**。游戏聊天来源可提供发令玩家；JLine Shell 来源可读取对应 `CommandSession` 的当前绑定玩家。具体优先级与环境边界仍须按下文确定。
- 这些规则描述**目标语法**；命令执行者的权限身份由实际来源决定，目标绑定本身不能赋予权限。

期望输入示例（**设计目标，尚未实现**）：

```text
give 1001                  # item=1001, count=1, target=当前环境目标
give 1001 50               # item=1001, count=50, target=当前环境目标
give @10002 1001            # target=10002, item=1001, count=1
give @10002 1001 50         # target=10002, item=1001, count=50
give @s 1001               # @s 的具体解析需结合会话绑定规则核验
```

**解析的实际约束**：由于选择器与物品都是可变参数，解析器需要能识别可选的首项是否为选择器。现有 AstaPS 的 `@UID` 记法可作可识别的前缀；物品 ID 仍可为纯数字。不能自动假定 Picocli 连续声明三个位置参数即可准确消歧。尤其 `@s`、`@p`、玩家名、多个目标、引号中的物品名称与合法数量范围等规则仍需后续明确。

**尚未决定的边界**：
- Shell 未绑定默认玩家时，省略选择器如何报错或使用其他环境规则；
- `@s` 在游戏与 Shell 中的准确指代、`@p` 是否实现，以及多目标选择器如何操作；
- 是否让普通 Shell 变量命令修改当前绑定的默认目标；
- 旧 `--amount` / 尾置 `@UID` 是否直接移除（用户不要求兼容旧语法，不自行增加兼容）；
- 如何将解析后授权、会话隔离、命令执行完成状态结合到实际实现。

以上命令语法属于**用户已决定的需求**，后续架构比较不得再将 Minecraft 前置必选目标或 Picocli `--target` 当成待选主语法。本次仅更新文档，无代码变更。

## 11. 2026-11-02 选择器决议（用户确认）

以下条目覆盖先前研究中与之不符的候选描述；这里是**当前明确需求**，不是待选项。

- **S01–S04：`@s`、`@p`、`@r`、`@a` 均暂不支持。** 不实现「自己／最近／随机／全部玩家」特殊选择器；此前的 `give @s 1001` 仅为研究示例，当前不得作为有效语法或测试预期。
- **S05 = B：仅支持 `@UID` 与 `username@` 这两种显式目标写法。** 用户尚未授权增加 `@username`、`username@UID` 或其他写法；现有实现中已存在的额外语法应在实施时按本决议审视，不为其添加兼容层。
- **S06 = A：账号用户名唯一。** 对明确账号标识进行唯一匹配；不要把“多个账号同名”作为需要支持的情况。游戏显示昵称的重名问题与账号用户名不同，本次没有要求通过昵称解析。
- 继续遵守已确定的 `give [选择器] <item> [数量]`、默认数量 `1`、省略选择器从当前执行环境读取目标的规则。
- 未选择的 E/G/X 等选项继续保持未定；不能从这次决议中推断它们的答案。

```text
give 1001               # 读取环境默认目标，数量 1
give @10001 1001 5      # 指定 UID
give rino@ 1001 5       # 指定唯一账号用户名
```

**语法注意**：`@` 单独出现、用户名是否允许特殊字符、账号名验证细节需以实际持久化与解析源码为依据确认；`username@` 表示账号用户名。全部例子均是目标语法，未表示已经在本分支实现。

## 12. 2026-11-02 已确定：`target` 环境作用域（T01–T03）

用户确认以下三项决定；未选细节保持未定，尚未授权编写实现代码。

| 决策 | 用户选择 | 含义 |
| --- | --- | --- |
| **T01A** | 显式目标仅对当前命令生效 | `give @10002 1001 50` 不修改会话中由 `target` 设置的默认目标；下一条省略目标的命令继续读取原默认目标 |
| **T02A** | 每个 Shell / Web 会话独立 | 每个管理端命令会话维护自身默认目标；本地 Shell 和不同 Web 会话不得共享 `CommandMap.CONSOLE_ID` 的全局目标状态 |
| **T03B** | 支持面向一批命令的临时目标作用域 | 未来提供一次性建立目标环境、执行一批命令、结束后恢复原目标的能力；**命令名、语法、作用域嵌套规则及实现机制未定** |

期望行为示例（目标语法示意，**并非已实现**）：

```text
# 管理会话 A
target @10001
give @10002 1001 50  # 明确指定 10002，只对本条生效
give 1002 10         # 仍指向会话 A 默认目标 10001

# 管理会话 B（独立）
target username@
give 1003 1          # 由会话 B 的环境选择，互不干扰
```

**实现研究约束**：JLine 4 `CommandSession` 可持有会话变量，`fork` 会复制变量；因此 T03B 所需的「临时绑定并恢复」必须继续核对脚本、管道和后台任务的真实生命周期，不能预设会自动还原。T02A 对 Web Console 意味着需要真实的远程会话隔离，当前 `CommandMap.invoke(null,null,...)` 共享 `CONSOLE_ID` 的实现不满足要求。管理会话独立不等于切换命令权限身份。

其他决策，例如 E01（游戏内省略选择器）、E02–E04、E07–E09、G01–G08、X01–X02、临时作用域命令的具体语法等继续保持未定。此提交只记载决定，不修改代码，也不运行 CI。

## 13. 2026-11-02 已确认：无参数 `target` 恢复命令执行者

用户明确指出：**`target` 不带参数时，默认目标应回到实际命令执行者（executor/sender）**。

- 游戏内命令：执行者是登录玩家。无参数 `target` 清除覆盖目标，后续省略选择器的命令默认对该执行玩家生效。
- 本地或 Web 管理终端：执行者是有完整管理权限的**服务器控制台主体**，没有关联的游戏 `Player`；清除覆盖目标后，游戏目标为空。需要玩家目标的命令必须明确指定 `@UID`、`username@` 或再次 `target` 绑定。
- `target` 只控制默认**目标**，不会改变实际执行者与其权限。此前的 T01A、T02A、T03B 不变；一条命令中显式选择器仍不影响后续默认目标。
- 该决议可直接采用「删除会话目标覆盖值，解析时自然回退到执行者」的语义，不需要把执行者玩家复制保存到变量中；具体持久状态机制随 JLine 4 接入决定。

现有 `CommandMap.setPlayerTarget(..., "")` 会移除 `selectedTargets` 中的覆盖值，`getTargetPlayer(...)` 在无覆盖时返回 `player`；控制台 `player == null`，因此其默认目标自然为空。这已经基本符合决议，但目前控制台与 Web 共用 `CONSOLE_ID`，迁移到独立 JLine/Web 会话后应确保相同效果。

```text
# 游戏内玩家 UID 10001
target @10002
give 1001             # 目标为 10002
target                # 恢复默认执行者 10001
give 1001             # 目标为 10001

# 后台 Shell（无游戏 Player）
target @10002
give 1001             # 目标为 10002
target                # 清除目标覆盖，回到后台执行主体
give 1001             # 没有玩家目标，报告缺少目标
```

注：这里的 executor 指**命令发起主体**，与 Java `Executor` 线程池执行器无关。既定语法 `give [选择器] <item> [数量]` 不变。

## 14. 2026-11-02 已确定：执行者、权限与生命周期（P01–P06）

用户一次性确认 **P01A P02A P03A P04A P05A P06A**；全部为已批准的设计决策。其余未选择细节继续保持未定。

| 决策 | 确定规则 |
| --- | --- |
| **P01A** | 继续以 `Player sender` 表示命令执行者，`sender == null` 表示受信任的服务器控制台主体；本轮不引入单独的执行者类型层 |
| **P02A** | 玩家使用 `target @UID` 或 `target username@` 绑定任意目标无需额外的 `target.others` 权限；实际业务命令执行时检查其权限 |
| **P03A** | 玩家操作其他玩家时必须同时拥有基本命令权限与对应 `.others` 权限（具体节点仍由命令声明，当前字段是 `permissionTargeted`） |
| **P04A** | 用稳定的玩家 **UID** 判断目标是否为执行者自身；不要依赖 `Player` Java 对象引用的同一性 |
| **P05A** | Web Console 经管理密码登录、会话验证通过后，作为拥有完整权限的管理控制台执行命令；不同 Web 会话的目标状态继续遵守 **T02A**，相互隔离 |
| **P06A** | 游戏聊天玩家的 `target` 默认绑定状态在玩家下线时清除，不跨登录，也不持久化至账号；不影响本地 Shell 与 Web 管理会话各自的目标状态 |

**执行顺序与身份边界**：
1. 从受信任的命令入口确定 `sender`：本地 Shell／通过认证的 Web Console 为 `null`，游戏聊天传入真实 `Player`。
2. 解析命令参数；解析显式选择器或根据已决定的目标覆盖／执行者回退规则计算目标。
3. `sender == null` 享有完整控制台权限；玩家命令检查账号基本权限，如目标 UID 与 sender UID 不同，再检查额外的 `.others` 权限。
4. `target` 改变目标环境，绝不改变 `sender` 身份或授权来源；临时目标作用域 **T03B** 同理。
5. 游戏玩家断开连接时删除该玩家会话的默认目标绑定；重新进入游戏时无历史覆盖值。

**现有源码差异／后续重构要求（尚未实施）**：
- `DefaultPermissionHandler.checkPermission` 当前使用 `player != targetPlayer` 区分自己与别人；应按 **P04A** 改为比较 UID，并遵守 **P03A** 的基本 + 额外权限。
- `CommandMap` 当前通过全局 `selectedTargets` 按账号 ID 记住玩家绑定，尚须核对下线清理路径以满足 **P06A**。
- `CommandMap.invoke(null,null,...)` 当前从 Web 与终端入口共同使用；按 **P01A/P05A** 保留内部约定，但只允许已验证的受信任入口构造控制台权限，不能让玩家请求伪造空 sender。
- `target` 现在绕过标准命令权限检查；**P02A** 明确允许仅绑定目标，真实业务操作的权限由后续命令检查。
- 保持已确认的 `give [选择器] <item> [数量]`、数量默认 1、仅 `@UID` 和 `username@`、T01A/T02A/T03B、无参数 `target` 恢复执行者等规则不变。

本次只记录决策；没有修改源代码，也没有运行 CI。

## 15. 2026-11-02 give 参数决定与库存发放链路审计（G01–G10）

用户最终确认：

| 决策 | 当前结论 |
| --- | --- |
| **G01B** | `<item>` 只支持数字 ID 与内部资源标识（如有效 `genshin.*` 标识）；不把本地化名称识别当成批准需求 |
| **G02A** | 如果参数含空格，须用引号包裹；在 G01B 范围内多数内部资源名无空格 |
| **G03A** | 数量仅十进制正整数，默认 1 |
| **G04C（覆盖先前 G04B）** | 超出允许存放数量时截断实际发放量；已明确策略方向，截断算法、剩余空间为零时的返回结果和 Shell 成功状态仍需细化 |
| **G05C，具体数值待研究** | 倾向按物品类型分别限制单次发放数量；用户明确要求进一步研究发放机制后再确定限制，不擅自填固定阈值 |
| **G06A（最后明确覆盖最初 G06C）** | 当前只给**在线**玩家发放，暂不做离线发放 |
| **G07A** | 删除旧的 `-n` / `--amount` 发放数量输入（目标语法的数量是可选位置参数） |
| **G08A** | 删除尾置玩家选择器语法，只使用此前确定的可选前置 `@UID` / `username@` |
| **G09C** | 删除 `all`、`weapons`、`mats` / `materials`、`avatars` / `characters` 等批量类别作为 `give` 的内置目标 |
| **G10B** | 旧武器、圣遗物、角色的等级／精炼／命座／词条等附加参数后续重新设计，暂不直接保留或删除 |

### 15.1 G04 的准确含义：堆叠上限与库存槽位上限

`ItemData.stackLimit` 来源包括 `MaterialExcelConfigData.json` 等物品资源；`MaterialInventoryTab` 以 **itemId 唯一映射**一个 `GameItem`，每种材料／摆设在一个槽位内存储累计数量。当前不会像 Minecraft 那样把同物品拆成多个独立堆叠：
- `Inventory.putItem()` 的材料分支先验证新增数量不超过 `data.getStackLimit()`，再计算 `existingCount + addedCount <= stackLimit`，否则返回 `STACK_LIMIT`，不会截断或拆包。
- `InventoryRewardAdmission.admissionFailure()` 在批量发放前做同样的总量预测；同时核对新增 ID 占用的槽位数。
- 武器和圣遗物为每件独立 `GameItem(count=1)`，受装备库存槽位容量限制；没有材料的“每个 ID 一组堆叠”语义。
- 虚拟货币／经验是玩家属性或即时作用，不占库存材料槽位；特定有界余额使用 `Integer.MAX_VALUE` 溢出检查，不能统一套材料的 `stackLimit`。
- `useOnGain`（角色卡、名片、皮肤等）会触发效果或解锁，通常不占背包；数量、重复解锁和效果成功与否需要单独约束。

### 15.2 当前物品发放实际执行路径

```text
GiveCommand.Args.run()
  ├─ 数字 ID / ResourceRegistry / GenshinResourceIds / NameIndex 解析
  ├─ genshin.recipe.*：直接解锁食谱
  ├─ genshin.avatar.*：直接创建或修改 Avatar
  ├─ ITEM_WEAPON / ITEM_RELIQUARY：
  │     makeUnstackableItems()/makeArtifacts() 逐件构造
  │     → Inventory.addItems(..., ActionReason.SubfieldDrop)
  └─ 其他物品：new GameItem(ItemData, amount)
        → Inventory.addItem(..., ActionReason.SubfieldDrop)
              → putItem()
                    ├─ useOnGain：useItemDirect / 皮肤解锁
                    ├─ ITEM_VIRTUAL：更改角色经验、货币、树脂等玩家状态
                    ├─ ITEM_WEAPON / ITEM_RELIQUARY / ITEM_TPS_WEAPON：按装备槽位
                    └─ ITEM_MATERIAL / ITEM_FURNITURE：同 ID 累计并校验 stackLimit
              → 记录获得历史 / Quest、Watcher、Battle Pass 事件
              → PlayerObtainItemEvent / 发背包变更与获得提示包
```

关键区别：`Inventory.addItems(...)` **默认 `ALL_OR_NOTHING`**，对多条物品记录先做入库预检；`Inventory.addItem(GameItem,...)` 通过单条直接插入路径，返回 boolean。当前 `GiveCommand` 对以上两种返回值均**未检查**，会在实际拒绝发放后依然输出“发放成功”，属于需要在改造时修正的已证实问题。

### 15.3 G05：分类限制应来自实际背包和效果语义，数值未决定

- `ConfigContainer.GameOptions.InventoryLimits` 默认 `weapons=2000`、`relics=2000`、`materials=2000`、`furniture=2000`，其中材料/摆设容量是**不同物品 ID 的槽位数**，武器/圣遗物容量是每件占一个槽位。均可配置；它们不是命令单次固定数量上限。
- 单次给 N 件装备会构造 N 个带属性的独立 `GameItem`；因此最大可接受数量首先受**当前装备空余槽位**限制。
- `InventoryGrantBuilder.create()` 对装备或 `useOnGain` 按条逐件展开，并有 **10,000 项批量构造硬上限**；但当前 `GiveCommand` 自行使用 `makeArtifacts` / `makeUnstackableItems`，**未走这个构造器**，不能错误宣称 `give` 已受到 10,000 的限制。
- 材料和家具上限应参考物品自身 `ItemData.stackLimit` 与当前数量。
- 有界虚拟货币及资源上限需考虑当前余额和 32 位整数容量；升级经验/树脂等效果还可能另有业务规则。额外的命令级数量限制尚未批准。
- `useOnGain` 单次解锁卡需根据其真实效果是否允许重复、效果执行次数以及当前解锁状态定义限制。
- `InventoryRewardAdmissionTest` 已覆盖累计堆叠溢出、装备槽位、虚拟货币溢出和单次直接解锁数量约束；`InventoryGrantBuilderTest` 覆盖装备逐件展开与 10,000 阈值。但未发现专门的 `GiveCommandTest`，实施时需要覆盖 GM 发放路径。

### 15.4 G06A：只允许在线玩家的理由与现有支持

- `GameServer.getPlayerByUid(uid, false)` 只返回在线玩家缓存，`true` 可退回数据库读取离线 Player。
- 当前 `CommandMap.findPlayer`／`getTargetPlayer` 允许查询离线 `Player`，目的是支持不同命令的不同目标要求；`GiveCommand` 的 `@Command` 未重写 `targetRequirement`，因此默认 `ONLINE`。
- `CommandMap.targetUsable(ONLINE,...)` 检查 `isOnline()`、有效 `getSession()` 和 `isActive()`，在指派任务前和真正执行时各检查一次，因此当前 `give` 路由**已设计为仅在线目标**。
- 真正库存入库会保存数据库项目、触发玩家的任务／活动／成就通知、插件事件、网络数据包和玩家世界相关副作用；离线直接修改数据需要单独研究状态一致性和效果补偿。当前不扩展离线发放。
- `target @UID` 可允许绑定离线玩家与否是 **target 命令本身的独立语义**；G06A 限定的是 `give` 业务执行目标必须在线。

### 15.5 已确认的隐藏风险及后续未定

1. 现有 `GiveCommand` 的武器和圣遗物批量路径没有检查 `InventoryAddResult.allAccepted()`，材料路径没有检查 `addItem()` boolean；存在失败却报告成功的情况，且状态可能已经变更或因直接效果执行。
2. 当前 `GiveCommand` 对普通 `useOnGain` 材料调用 `new GameItem(data, amount)`，而 `Inventory.putItem()` 对 `useOnGain` **只调用一次** `useItemDirect`，没有事先限制其 `count==1`；与 `InventoryRewardAdmission` 多条预检的 `count == 1` 要求不一致，需要按 G05 分类行为重新设计。
3. 现有 `genshin.avatar.*` 与 `genshin.recipe.*` 的直接操作路径有特殊数量处理；单纯 G01B 并没有决定这些类型的最终附加语义，G10B 保持后续设计。
4. G09C 删除批量类别时，**`genshin.artifact.<set>` 成套圣遗物资源标识是否保留**尚未得到单独裁决；不能擅自将其归入需删除或保留的范围。
5. 本节记录时曾决定 G04B（超过可存放上限拒绝），已由后续 G04C（截断）覆盖；G05 分类限额、重复解锁及具体输出格式仍需继续确定。

**截至此次研究：只批准 G05C 方向，没有批准任何新增阈值；G06 以最后的明确要求 G06A 为准。本次只更新研究文档，不修改代码、不创建分支、不运行 CI。**

## 16. 2026-11-02 G04 最终改为 C：超限截断（覆盖 G04B）

用户在 G01B G02A G03A G04B G05C G06A G07A G08A G09C G10B 决议后明确更改：**G04C**。因此第 15 节内关于「超过上限时拒绝」的描述仅代表当时已批准的旧决议或当前实现行为，不能作为新命令的目标语义。

### 16.1 源码已确认的机制与可计算空间

- **普通材料／家具**：`MaterialInventoryTab` 每种 ID 对应一个 `GameItem`；`ItemData.stackLimit` 是该 ID 的**总持有数量上限**，不是 Minecraft 式多个 64 件独立物品堆叠的上限。现有 `Inventory.putItem()` 遇超限会拒绝。对于新 G04C，可以在入库前计算 `remaining = max(0, stackLimit - currentCount)`，再以 `min(requested, remaining)` 发放。此公式为研究推导，尚须用户确认「截断」覆盖已持有数量。
- **武器／圣遗物**：每件一个装备槽位，可用空间 `max(0, tab.maxCapacity - tab.size)`；可发放件数受剩余槽位和后续批准的 G05 命令数量限制共同约束。需要在创建 `makeArtifacts()`／`makeUnstackableItems()` 的大量对象之前截断数量。
- **有界虚拟货币**：部分 `ITEM_VIRTUAL` 由玩家整型余额记录（如原石、摩拉），现有 `InventoryRewardAdmission` 和 `Inventory.putItem()` 对 `Integer.MAX_VALUE` 检查溢出。若按 G04C 截断，需要以当前余额推导可增加数量；树脂、经验等还可能有业务上限，不能一概套整型边界。
- **直接解锁／使用效果**：`useOnGain`、角色、食谱等通常需要单次解锁，可能已拥有；容量与“可重复的数量”不等同。其数量 0、1、重复指令、原有状态、`G10B` 附加参数的规则继续研究，不应机械地将可发数量设为堆叠空间。
- **G05C** 是物品类型的**单次命令数量上限**，具体阈值仍未批准。实际可发数量可能同时受 G04 的剩余空间、G05 的命令限额及其他业务限制约束；需要精确区分原因。
- **G06A** 继续要求 `give` 仅对在线且游戏会话有效的玩家操作。

### 16.2 与真正成功／失败状态的关联

现有 `GiveCommand` 忽略 `Inventory.addItems(...).allAccepted()` 和 `Inventory.addItem(...)` 的结果；超限拒绝可能仍打印成功。重构时必须核实实发数量再反馈。以下是提出 G04C 时留下的待议事项；**本节第 1、2、4 项涉及的 X01–X04 后续已于第 17 节决定**，其余细节仍未定：
1. 截断的具体计算范围，尤其可用数量为 0 时是以「无物品变更」返回失败，还是特殊反馈；
2. 部分发放时 Shell 的退出状态：成功／失败／独立的部分完成状态，以及 `&&` 是否继续；
3. 显示请求数量、实发数量、截断理由的最终输出格式；
4. `X01` 对异步发放完成的判定点、`X02` 解析错误与权限错误的显示策略。

### 16.3 Shell 4.4.8 关联的已验证障碍

`DefaultCommandDispatcher.runCommandStage()` 在 `stageCmd.execute()` 正常返回后设置 `lastExitCode=0`，不使用直接返回的非零数值；异常时才改为 1。因此设计 `&&` 时不能简单寄望 GM 命令返回 Picocli 非零整数就被 Shell 识别为失败。后台 `&` 另外使用线程执行，需要区分「提交成功」与「业务最终成功」。完整 JLine 4 Shell 目标与先前已确定的会话隔离继续有效。

本次更新仅调整研究文档，不改源代码、分支或 CI。

## 17. 2026-11-02 已确认：give 执行与结果语义（D01 / X01–X04）

用户明确确认：**D01B X01B X02B X03A X04B**。未选择的问题保持未定。本节覆盖第 16 节中关于 X01–X04 的待决描述；第 15 节 G04B 已被 G04C 覆盖。

| 决策 | 已批准的行为 |
| --- | --- |
| **D01B：直接同步** | `give` 在命令调用线程执行，完成业务处理后返回；重构时移除现有 `GiveCommand @Command(threading = true)` 对该命令的自动线程池投递。没有批准再包装“异步并等待”层。 |
| **X01B：实际发放结束才算完成** | 只有游戏物品／解锁业务操作执行结束，并取得其真实结果后，`give` 才对调用方报告完成；仅提交工作或解析成功不得算发放成功。**这里不自动升级为“所有数据库持久化任务完全落盘”承诺**（X01C 未选）。 |
| **X02B：权限错误优先** | 同时存在权限不足和参数错误时，先报告权限错误；命令入口应先做足以判定权限的检查，再提供参数解析错误信息。具体处理“目标选择器无效导致无法确定 .others 权限”的次序另行按实现条件研究，不能用伪造目标绕过授权。 |
| **X03A：截断后实发正数量视为成功** | 若按 G04C 计算后实际发放 `>0` 且这部分完整发放成功，命令返回成功，并明确报告请求数量和实际数量、以及发生截断。实际入库失败仍应按失败处理。 |
| **X04B：可发数量为 0 视为成功** | 若 G04C 截断后允许发放量为 `0`，命令视为成功，明确反馈“无物品变更”及原因；不能虚报为已发放。仅适用于**合法请求经容量截断为零**，无效物品、无权限、目标离线、执行异常不因此被视为成功。 |

### 17.1 语义样例（行为已定，文本格式未定）

```text
# 假设 item=1001 的总持有上限是 9999，目标已有 9000
give 1001 2000     # 请求 2000，实发 999，状态：成功（X03A）
give 1001 2000     # 已满，实发 0，状态：成功（X04B）
# 若无权限或目标离线：失败，与“已满截断到零”区别对待
```

G04C 对材料/家具可按剩余堆叠数量计算，对武器/圣遗物可按剩余槽位计算；虚拟货币、即时生效解锁和 G05C 的分类限额仍需逐类分析。**G05C 具体上限数值尚未批准**，也未明确“超过 G05 命令限额”是否按 G04C 截断。

### 17.2 已核实的代码差异及留待实施的问题

- `GiveCommand.java` 目前标记 `threading = true`，`CommandMap.executeCommand(...)` 会将其提交至 `Grasscutter.getThreadPool()`；D01B 实施后需改为同步执行。
- 直接同步意味着**调用线程**承担库存、数据库调用、插件事件和通知的工作。游戏聊天消息线程／Web Console HTTP 处理线程有卡顿风险；这是 D01B 已知代价，**不得未经批准替换为异步执行方案**。实现前仍需确认游戏状态线程约束。
- `GiveCommand` 当前未核对 `Inventory.addItem(...)` boolean 或 `Inventory.addItems(...).allAccepted()` 返回值，存在发放被拒仍报告成功的问题；X01B/X03A/X04B 需要使用真实实际发放结果。
- JLine 4.4.8 的 `DefaultCommandDispatcher.runCommandStage()` 在命令正常返回时将退出码设为 0，不会自动采纳 Picocli 返回的非零整数；Shell 脚本的成功/失败状态与 `&&` 仍需明确集成方式。此次未批准额外兼容层。
- 权限前置（X02B）与目标解析（决定 `.others`）之间的精确错误优先级，及部分发放过程中实际执行失败后的返回码，仍须后续设计。
- T01A/T02A/T03B、P01A–P06A、G01B/G02A/G03A/G04C/G05C/G06A/G07A/G08A/G09C/G10B 继续有效。

本次仅更新研究文档，不修改项目代码、不创建新分支、不运行 CI。

## 18. 2026-11-02 用户重申：截断是正常结果

用户明确补充：**「触发截断不算错」**。这是 G04C / X03A / X04B 的统一解释，应覆盖先前文档中把容量截断描述为异常或失败的研究候选文本。

- 合法的发放请求，经 G04C 的容量计算截断后，**实发正数或实发 0 均视为成功**，应反馈真实实发数量；截断本身不抛异常、不作为权限或参数错误、不应将 Shell 退出状态标为失败。
- 真正的失败仍包括：权限不足、非法参数、无效物品、G06A 目标不在线、计算出的合法数量入库时实际失败或发生执行异常。不能为了 X04B 将这些条件包装成“容量已满的成功空操作”。
- **G05C 的命令级额外限制是否也执行截断** 尚未单独决定；不能自动把“容量截断成功”推广为任何任意输入都成功。
- 普通材料/家具：资源给出的 `ItemData.stackLimit` 减去已有数量；装备：背包剩余槽位；有界虚拟余额：剩余可表示空间；立即生效奖励和一次性解锁必须依其游戏规则单独评估。
- `give` 的同步执行 D01B、实际完成确认 X01B、权限错误优先 X02B 与上述成功语义并行生效。

接下来只研究与选择 G05C 的各类命令级额外限制及 G10B 附加参数。不修改代码或引入自行定义的阈值。

## 19. 2026-11-02 G05 分类发放限额最终选择（L01–L05）

用户明确选择：**L01A L02A L03A L04A L05A**；尚未决定的其他事项继续保持未定。本次仅记录设计。

| 决策 | 确定语义 |
| --- | --- |
| **L01A** | 材料、家具仅依物品原有 `ItemData.stackLimit`、已有数量及背包槽位容量约束；不增加固定或配置式的单次命令数量上限 |
| **L02A** | 武器、圣遗物以相应背包当前剩余槽位约束可发件数；不额外规定固定或可配置的单次 `give` 件数上限 |
| **L03A** | 虚拟货币依据当前余额和原有数值/业务容量计算可增加数量；不额外规定固定或可配置的单次命令上限 |
| **L04A** | 经验、树脂等立即生效类物品遵循对应游戏系统的业务规则，不设置统一的额外 `give` 数量上限；具体每类业务边界需要按源码核验 |
| **L05A** | 若今后存在/批准单次命令限额，达到该限额时采用截断并正常成功的语义，不因截断报错；目前 L01–L04 均未授权新增单次命令上限，故此规则只是条件性约定 |

与现有 **G04C、X03A、X04B** 的关系：
- 对有效物品及在线目标，按照对应物品业务约束计算实际可发数量。可发量少于请求量，包含 0，都属于正常截断、成功反馈；必须说明真实发放数量，不应伪报完成原请求数量。
- 非法参数、无法识别的物品、权限不足、目标离线、实际发放时发生的错误等仍是失败；“截断不算错”不覆盖这些错误。
- `give` 继续采用 **D01B** 同步执行与 **X01B** 实际执行结束后完成；**X02B** 权限错误优先。
- L01–L04 约束的是**本来可发放的物品/效果**。`useOnGain`、角色、食谱等一次性解锁或更新行为仍需结合 G10B 设计具体规则；重复解锁、不能产生效果的合法请求如何反馈，尚未单独决定。
- 源码现状：`InventoryGrantBuilder` 对逐件创建物品内部有 10,000 件硬上限，但 `GiveCommand` 的武器/圣遗物路径使用独立构造代码。此实现限制不因 L02A 变成新增的 `give` 产品级限额，也不能凭本决议自动删除其他系统的既有限制。

**未决定**：G10B 物品特性选项语法、部分物品效果的细分边界、JLine Shell 退出码集成、T03B 临时目标作用域的具体语法等。**没有修改源代码、增加新分支或运行 CI**。

## 20. 2026-11-02 G10 基础属性设计：仅确认 A01A、A02A

用户确认 **A01A A02A**；其他 A03–A10 均未选择，继续保持未定。此节只记录语法与参数合法性，不推定装备、圣遗物或角色的默认属性。

| 决策 | 确定规则 |
| --- | --- |
| **A01A** | `give` 的附加属性采用**命名选项**形式，例如 `--level 80`、`--refinement 5`；选项具体名称、缩写、属性适用范围仍待逐项设计 |
| **A02A** | 显式指定的属性值**超出合法范围时拒绝命令**，按参数错误处理；不得悄悄钳制到上限或下限 |

**重要边界**：A02A 适用于显式属性参数（如等级、精炼、命座、词条参数），而此前已确认的 **G04C / X03A / X04B** 仍适用于有效物品发放数量超出库存容量的**正常截断**，包含截断到 0 仍成功。**G03A** 数量要求十进制正整数；非法数量不因 G04C 而变成合法。

**已有实现差异（待正式重构时再处理）**：现有 `GiveCommand` 的 `refinement = Math.max(1, Math.min(5, refinement))`、`level == null ? DEFAULT_LEVEL : Math.max(1, Math.min(MAX_LEVEL, level))` 会静默调整显式输入；实施 A02A 时需改成明确报参数错误。代码尚未修改。

A03–A10（武器/圣遗物/角色初始等级、精炼、命座、天赋、已拥有角色处理等）均保持未定。D01B、X01B、X02B、G05 L01A–L05A 和其他已批准决议不变。本次**仅更新研究文档**，不改代码、不新建分支、不运行 CI。
