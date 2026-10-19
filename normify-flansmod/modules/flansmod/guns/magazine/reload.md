---
uid: 9a17b859
id: flansmod.guns.magazine.reload
parent: flansmod.guns.magazine
state: planned
name: {zh: "装填流程", en: "Reload flow"}
description:
  zh: >
      从背包或弹匣物品装填、退弹与换弹匣，并按原版规则保留弹膛状态。
  en: >
      Loads from inventory or a magazine item, unloads and swaps magazines while preserving chamber state the way the original did.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: a8bd03e171f16d7de98d68e1885933ba7433dbee35d8a1003c1672f8fec31c98
source:
  - path: "src/main/java/com/flansmod/common/GunMagazine.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunMagazine.reload"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunMagazine.firstMagazine"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunMagazine.chamberedBullet"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
