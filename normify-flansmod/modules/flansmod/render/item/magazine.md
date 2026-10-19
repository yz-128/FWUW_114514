---
uid: 91c30eb7
id: flansmod.render.item.magazine
parent: flansmod.render.item
state: planned
name: {zh: "弹匣与弹", en: "Magazine and rounds"}
description:
  zh: >
      绘制弹匣、匣内子弹与弹膛内的子弹，包括弹簧跟随件。
  en: >
      Draws the magazine, its seated rounds and the chambered round, including the follower.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 6799b20c4df10252a11d879d70cc1dcf06e974805a4b9bb50925a270634795a4
source:
  - path: "src/main/java/com/flansmod/client/render/TurborigItemRenderer.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.TurborigItemRenderer.renderMagazine"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.TurborigItemRenderer.renderSeatedRounds"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
