---
uid: f03c2d0c
id: flansmod.render.pose.action
parent: flansmod.render.pose
state: planned
name: {zh: "动作姿态", en: "Action pose"}
description:
  zh: >
      检视、收枪等动作的姿态偏移与跟随计算。
  en: >
      Pose offsets and follow math for inspection and rest actions.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 0f2e8b9030abc667ec3bee88e38049dbb81396868167fb285f48c087180e1f47
source:
  - path: "src/main/java/com/flansmod/client/render/FmumActionPose.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.FmumActionPose.ads"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.FmumActionPose.follow"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
