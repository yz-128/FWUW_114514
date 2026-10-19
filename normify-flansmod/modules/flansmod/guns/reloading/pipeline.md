---
uid: 5a1d2eda
id: flansmod.guns.reloading.pipeline
parent: flansmod.guns.reloading
state: planned
name: {zh: "换弹阶段驱动", en: "Reload phase driver"}
description:
  zh: >
      按包的 ReloadSteps/ReloadTimes 推进阶段，处理强制换弹与换弹中途取消。
  en: >
      Advances phases from the pack ReloadSteps/ReloadTimes, handling forced reloads and mid-reload cancellation.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 6bf47c838b93f328e92a63bb9ded6db9b1b6ebdb2daa97392b7e6136b78666d4
source:
  - path: "src/main/java/com/flansmod/common/GunReloading.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunReloading.start"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunReloading.tick"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunReloading.cancel"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
