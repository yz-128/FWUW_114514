# EFT / Scav 护甲穿戴后端修复

用户明确的故障是“无法穿戴”。根因是原 `ContentRegistry` 将护甲也构造为普通 `ItemFlan extends Item`，仅有名称、图标和已解析的护甲参数，没有 `ArmorItem / Equipable` 身份。因此原生护甲槽、快捷穿戴、右键换装、护甲受击耐久路径均无法识别。

## 已实施

- `common/types/ItemFlanArmour.java`：新增 `ArmorItem` 子类，公开 `armour` 定义字段。保留 Helmet / Body / Pants / Boots 对应 HEAD / CHEST / LEGS / FEET；耳机和面罩原定义为 Pants 时仍为 LEGS，不擅自改变包内槽位。
- `common/ContentRegistry.java:78`：仅 `ArmourType` 改为新护甲类，物品注册 ID、读取顺序和既有重名处理保持兼容。`InfoType.getType` 以 `type.item` 身份查找，因此新子类可以继续被识别。
- 正数 `Durability` 直接成为 `MAX_DAMAGE`，不可堆叠；未提供耐久的装饰装备保留不损耗。装备使用原生的受击耐久路径，不额外重复扣耐久。
- 移速按 `MoveSpeedModifier - 1`、`ADD_MULTIPLIED_TOTAL` 加到对应穿戴槽；击退按原 `KnockbackReduction / KnockbackModifier` 用 `ADD_VALUE`。属性 ID 按槽区分，手持装备不获得穿戴属性。
- 每件装备使用直接材质 Holder 和一层 `ItemFlanArmour.layerId(armour)`，其值为 `flansmod:legacy/<registryPath>`。原生内层/外层资源分别为 `textures/models/armor/legacy/<registryPath>_layer_2.png`、`_layer_1.png`。名称沿用包原语言 key 和原显示名回退。
- `common/ArmourProtection.java`：按原定义将多件 `Defence` 百分比相加，限制在 0..1；注册到 NeoForge 的 ARMOR 减伤阶段，再由原生流程处理附魔、抗性和吸收。绕过护甲的伤害跳过该处理。实际装备在本击开始时取值，避免装备在这一击损坏后失去本击防护。其它装备已有减伤保留，再作用本百分比；不会变成治疗。
- `ArmourType`：命中音效数组按原实现修为 8 个，挡弹 0..3、穿透 4..7，修复此前 `part+1` 覆盖相邻身体部位。默认范围恢复为 8；增加原版 `Shoes` 槽拼写。

原版参考：`../FMUM-1710-master/src/main/java/com/flansmod/common/teams/ItemTeamArmour.java:35` 构造/耐久，`:51` 百分比防护，`:111` 穿戴属性；同目录 `ArmourType.java:26` 八组音效和各部位 +4 索引。

## 离线验证

四个生产文件独立 `javac --release 21` 编译通过。新增 `tools/offline/VerifyArmourItems.java` 直接反射构造真实 NeoForge `RegisterEvent`，调用生产 `ContentRegistry.onRegister`，验证真实两个包全部 80 份定义，而不是在测试另写一份注册工厂。

- 80 件全部成功注册新 `ArmorItem`，真实短名无冲突；65 件有耐久，15 件为无耐久装饰装备。
- 槽位：HEAD 32、CHEST 35、LEGS 7、FEET 6。
- 检查 `Equipable.get`、注册 ID、`InfoType.getType`、原槽位、精确耐久、名称、材质内外层路径、穿戴属性及手持不生效。
- 执行真实 NeoForge `DamageContainer` 的 ARMOR → MOB_EFFECTS → ABSORPTION 回调流程；100 点伤害、其它护甲先减 20、再装备 35% 防护后剩 52，随后 20% 抗性与 10 点吸收后剩 31.6。
- 验证 6B43 为 CHEST / 85 耐久、FastMT 为 HEAD、ComTac2HeadsetFDE 保持 LEGS；额外覆盖四部位完整挡弹/穿透声音字段，确认没有索引覆盖。

结果：**PASS 1,783 断言**，日志 `build/verification-armour/items-test.log`。测试执行的 JVM 使用既有 `build/offline-data` 的 FeatureFlagLoader 和 `build/offline` 的 FlansMod 离线桩，未启动游戏、客户端、世界或图形上下文。

## 边界与分工

本报告仅覆盖穿戴后端和其离线验证。穿戴模型、关节、纹理资源、客户端扩展和最终 Gradle 构建由主代理负责。

原包的部位 `ProofLevel`、穿透概率、钝伤与弹丸穿甲消耗没有在本次“无法穿戴”修复中接入 `EntityBullet`；不将它们伪装为原生护甲点或韧性，也不将其声称已完成。面罩开关、夜视和特殊效果没有扩面实现。未运行实机游戏验证。
