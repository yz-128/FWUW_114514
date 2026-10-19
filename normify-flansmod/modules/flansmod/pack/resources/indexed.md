---
uid: 2c053eb9
id: flansmod.pack.resources.indexed
parent: flansmod.pack.resources
state: planned
name: {zh: "索引式资源", en: "Indexed resources"}
description:
  zh: >
      用预先建立的条目索引回答资源查询，避免每次读取都对 ZIP 做一次全表扫描。
  en: >
      Answers resource lookups from a prebuilt entry index instead of rescanning the archive on every read.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: de6d6b6ac2422b4be3660ac919fa48bde20019b39aa56490b9e7259a9aa9dcf9
source:
  - path: "src/main/java/com/flansmod/common/IndexedPackResources.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.IndexedPackResources.open"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
