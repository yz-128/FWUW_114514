---
uid: b7001bc3
id: flansmod.ballistics.soft
parent: flansmod.ballistics
state: planned
name: {zh: "软质方块", en: "Soft blocks"}
description:
  zh: >
      草、树叶、玻璃板等可穿透方块的速度衰减与额外伤害判定。
  en: >
      Speed attenuation and extra damage rules for penetrable blocks such as grass, leaves and panes.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 1339d4268b120ececb2e41360e97b9ccd6ee2700bcd841c94c813840cc7d615e
source:
  - path: "src/main/java/com/flansmod/common/entity/SoftBlockBallistics.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.entity.SoftBlockBallistics.attenuate"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
