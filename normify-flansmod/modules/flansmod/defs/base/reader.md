---
uid: d2d56550
id: flansmod.defs.base.reader
parent: flansmod.defs.base
state: planned
name: {zh: "定义读取器", en: "Definition reader"}
description:
  zh: >
      逐行解析与类型无关的公共键，并处理注释、引号与数值容错；配方网格也从这里读。
  en: >
      Parses type-independent keys line by line, handling comments, quoting and number tolerance; the recipe grid is read here too.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: f16ed18ea20375682d9b01a105cc3dc6e5d8b5f7129be88e024e61fa5a7a7f03
source:
  - path: "src/main/java/com/flansmod/common/types/InfoType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.InfoType.read"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.InfoType.splitConfigLine"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
