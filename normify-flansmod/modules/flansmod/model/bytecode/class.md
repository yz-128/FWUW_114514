---
uid: 518c5bc7
id: flansmod.model.bytecode.class
parent: flansmod.model.bytecode
state: planned
name: {zh: "类结构与构造", en: "Class structure and constructor"}
description:
  zh: >
      解析类头、字段表与构造函数，识别渲染器数组与几何写入模式。
  en: >
      Parses the class header, field table and constructor, recognising renderer arrays and geometry-write patterns.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: dc421eff56474ef1a70bdff12b5029e1aecdf93e08960beea18ce4bcf14a50ba
source:
  - path: "src/main/java/com/flansmod/client/model/loader/BytecodeModel.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.BytecodeModel.fromClassNode"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.BytecodeModel.constructor"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
