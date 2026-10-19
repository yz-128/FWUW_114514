---
uid: c4b2f51e
id: flansmod.model.dev.preview
parent: flansmod.model.dev
state: planned
name: {zh: "装配预览", en: "Assembly preview"}
description:
  zh: >
      离线渲染整枪装配与贴图版本，用于在无人操作时核对模型与材质。
  en: >
      Renders whole-gun assemblies and textured variants offline for checking models and materials without manual play.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 03fe5626a65aaa9f02a97876944a2cecb27b3fd026b667a0b1ba9871327f284c
source:
  - path: "src/main/java/com/flansmod/client/model/loader/RenderAssembly.java"
  - path: "src/main/java/com/flansmod/client/model/loader/RenderPreview.java"
  - path: "src/main/java/com/flansmod/client/model/loader/RenderPreviewTex.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.RenderAssembly.render"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.RenderPreview.render"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
