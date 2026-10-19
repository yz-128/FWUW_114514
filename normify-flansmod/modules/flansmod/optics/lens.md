---
uid: 895002d7
id: flansmod.optics.lens
parent: flansmod.optics
state: planned
name: {zh: "镜片成像", en: "Lens image"}
description:
  zh: >
      镜片按屏幕 1:1 取样已放大的世界画面；不再裁切成品帧，因此镜内保持主渲染分辨率。
  en: >
      The lens samples the already magnified world at 1:1 screen coordinates; it no longer crops the finished frame, so the ocular keeps the main render resolution.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 44702384ea2a952397ad66c5372c2229424d3c894312b9b335e26dce1bccf97d
source:
  - path: "src/main/java/com/flansmod/client/render/ScopeWorldTexture.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.ScopeWorldTexture.copyColor"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.ScopeWorldTexture.available"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
