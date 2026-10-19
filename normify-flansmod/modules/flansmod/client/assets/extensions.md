---
uid: ad233dc3
id: flansmod.client.assets.extensions
parent: flansmod.client.assets
state: planned
name: {zh: "客户端扩展", en: "Client extensions"}
description:
  zh: >
      把物品的自定义渲染器与属性接入新版本客户端接口。
  en: >
      Wires the item custom renderer and properties into the modern client interfaces.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: e26c76242284d20fc7b68f11c5d9c06ba1debd50f17941f7af051c8e7bd19df1
source:
  - path: "src/main/java/com/flansmod/client/ClientExtensions.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.ClientExtensions.init"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
