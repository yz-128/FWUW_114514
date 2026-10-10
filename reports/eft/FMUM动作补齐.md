# EFT / FMUM 1.1 动作补齐

2026-10-04。判定基准是 EFT 配套的 1.7.10 FMUM 1.1 Modified 核心及原包模型。此次接回掏枪、验弹、查膛、视角跟随、rest 的基础链；枪托距离按核心实际返回值保留为零。

状态：编译及离线回归通过，新动作尚待用户手动实机验收。此次没有启动客户端、执行自动给物品/开火脚本、操作用户桌面或生成键鼠输入。

修复版已打包到 `E:\JAVAMCMODGJ\FLAN 移植i\flanmod-template-1.21.1\build\libs\flansmod-0.4.0.jar`。已检查 jar 中包含动作/姿势/网络实现，不含离线测试、核心参考 class 或测试替身。

改前完整备份：`E:\JAVAMCMODGJ\FLAN 移植i\BACKUP_before_fmum_actions_20261004_135700`，包含 src、tools、reports。

组合键调整前备份：`E:\JAVAMCMODGJ\FLAN 移植i\BACKUP_before_alt_action_keys_20261004_155243`，包含 src、reports 和调整前 JAR。

16:36 后的弹匣、弹膛与分步装弹更新见[弹匣弹膛与装弹修复](弹匣弹膛与装弹修复.md)。该报告明确区分保膛换匣与原核心再次拉栓退弹的行为，以下动作基础说明继续适用。

## 已实现

| 项目 | 行为与基准 |
|---|---|
| takeOut | 切换持枪类型或热栏槽位时，从 1 递减至 0，使用包的 takeOutTranslate / takeOutRotate 和 TakeOutTime；默认 16 tick，AK74M 12 tick，附件 TakeOutTimeMult 参与计时。计入原版浮点余量及上一帧门控。掏枪期间射击/换弹请求等待动作结束。 |
| checkAmmo | Alt+T，需装入真实弹匣。按 CheckAmmoTime 执行往返，ADS 按 tilt 比例退让，枪体、实际弹匣与操作手共用包数据。服务端在顶点读取实际弹匣余量并按其 CheckAmmoState 显示提示；检查不写入弹药。空弹匣保留，可继续检查。AK74M 60 tick。 |
| checkChamber | Alt+R。正常查膛及 bolt-catch 分支分别使用两组时长、退让比例和声音。正常分支联动拉机柄、枪栓和操作手；BC 分支观察锁定枪栓。检查读取独立弹膛，不消耗或退出那一发。AK74M 正常 60 tick，BC 参数 40 tick；AK 无挂起枪栓状态时走正常分支。 |
| 视角跟随 | 按模型参数恢复转头惯性、竖直加速度、呼吸和行走的平移/旋转，以及呼吸/行走对视角的变化。跟随旋转使用逆局部坐标轴。持枪时暂时关闭现代原版摇晃，放下枪或退出世界后恢复此前选项。 |
| rest / unrest | O 或 Alt+V 手动收枪/恢复，默认收枪 12 tick、恢复 6 tick；靠墙收枪使用 unrestTime。包的 restTranslate / restRotate 按 progress × (1−sprint) 混合。检查、射击、换弹请求与收枪状态相互衔接，避免收枪未结束就开火。 |
| 枪托距离 | 实际核心 GunType.getStockDistance 的方法体只有 FCONST_0 / FRETURN。EFT 也未配置非零折叠距离，本轮保留零位，不增加额外位移。 |

默认操作：**Alt+T 验弹、Alt+R 查膛、O 或 Alt+V 收枪**。验弹需装入真实弹匣。R 仍是换弹。组合键使用 NeoForge 原生修饰键绑定，控制设置直接显示 Alt 组合，可分别修改验弹、查膛和两种收枪键。NeoForge 优先分发匹配的组合键，因此 Alt+R 不会同时触发普通 R 换弹。此前接受的隐藏准星逻辑未修改。

## 弹匣和弹膛

原先移植版将 EFT 的 Mag 链解析为散弹类型。为了让检查读取真实对象，本轮补上实际 ItsType=mag 物品的装入、序列化、模型及剩余弹量读取。换弹优先选择匹配、已装弹的真实弹匣；空弹匣堆叠不会被误认为满弹匣。没有备用弹药时，不移走枪里原有弹匣。

弹膛使用独立的物品数据保存实际子弹类型。30 发弹匣首次上膛是弹匣 29 发 + 弹膛 1 发；战术换弹保留弹膛那一发。弹匣空、弹膛仍有弹时，验弹和查膛会分别报告各自状态。检查读取不会改写两者，枪仍能射出最后那一发。

如果旧存档的枪仍装着此前散弹回退数据，Alt+T 不会凭空生成一个弹匣。手动验收时，先持实际弹匣（如 6L20_545x39_30Rounds）按 R 装入匹配散弹，再持枪按 R 装入该弹匣。

后续弹匣修复已接入包声明的分步换匣、压弹和对应的操作手姿势，替换原有通用换弹计时。手动拉栓/释栓与混装不同子弹的完整 FMUM 流程仍属后续工作；新增检查姿势不能作为这些流程已经还原的证明。具体计数、旧槽迁移、原核心差异和验收边界见[弹匣修复报告](弹匣弹膛与装弹修复.md)。

## 验证证据

- `compileJava` 和离线完整脚本通过。日志：[动作](actions_verification.log)、[真实物品数据](magazine_verification.log)、[既有姿势](verification.log)。
- 组合键调整后再次通过 `compileJava jar`，已检查新 JAR 的键位处理类和中英文控制设置文本。Alt+R 与 R 的分发依据本项目安装的 NeoForge 21.1.250 的 KeyMappingLookup 源码核对；未生成模拟输入，组合键实机触发仍待手动验证。
- 实际核心 ModelGun 的默认标量和浮点数组逐项检查通过，避免把模型未声明的字段当成零。EFT 31 把枪有 takeOut / rest 与验弹；29 把声明正常和 BC 查膛时长，另外两把不会生成未声明的动作。
- 31 把枪 × 5 种状态 × 5 个采样点：775 组枪体/弹匣姿势和 1550 个手臂矩阵均有限。动作与运动为零时，31 把枪的静止和 ADS 矩阵保持此前已校准的位置。
- 掏枪门控、检查中途取消、收枪后检查、自动碰墙收枪、退出墙面、声音各一次、逆坐标轴跟随及枪管射线映射通过。
- 使用生产 GunMagazine 和真实 ItemStack/NBT 验证弹匣/子弹身份、空弹匣保留、其他枪数据保留、30 次精确消耗、弹匣选择优先级、独立弹膛和读检查不写弹药。
- 实际核心的枪托零位方法体检查通过。原包材质对照仍为 0 个高置信异常、9 个待目视复核项，详见[材质报告](materials/材质审计报告.md)。

离线第一人称预览与运行时共用 FmumFirstPersonPose、FmumActionPose、LegacyGunMotion。预览绑定原包实际弹匣：

[掏枪半程](AK74M_action_takeout.png) · [验弹](AK74M_action_check_ammo.png) · [查膛](AK74M_action_check_chamber.png) · [BC 参数姿势](AK74M_action_check_chamber_bc.png) · [收枪](AK74M_action_rest.png) · [运动跟随](AK74M_action_view_follow.png)。

掏枪与收枪本来就会把部分枪和手臂移向画面外；不能套用静止帧的“双臂都完整可见”标准判定这些动作。

运行复现：`./tools/offline/RunEftAudit.ps1`。工具与核心参考 class 放在 tools/offline，不随 Gradle clean 删除，也不进入游戏 jar。核心参考只按字节码读取，不加载执行。

真实物品数据测试在独立 JVM 中初始化注册表，不创建客户端、玩家或世界。仅在该测试进程中，用 FeatureFlagLoader 替身跳过模组发现，并使用 NeoForge 的注册表 unfreeze 接口注册三个测试物品。生产弹匣读写与 ItemStack 序列化均未替换。

## 尚待手动核对

实机检查切枪/掏枪、Alt+T、Alt+R、O / Alt+V、运动跟随、靠墙收枪和取消动作后射击/换弹。正常、锁定枪栓两种查膛分支应分别检查。服务端没有客户端每帧运动插值，碰墙门控的实际时机需实机核对。

屏息/喘息体力系统、手动切换瞄具的完整状态仍未接入。附件手臂优先级已在当日晚的[静止持枪对照修复](静止持枪对照修复.md)接入当前默认附件树；玩家拆装与物品级附件状态仍未完成。现有射击后 20 tick 冲刺姿势抑制保留；默认 RunPoseTime 已改为实际核心的 6 tick。本报告只确认上述基础链和离线证据，不把手动验收标记为通过。

## 核心参考校验值

从用户提供的 FMUM 1.1 Modified 核心中直接提取：

- 原 jar SHA256：`5C07909111E60EFAA1EDCD8B31D58103F2CDE91E1155EAF0E88D7ED9DE146A23`
- ModelGun_1.1.class SHA256：`A91794B30EDF2BC859D80436EE9FF16034CAAC46C854E4E0A8DCAF6686E3329F`
- GunType_1.1.class SHA256：`65283F8127AB5F55C21608C50A371E87E0D29EB970504E32100323CEB59B2CB1`
