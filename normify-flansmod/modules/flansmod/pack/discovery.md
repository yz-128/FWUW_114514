---
uid: dbed0498
id: flansmod.pack.discovery
parent: flansmod.pack
state: planned
name: {zh: "包发现", en: "Pack discovery"}
description:
  zh: >
      扫描 Flan 目录，把散装目录与 .zip/.jar 都登记成内容包，并给出统一的资源命名空间。
  en: >
      Scans the Flan directory, registers loose folders and .zip/.jar files as content packs and exposes a unified resource namespace.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 8b99cc079d31d785d4c1c72fab41d9c43918f619baacf268a9405921a2b1907b
source:
  - path: "src/main/java/com/flansmod/common/ContentPackIndex.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.ContentPackIndex.scan"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.ContentPackIndex.generation"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
