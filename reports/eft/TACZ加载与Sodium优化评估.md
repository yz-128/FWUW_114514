# TACZ 内容包加载与 Sodium 优化评估

日期：2026-10-05。结论：两者都有可借鉴的部分。优先改进 Flan 自己的包索引、缓存命中路径和顶点写入；保持 1.7.10/FMUM 的包格式、模型和动作行为。

本次完成本地源码、当前 Flan 调用链、已装 Sodium JAR 类签名的只读检查，未修改运行代码或重新打包。以下是实施建议，尚未测量实际帧率收益。

## 版本核对

| 项目 | 实际版本与目标 | 对本项目的意义 |
|---|---|---|
| TACZ 本地源码 | 1.1.8-hotfix；Forge 1.20.1 / 47.3.19；Java 17 | 加载架构可借鉴，Forge 注册和资源接口需适配 |
| sodium-dev 本地源码 | Sodium 0.9.2；Minecraft 26.3；NeoForge 26.3 alpha；Java 25 | 与当前目标不同，不能直接编译替换 1.21.1 模组 |
| 当前已装 Sodium | sodium-neoforge-0.8.13+mc1.21.1 | 已经提供部分基础优化，可作为可选批量顶点接口的适配目标 |
| 当前 Flan | 0.4.0；NeoForge 21.1.250 / Minecraft 1.21.1；Java 21 | 优化应保留现有注册 ID、包优先级和渲染通道 |

版本证据：[TACZ gradle.properties](<E:/JAVAMCMODGJ/FLAN 移植i/TACZ_看看他是怎么加载内容包的/TACZ-1.20.1/gradle.properties:3>)、[Sodium BuildConfig](<E:/JAVAMCMODGJ/x小地图/sodium-dev/buildSrc/src/main/kotlin/BuildConfig.kt:4>)、[Sodium 实际 Java toolchain](<E:/JAVAMCMODGJ/x小地图/sodium-dev/buildSrc/src/main/kotlin/multiloader-base.gradle.kts:9>)。Sodium README 中 Java 21 的说明与这个本地源码的构建配置不一致，以构建配置为准。

当前运行日志记录 8 个包、1,217 条定义、860 个注册物品。当前顶层启用包均为文件夹；ZIP 优化主要帮助之后使用压缩包的场景。这些是现有目录和日志快照，不是本轮性能测试结果。

## TACZ 实际如何加载内容包

1. `GunPackLoader` 扫描游戏目录 `tacz/`，读取目录或 ZIP 中的 `gunpack.meta.json`，校验依赖版本，将内容包装成资源包并聚合为必选资源源。
2. 通过 Minecraft ResourceManager 按资源 ID 查找 JSON、纹理、模型和声音。普通数据使用 `prepare → apply` 重载流程；`prepare` 读取 JSON，`apply` 清缓存并反序列化。不能把它理解为所有加载都已移到后台。
3. 模型和动画等重资产由 `LazyJsonDataManager` 建立 ID 清单，首次使用时加载并缓存；失败结果也缓存，直到重载清理。指定的内置资源可提前加载。
4. 客户端重建索引，并预热玩家持有、快捷栏和背包中的模型。未预热的模型仍可能在使用时同步加载或等待任务完成，懒加载本身不能保证切枪流畅。
5. 服务端加载逻辑数据并向客户端同步 JSON 数据映射。该同步不包含完整 ZIP、贴图、模型和声音，也不传 Lua 脚本。

主要证据：[GunPackLoader](<E:/JAVAMCMODGJ/FLAN 移植i/TACZ_看看他是怎么加载内容包的/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/GunPackLoader.java:66>)、[JsonDataManager](<E:/JAVAMCMODGJ/FLAN 移植i/TACZ_看看他是怎么加载内容包的/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/manager/JsonDataManager.java:48>)、[LazyJsonDataManager](<E:/JAVAMCMODGJ/FLAN 移植i/TACZ_看看他是怎么加载内容包的/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/manager/LazyJsonDataManager.java:65>)、[ClientIndexManager](<E:/JAVAMCMODGJ/FLAN 移植i/TACZ_看看他是怎么加载内容包的/TACZ-1.20.1/src/main/java/com/tacz/guns/client/resource/ClientIndexManager.java:48>)、[同步消息](<E:/JAVAMCMODGJ/FLAN 移植i/TACZ_看看他是怎么加载内容包的/TACZ-1.20.1/src/main/java/com/tacz/guns/network/message/ServerMessageSyncGunPack.java:24>)。

Flan 的资源包入口已经采用类似的 `RepositorySource → Pack → PackResources` 结构，见 [ContentPackResources](<E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1/src/main/java/com/flansmod/common/ContentPackResources.java:38>)。值得继续补齐的是统一索引、缓存生命周期、按需加载与预热，不需要重写已有入口或转换旧包为 TACZ JSON。

### 热加载的边界

TACZ 固定注册通用枪械、弹药和附件物品，具体枪型以数据 ID 存在 ItemStack 中，见 [ModItems](<E:/JAVAMCMODGJ/FLAN 移植i/TACZ_看看他是怎么加载内容包的/TACZ-1.20.1/src/main/java/com/tacz/guns/init/ModItems.java:19>)。因此它可以重载新增枪型数据。

Flan 当前按每个旧包定义独立注册物品，见 [ContentRegistry](<E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1/src/main/java/com/flansmod/common/ContentRegistry.java:50>)。适合先支持已有物品的模型、贴图、声音和派生缓存重载；增删注册物品仍需重启。改成 TACZ 通用物品体系会涉及存档、注册名、组件和命令迁移，不属于本轮优化的必要部分。

## Sodium 哪些优化能用于 Flan

| 机制 | 当前是否已覆盖 | 适用方式 |
|---|---|---|
| 地形区块批次、区域 GPU 缓冲和可见性 | 由已安装的 Sodium 管理世界地形 | 继续使用独立模组；其地形数据结构不适合直接用于手臂、枪械动画和光影后处理 |
| 矩阵变换减少临时对象 | 当前 `addVertex(matrix, …)` 已会经过已装 Sodium 的对应 Mixin | 避免重复实现同一层优化 |
| 原版方盒模型快速写入 | 接管 `ModelPart.Cube.compile`；Flan 的自定义 Quad 不经过这个入口 | 借鉴连续顶点存储；EFT 变形盒应保留实际四个顶点，不套用普通方盒角点算法 |
| 批量顶点写入 | 当前 RigRenderer 逐属性链没有调用 bulk writer | 可按动画部件准备顶点块，通过可选适配批量提交；不支持的 Consumer 保留现有路径 |
| 内存和缓冲复用 | Flan 有几处可独立改进 | 优先复用光影枪口焰缓冲、减少每帧资源查询和对象分配 |

Sodium 源码证据：[VertexBufferWriter](<E:/JAVAMCMODGJ/x小地图/sodium-dev/common/src/api/java/net/caffeinemc/mods/sodium/api/vertex/buffer/VertexBufferWriter.java:33>)、[BufferBuilderMixin](<E:/JAVAMCMODGJ/x小地图/sodium-dev/common/src/main/java/net/caffeinemc/mods/sodium/mixin/core/render/immediate/consumer/BufferBuilderMixin.java:52>)、[CubeMixin](<E:/JAVAMCMODGJ/x小地图/sodium-dev/common/src/main/java/net/caffeinemc/mods/sodium/mixin/features/render/entity/CubeMixin.java:60>)、[矩阵优化](<E:/JAVAMCMODGJ/x小地图/sodium-dev/common/src/main/java/net/caffeinemc/mods/sodium/mixin/features/render/immediate/matrix_stack/VertexConsumerMixin.java:25>)、[地形渲染器](<E:/JAVAMCMODGJ/x小地图/sodium-dev/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.java:53>)。

已装 0.8.13 JAR 的内嵌 `META-INF/jarjar/net.caffeinemc.sodium-neoforge-0.8.13+mc1.21.1-mod.jar` 经类签名检查，具有：

```java
VertexBufferWriter.tryOf(VertexConsumer, VertexFormat)
VertexBufferWriter.canUseIntrinsics(VertexFormat)
VertexBufferWriter.push(MemoryStack, long, int, VertexFormat)
```

这里的 VertexFormat 是 1.21.1 的 `com.mojang.blaze3d.vertex.VertexFormat`。本地 0.9.2 已换用 RenderPearl API，且 `tryOf` 只有 Consumer 参数。后续适配应针对当前已装版本，缓存接口探测结果，检测实际 Consumer 和格式支持，并保留未安装 Sodium、Iris 包装 Consumer、特殊格式等情况下的回退。

## 当前 Flan 最值得先改的六处

| 顺序 | 已确认的重复工作 | 实施建议与保留语义 |
|---|---|---|
| 1 | `Textures.forType` 每次构造资源 ID 并探测旧皮肤路径；`ModelCache.rigFor` 在缓存命中前仍解析贴图、遍历模型 parts 计算声明尺寸、拼接缓存 key | 缓存最终 rig/texture/静态渲染信息，使每帧命中直接返回。保留 skins 优先级、单轴 UV 修正，以及玩家皮肤差异 |
| 2 | 启动模型统计调用无缓存 `ModelCache.load`；后续生成资产才经 `rigFor` 缓存模型。MISSING 记录缺失，但加载入口没有据此短路查询 | 可用性统计与渲染共用 geometry 缓存；同一资源代次内缺失模型只查一次。不能永远缓存失败，重载后需要重新允许查询 |
| 3 | 文件夹大小写回退反复列目录；图标与语言扫描分散；GeneratedAssets 的磁盘和内存构建分别 generate | 建立一次有序文件索引，共用根路径与资源来源；按资源代次复用生成资产快照。当前 ZIP 资源已有大小写索引，不能把已有优化再重复实现 |
| 4 | 默认附件树每帧按名称线性查 AttachmentType，再解析 rig 和贴图；InfoType 的 Item/名称查询也线性遍历 | 预解析附件绘制树和查找 Map。保留嵌套挂点、槽位旋转、移动位置、bolt/charginghandle/piston 独立动作与当前重复定义赢家 |
| 5 | 弹匣绘制分别读取顶弹、总数和每个可见弹位；底层会重复复制标签、解析 ItemStack 并扫描列表 | 每次绘制只解析一次弹药快照。保留混装顺序、旧存档计数兼容、独立膛内弹和 30+1；跨帧缓存需由真实组件变化失效 |
| 6 | Quad 使用多层 float 数组，逐顶点逐属性写入；光影枪口焰有效帧创建并销毁专用 native buffer | 先连续化顶点数据、复用缓冲，随后比较原路径与可选 Sodium 批写路径。保留动画 part、镜像绕序、法线、UV、光照和 overlay |

证据：[Textures](<E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1/src/main/java/com/flansmod/client/render/Textures.java:84>)、[ModelCache 缓存命中前的工作](<E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1/src/main/java/com/flansmod/client/model/loader/ModelCache.java:203>)、[启动模型统计](<E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1/src/main/java/com/flansmod/common/FlansMod.java:137>)、[文件夹查找](<E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1/src/main/java/com/flansmod/common/FolderPackResources.java:73>)、[生成资产](<E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1/src/main/java/com/flansmod/common/GeneratedAssets.java:167>)、[附件递归绘制](<E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1/src/main/java/com/flansmod/client/render/TurborigItemRenderer.java:386>)、[弹匣绘制](<E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1/src/main/java/com/flansmod/client/render/TurborigItemRenderer.java:475>)、[顶点写入](<E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1/src/main/java/com/flansmod/client/render/RigRenderer.java:106>)、[光影枪口焰缓冲](<E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1/src/main/java/com/flansmod/client/render/ShaderMuzzleRenderer.java:82>)。

需要准确区分已有能力：TextureSizes 已缓存 PNG 头部尺寸，不是每帧解码 PNG；ShaderMuzzleRenderer/ShaderSmokeRenderer 已缓存 RenderType；默认附件递归没有逐附件显式 endBatch，不能直接称为“一附件一 draw call”；当前默认附件树不代表完整动态改枪系统已完成。

### 缓存生命周期与实施顺序

当前 ModelCache.clear 和 TextureSizes.clear 存在，但生产源码未找到调用方，也未找到资源重载监听器注册。所有新增缓存应由同一重载代次控制，避免重载后旧贴图尺寸、旧 rig 和缺失结果残留。

建议顺序：统一缓存生命周期 → 最终渲染信息与缺失缓存 → 包文件索引与模型解析复用 → 附件/弹匣绘制快照 → 连续顶点与缓冲复用 → 可选 Sodium 批写。后台加载和预热放在共享缓存稳定之后，CPU 字节码解析可以准备新快照，依赖 ResourceManager 的资源绑定和 GPU 操作按正确线程提交。不能把现有可变模型 Map 直接交给多线程共同写入。

烟雾和枪口焰保持现有世界/手部/GUI 后处理阶段、透明排序与双面 NO_CULL。尤其保留 Iris 烟雾的 BLOCK 格式和透明渲染通道；不同渲染阶段不能仅为减少调用而合并。世界烟雾与弹壳后续可添加包围盒/距离可见性检查，跳过绘制时仍正常更新效果生命周期。

实施后的验证应比较：启动模型解析次数、资源查找次数、每帧分配量、顶点提交 CPU 时间、帧时间分布；同时检查无 Sodium、仅 Sodium、Sodium+Iris 三种路径的枪/附件/双臂/弹匣、镜像、换弹、烟雾和枪口焰。当前没有这些基准结果，不给出 FPS 提升百分比。

## 源码复用与本轮产物

TACZ README 标明代码 GPL 3、素材 CC BY-NC-ND 4.0；Sodium 本地源码为 PolyForm Shield 1.0.0，包含分发通知和 Noncompete 条款；当前 Flan 声明 CC BY-NC-SA 3.0。后续优先参考设计独立实现或使用可选 API；如复制源码或素材分发，应分别核对其具体许可证，不能把它们作为同许可证代码直接合入。证据：[TACZ License 声明](<E:/JAVAMCMODGJ/FLAN 移植i/TACZ_看看他是怎么加载内容包的/TACZ-1.20.1/readme.md:45>)、[Sodium LICENSE](<E:/JAVAMCMODGJ/x小地图/sodium-dev/LICENSE.md:30>)。

按项目习惯建立评估前源码快照：`E:\JAVAMCMODGJ\FLAN 移植i\BACKUP_before_tacz_sodium_review_20261005_140212`。本轮只新增此报告，两个参考仓库和游戏目录均未修改，未启动或操作游戏。

现有 `build/libs/flansmod-0.4.0.jar` 保持 451,103 字节，SHA-256：`E84DCA24BDEFA1B9E15E54A2508F562F223DEEA66D02FA8E07B1A30A0F28A3E0`。
