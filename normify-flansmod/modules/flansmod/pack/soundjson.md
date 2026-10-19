---
uid: efc90498
id: flansmod.pack.soundjson
parent: flansmod.pack
state: planned
name: {zh: "声音定义重写", en: "Sound definition rewriting"}
description:
  zh: >
      把 1.7.10 的 sounds.json 结构改写成现代资源包期望的格式。
  en: >
      Rewrites the 1.7.10 sounds.json structure into the layout a modern resource pack expects.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 934179b423dbe8bd6ec6721f6fec7a5564f791a8de0be3f081808a36d1fdcdc6
source:
  - path: "src/main/java/com/flansmod/common/SoundJsonRewriter.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.SoundJsonRewriter.rewrite"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
