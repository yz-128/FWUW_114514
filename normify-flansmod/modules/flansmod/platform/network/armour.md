---
uid: db5c9a0d
id: flansmod.platform.network.armour
parent: flansmod.platform.network
state: planned
name: {zh: "护甲载荷", en: "Armour payload"}
description:
  zh: >
      同步护甲的遮罩/涂装状态，使其他客户端看到同一套外观。
  en: >
      Syncs armour mask and paintjob state so other clients see the same appearance.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 8f9408c9f462bc71e39fab2bec5efc0db103dd1f0daa57db85ea241e5986f93c
source:
  - path: "src/main/java/com/flansmod/common/network/ArmourMaskPayload.java"
apis:
  - protocol: rpc
    path: "rpc:flansmod:armour_mask"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
