---
uid: dd867c5d
id: flansmod.defs.gun.parser
parent: flansmod.defs.gun
state: planned
name: {zh: "枪械解析", en: "Gun parsing"}
description:
  zh: >
      按 1.7.10 的键名逐条解析枪械定义，包含别名键、单位换算与后读修正。
  en: >
      Parses gun definitions key by key following 1.7.10, including alias keys, unit conversion and post-read fixes.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: c3876f22401cfc9ea3f0e703101f529eb5450e45ecc3c82f09468b9c4c52775e
source:
  - path: "src/main/java/com/flansmod/common/types/GunType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.GunType.read"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.GunType.postRead"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
