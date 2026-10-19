---
uid: aa16c441
id: flansmod.client.hud.details
parent: flansmod.client.hud
state: planned
name: {zh: "物品提示", en: "Item tooltips"}
description:
  zh: >
      按住 Shift 展开的有效数值与弹药说明。
      
  en: >
      Shift-expanded effective stats and ammunition descriptions.
      
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:49.656Z"
fingerprint: 02cbf4397b5f433d9de1e3d80dcdd33b8de1a9d2c82d896e11d300f69c660913
source:
  - path: "src/main/java/com/flansmod/client/GunDetails.java"
  - path: "src/main/java/com/flansmod/client/AmmoDetails.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.GunDetails.tooltip"
    description:
      zh: >
          枪械数值与说明的提示行。
      en: >
          Tooltip lines for gun stats and description.
  - protocol: rpc
    path: "rpc:com.flansmod.client.AmmoDetails.tooltip"
    description:
      zh: >
          弹药数值与说明的提示行。
      en: >
          Tooltip lines for ammunition stats and description.
---
