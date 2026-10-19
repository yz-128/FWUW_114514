---
uid: 8ac52700
id: flansmod.guns.magazine.storage
parent: flansmod.guns.magazine
state: planned
name: {zh: "弹药存储", en: "Ammo storage"}
description:
  zh: >
      以 NBT 列表保存每个弹膛的内容，读写的唯一入口，保证服务端与客户端一致。
  en: >
      Stores each chamber in an NBT list; this is the only read/write entry point, keeping client and server consistent.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: a8bd03e171f16d7de98d68e1885933ba7433dbee35d8a1003c1672f8fec31c98
source:
  - path: "src/main/java/com/flansmod/common/GunMagazine.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunMagazine.get"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunMagazine.set"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunMagazine.findLoaded"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
