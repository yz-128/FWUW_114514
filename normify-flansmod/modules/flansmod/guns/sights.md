---
uid: ec0322de
id: flansmod.guns.sights
parent: flansmod.guns
state: planned
name: {zh: "瞄具选择", en: "Sight selection"}
description:
  zh: >
      按共轴分组枚举瞄具、记录每把枪选中的组与档位，并处理折叠联动目标。
  en: >
      Enumerates sights into coaxial groups, records each gun selected group and step, and computes fold toggle targets.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 0d96be54148177996a9d72106c3d5ab267414ecdd43f11b8b13c87d9e2169ef8
source:
  - path: "src/main/java/com/flansmod/common/GunSightState.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunSightState.groups"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunSightState.scopeSight"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunSightState.apply"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
