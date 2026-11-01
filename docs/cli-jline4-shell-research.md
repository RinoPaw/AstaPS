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
