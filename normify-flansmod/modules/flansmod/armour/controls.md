---
uid: bdd923f5
id: flansmod.armour.controls
parent: flansmod.armour
state: planned
name: {zh: "护甲操作", en: "Armour controls"}
description:
  zh: >
      切换遮罩与涂装等护甲操作的客户端入口。
  en: >
      Client entry point for armour operations such as toggling mask and paintjob.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 467fd88d2858cc744c31ab010f3aefabd42736f15d1cb4e5386c575f11a87f56
source:
  - path: "src/main/java/com/flansmod/client/ArmourControls.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.ArmourControls.handle"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
