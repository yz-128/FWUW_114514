# 原包夜光材质识别与手持、翻折接线

日期：2026-10-22。基线为工作区 0.5.39；本轮未修改原包、1.7.10 工程或版本。正式最终 JAR 的构建和整体报告由主线程统一完成。本页不宣称已运行游戏。

## 真实规则和缺口

1.7.10 `FMUM-1710-master/src/main/java/com/flansmod/client/model/ModelFlan.java` 的 `glowOn()` 调用 `glowOn(15)`，把原 block/sky lightmap 分别加 `glow / 15 * 240` 并封顶 240；`glowOnStatic()` 则直接设为 240/240。`glowOff()` 恢复原值。它不是按贴图、字段或文件名中有没有 `Light` 来决定材质。

旧工程 `Octagon_Weapons/ModelBerettaM9RearSight.java` 提供明确源代码：`render(attachmentModel)` 后调用 `glowOn()`，再 `render(lightModel)`、`glowOff()`。`renderWithoutGlow` 则仍画两组，只不提升 lightmap。

更重要的是当前原包 class 本身的事实（用 javap 与 ASM 读取，不加载执行旧类）：

| 当前原包模型 | 原 `render(float)` | 实际几何 |
| --- | --- | --- |
| `OCRemakeWeapons.ModelBerettaM9RearSight` | body → glowOn → lightModel → glowOff | 重新制作版有 6 个夜光 box / 36 个面，不能拿旧源码中的 2 box 覆盖它 |
| `EFTRWea.ModelMP443LightSight` | body → glowOn → lightModel → glowOff | 9 个夜光 box / 54 个面 |
| `EFTRAtt.ModelGlock18CSlideBocchi` | body / 普通快慢机 → glowOn → lightModel → 按 getFireModeNum 分支 partialRender(fireModeLightModel) → glowOff | 状态 0/1/2 仅画各自 `fireModeLightModel#0/#1/#2` 的 1 个 box，不能把整个指示数组点亮 |

0.5.39 的 `BytecodeModel` 只保留了这些数组的几何，未保留 glow 调用。`TurborigItemRenderer.renderAttachments` 只画 `attachmentModel`，导致夜光组整组漏画；直接手持附件时则可能把全部条件数组按普通光照一起画。GUI 另有自己的预览着色，需由主线程单独接入本次提供的材质查询。

扫描 `run/flan` 中 1122 个原 class，共有 45 个模型的真实 `render(F)V` 含 glow 调用：26 个来自 EFT Reforged，19 个来自 Octagon Reforged。逐条定义 `Model` 行反查后，43 个是附件定义，2 个为当前未引用的原包模型（`OCRemakeAttachments.LMTMK18RearSight`、`OCRemakeWeapons.ARFrontSightGasBlock`）；没有把它们按类名所在 `Wea/Weapons` 目录误归为枪主体，也没有涉及护甲或道具。

## 已实现

- `ModelGeometry.GlowLight(strength, absolute)` 保留相对发光强度与绝对 lightmap 的区别，`apply(packedLight)` 分别处理两个通道；不引入动态世界照明。
- `BytecodeModel` 只解释真实 `render(F)V` 的有限渲染契约：数组 render、partialRender、常量 glowOn(int)、glowOnStatic/glowOff、getFireModeNum、简单整型条件和跳转。未知状态调用或未平衡 glow 直接关闭该类的自动材质识别，并设置诊断标记，避免把未知分支猜成常亮。
- 对原先 byte 类型的 fireModeNum，完整计算 256 个输入值；同时保留同一次解释得到的 `normalDrawsByMode` 和 `emissiveDrawsByMode`，以两者联合 profile 共用不可变快照。没有每帧解释字节码，也不执行旧 Java 类。
- `BakedRig` 将 profile 烘焙为不可变 `EmissivePass`，只保留原 Quad 引用和精确 renderer 范围；范围遍历受实际 renderer 集合限制。普通 body 也从原 `render/partialRender` 调用推导，排除同一模式已经发光绘制的相同 Quad，避免重复。Bocchi 的普通快慢机不是丢弃：模式 0/1 画原 `fireModeModel[0,5)`，模式 2 画 `[5,6)`；非当前模式的夜光指示组保持隐藏。
- `TurborigItemRenderer` 在安装附件与单独手持附件两条入口调用相同 surface helper，继续使用相同 texture、opaque cutout、pose、UV 和 overlay，只有经证明的发光顶点改变 packed light。枪上的递归附件从物品状态传入当前 fireMode，覆盖第一/第三人称、掉落和物品框的对应物品渲染路径。
- `SightFoldRenderer` 的夜光组沿同一刚性几何 track 移动；保留既有 UV 过渡和淡入淡出，材质变化也参与过渡。不把 scope glass、reticle、激光或枪口焰的专用阶段重复加进附件 body。

GUI 可用的查询（均无每帧集合构建）：

```java
boolean BakedRig.hasEmissiveProfile();
ModelGeometry.GlowLight BakedRig.emissiveLight(Quad quad, int fireMode); // identity; null = 普通光照
List<Quad> BakedRig.attachmentBody(int fireMode);
List<BakedRig.EmissivePass> BakedRig.emissivePasses(int fireMode);
// EmissivePass.quads(); EmissivePass.light().apply(packedLight)
```

`attachmentBody` 限于附件普通渲染阶段，不能替代任意枪主体的全部 mesh。缓存随每次 bake 新建，原有 selector 缓存保留；256 项只是 byte 状态索引，相同 profile 的几何集合共用，不随渲染帧数增长。没有发光元数据的 rig 不建立额外的全几何 identity map。

## 验证

独立工具 `tools/offline/VerifyLegacyEmission.java` 已用开发隔离编译的实际生产类执行，34,212 项检查通过，包含：

- 1122 个原 class、45/45 个有实际 glow render 的模型识别，无 unsupported 遗漏；逐模式核对原字段、renderer 范围与 Quad/UV identity。
- 强度 3 的相对光照、原默认强度 15、glowOnStatic、双通道封顶、极端整数、负值与 byte 模式边界。
- 任意名称 `arbitraryPanel` 可由真实 glow 区间识别；名叫 lightModel、scopeGlassModel、laserModel 却没有 glow 调用的字段不被猜成材质。
- 缓存不可变、重新 bake 隔离、普通 body 与发光组不重复；Bocchi 三种模式各自只亮一个指示 box，同时保留当前模式的普通快慢机 renderer 范围和面数。
- 直接调用生产 `TurborigItemRenderer.renderAttachmentSurface`，记录真实 `VertexConsumer` 的每个 packed light、顶点数、UV、overlay；普通 body 保持世界 light，真实 Beretta、MP443、Bocchi 的夜光顶点在黑暗中为满亮。
- 真实 MP7 前准星和 CICIS4 前准星展开/折叠两组，0 / 0.5 / 1 三阶段的实际 `SightFoldRenderer` 顶点记录；发光点随其 track 的同一刚性 pose，端点所有可见 body + light quad 精确绘制一次。

开发日志：`build/emissive-investigation-0.5.40/verify.log`。最终 JAR 仍须从最终产物 classpath 复跑相同工具并记录 JAR SHA256；开发 loose class 的结果不替代最终产物证据。游戏内暗处、镜内、光影开启及资源重载画面验收尚待完成。
