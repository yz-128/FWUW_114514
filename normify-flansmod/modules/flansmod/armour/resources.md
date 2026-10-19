---
uid: 04b0fae9
id: flansmod.armour.resources
parent: flansmod.armour
state: planned
name: {zh: "护甲资源", en: "Armour resources"}
description:
  zh: >
      解析护甲模型与贴图引用，并把缺失资源回落到可诊断的默认值。
  en: >
      Resolves armour model and texture references, falling back to diagnosable defaults when missing.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 228c4f22dafb36f16c865f723714c1c1e61c55bcad6be562912b7ec40aa8e475
source:
  - path: "src/main/java/com/flansmod/client/render/ArmourResources.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.ArmourResources.model"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.ArmourResources.texture"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
