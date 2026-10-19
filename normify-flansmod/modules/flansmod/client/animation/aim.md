---
uid: 75e3a18d
id: flansmod.client.animation.aim
parent: flansmod.client.animation
state: planned
name: {zh: "瞄准进度", en: "Aim progress"}
description:
  zh: >
      瞄准进度是"每 tick 保留系数"而不是固定时长，因此开镜速度由 AimTime 合成得出。
  en: >
      Aim progress is a per-tick retention coefficient rather than a fixed duration, so raise speed is derived from AimTime.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: d3d4467151a1fecf98819d14d0ffdb305528e94b5a2a990a7a3f1d331cadbd28
source:
  - path: "src/main/java/com/flansmod/client/AimState.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.AimState.tick"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.AimState.blend"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
