---
uid: c2f1cd2e
id: flansmod.optics.sight
parent: flansmod.optics
state: planned
name: {zh: "瞄具绘制", en: "Sight rendering"}
description:
  zh: >
      按瞄具是否有镜片分派：有镜片的走镜内合成，红点镜走自身准星绘制。
  en: >
      Dispatches by whether the sight has glass: optics composite into the lens while red dots draw their own reticle.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 4f9836a611054068eb4b0d7d003b4967e5dcf7317278557841bd059b088db697
source:
  - path: "src/main/java/com/flansmod/client/render/FmumSightRenderer.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.FmumSightRenderer.render"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
