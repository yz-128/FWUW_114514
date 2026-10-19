---
uid: bb47bec3
id: flansmod.defs.attachment.parser
parent: flansmod.defs.attachment
state: planned
name: {zh: "配件解析", en: "Attachment parsing"}
description:
  zh: >
      逐条解析配件定义，包括 ZoomLevel 的多档位数组、DotModel 的帧数与 Paintjob 列表。
  en: >
      Parses attachment definitions line by line, including the multi-step ZoomLevel array, DotModel frame count and paintjob list.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 4321460b11d7c5f6d4188cd8a98553056372b54f7db6e3e4f14f5dd9a96f4d61
source:
  - path: "src/main/java/com/flansmod/common/types/AttachmentType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.AttachmentType.read"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.AttachmentType.getAttachment"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
