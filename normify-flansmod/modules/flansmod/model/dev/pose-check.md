---
uid: dbec3fe7
id: flansmod.model.dev.pose-check
parent: flansmod.model.dev
state: planned
name: {zh: "姿态核对", en: "Pose check"}
description:
  zh: >
      把模型姿态与实测结果对照，定位"模型在但看起来不对"的问题。
  en: >
      Compares model poses against measured references to locate "the model is there but looks wrong" problems.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: b6e739294998c47a671d5e1d10c2f267da5ed1ff7a7e26f1e1ca11c5a59f6d9f
source:
  - path: "src/main/java/com/flansmod/client/model/loader/PoseCheck.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.PoseCheck.run"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
