---
uid: e5a30b20
id: flansmod.defs.modifiable.hitbox
parent: flansmod.defs.modifiable
state: planned
name: {zh: "命中箱", en: "Hit boxes"}
description:
  zh: >
      HitBox/BigHitBox 的包围盒解析，用于配件冲突检测与枪管长度推算。
  en: >
      Parses HitBox/BigHitBox bounds for attachment conflict checks and gun-length estimation.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 0c027889bbddfb25bb61736b42abce13b3c5f94c23a6252e02e51a10c9f0293f
source:
  - path: "src/main/java/com/flansmod/common/types/ModifiableType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.ModifiableType.parseHitBoxes"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
