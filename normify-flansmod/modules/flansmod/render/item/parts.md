---
uid: f46d9514
id: flansmod.render.item.parts
parent: flansmod.render.item
state: planned
name: {zh: "部件动画", en: "Animated parts"}
description:
  zh: >
      按部件名驱动枪机、拉机柄、活塞等可动件的位移与旋转。
  en: >
      Drives slide, charging handle and piston travel and rotation by part name.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 6799b20c4df10252a11d879d70cc1dcf06e974805a4b9bb50925a270634795a4
source:
  - path: "src/main/java/com/flansmod/client/render/TurborigItemRenderer.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.TurborigItemRenderer.renderAnimatedParts"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
