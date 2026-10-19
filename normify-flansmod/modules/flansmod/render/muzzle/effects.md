---
uid: cca1c9ba
id: flansmod.render.muzzle.effects
parent: flansmod.render.muzzle
state: planned
name: {zh: "枪口效果合成", en: "Muzzle effect composition"}
description:
  zh: >
      管理活跃的枪口焰/烟/弹壳，并按当前通道选择绘制时机与光源位置。
  en: >
      Manages live flashes, smoke and casings and picks the draw pass and light position for each.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 38fd0fd597e6970e64e3f6253d18c2010638658da63211117fd4e1a4035af362
source:
  - path: "src/main/java/com/flansmod/client/render/MuzzleEffectsRenderer.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.MuzzleEffectsRenderer.render"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.MuzzleEffectsRenderer.remote"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
