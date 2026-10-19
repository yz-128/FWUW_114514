---
uid: "5600e388"
id: flansmod.model.bytecode.arrays
parent: flansmod.model.bytecode
state: planned
name: {zh: "向量与数组", en: "Vectors and arrays"}
description:
  zh: >
      捕获写入继承数组的元素与对继承向量的 set 调用，这是准星与镜片数据能读到的关键。
  en: >
      Captures element writes into inherited arrays and set calls on inherited vectors, which is what makes reticle and lens data readable.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: dc421eff56474ef1a70bdff12b5029e1aecdf93e08960beea18ce4bcf14a50ba
source:
  - path: "src/main/java/com/flansmod/client/model/loader/BytecodeModel.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.BytecodeModel.captureVectorArrays"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.BytecodeModel.captureArrayElementSetters"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
