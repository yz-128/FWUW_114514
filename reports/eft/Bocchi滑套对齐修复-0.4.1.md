# Bocchi 枪体与滑套对齐修复（0.4.1）

目标工程为 `flanmod-template-1.21.1`。用户截图中的“孤独 全自动手枪”对应原包 `BocchiTheGlock18C`，滑套为 `Glock18CSlideBocchi`（实际附件定义扩展名为 `.df`）。本次先修复已明确复现的静态对齐偏差；截图无法单独证明是否还有射击卡住或不随动，相关症状仍待确认。

原枪体 `.class` 构造结尾先调用 `translateAll(0,0.5,0)`，再调用 `flipAll()`。原 FMUM `ModelFlan.flip` 同时翻转几何 Y/Z 与定位点 Y/Z，所以最终平移应为 Y=-0.5。旧读取器将翻转和平移固定按另一顺序组合，得到 +0.5；独立滑套没有相同平移，枪体相对它多偏移一个模型像素，即模型缩放 0.2 下的 0.0125 格。

修复在 `ModelGeometry.Model` 中顺序组合构造期变换；执行翻转时同时翻转之前累计的 Y/Z 平移，连续两次翻转相互抵消。`BytecodeModel` 两处入口共用该操作，没有针对枪名硬编码偏移。保留原滑套行程、挂点和动画参数，不修改原内容包或贴图。

验证从最终 JAR 执行。新增 `VerifyModelTransforms` 以逐点变换作为独立参考：11 组顺序、329 个原枪体定位点、365 个原模型中的 392 个构造变换调用，1463 项断言通过。旧 0.4.0 JAR 在 `TF minimum 1` 顺序测试中明确失败。原包中这些变换均在构造函数内，未发现辅助方法/覆盖方法造成的重复计数。

既有 `VerifyLegacyRender`、`VerifyHoldingArms`、`VerifyReloadRendering`、`VerifyGunActions` 从 0.4.1 JAR 回归通过，覆盖 31 把 EFT 枪、32 个弹匣、10572 个有限换弹姿势和 1550 个操作姿势等。新增变换测试使用 Java 21 编译并运行；四项既有工具因其原有工具类编译版本使用本机 Java 25 执行，生产 JAR 由 Java 21 工具链编译。Gradle `build` 成功；其 `test` 为 `NO-SOURCE`，不是 JUnit 测试通过的证据。审阅过 scoped diff，`git diff --check` 通过。

产物：`build/libs/flansmod-0.4.1.jar`。旧 `flansmod-0.4.0.jar` 保留；源码与旧 JAR 备份目录见 `build/bocchi-slide-backup-path.txt`。未修改运行目录内容包，未启动、关闭或控制游戏。替换新 JAR 并手动重启后确认实机效果。

离线预览使用实际原包网格、贴图和持握变换，属于 CPU 渲染，未应用用户截图中的光影，不是游戏截图：

![修复前离线预览](<E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1/build/bocchi-before.png>)

![修复后离线预览](<E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1/build/bocchi-after-0.4.1.png>)

协作任务书为根目录 `COLLABORATION.md`。DeepSeek 的原模型与动作状态复核尚未收到回执，本记录不将其描述为已完成。
