---
uid: b3ba7155
id: flansmod.model.armour
parent: flansmod.model
state: planned
name: {zh: "护甲几何", en: "Armour geometry"}
description:
  zh: >
      护甲模型独立于人形骨架的几何烘焙入口。
  en: >
      Baking entry point for armour models that are independent of the humanoid skeleton.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: c04136921f7ba0a82275c5b529a8d61d4f1698ff90b646b0a34942fcae32d944
source:
  - path: "src/main/java/com/flansmod/client/model/loader/ArmourGeometry.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.ArmourGeometry.bake"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
