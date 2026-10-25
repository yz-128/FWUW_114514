# Java 25 运行验证（实测：能启动并进入世界）

日期：2026-10-22。问题：我们的项目能不能在 Java 25 上正常运行？

## 一、结论

**能启动、能进入世界**（实测，dev 客户端 + 全部 10 个关注模组）。但：

- 这是 **NeoForge 1.21.1 官方不支持**的组合；
- 本轮只验证到"进入世界"（`[DebugCompat] Joined` 后采集脚本即停止），**四个兼容场景没有在 25 下跑完**；
- 用 25 构建会产出 Java 25 字节码（class major 69），**不能分发给 Java 21 用户**；验证后已用默认 21 重新构建并复核。

## 二、验证方法

给 `build.gradle` 的 toolchain 加了一个受属性控制的开关（默认 21，不改变正常行为）：

```groovy
def flansJavaVersion = (project.findProperty('flansJava') ?: '21').toString().toInteger()
java.toolchain.languageVersion = JavaLanguageVersion.of(flansJavaVersion)
```

然后用 JDK 25（`D:\MC\FWQJAVA`，25.0.2）跑 dev 客户端：

```powershell
.\gradlew.bat --offline -Dorg.gradle.java.installations.paths=D:\MC\FWQJAVA `
  runClientAuto -PflansJava=25 -PflansCapture -PflansCompat --console=plain
```

## 三、结果（`run/logs/latest.log`）

```
JVM identified as Oracle Corporation Java HotSpot(TM) 64-Bit Server VM 25.0.2+10-LTS-69
ModLauncher 11.0.5+main.901c6ea8 starting: java version 25.0.2 by Oracle Corporation
[ModernFix] Total time to load game and open world was 23.135063 seconds
[Compat] mods={iris,sodium,acceleratedrendering,entityculling,modernfix,ferritecore,lithium,c2me,flerovium,sclp}
[DebugCapture] Kit: gun=AK74M(true) ammo=flansmod:5.45x39mm7n39 x60 slot=0
[DebugCompat] Joined; scenes: gunsmith 380-424, ADS 440-484, grenade 500, done at 580
```

- **没有** `UnsupportedClassVersionError`、`NoClassDefFoundError`、崩溃报告或异常；
- 10 个关注模组全部加载（含 ferritecore、SCLP、C2ME）；
- 世界加载 23 秒，玩家进入世界、kit 正常发放。

## 四、产物恢复

测试用 toolchain 25 会改写 `build/classes` / jar 的字节码版本，因此验证完立即用默认 21 重新构建并复核：

- `build/classes` 与 `flansmod-0.5.23.jar` 的 class major 均为 **65（Java 21）**；
- 三个审计（改枪含 GPU 深度 / 瞄具 GPU / 着弹点）全部通过，哈希 `BE9F1766665EADACADD76E4220AC7B8143F2A3BBC49323EA824DDBE1FE2F104B`。

## 五、注意事项

1. 发布、验收、兼容矩阵都应以 **Java 21** 为准；Java 25 只作为“未来运行时”的实验记录。
2. `-PflansJava=<n>` 仅用于实验，默认不设置时行为和以前完全一致。
3. 若要在 Java 25 下把四个场景完整跑完（改枪深度 / ADS 镜内 / 手雷 / 性能），需要让采集循环等到 `[DebugCompat] done` 而不是 `Joined`，约多 30 秒。
