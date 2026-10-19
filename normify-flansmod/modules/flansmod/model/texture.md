---
uid: a52860e8
id: flansmod.model.texture
parent: flansmod.model
state: planned
name: {zh: "贴图尺寸", en: "Texture sizes"}
description:
  zh: >
      读取贴图真实尺寸，用于修正模型类里写死的 UV 假设。
  en: >
      Reads the real texture dimensions used to correct the hardcoded UV assumptions inside model classes.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: da7e1a19e6298ef36011a142685c71b1b5c66f5f88507cb7085deab05f543fea
source:
  - path: "src/main/java/com/flansmod/client/model/loader/TextureSizes.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.TextureSizes.of"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
