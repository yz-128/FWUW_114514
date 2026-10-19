---
uid: 89961ab6
id: flansmod.guns.firing.shot
parent: flansmod.guns.firing
state: planned
name: {zh: "射击与命中", en: "Shot and impact"}
description:
  zh: >
      检查弹药、计算散布、生成弹丸并触发枪口表现与抛壳。
  en: >
      Checks ammunition, computes spread, spawns the projectile and triggers muzzle effects and casing ejection.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 4dad223a0b67028ba9fba34308ccc348e872ac7152826785ef9303a22760650f
source:
  - path: "src/main/java/com/flansmod/common/GunFiring.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunFiring.tryToShoot"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunFiring.fireRound"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunFiring.liveRound"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
