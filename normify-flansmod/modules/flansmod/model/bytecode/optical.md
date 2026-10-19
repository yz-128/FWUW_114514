---
uid: 8e340dea
id: flansmod.model.bytecode.optical
parent: flansmod.model.bytecode
state: planned
name: {zh: "光学字段", en: "Optical fields"}
description:
  zh: >
      还原瞄具的瞳孔参考点、镜片缩放、物镜中心与准星边框等光学字段，包括旧版别名。
  en: >
      Recovers the sight pupil reference, lens scale, objective centre and reticle border, including legacy aliases.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: dc421eff56474ef1a70bdff12b5029e1aecdf93e08960beea18ce4bcf14a50ba
source:
  - path: "src/main/java/com/flansmod/client/model/loader/BytecodeModel.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.BytecodeModel.opticalAliases"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.BytecodeModel.vectorField"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
