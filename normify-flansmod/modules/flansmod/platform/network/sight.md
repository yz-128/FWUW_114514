---
uid: 9e4806d9
id: flansmod.platform.network.sight
parent: flansmod.platform.network
state: planned
name: {zh: "瞄具载荷", en: "Sight payloads"}
description:
  zh: >
      瞄具组切换、变倍档位、准点帧与折叠动画的状态同步。
  en: >
      State sync for sight-group switching, zoom steps, reticle frames and fold animation.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 75d901a14919f7ca87b73019a7319734eef15ec6423e071a7cc7b69f1c11541e
source:
  - path: "src/main/java/com/flansmod/common/network/GunSightPayload.java"
  - path: "src/main/java/com/flansmod/common/network/GunSightAnimationPayload.java"
  - path: "src/main/java/com/flansmod/common/network/GunAimPayload.java"
apis:
  - protocol: rpc
    path: "rpc:flansmod:gun_sight"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:flansmod:gun_sight_animation"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:flansmod:gun_aim"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
