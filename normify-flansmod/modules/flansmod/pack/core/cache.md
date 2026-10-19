---
uid: 7c9f08cd
id: flansmod.pack.core.cache
parent: flansmod.pack.core
state: planned
name: {zh: "资源惰性缓存", en: "Lazy asset cache"}
description:
  zh: >
      按需读取并缓存资源，带解析状态与统计，避免同一模型/贴图被重复解压。
  en: >
      Reads and caches assets on demand with resolution state and statistics, so the same model or texture is not decompressed twice.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: f6eb3277f661ca5c88cdbda07ddab06d6d86797d4d3d9c719ffc057deefae41c
source:
  - path: "src/main/java/dev/packarms/core/LazyAssetCache.java"
apis:
  - protocol: rpc
    path: "rpc:dev.packarms.core.LazyAssetCache.get"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:dev.packarms.core.LazyAssetCache.stats"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
