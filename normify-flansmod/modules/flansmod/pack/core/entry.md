---
uid: acfbe602
id: flansmod.pack.core.entry
parent: flansmod.pack.core
state: planned
name: {zh: "包条目模型", en: "Pack entry model"}
description:
  zh: >
      单个条目的抽象与已索引包的类型：条目可按需打开流，包则描述它的根与条目数量。
  en: >
      The single-entry abstraction and the indexed-pack type: an entry opens a stream on demand while a pack describes its root and entry count.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 87cae07f2be77b209651dc6ef6c7a679e50f68bbbc232664f51754adeef96975
source:
  - path: "src/main/java/dev/packarms/core/PackEntry.java"
  - path: "src/main/java/dev/packarms/core/IndexedPack.java"
apis:
  - protocol: rpc
    path: "rpc:dev.packarms.core.PackEntry.open"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:dev.packarms.core.IndexedPack.entries"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
