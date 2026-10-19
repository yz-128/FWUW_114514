---
uid: f586b73f
id: flansmod.optics.reticle
parent: flansmod.optics
state: planned
name: {zh: "准星合成", en: "Reticle composition"}
description:
  zh: >
      把同轴组内各瞄具的准星投影到镜片 UV，按真实镜片条带裁切并插值贴图。
  en: >
      Projects each sight reticle in the coaxial group onto lens UVs, clipping against the real lens strips and interpolating the texture.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 148bf764c2ef3920cdd3e523eb9752e60f1a289e69299d38f584c26d8962da7f
source:
  - path: "src/main/java/com/flansmod/client/render/ScopeReticleRenderer.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.ScopeReticleRenderer.render"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.ScopeReticleRenderer.project"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
