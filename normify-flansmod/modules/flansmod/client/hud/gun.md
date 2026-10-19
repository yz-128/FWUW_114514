---
uid: a4607b68
id: flansmod.client.hud.gun
parent: flansmod.client.hud
state: planned
name: {zh: "枪械 HUD", en: "Gun HUD"}
description:
  zh: >
      准星下方显示剩余弹药与射击模式，持枪时隐藏原版准星。
  en: >
      Shows remaining ammunition and fire mode below the crosshair and hides the vanilla crosshair while a gun is held.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 37872c481a2742f9a79241da84690781a4b48b43550f21d4bb17d29398bdeaae
source:
  - path: "src/main/java/com/flansmod/client/GunHud.java"
apis:
  - protocol: rpc
    path: "event:RenderGuiLayerEvent.Pre"
    description:
      zh: >
          监听该事件。
      en: >
          Subscribes to this event.
  - protocol: rpc
    path: "rpc:com.flansmod.client.GunHud.render"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
