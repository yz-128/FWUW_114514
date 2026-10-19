---
uid: 48e6d198
id: flansmod.guns.stats
parent: flansmod.guns
state: planned
name: {zh: "数值聚合", en: "Stat aggregation"}
description:
  zh: >
      把基础数值与所有已装配件的倍率/增量递归相乘相加，得出有效伤害、后坐力与散布。
  en: >
      Recursively combines base values with every mounted part multiplier and delta to produce effective damage, recoil and spread.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 2881decf7f12f3c589404e543e44a5c69a1a9f99bd224a996419d72a540f1aae
source:
  - path: "src/main/java/com/flansmod/common/GunStats.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunStats.effective"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunStats.firingSpread"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
