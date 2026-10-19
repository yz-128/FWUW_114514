# FMUM 1.7.10 核心逐字段对照

复核人：DeepSeek（ZCode 会话）。日期：2026-10-18（Asia/Shanghai）。
只读复核。工程：`E:\JAVAMCMODGJ\FLAN 移植i\flanmod-template-1.21.1`（0.4.3）。

## 参考对象说明

本轮把三个 1.7.10 参考分清了：

| 参考 | 身份 | 用途 |
|---|---|---|
| `..\FMUM-1710-master` | `Flan's mod Ultimate 1.1 Modified`，version 1.0，MC 1.7.10 / Forge 10.13.4.1614，549 个 Java 文件 | FMUM 源码，可读，用于读规则 |
| `build/eft-core-reference.jar` | EFT 内容包实际编译所针对的 1.7.10 核心（1.5 MB，2023-07-09） | **权威**：字段与解析行为以它的字节码为准 |
| `..\Flans-Mod-Plus-Ultimate` | FMUSE 1.60 | 基础 1.7.10 行为 |

两者在 `AttachmentType` 字段集上一致；`eft-core-reference.jar` 是 EFT 包真正跑过的那个核心，因此本报告以它为主。

## 一、确认可用的部分

以下与原核心一致，不需要再查：

- 四种权限列表优先级（名称白 → 名称黑 → 非空类型白 → 类型黑），类型维度用 `AttachmentType.attachmentType` 而不是 `ItsType`。
- 导轨步数 `AttachVectors`、偏移 `AttachOffsets`、每槽 `MaxAttachments`、位置公式 `slot.attachVec * step + attachOffset[offset]`。
- 射击/自动/充电三门控（`NeededToShoot`/`NeededToAuto`/`NeededToCharge`）：递归到整棵树、按多重集消耗、三门控独立。
- `DefaultAttachments` 的括号嵌套与 `next` 分组解析。
- 单件保存、修订号、服务端拆装校验、物品守恒。
- 枪体/滑套模型变换：Bocchi 的 `translateAll(0,0.5,0)+flipAll()` 与滑套的纯 `flipAll()` 已正确还原。

## 二、真实缺陷（按影响排序）

### 2.1 `ZoomLevel` 解析错误，且镜筒渲染整套缺失 —— P0

原核心 `AttachmentType.read()` 字节码（偏移 2291-2461）明确：

```
split[0] == "ZoomLevel"
  split[1] → scopeGlassModel   (模型名，可 "None")
  split[2] → blackMaskModel    (模型名，可 "None")
  split[3], split[5], ...  → FOVZoomLevel[i]        (浮点)
  split[4], split[6], ...  → mouseSensitivityMult[i] (浮点)
  数组长度 = (split.length - 3) / 2
```

移植版 `common/types/AttachmentType.java:159-160`：

```java
case "ZoomLevel" -> zoomLevel = toFloat(split[1]);
case "FOVZoomLevel" -> FOVZoomLevel = toFloat(split[1]);
```

`toFloat` 对非数字返回 `0F`（`InfoType.java:253-263`），所以真实内容包里的

```
ZoomLevel EFTRAtt.PSO1Glass EFTRAtt.eftBlackMask 40 0.6
```

被解析成 `zoomLevel = 0`，`scopeGlassModel` / `blackMaskModel` / `mouseSensitivityMult` 全部丢失。

影响范围：EFT 包 6 个文件含 `ZoomLevel`（`G33Enabled`、`G33Disabled`、`NF7x`、`PU35RifleScope`、`PSO1`、`SeecterDR1x4x`），全部包共 15 行。`SeecterDR1x4x` 写了两级 `48.75 0.4 15.5 0.8`，原版可切倍率，移植版连单级都没有。

连带缺失：

- `FOVZoomLevel` 在原核心是 `float[]`，移植版是单个 `float`。
- `mouseSensitivityMult` 完全不存在。
- `scopeGlassModel`、`blackMaskModel`、`coatedGlassModel` 只存模型名字符串，没有加载，也没有任何渲染路径。
- 全项目没有任何 `Framebuffer` 或镜内渲染代码（grep `framebuffer|scopeGlass|renderScope` 为零）。原核心 `RenderGun.renderScopeFramebuffer` 用独立 framebuffer 做镜内画面。

结论：**开镜只有姿态变化，没有放大、没有镜筒画面、没有黑边遮罩、没有倍率切换。** 这是本对照里最大的一块缺口。

### 2.2 ADS 不缩放视野 —— P0（同一问题的表现面）

移植版唯一的 FOV 事件是 `client/GunDetails.java:53-64` 的 `ViewportEvent.ComputeFov`，它只乘上后坐力项 `(1F + kick)`，不读 `FOVZoomLevel`、不读 `AimState`。

对比原核心：`FlansModClient.java:2017` 用 `originalFOV * (1F + cameraFOVConstant)` 走后坐力，`RenderGun.java:278` 用 `originalFOV / FOVZoom` 走开镜放大。

因此即使把 §2.1 的解析修好，还需要在 FOV 事件里接上开镜倍率，并按 `zoomProgress` 插值。

### 2.3 16 种改枪操作只实现了 4 种 —— P1

原核心 `OperationQueue.Operation`（`OperationQueue.java:11-15`）：

```
UNSTALL_AT, REPOS_AT, REPAINT_AT, PAINTJOB, EX_PAINTJOB,
NEXT_AIM_CENTER, LAST_AIM_CENTER, SWITCH_CO_SIGHT, TOGGLE_AT,
SWITCH_LASER, SWITCH_LIGHT, NEXT_ZOOM, LAST_ZOOM,
NEXT_ZOOM_L, LAST_ZOOM_L, SWITCH_DOT
```

移植版 `GunModificationPayload.java:11`：

```java
public static final int OPEN=0, INSTALL=1, REMOVE=2, MOVE=3;
```

覆盖了 `UNSTALL_AT`（REMOVE）、`REPOS_AT`（MOVE）、安装（INSTALL），其余 13 种没有对应操作：

- 涂装：`PAINTJOB`、`EX_PAINTJOB`、`REPAINT_AT`
- 瞄具：`NEXT_AIM_CENTER`、`LAST_AIM_CENTER`、`SWITCH_CO_SIGHT`、`NEXT_ZOOM`、`LAST_ZOOM`、`NEXT_ZOOM_L`、`LAST_ZOOM_L`、`SWITCH_DOT`
- 战术件：`SWITCH_LASER`、`SWITCH_LIGHT`、`TOGGLE_AT`

其中 `NEXT_AIM_CENTER`/`LAST_AIM_CENTER` 用于在多组机械瞄具之间切换，`SWITCH_CO_SIGHT` 用于 G33 这类折叠副瞄，`SWITCH_DOT` 切换准点样式，`NEXT_ZOOM` 切换倍率。这几项与 §2.1、§2.4 是同一功能族。

### 2.4 准点（DotModel）只有解析，没有渲染 —— P1

`AttachmentType.java:203-210` 把 `DotModel <model> <texture> <count>` 存进 `lightStuffModel` / `lightStuffTexture` / `lightTextureCount`。

全项目对 `lightStuffModel` 的引用只有这两处（字段声明与赋值），**没有渲染调用**。

原核心在 `RenderGun.java:810,828,832,1057,1076` 用 `renderStaticGlowOnModel(lightStuffModel)` 与 `lightStuffModel.defaultModel[j].render(f)` 绘制，并用 `numTexture` 配合 `getDotNum()`/`setDotNum()` 切换准点样式。

影响范围：EFT 包 19 个文件含 `DotModel`。也就是说 19 个瞄具的准星/光点现在不会显示。

### 2.5 激光与手电只有解析，没有渲染 —— P1

`AttachmentType.java:299-305` 解析 `LaserPointer`，存 `laserPointer` / `laserModelFP` / `laserModel`。`Flashlight` 存 `flashlight` / `flashlightRange` / `flashlightStrength`。

全项目无渲染消费者。原核心在 `RenderGun.java:1893-1896` 绘制手电光锥，并有 `laserFrom` 起点、`laserModelFP` 第一人称激光。

影响范围：EFT 包各 1 个文件声明。

### 2.6 ShadowType / co-sight / toggle 未实现 —— P1

`shadowType`、`shadowUpdatesGunStates`、`hideInCreativeTab` 三个字段只在 `AttachmentType.java:177-193` 写入，无处读取。

原核心把它编译成 `itsShadowType` 位域（`isNone/isGeneric/isToggle/isCoSight/getHideInCreativeTab/getUpdateGunStates`），配合 `returnLayerStacks` 做替身替换、`SWITCH_CO_SIGHT` 做折叠切换。

影响范围：EFT 包 10 个文件（G33 一对、MP7 前后机瞄两对、QBZ191 两对）。

### 2.7 配件属性倍率大半没有消费 —— P1

| 字段 | 移植版有解析 | 有消费者 |
|---|---|---|
| `spreadMultiplier` | ✓ | ✓ |
| `recoilMultiplier` / `recoilYawMultiplier` | ✓ | ✓ |
| `modelRecoilMultipliers[5]` | ✓ | ✓ |
| `damageMultiplier` | ✓ | ✓ |
| `bulletSpeedMultiplier` | ✓ | ✓ |
| `aimTimeMult` | ✓ | ✗ |
| `sightSwitchMult` | ✓ | ✗ |
| `moveSpeedMultiplier` | ✓ | ✗ |
| `runPoseTimeMult` | ✓ | ✗ |
| `meleeDamageMultiplier` | ✓ | ✗ |
| `reloadTimeMultiplier` | ✓ | ✗ |
| `onUseAimTimeMult` | ✗ 未解析 | — |
| `onUseSightSwitchMult` | ✗ 未解析 | — |
| `aimingMoveSpeedMultiplier` | ✗ 未解析 | — |

而且对应的**枪本体**字段也没有消费者：`GunType.aimTimeConst`（`:150`）、`sightSwitchConst`（`:150`）、`moveSpeedModifier`（`:343`）、`aimingMoveSpeed`（`:177`）—— 全部只有解析。

结果：开镜固定每 tick `+0.2`（`GunActions.java:83`），5 tick 到满，与枪、与瞄具、与玩家移速都无关。"高倍镜开镜更慢"这类设定不会生效。

对照原核心：`GunType.getAimTimeConst()`（`FlansModClient.java:1666-1671`）用 `aimTimeConst` 做指数逼近，`addContribution` 把每个配件的 `aimTimeMult` 乘进去。

### 2.8 次武器（下挂）字段不全 —— P2

原核心 `AttachmentType` 有一整组次武器字段：

```
secondaryAmmo, secondaryDamage, secondaryAimingSpread, secondarySpread,
secondarySpreadWLP, secondaryShotGunAimingSpread, secondaryShotGunSpread,
secondaryShotGunSpreadWLP, secShootSoundRange, secEmptyClickSoundRange,
secondarySpeed, secondaryReloadTime, secondaryShootDelay,
secondaryShootSound, secEmptyClickSound, secondaryReloadSound,
secondaryFireMode
```

移植版只有 `secondaryFire`、`secondaryAmmoNames`、`secondaryDamage`、`secondarySpread`、`secondaryShotGunSpread`，以及借用的 `shootDelay`/`reloadTime`/`reloadSteps`/`reloadTimes`/`reloadSounds`/`emptyClickSound`。

不解析的键：`AimingSpread`、`SpreadWLP`、`BulletSpeed`、`DistortSound`、`TypesNeeded`（这五个是 FMUM/EFT 核心 `AttachmentType` 的键，移植版 `GunType` 有、`AttachmentType` 没有）。

实际影响**很低**：全包扫描中，`attachments/` 目录下这五个键的出现次数均为 0，`SecFire true` 也是 0。属于"字段缺失但内容包没用"，可以缓办。

### 2.9 `SpaceTaken` 是一个错误声明 —— P2（文档/代码质量问题）

`AttachmentType.java:85-86` 的注释写：

```java
/** How much of the gun's space the part occupies, per slot; FMUM's SpaceTaken line. */
```

但 `SpaceTaken` **不存在于** `eft-core-reference.jar`、`FMUM-1710-master`、`Flans-Mod-Ultimate-2.0-1.21.1` 任何一个里（三处 grep 均为空）。

它只存在于内容包：EFT 的 `attachments/` 里有 417 个文件写 `SpaceTaken`。

移植版解析后存进 `spaceTaken`，无任何消费者。任务书里那句"不凭名字猜 SpaceTaken/AttachmentTypesNeeded 语义"正好对上了：这个字段在 1.7.10 核心中根本没有被读过，属于包作者的冗余声明。建议把注释里的 "FMUM's" 删掉，避免后来者误以为需要实现。

### 2.10 条件子模型被无条件绘制 —— P1

`ModelGlock18CSlideBocchi` 有 `lightModel`(4 盒)、`fireModeModel`(6 盒)、`fireModeLightModel`(3 盒) 三个条件子模型。原核心只在 `isFlashlight && lightOn` 或对应射击模式下绘制。移植版 `TurborigItemRenderer:415-421` 用 `RigRenderer.render` 遍历整份 `rig.quads`，三个都会常显。

`InspectBocchiSlide` 实测确认三个都进了 `quadsByPart`。详见 `deepseek-slide-model.md` §4.1。

## 三、参数键的机械对照

| 类型 | 原核心键数 | 移植版键数 | 原核心独有 | 移植版独有 |
|---|---:|---:|---|---|
| `ModifiableType` | 13 | 15 | 无 | `DefaultAttachments`、`Paintjob` |
| `AttachmentType` | 53 | 59 | `AimingSpread`、`BulletSpeed`、`DistortSound`、`SpreadWLP`、`TypesNeeded` | `ExPaintjob`、`FOVZoomLevel`、`HideInCreativeTab`、`MuzzleFlashTime`、`Paintjob`、`ReloadTimeMultiplier`、`ZoomOverlay` |
| `GunType` | 111 | 140 | `DefaultAttachments`、`KnockbackModifier`、`LockOn*`(6)、`MagAimTime`、`MagMoveSpeed`、`MagType`、`MeleeDamageOffset`、`Model`、`Spread`、`TestModel`、`UsableByMechas` | 约 40 个 |

第三行需要说明，避免误报：

- `LockOnTo*` 在移植版位于 `BulletType`（`:173-177`），不在 `GunType`，是**位置移动**不是缺失。
- `MagType` 在移植版是 `case "Mag", "MagType"`（`GunType.java:553`），别名合并。
- `MagAimTime` / `MagMoveSpeed` 在原核心是**错误别名**：`GunType.java:344` 把 `MagMoveSpeed` 写进 `damage`，`:728` 把 `MagAimTime` 写进 `bulletSpeed`。移植版不解析它们反而是对的，但要确认没有内容包依赖这个错误别名。
- `KnockbackModifier` / `KnockbackReduction` / `MeleeDamagePoint` / `MeleeDamageOffset` 在移植版存在，位置在 `ArmourType` / `GunType`。
- `Model`、`Spread`、`TestModel`、`UsableByMechas` 是移植版 `GunType` 缺的；`UsableByMechas` 实际是 `case "UsableByPlayers", "Usab...` 合并写法，需人工确认。

## 四、与 `Master → 1.21.1` 报告的对照

`Flans-Mod-Ultimate-2.0-1.21.1/reports/` 那些报告不能直接套用到本工程：它们是 FMU 2 的审计，血统不同。本工程是独立移植，判断标准是 1.7.10 核心，不是 FMU 2。

## 五、建议的修复顺序

1. **修 `ZoomLevel` 解析**（§2.1）。这是纯解析 bug，改动小、风险低：按 `split[1..2]` 取两个模型名，`split[3..]` 按 (zoom, sensitivity) 成对填两个数组。
2. **接开镜倍率到 FOV 事件**（§2.2）。先做单级：`zoomProgress` 插值 + `FOVZoomLevel[0]`。
3. **补准点渲染**（§2.4）。19 个瞄具受影响，视觉收益最大。
4. **补 Shadow/co-sight/toggle**（§2.6）+ `SWITCH_CO_SIGHT` 操作。
5. **接倍率字段**（§2.7），至少 `aimTimeMult` 与 `moveSpeedMultiplier`。
6. **修条件子模型**（§2.10）。
7. 其余（§2.8、§2.9）属清理项。

## 六、验证边界

**已确认**：§2.1 的字节码语义（`javap` 偏移 2291-2461 逐条核对）、§2.3 的 16 个常量、§2.5/2.6/2.7/2.9 的"无消费者"结论（全项目 grep）、§2.10 的实测（`InspectBocchiSlide` 在 Java 25 上执行）、§3 的三张键表（脚本抽取 + `comm` 比对，已手工排除位置移动与别名）。

**尚不确定**：
- §2.4 的准点缺失在实机有多明显，取决于 19 个瞄具的 `DotModel` 是否含可见几何。
- §3 中 `UsableByMechas`、`Model`、`Spread` 等 6 个键的具体去向，脚本只做了字面比对，未逐个读移植版分支。
- `MagAimTime`/`MagMoveSpeed` 的错误别名是否有内容包依赖，未做全包扫描。

**未运行**：未启动游戏、未构建、未截图。§2.1 的"解析成 0"是静态推导（`toFloat` 对 `"EFTRAtt.PSO1Glass"` 抛 `NumberFormatException` 后返回 0），未在运行时断点验证。
