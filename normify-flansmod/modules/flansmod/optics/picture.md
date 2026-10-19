---
uid: 314f3065
id: flansmod.optics.picture
parent: flansmod.optics
state: planned
name: {zh: "光学几何", en: "Optical geometry"}
description:
  zh: >
      眼睛到瞳孔的实测距离、射线与镜框平面的交点，以及镜框可见性判定。
  en: >
      Measured eye-to-pupil distance, the ray/plane intersection with the reticle frame, and aperture visibility.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 02849c110de0b2dbd3e499ca67cac507b78622c4d5f1855ed56d49c5d0abf718
source:
  - path: "src/main/java/com/flansmod/client/render/FmumSightPicture.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.FmumSightPicture.pupilDistance"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.FmumSightPicture.intersection"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
