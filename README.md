# Flan's Mod · NeoForge 1.21.1 非官方移植

把 **Flan's Mod** 从 Minecraft 1.7.10 移植到 **NeoForge 1.21.1**。
行为与代码以 1.7.10（含内容包配套核心）为准，而非 1.20.1 上游。

> **非官方移植**，与 Flan's Mod 原作者及各分支无隶属关系。
> 授权见 [LICENSE-CC-BY-NC-SA.md](LICENSE-CC-BY-NC-SA.md)：**CC BY-NC-SA 3.0**（署名 · 非商业 · 相同方式共享）。

当前本地产物为 **0.4.11**。5.56 弹匣与物品修复见 `reports/eft/5.56x45弹匣与物品修复-0.4.11.md`；已修复多弹种、无序配方、托弹板、弹鼓坐标和测试图标，补齐弹壳开火保护。专项检查及既有 23 组最终 JAR 离线回归通过，5369 个原包文件哈希不变，实机观感待确认。此前瞄具与护甲记录继续保留。

## 它做什么

直接读取 1.7.10 时代的内容包——把包放进游戏目录旁的 `Flan/` 文件夹（散装目录或 `.zip`/`.jar` 均可），
包里的枪械、载具、飞机、护甲、零件就会从同一批 `.txt` 定义与 `.class` 模型类加载出来。

- **不转换、不重打包**：包保持原样，模型类由运行时读取字节码得到
- **物品名与提示走包的 `.lang`**：包自带的 `en_US.lang` / `zh_CN.lang` 会被解析并供语言系统使用，
  文本随游戏语言切换
- **不随包分发内容包**：请自行向各内容包作者获取

## 构建

```bash
./gradlew build          # 产物在 build/libs/
./gradlew runClient      # 开发环境启动客户端
```

需要 JDK 21。

## 内容包

放到游戏目录下的 `Flan/`：

```
.minecraft/Flan/你的内容包/guns/*.txt
.minecraft/Flan/另一个包.zip
```

## 已知范围

- 目标内容包：以 EFT（EscapeFromTarcraft）系列为准做验证
- 载具、队伍/方块/配方等分支仍在补齐中

## 授权与署名

本项目以 **CC BY-NC-SA 3.0** 发布，因为它转写自同样以该协议发布的作品：

- Flan's Mod（原作者 Flan / jamioflan）
- Flan's Mod Ultimate (FMUM) —— 1.7.10 续作分支
- Flan's Mod Ultimate 2.0 —— 1.21.1 分支

**非商业**：不得出售或置于付费墙后。**相同方式共享**：修改版须沿用同一协议。
完整说明与分发注意事项见 [LICENSE-CC-BY-NC-SA.md](LICENSE-CC-BY-NC-SA.md)。

## 开发文档

- `reports/eft/` —— 各功能模块的修复与验证记录（姿势、弹药、材质审计、枪口焰、抛壳、声音等）
- `PORTING_REVIEW.md` —— 较早的 1.20.1 移植阶段的源码审查记录

## Mapping / MDK

NeoForge MDK 使用 Mojang 官方映射名，受其自身许可约束，详见
<https://github.com/NeoForged/NeoForm/blob/main/Mojang.md>。
