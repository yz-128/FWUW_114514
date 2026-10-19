---
uid: a695f681
id: flansmod.platform.assets
parent: flansmod.platform
state: planned
name: {zh: "生成资源", en: "Generated assets"}
description:
  zh: >
      为内容包物品生成缺失的模型与图标定义，让没有自带资源的老包也能在 1.21.1 的物品栏里正确显示。
  en: >
      Generates missing item models and icons for pack items so older packs without modern assets still appear correctly in the 1.21.1 inventory.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 810186f1b7f2a470af5af6469267d247a72fcfa2f3a7329d1df6c7da6561ea28
source:
  - path: "src/main/java/com/flansmod/common/GeneratedAssets.java"
  - path: "src/main/java/com/flansmod/common/ItemAssets.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GeneratedAssets.generate"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.ItemAssets.iconFor"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
