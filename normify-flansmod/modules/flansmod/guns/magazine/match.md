---
uid: 314cc746
id: flansmod.guns.magazine.match
parent: flansmod.guns.magazine
state: planned
name: {zh: "口径匹配", en: "Caliber matching"}
description:
  zh: >
      判断弹药与弹匣是否适配该枪，并识别弹匣类型。
  en: >
      Decides whether a round or magazine fits the gun and identifies the magazine type.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: a8bd03e171f16d7de98d68e1885933ba7433dbee35d8a1003c1672f8fec31c98
source:
  - path: "src/main/java/com/flansmod/common/GunMagazine.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunMagazine.matchesAmmo"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunMagazine.acceptsMagazine"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunMagazine.magazineType"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
