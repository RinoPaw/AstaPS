# 从客户端逻辑恢复未知 CmdId：7.1 出生协议案例与通用方法

这份文档记录一次完整的 7.1 客户端协议恢复过程，以及其中可复用到其他未知 CmdId 的方法。目标不是保存一串最终数字，而是保存一套以后还能重复使用的思路：当本地 proto、旧版本映射和同类项目都不完整时，如何从客户端实际行为、状态机、UI 生命周期和网络 handler 反推出消息类型、CmdId、protobuf 字段和正确时序。

## 1. 证据等级

以后恢复任何未知协议时，建议把结论按下面的等级管理，避免把“看起来像”过早写成事实。

1. **Runtime-confirmed**：服务端发送/接收后，7.1 客户端产生与目标协议完全一致的可见行为。优先级最高。
2. **Client-static-confirmed**：从 7.1 原始 `GenshinImpact.exe` / `global-metadata.dat` 中直接确认，例如 GetCmdId 返回值、protobuf parser 的 tag、handler 调用链。
3. **Cross-project-supported**：Starlight、Grasscutter、LunaGC 等项目的逻辑与静态结果一致，可用于确认整体时序和语义。
4. **Historical clue**：旧版本 CmdId、旧 proto 字段、旧逆向结果。只能缩小搜索空间。
5. **Local generated proto**：当前仓库生成代码里的字段号或消息结构。除非已经被 7.1 客户端验证，否则只能当线索。

这次最重要的教训之一，就是 `SetPlayerBornDataRsp.retcode = 6` 一度被当成强约束，后来客户端逻辑证明真正的 7.1 响应是 `retcode = field #7`。以后不要让本地生成 proto 反过来限制客户端逆向。

## 2. 7.1 出生协议目前已经确认的结果

当前确认结果：

| Message | 7.1 CmdId | Proto | 证据 |
| --- | ---: | --- | --- |
| `DoSetPlayerBornDataNotify` | `22899` | 空消息 | Runtime-confirmed + static |
| `SetPlayerBornDataReq` | `26105` | `avatar_id` + `nick_name` | Runtime-confirmed + static |
| `SetPlayerBornDataRsp` | `4385` | `int32 retcode = 7` | Runtime-confirmed + static |
| `PlayerNicknameNotify` | `3064` | `string nickname = 12` | Static-confirmed；单独发送不会完成出生 |
| `PlayerEnterSceneNotify` | `9582` | 现有实现可构造 | Runtime-confirmed；参与出生后的场景阶段 |

其中 `SetPlayerBornDataRsp=4385` 已经可以正式盖章。隔离实验中服务器只做：

```text
26105 SetPlayerBornDataReq
→ 持久化 Traveler / nickname
→ 发送空 payload 的 4385
→ STOP
```

客户端在收到 4385 后，命名确认阶段立即结束，开场动画继续播放。成功响应的 `retcode=0` 是 protobuf 默认值，所以空 payload 就是合法成功响应。

客户端静态结果也与运行时吻合：4385 对应消息类的 `GetCmdId()` 直接返回 `0x1121`；`0x1121 = 4385`。其 parser 唯一特判 tag `0x38`，即 `(7 << 3) | 0`，所以唯一字段是 `int32 field #7`。handler 把对象 `+0x18` 当 retcode 使用，成功值 0 进入出生 UI/状态推进逻辑。

错误码路径里还专门处理了 `132`。公开 Retcode 表里 `132 = RET_NICKNAME_WORD_ILLEGAL`，这与出生请求同时提交昵称的语义高度吻合。`RET_REPEAT_SET_PLAYER_BORN_DATA = 116`。

## 3. 可复现的客户端样本

本次静态分析使用 7.1 原始客户端：

```text
GenshinImpact.exe
SHA256 08a3086d5f3fe695f01dab61efa42e442006b18e5e475b2520df356f6a073b7d

global-metadata.dat
SHA256 05ae04d7a91b91cc880217a56b0b01f3e67f845b06e894216654ec5d160e0da0
```

PE ImageBase：

```text
0x140000000
```

一些已经确认的关键地址和类型：

```text
GHAHAPOPLIH                         出生命名页
  OnNotify      0x14BDEF5A0
  UpdateView    0x14BDEF640
  ClosePage     0x14BDEF7D0
  submit        0x14BDEE770

HJDNCHODGOL                         SetPlayerBornDataReq
  sender        0x14725CEF0
  CmdId         26105

ONKOPMILDMF                         DoSetPlayerBornDataNotify
  CmdId         22899

PlayerNicknameNotify handler        0x14C23BA20
SetPlayerNameRsp handler             0x14C2513A0

SetPlayerBornDataRsp GetCmdId        0x14A9E88B0
  return 0x1121                     = 4385
```

命名页提交函数 `0x14BDEE770` 会从页面对象读取：

```text
this + 0x244 → selected avatar id
输入框         → nickname
```

然后调用 `0x14725CEF0` 发送 `SetPlayerBornDataReq`。这个提交函数本身没有等待服务器响应后再继续的同步调用，因此不能简单从“send 后下一条调用”认定 UI 完成逻辑。

## 4. 为什么最终从逻辑层找到 4385

最早的思路是从本地 proto 形状筛选：假设 `SetPlayerBornDataRsp` 只有一个 `int32 retcode = field #6`，枚举客户端里符合这种 shape 的未知消息。这个方法没有找到可靠候选。

后来把方向改成了：

```text
用户行为
→ UI 页面
→ 页面生命周期
→ 状态机
→ 网络 handler
→ handler 参数消息类型
→ GetCmdId
→ parser 字段
```

这是这次最有效的方法。

### 4.1 先找真正的页面关闭入口

命名页 `GHAHAPOPLIH` 明确存在 `ClosePage @ 0x14BDEF7D0`。继续追调用者后发现，`PlayerNicknameNotify (3064)` 和 `SetPlayerNameRsp (20824)` 都会进入同一个 UI helper：

```text
JKKBGKIAFCO.LDJEAIKHCII @ 0x148F29920
```

这个 helper 会查看 `UIManager + 0x2B8` 的页面栈，只在栈顶确实是 `GHAHAPOPLIH` 且其他运行时状态满足时，才继续走 `ClosePage()`。

创建命名页时也确实把同一个 `GHAHAPOPLIH` 实例加入 `UIManager + 0x2B8`。所以 3064 的确参与命名页关闭，但它不是完整出生响应。

### 4.2 追出生 pending 状态

点击确认发送 26105 之前，客户端会操作全局 manager `AJHBBDMELNC` 的状态对象：

```text
state + 0x18 = 1
state + 0x1A = 当前选择相关 bool
```

其中 `+0x18` 很像 born-request pending。客户端还存在专门维护这两个 bool 的方法，因此这不是偶然字段写入。

继续追状态变化后发现，`PlayerEnterSceneNotify (9582)` 的 handler 会进入同一个 manager。逻辑大致为：

```text
if (state + 0x18 == 1) {
    state + 0x10 = Process(PlayerEnterSceneNotify);

    if (state + 0x19 == 0)
        return;

    state + 0x18 = 0;
    state + 0x19 = 0;
    ...
}
```

说明 9582 参与出生完成后的场景状态推进，但它还依赖另一个状态位 `+0x19`。因此“发 9582 就能完成出生”也不成立。

### 4.3 从 UI 生命周期函数反推网络分发 case

追 `AJHBBDMELNC.OMOMMIFNOFP` 的上游时，普通 direct-call xref 一开始没有结果。后来把 **tail jump** 也纳入 xref 搜索，找到：

```text
0x14C2505C9 → jmp 0x14EB34CD0
```

继续向上追分发比较链，最终得到：

```text
cmp eax, 0x1121
je ...
```

也就是 CmdId `4385`。

再回到对应消息类确认 `GetCmdId()` 和 protobuf parser，最终闭环。这个过程说明：

**只扫 `call rel32 (E8)` 会漏掉很多关键逻辑。还要检查 tail jump、虚调用、事件注册和生命周期桥。**

## 5. 失败实验同样要保留

这些实验排除了大量错误路径，以后遇到类似协议问题应继续采用“单变量、小探针”的测试方式。

### 5.1 4761

曾经把 4761 当作候选 BornRsp 测试。空 payload 发送后命名页不结束。后来也没有客户端逻辑证据支持它是 BornRsp。

结论：排除。

### 5.2 3064 单独发送

使用正确的 7.1 `PlayerNicknameNotify`：

```text
CmdId = 3064
nickname = field #12
protobuf tag = 0x62
```

客户端仍不结束整个出生流程。

结论：3064 是出生后的昵称同步之一，但不承担 SetPlayerBornDataRsp 的职责。

### 5.3 4761 + 3064

两包一起发仍不完成出生。

结论：进一步排除 4761。

### 5.4 `onLogin() + 3064`

后来通过日志确认 `player.onLogin()` 确实发送了：

```text
PlayerEnterSceneNotify (9582)
sceneLoadState NONE → LOADING
enterSceneToken 0 → 非零
```

然后再发送正确 3064，仍无法证明这就是正确出生时序。

这个实验的另一个重要结果是：**完整 `player.onLogin()` 不适合作为协议探针。** 它发送海量数据、启动世界/实体/Ability/Quest 等大量系统，极大增加噪声，也会引入并发和副作用。

### 5.5 隔离 4385

这是最关键的正向实验。

```text
26105
→ persist
→ 4385 empty success
→ STOP
```

客户端立即让命名后的开场动画继续。

结论：运行时确认 4385 就是 `SetPlayerBornDataRsp`。

## 6. 出生协议和“完整出生生命周期”要分开

找到 4385 后又出现一个很重要的认识：**“SetPlayerBornDataRsp 正确”不代表整个 PlayerBorn 流程已经完整。**

隔离 4385 实验中，客户端继续播放开场动画；到了哥哥说完“把我的妹妹还给我”之后，客户端黑屏，命名页 UI 又重新出现。再次点击确定会再发送 26105，但服务器已经持久化 Traveler，所以重复请求被拒绝。

这个现象说明：

```text
4385 已经结束了“选择角色/提交昵称”阶段
但出生后的 world / scene / quest 生命周期没有完整接上
```

这也是为什么以后排协议问题时，要把“某个 response 是否正确”和“完整游戏流程是否继续”拆成两个验证目标。

## 7. 同类项目给出的成熟 born 逻辑

### 7.1 Starlight

Starlight 的结构最值得复用。它显式维护：

```text
BornState.Pending
BornState.Complete
```

新玩家普通登录期间，`BornState == Pending` 时不会进入世界，也不会发送第一次 `PlayerEnterSceneNotify`。

收到 `SetPlayerBornDataReq` 后，它执行：

```text
InitializeTraveler
→ BornState = Complete
→ Initialize team
→ SetPlayerBornDataRsp
→ PlayerNicknameNotify
→ Emit LifecycleEvent.PlayerBorn
```

`PlayerBorn` 生命周期里的 SceneModule 再做：

```text
EnterOwnWorld()
→ EnterScene()
→ PlayerEnterSceneNotify
```

这比“直接把整个 onLogin 塞进 born handler”干净得多，也更符合我们从 7.1 客户端看到的状态机分层。

### 7.2 老 Grasscutter / LunaGC

老 Grasscutter 的实现更粗：

```text
创建 Traveler
→ player.onLogin()
→ SetPlayerBornDataRsp
```

LunaGC 7.1 基本继承这一逻辑，只把未知的 rsp CmdId 做成配置项。

这种实现过去能工作，但对 7.1 当前客户端来说，`player.onLogin()` 会一次触发过多系统，不适合作为最终设计参考。

### 7.3 AstaPS 当前逻辑

AstaPS 已经有 `QuestManager.onPlayerBorn()`，并把：

```text
FIRST_MAIN_QUEST = 351
```

定义为序章起点。其注释明确要求出生初始化在第一次 `onLogin()` 前执行。

当前 `test/born-starlight-order` 实验采用：

```text
4385
→ 3064
→ player.onLogin()
```

运行结果是：

- 4385 后动画继续；
- `player.onLogin()` 大约卡住 86 秒；
- 期间 `Defender_None_Born` 的 Ability action 抛异常；
- 最终发送 `PlayerEnterSceneNotify (9582)`；
- 客户端后来重新登录，服务器出现 duplicated login；
- 最终进入游戏，但没有观察到预期的第二段序章动画。

这轮没有先调用 `QuestManager.onPlayerBorn()`，因此 351 没有按 AstaPS 当前设计启动。**351 缺失很可能影响第二段序章表现，但尚未经过专门的运行时隔离验证，不能提前盖章。**

## 8. 推荐的最终出生架构

协议层已经基本恢复完成，后续实现应尽量向明确生命周期靠拢：

```text
PlayerLoginReq on fresh account
→ 初始化最基础 player/session 状态
→ DoSetPlayerBornDataNotify (22899)
→ PlayerLoginRsp
→ 保持 PICKING_CHARACTER / BornPending

SetPlayerBornDataReq (26105)
→ validate avatar + nickname
→ Initialize Traveler
→ initialize team / head / mainCharacter
→ persist BornComplete
→ QuestManager.onPlayerBorn()      // 351 等出生任务初始化
→ SetPlayerBornDataRsp (4385)
→ PlayerNicknameNotify (3064)
→ Player.onBorn()                  // 新的轻量出生生命周期
    → establish world
    → enter own world
    → first PlayerEnterSceneNotify
    → 只执行出生后必须的同步
```

最终应避免在 born handler 里直接调用完整 `player.onLogin()`。普通登录和第一次出生共享的初始化可以提取公共 helper，但两条生命周期应保持独立。

## 9. 恢复未知 CmdId 的通用流程

以后碰到 `UnlockTransPointRsp`、其他 `Rsp`、`Notify` 的 CmdId 缺失，推荐按下面顺序做。

### Step 1：先定义“客户端正确行为”

不要从数字开始。先问：收到正确消息后，客户端应该发生什么？

例如 UnlockTransPoint：

```text
点击锚点
→ Req 已知
→ 正确 Rsp 到达后
→ 地图点锁定状态刷新 / 交互结束 / UI 状态推进
```

有了可观察行为，才能从逻辑层找到 handler。

### Step 2：从 UI / 状态机找消费者

优先寻找：

- 页面 `ClosePage` / `UpdateView` / `OnNotify`
- 状态字段写入者
- 事件分发器
- manager 的成功回调
- request pending flag 的清理点
- 场景/地图数据更新入口

目标是先回答：**哪个客户端函数代表“这件事成功了”？**

### Step 3：反查 handler

从成功函数向上做 xref。必须覆盖：

- `call rel32`
- `jmp rel32` / tail jump
- 虚表调用
- delegate/event 注册
- 生命周期分发
- switch/case CmdId dispatcher

这次 4385 就是因为普通 call xref 不够，最终从 tail-jump 和 dispatcher case 找到。

### Step 4：从 handler 参数定位消息类

确认 handler 收到哪个 obfuscated message type，然后去找这个类型的：

```text
GetCmdId()
Parser / MergeFrom
Serialize
```

`GetCmdId()` 是最直接的 CmdId 证据。

### Step 5：从 parser 读 protobuf tag

不要先相信本地 proto。

protobuf tag：

```text
tag = field_number << 3 | wire_type
```

常见 wire type：

```text
0 = varint
1 = fixed64
2 = length-delimited
5 = fixed32
```

例如：

```text
0x38 = field #7, wire 0
0x62 = field #12, wire 2
```

### Step 6：先用最小成功包验证 CmdId

如果 response 只有 retcode 且成功值为 0，优先发空 payload：

```text
new BasePacket(candidateCmdId)
```

这样测试只验证 CmdId / handler，不让错误的字段号干扰实验。

这是恢复 4385 最有效的一个技巧。

### Step 7：运行时验证后再改正式 proto

顺序应该是：

```text
client-static candidate
→ isolated runtime probe
→ visible behavior matches
→ runtime-confirmed
→ update PacketOpcodes / proto / packet class
```

避免在确认前把候选写进正式协议表。

## 10. 如何套到 UnlockTransPointRsp

AstaPS 当前已知：

```text
UnlockTransPointReq = 9369
Req: scene_id = 12, point_id = 1
UnlockTransPointRsp = unknown
本地生成 proto: retcode = field #6
```

`retcode=6` 现在只能视作线索。

推荐具体流程：

1. 在 7.1 客户端定位 `UnlockTransPointReq=9369` 的 sender。
2. 从点击锚点/神像的交互逻辑向后追 pending 状态。
3. 找“解锁成功后 UI/地图点状态改变”的客户端函数。
4. 反查是谁触发该函数。
5. 如果是网络 handler，拿到其参数类型。
6. 找该类型的 `GetCmdId()`。
7. 读 parser，确认实际 retcode 字段。
8. 若成功响应只有 retcode，先发 candidate CmdId + empty payload。
9. 看客户端是否立即完成交互并刷新锁定状态。
10. 运行确认后再补 `PacketOpcodes.UnlockTransPointRsp`。

不要因为当前服务端能通过额外发送 `GetScenePointRsp` 达到视觉刷新，就停止追真实 Rsp。那种 workaround 可以保留为临时兼容方案，但它不能提供真实协议映射。

## 11. 实验和日志经验

### 11.1 一次只改一个变量

推荐：

```text
candidate rsp only
candidate notify only
rsp + notify
rsp + scene entry
```

不要一上来同时发送完整 `onLogin()` 的几十种包，否则无法判断是哪一包改变了客户端状态。

### 11.2 记录关键事件，不要人工截终端

PowerShell：

```powershell
java -jar .\grasscutter-7.1.0.jar 2>&1 |
    Tee-Object -FilePath .\born-full.log |
    Select-String 'BORN|SetPlayerBornDataReq|4385|3064|PlayerEnterSceneNotify|9582'
```

`Tee-Object` 保存完整日志，`Select-String` 只在屏幕显示关键节点。人工复制终端非常容易把事件开头截掉，曾经因此误判“9582 没有发送”。

### 11.3 日志 absence 不能直接证明 packet absence

如果日志本身从中间开始、或复制范围被截断，搜索不到某包只能说明“当前文本片段里没有”。要先确认捕获边界完整。

### 11.4 服务端状态变化可以作为发送链旁证

例如 `PacketPlayerEnterSceneNotify` 构造会改变：

```text
sceneLoadState → LOADING
enterSceneToken → 新的非零 token
```

在发包日志太嘈杂时，可以用前后状态值辅助证明某构造链是否执行。

## 12. 二进制静态分析经验

### 12.1 从已知点建立锚

这次的关键锚点：

```text
26105 sender
22899 receiver
3064 handler
SetPlayerNameRsp handler
GHAHAPOPLIH naming page
```

先有几个可靠锚点，再在附近/调用图中扩展，比全二进制盲搜快很多。

### 12.2 obfuscated type name 依然非常有用

即使没有语义化名称，像：

```text
HJDNCHODGOL
GHAHAPOPLIH
AJHBBDMELNC
```

只要 metadata 中能稳定关联方法、字段、typeDefinition 和 methodDefinition，它们就可以充当可靠节点 ID。

### 12.3 parser 比序列化器更容易读字段

寻找：

```text
readTag()
cmp tag, imm
readInt32 / readString / readMessage
store [this + offset]
```

从 tag 直接恢复 field number，通常比猜字段语义快。

### 12.4 GetCmdId 是最强静态证据之一

如果某 obfuscated message type 有一个简单方法：

```asm
mov ax, 0x1121
ret
```

并且它位于协议接口实现位置，基本可以直接得到 CmdId。再用 handler 和 runtime 行为做交叉确认。

### 12.5 不要只扫 direct call

Unity IL2CPP / 生成代码里大量存在：

- tail jump
- interface dispatch
- virtual dispatch
- generic trampoline
- event delegate

本次 4385 的关键上游就是 tail-jump 链。如果只查 `E8 rel32`，会误以为目标方法“没有 caller”。

## 13. 对旧版本和同类项目的正确使用方式

同类项目最适合回答：

```text
“正常服务器应该做哪些逻辑？”
```

不要直接拿它们回答：

```text
“7.1 的 CmdId 一定是多少？”
```

例如：

- Starlight 给出了高质量的 born 生命周期设计；
- 老 Grasscutter 给出了 Traveler 初始化和持久化逻辑；
- LunaGC 7.1 说明社区同样卡在 born rsp CmdId；
- 旧 proto 可帮助识别字段语义和错误码。

最终 wire mapping 仍要由目标 7.1 客户端确认。

## 14. 当前出生工作剩余事项

协议映射已经基本收敛。后续主要是逻辑整合：

1. 把 `PacketOpcodes.SetPlayerBornDataRsp` 正式改为 `4385`。
2. 把 7.1 `SetPlayerBornDataRsp.retcode` 改为 field #7。
3. 使用现有 `PlayerNicknameNotify=3064`，确认/修正生成 proto 的 nickname field #12。
4. 新号 born handler 中按成熟时序处理 Traveler、队伍和持久化。
5. 在第一次进场前调用 `QuestManager.onPlayerBorn()`，专门验证 quest 351 与第二段序章动画的关系。
6. 将完整 `player.onLogin()` 从 born handler 中移出，设计轻量 `Player.onBorn()` / born lifecycle。
7. Born lifecycle 负责建立 world / scene，并发送第一次 `PlayerEnterSceneNotify`。
8. 解决当前完整 `onLogin()` 在出生期间触发的 `Defender_None_Born` Ability 异常和约 86 秒阻塞。
9. 验证开场两段动画、第一次场景加载、任务 351、重连和重复 born request。
10. 最后再合并回 `play/rino`，实验分支保留作为协议恢复证据。

## 15. 最核心的经验

这次真正有效的路线可以压缩成一句话：

> **当协议表不可信时，从客户端“成功之后会做什么”开始逆。行为找到状态，状态找到 handler，handler 找到 message type，message type 再给出 CmdId 和 protobuf。最后用最小运行时实验盖章。**

这套方法可以复用到绝大多数“Req 已知、Rsp/Notify CmdId 丢失”的 7.1 协议恢复工作。