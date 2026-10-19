---
uid: 02ef5421
id: flansmod.armour.state
parent: flansmod.armour
state: planned
name: {zh: "护甲状态", en: "Armour state"}
description:
  zh: >
      记录每件护甲的附加状态（遮罩、涂装）并随物品保存。
  en: >
      Tracks per-item armour state (mask, paintjob) and saves it with the item.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 4b975d8a674d9c21de2f047df5dc9b26e1a81c8f984b4e82fe9b2fdc3337d798
source:
  - path: "src/main/java/com/flansmod/common/ArmourState.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.ArmourState.mask"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.ArmourState.paintjob"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
