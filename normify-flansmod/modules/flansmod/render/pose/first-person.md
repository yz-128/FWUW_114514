---
uid: 6ac4d9ca
id: flansmod.render.pose.first-person
parent: flansmod.render.pose
state: planned
name: {zh: "第一人称姿态", en: "First-person pose"}
description:
  zh: >
      握持/瞄准/冲刺/换弹四组向量混合，再叠加后坐力与瞄具枢轴。
  en: >
      Blends hold, aim, sprint and reload vectors, then layers recoil and the sight pivot on top.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 51562ad8823a3af1c1f09014b78c6f828ad9868d74a15c5d0508098ed7954cb0
source:
  - path: "src/main/java/com/flansmod/client/render/FmumFirstPersonPose.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.FmumFirstPersonPose.placement"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.FmumFirstPersonPose.aim"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.FmumFirstPersonPose.eyeInGunSpace"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
