# tools/native — 离线原生工具（开发期专用）

**这里的东西永远不进 mod jar。** Gradle 的 `jar` 只打包 `src/main`（java + resources），
没有任何 sourceSet 包含 `tools/`；`build.ps1` 末尾会实测最新 `flansmod-*.jar` 里
没有 `.exe/.dll/.so/.dylib`，一旦泄漏直接失败。

## 为什么用 C
- GPU 审计要比较几百张 1304x648 的 PNG，用 C 工具做像素统计/差分比启动 JVM 快；
- 参考实现（弹道/光学数学）用独立 C 写成"第三方 oracle"，和 Java 实现对照；
- 不影响发布包，不影响兼容矩阵。

## 构建
```powershell
pwsh -File tools/native/build.ps1
```
工具链查找顺序：`$env:CC` → Zig（`zig cc`）→ PATH 上的 `gcc` → WinLibs/MSYS2 目录 → `clang`。

## 现有工具
| 工具 | 语言 | 用途 |
| --- | --- | --- |
| `pngstat` | C | PNG 区域统计与差分：`pngstat a.png --region 100 100 64 64 --top-colors 5`；`pngstat a.png --diff b.png --threshold 2 --out-diff d.png` |
| `compatlog` | C++ | 兼容矩阵读取器：把一个或多个 dev 客户端日志（含 7 个优化模组全开/逐个关闭）解析成一张表 + 发现问题清单 |

### 为什么 C 和 C++ 混用
- **C（pngstat）**：纯像素循环，没有复杂数据结构，C 足够且启动快。
- **C++（compatlog）**：多文件日志解析、可选字段、正则/文件系统/聚合，STL 更合适；用 `g++ -std=c++20` 编译。
- 两者都是**离线开发工具**：Gradle 只打包 `src/main`，`build.ps1` 末尾会实测最新 JAR 内没有 `.exe/.dll/.so/.dylib`。

### compatlog 用法
```powershell
# 单个日志
tools/native/bin/compatlog.exe run/logs/latest.log
# 目录（取其中 *.log）并输出 markdown
tools/native/bin/compatlog.exe run/logs --out reports/eft/compat-baseline/matrix.md
```
表格列：run / mods / iris / fabulous / scope camera / gl / perf windows / fps / scope ms/frame / rig quads/frame / mixin / probe / fatal / asset；
Findings 会列出未加载的关注模组、`[Compat]` 缺失、`[RenderPerf]` 未开启、以及前三条真实错误。

## 许可
`vendor/stb_image.h`、`vendor/stb_image_write.h` 来自 nothings/stb（public domain / MIT），
仅作为开发工具的图像解码依赖。
