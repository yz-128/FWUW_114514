---
uid: a7b265ed
id: flansmod.defs.gun.fields
parent: flansmod.defs.gun
state: planned
name: {zh: "枪械字段", en: "Gun fields"}
description:
  zh: >
      伤害、散布、射速、声音、弹药、配件白名单、握持与换弹姿态等字段的声明与默认值。
  en: >
      Declarations and defaults for damage, spread, rate of fire, sounds, ammunition, attachment whitelists, hold and reload poses.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: c3876f22401cfc9ea3f0e703101f529eb5450e45ecc3c82f09468b9c4c52775e
source:
  - path: "src/main/java/com/flansmod/common/types/GunType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.GunType.effectiveReloadTime"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.GunType.withAttachments"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
