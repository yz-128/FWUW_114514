---
uid: 2b54fe1a
id: flansmod.pack.decoding
parent: flansmod.pack
state: planned
name: {zh: "文本解码", en: "Text decoding"}
description:
  zh: >
      按 BOM 与启发式规则解码定义文件：1.7.10 时代的包大量使用 GBK/UTF-8 混用，直接按 UTF-8 读会产生乱码键名。
  en: >
      Decodes definition files using BOM and heuristics: 1.7.10-era packs mix GBK and UTF-8, and reading them as plain UTF-8 corrupts key names.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 23b1232dc54e444a395080cbba1573e8c970b67c4746ae232d05e15b0ee88b7b
source:
  - path: "src/main/java/com/flansmod/common/TextDecoding.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.TextDecoding.readLines"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
