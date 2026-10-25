# 经典改枪界面构建变体

`classicUi` 保留改造前的蓝灰色改枪界面，仅覆盖以下文件：

- `com/flansmod/client/gui/GunModificationScreen.java`
- `com/flansmod/client/gui/GunModificationPreview.java`
- `assets/flansmod/lang/zh_cn.json`
- `assets/flansmod/lang/en_us.json`

初始界面来自 0.5.9 备份，并保存在源码目录中；构建不依赖 `build/backups`。Screen 保留原布局，类别名称及语言资源继续接收可读性修正，Preview 同步接入当前渲染修复。0.5.22 补齐共享深度材质、深度区域清理、当前快慢机档位和状态恢复；不能为保留旧布局而继续使用旧的错误渲染路径。不覆盖物品、配件规则、网络、瞄具或数据组件。

运行项目的常规 `gradlew.bat build` 会同时构建当前 `gradle.properties` 指定版本的两份 JAR。以 0.5.49 为例：

- `build/libs/flansmod-0.5.49.jar`：新版暖灰改枪界面。
- `build/libs/flansmod-0.5.49-classic.jar`：原蓝灰改枪界面。

也可以单独运行 `gradlew.bat classicJar`。依赖已经缓存时可加 `--offline`。两个包使用相同主源码、模组元数据和当前协议 19；分类名仅区分界面，均为同一个 `flansmod` 模组，每个游戏实例选择其中一份安装。多人游戏客户端和服务端使用同版本构建。

Gradle 的 `classicJar` 先收集经典界面输出，再收集主输出，并从主输出排除两种 GUI 类及其内部类、上述两份语言资源。主 JAR 的 manifest 由经典变体复用，未排除的主输出直接共用。

离线新版布局图由 AWT 和真实模型绘制，不是经典变体截图，也不是游戏 `GuiGraphics` 截图。经典界面的实际游戏表现仍需要在对应实例中验证。

两种产物都通过 `RunGunsmithAudit.ps1 -Variant modern|classic` 检查各自实际 Preview 的材质接线、GPU 深度及透明混合；仅检查共享类不足以证明两种界面的渲染都正确。
