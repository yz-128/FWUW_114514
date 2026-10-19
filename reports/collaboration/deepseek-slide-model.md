# DS-SLIDE-01 · 原包枪体与滑套模型独立复核

复核人：DeepSeek（ZCode 会话）。日期：2026-10-18（Asia/Shanghai）。
只读复核。工程：`E:\JAVAMCMODGJ\FLAN 移植i\flanmod-template-1.21.1`（0.4.3）。
未修改任何内容包、源码或构建产物。

## 回执

领取 **DS-SLIDE-01**（原包枪体与滑套模型复核）。本报告同时确认交接文档中"Y=17.5、读取器修复保留"的当前状态。DS-SLIDE-02 见 `deepseek-slide-state.md`。

## 方法

1. 直接读 `run/flan/EscapeFromTarcraftReforged_1.5.3/` 里的原始定义 `.txt` / `.df`。
2. `javap -p -c` 反汇编 `ModelBocchiTheGlock18C.class` 与 `ModelGlock18CSlideBocchi.class`，按指令序取构造期变换与常量。
3. 用工程自带的 `tools/offline/InspectBocchiSlide`（编译于 `build/offline/`，Java 25 执行，逻辑类 `build/verification-0.4.3/` 为 Java 21）读出移植侧实际解析结果，与 2 对照。命令与交接文档给出的离线复现方式一致，未启动游戏。

## 一、定义文件事实

`guns/BocchiTheGlock18C.txt`：

```
AttachPoints 0 17.5 0 0 0 0 29 14.5 0
AttachVectors 0 1 0 1 3 3
AttachRotates 0 0 180
MaxAttachments 1 1 1
DefaultAttachments Glock18CSlideBocchi next Crossline
TypeWhitelist Glock18CSlideBocchi next Crossline next flashlight PistolMount generic Grip
```

解读（与 FMUM `ModifiableType.java:95-104` 的解析一致）：

| 槽 | 位置（像素） | 步向量 | 步数上限 | 旋转 | 上限 | 默认挂载 |
|---|---|---|---|---|---|---|
| 0 | (0, 17.5, 0) | 0 | 1 | 0° | 1 | `Glock18CSlideBocchi` |
| 1 | (0, 0, 0) | 0 | 1 | 0° | 1 | `Crossline` |
| 2 | (29, 14.5, 0) | 3/16 | 3 | 180° | 1 | — |

`attachments/Glock18CSlideBocchi.df`：`ItsType bolt`，`Model EFTRAtt.Glock18CSlideBocchi Weapons/BocchiTheGlockDefault 0.2`。
`attachments/Crossline.txt`：`ItsType sight`，`AttachmentType Crossline`，`AimCenter 23`。

滑套的 `ItsType` 是 **bolt**，不是 sight。瞄具挂在槽 1（导轨），不在滑套上。

## 二、模型字节码事实

**枪体 `ModelBocchiTheGlock18C`** 构造结尾（`javap` 偏移 33463-33472）：

```
33464  fconst_0        // 0.0
33465  ldc  #55       // float 0.5f
33467  fconst_0        // 0.0
33468  invokevirtual translateAll(FFF)V
33472  invokevirtual flipAll()V
```

**滑套 `ModelGlock18CSlideBocchi`** 构造结尾（偏移 11473 起）：

```
11473  invokevirtual flipAll()V
11480  invokestatic flip([LModelRendererTurbo;)V   // fireModeModel
11487  invokestatic flip(...)                     // lightModel
11494  invokestatic flip(...)                     // fireModeLightModel
```

滑套**没有** `translateAll`。

**模型标量**（枪体）：`slideLockDistance = 0.625`、`gunSlideDistance = 1.25`、`chargeHandleDistance = 0.625`、三个 `antiRecoil*`、`checkChamberTiltTimeRatio = 0.35` 等。
**模型标量**（滑套）：只设了 `textureX/Y` 与部件计数，**未设** `slideLockDistance` / `gunSlideDistance`。

## 三、移植侧实际解析结果（`InspectBocchiSlide` 输出）

```
TYPE BocchiTheGlock18C scale=0.2
 slot 0 xyz=0.000000,0.218750,0.000000 rotation=0.000000
 slot 1 xyz=0.000000,0.000000,0.000000 rotation=0.000000
 slot 2 xyz=0.362500,0.181250,0.000000 rotation=180.000000
 attached Glock18CSlideBocchi slot=0 pos=0
 attached Crossline slot=1 pos=0
 model=...ModelBocchiTheGlock18C flipped=true translate=0.000000,-0.500000,-0.000000 slide=1.250000
 scalars={... slideLockDistance=0.625, gunSlideDistance=1.25, chargeHandleDistance=0.625 ...}
 part=gunModel boxes=329 quads=1974

TYPE Glock18CSlideBocchi scale=0.2
 model=...ModelGlock18CSlideBocchi flipped=true translate=0,0,0 slide=0.250000
 part=attachmentModel boxes=101 quads=606
 part=lightModel boxes=4 quads=24
 part=fireModeModel boxes=6 quads=36
 part=fireModeLightModel boxes=3 quads=18
```

逐项核对：

| 项 | 期望 | 实测 | 结论 |
|---|---|---|---|
| 槽 0 Y | 17.5/16×0.2 = 0.21875 | 0.21875 | 一致 |
| 槽 2 X / Y | 29/16×0.2=0.3625；14.5/16×0.2=0.18125 | 0.3625 / 0.18125 | 一致 |
| 槽 2 旋转 | 180° | 180 | 一致 |
| 枪体变换 | translate(0,0.5,0) 后 flip → 合成 Y=-0.5 | `translate=0,-0.5,0`, `flipped=true` | **正确**，读取器修复生效 |
| 滑套变换 | 只有 flip，无平移 | `translate=0,0,0`, `flipped=true` | 一致 |
| 动画用值 | FMUM 用枪体的 1.25 / 0.625 | 枪体 1.25 / 0.625；滑套默认 0.25 | 正确（见 §4.2） |

**结论：交接文档声称的"恢复原包挂点 Y=17.5、保留读取器修复"与源码/字节码完全吻合。** 当前运行内容包里没有残留 Y=18.5。

## 四、发现的两个真实缺口

### 4.1 条件子模型被无条件绘制 —— P1

`ModelGlock18CSlideBocchi` 除 `attachmentModel` 外还有三个子模型数组：`lightModel`（4 盒）、`fireModeModel`（6 盒）、`fireModeLightModel`（3 盒）。反汇编显示它们只经过 `flip(...)`，随后挂在独立字段上。

原版只在条件成立时绘制（`RenderGun.java:1893-1896`）：

```java
if(atType.isFlashlight && lightOn && atType.lightModel != null)
    renderStaticGlowOnModel(atType.lightModel);
```

以及 `getFireModeNum()` / `getZoomStep()` / `getReticleNum()` 控制 `fireModeModel` 的当前帧。

移植侧 `TurborigItemRenderer.renderAttachments`（`:415-421`）调用的是 `RigRenderer.render(pose, partBuffer, partRig, ...)`，而 `RigRenderer.render`（`:74-78`）遍历 `rig.quads` —— **整份 rig，不区分部件**。`InspectBocchiSlide` 也确认这三个子模型都进了 `quadsByPart`。

后果：Bocchi 滑套上的**手电光锥模型和快慢机指示模型会一直显示**，不随 `lightOn` 或射击模式变化。这不是静态对齐问题，是可见的渲染错误。

同类风险：`ModelGlock18CSlide` 的 `lightModel`（1 盒）、`fireModeModel`（16 盒）同理。

### 4.2 挂在滑套上的瞄具，ADS 枢轴不跟随滑套 —— P2

原版 `RenderGun.java:1870-1873`：

```java
case ItsType.BOLT:
    GL11.glTranslatef(tempFloat = actualSlideDistance * gunType.modelScale, 0F, 0F);
    if(onScopeLayerRendering == -1) applyTransForSightsOnBolt(currentAt, nextAt + 2, tempFloat);
```

`applyTransForSightsOnBolt`（`:1966-1970`）遍历 `allSightsCoors`，对挂在滑套路径上的瞄具调用 `applyXTrans(transAmount)`（`SightCoor.java:61-62`）。

移植侧：

- **视觉**是对的：`renderAttachments` 对 `bolt` 节点先 `pose.translate(motion.slide*scale,...)`（`:412`），再在同一个 pose 里递归渲染子节点（`:424-425`）。挂在滑套上的模型会跟着动。
- **ADS 枢轴**不对：`LegacySightPose.collect`（`:33-63`）按静态槽位累加坐标，没有任何滑套位移项。`TurborigItemRenderer:225` 把它的结果喂给 `FmumFirstPersonPose.aim`，因此滑套后坐时开镜中心不跟随。

当前 Bocchi 的 Crossline 挂在槽 1（导轨），不触发这个问题。但只要某个包把瞄具挂在 `ItsType bolt` 的配件下（EFT 里有 `scopeIsOnSlide` 的模型字段，`BytecodeModel.java:589` 已解析），就会表现出来。

### 4.3 附带观察：`scopeIsOnSlide` 只解析不使用 —— P3

`BytecodeModel.java:589` 解析 `scopeIsOnSlide`，`ModelGeometry.java:250` 有字段，但全项目无渲染消费者。与原版 `applyTransForSightsOnBolt` 属于同一功能族，可一并实现。

## 五、已确认 / 尚不确定 / 未运行

**已确认**：第一节定义事实、第二节字节码事实、第三节解析结果、§4.1 与 §4.2 的代码路径，均有行号或反汇编偏移。`InspectBocchiSlide` 已在 Java 25 上实际执行并产出上述输出。

**尚不确定**：
- §4.1 在实机中是否被用户可见（需确认 Bocchi 的 `lightModel` 是否有可见几何，或是否需要 `lightOn`）。静态证据只证明"会被无条件提交绘制"。
- 全包范围内有多少模型带条件子模型：一次全量 `javap` 扫描在完成前被中断，未得到总数。Bocchi 与 Glock18CSlide 两例已确认。

**未运行**：未启动游戏、未截图、未做 GPU 渲染验证。§4.1 的"可见错误"是静态推断，实机确认后才能定级为已复现。
