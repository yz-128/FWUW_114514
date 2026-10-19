---
uid: c488957a
id: flansmod.defs.modifiable.defaults
parent: flansmod.defs.modifiable
state: planned
name: {zh: "默认装配树", en: "Default attachment tree"}
description:
  zh: >
      DefaultAttachments 的嵌套语法（next、方括号、步进、偏移、涂装）解析成树，并计算每个节点的实际位置。
  en: >
      Parses the nested DefaultAttachments syntax (next, brackets, steps, offsets, paintjobs) into a tree and resolves each node position.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 0c027889bbddfb25bb61736b42abce13b3c5f94c23a6252e02e51a10c9f0293f
source:
  - path: "src/main/java/com/flansmod/common/types/ModifiableType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.ModifiableType.parseDefaultAttachments"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.ModifiableType.resolveDefaultAttachments"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
