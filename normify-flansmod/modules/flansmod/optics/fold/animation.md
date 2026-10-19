---
uid: "29337874"
id: flansmod.optics.fold.animation
parent: flansmod.optics.fold
state: planned
name: {zh: "折叠时序", en: "Fold timing"}
description:
  zh: >
      折叠状态的内存缓存与插值帧，换枪或换槽时取消。
  en: >
      In-memory fold state with interpolated frames, cancelled on gun or slot change.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: e5dea557b33b59583000aa48bced03104c74c96088abb52208bf0070655b42d1
source:
  - path: "src/main/java/com/flansmod/client/SightFoldAnimations.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.SightFoldAnimations.forStack"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
