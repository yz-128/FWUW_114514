---
uid: f04922ef
id: flansmod.platform.registry
parent: flansmod.platform
state: planned
name: {zh: "物品注册", en: "Item registration"}
description:
  zh: >
      把解析完成的内容包类型逐个注册成游戏物品，并保持包与物品之间的双向查找。
  en: >
      Registers every parsed content-pack type as a game item and keeps the two-way lookup between packs and items.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 3762e76206d17f6fe0622c054da06e6a152afbb50b0236043c8e7fc0a1cd349c
source:
  - path: "src/main/java/com/flansmod/common/ContentRegistry.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.ContentRegistry.registerAll"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
