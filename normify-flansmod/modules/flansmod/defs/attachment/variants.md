---
uid: 256a328c
id: flansmod.defs.attachment.variants
parent: flansmod.defs.attachment
state: planned
name: {zh: "折叠与联动变体", en: "Fold and co-sight variants"}
description:
  zh: >
      ShadowType 决定的折叠/联动/共轴行为：哪些配件是折叠态、哪些是切换目标。
  en: >
      Fold, toggle and co-sight behaviour driven by ShadowType: which parts are the folded state and which are the toggle targets.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 4321460b11d7c5f6d4188cd8a98553056372b54f7db6e3e4f14f5dd9a96f4d61
source:
  - path: "src/main/java/com/flansmod/common/types/AttachmentType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.AttachmentType.isFoldedVariant"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.AttachmentType.isCoSight"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
