---
uid: 2e150b7c
id: flansmod.client.input.suppress
parent: flansmod.client.input
state: planned
name: {zh: "输入抑制", en: "Input suppression"}
description:
  zh: >
      在持枪时抑制原版的使用/攻击动作，避免吃东西或挥手。
  en: >
      Suppresses vanilla use and attack behaviour while holding a gun so the player does not eat or swing.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 2721ed627cde6fe958f6a01a12ba7be575e826456d2d60a421b16d2059593c46
source:
  - path: "src/main/java/com/flansmod/client/GunInputSuppressor.java"
apis:
  - protocol: rpc
    path: "event:InputEvent.InteractionKeyMappingTriggered"
    description:
      zh: >
          监听该事件。
      en: >
          Subscribes to this event.
  - protocol: rpc
    path: "rpc:com.flansmod.client.GunInputSuppressor.suppress"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
