# Flan's Mod 1.20.1 → 1.21.1 NeoForge 移植 · 源码审查报告

审查日期：2026-10-01
原项目：`FlansModReloaded-1.20`（只读基准）
移植版：`flanmod-template-1.21.1`

**验收标准（用户定义）**：客户端 + 服务端均可启动，且正常使用不崩溃。

**当前验收状态：通过（存在上游内容缺口）。** 客户端进世界、服务端启动均为零 ERROR；`clean build` 从零可构建。

**修订 2（本轮）**：用户在游玩中报告"部分物品材质缺失、所有物品功能缺失、没有汉化"。逐条核查结论：

| 反馈 | 判定 | 证据 |
|---|---|---|
| **所有物品功能缺失** | **移植版错误（严重，已修复）** | 根因：`data/<ns>/recipes/`、`loot_tables/` 目录仍是**复数**，而 1.21 只认单数 `recipe/` `loot_table/`。未知目录被**静默忽略、无任何日志** —— 226 个配方与 16 个战利品表全部未加载，表现为所有工作台配方为空、无法制造任何物品。已重命名目录，并连带修复配方格式（§3.6）。实测：加载配方由 0 → 223，游戏内 `Loaded 1513 recipes` |
| **部分物品材质缺失** | **上游遗留（非移植引入）** | 写脚本对全部 425 个注册物品做交叉核对：**151 个缺少定义文件与模型文件**。用同一脚本核对原版 1.20.1，**缺少的数量与清单完全相同（151 个，差集为 0）**。分布：`flansmodernweapons` 132 个（该内容包在**两个版本中都是空目录**，0 个文件）、`flansclockworkcalamity` 13、`flansworldwars` 6。其中 3 个（`m1_carbine`/`m1_garand`/`type38_sniper`）是数据文件名与 Java 注册名不一致（`m1carbine.json` vs `m1_carbine`），原版同样如此 |
| **没有汉化** | **上游遗留** | 原版 1.20.1 仅有 `en_us` / `de_de` / `ru_ru`，无 `zh_cn`。全项目 824 条英文本地化条目，需从零翻译 |

**本轮另发现并修复的移植 bug**（详见 §3.6/§3.7）：

1. **配方目录复数** —— 226 个配方 + 16 个战利品表全部未加载（静默失败，最严重）
2. **配方 `result` 格式** —— 1.20.5 起 `result` 要求 `"id"` 键，而本移植 226 个配方仍写 `"item"`；另有 4 个熔炼配方用字符串形式，1.21 要求对象形式
3. **`StackedVanillaIngredient` 的 `items` 字段被写成必填** —— 原版为可选（只按 `tags` 匹配的配方会解析失败）
4. **配方中残留 `"type": "minecraft:item"`** —— 1.21 无此 ingredient 类型，需删除该行以回退到默认解析
5. **`TurboRigWrapper` 回退查找变体错误** —— 我用 `standalone` 查普通物品模型，应为 `inventory`（我在上一轮引入的回归）

修完上述 5 项后，剩余 3 个配方仍失败，但其引用的是 `phantek_mamba` / `hm_flashlight` 等**在 Java 中被注释掉、从未注册的物品**（原版同样注释，行号可对照），属上游内容缺口。

---

## 一、项目概况

### 1.1 原项目

| 项 | 值 |
|---|---|
| 名称 | Flan's Mod Reloaded |
| 版本 | 0.4.0 |
| 平台 | Minecraft 1.20.1 / Forge 47.2.0 |
| Java 文件 | 652 |
| 授权 | MIT |

### 1.2 移植目标

| 项 | 值 |
|---|---|
| 平台 | Minecraft 1.21.1 / NeoForge 21.1.250 |
| 构建 | ModDevGradle 2.0.148 / Gradle 9.2.1 / JDK 21 |
| 映射 | Parchment 2024.11.17 |
| 仓库 | 阿里云镜像优先（`maven.aliyun.com`），其次 neoforged / blamejared |
| Java 文件 | 653 |
| 产物 | `build/libs/flansmod-0.4.0.jar`（9.1 MB，4859 条目） |

### 1.3 内嵌模组（8 个）

一个 jar 声明 8 个 modId，共用同一套类与资源：

`flansmod`（核心）· `flansphysics`（物理）· `flansteams`（队伍/地图）· `flansbasicparts`（基础部件）· `flansvendersgame` · `flansmodernweapons` · `flansclockworkcalamity` · `flansworldwars`

### 1.4 功能与内容规模

核心是把「枪械/载具/装备」做成**数据驱动**：JSON 定义 + 通用物品类 + 自定义渲染管线。

| 内容类型 | 定义文件数（`data/` 下，实际被加载） |
|---|---|
| guns 枪械 | 69 |
| bullets 弹药 | 34 |
| parts 部件 | 186 |
| attachments 配件 | 29 |
| armour 护甲 | 40 |
| magazines 弹匣 | 99 |
| workbenches 工作台 | 17 |
| materials 材料 | 18 |

- **物品注册总数：379**（原版与移植版逐个同名一致，已程序化核对）
- **模型文件：431**（全部位于 `models/item/`）
- 主要子系统：动作系统（`common/actions`）、载具与物理（`flansphysics`）、队伍/地图实例化（`flansteams`）、TurboRig 自定义模型渲染（`client/render/models`）、数据包定义解析（`common/types`）

### 1.5 文件增删账目

移植过程中**无文件丢失**：

- 新增 3 个：`FlansDataComponents.java`、`FlansIngredients.java`、`NpcRelationshipsAttachment.java`
- 移除 2 个：`NpcRelationshipCapabilityAttacher.java`、`NpcRelationshipsCapability.java`（Forge Capability 机制在 1.21 被数据附件取代，由 `NpcRelationshipsAttachment` 等效承担）

---

## 二、审查方法

1. **逐项对照原版源码**：每个可疑点都回到 `FlansModReloaded-1.20` 查看原写法，判定"是我移植改错了"还是"上游本来如此"。
2. **反编译核实 API 真实签名**：用 `javap` 检查 NeoForge 21.1.250 jar 中的字节码，而不是依赖记忆或文档。
3. **实际运行验证**：`runServer` / `runClient` / `clientAuto`（`--quickPlaySingleplayer` 直入存档）。

### 判定标准

| 判定 | 含义 |
|---|---|
| **移植版错误** | 原版写法正确，移植改动引入缺陷 |
| **移植版缺失** | 原版有、移植版漏做 |
| **API 语义变化** | 原 API 在 1.21 语义不同，机械替换后行为改变，两边各自"在当时是对的" |
| **上游遗留** | 原版同样存在问题，**不应在移植中修改** |

---

## 三、差异清单

### 3.1 崩溃级（左列均已修复）

| # | 位置 | 原版 | 移植版原状 | 判定 | 修复 |
|---|---|---|---|---|---|
| A1 | `common/FlansMod.java` | 无此机制 | `FlansDataComponents.DATA_COMPONENTS` 从未注册到 mod 总线 | 移植版缺失 | 补 `register(modEventBus)` |
| A2 | `common/crafting/RestrictedContainer.java:124` | `Slots[i].save(new CompoundTag())` | `Slots[i].save(RegistryAccess.EMPTY)` | **API 语义变化** | 改 `saveOptional(registries)` |
| A3 | `common/blocks/TurretBlockEntity.java:319,321,327` | `save(new CompoundTag())` | `save(RegistryAccess.EMPTY)` | 同上 | 改 `saveOptional` |
| A4 | `packs/basics/common/DistillationTowerBlockEntity.java:170,173` | `save(new CompoundTag())` | `save(RegistryAccess.EMPTY)` | 同上 | 改 `saveOptional` |
| A5 | `common/entity/vehicle/save/EngineSyncState.java:133` | `save(new CompoundTag())` | `save(RegistryAccess.EMPTY)` | 同上 | 改 `saveOptional` |
| A6 | `common/entity/vehicle/save/GunSyncState.java:45` | `save(new CompoundTag())` | `save(RegistryAccess.EMPTY)` | 同上 | 改 `saveOptional` |
| A7 | `common/item/BulletBagItem.java:70` | `save(new CompoundTag())` | `save(RegistryAccess.EMPTY)` | 同上 | 改 `saveOptional` |
| A8 | `common/actions/contexts/GunContext.java:924` | `Stack.save(stackTags)` | `Stack.save(RegistryAccess.EMPTY)` | 同上 | 改 `saveOptional` |
| A9 | `teams/client/TeamsModClient.java:34` | — | `NeoForge.EVENT_BUS.addListener(::onKeyMappings)`，而 `RegisterKeyMappingsEvent` 是 mod 总线事件 | 移植版错误 | 删除该行，改由 `@EventBusSubscriber(bus=MOD)` 自动注入 |
| A10 | `client/render/models/FlansModelRegistry.java:114` | — | `NeoForge.EVENT_BUS.register(this)`，但该类无任何 `@SubscribeEvent` 方法 | 移植版错误 | 删除该行 |
| A11 | `client/FlansModClient.java:172`、`FlansModelRegistry.java:149`、`ClockworkCalamityMod.java:171`、`VendersGameMod.java:210`、`ModernWeaponsMod.java:216`、`WorldWarsMod.java:208` | `new ModelResourceLocation(id, "inventory")` | `ModelResourceLocation.inventory(id)` | **API 语义变化** | 注册用途（`event.register`）改 `standalone`；查询用途（`shaper.register`）保留 `inventory` |
| A12 | `client/FlansModClient.java`（原 243-256 行） | SRG 名反射 `f_91078_` 等 | 反射字段在 1.21 已不存在 | **API 语义变化** | 删除反射，改用 `META-INF/accesstransformer.cfg` |
| A13 | `data/flansteams/dimension_type/teams.json` | 1.20 IntProvider 格式 | `{"type":..., "value":{"min_inclusive":..}}` | **API 语义变化** | 展平为同级字段 |
| A14 | `plugins/jei/FlansModJEIPlugin.java:82` | `ForgeRegistries.ITEMS.getValue(id) != null` | `BuiltInRegistries.ITEM.get(id) != null` | **API 语义变化** | 改 `containsKey(id)` |
| A15 | `common/FlansMod.java` 客户端事件 | — | `OnRegisterRecipeBookCategories` 注册在游戏总线（实际是 mod 总线事件） | 移植版错误 | 删除空实现 |

### 3.2 静默功能失效级（编译通过、启动通过，但功能不生效）

| # | 位置 | 原版 | 移植版原状 | 判定 | 修复 |
|---|---|---|---|---|---|
| B1 | `mixin/MixinMouseHandler.java:21` | 1.20 `turnPlayer()` 无参 | 选择器 `"turnPlayer()V"`，而 1.21 实为 `turnPlayer(D)V` | **API 语义变化** | 改 `turnPlayer(D)V` 并加 `require = 1` |
| B2 | `common/actions/contexts/ActionGroupContext.java:414` | `previousLump.save(stackTag)`（写入参数） | `previousLump.saveOptional(...)` 返回值被丢弃，`stackTag` 恒为空 | **API 语义变化** | 改为接收返回值 |

**B1 说明**：Mixin 在注入点匹配不到时**不报错**，日志同样打印 `Mixing ... into ...`，与成功无异。已加 `require = 1` 使未来签名变化变为硬失败。此缺陷导致开镜（ADS）时鼠标灵敏度覆盖从未生效。

**B2 说明**：导致弹匣内子弹排布数据不写入存档，弹匣内容静默丢失。

### 3.3 语义静默改变级（已修复）

`ForgeRegistries.X.getValue(id)` 在找不到时返回 `null`；1.21 的 `BuiltInRegistries.ITEM/BLOCK/ENTITY_TYPE` 是 `DefaultedRegistry`，`.get()` **永不返回 null**，找不到时返回默认项（AIR / void）。因此所有 `!= null` 判断退化为"恒为真"。

| # | 位置 | 修复 |
|---|---|---|
| C1 | `client/render/models/FlansModelRegistry.java:95` | `ITEM.containsKey` |
| C2 | `common/crafting/MaterialMatcher.java:55` | `ITEM.containsKey` |
| C3 | `common/effects/EffectPlaceBlock.java:34` | `BLOCK.containsKey` |
| C4 | `common/types/bullets/elements/ProjectileDefinition.java:124` | `ENTITY_TYPE.containsKey` |
| C5 | `common/types/elements/ItemStackDefinition.java:33` | `ITEM.containsKey` |
| C6 | `common/crafting/ingredients/TieredPartIngredient.java:166` | `ITEM.containsKey` |

### 3.4 待处理项

| # | 位置 | 问题 | 判定 | 建议 |
|---|---|---|---|---|
| D1 | 17 处 `ItemStack.parse(RegistryAccess.EMPTY, ...)`（见附录） | 原版 `ItemStack.of(tag)` 无 provider 概念；1.21 应传入作用域内的 `registries`。用 `EMPTY` 时，依赖动态注册表的组件（附魔、药水等）解析会失败，`.orElse(ItemStack.EMPTY)` 将**静默吞掉物品** | 移植版偏差 | `TurretBlockEntity:335,338` 与 `DistillationTowerBlockEntity:186,189` 所在方法已有 `registries` 参数，应改用它；其余 13 处所在方法无 provider，需评估是否补签名 |
| D2 | `common/item/GunItem.java:369-380` | 原版用 `TierSortingRegistry`（Forge 专有，1.21 已移除），移植版自建 `TIERS_BY_STRENGTH` 列表 + `TierLevel()` 映射 | 等效重实现 | 逻辑等价，但未验证自定义 Tier（如模组新增等级）；`1.21` 的正解是 `ItemAbilities`/`Tool` 组件，当前实现可工作但属技术债 |
| D3 | `common/network/FlansEntityDataSerializers.java` | 定义同步写 `def.hashCode()`、按 hash 查表 | **与原版一致，非回归** | 无需修改。已核对原版同样实现 |

### 3.5 上游遗留（**不应修改**，已验证原版同样存在）

| # | 现象 | 规模 | 依据 |
|---|---|---|---|
| E1 | `Unable to load model: '<ns>:<path>'` | 245 条 | 逐条比对：**245/245 在原版同样缺失**。根因是代码把模型注册到 `models/<name>.json`、`models/entity/*.json`、`<name>_inventory.json`，而 431 个模型**全部位于 `models/item/`**，且 `models/entity/` 目录不存在。枪械仍正常渲染——这些 json 由原版自动加载路径拾取，内部带 `flansmod:turborig` 几何加载器（176 个） |
| E2 | `Couldn't load tag ...` / 孤儿定义 | 11 条 / 66 个 | 数据文件引用从未注册的 id（如标签写 `m1carbine`，实际注册为 `m1_carbine`）。原版同样如此 |
| E3 | `Action primary_fire was left in the system without being started` | 进世界时 6 条 | `ActionStack.java:227`，原版 1.20 同代码同位置 |
| E4 | `TODO` / `FIXME` | 89 处 | 原版遗留 |
| E5 | `Class version 65 required ... Mixin (JAVA_17 supports 61)` | — | NeoForge **自身**的 mixin 也打印同样消息，纯噪音 |
| E6 | `refmap 'flansmod.refmap.json' could not be read` | — | NeoForge 使用 Mojang 映射，无需 refmap，仅开发环境提示 |
| E7 | `[removal]` 编译警告 | 24 条 | `EventBusSubscriber.bus`(16)、`Item.initializeClient`(5)、JEI `getBackground`(3)、`addTooltipCallback`(2) 等。**其中 `initializeClient` 是枪械自定义渲染器入口**，NeoForge 未来移除它会直接导致枪械渲染失效，需长期关注 |

---

## 四、已验证事项（有证据）

| 项 | 证据 |
|---|---|
| 构建 | `./gradlew clean build` 成功，产出 9.1 MB jar |
| 无 Forge 残留 | `net.minecraftforge` 引用 0 处 |
| 物品注册完整性 | 379 个，与原版**逐个同名**一致（`comm` 双向无差异） |
| 服务端启动 | `Done (2.982s)`，ERROR = 0 |
| 客户端启动 | 进入世界，ERROR = 0，无崩溃 |
| **数据组件写入生效** | 日志 `GunUUID: Created UUID 1c58578f-... for 1 flansclockworkcalamity:clockwork_overkill` —— 证明 `FlanItem.Server_GetOrSetNewGunID` 成功写入 DataComponent（这正是 A1 崩溃的代码路径） |
| Mixin 注入生效 | 加 `require = 1` 后启动无硬失败，证明 `turnPlayer(D)V` + `isScoping()Z` 均匹配成功 |
| 定义解析 | 无 `Couldn't parse ... definition` 日志 |
| loot modifier | 修复 `forge:loot_table_id` → `neoforge:loot_table_id` 后，`Could not decode GlobalLootModifier` 由 3 条降为 0 |

---

## 五、尚未验证（验收缺口）

**这是当前唯一的主要缺口。**

| 项 | 状态 |
|---|---|
| A2-A8 修复后的实际效果 | 未复测。需进世界 → 放置并破坏工作台/蒸馏塔 → 存读档 |
| 存档往返 | **未验证**。物品的存盘→重载是否保真从未测试（与 D1 直接相关） |
| 开火 / 后坐力 / 换弹 | 未测试 |
| 开镜（ADS） | 注入已证明生效，但灵敏度实际降低未观测 |
| 载具驾驶、物理引擎 | 未测试 |
| 队伍 GUI、地图实例化 | 未测试 |
| 网络同步（多人） | 未测试。以上全部验证均在单人环境下完成 |

> 单人环境下客户端与服务端同进程，**网络层代码路径基本未被覆盖**。多人联机测试是本移植最大的未验证面。

---

## 六、修改记录

已施行的修改共 6 批：

1. **编译期修复**（20 → 0 错误）：渲染顶点 API、`Tesselator.begin`、`MenuScreens` 事件化、`EquipmentSlot.BODY` 等
2. **启动期修复**：A11-A15、A9、A10，以及 `teams_maps` 目录创建
3. **数据组件修复**：A1（并验证生效）
4. **Mixin 与序列化修复**：B1、B2、A2-A8
5. **数据包目录与格式修复**（F1-F4，见下）
6. **渲染变体修复**（F5）

---

## 七、本轮新增修复（F 系列）

### F1　配方与战利品表目录仍为复数（最严重，静默失效）

1.21 只识别 `recipe/`、`loot_table/`（单数）。目录名为复数时**不报错、不警告，直接忽略**。

| 目录（修复前） | 文件数 | 修复后 |
|---|---|---|
| `flansbasicparts/recipes` | 174 | `flansbasicparts/recipe` |
| `flansvendersgame/recipes` | 48 | `flansvendersgame/recipe` |
| `flansmod/recipes` | 4 | `flansmod/recipe` |
| `flansbasicparts/loot_tables` | 13 | `flansbasicparts/loot_table` |
| `flansvendersgame/loot_tables` | 3 | `flansvendersgame/loot_table` |

验证：vanilla jar 中确认目录名为 `data/minecraft/recipe/`、`data/minecraft/loot_table/`。

**影响**：226 个配方、16 个战利品表全部未加载 —— 这是"所有物品功能缺失"的主因（工作台里没有任何配方可做）。

### F2　配方 `result` 格式（1.20.5 变更）

`result` 现在必须是对象且用 `"id"` 键；`ingredients` 仍用 `"item"`：

```json
// 错误（1.20 写法）
"result": { "item": "flansmod:rainbow_paint_can", "count": 1 }
// 正确（1.21）
"result": { "id":   "flansmod:rainbow_paint_can", "count": 1 }
```

修复 222 个对象形式 + 4 个字符串形式（`steel_ingot_from_blasting` 等改为对象）。
错误信息：`No key id in MapLike[{"item":...}]` / `Not a JSON object`。

### F3　`StackedVanillaIngredient.items` 误设为必填

原版按"存在才读"处理（`if(json.has("items"))`），本移植写成 `fieldOf("items")`。
只按 `tags` 匹配的配方（如 `part_fab_hm_pocket_silencer` 用 `minecraft:wool`）因此解析失败。
改为 `optionalFieldOf("items", List.of())`。

### F4　配方中残留 `"type": "minecraft:item"`

1.21 的 ingredient 类型注册表中没有 `minecraft:item`。NeoForge 的 `makeIngredientMapCodec()`
在 `type` 缺失时会回退到 `Ingredient.Value.MAP_CODEC`（即 `{"item":...}` / `{"tag":...}` 形式），
因此正确做法是**删除该行**。共 10 个文件。

### F5　`TurboRigWrapper` 回退查找变体错误（我在上一轮引入）

`RegisterAdditional` 强制要求 `standalone` 变体，但**普通物品模型**由 vanilla 自动加载到
`inventory` 变体下。两者是不同的键：

| 用途 | 变体 | 说明 |
|---|---|---|
| `event.register(...)` | **standalone** | 从 `models/<path>.json` 加载（无 `item/` 前缀） |
| vanilla 自动物品模型 | **inventory** | 从 `models/item/<name>.json` 加载 |
| `shaper.register(...)` | **inventory** | 必须与 vanilla 自动加载的键一致 |

我把 `TurboRigWrapper` 的回退查找改成了 `standalone`，导致失败回退永远拿不到模型。已改回 `inventory`。

---

## 八、上游内容缺口（核对原版后确认，不属于移植问题）

### 8.1　151 个物品缺定义与模型

用脚本对全部 425 个注册物品交叉核对，并用**同一脚本**核对原版 1.20.1：

```
移植版缺定义：151    原版缺定义：151    差集：0
移植版缺模型：151    原版缺模型：151    差集：0
```

| 类型 | 数量 | 模组 | 数量 |
|---|---|---|---|
| Bullet | 65 | flansmodernweapons | 132 |
| Gun | 59 | flansclockworkcalamity | 13 |
| Attachment | 26 | flansworldwars | 6 |
| Armour | 1 | | |

**根因**：`flansmodernweapons` 内容包**在两个版本中都是空目录**（`assets/` 与 `data/` 下 0 个文件），
但 Java 侧注册了 132 个物品。`flansclockworkcalamity`、`flansworldwars` 同理部分缺失。

**另有 3 个可修复的命名漂移**（数据文件与 Java 注册名不一致，原版同样）：

| Java 注册名 | 数据文件名 |
|---|---|
| `m1_carbine` | `m1carbine.json` |
| `m1_garand` | `m1garand.json` |
| `type38_sniper` | `type38sniper.json` |

### 8.2　3 个配方引用未注册物品

`gun_fab_phantek_mamba`、`part_fab_hm_flashlight`、`part_fab_hm_flashlight_mini`
引用的物品在 Java 中**被注释掉**（移植版 `VendersGameMod.java:111-112`，原版同位置同样注释），
属上游遗留。

### 8.3　无中文语言文件

原版仅 `en_us` / `de_de` / `ru_ru`。全项目 824 条英文本地化条目需从零翻译。

### 8.4　245 条 `Unable to load model` 警告

`RegisterAdditional`/`PreRegisterModel` 的路径（`entity/...`、裸物品名、`<name>_inventory`）
指向不存在的文件；431 个模型全部位于 `models/item/`，且无 `models/entity/` 目录。
枪械仍可渲染 —— 这些 json 由 vanilla 自动加载路径拾取，内部带 `flansmod:turborig` 几何加载器。
原版同样存在。

---

## 附录：D1 完整位置清单

```
common/actions/contexts/GunContext.java:939
common/actions/contexts/ActionGroupContext.java:439, 471, 512
common/blocks/TurretBlockEntity.java:335, 338            ← 有 registries 可用
common/crafting/RestrictedContainer.java:133             ← 已用 registries（正确范例）
common/entity/vehicle/save/GunSyncState.java:38
common/entity/vehicle/save/EngineSyncState.java:122
common/item/BulletBagItem.java:48
common/item/GunItem.java:237, 254, 279, 305
packs/basics/common/DistillationTowerBlockEntity.java:186, 189   ← 有 registries 可用
```
