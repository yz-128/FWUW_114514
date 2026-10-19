# DS-MODIFY-01 · 原 FMUM 改枪规则的独立复核

复核人：DeepSeek（ZCode 会话）。日期：2026-10-18（Asia/Shanghai）。
只读复核，未修改任何生产源码。工程绝对路径：`E:\JAVAMCMODGJ\FLAN 移植i\flanmod-template-1.21.1`

## 回执

确认工程绝对路径 `E:\JAVAMCMODGJ\FLAN 移植i\flanmod-template-1.21.1`，领取任务 **DS-MODIFY-01**（原 FMUM 规则复核）与 **DS-MODIFY-02**（改枪差异独立审查）。本文件是 DS-MODIFY-01 的回执与结果；DS-MODIFY-02 见 `deepseek-modify-review.md`。

注：`COLLABORATION.md` 写的起点 `run/flan/EscapeFromTarcraftReforged_1.5.3/guns/BocchiTheGlock18C.txt` 存在，但与滑套相关的挂点行当前为 `AttachPoints 0 17.5 0 0 0 0 29 14.5 0`，与交接文档一致（Y=17.5，未再上移）。

## 证据根

- `R/` = `..\FMUM-1710-master\src\main\java\com\flansmod`
- `T/` = 本工程 `src\main\java\com\flansmod`

## 一、原 FMUM 的硬约束（逐条列出，附证据）

### 1. 四种权限列表的优先级

`R/common/guns/AttachableSlot.java:24-38`：

```java
public boolean isAttachmentAllowed(AttachmentType toAddType)
{
    if(attachmentWhitelist != null) for(String s : attachmentWhitelist) if(toAddType.shortName.equals(s)) return true;
    if(attachmentBlacklist != null) for(String s : attachmentBlacklist) if(toAddType.shortName.equals(s)) return false;
    if(typeWhitelist != null) {
        for(int i : typeWhitelist) if(toAddType.attachmentType == i) return true;
        return false;
    }
    if(typeBlacklist != null) for(int i : typeBlacklist) if(toAddType.attachmentType == i) return false;
    return true;
}
```

顺序固定为：**名称白名单命中即放行 → 名称黑名单命中即拒绝 → 类型白名单非空时只按白名单（不命中即拒绝）→ 类型黑名单命中即拒绝 → 否则放行**。

关键点：名称白名单优先于名称黑名单，也就是说一个同时在两份名单里的配件**会被允许**。这是原版真实语义，不是笔误。

### 2. 类型比较用的是 `AttachmentType` 的哈希，不是 `ItsType`

`R/common/guns/AttachmentType.java:313-314`：

```java
else if(split[0].equals("AttachmentType"))
    attachmentType = split[1].hashCode();
```

`R/common/guns/ModifiableType.java:167-169` 把白/黑名单里的类型串也存成 `split[i].hashCode()`。所以比较的两侧都是字符串哈希。`ItsType` 是另一套 byte 常量（`ModifiableType.ItsType`，`barrel/muzzle/bolt/sight/...`），参与的是模型与动作逻辑，不参与槽位放行。

### 3. 每个槽的数量上限

`R/common/guns/AttachableSlot.java:10` 默认 `maxCanAttach = 64`；
`R/common/guns/ModifiableType.java:104` 由 `MaxAttachments` 逐槽覆盖；
`R/common/network/PacketGunAttachment.java:654` 实际判定：

```java
if(prTag.tagCount() >= FlansMod.maxAttachments || prTag.tagCount() >= slot.maxCanAttach) return;
```

即**全局上限与服务端配置 `FlansMod.maxAttachments`（默认 5，`FlansMod.java:119,523`）取交集**，两个都要满足。这一点容易被漏掉：只实现每槽 `maxCanAttach` 会让默认 5 件的全局上限失效。

### 4. 导轨步数与偏移

`R/common/guns/ModifiableType.java:95-104`：

- `AttachPoints x y z [x y z ...]`：每 3 个数一个槽，值 `/16`。
- `AttachVectors vec maxStep`：每 2 个数一个槽，`vec/16` 与整数步数。
- `AttachRotates rot`：每 1 个数一个槽，度。
- `MaxAttachments n [n ...]`：每个槽一个上限。

服务端校验 `PacketGunAttachment.java:655-657`：`transStep > slot.maxStep` 拒绝；`attachOffset >= toAddType.attachOffset.length` 拒绝（`attachOffset` 的长度由 `AttachOffsets` 决定，默认 `new float[]{0F}`）。

位置计算 `ModifiableType.java:52,56` 与 `PacketGunAttachment.java:691`：

```
pos = slot.attachVec * transStep + toAddType.attachOffset[attachOffset]
```

### 5. 拆卸时子配件随父件整体返还

`PacketGunAttachment.java:117-141` 的 `returnLayerStacks`：

```java
for(int i = prList.tagCount(), j; --i >= 0; prList.removeTag(i)) {
    for(j = (prTag = GunType.getTagListFromTagList(prList, i)).tagCount(); --j >= 0; ) {
        returnLayerStacks(prTag.getCompoundTagAt(j), player);
        prTag.removeTag(j);
    }
}
...
ItemStack toGive = new ItemStack(thisType.item, 1, AttachmentType.getAtDam(atTag));
if(!InventoryHelper.addItemStackToPlayerInventory(player.inventory, toGive)) player.entityDropItem(toGive, 0.5F);
```

递归**先处理全部子件**，每个子件都作为独立物品返还（子件不合并进父件）。这一点与当前移植的"整组件返还"相反，见 DS-MODIFY-02 第二节。

### 6. 双握把限制

`PacketGunAttachment.java:670-675`：

```java
if((toAddType.itsType == ItsType.GRIP || toAddType.itsType == ItsType.REAR_GRIP)
        && AttachmentType.getAttachmentAtByType(itemStack.stackTagCompound, new byte[0], toAddType.itsType, ItsType.NONE).size() > 0)
    return;
```

`getAttachmentAtByType`（`AttachmentType.java:634-651`）是**全树递归搜索**，不是只看当前槽的直接子件。所以：

- 限制的维度是 `itsType`（`grip` 与 `reargrip` 各自独立）。
- 搜索范围是整棵配件树（含嵌套在别的配件下的握把）。
- `spType` 参数为 `ItsType.NONE` 时只按 `itsType` 匹配；同一函数还被激光/手电/可切换/折叠副瞄复用（`spType` 分支）。

### 7. 射击/自动/充电门控

`R/common/guns/ModifiableType.java:259-283` 的 `checkCanOperate`：

```java
LinkedList<Integer> shootNeed = ... neededToShoot ...
LinkedList<Integer> autoNeed  = ... neededToAuto ...
LinkedList<Integer> chargeNeed= ... neededToCharge ...
for each slot, each child:
    atType.checkCanOperate(baseTag, cans);            // 先递归
    shootNeed.remove(intType = atType.attachmentType); // 再逐层消耗
    autoNeed.remove(intType);
    chargeNeed.remove(intType);
if(cans[0]) cans[0] = shootNeed.size() == 0;
...
```

要点三条：**递归到整棵树**、**按多重集消耗**（`remove` 一次只删一个，重复需求需要重复满足）、**三个门控彼此独立**。需求串在解析时被存成 `split[i].hashCode()`（`ModifiableType.java:106-120`）。

### 8. 拆卸相机/枪管/枪机的连带状态

`PacketGunAttachment.java:757-775`：拆卸后若 `barrelRemoved` 则退膛内子弹（`getAmmoAt(itemStack,0)` 并清空），若 `boltRemoved` 则清 `BOLT_CATCH_OFF`；随后 `setGunStates`、`setGunOperatable`、`setSightsAtForGun` 全部重算。`returnLayerStacks` 里通过 `thisType.itsType == BARREL/BOLT` 设置这两个标志（`:129-130`）。

### 9. Shadow 体系

`R/common/guns/AttachmentType.java:207-220` 用 switch 穿透：

```java
switch(split.length) {
    case 5: itsShadowType = ItsShadowTpfye.fromString(split[4]);
    case 4: setUpdateGunStates(Boolean.parseBoolean(split[3]));
    case 3: setHideInCreativeTab(Boolean.parseBoolean(split[2]));
    default: shadowType = split[1];
}
```

`ShadowType <name> <hideInCreative> <updateGunStates> <toggle|cosight|generic>`。`ItsShadowTpye` 常量见 `:136-145`。作用：一个配件可以有一个"替身"配件（如 G33 折叠/展开），二者共享物品但呈现不同。`returnLayerStacks` 在返还时会沿 `shadowType` 链替换成对外的那个（`:131-138`）。

## 二、当前移植的对应实现状态

| 规则 | 目标实现 | 判定 |
|---|---|---|
| 四种列表优先级 | `T/common/GunAttachments.java:129-135` | **一致**。名称白→名称黑→非空类型白→类型黑，且用的是 `attachmentType` 字符串而非 `itsType` |
| 类型比较维度 | `AttachmentType.attachmentType` 为字符串，`ModifiableType` 白/黑名单存字符串 | **一致**（原版是 hash，语义等价） |
| 每槽上限 | `GunAttachments.java:144` `mount.maxCanAttach`，默认 64（`ModifiableType.java:35`） | **部分**：缺全局 `maxAttachments` 默认 5 的上限 |
| 导轨步数/偏移 | `GunAttachments.java:143` 校验 `step<=mount.step`、`offset<attachOffsets.length`；`:149-152` 计算位置 | **一致** |
| 拆卸返还 | `GunAttachmentTransaction.java:37-51` 整组件返还 | **与原版不同**（已在交接文档声明为有意差异） |
| 双握把 | `GunAttachments.java:146-147` `hasKind(tree, part.itsType)` | **基本一致**：`hasKind` 递归全树（`:192-198`），但只按 `itsType` 字符串比较，未覆盖原版 `spType` 的四种特例 |
| 射击/自动/充电门控 | `GunAttachments.java:200-212`；调用点 `GunFiring.java:215,415`、`GunReloading.java:33`、`GunMagazine.java:479` | **一致**：递归 + 多重集 + 三门控 |
| 枪管/枪机拆卸连带 | 未找到 `barrelRemoved`/`boltRemoved` 对应物 | **缺失** |
| Shadow 体系 | `attachmentType.java:56-58` 解析了 `shadowType`/`hideInCreativeTab`/`shadowUpdatesGunStates`，但**无任何消费者**（全项目 grep 仅出现在解析处） | **仅解析，未实现** |
| 激光/手电/可切换/折叠副瞄 | 未见 | **缺失** |

## 三、必须在 1.21.1 重现的硬约束（给 Codex 的清单）

按影响面排序：

1. **全局 `maxAttachments` 上限**。原版默认 5，服务端可配、并下发给客户端（`PacketModConfig.java:34,76`）。当前移植只有每槽 64。真实内容包里 `MaxAttachments` 出现在 26 个 EFT 枪文件、若干附件文件中，多为一槽 1 或 2，因此全局 5 在多数枪上不触发；但只要有一把枪的某个槽配 6 个以上，行为就会与原版分岔。
2. **`ShadowType` 的整套语义**。当前解析后无人使用。G33 这一对（`G33Enabled`/`G33Disabled`，`run/flan/EscapeFromTarcraftReforged_1.5.3/attachments/`）就是典型：它们是 `cosight`，依靠替身切换来表现折叠/展开。解析了不用，意味着这两件在改枪界面里会被当成两个独立配件，且拆卸时不会替换成对外的那个。
3. **枪管/枪机拆卸的连带状态**。原版拆枪管会退膛内弹、并重算可射击性；拆枪机清空仓挂机。当前移植的拆卸只改树和背包，不动弹膛与挂机状态。
4. **激光 / 手电 / 可切换瞄具 / 折叠副瞄的开关路径**。原版通过 `PacketGunAttachment` 的 `SWITCH_LASER`、`TOGGLABLE`、`CO_SIGHT_FLIPPED` 等操作实现，且拆装后会沿树重算。当前移植的 `GunModificationPayload` 只有 `OPEN/INSTALL/REMOVE/MOVE` 四种操作。
5. **`AttachmentTypesNeeded`**。当前解析进 `attachmentTypesNeeded` 后无消费者（`AttachmentType.java:32,139` 之外无引用）。原版 `typesNeeded` 亦无运行时消费者（`AttachmentType.java:86,317-326` 只有赋值），所以这一条**不是缺失**，属于两边都未启用。

## 四、需要 Codex 确认的三处歧义

1. `MaxAttachments` 的全局值，是否要作为服务端配置加入本工程（原版默认 5）。若加入，需要同步到客户端否则界面会显示可装而服务端拒绝。
2. `ShadowType` 的优先级：原版只在**拆卸返还**时沿链替换（`returnLayerStacks`），装配与渲染仍按实际挂的那个。移植若要实现，需确认是只做返还替换，还是连界面显示一起做。
3. 双握把的 `spType` 特例（激光/手电/折叠副瞄/可切换）是否属于本轮范围。这些是 `getAttachmentAtByType` 的共用分支，实现它们会顺带解决问题 4 的搜索基础。

## 五、已确认 / 尚不确定 / 未运行

**已确认（源码级、可复现）**：第一节全部 9 条，均给出文件行号。第二节表格中"一致/部分/缺失"的判定，均以目标源码实际存在或不存在对应代码为准。

**尚不确定**：
- `MaxAttachments` 全局 5 在真实 EFT 内容包上是否会实际触发。我只统计了声明数量（26 个枪文件 + 若干净文件），未计算每槽实际挂载数。
- `ShadowType` 的 G33 对在实机里是否真的被玩家使用（物品是否出现在战利品/购买表）。未检查。
- 原版 `atHitBoxTest`（`FlansMod.java:115` 默认 true）在 EFT 包上的实际效果。EFT 的 attachments 目录中 `BigHitBox` 出现 0 次，`HitBox` 也未见；Octagon 包有 73 个文件含 `BigHitBox`。因此几何冲突检测对 EFT 无影响，对 Octagon 有潜在影响，但当前移植的 `GunActions.java:177` 只把 hitBox 用于枪长/碰撞射线，**没有装配时的冲突检测**。

**未运行**：未启动游戏、未构建、未执行任何离线验证工具。本报告全部为静态源码复核。
