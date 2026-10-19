---
uid: 6bf5e90f
id: flansmod.guns.attachments.view
parent: flansmod.guns.attachments
state: planned
name: {zh: "装配视图", en: "Assembly view"}
description:
  zh: >
      把物品上的装配树读成不可变视图，供瞄准、渲染与改装共用；修订号用于检测过期请求。
  en: >
      Reads the stack attachment tree into an immutable view shared by aiming, rendering and modifications; a revision detects stale requests.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 85ebfbcad9b44547c713fb69131bb9441b89d6a424659faf97d359adf7e7a0ad
source:
  - path: "src/main/java/com/flansmod/common/GunAttachments.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunAttachments.view"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunAttachments.revision"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunAttachments.ownerAt"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
