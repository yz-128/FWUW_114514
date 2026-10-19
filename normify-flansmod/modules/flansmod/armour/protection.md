---
uid: a2dd4b8d
id: flansmod.armour.protection
parent: flansmod.armour
state: planned
name: {zh: "减伤计算", en: "Damage reduction"}
description:
  zh: >
      按护甲值与覆盖部位计算实际减伤。
  en: >
      Computes effective damage reduction from armour value and covered body parts.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: bc4248771edc0a2e499870b3f21c534cf737d83394f5d8206b870f267a977725
source:
  - path: "src/main/java/com/flansmod/common/ArmourProtection.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.ArmourProtection.reduce"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
