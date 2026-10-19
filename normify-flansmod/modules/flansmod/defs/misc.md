---
uid: 34b9498d
id: flansmod.defs.misc
parent: flansmod.defs
state: planned
name: {zh: "其它物品定义", en: "Other item definitions"}
description:
  zh: >
      手雷、零件与工具的定义读取。
  en: >
      Definition readers for grenades, parts and tools.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: f720a766b658c45a93fc277ef085444ae3b850507cf60cfb5f35501f3c5a143f
source:
  - path: "src/main/java/com/flansmod/common/types/GrenadeType.java"
  - path: "src/main/java/com/flansmod/common/types/PartType.java"
  - path: "src/main/java/com/flansmod/common/types/ToolType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.GrenadeType.read"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.PartType.read"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.ToolType.read"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
