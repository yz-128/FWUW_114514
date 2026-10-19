---
uid: 1bdcf63e
id: flansmod.optics.magnification
parent: flansmod.optics
state: planned
name: {zh: "倍率应用", en: "Magnification"}
description:
  zh: >
      把光学倍率作用到世界相机 FOV，同时保留手持物的独立 FOV，使枪与镜不变形。
  en: >
      Applies optical magnification to the world camera FOV while keeping a separate hand FOV so the gun and optic keep their size.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: bba745b05656df09d8706c5c5680236dc382595c647018e0f666a3224f1769a2
source:
  - path: "src/main/java/com/flansmod/client/render/ScopeMagnification.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.ScopeMagnification.frame"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.ScopeMagnification.zoom"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
