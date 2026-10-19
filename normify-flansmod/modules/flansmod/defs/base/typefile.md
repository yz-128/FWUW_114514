---
uid: 169bd4d8
id: flansmod.defs.base.typefile
parent: flansmod.defs.base
state: planned
name: {zh: "定义文件与类型枚举", en: "Definition file and type enum"}
description:
  zh: >
      承载文件内容、包归属与路径信息的载体，以及 info 类型到目录名的映射。
  en: >
      Carries file content, owning pack and path, plus the mapping from info type to folder name.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 403a19714619ffdf0b2397d3ae892416d39213bea70205447c964370e1d4373e
source:
  - path: "src/main/java/com/flansmod/common/types/TypeFile.java"
  - path: "src/main/java/com/flansmod/common/types/EnumType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.TypeFile.read"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.EnumType.folderName"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
