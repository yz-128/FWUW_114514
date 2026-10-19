---
uid: ac0fb0ef
id: flansmod.client.input.reload
parent: flansmod.client.input
state: planned
name: {zh: "换弹键", en: "Reload key"}
description:
  zh: >
      R 的点击/双击/长按，以及 G、H、B 与 O/V 的绑定。
  en: >
      Tap, double-tap and hold on R, plus the G, H, B and O/V bindings.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 4dd71d90b100d6cadbaaaa2ccf4f5b5d51c27d506bb33f09cb70674581c28ac0
source:
  - path: "src/main/java/com/flansmod/client/ReloadKeyHandler.java"
apis:
  - protocol: rpc
    path: "event:ClientTickEvent.Post"
    description:
      zh: >
          监听该事件。
      en: >
          Subscribes to this event.
  - protocol: rpc
    path: "rpc:com.flansmod.client.ReloadKeyHandler.onKey"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
