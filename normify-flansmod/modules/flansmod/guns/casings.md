---
uid: d53c1775
id: flansmod.guns.casings
parent: flansmod.guns
state: planned
name: {zh: "弹壳", en: "Casings"}
description:
  zh: >
      从弹药定义找到抛出的弹壳物品，并在枪内保留未退膛的弹壳状态。
  en: >
      Finds the ejected casing item from a bullet definition and tracks a spent case retained in the gun.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 50b84ebd4fa0c9cc08a273c4ab08620c1bb12419e16167e7ae33cbfabf4a9846
source:
  - path: "src/main/java/com/flansmod/common/GunCasings.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunCasings.casing"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunCasings.retained"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
