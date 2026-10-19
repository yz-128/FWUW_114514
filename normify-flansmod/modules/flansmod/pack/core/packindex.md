---
uid: "76465020"
id: flansmod.pack.core.packindex
parent: flansmod.pack.core
state: planned
name: {zh: "包索引引擎", en: "Pack index engine"}
description:
  zh: >
      第三方 pack-core 的索引实现：把目录或压缩包读成一份条目索引，供上层按路径查询。
  en: >
      The vendored pack-core index: reads a directory or archive into an entry index that upper layers query by path.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 1ebebe993262c2aab87088c022fbffdfd588a80a5174942b0eb67a0f5fc56a37
source:
  - path: "src/main/java/dev/packarms/core/PackIndex.java"
  - path: "src/main/java/dev/packarms/core/PackPaths.java"
  - path: "src/main/java/dev/packarms/core/ScanPolicy.java"
  - path: "src/main/java/dev/packarms/core/ScanProblem.java"
apis:
  - protocol: rpc
    path: "rpc:dev.packarms.core.PackIndex.open"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:dev.packarms.core.PackPaths.normalise"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
