# Flan's Mod 1.21.1 NeoForge 移植 · 中文本地化说明

## 范围

为 8 个模组新增简体中文（`zh_cn`）语言文件，**共 1075 条**，全部人工翻译。

| 文件 | 条目数 | 内容 |
|---|---|---|
| `assets/flansmod/lang/zh_cn.json` | 298 | 核心 UI：工具提示、工作台、制造、按键、修正符 |
| `assets/flansbasicparts/lang/zh_cn.json` | 255 | 部件、材料、能力（Perk）描述 |
| `assets/flansvendersgame/lang/zh_cn.json` | 203 | 厂商枪械、涂装、弹匣、NPC 对话 |
| `assets/flansworldwars/lang/zh_cn.json` | 170 | 二战枪械、载具、军服、弹匣 |
| `assets/flansteams/lang/zh_cn.json` | 90 | 队伍系统、构建区命令反馈 |
| `assets/flansclockworkcalamity/lang/zh_cn.json` | 33 | 发条内容包 |
| `assets/flansphysicsmod/lang/zh_cn.json` | 16 | 物理检查器 |
| `assets/flanmod/lang/zh_cn.json` | 10 | 模板残留项 |

**未改动任何英文（`en_us`）或其它语言文件** —— 经逐个比对与原版 1.20.1 一致。

## 术语表（保持一致性）

| 英文 | 中文 | 说明 |
|---|---|---|
| Gun / Bullet / Magazine | 枪械 / 子弹 / 弹匣 | |
| Attachment | 配件 | 握把/枪托/枪管/瞄具的统称 |
| Receiver | 机匣 | upper/lower receiver |
| Grip / Stock / Barrel / Sights | 握把 / 枪托 / 枪管 / 瞄具 | |
| Recoil / Spread / Knockback | 后坐力 / 散布 / 击退 | |
| Craft / Fabrication | 制作 / 制造 | 工作台专属配方用「制造」 |
| Paintjob / Paintcan | 涂装 / 喷漆罐 | |
| Loadout | 配装 | |
| Construct | 构建区 | 队伍地图编辑区 |
| Tier | 级 | 材料等级 |

## 格式符安全（重要）

Minecraft 用 `TranslatableContents.FORMAT_PATTERN` 解析 `%<序号$><[A-Za-z%]|字符串结尾>`。

中文句子常以 `%` 结尾（如「伤害提高 5%」），而**字符串末尾的 `%` 会匹配该模式**：

- MC 抛出 `TranslatableFormatException` → 内部捕获 → 回退为原文
- 正确写法是 `%%`（转义为字面 `%`），渲染结果完全相同，但不会走异常路径

本项目所有中文文件已按此处理（`60%%` 这类）。英文原文件保留上游的裸 `%` 写法，**未改动**。

### 校验方法

`tools/verify_lang.pl` 会：
1. 逐键比对 `zh_cn` 与 `en_us` 的键集合
2. 用 MC 的真实正则提取格式符序列并比对
3. 把 `%%` 视为不消耗参数的转义，把裸尾 `%` 视为字面量

结果：**1075 键，0 缺失，0 格式符不匹配。**

```bash
perl tools/verify_lang.pl
```

## 已知未翻译区域

- 物品/定义的**名称**多来自数据包的 `name` 字段而非语言文件，因此部分枪械名仍显示英文
- `flansmodernweapons` 内容包在**上游就是空的**（0 个资源文件，133 个物品注册无定义），其条目无法翻译
- 上游缺失定义/模型的 151 个物品同样没有可翻译的名称
