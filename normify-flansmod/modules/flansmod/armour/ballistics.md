---
uid: 5a50b61d
id: flansmod.armour.ballistics
parent: flansmod.armour
state: planned
name: {zh: "护甲弹道", en: "Armour ballistics"}
description:
  zh: >
      子弹命中护甲时的穿透、角度与部位判定。
  en: >
      Penetration, angle and hit-location resolution when a bullet strikes armour.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 2757eb0af6ede3272d3dac4dfc71ce545750a7e4ce189abac0098068d71c77b9
source:
  - path: "src/main/java/com/flansmod/common/ArmourBallistics.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.ArmourBallistics.resolve"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
