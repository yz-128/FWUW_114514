---
uid: 93995c73
id: flansmod.model.bytecode.primitives
parent: flansmod.model.bytecode
state: planned
name: {zh: "调用与常量", en: "Calls and constants"}
description:
  zh: >
      解释常见方法调用与常量压栈，把 addBox、setRotationPoint 等调用还原成几何。
  en: >
      Interprets common method calls and constant pushes, turning addBox, setRotationPoint and friends back into geometry.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: dc421eff56474ef1a70bdff12b5029e1aecdf93e08960beea18ce4bcf14a50ba
source:
  - path: "src/main/java/com/flansmod/client/model/loader/BytecodeModel.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.BytecodeModel.handle"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.BytecodeModel.push"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
