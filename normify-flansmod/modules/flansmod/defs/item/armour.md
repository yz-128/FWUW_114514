---
uid: ed696c84
id: flansmod.defs.item.armour
parent: flansmod.defs.item
state: planned
name: {zh: "护甲物品", en: "Armour item"}
description:
  zh: >
      护甲物品的装备槽、护甲值与自定义渲染入口。
  en: >
      Armour item equipment slot, armour value and custom renderer entry point.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 589ce0a698c8b78f90b622a0bbb0c5f4ffde551bc82db82ae9d3cc63d9a2a526
source:
  - path: "src/main/java/com/flansmod/common/types/ItemFlanArmour.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.ItemFlanArmour.getEquipmentSlot"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.ItemFlanArmour.getArmourValue"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
