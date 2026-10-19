---
uid: 52caffc4
id: flansmod.render.pose.arms
parent: flansmod.render.pose
state: planned
name: {zh: "手臂姿态", en: "Arm poses"}
description:
  zh: >
      双手在握持、换弹、拉栓等状态下的落点与旋转。
  en: >
      Hand placement and rotation for holding, reloading and charging.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 1fe44d06916fee19728ce4138465be1b74a8c4132b7872365985d30af1ea2ae2
source:
  - path: "src/main/java/com/flansmod/client/render/FmumHoldingArmPose.java"
  - path: "src/main/java/com/flansmod/client/LegacyGunArmPose.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.FmumHoldingArmPose.arm"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.LegacyGunArmPose.apply"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
