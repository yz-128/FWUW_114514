---
uid: "34516409"
id: flansmod.optics.fold.pose
parent: flansmod.optics.fold
state: planned
name: {zh: "折叠姿态", en: "Fold pose"}
description:
  zh: >
      折叠进度作用到武器与子配件的姿态矩阵。
  en: >
      Applies fold progress to the weapon and child part matrices.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 81ca482a90c2c07c15562a1939d5cd374684571e13f42780b2325401e2ea73b9
source:
  - path: "src/main/java/com/flansmod/client/render/SightFoldPose.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.SightFoldPose.weapon"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
