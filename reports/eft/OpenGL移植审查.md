# OpenGL 移植审查：1.7.10 固定管线 → NeoForge 1.21.1 Blaze3D

日期：2026-10-21（审查对象：当前工作树，`mod_version=0.5.10`）。
方法：全仓 grep + 逐个阅读 16 个渲染类 + 当前 GPU 审计实测。**未启动游戏、未操作输入**；报告只读，不改代码。

## 一、结论摘要

| 项目 | 状态 |
| --- | --- |
| 固定管线（`glBegin/glEnd`、`glPushMatrix`、`glTranslatef`、`glRotatef`、`glColor/glTexCoord/glVertex`、`glEnable/glDisable`、显示列表） | **已完全移除**：全仓 0 处调用 |
| `IItemRenderer` / `ModelBase` 运行时依赖 | **已移除**：仅注释里作为 1.7.10 对照出现（`ClientExtensions:18`、`TurborigItemRenderer:41`、`BytecodeModel:404`） |
| 现代渲染栈覆盖 | 25 个文件引用 Blaze3D、13 个用 `PoseStack`、9 个用 `VertexConsumer`、16 处 `RigRenderer.render` 调用点、**7 个自定义 `RenderType`** |
| 残留原生 GL | **仅 3 个文件**，全部在镜内 FBO 路径，属状态查询/恢复与 FBO 拷贝，**不是固定管线**（详见第三节） |
| 手写 GLSL | **0 个**（无 `.vsh/.fsh`，全部走 vanilla shader program） |
| 模型来源 | 1.7.10 `ModelBase` 字节码 → `ModelGeometry` → `BakedRig`，`glScalef` 与 1/16 比例在烘焙期折入几何 |
| 镜内世界 | 独立 `RenderTarget` 二次渲染 + `glCopyTexSubImage2D` 动态贴图；Iris/Fabulous 下自动降级单相机 |
| 当前审计（0.5.10） | `RunOpticsAudit -GpuChecks` 通过、`RunImpactAudit` 通过，均哈希 `28ECC5BC…` |

一句话：**渲染后端已经完整迁移到 1.21.1 的 Blaze3D/VertexConsumer 模型，唯一保留的原生 GL 是镜内 FBO 的状态保存与 framebuffer 拷贝，这属于设计选择而非遗留。**

## 二、逐项对照

| 1.7.10 机制 | 1.21.1 替代 | 证据 |
| --- | --- | --- |
| 立即模式 `glBegin(GL_QUADS)` + `glVertex/glTexCoord` | `VertexConsumer.addVertex(...).setUv/.setColor/.setLight/.setOverlay/.setNormal`，每 quad 两三角 | `render/RigRenderer.java:106-116` |
| 矩阵栈 `glPushMatrix/glTranslatef/glRotatef/glScalef/glPopMatrix` | `PoseStack.pushPose/mulPose/translate/scale/scale`；1.7.10 渲染器内联的 `glScalef` 折入烘焙（如 0.5 比例） | `ModelGeometry.java:16-19,137`、`ModelCache.java:402` |
| `Render`/`RenderManager` 注册实体渲染 | `EntityRenderer` + `EntityRenderersEvent.RegisterRenderers` | `ClientExtensions.java:93-98`、`EntityBulletRenderer`/`GrenadeRenderer`/`GunItemRenderer` |
| `IItemRenderer` 五个 item 类型 | `IClientItemExtensions` + BEWLR `TurborigItemRenderer.renderByItem` | `ClientExtensions.java:18-67`、`TurborigItemRenderer.java:146-230` |
| 手持/第三人称的 1.7.10 GL 变换（RenderGun EQUIPPED、RenderPlayer 手臂链） | 用矩阵运算**反向重建**旧帧：`FmumFirstPersonPose`、`LegacyThirdPersonPose`、`applyLegacyTransform`（含 GROUND 偏移与 grenade/plane/vehicle 特例） | `TurborigItemRenderer.java:959-1135` |
| `ModelBase.render(..., 0.0625F)` | ASM 读 class 字节码 → `ModelGeometry`（box/UV/pivot/旋转点）→ 烘焙 `BakedRig`；纹理尺寸不符时用 `TextureSizes` 修正 UV | `loader/BytecodeModel.java`(852 行)、`loader/ModelGeometry.java`(358 行)、`render/TextureSizes.java` |
| 镜像缩放时 GL 自动处理绕序 | 显式反转 quad 顶点顺序（`mirrored` 参数） | `RigRenderer.java:57-99` |
| `bindTexture` + 直接文件路径 | `ResourceLocation` + `RenderType`/`TextureStateShard`；内容包资源由 `ContentPackResources` 暴露 | `Textures.java`、各 `render/*Renderer` |
| 手写 GLSL/固定光照 | 全部 vanilla shader（`RENDERTYPE_*_SHADER`/`POSITION_COLOR_SHADER`/`GameRenderer::getPositionTexColorShader`）；无自定义 shader 程序 | `MuzzleGlowRenderer.java:11-16`、`ScopeRenderLayers.java:18-23`、`ScopeWorldTexture.java:51-55`、`ShaderMuzzleRenderer.java:33-38`、`ShaderSmokeRenderer.java:17` |
| lightmap 手动设置 | 每顶点 `.setLight(light)`，全亮用 `0xF000F0`，动态枪口光 `MuzzleLighting` | `RigRenderer.java:110-116`、`MuzzleLighting.java` |
| 瞄准镜内画面：再次渲染到 FBO/贴图 | 独立 `TextureTarget`（带深度/模板）+ `GameRenderer.renderLevel` 二次渲染 + `glCopyTexSubImage2D` 上传到 `AbstractTexture`；单相机路径直接拷贝 read framebuffer 中央方形 | `ScopeWorldTexture.java:66-141,201-231` |
| `EntityFX` 粒子（枪口焰/烟/曳光/弹壳） | 网格特效（`MuzzleEffectsRenderer`、`ShaderMuzzleRenderer`、`ShaderSmokeRenderer`）+ 服务器粒子；弹壳为实体 | `render/MuzzleEffectsRenderer.java`、`render/ShaderMuzzleRenderer.java` |
| `GuiIngame`/`FontRenderer` HUD | `RenderGuiLayerEvent` + `GuiGraphics`（弹药层注册、准星取消） | `client/GunHud.java:26-45` |
| `ModelBiped` 盔甲 | `LegacyArmourModel extends Model`，用 `HumanoidModel` 实时关节驱动原模型数组；`RenderType::armorCutoutNoCull` | `render/LegacyArmourModel.java:15-45` |
| 改枪 GUI 的 GL 绘制 | `GuiGraphics.fill/drawString/enableScissor`；3D 预览 `PoseStack` + 手动 `RenderSystem.enableDepthTest`（配对关闭） | `gui/GunModificationScreen.java`、`gui/GunModificationPreview.java:153-172` |
| 光学合成的深度语义（0.5.9） | `ScopeRenderLayers.composite`：LEQUAL 读镜筒深度、光学层不写深度，结束后恢复原比较函数/开关 | `render/ScopeRenderLayers.java:26-31` |

## 三、仅存的 3 处原生 GL 调用（逐个定性）

### 1. `render/ScopeWorldTexture.java`（GL11/GL12/GL13/GL14/GL30）
用途分四类，全部是核心 profile 合法调用，且都是"保存/恢复"或"FBO↔贴图"：

- **状态快照**：`glGetInteger(GL_TEXTURE_BINDING_2D)`、`glIsEnabled(GL_BLEND/SCISSOR/DEPTH_TEST/CULL_FACE)`、`glGetBoolean(GL_DEPTH_WRITEMASK)`、`glGetIntegerv(GL_VIEWPORT/GL_SCISSOR_BOX)`、`GL_DEPTH_FUNC`、`GL_BLEND_SRC_RGB…`（81-102 行）；
- **FBO 绑定/解绑**：`GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER/READ_FRAMEBUFFER, …)`（125-126 行）；
- **framebuffer→贴图拷贝**：`GL11.glCopyTexSubImage2D`（217 行）——这正是 1.7.10 时代的写法，保留是刻意设计：单相机路径下把"current read framebuffer 的中央方形"直接抄进镜内贴图，避免再渲染一次；
- **纹理参数/上传**：`glTexParameteri`（LINEAR/CLAMP_TO_EDGE）、`TextureUtil.prepareImage`、`TextureUtil.writeAsPNG`（仅诊断）。

结论：**不是固定管线**，是 FBO 路径必需的底层状态管理；结束后逐项恢复（135-141 行）。

### 2. `render/ScopeRenderLayers.java`
只做三件事：读当前深度开关/写掩码/比较函数（`glIsEnabled`/`glGetBoolean`/`glGetInteger`），用 `RenderSystem` 设置 LEQUAL + 禁写，draw 后恢复。**没有绕过 Blaze3D 的写入**。

### 3. `render/ShaderMuzzleRenderer.java`
仅一行 `GL11.glIsEnabled(GL11.GL_DEPTH_TEST)`（76 行）用于判断是否需在整屏闪光后恢复深度测试。

## 四、风险清单（按优先级）

| 级别 | 风险 | 说明 / 现有缓解 |
| --- | --- | --- |
| 中 | **双相机二次渲染** | ADS 时 `mc.gameRenderer.renderLevel` 再跑一遍整关卡（`ScopeWorldTexture:113`）。状态保存/恢复很完整，且 `dualRenderEnabled()` 在 Iris 光影或 Fabulous 下自动关闭；异常时 `dualFailed` 回退单相机。但性能成本是"ADS 时每帧两次世界渲染"，低端机/大视野下值得实测 |
| 中 | **`mc.mainRenderTarget` 字段交换 + `GlStateManager._glBindFramebuffer`** | 属于 Blaze3D 内部/易变面；1.21.1 可用，但 1.21.2+ `RenderTarget`/`GameRenderer` 有 API 变化，升级时必须迁移 |
| 中 | **无"开启光影包"的画面级回归** | 只有反射探测（`Class.forName("net.irisshaders.iris.api.v0.IrisApi")`）+ 单相机回退；没有在真实 shaderpack 下跑 GPU 像素验证 |
| 低-中 | **`RenderSystem.getModelViewStack()`** | 仅 `ScopeWorldTexture:103`、`ShaderMuzzleRenderer:79` 两处；该 API 在 1.21.2 被移除/迁移，升级需改 `PoseStack` |
| 低 | **`glCopyTexSubImage2D` 依赖当前 read framebuffer** | Iris shadow pass 用 `shadowPass()` 守卫（`ScopeWorldTexture:181-185`）；其余情况有 `failed` 回退 |
| 低 | **FIXED（展示框）上下文** | `applyLegacyTransform` 只对 GROUND 做偏移（`TurborigItemRenderer:1048-1057`），且只覆盖 gun/grenade/plane/vehicle；物品展示框里的位置未单独考据 |
| 低 | **镜内贴图过滤** | `GL_LINEAR` + clamp，无 mipmap；高倍率下可能有轻微闪烁（0.5.9 的深度遮挡验证覆盖了内容，不覆盖闪烁） |
| 低 | **每帧重发几何** | `RigRenderer` 每帧把所有 quad 写进 `VertexConsumer`（与 1.7.10 相同），未做静态 VBO 缓存；大量枪械同屏时有 CPU 开销 |

## 五、验证覆盖（当前工作树）

- **GPU 像素级**：`RunOpticsAudit.ps1 -GpuChecks`（隐藏 GLFW 上下文）——208 组瞄具组合、`VerifyScopeZoom` 66 项倍率声明、22 组真实挂装对照；另有 0.5.9 新增的 `VerifyScopeDepthOcclusion`（旧版 12/12 失败 → 0.5.9 12/12 通过，74 checks）。
- **模型/变换**：`VerifyLegacyRender`、`VerifyModelTransforms`、`VerifySightFoldGeometry`、`VerifyScopeFramebuffer`、`VerifyScopeComposition`、`VerifyReticleResources`、`VerifyShaderMuzzleLighting`、`VerifyMuzzleEffects`、`VerifyMuzzleGlow`。
- **物品/图标**：`VerifyItemIcons`（含皮肤兜底与占位图）。
- **本次审查实测（0.5.10）**：
  - `RunOpticsAudit -GpuChecks` → PASS，`SHA256=28ECC5BC8254AE578C42F224AB304CD27B199EEB68399B38380F9BB3B271961D`；
  - `RunImpactAudit` → 7 组通过，同一哈希。
- 版本产物：`flansmod-0.5.10.jar`（822654 B）/ `flansmod-0.5.10-classic.jar`（812907 B）；两者**渲染后端完全相同**，classic 只替换改枪界面与语言资源（见 `build.gradle` 的 `classicUi` sourceSet 与 0.5.10 报告）。

## 六、建议

1. **保持现状即可**：渲染后端已经完成迁移，没有回退到固定管线的必要；继续新增特效时沿用 `RenderType.create` + `VertexConsumer`，不要再引入 `GL11` 直接写状态。
2. **升级 1.21.2+ 前**先做三处迁移：`getModelViewStack` → `PoseStack`、`RenderTarget`/`TextureTarget` 构造、`mc.mainRenderTarget` 访问；
3. **给镜内路径补一个真实 shaderpack 冒烟测**（Iris + 一个光影包，跑 `VerifyScopeComposition`/深度遮挡的 GPU 用例），把"单相机回退"从代码路径变成验证过的行为；
4. 如做性能优化，优先把 `BakedRig` 缓存为静态 `MeshData`（一次上传、每帧只提交矩阵），而不是每次重发顶点；
5. 物品展示框（FIXED）如果要支持，给 `applyLegacyTransform` 补一条 FIXED 分支并加一条离线断言即可。
