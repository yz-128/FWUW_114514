---
uid: 496203ac
id: flansmod.model.geometry
parent: flansmod.model
state: planned
name: {zh: "几何模型", en: "Geometry model"}
description:
  zh: >
      与渲染无关的几何描述：部件、盒、棱柱、贴图偏移与枢轴。
  en: >
      Renderer-independent geometry: parts, boxes, prisms, texture offsets and pivots.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 9095c95930f2fac4e7d255f237e8d1d2daabe57783564a1d9e68f3ab18db540e
source:
  - path: "src/main/java/com/flansmod/client/model/loader/ModelGeometry.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.ModelGeometry.Part"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.ModelGeometry.Box"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
