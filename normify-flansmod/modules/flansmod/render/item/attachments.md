---
uid: 6a96720b
id: flansmod.render.item.attachments
parent: flansmod.render.item
state: planned
name: {zh: "配件渲染", en: "Attachment rendering"}
description:
  zh: >
      递归绘制挂点上的配件及其子配件，并按状态决定是否绘制条件子模型。
  en: >
      Recursively draws mounted parts and their children, deciding per state whether a conditional submodel is drawn.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 6799b20c4df10252a11d879d70cc1dcf06e974805a4b9bb50925a270634795a4
source:
  - path: "src/main/java/com/flansmod/client/render/TurborigItemRenderer.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.TurborigItemRenderer.renderAttachments"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
