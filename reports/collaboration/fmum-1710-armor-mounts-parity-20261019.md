# FMUM 1.7.10 配件与护甲精确对照

本轮为只读对照，检查了当前源码、原 FMUM 源码及 `build/eft-core-reference.jar` 的实际字节码；没有改生产源码或启动游戏。结论针对配件安装/拆卸和护甲穿戴，不代表整个模组已完整复现 1.7.10。

核查中发现过两处真实拆卸回归：满背包时静默丢弃膛弹，以及拆父组件时只检查父节点的 barrel/bolt 类型。已报告主代理；主代理修正后的源码已再次阅读，现已先规划库存、递归检查拆除子树、处理溢出掉落/拒绝。新构建及相应事务回归由主代理执行，本报告不将源码复查写成已完成实机验证。

## 1. 对照依据

原源码根目录：`E:/JAVAMCMODGJ/FLAN 移植i/FMUM-1710-master/src/main/java/`。

- `com/flansmod/common/guns/AttachableSlot.java:24`：名称/类型名单判定；同文件 `:10` 默认槽容量 64。
- `com/flansmod/common/guns/ModifiableType.java:95`：挂点和步进解析，`:104` 每槽 MaxAttachments，`:124` 四类名单，`:159` / `:167` 名称和类型列表赋值。
- `com/flansmod/common/network/PacketGunAttachment.java:623`：按父节点路径寻找真实挂点；`:654` 单槽全局/局部双容量；`:681` 名单；`:686` 两种握把唯一性；`:697` 挂点位置；`:752` 拆卸。
- `com/flansmod/common/guns/DefaultAts.java:24`：`next` / `[]` / step / offset / paintjob；`:73` 将显式默认树写入物品。
- `com/flansmod/common/guns/GunType.java:794`：读取默认树；`:844` / `:853` 为新枪首次写入默认附件。
- `com/flansmod/common/teams/ItemTeamArmour.java:35`：护甲类型与槽；`:41` / `:58` 耐久；`:51` Defence；`:66` 贴图；`:111` 属性。
- `com/flansmod/client/model/ModelCustomArmour.java:29` / `:32`：整体 ModelScale；`:51` 起按模型组绑定关节；`:86` 起覆盖角度；`:98` 起覆盖旋转点。

二进制复核：使用本地 JDK 的 `javap -classpath build/eft-core-reference.jar -c -p`，结果保存在 `build/verification-armour-preview/fmum-parity-bytecode.txt`。关键证据：

- `AttachableSlot.isAttachmentAllowed` BCI 0..197：先 attachmentWhitelist，再 attachmentBlacklist，再 typeWhitelist，最后 typeBlacklist；BCI 130 读取的是 `AttachmentType.attachmentType`，并非 `itsType`。
- `PacketGunAttachment.handleServerSide` BCI 3656/3669：分别读取 `FlansMod.maxAttachments` 和 `AttachableSlot.maxCanAttach`。
- `PacketGunAttachment.returnLayerStacks` BCI 47：递归返回子配件；BCI 86/103：递归标记 barrel/bolt；BCI 195：背包放不下则调用原混淆名 `EntityPlayer.func_70099_a`，即掉落物品。
- `ItemTeamArmour.getProperties` BCI 0..17：`new ArmorProperties(1, type.defence, Integer.MAX_VALUE)`；没有把 ProofLevel 当作 vanilla armor 点。
- `ItemTeamArmour` 构造 BCI 33..66：显式 durability 优先，仅 breakableArmor==1 时使用默认耐久；`damageArmor` BCI 0..31 还检查全局模式。
- `ModelCustomArmour.setBodyPart` BCI 27..33 等：用当前 biped 旋转点除以 ModelScale 后覆盖 renderer 旋转点，与当前关节平移 + 已缩放局部网格的表达等价。

## 2. 符合原版的主要规则

| 项目 | 当前行为与定位 | 对照结论 |
| --- | --- | --- |
| 名单优先级 | `GunAttachments.java:152`，名称白名单命中立即允许，名称黑名单随后拒绝；有类型白名单时只按它判断，否则看类型黑名单 | 与原四级顺序相同。名称白名单是例外许可，不是只允许这几个名字；类型白名单匹配 `AttachmentType`，不是 `ItsType` |
| 空白名单分组 | `GunAttachments.java:156` 将 `next` 之间空组视为没有限制 | 原解析器仅在 head!=end 时赋值，空组不产生一个“拒绝所有”的列表；当前符合 |
| 单槽容量 | `GunAttachments.java:188`：`min(5, slot.maxCanAttach)`；默认槽容量 64 也受全局 5 限制 | 原 `FlansMod.java:119` 默认 maxAttachments=5，`:523` 说明作用于单个槽/导轨；不是整枪最多 5 件。当前匹配默认值，尚未移植原配置可调项 |
| 前置父路径 | `ownerAt / nodeAt / canInstall` 沿实际树取当前父配件，之后检查该父自己的 slot；`GunAttachmentCatalog.treeEntries` 只列枪和已装父件的槽 | 与原 PacketGunAttachment:623–650 的父路径语义相同。某瞄具能接某导轨不等于能直接装到没有该导轨的枪根上 |
| 位置计算 | `GunAttachments.position` 使用父槽 stepVector × step + 子配件 attachOffset，模型其他坐标由挂点负责 | 对应原 PacketGunAttachment:697 和 :711 的表达 |
| 握把限制 | `canInstall` 对 `grip`、`rear_grip` 分别检查全树已有同功能类型；`reargrip` 兼容同义拼写 | 对应原 :686–687；是同功能各自唯一，不是所有握把两类合计只能有一个 |
| 默认树语义 | `ModifiableType.resolveDefaultAttachments` 与 bracket 子树；`FlansMod.java:356` 全部定义读完后重新解析；`GunAttachments.read` 只在未存自定义树时回退默认 | 保留原 `DefaultAttachments / next / []` 显式层次；写过的空树仍为空，不会拆完又长回默认配件。延后解析提高对读取顺序的容错 |
| 护甲槽 | `ItemFlanArmour.slotType` 保持 Helmet/Body/Pants/Boots→HEAD/CHEST/LEGS/FEET | 对应原 `super(CLOTH,0,t.type)`。ComTac2 原腿槽不擅自改成头槽；adidasCloth 原靴槽不擅自改成胸槽 |
| Defence 与属性 | `ArmourProtection` 使用装备 Defence 百分比；移速为 moveSpeedModifier-1 的总乘法，击退为加值 | 百分比含义与原 special armor 一致；移速/击退运算对应原 attribute operation 2/0。ProofLevel 没有被塞进护甲点或韧性 |
| 贴图层与骨骼 | `Textures.forArmour` 按 type==2 选 `_2`，其余 `_1`；`ArmourGeometry` 剔除将被覆盖的构造 pivot/rotation，`LegacyArmourModel` 按几何组绑定实时关节 | 对应原 `getArmorTexture` 和 ModelCustomArmour。槽位与模型组独立，腿槽耳机仍随 headModel；保留 ModelScale |

当前 `AttachmentType.java:22` 附近仍有历史注释声称槽白名单匹配 ItsType，该注释与当前正确代码及原实现矛盾，仅为文档问题，不能据此更改实际匹配逻辑。

## 3. 必要的现代 API 迁移与明确行为差异

必要迁移：

- `ItemArmor`→`ArmorItem`，通过原生 `Equipable` 支持护甲槽、快捷穿戴和右键换装；注册事件替代旧构造期间注册。
- 原 `getArmorModel`→NeoForge `IClientItemExtensions.getGenericArmorModel`。原 `_1/_2` 图像通过生成资源别名接入现代 ArmorMaterial.Layer，原像素不重画。
- 旧 NBT 树和 item damage 元数据改为每件物品的 CUSTOM_DATA 树；当前 `InfoType.getType` 根据 `type.item` 识别，不要求继承 ItemFlan。
- 移速/击退使用现代按槽 attribute modifier；百分比减伤在 NeoForge ARMOR 阶段回调执行，在附魔/抗性/吸收前完成，材质 armor/toughness 为 0，避免额外再算一次 vanilla 护甲减伤。

当前主动采用的流程改进，不能说成旧版原样行为：

- **拆父保留子树**：当前 `GunAttachments.item` 将子树写进返回的父物品，之后可以整组安装；原 `returnLayerStacks` 会先递归拆散所有子件，再逐件返还。当前实现保留更多状态，属于明确的工作流改进。
- 原普通配件背包满时会掉地；当前父组件本身放不下时返回 inventory_full，整笔拒绝。膛弹容量也先检查，再使用掉落或整笔拒绝兜底。
- 当前限制同配件、同槽、同 step/offset 的完全重复位置，且请求绑定当前界面、枪实例和 revision；原协议没有完全对应的现代并发/界面约束。
- 当前名称比较兼容大小写、reargrip 拼写；原名字 equals 和类型字符串 hashCode 是区分大小写的。对真实包是容错，不是对抽象输入逐字节等价。

尚未完整复刻的护甲细节：

- 原全局 `breakableArmor` 0/1/2/3 模式与 `defaultArmorDurability` 没有整体迁移。目前有显式 Durability 的装备走现代原生受击损耗，未设置耐久的装饰物不损耗。显式数值相符，不意味着原所有模式和扣损公式完全相同。
- 原 `getArmorDisplay` 返回 `(int)(Defence*20)`；当前 vanilla armor 属性为 0，单靠原生 HUD 不会显示这些百分比，中文 tooltip 显示实际 Defence。本轮对照指出后，主代理已补客户端 `LegacyArmourHud.java:20` 显示层及 `ArmourProtection.java:19` 纯换算 `displayPoints`，在 ARMOR_LEVEL 层使用原生 sprites 画换算点数与其它原生护甲点之和，限制 20，保留实际 ARMOR 属性为 0。显示界面仍待验证，不把补图标条变成重复伤害计算。
- 原 `getProperties` 本身没有根据 DamageSource 过滤；当前明确跳过 BYPASSES_ARMOR。未逐项重演旧 Forge 整条伤害管线，不声明所有特殊伤害语义绝对相同。
- **部位 ProofLevel、穿甲概率、钝伤/穿透磨损、面罩开关/遮罩、夜视及其它装备效果仍未接入完整运行时**。解析字段和穿戴成功不能当成已完成这些功能。
- 原 `atHitBoxTest=true` 时还可检查附件冲突盒；当前 HitBox/BigHitBox 只解析，安装事务未实现原 SAT 冲突判定。此次检索真实 EFT 附件文件没有这两类行，不能据此声称这就是当前 EFT 装不上配件的原因。

## 4. 主动修正原内容包错误

**DTMDR 原默认接口错写**：`run/flan/EscapeFromTarcraftReforged_1.5.3/guns/DTMDR762.txt:27` 明确默认树为 DTMDR762DefaultBarrel → DTMDR762Muzzle；但枪管文件 TypeWhitelist 写 `762x51Muzzle`，枪口 AttachmentType 写 `762NATOMuzzle`。原默认树写入不检查名单，所以旧默认装得上、拆下来手动装回却可能失败。当前 `GunAttachments.originalEftMdrMuzzle` 仅允许这一指定包、指定父子、指定槽和接口，不放开所有 NATO/762 接口，名称黑名单仍优先拒绝。属于精确修复原数据矛盾，而非原版通用许可规则。

**Killa 皮肤文件错名**：两护甲包定义 ArmorTexture Maska1Sch_Killa，但实际文件为 `Maska1SchaMaska1Sch_Killa_1/2.png`。`Textures.java:78` 在正规同名资源不存在后才认这一精确错名；正常文件/资源包替换仍优先。属于原包资源容错，不改变所有皮肤命名规则。

## 5. 本轮发现与源码复查状态

初读发现：

1. 枪管刚好占用最后一个空格时，旧当前 helper 先清膛，最终没有格子就静默吞掉子弹，并在注释错误宣称旧版也这样做。原源码 :765 与核心 bytecode 均证明旧版掉地兜底。
2. 拆含枪管/枪栓的父组件时，旧当前 helper 只检查父配件 ItsType，遗漏树内 barrel/bolt。原 :124–130 对整个递归拆除树设置标记。

最新 `GunAttachmentTransaction.java:39` 起已先复制计划库存，`:42` 使用递归 `containsKind`，`:43` 起规划膛弹并处理溢出掉落/取消，之后才写枪、清膛和清 boltCatch。本轮再次阅读确认源码覆盖了上述两个条件；定向运行结果以主代理新增测试及构建日志为准。未进一步发现有明确触发证据的新增物品丢失或复制问题。

另查明护甲显示精度差异：原 `ArmourType.java:253` 用 `Double.parseDouble`，原 `getArmorDisplay` 字节码是 `defence:D → 20.0d → dmul → d2i`。迁移代码此前先 `toFloat` 再存 double，0.35 可能成为略小于 0.35 的数，再乘 20 截断会少显示一点。已反馈主代理，最终源码已将 Defence 解析恢复 `Double.parseDouble`，`displayPoints` 也使用 double 运算；相应测试改为真实双精度 `.35D → 7`、`.3D → 6`。这项精度修复与新增 HUD 均没有给实际 ARMOR 属性增加数值。

已有独立证据可交叉审查：`build/verification-armour/items-test.log` 记录真实 80 件护甲后端 1,783 断言；`reports/eft/armour-mounts-0.4.6/armour-preview.png` 展示生产顶点输出的真实贴图/关节对照。这些均为离线证据，不是实机游戏验证。
