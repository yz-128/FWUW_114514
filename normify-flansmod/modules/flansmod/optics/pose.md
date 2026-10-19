---
uid: 390d6c44
id: flansmod.optics.pose
parent: flansmod.optics
state: planned
name: {zh: "瞄具枢轴", en: "Sight pivot"}
description:
  zh: >
      把选中瞄具的中心对齐到相机在枪空间的实际位置。
  en: >
      Aligns the selected sight centre to the camera position actually measured in gun space.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 33d7a951349e3e2c6ae75a6c12ecb148051b911a56d5b9c1cfc8c1fc565afb94
source:
  - path: "src/main/java/com/flansmod/client/render/LegacySightPose.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.LegacySightPose.forGun"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
