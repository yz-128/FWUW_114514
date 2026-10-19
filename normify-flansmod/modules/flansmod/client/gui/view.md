---
uid: 16e8579e
id: flansmod.client.gui.view
parent: flansmod.client.gui
state: planned
name: {zh: "取景框", en: "View framing"}
description:
  zh: >
      按旋转轮廓自动取景，使不同长度的枪都落在画面内。
  en: >
      Frames the gun from its rotated silhouette so guns of different lengths all fit the panel.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 1b579dba59b72ab668533e1b31153c9a4bddda0a8121cdeb1494938cf490b3b3
source:
  - path: "src/main/java/com/flansmod/client/gui/GunModificationView.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.gui.GunModificationView.frame"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
