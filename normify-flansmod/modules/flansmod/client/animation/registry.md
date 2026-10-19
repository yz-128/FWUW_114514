---
uid: 2e9655c6
id: flansmod.client.animation.registry
parent: flansmod.client.animation
state: planned
name: {zh: "动画注册表", en: "Animation registry"}
description:
  zh: >
      按玩家持有枪械注册动画对象，并提供默认值。
  en: >
      Registers animation objects per player-held gun and supplies defaults.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 563f58b2f8ded698fd9b0703f7f7278a576313e32c851251a11d8ae955e87c1a
source:
  - path: "src/main/java/com/flansmod/client/GunAnimationRegistry.java"
  - path: "src/main/java/com/flansmod/client/model/GunAnimations.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.GunAnimationRegistry.right"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.GunAnimations.tick"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
