---
uid: 75267dcd
id: flansmod.client.gui.preview
parent: flansmod.client.gui
state: planned
name: {zh: "整枪预览", en: "Assembly preview"}
description:
  zh: >
      在界面里旋转整枪并应用当前候选改装，实时呈现效果。
  en: >
      Rotates the whole gun inside the screen and applies the pending modification for a live preview.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 6f36ca84fa14929a30c50ada63f82df712844f6fe9d77f7d45c8cf1beeac04c8
source:
  - path: "src/main/java/com/flansmod/client/gui/GunModificationPreview.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.gui.GunModificationPreview.render"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
