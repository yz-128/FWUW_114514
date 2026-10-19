---
uid: b3be82a2
id: flansmod.guns.firing.mode
parent: flansmod.guns.firing
state: planned
name: {zh: "发射模式切换", en: "Fire mode switching"}
description:
  zh: >
      在可用模式之间循环并写回物品；客户端立即切换，服务端的结果最终覆盖。
  en: >
      Cycles through the available modes and writes the choice back; the client switches immediately and the server result wins.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 4dad223a0b67028ba9fba34308ccc348e872ac7152826785ef9303a22760650f
source:
  - path: "src/main/java/com/flansmod/common/GunFiring.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunFiring.serverCycleFireMode"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
