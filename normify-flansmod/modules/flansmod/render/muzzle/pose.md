---
uid: 0a7cc919
id: flansmod.render.muzzle.pose
parent: flansmod.render.muzzle
state: planned
name: {zh: "枪口定位", en: "Muzzle placement"}
description:
  zh: >
      从模型字段推导枪口与抛壳口的位置和朝向。
  en: >
      Derives muzzle and ejection port position and orientation from model fields.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 5a28816c4033cf95e8ecc8bfb24e4649f49d35499ae466a74e160e16982783f1
source:
  - path: "src/main/java/com/flansmod/client/render/FmumMuzzlePose.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.FmumMuzzlePose.smokeOrigin"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.FmumMuzzlePose.flashOrigin"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
