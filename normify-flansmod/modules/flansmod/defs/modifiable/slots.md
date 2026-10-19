---
uid: 775a9076
id: flansmod.defs.modifiable.slots
parent: flansmod.defs.modifiable
state: planned
name: {zh: "挂点与槽位", en: "Mount points and slots"}
description:
  zh: >
      AttachPoints / AttachVectors / AttachRotates 读成可用的槽位，并按模型缩放换算成方块单位。
  en: >
      Reads AttachPoints / AttachVectors / AttachRotates into usable slots and scales them into block units with the model scale.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 0c027889bbddfb25bb61736b42abce13b3c5f94c23a6252e02e51a10c9f0293f
source:
  - path: "src/main/java/com/flansmod/common/types/ModifiableType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.ModifiableType.readSlots"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
