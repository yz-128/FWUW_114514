---
uid: dd056ab9
id: flansmod.render.pose.reload
parent: flansmod.render.pose
state: planned
name: {zh: "换弹手臂", en: "Reload arms"}
description:
  zh: >
      换弹各阶段手臂的位置与朝向。
  en: >
      Arm position and orientation through each reload phase.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 7a01b497379b4fa2b4ca64d963a66a54cb6be93c22372fddd03f6ecbf11a8361
source:
  - path: "src/main/java/com/flansmod/client/render/FmumReloadPose.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.FmumReloadPose.arm"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
