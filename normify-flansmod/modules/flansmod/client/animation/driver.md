---
uid: 6c4f01ac
id: flansmod.client.animation.driver
parent: flansmod.client.animation
state: planned
name: {zh: "驱动主循环", en: "Driver loop"}
description:
  zh: >
      每 tick 推进瞄准进度、动作状态、冲刺姿态与射击时序。
  en: >
      Advances aim progress, action state, sprint stance and shot timing each tick.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 3a60f75ddc60555f4527e8efd2371b45be031268ce04b9ad7c9bdb170acf1ecf
source:
  - path: "src/main/java/com/flansmod/client/GunAnimationDriver.java"
apis:
  - protocol: rpc
    path: "event:PlayerTickEvent.Post"
    description:
      zh: >
          监听该事件。
      en: >
          Subscribes to this event.
  - protocol: rpc
    path: "rpc:com.flansmod.client.GunAnimationDriver.onPlayerTick"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
