---
uid: dde87bdd
id: flansmod.client.assets.prewarm
parent: flansmod.client.assets
state: planned
name: {zh: "预热器", en: "Prewarmer"}
description:
  zh: >
      按注册表批量预热枪械、配件与护甲模型。
  en: >
      Warms gun, attachment and armour models in bulk from the registry.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: a84cd8d306239283d3d8ddd4028fc274598ab73eb0a1f5014f04ab718f97dd12
source:
  - path: "src/main/java/com/flansmod/client/ClientAssetPrewarmer.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.ClientAssetPrewarmer.warm"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
