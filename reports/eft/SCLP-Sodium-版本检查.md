# 检查：SCLP 与 Sodium 的版本不匹配（及全开配置复测）

日期：2026-10-22。检查对象：`[钠-汉化模组] sclp-neoforge-3.17.8.jar` 与 `[钠] sodium-neoforge-0.8.13+mc1.21.1.jar`，
以及 `all`（10 个关注模组全开）配置的实际启动/场景情况。

## 一、结论：SCLP 3.17.8 是按 Sodium 0.6.0 构建的，和 0.8.13 不匹配

1. **SCLP 仓库自己写的目标版本**（`要做的兼容/SCLP-1.21/gradle.properties`）：

   ```
   minecraft_version=1.21
   sodium_version=mc1.21-0.6.0-beta.1
   ```

2. **Sodium 0.8.13 的类结构**（真实代码在 jar 内嵌的
   `META-INF/jarjar/net.caffeinemc.sodium-neoforge-0.8.13+mc1.21.1-mod.jar`，727 个类；外层 jar 只是引导器，72 个类）：

   | SCLP 的 mixin 目标 | Sodium 0.8.13 里是否存在 |
   | --- | --- |
   | `OptionPageFrame` | **缺失** |
   | `OptionImpl` | **缺失** |
   | `SodiumOptionsGUI` | **缺失**（改名为 `client/gui/VideoSettingsScreen`） |
   | `SodiumVideoOptionsScreen` | **缺失** |
   | `TabHeaderWidget`（reeses 插件） | **缺失** |
   | `PageBuilderImpl` | 存在（`client/config/builder/PageBuilderImpl`），但字段布局变了 |
   | `ScreenPrompt`、`ControlValueFormatter` | 存在（路径分别是 `client/gui/prompt`、`api/config/option`） |

3. **运行时证据**（`all-probe/gradle.log`）：

   ```
   Compatibility level JAVA_17 specified by sclp.mixins.json is higher than the maximum level supported by this mixin (JAVA_13)
   Write access detected to @Final field name:Lnet/minecraft/network/chat/Component; in sclp.mixins.json:sodium.MixinPageBuilderImpl
   ```

   即 SCLP 的 `required: true` mixin 打在 0.8.13 的 `PageBuilderImpl` 上，但那个字段在这一版是 `@Final`，写入被拒。

**影响范围**：只有 Sodium 的选项页汉化（GUI/mixin）受影响，**不影响渲染器，也不影响本移植版的任何渲染路径**。
`all` 配置仍能正常启动、完成四场景（见下）。

**建议**：把 SCLP 换成为 Sodium 0.8.x 构建的版本（其仓库有 `1.21-1.21.10-New` 等分支，需按 Sodium 版本挑对应 release），
或者在兼容测试环境里先移除 SCLP（矩阵已有 `no-sclp` 配置）。

## 二、`all` 配置复测（0.5.21，10 个关注模组全开）

干净环境下重跑 `runClientAuto -PflansCapture -PflansCompat -PflansPerf`：

- `[Compat] mods={iris,sodium,acceleratedrendering,entityculling,modernfix,ferritecore,lithium,c2me,flerovium,sclp}`
  —— **ferritecore 这次正常加载**，上一轮 `all` 行少一个模组是中断采集留下的 `mods-disabled` 残留造成的，已排除；
- 启动到 `[Compat]` 约 20 秒，`[DebugCompat] Joined` → `done` 约 30 秒，**没有卡死**；
- 改枪 3 帧、ADS 3 帧、手雷 4 帧均按计划触发。

但这一轮发现一个新的**采集链不稳定**问题：该次运行服务端的中段没有执行
（日志里只有 `Kit: ...` 的重复行，没有 `Reload started` / `Trigger down` / `Final`，也没有
`optic scene` / `grenade scene`），导致 ADS `blend=0.0`、没有进入镜内相机。

复核时间线后判断原因是**服务端 tick 严重滞后**而不是重连：客户端在 15:43:23–15:43:30 已经跑完
380–580 的场景 tick，而服务端在 15:43:21 还停留在约 tick 70（`Kit:`/reload 附近），所以 430/500 的服务端
场景根本没到。10 模组全开 + 世界首次加载（区块生成/C2ME/资源）时 TPS 会掉到很低，客户端 tick 与整合服务器
tick 不同步。

结论：采集链要以**服务端 tick** 为准——场景应挂在服务端的 tick 上（或等待 TPS 恢复后再开始），
并在校验里要求出现 `Final`、`optic scene`、`grenade scene` 三行，避免把半截运行当有效数据。

## 三、已修 / 待修

**已修**
- 矩阵脚本依赖闭包（`no-sodium` 自动移除 Flerovium）；
- 矩阵日志改从重定向的 stdout 读取（不被 `latest.log` 文件锁影响）；
- 进程内 `Minecraft.close()` 的 GLFW-after-free NPE 改为采集脚本终止进程树；
- `RenderPerf` 的 `scope ms/frame` 公式（窗口纳秒/窗口帧数）与 fps 分母。


## 附录：C2ME 的 `natives_math` 模块加载失败（外部打包问题，不是本移植版问题）

`c2me-neoforge-mc1.21.1-0.4.0-alpha.0.122.jar` 里内嵌 20 个模块 jar，逐个读入口类的 class major：

| 模块 | 入口类 | 编译目标 |
| --- | --- | --- |
| base / chunkio / scheduling / worldgen / lighting 等 19 个模块 | `*.ModuleEntryPoint` | **Java 21（major 65）** |
| `c2me-neoforge-opts-natives-math-...jar` | `com.ishland.c2me.opts.natives_math.ModuleEntryPoint` | **Java 25（major 69）** |

该模块的 `neoforge.mods.toml` 声明 `modLoader="lowcodefml"`，运行时 C2ME 的 Modules Manager 尝试加载它时报：

```
Error loading module entrypoint: com.ishland.c2me.opts.natives_math.ModuleEntryPoint
java.lang.UnsupportedClassVersionError: class file version 69.0
```

Minecraft 1.21.1 / NeoForge 21.1 的官方运行时是 **Java 21**，所以这一模块在任何正确的 1.21.1 实例里都加载不了；
这是 C2ME 这个 alpha 构建自身的编译/打包问题（一个模块用了 JDK 25 编译），与本移植版无关——移植代码没有引用
C2ME 的任何类。其余 19 个模块照常加载，C2ME 的主优化仍在。

处理建议：把这条记成“环境注记”，不作为冲突；若要 C2ME 完整生效，换一个所有模块都是 Java 21 的 C2ME 构建即可。

**待修（Round 1 之前）**
1. `DebugCapture` 增加"同一玩家重复 join → 重置/记录"逻辑，并在场景校验里要求 `Final`/`optic scene`；
2. SCLP 版本与 Sodium 0.8.13 对齐（或从 dev 环境移除）；
3. 用 0.5.21 修好的公式重跑矩阵，拿到可比的 `scope ms/frame`。
