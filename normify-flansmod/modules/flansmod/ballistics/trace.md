---
uid: 18bdc6cb
id: flansmod.ballistics.trace
parent: flansmod.ballistics
state: planned
name: {zh: "方块射线", en: "Block ray traversal"}
description:
  zh: >
      沿弹道逐格遍历方块并返回全部命中点，而不是只取最近的一个。
  en: >
      Walks blocks along the trajectory and returns every hit instead of only the nearest one.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: f7984a1d97d2339fb806a46a46abdc75ce3d06b0d66b2e3ba0add56648702739
source:
  - path: "src/main/java/com/flansmod/common/entity/BulletBlockTrace.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.entity.BulletBlockTrace.collect"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
