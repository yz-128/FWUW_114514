---
uid: 028b13ef
id: flansmod.render.item.entry
parent: flansmod.render.item
state: planned
name: {zh: "渲染入口", en: "Render entry"}
description:
  zh: >
      按显示上下文分派：物品栏画图标，手持则进入完整装配渲染。
  en: >
      Dispatches by display context: the inventory draws the pack icon while a held gun enters the full assembly render.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 6799b20c4df10252a11d879d70cc1dcf06e974805a4b9bb50925a270634795a4
source:
  - path: "src/main/java/com/flansmod/client/render/TurborigItemRenderer.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.TurborigItemRenderer.renderByItem"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
