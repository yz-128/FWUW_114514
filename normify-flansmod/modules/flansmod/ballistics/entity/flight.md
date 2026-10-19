---
uid: 68c830e5
id: flansmod.ballistics.entity.flight
parent: flansmod.ballistics.entity
state: planned
name: {zh: "弹丸飞行", en: "Projectile flight"}
description:
  zh: >
      按速度与重力推进位置，做逐段射线检测以避免穿透薄方块。
  en: >
      Advances position by velocity and gravity, using stepped ray checks so thin blocks are not tunnelled through.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 2cd74c8d50c897eba21421474806fa903f343a388498facf80533390acd17842
source:
  - path: "src/main/java/com/flansmod/common/entity/EntityBullet.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.entity.EntityBullet.tick"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.entity.EntityBullet.rayTrace"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
