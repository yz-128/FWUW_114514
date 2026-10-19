# DS-SLIDE-02 · 滑套动作状态独立复核

复核人：DeepSeek（ZCode 会话）。日期：2026-10-18（Asia/Shanghai）。
只读复核。工程：`E:\JAVAMCMODGJ\FLAN 移植i\flanmod-template-1.21.1`（0.4.3）。
未修改任何源码或内容包，未启动游戏。

## 回执

领取 **DS-SLIDE-02**（滑套状态复核）。追踪的是实际调用链，未按零件名称推断。模型与挂点几何见 `deepseek-slide-model.md`。

## 一、状态载体

移植侧把空仓挂机存成一个布尔 CustomData 标记：

`common/GunActions.java:147-153`

```java
public static boolean boltCaught(ItemStack stack) {
    var tag = stack.get(DataComponents.CUSTOM_DATA);
    return tag != null && tag.copyTag().getBoolean("flansmodBoltCatch");
}
public static void boltCaught(ItemStack stack, boolean value) {
    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("flansmodBoltCatch", value));
}
```

存于物品数据，**随物品保存/掉落/交易保留**。原版用枪 states 的 `BOLT_CATCH_OFF` 标志位（`GunType.java:940` 附近），语义相近，都是每把枪独立的持久状态。这一点没有跨枪串扰风险。

## 二、状态迁移链（逐条带行号）

| 事件 | 位置 | 行为 | 与原版对照 |
|---|---|---|---|
| 开火后弹匣空 | `GunFiring.java:403` | `if(!left && gun.releaseSlideTime > 0 && GunMagazine.isEmpty(stack, gun)) boltCaught(stack, true)` | 对应 `releaseSlideTime > 0` 的枪在空仓后挂机。**只对主手（`!left`）生效** |
| 手动换弹成功 | `GunFiring.java:349` | `GunActions.boltCaught(stack, false)` | 对应原版 `setTOFOff(BOLT_CATCH_OFF)` |
| 换弹流程进入 RELEASE 步 | `GunReloading.java:148-149` | `ensureChamber(...)` 后 `boltCaught(..., false)` | 对应原版 `RELEASE` 后释放滑套 |
| 查膛（按操作） | `GunActions.java:68-71` | `requested = boltCaught(stack) ? CHECK_CHAMBER_BC : CHECK_CHAMBER` | 对应原版 `checkChamberStage` 的两个变体 |
| 弹药耗尽且无弹 | `GunFiring.java:403` | 同上 | — |

**结论：挂机 → 换弹/拉栓 → 释放 的主链是闭合的**，没有发现"挂机后永远无法解除"或"换弹后仍显示挂机"的死锁。

## 三、渲染表现

`FmumActionPose.slide`（`client/render/FmumActionPose.java:75-78`）：

```java
public static float slide(Model m, Frame f, float recoilSlide, boolean caught) {
    if(f.operation()==Operation.CHECK_CHAMBER && f.progress()>=tilt(m,f)) return charge(m,f).slide();
    return caught ? Math.min(recoilSlide,-scalar(m,"slideLockDistance",0F)) : recoilSlide;
}
```

`FmumReloadPose.slide`（`:23-27`）：

```java
if(reload.step()==Step.CHARGE) return charge(model,reload,action).slide();
if(reload.step()==Step.RELEASE) return -scalar(model,"slideLockDistance",0F)*(1F-reload.progress());
return FmumActionPose.slide(model,action,recoil,caught);
```

`TurborigItemRenderer` 把结果按 `ItsType` 施加（`:408-417`）：

```java
case "bolt" -> pose.translate(motion.slide*scale,0F,0F);
```

用的是**枪体模型**的 `slideLockDistance` / `gunSlideDistance`（`partMotion` 传入 `rig.animation`，`:448-455`）。这与原版一致：原版 `RenderGun.java:1322-1335` 用的是 `gunModel.gunSlideDistance` / `gunModel.slideLockDistance`，即枪体模型的标量，而不是滑套配件自己的。Bocchi 枪体为 1.25 / 0.625（见 DS-SLIDE-01 §2），滑套自己只有默认 0.25 —— 移植侧取枪体值，**正确**。

## 四、与原版的三处差异

### 4.1 滑套回位是线性而非加速 —— P3

原版 `GunAnimations.doSlideRelease`（`:148-153`）把 `gunSlide` 直接设成锁定比例，再由逐 tick 的 `slideVelocity += slideAcceleration[0] + slideAcceleration[1]*gunSlide`（`:128-131`）加速回零。

移植侧 `Step.RELEASE` 分支用 `-slideLockDistance*(1-progress)`。`GunReloadState.Plan` 的进度是线性推进的，因此**释放是一次匀速插值**，不是原版那条加速曲线。

观感差异很小（都是快速回弹），但严格说不是同一曲线。若要完全对齐，需要在 RELEASE 阶段复用 `GunAnimations` 的 slide 速度积分，而不是 `(1-progress)`。

### 4.2 `preLockSlideTime` 缺失 —— P3

原版 `GunAnimations`（`:133-134`）维护 `preLockSlideTime`，用于在锁定前留一小段延迟。移植侧没有对应字段或逻辑。影响极窄：只在"滑套即将锁定的瞬间"存在，玩家几乎不可察觉。

### 4.3 副手武器不触发挂机 —— P2

`GunFiring.java:403` 的条件带 `!left`。副手开火打空后 `boltCaught` 不会置位，因此：

- 副手枪不会显示空仓挂机；
- 副手枪的查膛不会走 `CHECK_CHAMBER_BC`。

原版 1.7.10/FMUM 没有真正意义上的副手枪（`PacketOffHandGunInfo` 是 FMUM 2.0 才有的机制），所以这不是"与原版不一致"，而是本移植在双持扩展上的一个未覆盖分支。是否修取决于双持是否算正式功能。

## 五、挂在滑套上的瞄具是否跟随

分两层，结论不同：

- **视觉层：跟随。** `renderAttachments`（`:408-425`）先对 bolt 节点施加位移，再在同一个 pose 内递归渲染子节点，所以挂在滑套上的瞄具模型会随滑套一起后坐。
- **开镜枢轴层：不跟随。** `LegacySightPose.forGun` 完全静态，没有滑套位移项；`TurborigItemRenderer:225` 用它计算 ADS 枢轴。原版用 `applyTransForSightsOnBolt`（`RenderGun.java:1872,1966-1970`、`SightCoor.java:61-62`）补偿。

当前 Bocchi 的 Crossline 在槽 1（导轨），不触发。带 `scopeIsOnSlide` 的模型会触发。详见 DS-SLIDE-01 §4.2。

## 六、已确认 / 尚不确定 / 未运行

**已确认**：§1 状态载体、§2 五条迁移路径、§3 渲染取值来源、§4.1/4.2/4.3 差异、§5 两层结论，均有源码行号。§3 中"移植侧取枪体标量而非滑套标量"这一点，用 `InspectBocchiSlide` 实测的两个模型标量交叉验证过。

**尚不确定**：
- §4.1 的线性/加速差异在实机是否可辨。需要逐帧截图或慢放对比。
- §4.3 是否需要修，取决于双持是否列为正式功能，这是设计决策。

**未运行**：未启动游戏、未模拟输入、未逐帧截图。本报告全部为静态调用链复核，不含运行时证据。任务书要求的"开火后归位、空仓挂机、手动拉栓、查膛、释放滑套"五项实机确认仍待完成。
