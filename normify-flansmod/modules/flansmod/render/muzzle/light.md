---
uid: 47548b8a
id: flansmod.render.muzzle.light
parent: flansmod.render.muzzle
state: planned
name: {zh: "瞬时照明", en: "Transient lighting"}
description:
  zh: >
      射击瞬间对周围方块的短时补光。
  en: >
      Briefly lights surrounding blocks at the moment of the shot.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 5dfe3ed42a3bddd275ad87eb24be8a29487c946975356b69ce9db488cc66f77c
source:
  - path: "src/main/java/com/flansmod/client/render/MuzzleLighting.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.MuzzleLighting.flash"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
