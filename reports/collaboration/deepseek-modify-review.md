# DS-MODIFY-02 · 改枪差异独立审查

复核人：DeepSeek（ZCode 会话）。日期：2026-10-18（Asia/Shanghai）。
只读审查，未修改任何生产源码。工程：`E:\JAVAMCMODGJ\FLAN 移植i\flanmod-template-1.21.1`（0.4.3 工作区）。

## 回执

领取 **DS-MODIFY-02**。本文件覆盖任务书指定的七个审查方向：串改同名枪、存档丢失、复制或吞噬配件、重放过期操作、退出/换槽后仍能改、渲染/属性读取默认树，另加规则与 FMUM 的语义差异。DS-MODIFY-01 见 `deepseek-modify-rules.md`。

## 结论摘要

**未发现会导致物品复制、物品吞噬或跨枪串改的确定性缺陷。** 拒绝路径的求值顺序是正确的：所有校验都在扣减或写回之前完成。以下找出的问题按严重度排列，其中 P1 全部是"与原版行为分岔"，不是数据损坏。

| 级别 | 项 | 位置 |
|---|---|---|
| P1 | 缺全局 `maxAttachments`（原版默认 5）上限 | `GunAttachments.java:144` |
| P1 | 拆枪管/枪机不触发退膛与空仓挂机重算 | `GunAttachmentTransaction.java:36-53` 全段无对应逻辑 |
| P1 | `ShadowType` / co-sight / toggle / 激光 / 手电 全部未实现 | 仅解析于 `AttachmentType.java:177-193` |
| P1 | 开镜时间、移动速度、切镜时间字段完全无消费者 | 见下 §4 |
| P1 | 配件涂装（paintjob）只存不用 | `GunAttachments.java:58,74,173,180` |
| P2 | `allowed()` 用忽略大小写比较，原版是精确比较 | `GunAttachments.java:126-128` |
| P2 | 双握把按字符串比较，`rear_grip` / `reargrip` 不归一化 | `GunAttachments.java:146-147` |
| P2 | 目录列出兼容件时未过滤握把唯一性与槽容量 | `GunAttachmentCatalog.java:14-18` |
| P2 | `read()` 返回缓存中的可变 List | `GunAttachments.java:24-35` |
| P3 | 修订号 15 位，32768 次后回绕 | `GunAttachments.java:22,84` |

## 一、任务书指定方向的逐项结论

### 1.1 串改同名枪 —— 未发现

三处独立机制共同阻止：

1. 装配树按 **ItemStack 实例** 缓存：`GunAttachments.java:20` 的 `WeakHashMap<ItemStack, Cached>`。我核对了 `build/moddev/artifacts/neoforge-21.1.250-sources.jar` 里的 `net/minecraft/world/item/ItemStack.java`：该类**没有重写 `equals`/`hashCode`**（只有静态 `matches` 与 `hashItemAndComponents`）。因此 WeakHashMap 用的是身份语义，两把同型号枪是不同键。
2. `withAttachments` 是浅克隆并记录 `originalDefinition`：`GunType.java:24-32`。渲染与数值读的是克隆，注册与定义身份不变。
3. 写回只落在传入的那一个 `ItemStack`：`GunAttachments.write`（`:82-86`）。

反例尝试（静态推演）：给 A 枪装件后打开 B 枪 —— B 的 `read()` 走 `CACHE.get(B)` 未命中，`data` 为 B 自己的 CustomData，得到 B 自己的树。**无交叉。**

### 1.2 存档丢失 —— 有限风险，非缺陷

序列化路径完整：`encode`（`:66-80`）写 `name/slot/step/offset/paintjob/item/children`，`decode`（`:46-64`）按 `owner.slots` 重建并把 `item` 快照还给节点。往返一致。

**已知风险**：`decode` 在 `type == null || slot >= owner.slots.length` 时 `continue`（`:56`）——**静默丢弃**。也就是说：

- 卸载一个内容包后再打开枪，该包提供的配件会从树里消失，且**不会返还成物品**。
- 这不比原版更糟：FMUM 的 `getAtType` 同样解析不到就跳过（`AttachmentType.java:582` 起）。但当前实现没有任何日志，排查时不会留下痕迹。建议至少 `FlansMod.log` 一条。

### 1.3 复制或吞噬配件 —— 未发现

逐条核对求值顺序（`GunAttachmentTransaction.java:16-61`）：

- **INSTALL**：`revision` → `copy` → `canInstall` → `mounted`（只读源栈）→ 节点数/深度上限 → `add` + `write` → **最后** `source.shrink(1)`（`:33`）。任一校验失败都在 `shrink` 之前返回，**不会吞件**。
- **REMOVE**：先找落点 → 找不到返回 `inventory_full`（`:43`）→ 才 `remove` + `write` + 放入（`:48-51`）。**满包时枪不变**。
- **MOVE**：只改 `step/offset` 并 `position`，不动物品。

可能的复制面我检查了两处，均安全：

- `mounted()`（`:176-190`）把源件的子树复制进 `node.children`，同时从 `itemData` 里 `tag.remove(KEY)` / `tag.remove(REVISION)`（`:184`），避免子树在物品快照与节点里各存一份。源栈随后 `shrink`。
- `item()`（`:166-175`）用 `node.children` 重建返回件的 CustomData，不会凭空多出子件。

**一个语义差异**（交接文档已声明）：REMOVE 返回**一个**带子树的整组件，原版 `returnLayerStacks`（`PacketGunAttachment.java:117-141`）把每个子件**单独**返还。后果只在背包容量核算：原版拆一个带 2 个子件的护木需要 3 个空位，本移植只需 1 个。这会让本移植在原版会失败的情况下成功，属于更宽松，不是复制。

### 1.4 重放过期操作 —— 已阻止

三道闸：`request.containerId()!=containerId`（`GunModificationMenu.java:52`）、`player.containerMenu!=this`（同处）、`revision` 不匹配返回 `"stale"`（`GunAttachmentTransaction.java:18`）。`write()` 每次 `+1`（`:84`），因此同一请求重放必然 stale。容器 ID 由原版在换菜单时递增，重连后旧包也会被拒。

### 1.5 退出或换槽后仍能改 —— 已阻止

`stillValid`（`GunModificationMenu.java:65-68`）要求 `inventory.selected==gunSlot` 且服务端 `gunStack()==openedStack`（身份比较）。`modify()` 第一行再次调用 `stillValid`（`:52`），不是只靠原版轮询。`write()` 原地改组件、不替换 ItemStack 实例，所以正常的改枪不会误关菜单；换槽/丢枪/被替换则立即失效。

### 1.6 渲染与属性读取默认树 —— 主链路正确，两处漏网

已接线的（均为 `GunAttachments.view`）：

- 物品渲染 `TurborigItemRenderer.java:152`，此后 `:241-245` 的配件遍历、`:225` 的开镜枢轴、`:256` 的手臂、`:259` 的枪口特效都在视图上。
- 命中/贴墙 `GunActions.java:156,161`；取出时间 `:29`。
- 射击数值 `GunFiring.java:379-380`，散布与伤害 `:421,426`；枪声 `:439`。
- 门控 `GunFiring.java:215,415`、`GunReloading.java:33`、`GunMagazine.java:479`。
- 工具提示 `GunDetails.java:36`。

漏网：

1. **`GunAnimationDriver.java:51` 用 `InfoType.getType(held)` 拿的是定义本体**，`:103` 的 `gun.runPoseTime` 因此读的是未受配件影响的基值。
2. `LegacySightPose.forGun` 在部分调用点收到的是快照（`MuzzleEffectsRenderer.java:49,119,128` 经 `view`），但 `FmumFirstPersonPose.aim` 的第二参数来自 `TurborigItemRenderer.java:225` 的 `gun`（视图，正确）与 `FmumCollisionRay.java:17`（经 `view`，正确）。这一条没有问题，记录以排除疑虑。

## 二、与原版规则的语义差异（配合 DS-MODIFY-01）

### 2.1 全局 `maxAttachments` 缺失 —— P1

原版 `PacketGunAttachment.java:654` 同时判 `FlansMod.maxAttachments`（默认 5）与 `slot.maxCanAttach`。当前只有后者（`GunAttachments.java:144`）。影响：任何单槽允许超过 5 件的枪，本移植能装第 6 件而原版不能。真实 EFT 包里 `MaxAttachments` 多为 1 或 2，触发概率低，但一旦某包写 `MaxAttachments 8` 就会分岔。

### 2.2 拆枪管/枪机无连带 —— P1

原版 `PacketGunAttachment.java:757-775`：`barrelRemoved` → 退掉膛内弹并清空；`boltRemoved` → 清 `BOLT_CATCH_OFF`；随后重算 `setGunStates` / `setGunOperatable` / `setSightsAtForGun`。

当前 `REMOVE` 分支（`GunAttachmentTransaction.java:36-53`）只操作树与背包。**可复现现象**：装上枪管、上膛、再拆枪管 —— 膛内那发仍然存在。本移植的挂机状态在 `GunActions.java:149-152`（`flansmodBoltCatch`），拆卸时不会被清除。

### 2.3 Shadow / 副功能未实现 —— P1

`shadowType`、`hideInCreativeTab`、`shadowUpdatesGunStates` 在 `AttachmentType.java:56-58,177-193` 解析后**全项目无消费者**（我 grep 过生产源码）。原版用途在 `AttachmentType.java:207-220` 与 `returnLayerStacks:131-138`。

内容包里的实例（`run/flan/EscapeFromTarcraftReforged_1.5.3/attachments/`）：

```
G33Enabled : ItsType sight, ShadowType G33Disabled false false cosight
G33Disabled: ItsType sight, ShadowType G33Enabled  true  false cosight
```

两者互为替身，构成折叠/展开。当前移植会把它们当成两个独立配件：改枪界面各列一条，装上其中一个后另一个仍可同时装（`ItsType` 都是 `sight`，不触发握把唯一性）。这与原版观感不同。

同批未实现：`SWITCH_LASER`、`TOGGLABLE`、`CO_SIGHT_FLIPPED`（原版 `PacketGunAttachment.java:184-200`、`AttachmentType.java:634-651`）。当前 `GunModificationPayload.java:9` 只有 `OPEN/INSTALL/REMOVE/MOVE`。

### 2.4 配件倍率只应用了一部分 —— P1

`GunStats.attachment`（`:25-35`）应用了：伤害、弹速、四种散布、纵向/横向后坐力、模型后坐力五项。

未应用（字段有解析、无任何消费者）：`aimTimeMult`、`sightSwitchMult`、`moveSpeedMultiplier`、`runPoseTimeMult`、`reloadTimeMultiplier`、`meleeDamageMultiplier`（`AttachmentType.java:39-47,93-99`）。

更深一层：这些倍率对应的**枪本体字段**同样无消费者：

- `GunType.aimTimeConst`（`:150`）、`sightSwitchConst`（`:150`）、`moveSpeedModifier`（`:343`）、`aimingMoveSpeed`（`:177`）。
- 开镜进度是固定步长：`GunActions.java:83` 每 tick `±0.2F`，5 tick 到满。原版用 `getAimTimeConst`（FMUM `GunType.java:958`）。

所以"装了高倍镜开镜变慢"这类预期在当前移植里不会发生。这可能是刻意的简化，但如果是，应在报告里写明；否则这是一处需要补的缺口。

### 2.5 涂装只存不用 —— P1

`node.paintjob` 在 `ModifiableType.java:337,340` 解析、`GunAttachments.java:58,74,173,180` 往返，但**没有任何渲染代码读它**（`TurborigItemRenderer.renderAttachments:390-425` 只用 `textureFor(at)`）。原版 `AttachmentType.getPaintjob` → `getTex(dam)` 决定贴图。

另外 `:340` 把具名变体存成 `String.hashCode()`，原版 `DefaultAts.java:65` 存的是 `Item.getIdFromItem(p.item) | (1<<15)`。两者编码方式不同，将来实现涂装贴图时必须按原版重算，否则对不上。

### 2.6 小偏差 —— P2

- **大小写**：`allowed()`（`:126-128`）用 `equalsIgnoreCase`；原版 `AttachableSlot.java:26-27` 是 `toAddType.shortName.equals(s)` 精确比较。本移植更宽松。若某包故意用大小写区分白/黑名单，行为会不同。实际包内未见此用法。
- **握把拼写**：`:146-147` 直接比较 `part.itsType` 字符串。原版把 `rear_grip` 归一化为 `REAR_GRIP` 常量（`AttachmentType.java:516-531`）。若同一把枪上两种拼写混用，唯一性检查会漏判。EFT 包只用 `reargrip`，暂不触发。
- **目录未过滤**：`GunAttachmentCatalog.compatible`（`:14-18`）只调 `allowed`，不含握把唯一性与容量。UI 会列出装上必然失败的件（例如已有握把时仍列另一握把）。点击后由服务端返回 `"grip"`，不会误装，但交互上应该有提示。

## 三、稳健性观察（不构成本轮缺陷）

1. **`read()` 返回缓存内部列表**（`GunAttachments.java:24-35`）。当前所有会改树的调用方都先 `copy`（`GunAttachmentTransaction.java:19`、`GunModificationScreen.java:195`、`GunAttachmentCatalog.previewBase`），所以没有实测问题。但一次误用就会污染该 ItemStack 的整份缓存。建议改为返回不可变视图，或至少在方法注释里写死这条约束。
2. **修订号 15 位**（`:22,84`）：32768 次编辑后回绕，理论上可让一条极旧的请求在回绕点被接受。单人改枪不可能达到，联机自动化才有可能。
3. **`decode` 的节点预算与深度**：`budget` 递减在递归中共享（`:49-62`），超限只是停止读取剩余节点，不会抛错。行为可接受，但同样是静默截断。
4. **`GunModificationMenu.clicked` 全空**（`:70`）：配 36 个 `mayPlace=false/mayPickup=false` 的槽位（`:26-29`），是彻底的只读镜像。没有发现可绕过的原版点击路径。

## 四、离线断言不能证明的部分

`reports/eft/改枪界面与数据修复-0.4.2.md` 与 `改枪预览与配件目录-0.4.3.md` 声明的验证是 17842 + 4382 + 64 项**离线断言**，加 9 项既有回归。以下必须实机验证，离线结论不覆盖：

1. **真实 OpenGL 下的预览**：法线、明暗、取景矩阵在 GPU 上是否正确；离线 `GunModificationView` 只证明 CPU 顶点数据。
2. **真实点击与拖拽**：`GunModificationScreen` 的 `addAction` 命中区、滚动、窄屏切换（`:403` 起的 `positionControl`）。离线渲染不模拟鼠标。
3. **专用服务器**：菜单同步、`DataSlot` 修订号、快速连续拆装、换枪后旧请求、断线重连后菜单状态。所有 64 项断言都在单进程内跑。
4. **与光影/优化模组的交互**：预览用独立 shader 路径，实机叠加 Iris/Sodium 时是否冲突未知。
5. **开镜与移动速度**：§2.4 指出的固定 0.2/tick 是否就是预期的最终手感，必须实机确认。
6. **拆枪管后膛内弹是否残留**：§2.2 的静态结论建议用实机做一次「装枪管→上膛→拆枪管→看弹膛」确认。
7. **G33 折叠对的观感**：§2.3 说明当前会显示为两个独立件；需要确认这是否可接受。

## 五、给 Codex 的最小修复建议（按投入产出排序）

1. `GunAttachments.canInstall` 增加全局上限（服务端配置默认 5），并与客户端同步（原版走 `PacketModConfig`）。
2. `REMOVE` 分支补枪管/枪机连带：拆 `barrel` 时退膛并清空 `GunMagazine` 第 0 腔，拆 `bolt` 时清 `GunActions.boltCaught`。
3. `decode` 丢弃节点时写一条日志，避免"配件无声消失"无法排查。
4. `GunAttachmentCatalog.compatible` 增加握把唯一性与容量过滤，或返回原因让 UI 灰显。
5. `allowed()` 改为精确比较，或至少在注释里记录这是有意的放宽。

## 六、验证边界

**已确认**：§1 全部六项结论、§2 全部差异，均有源码行号；`ItemStack` 未重写 `equals/hashCode` 的结论来自本工程已缓存的 `neoforge-21.1.250-sources.jar`。

**尚不确定**：
- §2.4 的固定 0.2/tick 是否为有意简化。建议 Codex 确认设计意图。
- §2.1 的全局 5 件上限在真实内容包上是否会被触发（我只统计了 `MaxAttachments` 声明，未逐枪计算实际挂载数）。
- `ShadowType` 的 G33 对是否在实际游玩中出现（未检查战利品/购买表）。

**未运行**：未启动游戏、未执行 Gradle 构建、未运行任何离线验证工具。本报告全部为静态源码复核，不含运行时证据。
