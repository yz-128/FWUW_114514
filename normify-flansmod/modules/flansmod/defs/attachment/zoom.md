---
uid: 326fa0a6
id: flansmod.defs.attachment.zoom
parent: flansmod.defs.attachment
state: planned
name: {zh: "倍率与灵敏度", en: "Zoom and sensitivity"}
description:
  zh: >
      把包内倍率权重换算成实际光学倍率与鼠标灵敏度倍率；权重是名义倍率的十倍，这是内容包实测结论。
  en: >
      Converts the pack zoom weight into optical magnification and a mouse-sensitivity multiplier; the weight is ten times the nominal power, measured from the packs.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 4321460b11d7c5f6d4188cd8a98553056372b54f7db6e3e4f14f5dd9a96f4d61
source:
  - path: "src/main/java/com/flansmod/common/types/AttachmentType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.AttachmentType.zoomDivisor"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.AttachmentType.sensitivityMultiplier"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
