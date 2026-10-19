---
uid: 6ed412b4
id: flansmod.defs.bullet.effects
parent: flansmod.defs.bullet
state: planned
name: {zh: "命中效果", en: "Hit effects"}
description:
  zh: >
      把药水效果名解析成注册表持有者，并记录持续时间与概率。
  en: >
      Resolves potion effect names into registry holders and records duration and chance.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 2520364497ffb55ebf4f00e268399272d3a4642d422d01903f164c79d8996c83
source:
  - path: "src/main/java/com/flansmod/common/types/BulletType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.BulletType.resolvePotionEffect"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
