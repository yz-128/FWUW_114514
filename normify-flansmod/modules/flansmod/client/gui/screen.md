---
uid: f07e60f1
id: flansmod.client.gui.screen
parent: flansmod.client.gui
state: planned
name: {zh: "界面主体", en: "Screen"}
description:
  zh: >
      左侧装配树、中间候选列表、右侧预览与数值对比，并把操作打包成请求。
  en: >
      Assembly tree on the left, candidate list in the middle, preview and stat comparison on the right, packaging edits into requests.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 953a87c273daabe35b411ce0e84f2aa39150bf0c524cc5df1a5faae4999a42ca
source:
  - path: "src/main/java/com/flansmod/client/gui/GunModificationScreen.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.gui.GunModificationScreen.render"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.gui.GunModificationScreen.mouseClicked"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
