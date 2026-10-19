---
uid: 053d2112
id: flansmod.render.effects.world
parent: flansmod.render.effects
state: planned
name: {zh: "世界空间特效", en: "World-space effects"}
description:
  zh: >
      把特效从枪局部坐标换算到世界坐标。
  en: >
      Converts effects from gun-local into world coordinates.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: d4aa63ee522defada770d9634ffac8fe79a8b49463627c92f3d630fe6d6765ab
source:
  - path: "src/main/java/com/flansmod/client/render/FmumWorldEffectsPose.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.FmumWorldEffectsPose.apply"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
