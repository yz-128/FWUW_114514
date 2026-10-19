---
uid: c361a8bd
id: flansmod.guns.magazine.ammo
parent: flansmod.guns.magazine
state: planned
name: {zh: "弹药数量", en: "Ammo counts"}
description:
  zh: >
      弹匣物品的剩余发数、按发消耗与可装填判定。
  en: >
      Remaining rounds on a magazine item, per-round consumption and loadability checks.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: a8bd03e171f16d7de98d68e1885933ba7433dbee35d8a1003c1672f8fec31c98
source:
  - path: "src/main/java/com/flansmod/common/GunMagazine.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunMagazine.roundsRemaining"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunMagazine.consumeRound"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunMagazine.hasRound"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
