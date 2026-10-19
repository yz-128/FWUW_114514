---
uid: 481cdbaa
id: flansmod.defs.gun.modes
parent: flansmod.defs.gun
state: planned
name: {zh: "发射模式", en: "Fire modes"}
description:
  zh: >
      单发、连发、三连发、保险与加特林等模式的定义、切换顺序与自动射击判定。
  en: >
      Definitions, cycling order and automatic-fire predicates for semi, full-auto, burst, safe and minigun modes.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: c3876f22401cfc9ea3f0e703101f529eb5450e45ecc3c82f09468b9c4c52775e
source:
  - path: "src/main/java/com/flansmod/common/types/GunType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.GunType.modeFor"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.GunType.nextMode"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
