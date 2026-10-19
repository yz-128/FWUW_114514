---
uid: 08f4a857
id: flansmod.defs.gun.smoke
parent: flansmod.defs.gun
state: planned
name: {zh: "枪口烟参数", en: "Smoke parameters"}
description:
  zh: >
      枪口烟与抛壳口烟的模型、贴图、时长、速度与扩散范围。
  en: >
      Model, texture, duration, speed and spread for muzzle and chamber smoke.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: c3876f22401cfc9ea3f0e703101f529eb5450e45ecc3c82f09468b9c4c52775e
source:
  - path: "src/main/java/com/flansmod/common/types/GunType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.GunType.parseSmoke"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
