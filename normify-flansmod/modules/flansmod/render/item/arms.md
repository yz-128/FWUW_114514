---
uid: 64ec05d9
id: flansmod.render.item.arms
parent: flansmod.render.item
state: planned
name: {zh: "手臂", en: "Arms"}
description:
  zh: >
      绘制包自带的手臂模型并按握持点放置，而不是使用原版手臂。
  en: >
      Draws the pack-supplied arm model positioned at the grip instead of the vanilla arm.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 6799b20c4df10252a11d879d70cc1dcf06e974805a4b9bb50925a270634795a4
source:
  - path: "src/main/java/com/flansmod/client/render/TurborigItemRenderer.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.TurborigItemRenderer.renderArms"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.TurborigItemRenderer.drawArm"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
