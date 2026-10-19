---
uid: 2c3e7799
id: flansmod.render.pose.motion
parent: flansmod.render.pose
state: planned
name: {zh: "移动与第三人称", en: "Motion and third person"}
description:
  zh: >
      行走抖动、冲刺摆臂与第三人称手部框架。
  en: >
      Walk shake, sprint swing and the third-person hand frame.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 8d63d8f62e6e0c59ce3334e1afb9c43e2a845ec32b3f456c2caf8d7f42add114
source:
  - path: "src/main/java/com/flansmod/client/render/LegacyGunMotion.java"
  - path: "src/main/java/com/flansmod/client/render/LegacyThirdPersonPose.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.LegacyGunMotion.frame"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.LegacyThirdPersonPose.handFrame"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
