---
uid: 2ba5bce3
id: flansmod.platform.network.gun
parent: flansmod.platform.network
state: planned
name: {zh: "射击载荷", en: "Firing payloads"}
description:
  zh: >
      扳机按下/释放、射击表现、抛壳表现与射击模式切换的网络消息。
  en: >
      Network messages for trigger press and release, shot presentation, casing ejection and fire-mode switching.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: bbe9dd46665a7d8845afcaf914dfc0019db105af7578502ec11b573f9c8b2d9d
source:
  - path: "src/main/java/com/flansmod/common/network/TriggerPayload.java"
  - path: "src/main/java/com/flansmod/common/network/GunShotPayload.java"
  - path: "src/main/java/com/flansmod/common/network/GunEjectionPayload.java"
  - path: "src/main/java/com/flansmod/common/network/FireModePayload.java"
apis:
  - protocol: rpc
    path: "rpc:flansmod:trigger"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:flansmod:gun_shot"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:flansmod:gun_ejection"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:flansmod:fire_mode"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
