---
uid: 7e224f19
id: flansmod.defs.bullet.parser
parent: flansmod.defs.bullet
state: planned
name: {zh: "弹药解析", en: "Bullet parsing"}
description:
  zh: >
      逐条解析弹药定义并解析弹药引用的物品与效果。
  en: >
      Parses bullet definitions line by line and resolves referenced items and effects.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 2520364497ffb55ebf4f00e268399272d3a4642d422d01903f164c79d8996c83
source:
  - path: "src/main/java/com/flansmod/common/types/BulletType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.BulletType.read"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.BulletType.getBullet"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
