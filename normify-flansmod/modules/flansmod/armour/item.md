---
uid: 0e8a16d4
id: flansmod.armour.item
parent: flansmod.armour
state: planned
name: {zh: "护甲物品", en: "Armour item"}
description:
  zh: >
      装备槽、护甲值与自定义渲染的入口。
      
  en: >
      Equipment slot, armour value and custom renderer entry point.
      
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:25:02.803Z"
fingerprint: 589ce0a698c8b78f90b622a0bbb0c5f4ffde551bc82db82ae9d3cc63d9a2a526
source:
  - path: "src/main/java/com/flansmod/common/types/ItemFlanArmour.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.ItemFlanArmour.layerId"
    description:
      zh: >
          护甲渲染层标识。
      en: >
          The armour render layer id.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.ItemFlanArmour.getDefaultAttributeModifiers"
    description:
      zh: >
          护甲提供的属性修饰符。
      en: >
          Attribute modifiers the armour grants.
---
