---
uid: 46f30c02
id: flansmod.guns.reloading.state
parent: flansmod.guns.reloading
state: planned
name: {zh: "换弹帧", en: "Reload frame"}
description:
  zh: >
      把换弹进度换算成渲染用的阶段与进度帧。
  en: >
      Converts reload progress into the phase and progress frame the renderer consumes.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: fb3b4afc79b6f0c58acf0701af665eb2b6b3a0cb996ca8a52414ce40eb335c96
source:
  - path: "src/main/java/com/flansmod/common/GunReloadState.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunReloadState.frame"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
