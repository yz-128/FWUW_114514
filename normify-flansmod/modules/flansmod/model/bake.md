---
uid: a4ed4552
id: flansmod.model.bake
parent: flansmod.model
state: planned
name: {zh: "烘焙", en: "Baking"}
description:
  zh: >
      把关卡内的模型单位换算成方块单位，展开成可直接提交的四边形，并修正 UV 尺寸不匹配。
  en: >
      Converts model units into blocks, expands parts into ready-to-submit quads and corrects mismatched UV sheet sizes.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 8d136d0fab8cdbed935bff804860fc0c6f279fa13bdde2afaeecea911e44dcf4
source:
  - path: "src/main/java/com/flansmod/client/model/loader/BakedRig.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.BakedRig.bake"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
