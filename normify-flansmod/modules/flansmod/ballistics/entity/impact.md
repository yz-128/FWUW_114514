---
uid: 7dc51b29
id: flansmod.ballistics.entity.impact
parent: flansmod.ballistics.entity
state: planned
name: {zh: "命中结算", en: "Impact resolution"}
description:
  zh: >
      命中实体与方块时应用伤害、效果、穿透与掉落。
  en: >
      Applies damage, effects, penetration and drops when hitting entities or blocks.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 2cd74c8d50c897eba21421474806fa903f343a388498facf80533390acd17842
source:
  - path: "src/main/java/com/flansmod/common/entity/EntityBullet.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.entity.EntityBullet.onHitEntity"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.entity.EntityBullet.onHitBlock"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
