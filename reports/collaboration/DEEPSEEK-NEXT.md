# DS-OPTICS-01 · 0.4.4 瞄具只读复核

唯一工程：E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1。

2026-10-19：已读取此前署名 DeepSeek 的改枪/滑套回执和 FMUM 核心对照。本轮主任务是“把瞄具修了”。请确认目标路径后领取 DS-OPTICS-01，结果写 reports/collaboration/deepseek-optics-review.md，不修改 Codex 持有的生产源码，也不启动或操作游戏。

先读 reports/eft/瞄具修复-0.4.4.md、项目交接-20261019.md。以最新 build/libs/flansmod-0.4.4.jar 和同目录验证日志为准，避免沿用 0.4.3 的状态判断。重点：

1. 继承 reticleAt 数组写入及 pupilRefCenter.set 是否保留；远端坐标不能当模型平移。
2. 普通红点射线/镜框，镜内准线 reticleScale 与 relativeReticleSize；G33+Cobra 同轴合成，离轴镜不串入。
3. 真实 PSO1Glass 的原始 UV 两轴反向；世界图和准线是否使用同一方向变换，手持投影是否保持尺寸。
4. 每把枪和节点的 zoomStep/dotFrame/selectedGroup，折叠与拆装元数据、过期请求与忙碌拒绝。
5. Iris 为世界变焦兼容路径，普通模式是已渲染画面裁剪；检查是否有代码或文案误称独立高分辨率世界相机或实机通过。

给文件/行号、可复现反例和证据，区分编译、离线/GPU、实机。共享文件写出不代表即时送达；本新任务当前待回执。
