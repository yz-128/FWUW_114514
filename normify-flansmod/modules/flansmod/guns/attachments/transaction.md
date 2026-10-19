---
uid: edd36c83
id: flansmod.guns.attachments.transaction
parent: flansmod.guns.attachments
state: planned
name: {zh: "改装事务", en: "Modification transaction"}
description:
  zh: >
      一次改装的完整校验与写入：槽位、数量、兼容性、数量守恒，失败时整体回滚。
  en: >
      Validates and applies one modification — slot, count, compatibility, item conservation — rolling back entirely on failure.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 7076f1fe220616aea4008c3589c273d5fcae2f95d065f8fdca352424632dcac2
source:
  - path: "src/main/java/com/flansmod/common/GunAttachmentTransaction.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunAttachmentTransaction.apply"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
