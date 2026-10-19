# 护甲穿戴离线视觉证据

产物：`armour-preview.png`，1920 × 1600。工具：`tools/offline/RenderArmourPreview.java`。运行日志：`build/verification-armour-preview/preview.log`。

图像是离线 CPU 光栅结果，**不是游戏截图**。未启动游戏、世界、窗口或 OpenGL。使用已经构建的 0.4.6 生产类，单独编译/运行工具；这次视觉预览不作为额外一次最终 Gradle 回归。

## 输入和路径

读取两个真实护甲包，所选四件均从主包定义精确查找，原图均为 512 × 512：

| 定义 | 原槽 | 模型缩放 | 原贴图 | 实际几何组 | 面数 |
| --- | --- | --- | --- | --- | ---: |
| FastMT | 头 | 1.1 | `armor/fastmt_1.png` | headModel | 642 |
| 6B43 | 胸 | 1.2 | `armor/6b43_1.png` | bodyModel / leftArmModel / rightArmModel | 600 |
| ComTac2HeadsetFDE | 腿 | 1 | `armor/comtac2headsetfde_2.png` | headModel | 936 |
| adidasCloth | 靴 | 0.25 | `armor/adidascloth_1.png` | bodyModel / leftArmModel / rightArmModel | 372 |

几何来自生产 `ModelCache.rigForArmour`，顶点、UV、法线实际由生产 `LegacyArmourModel.renderToBuffer` 输出并记录。灰色人体由原生 `HumanoidModel.renderToBuffer` 输出；人体和四件装备使用同一个 HumanoidModel 实例，未给各装备单独适配尺寸或平移。

第一行是站姿，第二行显式设置左右臂和左右腿相反摆动、头部略向左转的姿态。每行依次为无护甲灰色参考、穿戴正面、穿戴 45°、穿戴侧面。所有人物使用同一固定比例、同一取景中心。绘制时逐顶点检查屏幕范围，防止通过裁切掩盖异常几何。

## 视觉检查结果

已通过 `view_image` 检查最终图像。未观察到明显倒置、部件脱离人体或不一致缩放：

- FastMT 覆盖头顶，ComTac 耳罩和话筒位于头部两侧及嘴前；两者在转头姿态下继续保持相对位置，证明耳机虽占原腿槽仍按 headModel 绑定。
- 6B43 躯干部件保持在胸腰附近，肩片随相应手臂运动。摆臂时衣袖同对应手臂和灰色手部一起运动。
- adidasCloth 的原始模型没有腿部几何组，因此图中灰色腿部是预期的原模型内容；不能因其 Type Boots 就将整件服装只渲染在脚上，或将灰腿误报为丢失网格。
- 初版预览的取景切到了头盔顶部；仅调整了工具内统一取景比例，最终逐顶点边界检查和图像检查均无此裁切。生产代码未作改动。

没有从这份证据发现必须进一步修改生产几何的明确问题。本图可审查静态尺度、绑定、原图 UV 与姿态下的遮挡；不证明游戏内光照、动画时序、资源异步预热或最终着色器已经通过实机验证。
